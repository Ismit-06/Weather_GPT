import json
import os
from pathlib import Path

import httpx
from dotenv import load_dotenv

from app.services.weather_intent import (
    detect_weather_intent,
)


load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)


SARVAM_CHAT_URL = (
    "https://api.sarvam.ai/v1/chat/completions"
)

MODEL = "sarvam-105b"


ALLOWED_INTENTS = {
    "CURRENT_WEATHER",
    "RAIN",
    "FORECAST",
    "TEMPERATURE",
    "WIND",
    "HUMIDITY",
    "TRAVEL",
    "OUTDOOR",
    "AGRICULTURE",
    "FLOOD",
    "ALERTS",
    "GENERAL_WEATHER",
}


async def classify_intent(
    question: str,
) -> str:

    # Fast first-pass classifier.
    rule_intent = detect_weather_intent(
        question
    )

    # If the rule engine has a specific intent,
    # use it without spending an LLM request.
    if rule_intent != "CURRENT_WEATHER":
        return rule_intent

    api_key = os.getenv(
        "SARVAM_API_KEY",
        "",
    ).strip()

    if not api_key:
        return rule_intent

    system = """
Classify the user's weather question into exactly ONE
of these intents:

CURRENT_WEATHER
RAIN
FORECAST
TEMPERATURE
WIND
HUMIDITY
TRAVEL
OUTDOOR
AGRICULTURE
FLOOD
ALERTS
GENERAL_WEATHER

Understand:
- English
- Hindi
- Telugu
- Tamil
- Bengali
- Kannada
- Malayalam
- Marathi
- Gujarati
- Punjabi
- Odia
- Romanized Indian languages
- code-mixed Indian languages

Examples:

"Should I carry an umbrella tomorrow?"
RAIN

"Can I go for a run at 6 PM?"
OUTDOOR

"Is it safe to drive tonight?"
TRAVEL

"Do I need a jacket tomorrow morning?"
TEMPERATURE

Return ONLY JSON:
{"intent":"RAIN"}
""".strip()

    payload = {
        "model": MODEL,
        "messages": [
            {
                "role": "system",
                "content": system,
            },
            {
                "role": "user",
                "content": question,
            },
        ],
        "temperature": 0,
        "max_tokens": 60,
    }

    headers = {
        "api-subscription-key": api_key,
        "Content-Type": "application/json",
    }

    try:

        async with httpx.AsyncClient(
            timeout=30
        ) as client:

            response = await client.post(
                SARVAM_CHAT_URL,
                headers=headers,
                json=payload,
            )

            response.raise_for_status()

            data = response.json()

        choices = data.get(
            "choices",
            []
        )

        if not choices:
            return rule_intent

        message = choices[0].get(
            "message",
            {}
        )

        content = message.get(
            "content"
        )

        # Sarvam can occasionally return no textual
        # content. Never call .strip() on None.
        if not isinstance(
            content,
            str
        ):
            return rule_intent

        content = content.strip()

        if not content:
            return rule_intent

        # Handle JSON inside markdown fences too.
        if content.startswith("```"):
            content = (
                content
                .replace("```json", "")
                .replace("```", "")
                .strip()
            )

        parsed = json.loads(
            content
        )

        intent = parsed.get(
            "intent",
            rule_intent
        )

        if intent not in ALLOWED_INTENTS:
            return rule_intent

        return intent

    except Exception:
        # Semantic classification must never break
        # the weather chatbot.
        return rule_intent
