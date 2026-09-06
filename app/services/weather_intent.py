import re


INTENTS = {
    "CURRENT_WEATHER",
    "RAIN",
    "FORECAST",
    "TEMPERATURE",
    "WIND",
    "HUMIDITY",
    "TRAVEL",
    "OUTDOOR",
    "ACTIVITY",
    "AGRICULTURE",
    "FLOOD",
    "ALERTS",
    "GENERAL_WEATHER",
    "EXPLAIN",
    "COMFORT",
}


def has_any(
    text: str,
    patterns: list[str]
) -> bool:

    return any(
        re.search(
            pattern,
            text
        )
        for pattern in patterns
    )


def detect_weather_intent(
    question: str
) -> str:

    text = question.lower().strip()

    # ---------------------------------------------------------
    # 0. PERSONAL COMFORT SCORE
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\bcomfort\b",
            r"\bcomfortable\b",
            r"\bcomfort score\b",
            r"\bcomfort level\b",
            r"\bhow comfortable\b",
            r"\bpersonal comfort\b",
            r"\bcomfort index\b",
            r"\bwear today\b",
            r"\bwhat to wear\b",
            r"\bclothing\b",
            r"\bgarmi kitni\b",
            r"\bchipchipahat\b",
            r"\bsuffocating\b",
            r"\bhumdrum\b",
            r"\bpleasant\b",
        ]
    ):
        return "COMFORT"

    # ---------------------------------------------------------
    # 0. EXPLANATION / "WHY" QUESTIONS (e.g. Why does it feel so hot? Why 34°C feels like 40°C?)
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\bwhy\b",
            r"\bwhy is it\b",
            r"\bwhy does it\b",
            r"\bwhy feels\b",
            r"\bfeels hotter\b",
            r"\bfeels colder\b",
            r"\bfeels like\b",
            r"\bexplain\b",
            r"\bhow come\b",
            r"\bkyun\b",
            r"\bkyu\b",
            r"\bkyon\b",
            r"क्यों",
            r"ఎందుకు",
            r"ஏன்",
            r"କାହିଁକି",
        ]
    ):
        return "EXPLAIN"

    # ---------------------------------------------------------
    # 1. HIGH-PRIORITY ACTIVITY / OUTDOOR QUESTIONS
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            # English
            r"\brun\b",
            r"\brunning\b",
            r"\bjog\b",
            r"\bjogging\b",
            r"\bwalk\b",
            r"\bwalking\b",
            r"\bcycle\b",
            r"\bcycling\b",
            r"\bbike\b",
            r"\bcricket\b",
            r"\bfootball\b",
            r"\bsoccer\b",
            r"\bswim\b",
            r"\bswimming\b",
            r"\bhike\b",
            r"\bhiking\b",
            r"\bexercise\b",
            r"\bworkout\b",
            r"\bpicnic\b",
            r"\bbeach\b",
            r"\boutdoor\b",
            r"\bgo outside\b",
            r"\bgo out\b",

            # Hindi / Romanized Hindi
            r"\bjogging\b",
            r"\bdaud\b",
            r"\bdaudna\b",
            r"\bghoomne\b",
            r"\bbahar\b",
            r"\bbahar jana\b",
            r"\bkhel\b",
            r"\bkhelna\b",
            r"\bexercise kar\b",
            r"\bworkout kar\b",

            # Telugu / Romanized Telugu
            r"బయటికి",
            r"బయట",
            r"జాగింగ్",
            r"నడవ",
            r"పరుగ",
            r"ఆట",
            r"\bjogging\b",
            r"\bnadavadam\b",
            r"\bparigettadam\b",
            r"\bbayataki\b",
            r"\bbayata\b",
            r"\bgame aad\b",

            # Tamil
            r"வெளியே",
            r"ஓட்டம்",
            r"நடக்க",
            r"ஜாக்கிங்",

            # Bengali
            r"বাইরে",
            r"দৌড়",
            r"হাঁটা",

            # Kannada
            r"ಹೊರಗೆ",
            r"ಓಟ",
            r"ನಡಿಗೆ",

            # Malayalam
            r"പുറത്ത്",
            r"ഓട്ടം",
            r"നടക്ക",

            # Marathi
            r"बाहेर",
            r"धावणे",
            r"चालणे",

            # Gujarati
            r"બહાર",
            r"દોડવું",
            r"ચાલવું",

            # Punjabi
            r"ਬਾਹਰ",
            r"ਦੌੜ",
            r"ਤੁਰਨਾ",

            # Odia
            r"ବାହାରେ",
            r"ଦୌଡ଼",
            r"ଚାଲିବା",
        ]
    ):

        # Specific travel should remain TRAVEL.
        if has_any(
            text,
            [
                r"\btravel\b",
                r"\btrip\b",
                r"\bjourney\b",
                r"\bdrive\b",
                r"\bdriving\b",
                r"\bcommute\b",
                r"\broad\b",
                r"\bsafar\b",
                r"\byatra\b",
                r"\bयात्रा\b",
                r"\bபயணம்\b",
                r"\bಪ್ರಯಾಣ\b",
                r"\bప్రయాణం\b",
            ]
        ):
            return "TRAVEL"

        return "ACTIVITY"

    # ---------------------------------------------------------
    # 2. TRAVEL / SAFETY
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\btravel\b",
            r"\btravelling\b",
            r"\btraveling\b",
            r"\btrip\b",
            r"\bjourney\b",
            r"\bdrive\b",
            r"\bdriving\b",
            r"\bcommute\b",
            r"\broad trip\b",
            r"\bis it safe\b",
            r"\bsafe to drive\b",
            r"\bsafe to travel\b",
            r"\bgo safely\b",

            r"\bsafar\b",
            r"\byatra\b",
            r"\btravel kar\b",
            r"\bdrive kar\b",
            r"\bgaadi\b",
            r"\bसफ़र\b",
            r"\bयात्रा\b",
            r"\bसुरक्षित\b",

            r"ಪ್ರಯಾಣ",
            r"ప్రయాణం",
            r"பயணம்",
            r"ভ্রমণ",
            r"ಪ್ರಯಾಣ",
        ]
    ):
        return "TRAVEL"

    # ---------------------------------------------------------
    # 3. RAIN
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\brain\b",
            r"\brainfall\b",
            r"\bshowers?\b",
            r"\bprecipitation\b",
            r"\bumbrella\b",

            r"\bba(?:a)?rish\b",
            r"\bbarish\b",
            r"\bvarsha\b",
            r"बारिश",
            r"वर्षा",

            r"వర్షం",
            r"వాన",
            r"వర్షాలు",
            r"\bvarsham\b",
            r"\bvarshalu\b",
            r"\bvaanam\b",
            r"\bvaana\b",
            r"\bvarsham\s+padutunda\b",
            r"\bvarsham\s+paduthunda\b",
            r"\bvaana\s+padutunda\b",
            r"\bvaana\s+paduthunda\b",

            r"மழை",
            r"বৃষ্টি",
            r"ಮಳೆ",
            r"മഴ",
            r"पाऊस",
            r"વરસાદ",
            r"ਮੀਂਹ",
            r"ବର୍ଷା",
        ]
    ):
        return "RAIN"

    # ---------------------------------------------------------
    # 4. FLOOD
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\bflood\b",
            r"\bflooding\b",
            r"\bwater level\b",
            r"\briver\b",
            r"बाढ़",
            r"नदी",
            r"వరద",
            r"நீர்ப்பெருக்கு",
            r"வெள்ளம்",
            r"মাঠে বন্যা",
            r"ಬರ",
            r"വെള്ളപ്പൊക്കം",
            r"पूर",
            r"પૂર",
            r"ਬਾਢ",
            r"ବନ୍ୟା",
        ]
    ):
        return "FLOOD"

    # ---------------------------------------------------------
    # 5. ALERTS
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\balert\b",
            r"\bwarning\b",
            r"\bdanger\b",
            r"\bsevere weather\b",
            r"\bcyclone\b",
            r"\bstorm warning\b",

            r"चेतावनी",
            r"अलर्ट",
            r"హెచ్చరిక",
            r"எச்சரிக்கை",
            r"সতর্কতা",
            r"ಎಚ್ಚರಿಕೆ",
            r"മുന്നറിയിപ്പ്",
            r"इशारा",
            r"ચેતવણી",
            r"ਚੇਤਾਵਨੀ",
            r"ଚେତାବନୀ",
        ]
    ):
        return "ALERTS"

    # ---------------------------------------------------------
    # 6. TEMPERATURE
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\btemperature\b",
            r"\btemp\b",
            r"\bhot\b",
            r"\bheat\b",
            r"\bcold\b",
            r"\bjacket\b",
            r"\bcoat\b",

            r"गरमी",
            r"गर्मी",
            r"तापमान",
            r"ठंड",

            r"వేడి",
            r"ఉష్ణోగ్రత",

            r"வெப்பம்",
            r"வெப்பநிலை",

            r"তাপমাত্রা",
            r"গরম",
        ]
    ):
        return "TEMPERATURE"

    # ---------------------------------------------------------
    # 7. WIND
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\bwind\b",
            r"\bbreeze\b",
            r"\bgust\b",
            r"हवा",
            r"वायु",
            r"గాలి",
            r"காற்று",
            r"বাতাস",
            r"ಗಾಳಿ",
            r"കാറ്റ്",
        ]
    ):
        return "WIND"

    # ---------------------------------------------------------
    # 8. HUMIDITY
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\bhumidity\b",
            r"\bhumid\b",
            r"\bmoisture\b",
            r"नमी",
            r"आर्द्रता",
            r"తేమ",
            r"ஈரப்பதம்",
            r"আর্দ্রতা",
            r"ಆರ್ದ್ರತೆ",
            r"ഈർപ്പം",
            r"आर्द्रता",
            r"ભેજ",
            r"ਨਮੀ",
            r"ଆର୍ଦ୍ରତା",
        ]
    ):
        return "HUMIDITY"

    # ---------------------------------------------------------
    # 9. FORECAST / FUTURE
    # ---------------------------------------------------------

    if has_any(
        text,
        [
            r"\btomorrow\b",
            r"\btonight\b",
            r"\blater\b",
            r"\bforecast\b",
            r"\bnext hour\b",
            r"\bnext few hours\b",
            r"\bnext week\b",
            r"\bweekend\b",

            r"\bkal\b",
            r"\baaj raat\b",
            r"\bbaad mein\b",

            r"कल",
            r"आज रात",
            r"बाद में",

            r"రేపు",
            r"ఈ రాత్రి",

            r"நாளை",
            r"இன்றிரவு",

            r"কাল",
            r"আগামীকাল",
        ]
    ):
        return "FORECAST"

    # ---------------------------------------------------------
    # 10. DEFAULT
    # ---------------------------------------------------------

    return "CURRENT_WEATHER"
