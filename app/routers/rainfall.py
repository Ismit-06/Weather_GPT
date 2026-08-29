from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.prediction.rainfall_model import (
    train_rainfall_models,
    predict_rainfall,
)

router = APIRouter(
    prefix="/prediction",
    tags=["Rainfall Prediction"]
)


@router.post("/rainfall/train")
def train_rainfall(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        result = train_rainfall_models(
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
            detail=f"Rainfall training failed: {exc}"
        )


@router.get("/rainfall")
def rainfall_prediction(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:

        return {
            "status": "success",
            **predict_rainfall(
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
            detail=f"Rainfall prediction failed: {exc}"
        )
