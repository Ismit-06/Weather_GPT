from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.features import WeatherFeature

from app.risk.hazard_engine import (
    calculate_hazard_profile
)

from app.hydrology.water_model import (
    calculate_current_water_estimate,
    DEFAULT_PARAMS,
)

from app.safety.safety_engine import (
    build_safety_profile
)

router = APIRouter(
    prefix="/risk",
    tags=["Safety Decision Engine"]
)


@router.get("/safety")
def safety_profile(
    location: str = "Vijayawada",
    rainfall_probability: float = 0.0,
    db: Session = Depends(get_db)
):

    try:

        records = (
            db.query(WeatherFeature)
            .filter(
                WeatherFeature.location_name
                == location
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
            raise ValueError(
                "No complete feature record is available."
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

        hazard_scores = {
            result.hazard:
                result.score
            for result in hazards
        }

        # Add current flood estimate.
        flood_result = (
            calculate_current_water_estimate(
                db,
                location,
                DEFAULT_PARAMS
            )
        )

        flood_level = {
            "LOW": 15,
            "MODERATE": 40,
            "HIGH": 70,
            "SEVERE": 95,
        }.get(
            flood_result["flood_risk"],
            0
        )

        hazard_scores["FLOOD"] = (
            flood_level
        )

        safety = build_safety_profile(
            hazard_scores
        )

        return {
            "status": "success",

            "location": location,

            "reference_time":
                feature.feature_time,

            "hazards": [
                {
                    "hazard":
                        result.hazard,

                    "score":
                        result.score,

                    "level":
                        result.level,

                    "reasons":
                        result.reasons,
                }
                for result in hazards
            ],

            "flood": {
                "risk":
                    flood_result[
                        "flood_risk"
                    ],

                "estimated_water_level_m":
                    flood_result[
                        "estimated_water_level_m"
                    ],
            },

            "safety":
                safety,

            "engine":
                "weather_to_impact_v1",

            "important_note": (
                "Safety scores and thresholds are "
                "prototype decision-support logic. "
                "They require validation against "
                "historical events and domain guidance "
                "before operational emergency use."
            ),
        }

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    except Exception as exc:

        raise HTTPException(
            status_code=500,
            detail=f"Safety analysis failed: {exc}"
        )
