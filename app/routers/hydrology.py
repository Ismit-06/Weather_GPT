from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.hydrology.water_model import (
    DEFAULT_PARAMS,
    calculate_current_water_estimate,
)

router = APIRouter(
    prefix="/hydrology",
    tags=["Water & Flood Intelligence"]
)


@router.get("/water-level")
def current_water_level(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        result = calculate_current_water_estimate(
            db,
            location,
            DEFAULT_PARAMS
        )

        return {
            "status": "success",
            **result
        }

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    except Exception as exc:

        raise HTTPException(
            status_code=500,
            detail=f"Water-level calculation failed: {exc}"
        )
