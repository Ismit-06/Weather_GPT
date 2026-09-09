import os
import re
import base64
from pathlib import Path
import httpx
from dotenv import load_dotenv

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

SARVAM_TTS_URL = "https://api.sarvam.ai/text-to-speech"

# Mapping canonical language codes to optimal Sarvam Bulbul v3 speakers
LANGUAGE_SPEAKER_MAP = {
    "od-IN": "kavya",
    "hi-IN": "kavya",
    "te-IN": "kavitha",
    "ta-IN": "vijay",
    "bn-IN": "roopa",
    "kn-IN": "chaitra",
    "ml-IN": "kavya",
    "mr-IN": "ishita",
    "gu-IN": "pooja",
    "pa-IN": "anand",
    "en-IN": "kavya",
}

def resolve_target_language_code(language: str | None, text: str) -> str:
    if language:
        l = language.strip().lower()
        if "odia" in l or "oriya" in l or l.startswith("od") or l.startswith("or"):
            return "od-IN"
        if "hindi" in l or "hinglish" in l or l.startswith("hi"):
            return "hi-IN"
        if "telugu" in l or l.startswith("te"):
            return "te-IN"
        if "tamil" in l or l.startswith("ta"):
            return "ta-IN"
        if "bengali" in l or l.startswith("bn"):
            return "bn-IN"
        if "kannada" in l or l.startswith("kn"):
            return "kn-IN"
        if "malayalam" in l or l.startswith("ml"):
            return "ml-IN"
        if "marathi" in l or l.startswith("mr"):
            return "mr-IN"
        if "gujarati" in l or l.startswith("gu"):
            return "gu-IN"
        if "punjabi" in l or l.startswith("pa"):
            return "pa-IN"
        if "english" in l or l.startswith("en"):
            return "en-IN"

    # Script detection from text characters
    if re.search(r"[\u0B00-\u0B7F]", text):
        return "od-IN"
    if re.search(r"[\u0900-\u097F]", text):
        return "hi-IN"
    if re.search(r"[\u0C00-\u0C7F]", text):
        return "te-IN"
    if re.search(r"[\u0B80-\u0BFF]", text):
        return "ta-IN"
    if re.search(r"[\u0980-\u09FF]", text):
        return "bn-IN"
    if re.search(r"[\u0C80-\u0CFF]", text):
        return "kn-IN"
    if re.search(r"[\u0D00-\u0D7F]", text):
        return "ml-IN"
    if re.search(r"[\u0A80-\u0AFF]", text):
        return "gu-IN"
    if re.search(r"[\u0A00-\u0A7F]", text):
        return "pa-IN"
        
    return "en-IN"


# Common Indian city transliterations for Odia, Hindi, Telugu to prevent TTS skips
CITY_TRANSLITERATION = {
    "od-IN": {
        r"(?i)\bvijayawada\b": "ବିଜୟୱାଡ଼ା",
        r"(?i)\bamaravati\b": "ଅମରାବତୀ",
        r"(?i)\bbhubaneswar\b": "ଭୁବନେଶ୍ୱର",
        r"(?i)\bcuttack\b": "କଟକ",
        r"(?i)\bpuri\b": "ପୁରୀ",
        r"(?i)\bdelhi\b": "ଦିଲ୍ଲୀ",
        r"(?i)\bmumbai\b": "ମୁମ୍ବାଇ",
        r"(?i)\bkolkata\b": "କୋଲକାତା",
        r"(?i)\bchennai\b": "ଚେନ୍ନାଇ",
        r"(?i)\bhyderabad\b": "ହାଇଦ୍ରାବାଦ",
        r"(?i)\bbengaluru\b|\bbangalore\b": "ବେଙ୍ଗାଲୁରୁ",
    },
    "hi-IN": {
        r"(?i)\bvijayawada\b": "विजयवाड़ा",
        r"(?i)\bamaravati\b": "अमरावती",
        r"(?i)\bbhubaneswar\b": "भुवनेश्वर",
        r"(?i)\bcuttack\b": "कटक",
        r"(?i)\bpuri\b": "पुरी",
        r"(?i)\bdelhi\b": "दिल्ली",
        r"(?i)\bmumbai\b": "मुंबई",
        r"(?i)\bkolkata\b": "कोलकाता",
        r"(?i)\bchennai\b": "चेन्नई",
        r"(?i)\bhyderabad\b": "हैदराबाद",
        r"(?i)\bbengaluru\b|\bbangalore\b": "बेंगलुरु",
    },
    "te-IN": {
        r"(?i)\bvijayawada\b": "విజయవాడ",
        r"(?i)\bamaravati\b": "అమరావతి",
        r"(?i)\bbhubaneswar\b": "భువనేశ్వర్",
        r"(?i)\bhyderabad\b": "హైదరాబాద్",
        r"(?i)\bdelhi\b": "ఢిల్లీ",
        r"(?i)\bmumbai\b": "ముంబై",
        r"(?i)\bchennai\b": "చెన్నై",
        r"(?i)\bbengaluru\b|\bbangalore\b": "బెంగళూరు",
    }
}


def prepare_text_for_indian_tts(text: str, target_lang_code: str) -> str:
    from app.services.openrouter_tts import sanitize_text_for_tts
    cleaned = sanitize_text_for_tts(text)
    
    # Apply city name transliteration for target script
    trans_map = CITY_TRANSLITERATION.get(target_lang_code, {})
    for pattern, replacement in trans_map.items():
        cleaned = re.sub(pattern, replacement, cleaned)
        
    return cleaned.strip()


async def synthesize_speech_sarvam(
    text: str,
    language: str | None = None,
) -> bytes:
    """
    Synthesizes ultra-natural, human-grade speech using Sarvam Bulbul v3.
    """
    api_key = os.getenv("SARVAM_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("SARVAM_API_KEY is not configured.")

    target_lang_code = resolve_target_language_code(language, text)
    clean_text = prepare_text_for_indian_tts(text, target_lang_code)
    if not clean_text:
        raise ValueError("Input text is empty after sanitization.")

    speaker = LANGUAGE_SPEAKER_MAP.get(target_lang_code, "kavya")

    headers = {
        "api-subscription-key": api_key,
        "Content-Type": "application/json",
    }

    payload = {
        "inputs": [clean_text],
        "target_language_code": target_lang_code,
        "speaker": speaker,
        "model": "bulbul:v3",
    }

    async with httpx.AsyncClient(timeout=25.0) as client:
        response = await client.post(
            SARVAM_TTS_URL,
            headers=headers,
            json=payload,
        )
        if response.status_code != 200:
            raise RuntimeError(f"Sarvam TTS failed ({response.status_code}): {response.text}")

        data = response.json()
        audios = data.get("audios", [])
        if not audios or not audios[0]:
            raise RuntimeError("Sarvam TTS returned empty audio payload.")

        return base64.b64decode(audios[0])
