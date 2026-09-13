from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from app.services.met_weather import get_weather


def _parse_time(value: str) -> datetime:
    return datetime.fromisoformat(
        value.replace(
            "Z",
            "+00:00",
        )
    )


def _is_rain(
    item: dict,
) -> bool:

    value = item.get(
        "precipitation_mm"
    )

    if value is None:
        return False

    try:
        return float(value) > 0
    except (TypeError, ValueError):
        return False


async def get_rain_window(
    latitude: float,
    longitude: float,
    hours: int = 48,
    timezone_name: str = "Asia/Kolkata",
) -> dict:

    hours = max(
        1,
        min(hours, 72),
    )

    weather = await get_weather(
        latitude=latitude,
        longitude=longitude,
    )

    forecast = (
        weather.get("forecast") or []
    )[:hours]

    if not forecast:
        return {
            "status": "error",
            "message": "No forecast data available.",
        }

    timezone = ZoneInfo(
        timezone_name
    )

    rain_points = []

    for item in forecast:

        if not _is_rain(item):
            continue

        raw_time = item.get("time")

        if not raw_time:
            continue

        try:
            local_time = _parse_time(
                raw_time
            ).astimezone(
                timezone
            )
        except Exception:
            continue

        rainfall = float(
            item.get(
                "precipitation_mm"
            )
        )

        rain_points.append(
            {
                "local_time": local_time,
                "rainfall_mm": rainfall,
                "condition": item.get(
                    "symbol_code"
                ),
            }
        )

    if not rain_points:
        return {
            "status": "success",
            "rain_expected": False,
            "windows": [],
            "rain_points": [],
            "source": weather.get(
                "source",
                "MET Norway",
            ),
            "updated_at": weather.get(
                "updated_at"
            ),
        }

    timeline_items = []
    for item in forecast[:12]:
        raw_time = item.get("time")
        if not raw_time:
            continue
        try:
            local_time = _parse_time(raw_time).astimezone(timezone)
        except Exception:
            continue

        rainfall = float(item.get("precipitation_mm") or 0.0)
        prob = item.get("precipitation_probability_pct")
        cond = (item.get("symbol_code") or "").lower()

        # Pick matching weather emoji
        if "thunder" in cond:
            emoji = "⛈️"
        elif "heavyrain" in cond or rainfall >= 3.0:
            emoji = "🌧️"
        elif "rain" in cond or rainfall > 0.0:
            emoji = "🌦️"
        elif "cloud" in cond:
            emoji = "☁️"
        elif "fog" in cond:
            emoji = "🌫️"
        else:
            emoji = "☀️"

        time_str = local_time.strftime("%I %p").lstrip("0")
        prob_str = f" {int(prob)}%" if prob is not None and prob > 0 else (f" {int(rainfall*25)}%" if rainfall > 0 else "")

        timeline_items.append({
            "time": time_str,
            "hour": local_time.hour,
            "emoji": emoji,
            "probability_pct": prob,
            "rainfall_mm": rainfall,
            "condition": cond,
            "formatted_line": f"{time_str.ljust(5)} ─────────────── {emoji}{prob_str}"
        })

    rain_points.sort(
        key=lambda x: x["local_time"]
    )

    windows = []
    current = []

    for point in rain_points:

        if not current:
            current = [point]
            continue

        previous = current[-1]["local_time"]

        if (
            point["local_time"] -
            previous
        ) <= timedelta(hours=1):

            current.append(point)

        else:

            windows.append(current)
            current = [point]

    if current:
        windows.append(current)

    summaries = []
    peak_time_str = None
    peak_val = 0.0

    for window in windows:

        start = window[0]
        end = window[-1]

        total = sum(
            item["rainfall_mm"]
            for item in window
        )

        peak = max(
            item["rainfall_mm"]
            for item in window
        )

        for item in window:
            if item["rainfall_mm"] > peak_val:
                peak_val = item["rainfall_mm"]
                peak_time_str = item["local_time"].strftime("%I %p").lstrip("0")

        if peak >= 5:
            intensity = "HEAVY"
        elif peak >= 2:
            intensity = "MODERATE"
        else:
            intensity = "LIGHT"

        summaries.append(
            {
                "start":
                    start["local_time"].isoformat(),

                "end":
                    (
                        end["local_time"]
                        + timedelta(hours=1)
                    ).isoformat(),

                "start_label":
                    start["local_time"].strftime("%I %p").lstrip("0"),

                "end_label":
                    (end["local_time"] + timedelta(hours=1)).strftime("%I %p").lstrip("0"),

                "total_rainfall_mm":
                    round(total, 2),

                "peak_hourly_rainfall_mm":
                    round(peak, 2),

                "intensity":
                    intensity,
            }
        )

    return {
        "status": "success",
        "rain_expected": bool(rain_points),
        "windows": summaries,
        "peak_time": peak_time_str,
        "timeline": [t["formatted_line"] for t in timeline_items],
        "rain_points": [
            {
                "time":
                    item["local_time"].isoformat(),

                "rainfall_mm":
                    item["rainfall_mm"],

                "condition":
                    item["condition"],
            }
            for item in rain_points
        ],
        "source": weather.get(
            "source",
            "MET Norway",
        ),
        "updated_at": weather.get(
            "updated_at"
        ),
    }
