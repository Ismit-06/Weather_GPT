import os

import httpx
from dotenv import load_dotenv

load_dotenv()

OPENWEATHER_URL = (
    "https://api.openweathermap.org/data/3.0/onecall"
)


async def get_openweather_forecast(
    latitude: float,
    longitude: float,
) -> dict:

    api_key = os.getenv(
        "OPENWEATHER_API_KEY"
    )

    if not api_key:
        raise RuntimeError(
            "OPENWEATHER_API_KEY is not configured"
        )

    params = {
        "lat": latitude,
        "lon": longitude,
        "appid": api_key,
        "units": "metric",
    }

    async with httpx.AsyncClient(
        timeout=30.0,
        headers={
            "User-Agent": "WeatherGPT/1.0"
        },
    ) as client:

        response = await client.get(
            OPENWEATHER_URL,
            params=params,
        )

        response.raise_for_status()

        return response.json()


def normalize_openweather(
    data: dict,
) -> dict:

    current = data.get(
        "current",
        {}
    )

    return {
        "status": "success",

        "location": {
            "latitude":
                data.get("lat"),

            "longitude":
                data.get("lon"),

            "timezone":
                data.get("timezone"),

        },

        "current": {
            "timestamp":
                current.get("dt"),

            "temperature_c":
                current.get("temp"),

            "feels_like_c":
                current.get("feels_like"),

            "pressure_hpa":
                current.get("pressure"),

            "humidity_pct":
                current.get("humidity"),

            "dew_point_c":
                current.get("dew_point"),

            "cloud_cover_pct":
                current.get("clouds"),

            "visibility_m":
                current.get("visibility"),

            "wind_speed_ms":
                current.get("wind_speed"),

            "wind_direction_deg":
                current.get("wind_deg"),

            "wind_gust_ms":
                current.get("wind_gust"),

            "uv_index":
                current.get("uvi"),

            "rain_mm":
                (current.get("rain") or {})
                .get("1h", 0.0),

            "snow_mm":
                (current.get("snow") or {})
                .get("1h", 0.0),

            "weather":
                current.get(
                    "weather",
                    []
                ),
        },

        "minutely":
            data.get("minutely", []),

        "hourly":
            data.get("hourly", []),

        "daily":
            data.get("daily", []),

        "alerts":
            data.get("alerts", []),

        "source":
            "OpenWeather",

        "raw":
            data,
    }


async def get_weather(
    latitude: float,
    longitude: float,
) -> dict:

    data = await get_openweather_forecast(
        latitude=latitude,
        longitude=longitude,
    )

    return normalize_openweather(data)
