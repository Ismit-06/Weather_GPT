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
    """Cleans text of thinking tags, all emojis, markdown, and converts units/ranges for natural speech."""
    if not text:
        return ""
    
    # 1. Remove thinking tags and internal reasoning
    if "<think>" in text:
        text = re.sub(r"<think>[\s\S]*?</think>", "", text).strip()
    
    # 2. Remove all Emoji characters & symbols across Unicode blocks
    # Surrogate pairs, SMP pictographs, weather dingbats, miscellaneous symbols
    emoji_pattern = re.compile(
        "["
        "\U0001F000-\U0001FAFF"  # Extended Pictographic, Emoticons, Symbols
        "\U0001F300-\U0001F5FF"  # Misc Symbols and Pictographs
        "\U0001F600-\U0001F64F"  # Emoticons
        "\U0001F680-\U0001F6FF"  # Transport and Map
        "\U0001F700-\U0001F77F"  # Alchemical
        "\U0001F780-\U0001F7FF"  # Geometric
        "\U0001F800-\U0001F8FF"  # Supplemental Arrows
        "\U0001F900-\U0001F9FF"  # Supplemental Symbols
        "\U0001FA00-\U0001FA6F"  # Chess / Symbols
        "\U0001FA70-\U0001FAFF"  # Symbols Extended
        "\u2600-\u27BF"          # Weather & Misc symbols (sun, umbrella, cloud, etc.)
        "\u2300-\u23FF"          # Misc Technical
        "\u2B50"                 # Star
        "\uFE00-\uFE0F"          # Variation selectors
        "]+",
        flags=re.UNICODE
    )
    text = emoji_pattern.sub("", text)
    
    # 3. Natural conversational phrasing for time and number ranges
    # E.g. "4–5 PM" or "4-6 PM" or "3:30–5:30" -> "4 to 5 PM"
    text = re.sub(r"(\d+(?::\d+)?)\s*[–—\-]\s*(\d+(?::\d+)?)\s*(AM|PM|am|pm|baje)?\b", r"\1 to \2 \3", text)
    
    # 4. Expand units for natural phonetic speaking
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 degrees Celsius", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\s*F\b", r"\1 degrees Fahrenheit", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\b", r"\1 degrees", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*km/h\b", r"\1 kilometers per hour", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*m/s\b", r"\1 meters per second", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*mm\b", r"\1 millimeters", text)
    text = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*%\b", r"\1 percent", text)
    
    # 5. Remove all markdown formatting symbols and headers
    text = re.sub(r"[#*_`~>\[\]()|]", " ", text)
    text = re.sub(r"^\s*[-•]\s*", "", text, flags=re.MULTILINE)
    text = re.sub(r"[-=]{2,}", " ", text)
    text = re.sub(r"\bhttps?://\S+", "", text)
    
    # 6. Clean whitespace and excess punctuation
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"\.{2,}", ".", text)
    text = re.sub(r"\s+([.,!?])", r"\1", text)
    
    return text.strip()

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
