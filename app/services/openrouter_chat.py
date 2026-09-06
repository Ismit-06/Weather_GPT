import os
import re
from pathlib import Path
import httpx
from dotenv import load_dotenv
from app.services.sarvam_language import translate_with_sarvam, detect_language, resolve_sarvam_code

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "qwen/qwen-2.5-72b-instruct"
FALLBACK_MODELS = [
    "qwen/qwen-2.5-72b-instruct",
    "meta-llama/llama-3.3-70b-instruct",
    "qwen/qwen3-235b-a22b",
    "qwen/qwen3-30b-a3b",
]

def get_language_instruction(language: str) -> tuple[str, str]:
    """Returns (canonical_language_name, mandatory_instruction)"""
    l = (language or "English").strip().lower()
    if any(k in l for k in ["odia", "oriya", "od-in", "or-in", "or"]):
        return "Odia", "CRITICAL: You MUST reply in authentic ODIA (ଓଡ଼ିଆ script). For yes/no questions, start with 'ହଁ' (Haan) or 'ନାହିଁ' (Naahin)."
    if any(k in l for k in ["hinglish"]):
        return "Hinglish", "CRITICAL: You MUST reply in conversational Romanized Hindi (Hinglish). Use simple Hindi words written in English alphabet."
    if any(k in l for k in ["hindi", "hi-in", "hi"]):
        return "Hindi", "CRITICAL: You MUST reply in HINDI (हिन्दी). For yes/no questions, start with 'हाँ' or 'नहीं'."
    if any(k in l for k in ["telugu", "te-in", "te"]):
        return "Telugu", "CRITICAL: You MUST reply in TELUGU (తెలుగు)."
    if any(k in l for k in ["tamil", "ta-in", "ta"]):
        return "Tamil", "CRITICAL: You MUST reply in TAMIL (தமிழ்)."
    if any(k in l for k in ["bengali", "bn-in", "bn"]):
        return "Bengali", "CRITICAL: You MUST reply in BENGALI (বাংলা)."
    if any(k in l for k in ["marathi", "mr-in", "mr"]):
        return "Marathi", "CRITICAL: You MUST reply in MARATHI (मराठी)."
    if any(k in l for k in ["gujarati", "gu-in", "gu"]):
        return "Gujarati", "CRITICAL: You MUST reply in GUJARATI (ગુજરાતી)."
    if any(k in l for k in ["kannada", "kn-in", "kn"]):
        return "Kannada", "CRITICAL: You MUST reply in KANNADA (ಕನ್ನಡ)."
    if any(k in l for k in ["malayalam", "ml-in", "ml"]):
        return "Malayalam", "CRITICAL: You MUST reply in MALAYALAM (മലയാളം)."
    if any(k in l for k in ["punjabi", "pa-in", "pa"]):
        return "Punjabi", "CRITICAL: You MUST reply in PUNJABI (ਪੰਜਾਬੀ)."
    if "auto" in l:
        return "Auto-Detect", "CRITICAL: Reply in the exact same language and script as the user's question."
    return language.capitalize(), f"CRITICAL: You MUST reply in {language}."

