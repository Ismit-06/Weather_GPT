from datetime import datetime

from app.services.met_weather import get_weather
from app.services.weather_forecast_interpolate import (
    interpolate_forecast,
)


def _normalize(item: dict) -> dict:
    return {
        "time": item.get("time"),
        "temperature_c": item.get("temperature_c"),
        "rainfall_mm": item.get(
            "precipitation_mm"
        ),
        "humidity_pct": item.get(
            "relative_humidity_pct"
        ),
        "wind_speed_ms": item.get(
            "wind_speed_ms"
        ),
        "wind_direction_deg": item.get(
            "wind_direction_deg"
        ),
        "condition": item.get(
            "symbol_code"
        ),
    }


async def get_forecast_at_time(
    latitude: float,
    longitude: float,
    target_local_time: datetime,
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

    points = [
        _normalize(item)
        for item in forecast
        if item.get("time")
    ]

    result = interpolate_forecast(
        forecast=points,
        target_local=target_local_time,
    )

    if result is None:
        return {
            "status": "error",
            "message": (
                "Could not match the requested forecast time."
            ),
        }

    return {
        "status": "success",
        "requested_local_time":
            target_local_time.isoformat(),
        "forecast": result,
        "source": weather.get(
            "source",
            "MET Norway",
        ),
        "updated_at": weather.get(
            "updated_at"
        ),
    }
