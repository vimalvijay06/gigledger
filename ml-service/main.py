"""
main.py  —  GigLedger OCR Microservice
────────────────────────────────────────
FastAPI app that exposes one endpoint:

    POST /ocr/extract
        Accepts: multipart/form-data  (field name: "file")
        Returns: JSON  { promised_amount, distance_km, confidence, raw_text }

All actual OCR logic lives in ocr_engine.py — this file is purely the
HTTP layer. Keeping them separate means you can:
  • Unit-test the OCR engine without starting a server
  • Swap the web framework without touching any OCR code

Error handling philosophy:
  - Never return a 500 for bad image input. Instead return a 200 with
    confidence: 0.0 and a clear message in raw_text. The caller (Spring Boot)
    can then decide whether to surface that as an error to the user.
  - Only genuine server-side failures (e.g. Tesseract binary missing) should
    return a 500, because those need operator attention.
"""

import io
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, File, UploadFile, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import os
from pathlib import Path

def _load_env():
    env_path = Path(__file__).parent / ".env"
    if env_path.exists():
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ[k.strip()] = v.strip()

_load_env()

import pytesseract
import ocr_engine       # OCR pipeline
import anomaly_engine   # statistical anomaly detection
import policy_fetcher   # Policy news updates aggregator
import gemini_helper    # LLM translation, intent classification and summaries
import sarvam_helper    # Speech-to-Text and Text-to-Speech via Sarvam AI

# ── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
)
logger = logging.getLogger("gigledger.ocr")

# ── Allowed image MIME types ──────────────────────────────────────────────────
ALLOWED_CONTENT_TYPES = {
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/webp",
    "image/gif",
    "image/bmp",
    "image/tiff",
}

# Max upload size: 10 MB.
# Delivery-app screenshots are typically 100–500 KB; anything larger is suspicious.
MAX_FILE_BYTES = 10 * 1024 * 1024  # 10 MB


# ── Pydantic response model ────────────────────────────────────────────────────

class OcrResult(BaseModel):
    promised_amount: Optional[float]
    distance_km:     Optional[float]
    confidence:      float
    raw_text:        str


# ── Anomaly Detection models ───────────────────────────────────────────────────

class TaskInput(BaseModel):
    """One task record sent by Spring Boot for anomaly analysis."""
    task_id:         str
    promised_amount: float = Field(..., gt=0)
    actual_amount:   float = Field(..., ge=0)
    distance_km:     float = Field(..., gt=0)
    accepted_at:     str   # ISO-8601 datetime string


class AnomalyFlag(BaseModel):
    """One flagged time period returned by the anomaly detector."""
    period_start:   str
    period_end:     str
    bucket:         str   # "peak" or "off_peak"
    baseline_rate:  float # Rs/km rolling mean before the anomaly
    observed_rate:  float # Rs/km actually seen during this period
    severity:       str   # "low" | "medium" | "high"
    sd_below:       float # how many SDs below baseline the observed rate is


