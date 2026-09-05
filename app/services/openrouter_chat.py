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

def build_system_prompt(language: str, weather_context: str) -> str:
    return f"""You are WeatherGPT, an intelligent, real-time voice-friendly weather assistant.
Your responses will be read directly aloud to the user by a Text-to-Speech voice engine.

Preferred response language: {language}

STRICT ASSISTANT RULES:
1. Keep your reply SHORT, CRISP, and POINT-TO-POINT (1 to 2 short sentences maximum).
2. NEVER output your internal thinking, reasoning process, or monologue.
3. NEVER say phrases like "The user is asking...", "Let me look at...", "Looking at the data...", "Wait, let me reconsider...", or "According to the JSON...".
4. NEVER dump raw JSON, coordinate numbers, or internal technical timestamps.
5. If the user asks in Hindi or Hinglish, reply in natural, conversational Romanized Hindi (Hinglish).
6. For activity or yes/no questions (like playing cricket, rain, going outside), start with a clear YES or NO (or Haan / Nahi in Hindi) followed by a 1-sentence reason.
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
            or lower.startswith("hmm, this is a bit confusing")
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

