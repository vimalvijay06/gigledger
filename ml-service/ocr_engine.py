

from __future__ import annotations

import re
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import pytesseract
from PIL import Image

# ── Windows: Tesseract binary path ──────────────────────────────────────────
# Explicitly set the path since Tesseract-OCR may not be on the system PATH.
# Confirmed installed at this location via winget (UB-Mannheim Tesseract 5.4.0).
import os as _os
_TESS_WIN = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
if _os.path.exists(_TESS_WIN):
    pytesseract.pytesseract.tesseract_cmd = _TESS_WIN

# ── Compiled regex patterns (compiled once at import time for performance) ──

# Primary: matches ₹85  ₹ 85.50  Rs. 120  Rs 200  INR 85
# Also handles case where 'Rs.' is on a SEPARATE line from the number
# (common when Tesseract reads large-font text that wraps or when the
# currency prefix and the digit are drawn at different x-positions).
CURRENCY_RE = re.compile(
    r'(?:₹|Rs\.?|INR)\s*\n?\s*([0-9]+(?:\.[0-9]{1,2})?)',
    re.IGNORECASE
)

# Fallback: bare number in a realistic fare range (₹30–₹2000)
# Used only when no currency-symbol match is found.
BARE_NUMBER_RE = re.compile(r'\b([1-9][0-9]{1,3}(?:\.[0-9]{1,2})?)\b')

# Matches:  3.5 km  12 kms  7.2KM  2.1 kilometres  5 kilometer
DISTANCE_RE = re.compile(
    r'([0-9]+(?:\.[0-9]+)?)\s*(?:km|kms|KM|kilometre|kilometers|kilometres|kilometer)',
    re.IGNORECASE
)

#  STEP 1 — Image Preprocessing

def preprocess_image(bgr_img: np.ndarray) -> dict[str, np.ndarray]:
    
    stages: dict[str, np.ndarray] = {}

    # Original (RGB for matplotlib display)
    stages['original'] = cv2.cvtColor(bgr_img, cv2.COLOR_BGR2RGB)

    # 1. Grayscale
    gray = cv2.cvtColor(bgr_img, cv2.COLOR_BGR2GRAY)
    stages['grayscale'] = gray

    # 2. CLAHE — adaptive contrast equalisation
    #    clipLimit=2.0 prevents over-amplifying noise.
    #    tileGridSize=(8,8) applies equalisation in local 8×8 pixel blocks.
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    contrast = clahe.apply(gray)
    stages['contrast_adjusted'] = contrast

    # 3. Light Gaussian blur before thresholding
    blurred = cv2.GaussianBlur(contrast, (3, 3), 0)

    # 4. Otsu's automatic binarisation
    #    THRESH_OTSU picks the optimal threshold value automatically —
    #    no manual tuning required when images vary in brightness.
    _, binary = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # 5. Auto-inversion heuristic
    #    Delivery apps often use dark backgrounds (Zomato dark, Swiggy dark).
    #    Tesseract performs best with black text on a white background.
    #    If >60% of pixels are white, the image is already correct; otherwise invert.
    white_fraction = np.sum(binary == 255) / binary.size
    if white_fraction <= 0.6:
        binary = cv2.bitwise_not(binary)

    stages['thresholded'] = binary
    stages['final'] = binary
    return stages

#  STEP 2 — Tesseract OCR

def run_ocr(preprocessed_img: np.ndarray) -> str:
    
    try:
        pil_img = Image.fromarray(preprocessed_img)
        return pytesseract.image_to_string(pil_img, config='--oem 3 --psm 6', lang='eng')
    except Exception:
        return ''

#  STEP 3 — Field Extraction

def extract_fields(text: str) -> dict:
    
    currency_matches = [float(m) for m in CURRENCY_RE.findall(text)]
    distance_matches = [float(m) for m in DISTANCE_RE.findall(text)]

    used_fallback = False

    if currency_matches:
        # Use the first (topmost) currency match.
        # If multiple exist the confidence scorer will penalise this.
        promised_amount: Optional[float] = currency_matches[0]
    else:
        # Bare-number fallback: pick the largest number in a plausible fare range.
        # This is a last resort — lower confidence, flagged explicitly.
        candidates = [
            float(m) for m in BARE_NUMBER_RE.findall(text)
            if 30 <= float(m) <= 2000
        ]
        if candidates:
            promised_amount = max(candidates)
            used_fallback = True
        else:
            promised_amount = None

    distance_km: Optional[float] = distance_matches[0] if distance_matches else None

    return {
        'currency_matches': currency_matches,
        'distance_matches': distance_matches,
        'promised_amount':  promised_amount,
        'distance_km':      distance_km,
        'used_fallback':    used_fallback,
    }

#  STEP 4 — Confidence Scoring

def compute_confidence(fields: dict, raw_text: str) -> float:
    
    has_amount   = fields['promised_amount'] is not None
    has_distance = fields['distance_km']     is not None
    n_currency   = len(fields['currency_matches'])
    used_fallback = fields['used_fallback']

    if not has_amount and not has_distance:
        return 0.0   # Nothing found at all — confidence is zero

    score = 1.0

    if not has_amount:
        score -= 0.20   # Missing amount is a bigger problem than missing distance
    if not has_distance:
        score -= 0.20
    if n_currency > 1:
        score -= 0.30   # Ambiguous — multiple ₹ amounts, we guessed the first
    if used_fallback:
        score -= 0.25   # No currency symbol — bare number match is unreliable
    if len(raw_text.strip()) < 20:
        score -= 0.30   # OCR barely extracted text — image quality issue

    return round(max(0.0, min(1.0, score)), 4)

#  PUBLIC API — single entry point used by FastAPI and the notebook

def process_image_bytes(image_bytes: bytes) -> dict:
    
    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    bgr_img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if bgr_img is None:
        return _empty_result("Could not decode image bytes")
    return _run_pipeline(bgr_img)

def process_image_path(image_path: str | Path) -> dict:
    
    bgr_img = cv2.imread(str(image_path))
    if bgr_img is None:
        return _empty_result(f"Could not load image: {image_path}")
    return _run_pipeline(bgr_img)

# ── Internal helpers ─────────────────────────────────────────────────────────

def _run_pipeline(bgr_img: np.ndarray) -> dict:
    stages   = preprocess_image(bgr_img)
    raw_text = run_ocr(stages['final'])
    fields   = extract_fields(raw_text)
    conf     = compute_confidence(fields, raw_text)
    return {
        'promised_amount': fields['promised_amount'],
        'distance_km':     fields['distance_km'],
        'confidence':      conf,
        'raw_text':        raw_text.strip(),
    }

def _empty_result(reason: str) -> dict:
    return {
        'promised_amount': None,
        'distance_km':     None,
        'confidence':      0.0,
        'raw_text':        reason,
    }
