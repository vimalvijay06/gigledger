"""
anomaly_engine.py — GigLedger Statistical Anomaly Detection Engine

Extracts the bucketing, rolling baseline, and flagging logic from
anomaly_prototype.ipynb into a clean, importable module.

Public API
----------
detect_anomalies(tasks: list[dict]) -> list[dict]
    Takes a list of task dicts (task_id, promised_amount, actual_amount,
    distance_km, accepted_at as ISO-8601 string) and returns a list of
    flagged period dicts, or an empty list when there is not enough data.

Design decisions
----------------
- All logic is pure Python + pandas/numpy. No I/O, no HTTP calls.
- The function returns [] rather than raising if data is insufficient.
  The caller (FastAPI endpoint) decides how to surface that to the client.
- Configuration constants are module-level so they can be overridden in tests.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from typing import Any

import numpy as np
import pandas as pd

logger = logging.getLogger("gigledger.anomaly")

# ── Detection configuration ────────────────────────────────────────────────────
ROLLING_WINDOW_DAYS    = 30    # trailing baseline window in calendar days
FLAG_THRESHOLD_SD      = 1.5   # flag if observed < baseline - N * rolling_std
MEDIUM_THRESHOLD_SD    = 2.0
HIGH_THRESHOLD_SD      = 2.5
MIN_TASKS_IN_BUCKET    = 30    # minimum tasks required before any flag is raised
CONSECUTIVE_WEEKS_HIGH = 2     # escalate to 'high' if flagged for >= N consecutive weeks


# ── Time-of-day bucketing ──────────────────────────────────────────────────────

def _classify_bucket(hour: int) -> str:
    """
    Split the working day into surge vs normal windows.

    Peak (12-14h, 19-22h) carries platform-added surge pricing, so
    comparing peak rates to off-peak rates would generate false alarms.
    Keeping them in separate buckets means each bucket's baseline only
    reflects work done during the same demand conditions.
    """
    if 12 <= hour < 14:
        return "peak"
    if 19 <= hour < 22:
        return "peak"
    return "off_peak"


# ── Data ingestion and feature engineering ────────────────────────────────────

def _build_dataframe(tasks: list[dict[str, Any]]) -> pd.DataFrame:
    """
    Convert raw task dicts into a feature-enriched DataFrame.
    Tasks with missing or zero distance_km are dropped (can't compute rate).
    """
    if not tasks:
        return pd.DataFrame()

    df = pd.DataFrame(tasks)

    # Normalise accepted_at to timezone-naive datetime
    df["accepted_at"] = pd.to_datetime(df["accepted_at"], format="ISO8601", utc=True).dt.tz_convert(None)

    # Drop rows that cannot produce an effective rate
    df = df[df["distance_km"].notna() & (df["distance_km"] > 0)].copy()

    if df.empty:
        return df

    df["actual_amount"]  = pd.to_numeric(df["actual_amount"],  errors="coerce")
    df["distance_km"]    = pd.to_numeric(df["distance_km"],    errors="coerce")
    df                   = df.dropna(subset=["actual_amount", "distance_km"])

    # Core feature: Rs earned per km
    df["effective_rate"] = df["actual_amount"] / df["distance_km"]

    df["hour"]      = df["accepted_at"].dt.hour
    df["bucket"]    = df["hour"].apply(_classify_bucket)
    df["year_week"] = df["accepted_at"].dt.to_period("W")

    return df.sort_values("accepted_at").reset_index(drop=True)


# ── Rolling baseline computation ───────────────────────────────────────────────

def _compute_rolling_baseline(df: pd.DataFrame) -> pd.DataFrame:
    """
    For each task, compute the trailing 30-day mean and std of effective_rate
    *within the same time-of-day bucket*, using only tasks strictly before
    the current task (no lookahead bias).

    If fewer than MIN_TASKS_IN_BUCKET tasks exist in the window for a given
    bucket, rolling_mean and rolling_std are set to NaN — these rows will be
    excluded from flagging (the minimum-data guard).
    """
    df = df.copy()
    df = df.sort_values("accepted_at").reset_index(drop=True)
    df["_temp_id"] = range(len(df))
    
    temp_df = df.set_index("accepted_at")
    
    results = []
    for bucket, group in temp_df.groupby("bucket"):
        rolling = group["effective_rate"].rolling(f"{ROLLING_WINDOW_DAYS}D", closed="left")
        count = rolling.count().fillna(0).astype(int)
        mean = rolling.mean()
        std = rolling.std()
        
        mask = count < MIN_TASKS_IN_BUCKET
        mean[mask] = float("nan")
        std[mask] = float("nan")
        
        res = pd.DataFrame({
            "_temp_id": group["_temp_id"],
            "rolling_mean": mean,
            "rolling_std": std,
            "window_n": count
        })
        results.append(res)
        
    stats_df = pd.concat(results).sort_values("_temp_id").reset_index(drop=True)
    
    df["rolling_mean"] = stats_df["rolling_mean"]
    df["rolling_std"] = stats_df["rolling_std"]
    df["window_n"] = stats_df["window_n"]
    
    df = df.drop(columns=["_temp_id"])
    return df


# ── Weekly flagging ────────────────────────────────────────────────────────────

def _detect_weekly_flags(df: pd.DataFrame) -> pd.DataFrame:
    """
    Aggregate by ISO week + bucket, compute SD-below-baseline, and flag.

    Flag rule:  observed_weekly_avg_rate  <  baseline_mean - 1.5 * baseline_std

    Severity:
      low    — 1.5 to 2.0 SD below baseline
      medium — 2.0 to 2.5 SD below baseline
      high   — 2.5+ SD below baseline
      high   — also applied when flagged for >= CONSECUTIVE_WEEKS_HIGH weeks
    """
    weekly_records: list[dict] = []

    for (year_week, bucket), group in df.groupby(["year_week", "bucket"]):
        # Only use rows with a valid baseline
        gv = group.dropna(subset=["rolling_mean", "rolling_std"])
        if len(gv) == 0:
            continue

        obs        = gv["effective_rate"].mean()
        base_mean  = gv["rolling_mean"].mean()
        base_std   = gv["rolling_std"].mean()

        if base_std == 0 or np.isnan(base_std):
            continue

        sd_below   = (base_mean - obs) / base_std
        is_flagged = sd_below >= FLAG_THRESHOLD_SD

        if   sd_below < FLAG_THRESHOLD_SD:   severity = "none"
        elif sd_below < MEDIUM_THRESHOLD_SD: severity = "low"
        elif sd_below < HIGH_THRESHOLD_SD:   severity = "medium"
        else:                                severity = "high"

        weekly_records.append({
            "year_week"     : str(year_week),
            "period_start"  : group["accepted_at"].min().date().isoformat(),
            "period_end"    : group["accepted_at"].max().date().isoformat(),
            "bucket"        : bucket,
            "observed_rate" : round(obs, 4),
            "baseline_rate" : round(base_mean, 4),
            "baseline_std"  : round(base_std, 4),
            "sd_below"      : round(sd_below, 4),
            "n_tasks"       : len(gv),
            "is_flagged"    : is_flagged,
            "severity"      : severity,
        })

    if not weekly_records:
        return pd.DataFrame()

    wdf = (
        pd.DataFrame(weekly_records)
        .sort_values(["bucket", "period_start"])
        .reset_index(drop=True)
    )

    # Consecutive-week escalation: sustained low/medium → escalate to high
    for bucket in wdf["bucket"].unique():
        consec = 0
        indices_to_escalate: list[int] = []
        for idx, row in wdf[wdf["bucket"] == bucket].iterrows():
            if row["is_flagged"]:
                consec += 1
                if consec >= CONSECUTIVE_WEEKS_HIGH:
                    indices_to_escalate.append(idx)
            else:
                consec = 0
        for idx in indices_to_escalate:
            if wdf.at[idx, "severity"] != "none":
                wdf.at[idx, "severity"] = "high"

    return wdf[wdf["is_flagged"]].reset_index(drop=True)


# ── Public API ─────────────────────────────────────────────────────────────────

def detect_anomalies(tasks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """
    Detect sustained underpayment anomalies in a worker's task history.

    Parameters
    ----------
    tasks : list of dicts, each with keys:
        task_id         — any unique identifier (str or int)
        promised_amount — float, fare shown before accepting (Rs)
        actual_amount   — float, what was actually paid (Rs)
        distance_km     — float, delivery distance
        accepted_at     — str, ISO-8601 datetime (with or without timezone)

    Returns
    -------
    list of flagged-period dicts:
        period_start    — ISO date string (start of flagged week)
        period_end      — ISO date string (end of flagged week)
        bucket          — "peak" or "off_peak"
        baseline_rate   — Rs/km rolling baseline mean for that bucket/window
        observed_rate   — Rs/km actually observed during the flagged week
        severity        — "low" | "medium" | "high"
        sd_below        — how many SDs below the baseline the observed rate is

    Returns [] (empty list, not an error) when:
        - tasks is empty
        - no task has a valid distance_km
        - no bucket has accumulated MIN_TASKS_IN_BUCKET tasks in any window
    """
    logger.info("detect_anomalies called with %d tasks", len(tasks))

    df = _build_dataframe(tasks)
    if df.empty:
        logger.info("Empty DataFrame after ingestion — returning []")
        return []

    df = _compute_rolling_baseline(df)

    # Check if any task has enough baseline history to be evaluated
    if df["rolling_mean"].isna().all():
        logger.info(
            "No task has >= %d tasks in its trailing window — insufficient data, returning []",
            MIN_TASKS_IN_BUCKET,
        )
        return []

    flags = _detect_weekly_flags(df)

    if flags.empty:
        logger.info("No anomalies detected — all weeks within normal range")
        return []

    # Return only the fields the API contract promises
    output_cols = [
        "period_start", "period_end", "bucket",
        "baseline_rate", "observed_rate", "severity", "sd_below",
    ]
    result = flags[output_cols].to_dict(orient="records")
    logger.info("Returning %d flagged period(s)", len(result))
    return result
