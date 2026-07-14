# ─────────────────────────────────────────────────────────────────────────────
#  GigLedger OCR Microservice — Dockerfile
# ─────────────────────────────────────────────────────────────────────────────
#
#  Build:    docker build -t gigledger-ocr .
#  Run:      docker run -p 8001:8001 gigledger-ocr
#
#  IMPORTANT — READ THIS BEFORE MODIFYING:
#  ════════════════════════════════════════
#  This Dockerfile installs TWO separate things for OCR to work:
#
#    1. tesseract-ocr  (via apt-get)  ← THE SYSTEM BINARY — Tesseract itself.
#                                       This is what actually reads text from images.
#                                       WITHOUT this, OCR will not work at all.
#
#    2. pytesseract    (via pip)       ← A Python WRAPPER that calls the binary above.
#                                       This is NOT Tesseract — it just lets Python
#                                       invoke the tesseract command.
#
#  The most common mistake when Dockerising Tesseract apps is installing
#  pytesseract (pip) but forgetting tesseract-ocr (apt-get). This results in:
#  "TesseractNotFound: tesseract is not installed or it's not in PATH"
#
# ─────────────────────────────────────────────────────────────────────────────

# Use Python 3.11 slim (smaller image, Debian Bullseye base)
FROM python:3.11-slim

# Metadata
LABEL maintainer="GigLedger"
LABEL description="OCR microservice for delivery-app fare extraction"

# ── 1. Install system dependencies ───────────────────────────────────────────
#
#  tesseract-ocr        = the OCR binary (REQUIRED — see note above)
#  tesseract-ocr-eng    = English language data (needed for fare text)
#  libglib2.0-0         = runtime dependency for OpenCV
#  libgl1               = OpenCV graphics library dependency
#
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    libglib2.0-0 \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

# Verify Tesseract was installed correctly (build will fail here if not)
RUN tesseract --version

# ── 2. Set working directory ──────────────────────────────────────────────────
WORKDIR /app

# ── 3. Install Python dependencies ───────────────────────────────────────────
#  Copy requirements first (Docker layer caching — if requirements haven't
#  changed, this layer is reused and the slow pip install is skipped)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ── 4. Copy application code ──────────────────────────────────────────────────
COPY ocr_engine.py .
COPY main.py .

# ── 5. Expose port and start service ─────────────────────────────────────────
EXPOSE 8001

# uvicorn flags:
#   --host 0.0.0.0   listen on all interfaces (required inside Docker)
#   --port 8001      port the service runs on
#   --workers 1      single worker (OCR is CPU-bound; multiple workers on one
#                    container usually hurts more than it helps — scale by
#                    running multiple containers instead)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "1"]
