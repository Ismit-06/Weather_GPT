import re
from dataclasses import dataclass


@dataclass
class WeatherQuery:
    intent: str
    activity: str | None = None
    time_text: str | None = None
    day_text: str | None = None


ACTIVITY_PATTERNS = [
    (r"\brun\b|\brunning\b|\bjog\b|\bjogging\b", "running"),
    (r"\bwalk\b|\bwalking\b", "walking"),
    (r"\bcycle\b|\bcycling\b|\bbike\b", "cycling"),
    (r"\bhike\b|\bhiking\b", "hiking"),
    (r"\bcricket\b", "cricket"),
    (r"\bfootball\b|\bsoccer\b", "football"),
    (r"\bswim\b|\bswimming\b", "swimming"),
    (r"\bworkout\b|\bexercise\b", "exercise"),
    (r"\bpig?nic\b", "picnic"),
    (r"\bbeach\b", "beach"),
]


def extract_activity(
    text: str
) -> str | None:

    for pattern, activity in ACTIVITY_PATTERNS:
        if re.search(
            pattern,
            text,
            flags=re.IGNORECASE
        ):
            return activity

    return None


def extract_time(
    text: str
) -> str | None:

    patterns = [
        r"\b\d{1,2}:\d{2}\s*(?:am|pm)\b",
        r"\b\d{1,2}\s*(?:am|pm)\b",
        r"\b\d{1,2}\s*(?:a\.m\.|p\.m\.)\b",
        r"\b\d{1,2}\s*baje\b",
        r"\b\d{1,2}\s*बजे\b",
    ]

    for pattern in patterns:
        match = re.search(
            pattern,
            text,
            flags=re.IGNORECASE
        )

        if match:
            return match.group(0)

    return None


def extract_day(
    text: str
) -> str | None:

    patterns = [
        r"\btomorrow\b",
        r"\btonight\b",
        r"\btoday\b",
        r"\bthis evening\b",
        r"\bthis morning\b",
        r"\btomorrow morning\b",
        r"\btomorrow evening\b",
        r"\btomorrow night\b",

        r"\bkal\b",
        r"\baaj\b",
        r"\baaj raat\b",
        r"\bkal subah\b",
        r"\bkal shaam\b",

        r"कल",
        r"आज",
        r"आज रात",
        r"कल सुबह",
        r"कल शाम",

        r"రేపు",
        r"ఈరోజు",
        r"ఈ రాత్రి",

        r"நாளை",
        r"இன்று",

        r"আগামীকাল",
        r"আজ",

        r"ನಾಳೆ",
        r"ಇಂದು",

        r"നാളെ",
        r"ഇന്ന്",

        r"उद्या",
        r"आज",

        r"આવતીકાલે",
        r"આજે",

        r"ਕੱਲ੍ਹ",
        r"ਅੱਜ",

        r"ଆସନ୍ତାକାଲି",
        r"ଆଜି",
    ]

    # Prefer longer expressions first.
    patterns.sort(
        key=len,
        reverse=True
    )

    for pattern in patterns:
        match = re.search(
            pattern,
            text,
            flags=re.IGNORECASE
        )

        if match:
            return match.group(0)

    return None


def parse_weather_query(
    question: str,
    intent: str
) -> WeatherQuery:

    text = question.strip()

    return WeatherQuery(
        intent=intent,
        activity=extract_activity(text),
        time_text=extract_time(text),
        day_text=extract_day(text),
    )
