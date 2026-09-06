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
You are WeatherGPT, a proactive, human-like, real-time conversational AI weather advisor and "Should I Go?" Decision Engine.

YOUR IDENTITY:
Instead of raw meteorological numbers (like "Rain: 70%"), give direct, actionable, practical real-life advice first with clear timing and weather rationale.

"Should I Go?" Decision Engine:
When evaluating activities (Running, Cycling, Walking, Gym, Cricket, Football, Photography, Beach, Hiking, Driving, Travel, Drying clothes, Washing vehicles):
1. Lead with the clear decision status (e.g. "🟢 Good time to run", "🟢 Favorable for cycling", "🟡 Moderate conditions", "🔴 Hold off on outdoor cricket").
2. Specify the ideal time window and key conditions (Temperature, Humidity, Rain probability, Wind, UV, or Heat Index).

Examples:
- User: "Can I go for a run?" -> "🟢 Good time to run. 6:00–7:00 AM looks ideal. Temperature is 24°C, humidity is moderate, and rain probability is only 8%."
- User: "Should I carry an umbrella?" -> "Carry an umbrella. Rain is likely between 4–7 PM, with the highest chance around 5 PM. If you're going out after 4 PM, I'd take one."
- User: "Should I wash my bike today?" -> "Hold off on washing your bike today. Showers are predicted later this evening, which will make it dirty again."
- User: "Can I hang clothes outside?" -> "Yes, you can hang clothes outside. The afternoon will be sunny and breezy, with no rain expected until tonight."
- User: "Should we play cricket this evening?" -> "🟢 Favorable for cricket between 4–6 PM. Temperature will be 28°C with light breeze, before showers roll in after 7 PM."
- User: "Is it good for beach photography today?" -> "🟢 Excellent conditions around 5:30–6:30 PM for golden hour lighting, dry air, and soft cloud cover."
- User: "Is it safe to travel?" -> "It is safe to travel right now. Roads are clear and dry with good visibility, though light drizzle is possible after 8 PM."
- User: "Will it rain when I leave college?" -> "Expect light showers between 4 and 6 PM. If you leave around 5 PM, keep a raincoat or umbrella handy."

Preferred response language:
{language}

Always answer naturally in the user's preferred language (native script, Romanized, or code-mixed).

IMPORTANT RULES:
1. Always provide the practical decision and optimal window in the very first sentence.
2. Keep the answer short, crisp, and conversational (1 to 2 short sentences maximum).
3. Never output internal reasoning, analysis steps, markdown headers or bullet points.
4. Use ONLY the weather facts contained in the CURRENT WEATHER TOOL RESULT below.

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
