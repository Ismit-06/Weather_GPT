from app.services.met_weather import get_weather


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


async def get_hourly_forecast(
    latitude: float,
    longitude: float,
    hours: int = 24,
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

    return {
        "status": "success",
        "hours": hours,
        "forecast": [
            _normalize(item)
            for item in forecast
        ],
        "source": weather.get(
            "source",
            "MET Norway",
        ),
        "updated_at": weather.get(
            "updated_at"
        ),
    }
