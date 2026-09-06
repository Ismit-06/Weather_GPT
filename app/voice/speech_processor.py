import re
import unicodedata

EMOJI_PATTERN = re.compile(
    "["
    "\U00010000-\U0010FFFF"
    "\uD800-\uDBFF"
    "\uDC00-\uDFFF"
    "\u2600-\u27BF"
    "\u2300-\u23FF"
    "\u2B50\u2B55\u2934\u2935"
    "\uFE00-\uFE0F"
    "\u200D"
    "]+",
    flags=re.UNICODE
)

class SpeechTextProcessor:
    """
    Transforms WeatherGPT LLM responses into speech-safe, natural conversational audio text.
    Enforces strict architectural invariant: DISPLAY TEXT != SPEECH TEXT.
    Guarantees NO emojis, NO markdown, and natural phonetic unit expansions.
    """

    @classmethod
    def process_for_speech(cls, text: str, language_code: str = "en-IN") -> str:
        if not text or not text.strip():
            return ""

        s = text

        # 1. Strip internal monologue / <think> tags
        if "<think>" in s:
            s = re.sub(r"<think>[\s\S]*?</think>", "", s).strip()

        # 2. Filter reasoning monologue phrases
        lines = [line.strip() for line in s.split("\n") if line.strip()]
        filtered_lines = []
        for line in lines:
            lower = line.lower()
            if (
                lower.startswith("the user is asking")
                or lower.startswith("let me check")
                or lower.startswith("looking at the data")
                or lower.startswith("wait, let me reconsider")
                or lower.startswith("hmm")
                or lower.startswith("i think i'm")
                or lower.startswith("let me just respond")
                or lower.startswith("i'll respond in")
                or "overthinking" in lower
                or "respond naturally" in lower
                or "weather advisory or committee" in lower
                or ("could it be" in lower and lower.endswith("?"))
                or lower.startswith("the weather data provided is")
            ):
                continue
            filtered_lines.append(line)

        if filtered_lines:
            s = " ".join(filtered_lines)

        # 3. Deterministic Emoji Removal
        s = EMOJI_PATTERN.sub("", s)

        # 4. Remove Markdown code blocks, bolding, italics, headers, links
        s = re.sub(r"```[\s\S]*?```", "", s)
        s = re.sub(r"`[^`]*`", "", s)
        s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
        s = re.sub(r"\bhttps?://\S+", "", s)
        s = re.sub(r"^#{1,6}\s*", "", s, flags=re.MULTILINE)
        s = re.sub(r"^\s*[-*•+]\s+", "", s, flags=re.MULTILINE)
        s = re.sub(r"[*_~`#>]", "", s)

        # 5. Phonetic Weather Unit Expansions (Language aware)
        l_code = (language_code or "").lower()
        if l_code.startswith("hi") or "hinglish" in l_code:
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 डिग्री सेल्सियस", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*F\b", r"\1 डिग्री फ़ारेनहाइट", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\b", r"\1 डिग्री", s)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*km/h\b", r"\1 किलोमीटर प्रति घंटा", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*mm\b", r"\1 मिलीमीटर", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*%", r"\1 प्रतिशत", s)
        elif l_code.startswith("od") or l_code.startswith("or"):
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 ଡିଗ୍ରୀ ସେଲସିୟସ", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\b", r"\1 ଡିଗ୍ରୀ", s)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*km/h\b", r"\1 କିଲୋମିଟର ପ୍ରତି ଘଣ୍ଟା", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*mm\b", r"\1 ମିଲିମିଟର", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*%", r"\1 ପ୍ରତିଶତ", s)
        elif l_code.startswith("te"):
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 డిగ్రీల సెల్సియస్", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\b", r"\1 డిగ్రీలు", s)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*%", r"\1 శాతం", s)
        elif l_code.startswith("ta"):
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 டிகிரி செல்சியஸ்", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\b", r"\1 டிகிரி", s)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*%", r"\1 சதவீதம்", s)
        else:
            # English / Default
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*C\b", r"\1 degrees Celsius", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\s*F\b", r"\1 degrees Fahrenheit", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*°\b", r"\1 degrees", s)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*km/h\b", r"\1 kilometers per hour", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*m/s\b", r"\1 meters per second", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*mm\b", r"\1 millimeters", s, flags=re.IGNORECASE)
            s = re.sub(r"(\d+(?:\.\d+)?)\s*%", r"\1 percent", s)

        # 6. Normalize punctuation and collapse whitespaces
        s = re.sub(r"[()\[\]{}\"\']", "", s)
        s = re.sub(r"\s+", " ", s)
        s = re.sub(r"\s+([.,!?])", r"\1", s)
        s = s.strip()

        cls.assert_speech_invariants(s)
        return s

    @classmethod
    def assert_speech_invariants(cls, speech_text: str) -> None:
        """Enforces hard invariants that speech text has no emojis or raw markdown."""
        if not speech_text:
            return
        assert not EMOJI_PATTERN.search(speech_text), f"Speech text invariant failed: emoji detected in {speech_text!r}"
        assert not re.search(r"[*_~`#]", speech_text), f"Speech text invariant failed: markdown symbols detected in {speech_text!r}"

