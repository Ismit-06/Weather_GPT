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


import re


def refine_conversational_text(text: str) -> str:
    if not text:
        return ""
    
    t = text
    # Remove thinking tags
    t = re.sub(r"<think>[\s\S]*?</think>", "", t)
    
    # Remove all emojis and unicode pictographs
    t = re.sub(r"[\U00010000-\U0010ffff\u2600-\u27bf\ufe00-\ufe0f\u200d\u2300-\u23ff\u2b50\u2b55\u3030]", "", t)

    # Fix known translation glitches
    t = re.sub(r"(?i)\bblowing\s*रही\s*है", "चल रही है", t)
    t = re.sub(r"(?i)\bblowing\s*रहा\s*है", "चल रहा है", t)
    t = re.sub(r"(?i)\bblowing\b", "चल रही है", t)
    t = re.sub(r"\b100%\s*बादल\s*आश्रित\b", "आसमान में बादल छाए हुए हैं", t)
    t = re.sub(r"\bगर्मी\s+का\s+तना(?:\.\.\.)?|\bगर्मी\s+का\s+तनाव\b", "गर्मी का असर कम रहेगा", t)
    
    # Remove parenthetical metric dumps like (वर्षा: 0.0 mm), (2.9 m/s), (humidity: 60%), (0.0 mm)
    t = re.sub(r"\s*\((?:वर्षा|बारिश|rain|rainfall|wind|हवा|humidity|आर्द्रता|temp|तापमान)?:?\s*[\d.]+\s*(?:mm|m/s|km/h|°C|%|hPa)?\)", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\s*\([\d.]+\s*(?:mm|m/s|km/h|°C|%|hPa)\)", "", t, flags=re.IGNORECASE)

    # Transliterate English city names into Odia / Hindi / Telugu if present
    odia_cities = {
        r"(?i)\bvijayawada\b": "ବିଜୟୱାଡ଼ା",
        r"(?i)\bamaravati\b": "ଅମରାବତୀ",
        r"(?i)\bbhubaneswar\b": "ଭୁବନେଶ୍ୱର",
        r"(?i)\bcuttack\b": "କଟକ",
        r"(?i)\bhyderabad\b": "ହାଇଦ୍ରାବାଦ",
        r"(?i)\bdelhi\b": "ଦିଲ୍ଲୀ",
        r"(?i)\bmumbai\b": "ମୁମ୍ବାଇ",
    }
    hindi_cities = {
        r"(?i)\bvijayawada\b": "विजयवाड़ा",
        r"(?i)\bamaravati\b": "अमरावती",
        r"(?i)\bbhubaneswar\b": "भुवनेश्वर",
        r"(?i)\bcuttack\b": "कटक",
        r"(?i)\bhyderabad\b": "हैदराबाद",
        r"(?i)\bdelhi\b": "दिल्ली",
        r"(?i)\bmumbai\b": "मुंबई",
    }
    telugu_cities = {
        r"(?i)\bvijayawada\b": "విజయవాడ",
        r"(?i)\bamaravati\b": "అమరావతి",
        r"(?i)\bbhubaneswar\b": "భువనేశ్వర్",
        r"(?i)\bhyderabad\b": "హైదరాబాద్",
        r"(?i)\bdelhi\b": "ఢిల్లీ",
        r"(?i)\bmumbai\b": "ముంబై",
    }

    if re.search(r"[\u0B00-\u0B7F]", t):
        for pat, rep in odia_cities.items():
            t = re.sub(pat, rep, t)
    elif re.search(r"[\u0900-\u097F]", t):
        for pat, rep in hindi_cities.items():
            t = re.sub(pat, rep, t)
    elif re.search(r"[\u0C00-\u0C7F]", t):
        for pat, rep in telugu_cities.items():
            t = re.sub(pat, rep, t)

    # Convert bullet points and lines into natural sentences
    lines = [line.strip() for line in t.split("\n") if line.strip()]
    processed_lines = []
    for line in lines:
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
            or "ଆପଣଙ୍କ ପ୍ରଶ୍ନ ଥିଲା" in line
            or "your question was" in lower
            or "you asked about" in lower
        ):
            continue

        cleaned = re.sub(r"^[-•–—\d.)]+\s*", "", line).strip()
        if re.search(r"(?:मौसम की स्थिति|मौसम की कुछ बातें|मुख्य बातें|key details|forecast details|ପାଣିପାଗ)[:\s]*$", cleaned, re.IGNORECASE):
            continue
        cleaned = re.sub(r"(?:मौसम की कुछ बातें देखें|यहाँ कुछ बातें देखें|Here are a few points|ମୁଁ ଆପଣଙ୍କ ସହାୟତା କରିବା ପାଇଁ)[:\s]*", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s*[—–]\s*", ", ", cleaned)
        cleaned = cleaned.strip()
        if cleaned:
            processed_lines.append(cleaned)
    
    result = " ".join(processed_lines)
    result = re.sub(r"\s+", " ", result)
    result = re.sub(r"([।!?.,])\s*([।!?.,])+", r"\1", result)
    result = re.sub(r"\s+([।!?.,])", r"\1", result)
    return result.strip()


def build_system_prompt(
    language: str,
    weather_context: str,
) -> str:
    return f"""You are WeatherGPT, a smart, friendly, and practical human weather assistant.

CORE PERSONALITY & TONE:
- Talk like a knowledgeable, helpful friend having a genuine conversation—not a government weather report or robotic bot.
- Be calm, practical, direct, and conversational.
- Answer the user's ACTUAL question without dumping unrelated weather metrics (no UV, pressure, humidity, or AQI unless requested or directly relevant).
- If the user question is unclear or garbled, NEVER echo it back. Simply give current conditions for the location in 1-2 friendly sentences and ask how you can help.

RESPONSE LENGTH:
- Simple questions (e.g., "Will it rain?", "Should I take an umbrella?", "ଖରା କେତେ ଅଛି?"): 1–3 sentences.
- Moderately complex questions: 3–5 sentences.
- Keep answers concise, clear, and easy to speak aloud.

NO GENERIC AI CLICHES & NO AI HESITATION:
- NEVER say: "According to the weather data...", "Based on the latest forecast...", "As an AI...", "Certainly!", "I'd be happy to...", "In conclusion...", "Your question was...".
- NEVER say: "I wasn't able to pull up weather conditions...", "The system needs a more specific time...", "To give you a clear recommendation, I'd need to check...".
- If the user asks about an activity "right now" or without a specified time, give an immediate, confident recommendation based on the current weather telemetry.
- Use natural human phrasing: "Yeah, rain looks likely...", "I'd take an umbrella...", "Conditions in Amaravati are around 28°C and clear right now, so it's a great time for a run.", "ବର୍ତ୍ତମାନ ବିଜୟୱାଡ଼ାରେ ଖରା ସହିତ ତାପମାତ୍ରା ପ୍ରାୟ ୩୦ ଡିଗ୍ରୀ ଅଛି।"

NO OVER-FORMATTING OR RAW METRIC DUMPS:
- NEVER use markdown headers (#, ##), bold asterisks (**word**), bullet lists (- or •), numbered lists, or raw numbers with units in parentheses like (30.5°C) or (2.9 m/s).
- Write in clean, fluid sentences in the native script. Transliterate all city names into the target script (e.g., ବିଜୟୱାଡ଼ା, ଅମରାବତୀ, विजयवाड़ा).

LANGUAGE:
- Match the user's language naturally.
- English: use natural contractions ("It's", "you're", "there's", "I'd", "looks like").
- Hinglish: speak natural conversational Hinglish.
- Hindi: speak natural, colloquial Hindi without formal/bureaucratic stiffness or mixed English words.
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
        "temperature": 0.2,
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

    return refine_conversational_text(content)
