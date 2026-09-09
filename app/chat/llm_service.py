import json
import os

from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()


def generate_weather_answer(
    question: str,
    location: str,
    weather_context: dict,
) -> str:

    api_key = os.getenv("OPENAI_API_KEY")

    if not api_key:
        return build_fallback_answer(
            question,
            location,
            weather_context
        )

    client = OpenAI(
        api_key=api_key
    )

    system_prompt = """You are WeatherGPT, a smart, friendly, and practical human weather assistant.

CORE RULES:
1. Speak like a knowledgeable, helpful friend having a natural conversation.
2. Answer the user's ACTUAL question directly and concisely (1–3 sentences for simple questions; 3–5 for moderate).
3. Do NOT dump unrequested weather metrics (like pressure, humidity, UV, AQI) unless directly relevant.
4. Avoid generic AI cliches ("According to the weather data...", "Based on the forecast...", "As an AI...", "Certainly!").
5. Use natural English contractions ("It's", "you're", "there's", "I'd", "looks like").
6. Match the user's language naturally (support natural Hindi and Hinglish without robotic stiffness).
7. Do NOT use markdown headers, tables, or excessive bolding/bullet points in normal chat.
"""

    user_prompt = f"""
User question:
{question}

Location:
{location}

WeatherGPT computed intelligence:
{json.dumps(weather_context, default=str, indent=2)}

Answer the user's question using only the supplied
weather intelligence.
"""

    response = client.responses.create(
        model=os.getenv(
            "OPENAI_MODEL",
            "gpt-5-mini"
        ),
        instructions=system_prompt,
        input=user_prompt,
        temperature=0.2,
    )

    return response.output_text


def build_fallback_answer(
    question: str,
    location: str,
    context: dict,
) -> str:

    current = context.get(
        "current",
        {}
    )

    safety = context.get(
        "safety",
        {}
    )

    temperature = current.get(
        "temperature_c"
    )

    humidity = current.get(
        "humidity_pct"
    )

    risk = safety.get(
        "overall_risk_score"
    )

    level = safety.get(
        "overall_risk_level"
    )

    answer = (
        f"For {location}, "
    )

    if temperature is not None:
        answer += (
            f"the current temperature is "
            f"{temperature:.1f}°C. "
        )

    if humidity is not None:
        answer += (
            f"Humidity is {humidity:.0f}%. "
        )

    if risk is not None:
        answer += (
            f"The current WeatherGPT risk score "
            f"is {risk:.0f}/100 ({level}). "
        )

    answer += (
        "The detailed prediction and safety information "
        "should be considered alongside official local warnings."
    )

    return answer
