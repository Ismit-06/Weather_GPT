from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.services.ingestion import save_observation

router = APIRouter(
    prefix="/ingestion",
    tags=["Data Ingestion"]
)


@router.post("/test")
def ingest_test_observation(
    db: Session = Depends(get_db)
):

    sample = {
        "location_name": "Vijayawada",
        "latitude": 16.5062,
        "longitude": 80.6480,

        "temperature_c": 29.0,
        "humidity_pct": 74.0,
        "pressure_hpa": 1008.0,
        "wind_speed_kmh": 14.0,
        "wind_direction_deg": 220.0,
        "rainfall_mm": 2.0,

        "source": "phase3_test"
    }

    try:
        observation = save_observation(
            db,
            sample
        )

    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    return {
        "status": "stored",
        "id": observation.id,
        "location": observation.location_name,
        "temperature_c": observation.temperature_c,
        "humidity_pct": observation.humidity_pct,
        "rainfall_mm": observation.rainfall_mm,
        "source": observation.source
    }
