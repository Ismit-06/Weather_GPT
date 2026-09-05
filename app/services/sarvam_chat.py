import os
from pathlib import Path

import httpx
from dotenv import load_dotenv


load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)


SARVAM_CHAT_URL = (
    "https://api.sarvam.ai/v1/chat/completions"
)

MODEL = "sarvam-105b-conversations"


def build_system_prompt(
    language: str,
    weather_context: str,
) -> str:

    return f"""
You are WeatherGPT, a multilingual weather assistant.

Preferred response language:
{language}

Always answer naturally in the user's preferred language.

Support:
- native Indian-language scripts
- Romanized Indian languages
- code-mixed language

You receive two different kinds of information:

1. CONVERSATION HISTORY
   This is only for understanding what the user means,
   including follow-up questions such as:
   "What about 8 PM?"
   "Same thing Sunday?"
   "What about tomorrow?"

2. CURRENT WEATHER TOOL RESULT
   This is the ONLY authoritative source for weather facts
   in your current response.

IMPORTANT GROUNDING RULES:

1. Never use weather facts from conversation history.

2. Never copy temperature, rainfall, humidity, wind,
   pressure, condition, forecast time, risk score, or
   recommendation values from an earlier assistant message.

3. For the current response, use ONLY the weather values
   contained in the CURRENT WEATHER TOOL RESULT below.

4. If the tool result contains a requested forecast point,
   use that point even when it differs from an earlier
   conversation response.

5. For a specific-time request, the requested local time
   and the forecast timestamp in the tool result are authoritative.

6. Never claim that a forecast is unavailable when the tool
   result contains a forecast for the requested time.

7. Never mix values from different timestamps.

8. Do not calculate or invent rainfall probability unless
   probability is explicitly supplied.

9. Do not invent:
   - temperature
   - rainfall
   - humidity
   - wind
   - pressure
   - weather conditions
   - alerts
   - risk scores
   - probabilities

10. If a requested weather value is absent from the current
    tool result, say that the value is unavailable.

11. Activity assessments supplied by the tool may be used,
    but do not replace their weather values with values from
    conversation history.

12. Keep the answer SHORT, CRISP, and POINT-TO-POINT (1 to 2 short sentences maximum).
    This will be read aloud to the user by a voice assistant.
    Never output your internal reasoning or analysis steps.
    Never output raw data dumps.

CURRENT WEATHER TOOL RESULT:
{weather_context}
""".strip()


async def chat(
    question: str,
    language: str,
    weather_context: str,
    history: list[dict] | None = None,
) -> str:

    api_key = os.getenv(
        "SARVAM_API_KEY",
        ""
    ).strip()

    if not api_key:
        raise RuntimeError(
            "SARVAM_API_KEY is not configured."
        )

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
        messages.append(
            {
                "role": "system",
                "content": (
                    "CONVERSATION HISTORY BELOW IS FOR CONTEXT "
                    "ONLY. DO NOT USE IT AS A SOURCE OF WEATHER FACTS."
                ),
            }
        )

        messages.extend(
            history[-12:]
        )

    messages.append(
        {
            "role": "user",
            "content": question,
        }
    )

    payload = {
        "model": MODEL,
        "messages": messages,
        "temperature": 0.3,
        "max_tokens": 150,
    }

    headers = {
        "api-subscription-key": api_key,
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(
        timeout=45.0
    ) as client:

        response = await client.post(
            SARVAM_CHAT_URL,
            headers=headers,
            json=payload,
        )

        print(
            "Sarvam HTTP:",
            response.status_code
        )

        response.raise_for_status()

        data = response.json()

    choices = data.get(
        "choices",
        []
    )

    if not choices:
        raise RuntimeError(
            "Sarvam returned no choices."
        )

    content = (
        choices[0]
        .get("message", {})
        .get("content", "")
    )

    if not content:
        raise RuntimeError(
            "Sarvam returned an empty response."
        )

    return content