# ── App startup / shutdown ────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Verify that the Tesseract binary is reachable before accepting requests.
    Fail fast at startup rather than on the first OCR request.
    """
    try:
        version = pytesseract.get_tesseract_version()
        logger.info("Tesseract binary confirmed — version %s", version)
    except Exception as exc:
        logger.error(
            "Tesseract binary not found: %s\n"
            "  Install it before starting this service:\n"
            "    Windows: https://github.com/UB-Mannheim/tesseract/wiki\n"
            "    Linux:   sudo apt-get install tesseract-ocr\n"
            "    macOS:   brew install tesseract",
            exc,
        )
        raise RuntimeError("Tesseract binary not available") from exc
    yield
    logger.info("OCR service shutting down.")


# ── FastAPI app ───────────────────────────────────────────────────────────────

app = FastAPI(
    title="GigLedger ML Service",
    description="OCR screenshot extraction + statistical anomaly detection for gig workers.",
    version="2.0.0",
    lifespan=lifespan,
)

# Allow requests from the React frontend (dev: localhost:5173, prod: configured separately)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # tighten this in production to specific frontend origin
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
def health_check():
    """
    Simple health endpoint. Spring Boot polls this to verify the service is up
    before forwarding real screenshot uploads.
    """
    return {"status": "ok", "service": "gigledger-ocr"}


@app.post("/ocr/extract", response_model=OcrResult)
async def extract_from_screenshot(
    file: UploadFile = File(..., description="Delivery-app screenshot (PNG/JPG/WEBP)"),
) -> OcrResult:
    """
    POST /ocr/extract

    Accepts a delivery-app screenshot as a multipart file upload.
    Returns the extracted fare amount, distance, and a confidence score.

    ### What happens internally
    1. Validate that the uploaded file is an image (check Content-Type).
    2. Read the raw bytes.
    3. Pass bytes to ocr_engine.process_image_bytes() which:
         - Decodes bytes → OpenCV BGR image
         - Preprocesses: grayscale → CLAHE contrast → Otsu threshold
         - Runs Tesseract with --oem 3 --psm 6
         - Applies regex to extract ₹/Rs. amounts and km distances
         - Computes a confidence score
    4. Return the structured JSON result.

    ### Error handling
    - Non-image file: HTTP 422 (Unprocessable Entity) with a clear message.
    - File too large: HTTP 413.
    - OCR finds nothing: HTTP 200 with confidence=0.0 and nulls — NOT a crash.
    - Tesseract fails: HTTP 500.
    """
    # ── 1. Validate content type ──────────────────────────────────────────────
    content_type = (file.content_type or "").lower()
    if content_type not in ALLOWED_CONTENT_TYPES:
        logger.warning("Rejected upload: content_type=%s filename=%s", content_type, file.filename)
        raise HTTPException(
            status_code=422,
            detail=(
                f"Unsupported file type: '{content_type}'. "
                f"Please upload a PNG, JPG, or WEBP screenshot."
            ),
        )

    # ── 2. Read bytes ─────────────────────────────────────────────────────────
    image_bytes = await file.read()

    if len(image_bytes) == 0:
        raise HTTPException(status_code=422, detail="Uploaded file is empty.")

    if len(image_bytes) > MAX_FILE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"File too large ({len(image_bytes) // 1024} KB). Max allowed: 10 MB.",
        )

    logger.info(
        "Processing screenshot: filename=%s  size=%d KB  content_type=%s",
        file.filename, len(image_bytes) // 1024, content_type,
    )

    # ── 3. Run OCR pipeline ───────────────────────────────────────────────────
    try:
        result = ocr_engine.process_image_bytes(image_bytes)
    except Exception as exc:
        # This should only happen if Tesseract crashes mid-run (not a bad image —
        # process_image_bytes handles bad images internally and returns confidence=0).
        logger.error("Unexpected OCR error: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail="OCR processing failed unexpectedly. Check server logs.",
        ) from exc

    # ── 4. Log result summary ─────────────────────────────────────────────────
    logger.info(
        "OCR result: amount=%.2f  distance=%s  confidence=%.2f",
        result["promised_amount"] or 0,
        result["distance_km"],
        result["confidence"],
    )

    return OcrResult(
        promised_amount=result["promised_amount"],
        distance_km=result["distance_km"],
        confidence=result["confidence"],
        raw_text=result["raw_text"],
    )


# ── Anomaly Detection endpoint ────────────────────────────────────────────────

@app.post("/analytics/detect-anomaly", response_model=list[AnomalyFlag])
def detect_anomaly(
    tasks: list[TaskInput],
) -> list[AnomalyFlag]:
    """
    POST /analytics/detect-anomaly

    Accepts a JSON array of a worker's task + payout history and returns
    an array of flagged periods where a sustained rate anomaly was detected.

    ### Request body
    A JSON array of task objects:
        [
          {
            "task_id":         "uuid-string",
            "promised_amount": 85.00,
            "actual_amount":   70.00,
            "distance_km":     5.5,
            "accepted_at":     "2025-02-01T14:30:00Z"
          },
          ...
        ]

    ### Response (200 OK)
    An array of flagged periods (may be empty):
        [
          {
            "period_start":  "2025-02-24",
            "period_end":    "2025-03-02",
            "bucket":        "peak",
            "baseline_rate": 16.55,
            "observed_rate": 12.90,
            "severity":      "low",
            "sd_below":      1.83
          }
        ]

    ### Empty array means "not enough data yet" — NOT an error.
    The caller should display a friendly "keep logging tasks" message
    rather than treating an empty response as a failure.

    ### Error handling
    - 422 if any task_input field fails validation (e.g. distance_km <= 0)
    - 500 if the detection engine crashes unexpectedly (check server logs)
    """
    logger.info("/analytics/detect-anomaly called with %d tasks", len(tasks))

    if not tasks:
        logger.info("Empty task list received — returning []")
        return []

    # Convert Pydantic models to plain dicts for the engine
    task_dicts = [
        {
            "task_id"        : t.task_id,
            "promised_amount": t.promised_amount,
            "actual_amount"  : t.actual_amount,
            "distance_km"    : t.distance_km,
            "accepted_at"    : t.accepted_at,
        }
        for t in tasks
    ]

    try:
        flags = anomaly_engine.detect_anomalies(task_dicts)
    except Exception as exc:
        logger.error("Anomaly detection failed: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail="Anomaly detection failed unexpectedly. Check server logs.",
        ) from exc

    logger.info("/analytics/detect-anomaly returning %d flag(s)", len(flags))
    return flags


@app.get("/analytics/fetch-policy")
def fetch_policy_updates():
    """
    Fetches raw government updates and policy news from PIB RSS & NewsAPI.
    Returns: JSON list of deduplicated updates.
    """
    logger.info("/analytics/fetch-policy triggered")
    try:
        articles = policy_fetcher.fetch_all_updates()
        logger.info("Successfully fetched %d articles", len(articles))
        return articles
    except Exception as exc:
        logger.error("Failed to fetch policy updates: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Failed to fetch policy updates: {str(exc)}"
        ) from exc


# ── Pydantic Request Models ───────────────────────────────────────────────────

class SummarizeRequest(BaseModel):
    raw_content: str

class ClassifyRequest(BaseModel):
    title: str
    content: str

class TranslateRequest(BaseModel):
    text: str
    lang_code: str

class ParseIntentRequest(BaseModel):
    transcript: str


# ── AI API Endpoints ───────────────────────────────────────────────────────────

@app.post("/analytics/classify")
def classify_article_endpoint(request: ClassifyRequest):
    """
    Classifies a news article into category, urgency, and provides reasoning.
    Returns: { category: str, urgency: str, reasoning: str }
    """
    logger.info("/analytics/classify triggered for title='%s'", request.title)
    return gemini_helper.classify_article(request.title, request.content)


@app.post("/analytics/summarize")
def summarize_article_endpoint(request: SummarizeRequest):
    """
    Summarizes raw government text in 2-3 sentences for gig workers.
    Returns: { summary: str, relevant: bool }
    """
    logger.info("/analytics/summarize triggered")
    return gemini_helper.summarize_article(request.raw_content)


@app.post("/analytics/translate")
def translate_text_endpoint(request: TranslateRequest):
    """
    Translates input text to target language code (e.g. 'ta', 'hi').
    Returns: { translated_text: str }
    """
    logger.info("/analytics/translate triggered for lang=%s", request.lang_code)
    translated = gemini_helper.translate_text(request.text, request.lang_code)
    return {"translated_text": translated}


@app.post("/voice/parse-intent")
def parse_voice_intent_endpoint(request: ParseIntentRequest):
    """
    Classifies a voice transcript into one of 5 target intents.
    Returns: { intent: str, entities: dict }
    """
    logger.info("/voice/parse-intent triggered for transcript='%s'", request.transcript)
    return gemini_helper.parse_voice_intent(request.transcript)


@app.post("/voice/transcribe")
async def transcribe_voice_endpoint(file: UploadFile = File(...)):
    """
    Accepts raw audio files, transcribes speech using Sarvam AI, and returns text.
    """
    logger.info("/voice/transcribe triggered for file=%s", file.filename)
    try:
        content = await file.read()
        res = sarvam_helper.transcribe_audio(content, file.filename)
        return res
    except Exception as exc:
        logger.error("Failed to transcribe audio: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Transcription failed: {str(exc)}"
        ) from exc


class NarrateRequest(BaseModel):
    text: str
    lang_code: str

@app.post("/voice/narrate")
def narrate_voice_endpoint(request: NarrateRequest):
    """
    Narrates input text into voice audio stream using Sarvam AI.
    Returns: wav/mp3 audio streaming response.
    """
    logger.info("/voice/narrate triggered for lang=%s text=%s", request.lang_code, request.text[:30])
    try:
        audio_bytes = sarvam_helper.narrate_text(request.text, request.lang_code)
        return Response(content=audio_bytes, media_type="audio/wav")
    except Exception as exc:
        logger.error("Failed to narrate text: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Speech synthesis failed: {str(exc)}"
        ) from exc


