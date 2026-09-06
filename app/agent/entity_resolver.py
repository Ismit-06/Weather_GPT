import re
from dataclasses import dataclass

from app.agent.context_manager import ConversationContext


@dataclass
class ResolvedQuery:
    activity: str | None = None
    time_text: str | None = None
    day_text: str | None = None
    location_text: str | None = None
    ambiguous_time: str | None = None
    uses_previous_activity: bool = False
    uses_previous_time: bool = False
    uses_previous_day: bool = False


def extract_time(text: str) -> str | None:

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
            flags=re.IGNORECASE,
        )

        if match:
            return match.group(0)

    return None


def extract_day(text: str) -> str | None:

    patterns = [
        "tomorrow morning",
        "tomorrow evening",
        "tomorrow night",
        "this morning",
        "this evening",
        "tonight",
        "tomorrow",
        "today",

        "kal subah",
        "kal shaam",
        "aaj raat",
        "aaj",
        "kal",

        "कल सुबह",
        "कल शाम",
        "आज रात",
        "आज",
        "कल",

        "రేపు ఉదయం",
        "రేపు",
        "ఈరోజు",
        "ఈ రాత్రి",

        "நாளை",
        "இன்று",

        "আগামীকাল",
        "আজ",

        "ನಾಳೆ",
        "ಇಂದು",

        "നാളെ",
        "ഇന്ന്",

        "उद्या",
        "आज",

        "આવતીકાલે",
        "આજે",

        "ਕੱਲ੍ਹ",
        "ਅੱਜ",

        "ଆସନ୍ତାକାଲି",
        "ଆଜି",
        
        # Sunday/weekdays
        "sunday",
        "monday",
        "tuesday",
        "wednesday",
        "thursday",
        "friday",
        "saturday",
    ]

    lower = text.lower()

    # Longest first.
    patterns.sort(
        key=len,
        reverse=True,
    )

    for value in patterns:

        if value.lower() in lower:
            return value

    return None


def extract_activity(
    text: str,
) -> str | None:

    lower = text.lower()

    activities = [
        # Umbrella & Rain gear
        ("carry umbrella", "carry_umbrella"),
        ("carry an umbrella", "carry_umbrella"),
        ("take umbrella", "carry_umbrella"),
        ("take an umbrella", "carry_umbrella"),
        ("need umbrella", "carry_umbrella"),
        ("need an umbrella", "carry_umbrella"),
        ("umbrella", "carry_umbrella"),
        ("raincoat", "carry_umbrella"),
        ("chhatri", "carry_umbrella"),
        ("chhata", "carry_umbrella"),

        # Laundry & Clothes
        ("hang clothes", "drying_clothes"),
        ("hanging clothes", "drying_clothes"),
        ("dry clothes", "drying_clothes"),
        ("drying clothes", "drying_clothes"),
        ("clothes outside", "drying_clothes"),
        ("wash clothes", "drying_clothes"),
        ("laundry", "drying_clothes"),
        ("kapde", "drying_clothes"),

        # Vehicle washing
        ("wash my bike", "wash_bike"),
        ("wash bike", "wash_bike"),
        ("bike wash", "wash_bike"),
        ("wash my car", "wash_car"),
        ("wash car", "wash_car"),
        ("car wash", "wash_car"),
        ("gaadi", "wash_car"),

        # Commute & Leaving
        ("leave college", "leaving_college"),
        ("leaving college", "leaving_college"),
        ("leave office", "leaving_office"),
        ("leaving office", "leaving_office"),
        ("commute", "commute"),
        ("heading out", "commute"),
        ("going out", "commute"),
        ("safe to travel", "travel"),
        ("travel", "travel"),
        ("driving", "travel"),
        ("drive", "travel"),
        ("road trip", "travel"),

        # Plants & Gardening
        ("water plants", "gardening"),
        ("water my plants", "gardening"),
        ("watering plants", "gardening"),

        # Sports & Fitness
        ("running", "running"),
        ("run", "running"),
        ("jogging", "running"),
        ("jog", "running"),
        ("cycling", "cycling"),
        ("cycle", "cycling"),
        ("walking", "walking"),
        ("walk", "walking"),
        ("gym", "gym"),
        ("workout", "exercise"),
        ("exercise", "exercise"),
        ("cricket", "cricket"),
        ("football", "football"),
        ("soccer", "football"),
        ("swimming", "swimming"),
        ("swim", "swimming"),
        ("hiking", "hiking"),
        ("hike", "hiking"),
        ("trekking", "hiking"),
        ("trek", "hiking"),
        ("photography", "photography"),
        ("photoshoot", "photography"),
        ("photo", "photography"),
        ("beach", "beach"),
        ("picnic", "picnic"),
    ]

    for phrase, activity in activities:
        if phrase in lower:
            return activity

    return None


