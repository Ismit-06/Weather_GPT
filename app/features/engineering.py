import math

import pandas as pd
from sqlalchemy.orm import Session

from app.models.observation import WeatherObservation
from app.models.features import WeatherFeature


def calculate_heat_index(
    temperature_c,
    humidity_pct
):
    if temperature_c is None or humidity_pct is None:
        return None

    # Simplified heat-index calculation.
    # Convert Celsius to Fahrenheit for the standard formula.
    temp_f = temperature_c * 9 / 5 + 32

    # Heat index is only meaningful in warm/humid conditions.
    if temp_f < 80 or humidity_pct < 40:
        return temperature_c

    hi_f = (
        -42.379
        + 2.04901523 * temp_f
        + 10.14333127 * humidity_pct
        - 0.22475541 * temp_f * humidity_pct
        - 0.00683783 * temp_f * temp_f
        - 0.05481717 * humidity_pct * humidity_pct
        + 0.00122874 * temp_f * temp_f * humidity_pct
        + 0.00085282 * temp_f * humidity_pct * humidity_pct
        - 0.00000199 * temp_f * temp_f * humidity_pct * humidity_pct
    )

    return (hi_f - 32) * 5 / 9


def calculate_features(
    db: Session,
    location_name: str
):
    observations = (
        db.query(WeatherObservation)
        .filter(
            WeatherObservation.location_name
            == location_name
        )
        .order_by(
            WeatherObservation.observed_at.asc()
        )
        .all()
    )

    if not observations:
        return 0

    rows = []

    for observation in observations:
        rows.append({
            "id": observation.id,
            "location_name": observation.location_name,
            "latitude": observation.latitude,
            "longitude": observation.longitude,
            "time": observation.observed_at,
            "temperature_c": observation.temperature_c,
            "humidity_pct": observation.humidity_pct,
            "pressure_hpa": observation.pressure_hpa,
            "wind_speed_kmh": observation.wind_speed_kmh,
            "rainfall_mm": observation.rainfall_mm,
        })

    df = pd.DataFrame(rows)

    df["time"] = pd.to_datetime(
        df["time"],
        utc=True
    )

    df = (
        df.sort_values("time")
        .drop_duplicates(
            subset=["location_name", "time"]
        )
        .set_index("time")
    )

    # Temperature features
    df["temperature_change_1h"] = (
        df["temperature_c"]
        .diff()
    )

    df["temperature_avg_3h"] = (
        df["temperature_c"]
        .rolling("3h", min_periods=1)
        .mean()
    )

    df["temperature_avg_6h"] = (
        df["temperature_c"]
        .rolling("6h", min_periods=1)
        .mean()
    )

    # Humidity
    df["humidity_change_3h"] = (
        df["humidity_pct"]
        - df["humidity_pct"]
        .shift(freq="3h")
        .reindex(df.index)
    )

    # Pressure
    df["pressure_change_3h"] = (
        df["pressure_hpa"]
        - df["pressure_hpa"]
        .shift(freq="3h")
        .reindex(df.index)
    )

    # Wind
    df["wind_change_3h"] = (
        df["wind_speed_kmh"]
        - df["wind_speed_kmh"]
        .shift(freq="3h")
        .reindex(df.index)
    )

    # Rainfall accumulation
    df["rainfall_3h"] = (
        df["rainfall_mm"]
        .rolling("3h", min_periods=1)
        .sum()
    )

    df["rainfall_6h"] = (
        df["rainfall_mm"]
        .rolling("6h", min_periods=1)
        .sum()
    )

    df["rainfall_24h"] = (
        df["rainfall_mm"]
        .rolling("24h", min_periods=1)
        .sum()
    )

    # Current hourly rainfall as a simple intensity proxy.
    df["rainfall_intensity_1h"] = (
        df["rainfall_mm"]
    )

    # Heat index
    df["heat_index_c"] = [
        calculate_heat_index(
            temp,
            humidity
        )
        for temp, humidity
        in zip(
            df["temperature_c"],
            df["humidity_pct"]
        )
    ]

    created = 0

    for timestamp, row in df.iterrows():

        existing = (
            db.query(WeatherFeature)
            .filter(
                WeatherFeature.location_name
                == row["location_name"],

                WeatherFeature.feature_time
                == timestamp.to_pydatetime()
            )
            .first()
        )

        if existing:
            feature = existing
        else:
            feature = WeatherFeature(
                location_name=row["location_name"],
                latitude=float(row["latitude"]),
                longitude=float(row["longitude"]),
                feature_time=timestamp.to_pydatetime(),
                source="weather_engine_v1"
            )

            db.add(feature)
            created += 1

        def value(name):
            result = row.get(name)

            if pd.isna(result):
                return None

            return float(result)

        feature.temperature_c = value(
            "temperature_c"
        )

        feature.temperature_change_1h = value(
            "temperature_change_1h"
        )

        feature.temperature_avg_3h = value(
            "temperature_avg_3h"
        )

        feature.temperature_avg_6h = value(
            "temperature_avg_6h"
        )

        feature.humidity_pct = value(
            "humidity_pct"
        )

        feature.humidity_change_3h = value(
            "humidity_change_3h"
        )

        feature.pressure_hpa = value(
            "pressure_hpa"
        )

        feature.pressure_change_3h = value(
            "pressure_change_3h"
        )

        feature.wind_speed_kmh = value(
            "wind_speed_kmh"
        )

        feature.wind_change_3h = value(
            "wind_change_3h"
        )

        feature.rainfall_mm = value(
            "rainfall_mm"
        )

        feature.rainfall_3h = value(
            "rainfall_3h"
        )

        feature.rainfall_6h = value(
            "rainfall_6h"
        )

        feature.rainfall_24h = value(
            "rainfall_24h"
        )

        feature.rainfall_intensity_1h = value(
            "rainfall_intensity_1h"
        )

        feature.heat_index_c = value(
            "heat_index_c"
        )

    db.commit()

    return created
