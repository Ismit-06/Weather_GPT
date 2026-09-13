import os
from pathlib import Path
import httpx
from dotenv import load_dotenv

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

SARVAM_LID_URL = "https://api.sarvam.ai/text-lid"
SARVAM_TRANSLATE_URL = "https://api.sarvam.ai/translate"

LANGUAGE_CODE_MAP = {
    "odia": "od-IN",
    "oriya": "od-IN",
    "od-in": "od-IN",
    "or-in": "od-IN",
    "or": "od-IN",
    "hindi": "hi-IN",
    "hi-in": "hi-IN",
    "hi": "hi-IN",
    "hinglish": "hi-IN",
    "bengali": "bn-IN",
    "bn-in": "bn-IN",
    "bn": "bn-IN",
    "gujarati": "gu-IN",
    "gu-in": "gu-IN",
    "gu": "gu-IN",
    "kannada": "kn-IN",
    "kn-in": "kn-IN",
    "kn": "kn-IN",
    "malayalam": "ml-IN",
    "ml-in": "ml-IN",
    "ml": "ml-IN",
    "marathi": "mr-IN",
    "mr-in": "mr-IN",
    "mr": "mr-IN",
    "punjabi": "pa-IN",
    "pa-in": "pa-IN",
    "pa": "pa-IN",
    "tamil": "ta-IN",
    "ta-in": "ta-IN",
    "ta": "ta-IN",
    "telugu": "te-IN",
    "te-in": "te-IN",
    "te": "te-IN",
    "english": "en-IN",
    "en-in": "en-IN",
    "en": "en-IN",
}

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

def resolve_sarvam_code(lang_str: str) -> str:
    if not lang_str:
        return "en-IN"
    clean = lang_str.strip().lower()
    return LANGUAGE_CODE_MAP.get(clean, "en-IN")

async def detect_language(text: str) -> dict:
    """Detects language of input text using Sarvam AI LID."""
    api_key = os.getenv("SARVAM_API_KEY", "").strip()
    if not api_key:
        return {"language_code": "en-IN", "language": "English"}

    payload = {"input": text[:1000]}
    headers = {
        "api-subscription-key": api_key,
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                SARVAM_LID_URL,
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
            data = response.json()

        code = data.get("language_code") or "en-IN"
        return {
            "language_code": code,
            "language": LANGUAGE_NAMES.get(code, "English"),
            "script_code": data.get("script_code"),
        }
    except Exception:
        return {"language_code": "en-IN", "language": "English"}

async def translate_with_sarvam(
    text: str,
    target_language: str,
    source_language: str = "en-IN",
) -> str:
    """Translates text into native Indian language using Sarvam AI translation."""
    if not text.strip():
        return text

    target_code = resolve_sarvam_code(target_language)
    if target_code == "en-IN" and source_language == "en-IN":
        return text

    api_key = os.getenv("SARVAM_API_KEY", "").strip()
    if not api_key:
        return text

    payload = {
        "input": text,
        "source_language_code": source_language,
        "target_language_code": target_code,
        "mode": "formal",
    }
    headers = {
        "api-subscription-key": api_key,
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=12.0) as client:
            response = await client.post(
                SARVAM_TRANSLATE_URL,
                headers=headers,
                json=payload,
            )
            if response.status_code == 200:
                data = response.json()
                translated = data.get("translated_text")
                if translated and translated.strip():
                    return translated.strip()
    except Exception:
        pass

    return text
