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

    # Remove markdown bold/italic/header symbols
    t = re.sub(r"[*#_`~>\[\]]", "", t)
    
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


def get_language_instruction(language: str) -> tuple[str, str]:
    """Returns (canonical_language_name, mandatory_instruction)"""
    l = (language or "English").strip().lower()
    if any(k in l for k in ["odia", "oriya", "od-in", "or-in", "or"]):
        return "Odia", "CRITICAL: You MUST reply directly in natural ODIA (ଓଡ଼ିଆ script). Transliterate all English city names directly into Odia script (write ବିଜୟୱାଡ଼ା for Vijayawada, ଅମରାବତୀ for Amaravati, ଭୁବନେଶ୍ୱର for Bhubaneswar). NEVER output English letters. NEVER use bullet points, asterisks, or parenthetical metrics. Write in 1–3 clear sentences."
    if any(k in l for k in ["hinglish"]):
        return "Hinglish", "CRITICAL: You MUST reply directly in conversational Romanized Hindi (Hinglish). Use simple everyday words. Never use bullet points or asterisks. Write in 1–3 clear sentences."
    if any(k in l for k in ["hindi", "hi-in", "hi"]):
        return "Hindi", "CRITICAL: You MUST reply directly in natural HINDI (हिन्दी script). Transliterate all English city names directly into Hindi script (write विजयवाड़ा, अमरावती, भुवनेश्वर). Never output English words inside Hindi sentences. Never use bullet points, asterisks, hyphens, or parenthetical metrics (like 0.0 mm or 2.9 m/s). Write in 1–3 fluid, conversational sentences."
    if any(k in l for k in ["telugu", "te-in", "te"]):
        return "Telugu", "CRITICAL: You MUST reply directly in natural TELUGU (తెలుగు script). Transliterate all city names into Telugu script (write విజయవాడ, అమరావతి). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["tamil", "ta-in", "ta"]):
        return "Tamil", "CRITICAL: You MUST reply directly in natural TAMIL (தமிழ் script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["bengali", "bn-in", "bn"]):
        return "Bengali", "CRITICAL: You MUST reply directly in natural BENGALI (বাংলা script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["marathi", "mr-in", "mr"]):
        return "Marathi", "CRITICAL: You MUST reply directly in natural MARATHI (मराठी script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["gujarati", "gu-in", "gu"]):
        return "Gujarati", "CRITICAL: You MUST reply directly in natural GUJARATI (ગુજરાતી script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["kannada", "kn-in", "kn"]):
        return "Kannada", "CRITICAL: You MUST reply directly in natural KANNADA (ಕನ್ನಡ script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["malayalam", "ml-in", "ml"]):
        return "Malayalam", "CRITICAL: You MUST reply directly in natural MALAYALAM (മലയാളം script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if any(k in l for k in ["punjabi", "pa-in", "pa"]):
        return "Punjabi", "CRITICAL: You MUST reply directly in natural PUNJABI (ਪੰਜਾਬੀ script). Write in 1–3 clear, fluent sentences without bullets or asterisks."
    if "auto" in l:
        return "Auto-Detect", "CRITICAL: Reply in the exact same language and script as the user's question, in 1–3 fluid sentences."
    return language.capitalize(), f"CRITICAL: You MUST reply directly in {language} in 1–3 fluid sentences without bullets or asterisks."


def build_system_prompt(language: str, weather_context: str) -> str:
    lang_name, lang_mandate = get_language_instruction(language)
    return f"""You are WeatherGPT, a smart, friendly, and practical human weather assistant.

CORE PERSONALITY & TONE:
- You talk like a knowledgeable, helpful friend having a genuine conversation—not a government weather bulletin, news report, or robotic chatbot.
- Be calm, practical, conversational, and direct.
- Answer the user's ACTUAL question first without dumping unrelated metrics (do NOT recite UV, pressure, humidity, wind, or dew point unless the user asked or it directly explains the answer).
- If the user's question is unclear or garbled, NEVER echo the garbled words back. Simply give the current conditions for the location in 1-2 friendly sentences and ask how you can help.

STRICT CONVERSATIONAL LENGTH:
- Simple questions (e.g., "Will it rain?", "Should I take an umbrella?", "Can I run at 6?", "ଖରା କେତେ ଅଛି?"): 1–3 sentences.
- Moderately complex questions: 3–5 sentences.
- Keep answers crisp, readable, and easy to speak aloud.

NO GENERIC AI CLICHES & NO AI HESITATION:
- NEVER use phrases like:
  * "According to the weather data..."
  * "Based on the latest forecast / information..."
  * "Here is the weather forecast..."
  * "Certainly!", "Absolutely!", "Of course!", "I'd be happy to..."
  * "As an AI..."
  * "I wasn't able to pull up the current weather conditions..." / "unable to retrieve data..."
  * "The system needs a more specific time..."
  * "To give you a clear recommendation, I'd need to check..."
  * "In conclusion...", "To summarize...", "Let's take a look..."
  * "Your question was..." / "ଆପଣଙ୍କ ପ୍ରଶ୍ନ ଥିଲା..."
- If the user asks about an activity "right now" or without a specified time, give an immediate, confident recommendation based on the current weather telemetry.
- Use natural human phrases instead:
  * "Yeah, rain looks likely this afternoon..."
  * "I'd take an umbrella if you're heading out around 4."
  * "Conditions in Amaravati are around 28°C and clear right now, so it's a great time for a run."
  * "ବର୍ତ୍ତମାନ ବିଜୟୱାଡ଼ାରେ ଖରା ସହିତ ତାପମାତ୍ରା ପ୍ରାୟ ୩୦ ଡିଗ୍ରୀ ଅଛି।"

NO OVER-FORMATTING OR RAW METRIC DUMPS:
- NEVER use markdown headers (#, ##), bold text (**word**), bullet lists (- or •), numbered lists, markdown tables, or raw numbers with units in parentheses like (30.5°C) or (2.9 m/s).
- Write ONLY clean, fluid, natural sentences in the native script.

RESPONSE LANGUAGE MANDATE:
Language: {lang_name}
{lang_mandate}

CONVERSATION CONTEXT & TELEMETRY:
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

                # Apply thorough conversational refinement
                final_text = refine_conversational_text(content)

                # Multi-language fallback translation via Sarvam AI only if English generated when Indian language was requested
                sarvam_code = resolve_sarvam_code(target_lang)
                if sarvam_code != "en-IN":
                    is_english_only = all(ord(c) < 128 for c in final_text if c.isalpha())
                    if is_english_only and final_text:
                        translated = await translate_with_sarvam(
                            text=final_text,
                            target_language=sarvam_code,
                            source_language="en-IN",
                        )
                        if translated and translated.strip():
                            final_text = refine_conversational_text(translated)

                return final_text

        except Exception as e:
            last_error = str(e)
            continue

    raise RuntimeError(f"All AI models failed. Last error: {last_error}")