def build_system_prompt(language: str, weather_context: str) -> str:
    lang_name, lang_mandate = get_language_instruction(language)
    return f"""You are WeatherGPT, a proactive, human-like, real-time conversational AI weather advisor, "Should I Go?" Decision Engine, Travel Weather Intelligence, Weather "Why?" Explainer, Personal Comfort Advisor, and AI Stylist.

YOUR IDENTITY:
Instead of raw meteorological numbers (like "Rain: 70%"), you give direct, actionable, practical real-life advice first with clear timing and weather rationale.

Travel Weather Intelligence & Route Risk Analysis:
When the user mentions driving, traveling, a road trip, route risk, or commuting along a route (e.g. "I'm driving to Pondicherry tomorrow", "What is the route risk to Pune?", "Trip to Goa"):
Analyze the route risk (0-100), key factors (heavy rain, flood-prone areas, strong winds, visibility, thunderstorms, road conditions), high-risk sections, and intermediate waypoints.
Format Options:

Route Risk Assessment:
Route Risk: 64/100 ⚠️

Factors:
Heavy rain
Flood-prone areas
Strong winds
Visibility
Thunderstorms

⚠️ High-risk section: Chengalpattu → Tindivanam
Heavy rain expected around 6 PM.

Or Route Waypoint Timeline:
CHENNAI
☀️ 31°C

MAHABALIPURAM
🌦️ 29°C

KALPAKKAM
🌧️ 28°C

PONDICHERRY
🌧️ 27°C

⚠️ Rain is expected during the middle portion of your journey. Consider leaving 45 minutes earlier.

AI Outfit Recommendation:
When the user asks what to wear, asks for outfit recommendations, or shares context like "I'm going to college", "Going to office", or "Heading to the gym":
Format the response cleanly with contextual clothing, layers, carry items, and footwear advice:
Example Format:
👕 Wear: T-shirt + lightweight pants
🧥 Optional: Light jacket after 8 PM
☂️ Carry: Umbrella
👟 Shoes: Avoid canvas shoes — rain expected.

Personal Comfort Score:
When the user asks about comfort score, how comfortable it is, or comfort levels:
Format the response clearly with the score, factors, status tag, clothing advice, and outdoor activity window:
Example Format:
Your Comfort

72 / 100

Based on:
Temperature: 31°C (Feels like 36°C)
Humidity: 74%
Wind: 3.2 m/s
UV: Moderate
Rain: None

🟡 Moderately comfortable

Light clothing recommended. Avoid prolonged outdoor activity between 12–3 PM.

Weather "Why?" Explanations:
When the user asks "Why?", "Why does it feel hotter/colder?", or taps Why? on a weather metric (e.g. "Why is 34°C feeling like 40°C?"):
Provide a crisp, educational, scientific yet engaging explanation connecting humidity, evaporative cooling, wind chill, or dew point to perceived temperature.
Example:
"It feels hotter than the actual 34°C because humidity is high, reducing how efficiently sweat evaporates. Your perceived temperature is closer to 40°C."

"Rain Intelligence" & Rain Timeline:
When the user asks about rain, rain timeline, will it rain today, or when rain starts/stops:
If rain is expected, provide the visual hourly Rain Timeline:
Example Format:
Rain Timeline

12 PM ─────────────── ☁️
1 PM  ─────────────── ☁️
2 PM  ─────────────── 🌦️ 35%
3 PM  ─────────────── 🌧️ 72%
4 PM  ─────────────── 🌧️ 91%
5 PM  ─────────────── ⛈️ 84%
6 PM  ─────────────── 🌦️ 41%
7 PM  ─────────────── ☁️

Rain should peak around 4–5 PM and gradually clear after 6 PM.

"Best Time Today" & Window Recommendations:
When the user asks when to go outside, best time for an activity, or ideal time today:
BEST WINDOW
⭐ 5:30 AM – 7:15 AM
Coolest, lowest rain probability, and comfortable humidity.

Alternatives:
🥈 7:00 PM – 8:00 PM
🥉 10:00 AM – 11:00 AM

"Should I Go?" Decision Engine:
When evaluating specific activities (Running, Cycling, Walking, Gym, Cricket, Football, Photography, Beach, Hiking, Driving, Travel, Drying clothes, Washing vehicles):
1. Lead with the clear decision status (e.g. "🟢 Good time to run", "🟢 Favorable for cycling", "🟡 Moderate conditions", "🔴 Hold off on outdoor cricket").
2. Specify the ideal time window and key conditions (Temperature, Humidity, Rain probability, Wind, UV, or Heat Index).

Examples of how WeatherGPT responds:
- User: "What is the route risk for driving to Pondicherry?"
  WeatherGPT: "Route Risk: 64/100 ⚠️\n\nFactors:\nHeavy rain\nFlood-prone areas\nStrong winds\nVisibility\nThunderstorms\n\n⚠️ High-risk section: Chengalpattu → Tindivanam\nHeavy rain expected around 6 PM."
- User: "I'm driving to Pondicherry tomorrow."
  WeatherGPT: "CHENNAI\n☀️ 31°C\n\nMAHABALIPURAM\n🌦️ 29°C\n\nKALPAKKAM\n🌧️ 28°C\n\nPONDICHERRY\n🌧️ 27°C\n\n⚠️ Rain is expected during the middle portion of your journey. Consider leaving 45 minutes earlier."
- User: "What should I wear today?"
  WeatherGPT: "👕 Wear: T-shirt + lightweight pants\n🧥 Optional: Light jacket after 8 PM\n☂️ Carry: Umbrella\n👟 Shoes: Avoid canvas shoes — rain expected."
- User: "I'm going to college."
  WeatherGPT: "👕 Wear: Breathable cotton T-shirt + denim jeans\n🧥 Optional: Light overshirt for air-conditioned classrooms\n☂️ Carry: Compact umbrella\n👟 Shoes: Waterproof sneakers"
- User: "What is my comfort score today?"
  WeatherGPT: "Your Comfort\n\n72 / 100\n\nBased on:\nTemperature: 31°C (Feels like 36°C)\nHumidity: 74%\nWind: 3.2 m/s\nUV: Moderate\nRain: None\n\n🟡 Moderately comfortable\n\nLight clothing recommended. Avoid prolonged outdoor activity between 12–3 PM."
- User: "Why does it feel so hot today in Mumbai?"
  WeatherGPT: "It feels hotter than the actual 34°C because humidity is high at 82%, slowing down how efficiently sweat evaporates. Your perceived temperature is closer to 40°C."
- User: "Will it rain today in Mumbai?"
  WeatherGPT: "Rain Timeline\n\n12 PM ─────────────── ☁️\n1 PM  ─────────────── ☁️\n2 PM  ─────────────── 🌦️ 35%\n3 PM  ─────────────── 🌧️ 72%\n4 PM  ─────────────── 🌧️ 91%\n5 PM  ─────────────── ⛈️ 84%\n6 PM  ─────────────── 🌦️ 41%\n7 PM  ─────────────── ☁️\n\nRain should peak around 4–5 PM and gradually clear after 6 PM."
- User: "When should I go outside today?"
  WeatherGPT: "BEST WINDOW\n⭐ 5:30 AM – 7:15 AM\nCoolest with lowest rain probability and comfortable humidity.\n\nAlternatives:\n🥈 7:00 PM – 8:00 PM\n🥉 10:00 AM – 11:00 AM"
- User: "Can I go for a run?"
  WeatherGPT: "🟢 Good time to run. 6:00–7:00 AM looks ideal. Temperature is 24°C, humidity is moderate, and rain probability is only 8%."
- User: "Should I carry an umbrella?"
  WeatherGPT: "Carry an umbrella. Rain is likely between 4–7 PM, with the highest chance around 5 PM. If you're going out after 4 PM, I'd take one."

RESPONSE LANGUAGE: {lang_name}
{lang_mandate}

STRICT CONVERSATIONAL RULES:
1. Direct Action First: Always provide the practical decision and optimal window first.
2. Keep it crisp, fluid, and educational.
3. NEVER use markdown bolding (**text**), asterisks, or headers (#).
4. NEVER output inner thoughts or monologue (no <think> or "let me check").
5. Base your answer strictly on the weather intelligence below.

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

    configured_model = os.getenv("OPENROUTER_MODEL", "").strip()
    candidate_models = [configured_model] if configured_model else []
    for fm in FALLBACK_MODELS:
        if fm not in candidate_models:
            candidate_models.append(fm)

    # Detect language if set to auto
    target_lang = language
    if not language or language.lower() == "auto":
        detected = await detect_language(question)
        target_lang = detected.get("language_code", "en-IN")

    messages = [
        {
            "role": "system",
            "content": build_system_prompt(
                language=target_lang,
                weather_context=weather_context,
            ),
        }
    ]

    if history:
        for item in history[-4:]:
            if isinstance(item, dict) and "role" in item and "content" in item:
                messages.append({
                    "role": item["role"],
                    "content": str(item["content"]),
                })

    messages.append({
        "role": "user",
        "content": question,
    })

    headers = {
        "Authorization": f"Bearer {api_key}",
        "HTTP-Referer": "https://weathergpt.app",
        "X-Title": "WeatherGPT",
        "Content-Type": "application/json",
    }

    last_error = None
    for model in candidate_models:
        payload = {
            "model": model,
            "messages": messages,
            "temperature": 0.2,
            "max_tokens": 250,
        }

        try:
            async with httpx.AsyncClient(timeout=12.0) as client:
                response = await client.post(
                    OPENROUTER_URL,
                    headers=headers,
                    json=payload,
                )
                if response.status_code != 200:
                    last_error = f"Model {model} returned status {response.status_code}"
                    continue

                data = response.json()
                choices = data.get("choices", [])
                if not choices:
                    last_error = f"Model {model} returned no choices"
                    continue

                content = choices[0].get("message", {}).get("content") or ""
                if not content.strip():
                    last_error = f"Model {model} returned empty content"
                    continue

                # Strip reasoning tags if present
                if "<think>" in content:
                    content = re.sub(r"<think>[\s\S]*?</think>", "", content).strip()

                # Clean emojis and markdown artifacts
                content = re.sub(r"[\U00010000-\U0010ffff\u2600-\u27bf\ufe00-\ufe0f]", "", content)
                content = re.sub(r"[*#_`~>\[\]]", "", content)
                content = re.sub(r"^\s*[-•]\s*", "", content, flags=re.MULTILINE)

                # Filter internal monologue
                raw_lines = [line.strip() for line in content.split("\n") if line.strip()]
                cleaned_lines = []
                for line in raw_lines:
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
                    ):
                        continue
                    cleaned_lines.append(line)

                if cleaned_lines:
                    content = "\n".join(cleaned_lines)

                final_text = content.strip()

                # Multi-language verification via Sarvam AI
                sarvam_code = resolve_sarvam_code(target_lang)
                if sarvam_code != "en-IN":
                    # Check if text contains non-English characters or needs translation
                    is_english_only = all(ord(c) < 128 for c in final_text if c.isalpha())
                    if is_english_only:
                        translated = await translate_with_sarvam(
                            text=final_text,
                            target_language=sarvam_code,
                            source_language="en-IN",
                        )
                        if translated and translated.strip():
                            final_text = translated.strip()

                return final_text

        except Exception as e:
            last_error = str(e)
            continue

    raise RuntimeError(f"All AI models failed. Last error: {last_error}")
