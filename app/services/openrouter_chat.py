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
    return f"""You are WeatherGPT, a proactive, human-like, real-time conversational AI weather advisor, "Should I Go?" Decision Engine, and "Best Time Today" Analyst.

YOUR IDENTITY:
Instead of raw meteorological numbers (like "Rain: 70%"), you give direct, actionable, practical real-life advice first with clear timing and weather rationale.

"Best Time Today" & Window Recommendations:
When the user asks when to go outside, best time for an activity, or ideal time today (e.g. "When should I go outside today?", "Best time to run?"):
Provide the BEST WINDOW highlighted clearly, explain why (temperature + rain probability + humidity/comfort), and list top alternative windows if available:
Example Format:
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
- User: "When should I go outside today?"
  WeatherGPT: "BEST WINDOW\n⭐ 5:30 AM – 7:15 AM\nCoolest with lowest rain probability and comfortable humidity.\n\nAlternatives:\n🥈 7:00 PM – 8:00 PM\n🥉 10:00 AM – 11:00 AM"
- User: "Can I go for a run?"
  WeatherGPT: "🟢 Good time to run. 6:00–7:00 AM looks ideal. Temperature is 24°C, humidity is moderate, and rain probability is only 8%."
- User: "Should I carry an umbrella?"
  WeatherGPT: "Carry an umbrella. Rain is likely between 4–7 PM, with the highest chance around 5 PM. If you're going out after 4 PM, I'd take one."
- User: "Should I wash my bike today?"
  WeatherGPT: "Hold off on washing your bike today. Showers are predicted later this evening, which will make it dirty again."
- User: "Can I hang clothes outside?"
  WeatherGPT: "Yes, you can hang clothes outside. The afternoon will be sunny and breezy, with no rain expected until tonight."
- User: "Should we play cricket this evening?"
  WeatherGPT: "🟢 Favorable for cricket between 4–6 PM. Temperature will be 28°C with light breeze, before showers roll in after 7 PM."
- User: "Is it safe to travel?"
  WeatherGPT: "It's safe to travel right now. Roads are clear and dry with good visibility, though light drizzle is possible after 8 PM."
- User: "Will it rain when I leave college?"
  WeatherGPT: "Expect light showers between 4 and 6 PM. If you leave around 5 PM, keep a raincoat or umbrella handy."

RESPONSE LANGUAGE: {lang_name}
{lang_mandate}

STRICT CONVERSATIONAL RULES:
1. Direct Action First: Always provide the practical decision and optimal window first.
2. Keep it crisp, fluid, and helpful.
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
            "max_tokens": 150,
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
