from fastapi import (
    APIRouter,
    Depends,
    Query,
)

from sqlalchemy.orm import Session

from app.database import get_db

from app.prediction.rainfall_model import (
    predict_rainfall,
)

from app.hydrology.future_water import (
    predict_future_water_levels,
)

from app.hydrology.water_model import (
    DEFAULT_PARAMS,
)


router = APIRouter(
    prefix="/flood",
    tags=["Flood Intelligence"],
)


def extract_flood_risk(
    forecast: list,
) -> str:

    levels = []

    for item in forecast:

        if not isinstance(
            item,
            dict,
        ):
            continue

        value = (
            item.get("flood_risk")
            or item.get("risk")
            or item.get("level")
        )

        if isinstance(
            value,
            str,
        ):
            levels.append(
                value.upper()
            )

    if "SEVERE" in levels:
        return "SEVERE"

    if "HIGH" in levels:
        return "HIGH"

    if "MODERATE" in levels:
        return "MODERATE"

    return "LOW"


@router.get("")
def flood(
    location: str = Query(
        "Vijayawada",
        min_length=2,
        max_length=150,
    ),

    latitude: float | None = Query(
        None,
        ge=-90,
        le=90,
    ),

    longitude: float | None = Query(
        None,
        ge=-180,
        le=180,
    ),

    db: Session = Depends(
        get_db
    ),
):

    try:

        rainfall_result = predict_rainfall(
            db,
            location,
        )

        rainfall_forecast = (
            rainfall_result["forecast"]
        )

        water_forecast = (
            predict_future_water_levels(
                rainfall_forecast,
                DEFAULT_PARAMS,
            )
        )

        return {
            "status": "success",

            "location": location,

            "latitude": latitude,

            "longitude": longitude,

            "flood_risk":
                extract_flood_risk(
                    water_forecast
                ),

            "current_water_level_m":
                DEFAULT_PARAMS.initial_water_level_m,

            "warning_level_m":
                DEFAULT_PARAMS.flood_warning_level_m,

            "danger_level_m":
                DEFAULT_PARAMS.flood_danger_level_m,

            "rainfall_forecast":
                rainfall_forecast,

            "forecast":
                water_forecast,

            "engine":
                "rainfall_runoff_flood_v1",

            "source_type":
                "MODEL_ESTIMATE",

            "official_warning":
                False,

            "important_note": (
                "Water-level values are model estimates "
                "based on prototype catchment parameters. "
                "They are not direct river-gauge measurements."
            ),
        }

    except ValueError as exc:

        return {
            "status":
                "model_unavailable",

            "location":
                location,

            "latitude":
                latitude,

            "longitude":
                longitude,

            "flood_risk":
                None,

            "source_type":
                "MODEL_ESTIMATE",

            "official_warning":
                False,

            "message":
                str(exc),

            "important_note": (
                "The flood model does not have "
                "sufficient feature data for this "
                "location. No flood risk value "
                "has been invented."
            ),
        }

    except Exception as exc:

        return {
            "status":
                "error",

            "location":
                location,

            "latitude":
                latitude,

            "longitude":
                longitude,

            "flood_risk":
                None,

            "message":
                f"Flood model failed: {exc}",
        }
