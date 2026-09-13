from app.services.met_weather import get_weather


async def get_current_weather(
    latitude: float,
    longitude: float,
) -> dict:

    weather = await get_weather(
        latitude=latitude,
        longitude=longitude,
    )

    current = weather.get("current") or {}
    forecast = weather.get("forecast") or []

    if not current and forecast:
        current = forecast[0]

    if not current:
        return {
            "status": "error",
            "message": "No current weather data available.",
        }

    return {
        "status": "success",
        "time": current.get("time"),
        "temperature_c": current.get("temperature_c"),
        "humidity_pct": current.get(
            "relative_humidity_pct",
            current.get("humidity_pct"),
        ),
        "dew_point_c": current.get("dew_point_c"),
        "pressure_hpa": current.get("pressure_hpa"),
        "wind_speed_ms": current.get("wind_speed_ms"),
        "wind_direction_deg": current.get(
            "wind_direction_deg"
        ),
        "wind_gust_ms": current.get("wind_gust_ms"),
        "cloud_cover_pct": current.get("cloud_cover_pct"),
        "rainfall_mm": current.get(
            "precipitation_mm",
            current.get("rainfall_mm"),
        ),
        "condition": current.get("symbol_code"),
        "source": weather.get(
            "source",
            "MET Norway",
        ),
        "updated_at": weather.get("updated_at"),
    }
