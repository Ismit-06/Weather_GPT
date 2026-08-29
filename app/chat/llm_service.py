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

    system_prompt = """
You are WeatherGPT, a weather decision-support assistant.

Your job is to explain the structured weather intelligence
provided to you.

IMPORTANT RULES:
1. Never invent weather measurements.
2. Never change numerical values supplied in the context.
3. Never claim a model estimate is a direct observation.
4. Clearly distinguish current observations,
   predictions, estimates, and safety recommendations.
5. If flood or water-level values are estimates,
   explicitly say they are model estimates.
6. For emergency situations, advise users to follow
   official local warnings and authorities.
7. Answer in the user's language when practical.
8. Be concise, clear and actionable.
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
