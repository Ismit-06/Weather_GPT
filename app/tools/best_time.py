from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from app.services.met_weather import get_weather
from app.tools.activity_conditions import (
    assess_activity_conditions,
)


WEEKDAYS = {
    "monday": 0,
    "tuesday": 1,
    "wednesday": 2,
    "thursday": 3,
    "friday": 4,
    "saturday": 5,
    "sunday": 6,
}


def _parse_time(
    value: str,
) -> datetime:
    return datetime.fromisoformat(
        value.replace(
            "Z",
            "+00:00",
        )
    )


def _resolve_date(
    now: datetime,
    value: str,
):
    text = (
        value or "tomorrow"
    ).lower().strip()

    if text in {
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
    }:
        return (
            now +
            timedelta(days=1)
        ).date()

    if text in WEEKDAYS:

        target = WEEKDAYS[text]

        days_ahead = (
            target -
            now.weekday()
        ) % 7

        if days_ahead == 0:
            days_ahead = 7

        return (
            now +
            timedelta(
                days=days_ahead
            )
        ).date()

    return now.date()


async def get_best_activity_time(
    latitude: float,
    longitude: float,
    activity: str,
    date_text: str = "tomorrow",
    timezone_name: str = "Asia/Kolkata",
) -> dict:

    weather = await get_weather(
        latitude=latitude,
        longitude=longitude,
    )

    forecast = weather.get(
        "forecast",
        [],
    )

    if not forecast:
        return {
            "status": "error",
            "message": "No forecast data available.",
        }

    timezone = ZoneInfo(
        timezone_name
    )

    now = datetime.now(
        timezone
    )

    target_date = _resolve_date(
        now,
        date_text,
    )

    candidates = []

    for item in forecast:

        raw_time = item.get("time")

        if not raw_time:
            continue

        try:
            timestamp = _parse_time(
                raw_time
            )
        except Exception:
            continue

        local_time = timestamp.astimezone(
            timezone
        )

        if local_time.date() != target_date:
            continue

        if local_time.hour < 5:
            continue

        if local_time.hour >= 23:
            continue

        point = {
            "time": raw_time,
            "temperature_c":
                item.get("temperature_c"),
            "rainfall_mm":
                item.get("precipitation_mm"),
            "humidity_pct":
                item.get(
                    "relative_humidity_pct"
                ),
            "wind_speed_ms":
                item.get(
                    "wind_speed_ms"
                ),
            "wind_direction_deg":
                item.get(
                    "wind_direction_deg"
                ),
            "condition":
                item.get("symbol_code"),
        }

        assessment = (
            assess_activity_conditions(
                activity=activity,
                forecast=point,
            )
        )

        # Outdoor activity + measurable rain:
        # don't recommend that point.
        rainfall = point.get(
            "rainfall_mm"
        )

        if (
            activity in {
                "running",
                "cycling",
                "football",
                "cricket",
                "hiking",
                "walking",
                "picnic",
                "outdoor",
            }
            and isinstance(
                rainfall,
                (int, float),
            )
            and rainfall > 0
        ):
            assessment["score"] = 0.0
            assessment["level"] = "UNFAVORABLE"
            assessment["reasons"] = [
                "Rainfall is expected during this period."
            ]

        candidates.append(
            {
                "local_time": local_time,
                "forecast": point,
                "score": assessment["score"],
                "level": assessment["level"],
                "reasons": assessment["reasons"],
            }
        )

    if not candidates:
        return {
            "status": "error",
            "message": (
                "No usable forecast points were found."
            ),
        }

    best_score = max(
        item["score"]
        for item in candidates
    )

    selected = [
        item
        for item in candidates
        if item["score"] >= best_score - 3
    ]

    selected.sort(
        key=lambda x: x["local_time"]
    )

    windows = []
    current = []

    for item in selected:

        if not current:
            current = [item]
            continue

        previous = current[-1][
            "local_time"
        ]

        if (
            item["local_time"] -
            previous
        ) <= timedelta(hours=1):

            current.append(item)

        else:

            windows.append(current)
            current = [item]

    if current:
        windows.append(current)

    summaries = []

    for window in windows:

        average_score = (
            sum(
                item["score"]
                for item in window
            )
            / len(window)
        )

        summaries.append(
            {
                "start":
                    window[0]["local_time"].isoformat(),

                "end":
                    window[-1]["local_time"].isoformat(),

                "average_score":
                    round(
                        average_score,
                        1,
                    ),

                "best_score":
                    max(
                        item["score"]
                        for item in window
                    ),

                "level":
                    max(
                        window,
                        key=lambda x:
                            x["score"],
                    )["level"],

                "points":
                    window,
            }
        )

    summaries.sort(
        key=lambda x: (
            x["average_score"],
            x["best_score"],
        ),
        reverse=True,
    )

    best_window = summaries[0]

    start = datetime.fromisoformat(
        best_window["start"]
    )

    end = datetime.fromisoformat(
        best_window["end"]
    )

    # ---------------------------------------------------------
    # Confidence
    # ---------------------------------------------------------

    if len(summaries) == 1:

        confidence = "HIGH"

    else:

        second_score = summaries[1]["average_score"]

        difference = (
            best_window["average_score"]
            - second_score
        )

        if difference >= 8:

            confidence = "HIGH"

        elif difference >= 3:

            confidence = "MEDIUM"

        else:

            confidence = "LOW"

    # ---------------------------------------------------------
    # Average weather during the best window
    # ---------------------------------------------------------

    points = best_window.get(
        "points",
        []
    )

    temperatures = [
        point["forecast"].get(
            "temperature_c"
        )
        for point in points
        if isinstance(
            point["forecast"].get(
                "temperature_c"
            ),
            (int, float)
        )
    ]

    humidities = [
        point["forecast"].get(
            "humidity_pct"
        )
        for point in points
        if isinstance(
            point["forecast"].get(
                "humidity_pct"
            ),
            (int, float)
        )
    ]

    winds = [
        point["forecast"].get(
            "wind_speed_ms"
        )
        for point in points
        if isinstance(
            point["forecast"].get(
                "wind_speed_ms"
            ),
            (int, float)
        )
    ]

    rainfall_values = [
        point["forecast"].get(
            "rainfall_mm"
        )
        for point in points
        if isinstance(
            point["forecast"].get(
                "rainfall_mm"
            ),
            (int, float)
        )
    ]

    summary_weather = {
        "temperature_c":
            round(
                sum(temperatures) /
                len(temperatures),
                1,
            )
            if temperatures
            else None,

        "humidity_pct":
            round(
                sum(humidities) /
                len(humidities),
                1,
            )
            if humidities
            else None,

        "wind_speed_ms":
            round(
                sum(winds) /
                len(winds),
                1,
            )
            if winds
            else None,

        "max_rainfall_mm":
            round(
                max(rainfall_values),
                2,
            )
            if rainfall_values
            else None,
    }

    # ---------------------------------------------------------
    # Forecast coverage
    # ---------------------------------------------------------

    forecast_points_available = len(candidates)

    if forecast_points_available >= 12:
        forecast_coverage = "GOOD"
    elif forecast_points_available >= 6:
        forecast_coverage = "PARTIAL"
    else:
        forecast_coverage = "LIMITED"

    recommendation = {
        "activity":
            activity,

        "date":
            target_date.isoformat(),

        "start":
            start.strftime(
                "%I:%M %p"
            ).lstrip("0"),

        "end":
            end.strftime(
                "%I:%M %p"
            ).lstrip("0"),

        "score":
            best_window["average_score"],

        "level":
            best_window["level"],

        "confidence":
            confidence,

        "forecast_coverage":
            forecast_coverage,

        "forecast_points_available":
            forecast_points_available,

        "weather":
            summary_weather,

        "reasons":
            sorted(
                {
                    reason
                    for point in best_window.get(
                        "points",
                        []
                    )
                    for reason in point.get(
                        "reasons",
                        []
                    )
                }
            ),
    }

    return {
        "status": "success",

        "activity":
            activity,

        "date":
            target_date.isoformat(),

        "recommendation":
            recommendation,

        "best_window":
            best_window,

        "other_windows":
            summaries[1:4],

        "source":
            weather.get(
                "source",
                "MET Norway",
            ),

        "updated_at":
            weather.get(
                "updated_at"
            ),
    }
