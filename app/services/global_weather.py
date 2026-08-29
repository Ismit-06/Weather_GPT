import httpx


OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"


CURRENT_VARIABLES = ",".join([
    "temperature_2m",
    "relative_humidity_2m",
    "apparent_temperature",
    "precipitation",
    "rain",
    "showers",
    "snowfall",
    "weather_code",
    "cloud_cover",
    "pressure_msl",
    "surface_pressure",
    "wind_speed_10m",
    "wind_direction_10m",
    "wind_gusts_10m",
    "is_day",
])


HOURLY_VARIABLES = ",".join([
    "temperature_2m",
    "relative_humidity_2m",
    "dew_point_2m",
    "apparent_temperature",
    "precipitation_probability",
    "precipitation",
    "rain",
    "showers",
    "snowfall",
    "weather_code",
    "cloud_cover",
    "cloud_cover_low",
    "cloud_cover_mid",
    "cloud_cover_high",
    "pressure_msl",
    "surface_pressure",
    "visibility",
    "wind_speed_10m",
    "wind_direction_10m",
    "wind_gusts_10m",
    "uv_index",
    "is_day",
    "evapotranspiration",
    "vapour_pressure_deficit",
    "cape",
    "runoff",
])


DAILY_VARIABLES = ",".join([
    "temperature_2m_max",
    "temperature_2m_min",
    "temperature_2m_mean",
    "apparent_temperature_max",
    "apparent_temperature_min",
    "precipitation_sum",
    "rain_sum",
    "showers_sum",
    "snowfall_sum",
    "precipitation_hours",
    "precipitation_probability_max",
    "weather_code",
    "sunrise",
    "sunset",
    "sunshine_duration",
    "uv_index_max",
    "wind_speed_10m_max",
    "wind_gusts_10m_max",
    "wind_direction_10m_dominant",
])


async def get_global_forecast(
    latitude: float,
    longitude: float,
    forecast_days: int = 7,
) -> dict:

    forecast_days = max(
        1,
        min(forecast_days, 16)
    )

    params = {
        "latitude": latitude,
        "longitude": longitude,
        "current": CURRENT_VARIABLES,
        "hourly": HOURLY_VARIABLES,
        "daily": DAILY_VARIABLES,
        "forecast_days": forecast_days,
        "timezone": "auto",
        "temperature_unit": "celsius",
        "wind_speed_unit": "kmh",
        "precipitation_unit": "mm",
    }

    async with httpx.AsyncClient(
        timeout=30.0,
        headers={
            "User-Agent": "WeatherGPT/1.0"
        },
    ) as client:

        response = await client.get(
            OPEN_METEO_FORECAST_URL,
            params=params,
        )

        response.raise_for_status()

        data = response.json()

    return {
        "status": "success",
        "location": {
            "latitude": latitude,
            "longitude": longitude,
            "timezone": data.get("timezone"),
            "timezone_abbreviation":
                data.get("timezone_abbreviation"),
            "elevation":
                data.get("elevation"),
        },
        "current": data.get("current", {}),
        "current_units": data.get("current_units", {}),
        "hourly": data.get("hourly", {}),
        "hourly_units": data.get("hourly_units", {}),
        "daily": data.get("daily", {}),
        "daily_units": data.get("daily_units", {}),
        "source": "open_meteo_global",
        "model": data.get("model"),
        "generationtime_ms":
            data.get("generationtime_ms"),
    }


async def get_global_temperature(
    latitude: float,
    longitude: float,
) -> dict:

    forecast = await get_global_forecast(
        latitude=latitude,
        longitude=longitude,
        forecast_days=3,
    )

    current = forecast["current"]
    hourly = forecast["hourly"]

    return {
        "status": "success",
        "latitude": latitude,
        "longitude": longitude,
        "timezone":
            forecast["location"]["timezone"],
        "current": {
            "temperature_c":
                current.get("temperature_2m"),
            "apparent_temperature_c":
                current.get("apparent_temperature"),
            "humidity_pct":
                current.get("relative_humidity_2m"),
            "wind_speed_kmh":
                current.get("wind_speed_10m"),
            "wind_direction_deg":
                current.get("wind_direction_10m"),
            "wind_gusts_kmh":
                current.get("wind_gusts_10m"),
            "pressure_msl_hpa":
                current.get("pressure_msl"),
            "surface_pressure_hpa":
                current.get("surface_pressure"),
            "cloud_cover_pct":
                current.get("cloud_cover"),
            "precipitation_mm":
                current.get("precipitation"),
            "rain_mm":
                current.get("rain"),
            "showers_mm":
                current.get("showers"),
            "snowfall_cm":
                current.get("snowfall"),
            "weather_code":
                current.get("weather_code"),
            "is_day":
                current.get("is_day"),
        },
        "hourly": {
            "time":
                hourly.get("time", []),
            "temperature_c":
                hourly.get("temperature_2m", []),
            "apparent_temperature_c":
                hourly.get("apparent_temperature", []),
            "precipitation_probability_pct":
                hourly.get("precipitation_probability", []),
            "precipitation_mm":
                hourly.get("precipitation", []),
            "rain_mm":
                hourly.get("rain", []),
            "showers_mm":
                hourly.get("showers", []),
            "snowfall_cm":
                hourly.get("snowfall", []),
            "humidity_pct":
                hourly.get("relative_humidity_2m", []),
            "dew_point_c":
                hourly.get("dew_point_2m", []),
            "wind_speed_kmh":
                hourly.get("wind_speed_10m", []),
            "wind_direction_deg":
                hourly.get("wind_direction_10m", []),
            "wind_gusts_kmh":
                hourly.get("wind_gusts_10m", []),
            "pressure_msl_hpa":
                hourly.get("pressure_msl", []),
            "surface_pressure_hpa":
                hourly.get("surface_pressure", []),
            "cloud_cover_pct":
                hourly.get("cloud_cover", []),
            "visibility_m":
                hourly.get("visibility", []),
            "uv_index":
                hourly.get("uv_index", []),
            "is_day":
                hourly.get("is_day", []),
            "surface_runoff_mm":
                hourly.get("runoff", []),
            "evapotranspiration_mm":
                hourly.get("evapotranspiration", []),
            "vapour_pressure_deficit_kpa":
                hourly.get("vapour_pressure_deficit", []),
            "cape_jkg":
                hourly.get("cape", []),
        },
        "source":
            forecast["source"],
        "model":
            forecast["model"],
        "warning":
            "Weather values are forecast estimates and may differ from local observations.",
    }
