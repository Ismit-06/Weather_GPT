import os
import re
from pathlib import Path
import httpx
from dotenv import load_dotenv

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "inclusionai/ling-3.0-flash-fin:free"

def get_language_instruction(language: str) -> tuple[str, str]:
    """Returns (canonical_language_name, mandatory_instruction)"""
    l = (language or "English").strip().lower()
    if any(k in l for k in ["odia", "oriya", "od-in", "or-in", "or"]):
        return "Odia", "CRITICAL: You MUST reply in authentic ODIA (ଓଡ଼ିଆ script or natural conversational Odia). Do NOT reply in English or Hindi. For yes/no questions, start with 'ହଁ' (Haan) or 'ନାହିଁ' (Naahin)."
    if any(k in l for k in ["hinglish"]):
        return "Hinglish", "CRITICAL: You MUST reply in conversational Romanized Hindi (Hinglish). Use simple Hindi words written in English alphabet. For yes/no questions, start with 'Haan' or 'Nahi'."
    if any(k in l for k in ["hindi", "hi-in", "hi"]):
        return "Hindi", "CRITICAL: You MUST reply in HINDI (हिन्दी or natural conversational Hinglish). Do NOT reply in English. For yes/no questions, start with 'हाँ' or 'नहीं'."
    if any(k in l for k in ["telugu", "te-in", "te"]):
        return "Telugu", "CRITICAL: You MUST reply in TELUGU (తెలుగు). Do NOT reply in English. For yes/no questions, start with 'అవును' or 'కాదు'."
    if any(k in l for k in ["tamil", "ta-in", "ta"]):
        return "Tamil", "CRITICAL: You MUST reply in TAMIL (தமிழ்). Do NOT reply in English. For yes/no questions, start with 'ஆம்' or 'இல்லை'."
    if any(k in l for k in ["bengali", "bn-in", "bn"]):
        return "Bengali", "CRITICAL: You MUST reply in BENGALI (বাংলা). Do NOT reply in English."
    if any(k in l for k in ["marathi", "mr-in", "mr"]):
        return "Marathi", "CRITICAL: You MUST reply in MARATHI (मराठी). Do NOT reply in English."
    if any(k in l for k in ["gujarati", "gu-in", "gu"]):
        return "Gujarati", "CRITICAL: You MUST reply in GUJARATI (ગુજરાતી). Do NOT reply in English."
    if any(k in l for k in ["kannada", "kn-in", "kn"]):
        return "Kannada", "CRITICAL: You MUST reply in KANNADA (ಕನ್ನಡ). Do NOT reply in English."
    if any(k in l for k in ["malayalam", "ml-in", "ml"]):
        return "Malayalam", "CRITICAL: You MUST reply in MALAYALAM (മലയാളം). Do NOT reply in English."
    if any(k in l for k in ["punjabi", "pa-in", "pa"]):
        return "Punjabi", "CRITICAL: You MUST reply in PUNJABI (ਪੰਜਾਬੀ). Do NOT reply in English."
    if "auto" in l:
        return "Auto-Detect", "CRITICAL: Detect the user's input language and reply in the EXACT SAME LANGUAGE and script (Odia, Hindi, Telugu, Tamil, Bengali, or English)."
    return language.capitalize(), f"CRITICAL: You MUST reply in {language}."

def build_system_prompt(language: str, weather_context: str) -> str:
    lang_name, lang_mandate = get_language_instruction(language)
    return f"""You are WeatherGPT, an intelligent, real-time voice-first weather assistant.
Your responses will be read directly aloud to the user by a Text-to-Speech voice engine.

RESPONSE LANGUAGE: {lang_name}
{lang_mandate}

STRICT VOICE ASSISTANT RULES:
1. Speak directly to the user as a real-time voice assistant.
2. Keep your reply SHORT, CRISP, and POINT-TO-POINT (1 to 2 short sentences maximum, under 35 words).
3. Directly answer the user's question in the very first sentence (e.g. Yes/No, exact temperature, or rain timing).
4. NEVER output your internal thinking, reasoning process, or monologue. Output ONLY the clean response to be spoken aloud.
5. NEVER say phrases like "The user is asking...", "Let me look at...", "Looking at the data...", "Wait, let me reconsider...", or "According to the JSON...".
6. NEVER dump raw JSON, coordinate numbers, or internal technical timestamps.
7. Base your answer strictly on the weather intelligence provided below.

WEATHER INTELLIGENCE:
{weather_context}
"""

async def chat(
    question: str,
    language: str,
    weather_context: str,
    history: list[dict] | None = None,
) -> str:
    api_key = os.getenv("OPENROUTER_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("OPENROUTER_API_KEY is not configured.")

    model = os.getenv("OPENROUTER_MODEL", DEFAULT_MODEL).strip() or DEFAULT_MODEL

    messages = [
        {
            "role": "system",
            "content": build_system_prompt(
                language=language,
                weather_context=weather_context,
            ),
        }
    ]

    if history:
        for item in history[-6:]:
            if isinstance(item, dict) and "role" in item and "content" in item:
                messages.append({
                    "role": item["role"],
                    "content": str(item["content"]),
                })

    messages.append({
        "role": "user",
        "content": question,
    })

    payload = {
        "model": model,
        "messages": messages,
        "temperature": 0.2,
        "max_tokens": 150,
        "reasoning": {"effort": "none"},
    }

    headers = {
        "Authorization": f"Bearer {api_key}",
        "HTTP-Referer": "https://weathergpt.app",
        "X-Title": "WeatherGPT",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=35.0) as client:
        response = await client.post(
            OPENROUTER_URL,
            headers=headers,
            json=payload,
        )
        response.raise_for_status()
        data = response.json()

    choices = data.get("choices", [])
    if not choices:
        raise RuntimeError("No response choices returned by OpenRouter.")

    message_obj = choices[0].get("message", {})
    content = message_obj.get("content") or ""

    # Strip reasoning tags if present
    if "<think>" in content:
        content = re.sub(r"<think>[\s\S]*?</think>", "", content).strip()

    # Filter out any internal monologue lines that might leak through
    raw_lines = [line.strip() for line in content.split("\n") if line.strip()]
    cleaned_lines = []
    for line in raw_lines:
        lower = line.lower()
        if (
            lower.startswith("the user is asking")
            or lower.startswith("let me look at")
            or lower.startswith("looking at the data")
            or lower.startswith("wait, let me reconsider")
            or lower.startswith("hmm")
            or lower.startswith("i think i'm")
            or lower.startswith("let me just respond")
            or lower.startswith("i'll respond in")
            or lower.startswith("the user has been communicating")
            or "overthinking" in lower
            or "respond naturally" in lower
            or "weather advisory or committee" in lower
            or ("could it be" in lower and lower.endswith("?"))
            or lower.startswith("the weather data provided is")
        ):
            continue
        cleaned_lines.append(line)

    if cleaned_lines:
        content = " ".join(cleaned_lines)
    else:
        content = ""

    if not content.strip():
        raise RuntimeError("Empty clean response returned by OpenRouter.")

    return content.strip()

