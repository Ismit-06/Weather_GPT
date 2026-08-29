from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.hydrology.future_water import (
    predict_future_water_levels
)
from app.hydrology.water_model import (
    DEFAULT_PARAMS,
)
from app.prediction.rainfall_model import (
    predict_rainfall
)

router = APIRouter(
    prefix="/prediction",
    tags=["Flood Prediction"]
)


@router.get("/flood")
def flood_prediction(
    location: str = "Vijayawada",
    db: Session = Depends(get_db),
):

    try:

        rainfall_result = predict_rainfall(
            db,
            location
        )

        rainfall_forecast = (
            rainfall_result["forecast"]
        )

        water_forecast = (
            predict_future_water_levels(
                rainfall_forecast,
                DEFAULT_PARAMS
            )
        )

        return {
            "status": "success",

            "location": location,

            "current_water_level_m":
                DEFAULT_PARAMS.initial_water_level_m,

            "warning_level_m":
                DEFAULT_PARAMS.flood_warning_level_m,

            "danger_level_m":
                DEFAULT_PARAMS.flood_danger_level_m,

            "forecast":
                water_forecast,

            "engine":
                "rainfall_runoff_flood_v1",

            "important_note": (
                "Water-level values are model estimates "
                "based on prototype catchment parameters. "
                "They are not direct river-gauge measurements."
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
            detail=f"Flood prediction failed: {exc}"
        )
