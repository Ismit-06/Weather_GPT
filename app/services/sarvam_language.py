import os
from pathlib import Path

import httpx
from dotenv import load_dotenv


load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)


SARVAM_LID_URL = "https://api.sarvam.ai/text-lid"


LANGUAGE_NAMES = {
    "en-IN": "English",
    "hi-IN": "Hindi",
    "bn-IN": "Bengali",
    "gu-IN": "Gujarati",
    "kn-IN": "Kannada",
    "ml-IN": "Malayalam",
    "mr-IN": "Marathi",
    "od-IN": "Odia",
    "pa-IN": "Punjabi",
    "ta-IN": "Tamil",
    "te-IN": "Telugu",
}


async def detect_language(
    text: str
) -> dict:

    api_key = os.getenv(
        "SARVAM_API_KEY",
        ""
    ).strip()

    if not api_key:
        raise RuntimeError(
            "SARVAM_API_KEY is not configured."
        )

    payload = {
        "input": text[:1000]
    }

    headers = {
        "api-subscription-key": api_key,
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(
        timeout=20.0
    ) as client:

        response = await client.post(
            SARVAM_LID_URL,
            headers=headers,
            json=payload,
        )

        response.raise_for_status()

        data = response.json()

    code = data.get(
        "language_code"
    ) or "en-IN"

    return {
        "language_code": code,
        "language": LANGUAGE_NAMES.get(
            code,
            "English"
        ),
        "script_code": data.get(
            "script_code"
        ),
    }
