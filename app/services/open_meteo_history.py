import httpx
from datetime import datetime, timedelta, timezone


HISTORICAL_URL = "https://archive-api.open-meteo.com/v1/archive"


async def fetch_historical_weather(
    latitude: float,
    longitude: float,
    start_date: str,
    end_date: str,
) -> dict:

    params = {
        "latitude": latitude,
        "longitude": longitude,

        "start_date": start_date,
        "end_date": end_date,

        "hourly": ",".join([
            "temperature_2m",
            "relative_humidity_2m",
            "surface_pressure",
            "wind_speed_10m",
            "wind_direction_10m",
            "precipitation",
            "rain",
        ]),

        "timezone": "UTC",

        "temperature_unit": "celsius",
        "wind_speed_unit": "kmh",
        "precipitation_unit": "mm",
    }

    async with httpx.AsyncClient(
        timeout=30.0
    ) as client:

        response = await client.get(
            HISTORICAL_URL,
            params=params
        )

        response.raise_for_status()

        return response.json()


def historical_to_observations(
    data: dict,
    location_name: str,
    latitude: float,
    longitude: float,
) -> list[dict]:

    hourly = data.get("hourly")

    if not hourly:
        return []

    times = hourly.get("time", [])

    temperatures = hourly.get(
        "temperature_2m",
        []
    )

    humidities = hourly.get(
        "relative_humidity_2m",
        []
    )

    pressures = hourly.get(
        "surface_pressure",
        []
    )

    wind_speeds = hourly.get(
        "wind_speed_10m",
        []
    )

    wind_directions = hourly.get(
        "wind_direction_10m",
        []
    )

    precipitation = hourly.get(
        "precipitation",
        []
    )

    rain = hourly.get(
        "rain",
        []
    )

    observations = []

    for i, time_value in enumerate(times):

        observed_at = datetime.fromisoformat(
            time_value.replace("Z", "+00:00")
        )

        if observed_at.tzinfo is None:
            observed_at = observed_at.replace(
                tzinfo=timezone.utc
            )

        observations.append({
            "location_name": location_name,

            "latitude": latitude,

            "longitude": longitude,

            "temperature_c": (
                temperatures[i]
                if i < len(temperatures)
                else None
            ),

            "humidity_pct": (
                humidities[i]
                if i < len(humidities)
                else None
            ),

            "pressure_hpa": (
                pressures[i]
                if i < len(pressures)
                else None
            ),

            "wind_speed_kmh": (
                wind_speeds[i]
                if i < len(wind_speeds)
                else None
            ),

            "wind_direction_deg": (
                wind_directions[i]
                if i < len(wind_directions)
                else None
            ),

            "rainfall_mm": (
                precipitation[i]
                if i < len(precipitation)
                else (
                    rain[i]
                    if i < len(rain)
                    else None
                )
            ),

            "source": "open_meteo_historical",

            "observed_at": observed_at,
        })

    return observations
