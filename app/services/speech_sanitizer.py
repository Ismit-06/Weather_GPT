import re

def prepare_text_for_speech(text: str, language_code: str = "en-IN") -> str:
    """
    Deterministic application-level speech preparation:
    1. Removes all thinking tags & internal reasoning.
    2. Removes all emojis and decorative symbols.
    3. Converts/strips markdown headers, asterisks, bullet points, and code blocks.
    4. Normalizes units, percentages, and time expressions for natural speech.
    5. Transliterates common English location names for Indian languages.
    6. Preserves sentence meaning and factual accuracy.
    """
    if not text:
        return ""

    t = text

    # 1. Remove thinking / reasoning tags
    if "<think>" in t:
        t = re.sub(r"<think>[\s\S]*?</think>", "", t)

    # 2. Filter internal metadata and reasoning monologue lines
    raw_lines = [line.strip() for line in t.split("\n") if line.strip()]
    filtered_lines = []
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
            or "your query reference time" in lower
            or "query reference time" in lower
            or "confidence score" in lower
            or "100% confident" in lower
            or "आपकी query का reference time" in line
            or "ଆପଣଙ୍କ ପ୍ରଶ୍ନ ଥିଲା" in line
            or "your question was" in lower
        ):
            continue

        # Strip markdown headers (##, ###, #)
        line_clean = re.sub(r"^#+\s*", "", line)
        # Strip leading bullet dashes or list numbers like '1.' or '1)'
        line_clean = re.sub(r"^(?:[-•*–—]|\d+[.)])\s*", "", line_clean)
        line_clean = line_clean.strip()

        # Remove header lines ending with colons
        if re.search(r"(?:मौसम की स्थिति|मौसम की कुछ बातें|मुख्य बातें|key details|forecast details|ପାଣିପାଗ)[:\s]*$", line_clean, re.IGNORECASE):
            continue

        if line_clean:
            filtered_lines.append(line_clean)

    if filtered_lines:
        t = " . ".join(filtered_lines)

    # 3. Remove all emojis, unicode pictographs, and dingbats completely
    t = re.sub(
        r"["
        r"\U0001F000-\U0001FAFF"
        r"\U0001F300-\U0001F5FF"
        r"\U0001F600-\U0001F64F"
        r"\U0001F680-\U0001F6FF"
        r"\U0001F700-\U0001F7FF"
        r"\U0001F800-\U0001F9FF"
        r"\U0001FA00-\U0001FAFF"
        r"\u2600-\u27BF"
        r"\u2300-\u23FF"
        r"\u2B50\u2B55\u3030"
        r"\uFE00-\uFE0F\u200D"
        r"]+",
        "",
        t,
        flags=re.UNICODE,
    )

    # 4. Transliterate English location words into native scripts if Indian script is present
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

    if re.search(r"[\u0B00-\u0B7F]", t) or (language_code and "od" in language_code.lower()):
        for pat, rep in odia_cities.items():
            t = re.sub(pat, rep, t)
    elif re.search(r"[\u0900-\u097F]", t) or (language_code and "hi" in language_code.lower()):
        for pat, rep in hindi_cities.items():
            t = re.sub(pat, rep, t)
    elif re.search(r"[\u0C00-\u0C7F]", t) or (language_code and "te" in language_code.lower()):
        for pat, rep in telugu_cities.items():
            t = re.sub(pat, rep, t)

    # 5. Fix known machine translation glitches
    t = re.sub(r"(?i)\bblowing\s*रही\s*है", "चल रही है", t)
    t = re.sub(r"(?i)\bblowing\s*रहा\s*है", "चल रहा है", t)
    t = re.sub(r"(?i)\bblowing\b", "चल रही है", t)
    t = re.sub(r"\b100%\s*बादल\s*आश्रित\b", "आसमान में बादल छाए हुए हैं", t)
    t = re.sub(r"\bगर्मी\s+का\s+तना(?:\.\.\.)?|\bगर्मी\s+का\s+तनाव\b", "गर्मी का असर कम रहेगा", t)

    # 6. Normalize units & percentages phonetically
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\s*C(?!\w)", r"\1 degrees Celsius", t)
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°\s*F(?!\w)", r"\1 degrees Fahrenheit", t)
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*°(?!\w)", r"\1 degrees", t)
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*km/h(?!\w)|\b(\d+(?:\.\d+)?)\s*kmph(?!\w)", r"\1 kilometers per hour", t)
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*m/s(?!\w)", r"\1 meters per second", t)
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*mm(?!\w)", r"\1 millimeters", t)
    t = re.sub(r"(?i)(\d+(?:\.\d+)?)\s*%(?!\w)", r"\1 percent", t)

    # 7. Convert dashes between times and text
    t = re.sub(r"(\d+(?::\d+)?)\s*[–—\-]\s*(\d+(?::\d+)?)\s*(AM|PM|am|pm|baje)?\b", r"\1 to \2 \3", t)
    t = re.sub(r"\s*[—–]\s*", ", ", t)

    # 8. Strip markdown symbols
    t = re.sub(r"[*#_`~>\[\]()|]", "", t)

    # 9. Clean punctuation & whitespace
    t = re.sub(r"\s+", " ", t)
    t = re.sub(r"\s*\.\s*\.", ".", t)
    t = re.sub(r"\s+([.,!?।])", r"\1", t)
    t = re.sub(r"([.,!?।])\s*([.,!?।])+", r"\1", t)
    t = re.sub(r"^\s*[,.]+\s*", "", t)

    return t.strip()
