import httpx
from datetime import datetime, timezone
from sqlalchemy.orm import Session

from app.models.features import WeatherFeature

OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"


async def fetch_location_weather(
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
            "precipitation"
        ),
        "hourly": (
            "temperature_2m,"
            "relative_humidity_2m,"
            "surface_pressure,"
            "wind_speed_10m,"
            "precipitation"
        ),
        "past_days": 1,
        "forecast_days": 1,
        "timezone": "auto",
    }

    async with httpx.AsyncClient(
        timeout=30.0,
        headers={"User-Agent": "WeatherGPT/1.0"},
    ) as client:

        response = await client.get(
            OPEN_METEO_FORECAST_URL,
            params=params,
        )

        response.raise_for_status()
        return response.json()


def _average(values):
    values = [
        value for value in values
        if value is not None
    ]

    if not values:
        return None

    return sum(values) / len(values)


def _sum(values):
    return sum(
        value for value in values
        if value is not None
    )


def _change(values, periods_back):
    if len(values) <= periods_back:
        return None

    current = values[-1]
    previous = values[-1 - periods_back]

    if current is None or previous is None:
        return None

    return current - previous


def build_feature_from_weather(
    data: dict,
    location_name: str,
    latitude: float,
    longitude: float,
) -> WeatherFeature:

    current = data.get("current", {})
    hourly = data.get("hourly", {})

    temperatures = hourly.get(
        "temperature_2m",
        []
    )

    humidity = hourly.get(
        "relative_humidity_2m",
        []
    )

    pressure = hourly.get(
        "surface_pressure",
        []
    )

    wind = hourly.get(
        "wind_speed_10m",
        []
    )

    rainfall = hourly.get(
        "precipitation",
        []
    )

    current_temperature = current.get(
        "temperature_2m"
    )

    current_humidity = current.get(
        "relative_humidity_2m"
    )

    current_pressure = current.get(
        "surface_pressure"
    )

    current_wind = current.get(
        "wind_speed_10m"
    )

    current_rainfall = (
        current.get("precipitation") or 0.0
    )

    temperature_avg_3h = _average(
        temperatures[-3:]
    )

    temperature_avg_6h = _average(
        temperatures[-6:]
    )

    rainfall_3h = _sum(
        rainfall[-3:]
    )

    rainfall_6h = _sum(
        rainfall[-6:]
    )

    rainfall_24h = _sum(
        rainfall[-24:]
    )

    rainfall_intensity_1h = (
        max(rainfall[-3:])
        if rainfall[-3:]
        else 0.0
    )

    heat_index = None

    if (
        current_temperature is not None
        and current_humidity is not None
    ):
        heat_index = (
            current_temperature
            + 0.05 * current_humidity
        )

    return WeatherFeature(
        location_name=location_name,
        latitude=latitude,
        longitude=longitude,
        feature_time=datetime.now(timezone.utc),

        temperature_c=current_temperature,

        temperature_change_1h=_change(
            temperatures,
            1
        ),

        temperature_avg_3h=temperature_avg_3h,

        temperature_avg_6h=temperature_avg_6h,

        humidity_pct=current_humidity,

        humidity_change_3h=_change(
            humidity,
            3
        ),

        pressure_hpa=current_pressure,

        pressure_change_3h=_change(
            pressure,
            3
        ),

        wind_speed_kmh=current_wind,

        wind_change_3h=_change(
            wind,
            3
        ),

        rainfall_mm=current_rainfall,

        rainfall_3h=rainfall_3h,

        rainfall_6h=rainfall_6h,

        rainfall_24h=rainfall_24h,

        rainfall_intensity_1h=rainfall_intensity_1h,

        heat_index_c=heat_index,

        source="open_meteo_location_ingestion",
    )


def save_location_feature(
    db: Session,
    feature: WeatherFeature,
) -> WeatherFeature:

    db.add(feature)
    db.commit()
    db.refresh(feature)

    return feature
