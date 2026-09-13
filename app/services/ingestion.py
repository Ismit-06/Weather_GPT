from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.models.observation import WeatherObservation
from app.services.normalizer import (
    normalize_temperature,
    normalize_humidity,
    normalize_pressure,
    normalize_wind_speed,
    normalize_wind_direction,
    normalize_rainfall,
    normalize_timestamp,
)
from app.services.validator import validate_observation


def save_observation(
    db: Session,
    data: dict
) -> WeatherObservation:

    normalized = {
        "location_name": data["location_name"],
        "latitude": float(data["latitude"]),
        "longitude": float(data["longitude"]),

        "temperature_c": normalize_temperature(
            data.get("temperature_c")
        ),

        "humidity_pct": normalize_humidity(
            data.get("humidity_pct")
        ),

        "pressure_hpa": normalize_pressure(
            data.get("pressure_hpa")
        ),

        "wind_speed_kmh": normalize_wind_speed(
            data.get("wind_speed_kmh")
        ),

        "wind_direction_deg": normalize_wind_direction(
            data.get("wind_direction_deg")
        ),

        "rainfall_mm": normalize_rainfall(
            data.get("rainfall_mm")
        ),

        "source": data["source"],

        "observed_at": normalize_timestamp(
            data.get("observed_at")
        ),
    }

    valid, errors = validate_observation(
        normalized
    )

    if not valid:
        raise ValueError(
            f"Invalid weather observation: {errors}"
        )

    existing = db.query(
        WeatherObservation
    ).filter(
        WeatherObservation.location_name
        == normalized["location_name"],
        WeatherObservation.observed_at
        == normalized["observed_at"],
        WeatherObservation.source
        == normalized["source"],
    ).first()

    if existing:
        return existing

    observation = WeatherObservation(
        **normalized,
        received_at=datetime.now(timezone.utc)
    )

    db.add(observation)
    db.commit()
    db.refresh(observation)

    return observation
