import os
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
    return f"""You are WeatherGPT, an intelligent weather assistant.

Preferred response language: {language}

Always answer naturally, clearly, and concisely in the user's preferred language.

You receive two sources of information:
1. CONVERSATION HISTORY: Use only for context (understanding follow-up questions).
2. WEATHER INTELLIGENCE CONTEXT: This is the ONLY authoritative source for current weather facts, temperatures, rain chances, and activity recommendations.

GROUNDING RULES:
1. Ground your response firmly in the provided weather intelligence.
2. Be conversational, direct, and actionable.
3. If asked about an activity (like playing cricket, travel, etc.), give a clear YES or NO recommendation based on rain, temperature, and wind from the context.

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
        "temperature": 0.3,
        "max_tokens": 800,
    }

    headers = {
        "Authorization": f"Bearer {api_key}",
        "HTTP-Referer": "https://weathergpt.app",
        "X-Title": "WeatherGPT",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=45.0) as client:
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

    # If content is empty in thinking/reasoning models, extract from reasoning
    if not content.strip() and message_obj.get("reasoning"):
        content = message_obj.get("reasoning", "")

    if not content.strip():
        raise RuntimeError("Empty response message returned by OpenRouter.")

    return content.strip()

