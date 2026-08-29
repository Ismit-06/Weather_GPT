from dataclasses import dataclass

import numpy as np
import pandas as pd
from sqlalchemy.orm import Session

from app.models.features import WeatherFeature


@dataclass
class CatchmentParameters:
    # These are prototype parameters.
    # They must be calibrated for a real river basin.

    catchment_area_km2: float = 100.0

    runoff_coefficient: float = 0.65

    initial_water_level_m: float = 3.0

    flood_warning_level_m: float = 4.5

    flood_danger_level_m: float = 5.5

    stage_response_m_per_100mm: float = 1.0


DEFAULT_PARAMS = CatchmentParameters()


def get_recent_rainfall(
    db: Session,
    location: str,
    hours: int = 24
) -> pd.DataFrame:

    records = (
        db.query(WeatherFeature)
        .filter(
            WeatherFeature.location_name == location
        )
        .order_by(
            WeatherFeature.feature_time.desc()
        )
        .limit(hours)
        .all()
    )

    if not records:
        raise ValueError(
            "No weather feature data available."
        )

    rows = []

    for record in records:
        rows.append({
            "time": record.feature_time,
            "rainfall_mm": (
                record.rainfall_mm
                if record.rainfall_mm is not None
                else 0.0
            )
        })

    return (
        pd.DataFrame(rows)
        .sort_values("time")
        .reset_index(drop=True)
    )


def calculate_effective_rainfall(
    rainfall_mm: float,
    runoff_coefficient: float
) -> float:

    rainfall_mm = max(
        0.0,
        rainfall_mm
    )

    runoff_coefficient = min(
        1.0,
        max(
            0.0,
            runoff_coefficient
        )
    )

    return rainfall_mm * runoff_coefficient


def calculate_runoff_volume_m3(
    effective_rainfall_mm: float,
    catchment_area_km2: float
) -> float:

    # 1 mm over 1 km² = 1,000 m³.
    return (
        effective_rainfall_mm
        * catchment_area_km2
        * 1000.0
    )


def estimate_stage_rise(
    rainfall_mm: float,
    params: CatchmentParameters
) -> float:

    effective = calculate_effective_rainfall(
        rainfall_mm,
        params.runoff_coefficient
    )

    return (
        effective
        / 100.0
        * params.stage_response_m_per_100mm
    )


def classify_flood_risk(
    water_level_m: float,
    params: CatchmentParameters
) -> str:

    if water_level_m >= params.flood_danger_level_m:
        return "SEVERE"

    if water_level_m >= params.flood_warning_level_m:
        return "HIGH"

    if (
        water_level_m
        >= params.flood_warning_level_m * 0.85
    ):
        return "MODERATE"

    return "LOW"


def calculate_current_water_estimate(
    db: Session,
    location: str,
    params: CatchmentParameters = DEFAULT_PARAMS
) -> dict:

    rainfall = get_recent_rainfall(
        db,
        location,
        hours=24
    )

    total_rainfall = float(
        rainfall["rainfall_mm"].sum()
    )

    effective_rainfall = calculate_effective_rainfall(
        total_rainfall,
        params.runoff_coefficient
    )

    runoff_volume = calculate_runoff_volume_m3(
        effective_rainfall,
        params.catchment_area_km2
    )

    stage_rise = estimate_stage_rise(
        total_rainfall,
        params
    )

    estimated_level = (
        params.initial_water_level_m
        + stage_rise
    )

    risk = classify_flood_risk(
        estimated_level,
        params
    )

    return {
        "location": location,

        "period_hours": 24,

        "rainfall_total_mm":
            round(total_rainfall, 2),

        "effective_rainfall_mm":
            round(effective_rainfall, 2),

        "estimated_runoff_m3":
            round(runoff_volume, 2),

        "estimated_water_level_m":
            round(estimated_level, 3),

        "water_level_rise_m":
            round(stage_rise, 3),

        "flood_warning_level_m":
            params.flood_warning_level_m,

        "flood_danger_level_m":
            params.flood_danger_level_m,

        "flood_risk":
            risk,

        "engine":
            "rainfall_runoff_stage_v1",

        "note": (
            "Water level is an estimated model output, "
            "not a direct gauge observation. "
            "Catchment parameters must be calibrated "
            "for operational use."
        )
    }


def estimate_future_water_levels(
    rainfall_forecast: list[dict],
    params: CatchmentParameters = DEFAULT_PARAMS
) -> list[dict]:

    current_level = params.initial_water_level_m

    results = []

    for item in rainfall_forecast:

        horizon = int(
            item["horizon_hours"]
        )

        rainfall_mm = float(
            item.get(
                "predicted_rainfall_mm",
                0.0
            )
        )

        effective_rainfall = (
            calculate_effective_rainfall(
                rainfall_mm,
                params.runoff_coefficient
            )
        )

        runoff_volume = (
            calculate_runoff_volume_m3(
                effective_rainfall,
                params.catchment_area_km2
            )
        )

        stage_rise = estimate_stage_rise(
            rainfall_mm,
            params
        )

        predicted_level = (
            current_level
            + stage_rise
        )

        risk = classify_flood_risk(
            predicted_level,
            params
        )

        results.append({
            "horizon_hours": horizon,

            "rainfall_mm":
                round(rainfall_mm, 2),

            "effective_rainfall_mm":
                round(
                    effective_rainfall,
                    2
                ),

            "runoff_m3":
                round(
                    runoff_volume,
                    2
                ),

            "estimated_water_level_m":
                round(
                    predicted_level,
                    3
                ),

            "water_level_rise_m":
                round(
                    stage_rise,
                    3
                ),

            "flood_risk":
                risk,
        })

        current_level = predicted_level

    return results
