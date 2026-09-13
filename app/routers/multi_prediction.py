from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.prediction.multi_temperature import (
    train_direct_models,
    predict_direct_temperature,
)

router = APIRouter(
    prefix="/prediction",
    tags=["Multi-Horizon Prediction"]
)


@router.post("/temperature/train-direct")
def train_direct(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        result = train_direct_models(
            db,
            location
        )

        return {
            "status": "trained",
            "location": location,
            "models": result
        }

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    except Exception as exc:

        raise HTTPException(
            status_code=500,
            detail=f"Training failed: {exc}"
        )


@router.get("/temperature/direct")
def direct_temperature(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        return {
            "status": "success",
            **predict_direct_temperature(
                db,
                location
            )
        }

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    except Exception as exc:

        raise HTTPException(
            status_code=500,
            detail=f"Prediction failed: {exc}"
        )
