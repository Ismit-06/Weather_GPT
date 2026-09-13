import httpx
from datetime import datetime, timezone


OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"


async def fetch_current_weather(
    latitude: float,
    longitude: float,
) -> dict:

    params = {
        "latitude": latitude,
        "longitude": longitude,

        "current": (
            "temperature_2m,"
            "relative_humidity_2m,"
            "surface_pressure,"
            "wind_speed_10m,"
            "wind_direction_10m,"
            "rain"
        ),

        "timezone": "Asia/Kolkata",
    }

    async with httpx.AsyncClient(
        timeout=15.0
    ) as client:

        response = await client.get(
            OPEN_METEO_URL,
            params=params
        )

        response.raise_for_status()

        data = response.json()

    current = data.get("current", {})

    return {
        "latitude": latitude,
        "longitude": longitude,

        "temperature_c": current.get(
            "temperature_2m"
        ),

        "humidity_pct": current.get(
            "relative_humidity_2m"
        ),

        "pressure_hpa": current.get(
            "surface_pressure"
        ),

        "wind_speed_kmh": current.get(
            "wind_speed_10m"
        ),

        "wind_direction_deg": current.get(
            "wind_direction_10m"
        ),

        "rainfall_mm": current.get(
            "rain"
        ),

        "source": "open_meteo",

        "observed_at": current.get(
            "time",
            datetime.now(timezone.utc).isoformat()
        ),
    }
