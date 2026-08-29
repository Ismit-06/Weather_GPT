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
        "rain_expected": True,
        "windows": summaries,
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
