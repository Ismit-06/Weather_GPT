import os
from pathlib import Path

import httpx
from dotenv import load_dotenv


load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)


SARVAM_STT_URL = "https://api.sarvam.ai/speech-to-text"


async def transcribe_audio(
    audio_bytes: bytes,
    filename: str,
    language_code: str = "unknown",
) -> dict:

    api_key = os.getenv(
        "SARVAM_API_KEY",
        ""
    ).strip()

    if not api_key:
        raise RuntimeError(
            "SARVAM_API_KEY is not configured."
        )

    files = {
        "file": (
            filename,
            audio_bytes,
            "audio/mp4",
        )
    }

    data = {
        "model": "saaras:v3",
        "mode": "transcribe",
        "language_code": language_code,
    }

    headers = {
        "api-subscription-key": api_key,
    }

    async with httpx.AsyncClient(
        timeout=45.0
    ) as client:

        response = await client.post(
            SARVAM_STT_URL,
            headers=headers,
            data=data,
            files=files,
        )

        print(
            "Sarvam STT HTTP:",
            response.status_code
        )

        response.raise_for_status()

        result = response.json()

    return {
        "transcript":
            result.get("transcript", ""),

        "language_code":
            result.get("language_code"),

        "request_id":
            result.get("request_id"),
    }
