import os
import urllib.request
import urllib.error
import struct
import json

# Configure Sarvam AI API Key
sarvam_api_key = os.getenv("SARVAM_API_KEY")
use_sarvam = bool(sarvam_api_key)

if use_sarvam:
    print("Sarvam AI API successfully configured from environment.")
else:
    print("Sarvam AI API key not found in environment. Using local audio/mock fallbacks.")

def generate_silent_wav(duration_seconds=1, sample_rate=8000):
    """
    Generates valid silent mono 16-bit PCM WAV audio bytes.
    Avoids requiring ffmpeg or external audio codecs in mock mode.
    """
    num_samples = duration_seconds * sample_rate
    data_size = num_samples * 2  # 16-bit = 2 bytes per sample
    
    # WAV Header (RIFF / fmt / data chunks)
    header = struct.pack(
        '<4sI4s4sIHHIIHH4sI',
        b'RIFF',
        36 + data_size,
        b'WAVE',
        b'fmt ',
        16,       # Subchunk1Size
        1,        # AudioFormat (PCM)
        1,        # NumChannels (Mono)
        sample_rate,
        sample_rate * 2, # ByteRate
        2,        # BlockAlign
        16,       # BitsPerSample
        b'data',
        data_size
    )
    data = b'\x00' * data_size
    return header + data

def transcribe_audio(file_bytes: bytes, filename: str) -> dict:
    if use_sarvam:
        try:
            # Construct a boundary for multipart form data
            boundary = b'----GigLedgerVoiceBoundary'
            parts = []
            
            # Add file field
            parts.append(b'--' + boundary)
            parts.append(f'Content-Disposition: form-data; name="file"; filename="{filename}"'.encode('utf-8'))
            parts.append(b'Content-Type: audio/wav') # default type
            parts.append(b'')
            parts.append(file_bytes)
            
            # Add model field
            parts.append(b'--' + boundary)
            parts.append(b'Content-Disposition: form-data; name="model"')
            parts.append(b'')
            parts.append(b'saaras:v3')
            
            # Add language field (Tamil hint as requested)
            parts.append(b'--' + boundary)
            parts.append(b'Content-Disposition: form-data; name="language_code"')
            parts.append(b'')
            parts.append(b'ta-IN')
            
            # Close boundary
            parts.append(b'--' + boundary + b'--')
            parts.append(b'')
            
            body = b'\r\n'.join(parts)
            
            url = "https://api.sarvam.ai/speech-to-text"
            req = urllib.request.Request(url, data=body)
            req.add_header('Content-Type', f'multipart/form-data; boundary={boundary.decode("utf-8")}')
            req.add_header('api-subscription-key', sarvam_api_key)
            
            with urllib.request.urlopen(req, timeout=15) as response:
                res_data = json.loads(response.read().decode('utf-8'))
                print("RAW STT Response from Sarvam AI:", json.dumps(res_data, indent=2))
                
            transcript = res_data.get("transcript", "").strip()
            if not transcript:
                return {"transcript": "COULD_NOT_UNDERSTAND", "language_detected": "ta-IN", "confidence": 0.0}
            return {
                "transcript": transcript,
                "language_detected": res_data.get("language_code", "ta-IN"),
                "confidence": float(res_data.get("confidence", 0.90))
            }
        except Exception as e:
            print(f"Sarvam STT API call failed: {e}. Falling back to mock transcriber.")
            
    # Mock / Rule-based Transcriber based on filenames or mock markers
    fn_lower = filename.lower()
    if "payout" in fn_lower:
        return {"transcript": "log a payout of 120 rupees", "language_detected": "ta-IN", "confidence": 0.98}
    if "task" in fn_lower or "delivery" in fn_lower:
        return {"transcript": "log a task of 100 rupees and 6.5 kilometers", "language_detected": "ta-IN", "confidence": 0.95}
    if "today" in fn_lower or "earn" in fn_lower:
        return {"transcript": "how much did I earn today?", "language_detected": "ta-IN", "confidence": 0.99}
    if "fair" in fn_lower or "score" in fn_lower:
        return {"transcript": "what is my fairness score?", "language_detected": "ta-IN", "confidence": 0.97}
    if "policy" in fn_lower or "news" in fn_lower:
        return {"transcript": "read the latest policy update", "language_detected": "ta-IN", "confidence": 0.96}
    if "unknown" in fn_lower:
        return {"transcript": "COULD_NOT_UNDERSTAND", "language_detected": "ta-IN", "confidence": 0.0}
        
    return {"transcript": "log a payout of 150 rupees", "language_detected": "ta-IN", "confidence": 0.95}

def narrate_text(text: str, lang_code: str) -> bytes:
    """
    Synthesizes speech for the input text in the target language.
    Returns: wav/mp3 audio bytes.
    """
    if use_sarvam:
        try:
            # Map simple ISO codes to Sarvam language locale strings
            sarvam_lang = "ta-IN" if lang_code == "ta" else "hi-IN" if lang_code == "hi" else "en-IN"
            
            payload = {
                "inputs": [text],
                "target_language_code": sarvam_lang,
                "speaker": "vidya",
                "pitch": 0,
                "pace": 1.15,
                "loudness": 1.5,
                "speech_fmt": "wav"
            }
            
            url = "https://api.sarvam.ai/text-to-speech"
            data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(url, data=data, headers={
                'Content-Type': 'application/json',
                'api-subscription-key': sarvam_api_key
            })
            
            with urllib.request.urlopen(req, timeout=15) as response:
                res_data = json.loads(response.read().decode('utf-8'))
                
            # Sarvam returns audio encoded as base64 string
            audio_b64 = res_data.get("audios", [""])[0]
            import base64
            return base64.b64decode(audio_b64)
        except Exception as e:
            print(f"Sarvam TTS API failed: {e}. Generating silent WAV placeholder.")
            
    # Mock fallback: Generate a silent WAV file that browsers can decode and play
    return generate_silent_wav(duration_seconds=2)
