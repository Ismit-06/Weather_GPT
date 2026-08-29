from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.features import WeatherFeature
from app.risk.hazard_engine import (
    calculate_hazard_profile
)

router = APIRouter(
    prefix="/risk",
    tags=["Hazard Risk Engine"]
)


@router.get("/hazards")
def get_hazard_profile(
    location: str = "Vijayawada",
    rainfall_probability: float = 0.0,
    db: Session = Depends(get_db)
):

    records = (
        db.query(WeatherFeature)
        .filter(
            WeatherFeature.location_name == location
        )
        .order_by(
            WeatherFeature.feature_time.desc()
        )
        .all()
    )

    feature = None

    for record in records:

        values = [
            record.temperature_c,
            record.humidity_pct,
            record.pressure_hpa,
            record.wind_speed_kmh,
            record.rainfall_mm,
            record.rainfall_3h,
            record.rainfall_24h,
            record.pressure_change_3h,
            record.heat_index_c,
        ]

        if all(
            value is not None
            for value in values
        ):
            feature = record
            break

    if feature is None:

        raise HTTPException(
            status_code=400,
            detail=(
                "No complete feature record is available "
                "for hazard analysis."
            )
        )

    hazards = calculate_hazard_profile(
        temperature_c=feature.temperature_c,
        heat_index_c=feature.heat_index_c,
        rainfall_3h=feature.rainfall_3h,
        rainfall_24h=feature.rainfall_24h,
        rainfall_probability=rainfall_probability,
        wind_speed_kmh=feature.wind_speed_kmh,
        pressure_change_3h=feature.pressure_change_3h,
    )

    return {
        "status": "success",
        "location": location,
        "reference_time": feature.feature_time,

        "hazards": [
            {
                "hazard": result.hazard,
                "score": result.score,
                "level": result.level,
                "reasons": result.reasons
            }
            for result in hazards
        ],

        "engine": "hazard_engine_v1",

        "note": (
            "Hazard thresholds are an initial prototype "
            "and require calibration and validation before "
            "operational safety use."
        )
    }
