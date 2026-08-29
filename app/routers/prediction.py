from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.prediction.temperature_model import (
    train_temperature_model,
    predict_next_temperature,
)

router = APIRouter(
    prefix="/prediction",
    tags=["Prediction Engine"]
)


@router.post("/temperature/train")
def train_temperature(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        result = train_temperature_model(
            db,
            location
        )

        return {
            "status": "trained",
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
            detail=f"Training failed: {exc}"
        )


@router.get("/temperature/next-hour")
def next_hour_temperature(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        result = predict_next_temperature(
            db,
            location
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
            detail=f"Prediction failed: {exc}"
        )
