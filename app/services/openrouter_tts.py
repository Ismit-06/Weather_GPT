import os
import re
from pathlib import Path
import httpx
from dotenv import load_dotenv

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

OPENROUTER_TTS_URL = "https://openrouter.ai/api/v1/audio/speech"
TTS_MODEL = "fish-audio/s2.1-pro-free:free"

def sanitize_text_for_tts(text: str) -> str:
    """Cleans text of thinking tags, emojis, markdown, and converts units for natural speech."""
    if not text:
        return ""
    
    # Remove thinking tags
    if "<think>" in text:
        text = re.sub(r"<think>[\s\S]*?</think>", "", text).strip()
    
    # Remove emojis
    text = re.sub(r"[\U00010000-\U0010ffff\u2600-\u27bf\ufe00-\ufe0f]", "", text)
    
    # Expand units
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 degrees Celsius", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\s*F\b", r"\1 degrees Fahrenheit", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\b", r"\1 degrees", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*km/h\b", r"\1 kilometers per hour", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*m/s\b", r"\1 meters per second", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*mm\b", r"\1 millimeters", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*%\b", r"\1 percent", text)
    
    # Remove markdown symbols
    text = re.sub(r"[*#_`~>\[\]()]", " ", text)
    text = re.sub(r"^\s*[-•]\s*", "", text, flags=re.MULTILINE)
    text = re.sub(r"\bhttps?://\S+", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    
    return text

async def synthesize_speech_openrouter(
    text: str,
    response_format: str = "mp3"
) -> bytes:
    """
    Synthesizes neural speech using fish-audio/s2.1-pro-free:free via OpenRouter.
    Returns raw audio bytes (audio/mpeg).
    """
    api_key = os.getenv("OPENROUTER_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("OPENROUTER_API_KEY is not configured.")

    clean_text = sanitize_text_for_tts(text)
    if not clean_text:
        raise ValueError("Input text is empty after sanitization.")

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://weathergpt.app",
        "X-Title": "WeatherGPT",
    }

    payload = {
        "model": TTS_MODEL,
        "input": clean_text,
        "response_format": response_format,
    }

    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            OPENROUTER_TTS_URL,
            headers=headers,
            json=payload,
        )
        if response.status_code != 200:
            raise RuntimeError(
                f"OpenRouter TTS failed ({response.status_code}): {response.text}"
            )
        
        return response.content
