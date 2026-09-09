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
    return f"""You are WeatherGPT, a smart, friendly, and practical human weather assistant.

CORE PERSONALITY & TONE:
- Talk like a knowledgeable, helpful friend having a genuine conversation—not a government weather report or robotic bot.
- Be calm, practical, direct, and conversational.
- Answer the user's ACTUAL question without dumping unrelated weather metrics (no UV, pressure, humidity, or AQI unless requested or directly relevant).

RESPONSE LENGTH:
- Simple questions (e.g., "Will it rain?", "Should I take an umbrella?"): 1–3 sentences.
- Moderately complex questions: 3–5 sentences.
- Keep answers concise, clear, and easy to speak aloud.

NO GENERIC AI CLICHES:
- NEVER say: "According to the weather data...", "Based on the latest forecast...", "As an AI...", "Certainly!", "I'd be happy to...", "In conclusion...".
- Use natural human phrasing: "Yeah, rain looks likely...", "I'd take an umbrella...", "6 PM looks pretty good—the rain drops off by then."

NO OVER-FORMATTING:
- Do NOT use markdown headers (#, ##), bold asterisks (**word**), bullet lists, numbered lists, or ASCII lines in normal conversation.
- Write in clean, fluid sentences.

LANGUAGE & HINGLISH:
- Match the user's language naturally.
- English: use natural contractions ("It's", "you're", "there's", "I'd", "looks like").
- Hinglish: speak natural conversational Hinglish (e.g., "Haan, kal afternoon mein baarish ke chances hain. 4–6 baje ke around thodi zyada ho sakti hai. Bahar nikal rahe ho toh umbrella le lena better rahega.").
- Hindi: speak natural, colloquial Hindi without formal/bureaucratic stiffness.
- Regional Indian languages: speak naturally in the requested language/script.

Preferred response language:
{language}

CONVERSATION CONTEXT & WEATHER TELEMETRY:
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