def extract_location(
    text: str,
) -> str | None:

    # Capture a named place after common location
    # prepositions without swallowing time/day phrases or temporal adverbs.
    patterns = [
        r"\b(?:in|at|near)\s+([A-Za-z\u0900-\u0D7F][A-Za-z\u0900-\u0D7F .'-]{1,60}?)(?=\s+(?:today|tomorrow|tonight|this|on|at|around|right now|now|currently|presently|soon|later|aaj|kal)\b|[?.!,]|$)",
        r"\b(?:in|at|near)\s+([A-Za-z\u0900-\u0D7F][A-Za-z\u0900-\u0D7F .'-]{1,60})$",
    ]

    for pattern in patterns:

        match = re.search(
            pattern,
            text,
            flags=re.IGNORECASE,
        )

        if not match:
            continue

        value = match.group(1).strip(
            " .,!?"
        )

        if not value:
            continue

        # Strip trailing temporal words
        value = re.sub(r"(?i)\s+(?:right now|now|currently|presently|today|tomorrow|tonight|aaj|kal)$", "", value).strip(" .,!?")

        # Don't accidentally interpret common non-location phrases as place names.
        blocked = {
            "the weather",
            "home",
            "there",
            "here",
            "morning",
            "afternoon",
            "evening",
            "night",
            "right now",
            "now",
            "currently",
        }

        if value.lower() in blocked:
            continue

        # These refer to the user's current location,
        # not a named destination.
        if is_current_location_request(text):
            return None

        return value

    return None


def is_current_location_request(
    text: str,
) -> bool:

    normalized = (
        text.strip()
        .lower()
        .rstrip("?!.")
    )

    return normalized in {
        "here",
        "my location",
        "current location",
        "where i am",
        "where i am now",
        "near me",
        "what about here",
        "how about here",
        "same thing here",
        "what about my location",
        "what about current location",
    }


def extract_followup_location(
    text: str,
) -> str | None:

    patterns = [
        r"^\s*what about\s+([A-Za-z][A-Za-z .'-]{1,60})\s*\??\s*$",
        r"^\s*how about\s+([A-Za-z][A-Za-z .'-]{1,60})\s*\??\s*$",
        r"^\s*same thing in\s+([A-Za-z][A-Za-z .'-]{1,60})\s*\??\s*$",
    ]

    for pattern in patterns:

        match = re.search(
            pattern,
            text,
            flags=re.IGNORECASE,
        )

        if not match:
            continue

        value = match.group(1).strip(
            " .,!?"
        )

        if value:
            return value

    return None


def resolve_query(
    question: str,
    context: ConversationContext,
) -> ResolvedQuery:

    text = question.strip()

    activity = extract_activity(
        text
    )

    time_text = extract_time(
        text
    )

    day_text = extract_day(
        text
    )

    location_text = extract_location(
        text
    )

    if location_text is None:

        location_text = extract_followup_location(
            text
        )

    lower = text.lower()

    uses_previous_activity = False
    uses_previous_time = False
    uses_previous_day = False

    # "same thing", "same activity", "again"
    if (
        activity is None
        and context.activity
        and any(
            phrase in lower
            for phrase in [
                "same thing",
                "same activity",
                "same",
                "again",
            ]
        )
    ):
        activity = context.activity
        uses_previous_activity = True

    # "what about 8?", "and 8?"
    if (
        time_text is not None
        and context.target_local_time
    ):
        uses_previous_activity = (
            activity is None
            and context.activity is not None
        )

        if activity is None:
            activity = context.activity

    # "and tomorrow?", "what about Sunday?"
    if (
        day_text is not None
        and context.activity
        and activity is None
    ):
        activity = context.activity
        uses_previous_activity = True

    # Follow-up with only "what about..." / "and..."
    if (
        activity is None
        and time_text is None
        and day_text is None
        and context.activity
    ):
        activity = context.activity
        uses_previous_activity = True

    # A time-less follow-up keeps the previous time.
    if (
        time_text is None
        and context.target_local_time
        and any(
            phrase in lower
            for phrase in [
                "same time",
                "same",
                "what about",
                "and ",
            ]
        )
    ):
        uses_previous_time = True

    if (
        day_text is None
        and context.target_local_time
        and any(
            phrase in lower
            for phrase in [
                "what about",
                "same",
                "and ",
            ]
        )
    ):
        uses_previous_day = True

    ambiguous_time = None

    # A bare hour such as "7" or "at 7" is ambiguous
    # unless AM/PM/daypart was provided.
    bare_hour = re.search(
        r"\b(?:at|around|by)?\s*(\d{1,2})\b",
        text,
        flags=re.IGNORECASE,
    )

    if (
        bare_hour
        and time_text is None
        and not any(
            marker in lower
            for marker in [
                "am",
                "pm",
                "a.m.",
                "p.m.",
                "baje",
                "बजे",
                "morning",
                "afternoon",
                "evening",
                "night",
                "subah",
                "dopahar",
                "shaam",
                "raat",
                "सुबह",
                "दोपहर",
                "शाम",
                "रात",
            ]
        )
    ):
        ambiguous_time = bare_hour.group(1)

    return ResolvedQuery(
        activity=activity,
        time_text=time_text,
        day_text=day_text,
        location_text=location_text,
        ambiguous_time=ambiguous_time,
        uses_previous_activity=uses_previous_activity,
        uses_previous_time=uses_previous_time,
        uses_previous_day=uses_previous_day,
    )
