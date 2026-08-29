from datetime import datetime, timezone
from typing import Any

import httpx


MET_URL = (
    "https://api.met.no/weatherapi/"
    "locationforecast/2.0/complete"
)


USER_AGENT = (
    "WeatherGPT/1.0 "
    "(contact: weatherGPT-project)"
)


async def get_met_forecast(
    latitude: float,
    longitude: float,
) -> dict:

    params = {
        "lat": round(latitude, 4),
        "lon": round(longitude, 4),
    }

    async with httpx.AsyncClient(
        timeout=30.0,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/json",
        },
    ) as client:

        response = await client.get(
            MET_URL,
            params=params,
        )

        response.raise_for_status()

        return response.json()


def parse_time(value: str | None) -> datetime | None:

    if not value:
        return None

    try:
        return datetime.fromisoformat(
            value.replace("Z", "+00:00")
        )
    except ValueError:
        return None


def normalize_item(
    item: dict[str, Any]
) -> dict[str, Any]:

    time = item.get("time")

    data = item.get(
        "data",
        {}
    )

    instant = (
        data
        .get("instant", {})
        .get("details", {})
    )

    next_1h = data.get(
        "next_1_hours",
        {}
    )

    next_1h_details = (
        next_1h
        .get("details", {})
    )

    next_1h_summary = (
        next_1h
        .get("summary", {})
    )

    return {
        "time": time,

        "temperature_c":
            instant.get(
                "air_temperature"
            ),

        "relative_humidity_pct":
            instant.get(
                "relative_humidity"
            ),

        "dew_point_c":
            instant.get(
                "dew_point_temperature"
            ),

        "apparent_temperature_c":
            None,

        "pressure_hpa":
            instant.get(
                "air_pressure_at_sea_level"
            ),

        "wind_speed_ms":
            instant.get(
                "wind_speed"
            ),

        "wind_direction_deg":
            instant.get(
                "wind_from_direction"
            ),

        "wind_gust_ms":
            instant.get(
                "wind_speed_of_gust"
            ),

        "cloud_cover_pct":
            instant.get(
                "cloud_area_fraction"
            ),

        "fog_area_pct":
            instant.get(
                "fog_area_fraction"
            ),

        "precipitation_mm":
            next_1h_details.get(
                "precipitation_amount"
            ),

        "precipitation_probability_pct":
            next_1h_details.get(
                "probability_of_precipitation"
            ),

        "symbol_code":
            next_1h_summary.get(
                "symbol_code"
            ),
    }


def select_current(
    forecast: list[dict[str, Any]]
) -> dict[str, Any] | None:

    if not forecast:
        return None

    now = datetime.now(timezone.utc)

    parsed = []

    for item in forecast:

        dt = parse_time(
            item.get("time")
        )

        if dt is not None:
            parsed.append(
                (dt, item)
            )

    if not parsed:
        return forecast[0]

    # Prefer the latest forecast timestep that is
    # not later than the real current UTC time.
    past = [
        pair
        for pair in parsed
        if pair[0] <= now
    ]

    if past:
        return max(
            past,
            key=lambda pair: pair[0]
        )[1]

    # If the API's first timestep is still ahead
    # of our clock, use the nearest timestep.
    return min(
        parsed,
        key=lambda pair:
            abs(
                (pair[0] - now)
                .total_seconds()
            )
    )[1]


def normalize_met_forecast(
    data: dict,
    latitude: float,
    longitude: float,
) -> dict:

    properties = data.get(
        "properties",
        {}
    )

    meta = properties.get(
        "meta",
        {}
    )

    timeseries = properties.get(
        "timeseries",
        []
    )

    forecast = [
        normalize_item(item)
        for item in timeseries
    ]

    current = select_current(
        forecast
    )

    return {
        "status": "success",

        "location": {
            "latitude": latitude,
            "longitude": longitude,
            "altitude":
                meta.get("units", {}).get(
                    "altitude"
                ),
        },

        "updated_at":
            meta.get("updated_at"),

        "current":
            current,

        "forecast":
            forecast,

        "source": "MET Norway",
    }


async def get_weather(
    latitude: float,
    longitude: float,
) -> dict:

    data = await get_met_forecast(
        latitude=latitude,
        longitude=longitude,
    )

    return normalize_met_forecast(
        data=data,
        latitude=latitude,
        longitude=longitude,
    )
