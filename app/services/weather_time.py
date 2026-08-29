import re
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo


WEEKDAYS = {
    "monday": 0,
    "tuesday": 1,
    "wednesday": 2,
    "thursday": 3,
    "friday": 4,
    "saturday": 5,
    "sunday": 6,
}


def normalize_day(
    day_text: str | None,
    now: datetime,
):
    text = (day_text or "").lower().strip()

    if any(
        x in text
        for x in [
            "tomorrow",
            "kal",
            "कल",
            "రేపు",
            "நாளை",
            "আগামীকাল",
            "ನಾಳೆ",
            "നാളെ",
            "उद्या",
            "આવતીકાલે",
            "ਕੱਲ੍ਹ",
            "ଆସନ୍ତାକାଲି",
        ]
    ):
        return (now + timedelta(days=1)).date()

    for name, weekday in WEEKDAYS.items():

        if name in text:

            days_ahead = (
                weekday -
                now.weekday()
            ) % 7

            # A weekday mentioned today means the next
            # occurrence unless the caller explicitly means today.
            if days_ahead == 0:
                days_ahead = 7

            return (
                now +
                timedelta(days=days_ahead)
            ).date()

    return now.date()


def normalize_time(
    time_text: str | None,
    day_text: str | None,
    timezone_name: str,
    now: datetime | None = None,
    default_hour: int | None = None,
) -> datetime:

    timezone = ZoneInfo(timezone_name)

    current = (
        now.astimezone(timezone)
        if now
        else datetime.now(timezone)
    )

    date_value = normalize_day(
        day_text,
        current,
    )

    time_value = (
        time_text or ""
    ).lower().strip()

    day_value = (
        day_text or ""
    ).lower().strip()

    # If the caller supplies a previous hour, preserve it.
    hour = (
        default_hour
        if default_hour is not None
        else current.hour
    )

    minute = 0

    # ---------------------------------------------------------
    # Explicit AM/PM time
    # ---------------------------------------------------------

    match = re.search(
        r"(\d{1,2})(?::(\d{2}))?\s*(am|pm)",
        time_value,
    )

    if match:

        hour = int(match.group(1))

        minute = int(
            match.group(2) or 0
        )

        meridiem = match.group(3)

        if meridiem == "pm" and hour != 12:
            hour += 12

        elif meridiem == "am" and hour == 12:
            hour = 0

    else:

        # -----------------------------------------------------
        # Hindi/Romanized clock time
        # -----------------------------------------------------

        match = re.search(
            r"(\d{1,2})\s*(?:baje|बजे)",
            time_value,
        )

        if match:

            hour = int(
                match.group(1)
            )

            if any(
                x in day_value
                for x in [
                    "raat",
                    "रात",
                    "night",
                ]
            ):

                if hour < 12:
                    hour += 12

            elif any(
                x in day_value
                for x in [
                    "shaam",
                    "शाम",
                    "evening",
                ]
            ):

                if hour < 12:
                    hour += 12

            elif any(
                x in day_value
                for x in [
                    "subah",
                    "सुबह",
                    "morning",
                ]
            ):

                if hour == 12:
                    hour = 0

    # ---------------------------------------------------------
    # Daypart when no explicit clock time exists.
    # ---------------------------------------------------------

    if not time_text:

        if any(
            x in day_value
            for x in [
                "morning",
                "subah",
                "सुबह",
                "ఉదయం",
                "காலை",
                "সকাল",
                "ಬೆಳಗ್ಗೆ",
                "രാവിലെ",
            ]
        ):
            hour = 8

        elif any(
            x in day_value
            for x in [
                "afternoon",
                "dopahar",
                "दोपहर",
                "మధ్యాహ్నం",
                "மதியம்",
                "দুপুর",
                "ಮಧ್ಯಾಹ್ನ",
                "ഉച്ചയ്ക്ക്",
            ]
        ):
            hour = 14

        elif any(
            x in day_value
            for x in [
                "evening",
                "shaam",
                "शाम",
                "సాయంత్రం",
                "மாலை",
                "সন্ধ্যা",
                "ಸಂಜೆ",
                "വൈകുന്നേരം",
            ]
        ):
            hour = 18

        elif any(
            x in day_value
            for x in [
                "night",
                "raat",
                "रात",
                "రాత్రి",
                "இரவு",
                "রাত",
                "ರಾತ್ರಿ",
                "രാത്രി",
            ]
        ):
            hour = 21

    return datetime(
        year=date_value.year,
        month=date_value.month,
        day=date_value.day,
        hour=hour,
        minute=minute,
        tzinfo=timezone,
    )
