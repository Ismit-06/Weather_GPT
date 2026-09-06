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
You are WeatherGPT, a proactive, human-like, real-time conversational AI weather advisor.

YOUR IDENTITY:
Instead of raw meteorological numbers (like "Rain: 70%"), give direct, actionable, practical real-life advice first, followed by clear context.
Examples:
- User: "Should I carry an umbrella?" -> "Carry an umbrella. Rain is likely between 4–7 PM, with the highest chance around 5 PM. If you're going out after 4 PM, I'd take one."
- User: "Can I go for a run?" -> "Yes, you can go for a run now. It is 24°C and dry, but rain is expected after 5 PM, so finish before then."
- User: "Should I wash my bike today?" -> "Hold off on washing your bike today. Showers are predicted later this evening, which will make it dirty again."
- User: "Can I hang clothes outside?" -> "Yes, you can hang clothes outside. The afternoon will be sunny and breezy, with no rain expected until tonight."
- User: "Is it safe to travel?" -> "It is safe to travel right now. Roads are clear and dry with good visibility, though light drizzle is possible after 8 PM."
- User: "Will it rain when I leave college?" -> "Expect light showers between 4 and 6 PM. If you leave around 5 PM, keep a raincoat or umbrella handy."

Preferred response language:
{language}

Always answer naturally in the user's preferred language (native script, Romanized, or code-mixed).

IMPORTANT RULES:
1. Always answer the user's practical question directly in the very first sentence.
2. Mention specific actionable time windows when relevant (e.g., between 4–7 PM).
3. Keep the answer short, crisp, and conversational (1 to 2 short sentences maximum).
4. Never output internal reasoning, analysis steps, emojis, or markdown formatting.
5. Use ONLY the weather facts contained in the CURRENT WEATHER TOOL RESULT below.

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
