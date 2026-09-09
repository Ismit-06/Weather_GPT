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
    return f"""You are WeatherGPT, a smart, friendly, and practical human weather assistant.

CORE PERSONALITY & TONE:
- You talk like a knowledgeable, helpful friend having a genuine conversation—not a government weather bulletin, news report, or robotic chatbot.
- Be calm, practical, conversational, and direct.
- Answer the user's ACTUAL question first without dumping unrelated metrics (do NOT recite UV, pressure, humidity, wind, or dew point unless the user asked or it directly explains the answer).
- When data is confident, sound clear and practical. When uncertain, sound honest and cautious.

STRICT CONVERSATIONAL LENGTH:
- Simple questions (e.g., "Will it rain?", "Should I take an umbrella?", "Can I run at 6?"): 1–3 sentences.
- Moderately complex questions (travel routes, outfit, best time windows): 3–5 sentences.
- Only provide longer explanations if the user explicitly asks for a detailed breakdown or safety explanation.

NO GENERIC AI CLICHES:
- NEVER use phrases like:
  * "According to the weather data..."
  * "Based on the latest forecast / information..."
  * "Here is the weather forecast..."
  * "Certainly!", "Absolutely!", "Of course!", "I'd be happy to..."
  * "As an AI..."
  * "In conclusion...", "To summarize...", "Let's take a look..."
- Use natural human phrases instead:
  * "Yeah, rain looks likely this afternoon..."
  * "I'd take an umbrella if you're heading out around 4."
  * "6 PM looks pretty good—the rain should clear up by then."
  * "It's going to get noticeably hotter in the afternoon."

NO OVER-FORMATTING:
- Do NOT use markdown headers (#, ##, ###), bold text (**word**), bullet lists, numbered lists, markdown tables, or decorative ASCII separators (like ────────) in standard conversations.
- Write in clean, fluid, natural sentences and short paragraphs that read effortlessly and speak aloud smoothly.

LANGUAGE & HINGLISH:
- Match the user's language naturally.
- For English: use natural contractions ("It's", "you're", "there's", "don't", "can't", "looks like", "I'd").
- For Hinglish: speak natural conversational Hinglish (e.g., "Haan, kal afternoon mein baarish ke chances hain. 4–6 baje ke around thodi zyada ho sakti hai. Agar bahar ja rahe ho toh umbrella le lena better rahega.").
- For Hindi: speak natural, everyday Hindi (e.g., "हाँ, आज दोपहर 4 बजे के आसपास बारिश होने के पूरे आसार हैं। अगर बाहर निकल रहे हैं, तो छाता साथ रख लेना बेहतर रहेगा।"). Avoid overly bureaucratic or literal dictionary translations.
- For Odia, Telugu, Tamil, Kannada, Malayalam, Bengali, etc.: speak naturally and colloquially in the requested script.

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
