import os
import io
import wave
from pathlib import Path
import httpx
from dotenv import load_dotenv

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

SARVAM_STT_URL = "https://api.sarvam.ai/speech-to-text"

def pcm_to_wav(pcm_bytes: bytes, sample_rate: int = 16000, channels: int = 1, sample_width: int = 2) -> bytes:
    """Wraps raw 16-bit PCM bytes into standard WAV format."""
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(channels)
        wf.setsampwidth(sample_width)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm_bytes)
    return buf.getvalue()

async def transcribe_audio_stream(
    pcm_bytes: bytes,
    language_code: str = "unknown",
) -> dict:
    """
    Transcribes audio using Sarvam AI Saaras v3 ASR.
    Supports auto Indian language detection and code-switching (Hinglish/Tanglish).
    """
    api_key = os.getenv("SARVAM_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("SARVAM_API_KEY is not configured.")

    wav_data = pcm_to_wav(pcm_bytes)

    files = {
        "file": (
            "audio.wav",
            wav_data,
            "audio/wav",
        )
    }

    # Normalize language code for Sarvam ASR
    lang = language_code if language_code and language_code != "auto" else "unknown"

    data = {
        "model": "saaras:v3",
        "mode": "transcribe",
        "language_code": lang,
    }

    headers = {
        "api-subscription-key": api_key,
    }

    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            SARVAM_STT_URL,
            headers=headers,
            data=data,
            files=files,
        )
        if response.status_code != 200:
            raise RuntimeError(f"Sarvam STT returned status {response.status_code}: {response.text}")

        result = response.json()

    transcript = result.get("transcript", "").strip()
    detected_code = result.get("language_code", "en-IN")

    return {
        "transcript": transcript,
        "language_code": detected_code,
        "request_id": result.get("request_id"),
    }

