from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.services.open_meteo import fetch_current_weather
from app.services.ingestion import save_observation

router = APIRouter(
    prefix="/data",
    tags=["Real Meteorological Data"]
)


@router.get("/current")
async def get_real_current_weather(
    latitude: float = 16.5062,
    longitude: float = 80.6480,
    location_name: str = "Vijayawada",
):
    try:

        data = await fetch_current_weather(
            latitude=latitude,
            longitude=longitude
        )

        data["location_name"] = location_name

        return {
            "status": "success",
            "data": data
        }

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Weather data source unavailable: {exc}"
        )


@router.post("/ingest-current")
async def ingest_real_current_weather(
    latitude: float = 16.5062,
    longitude: float = 80.6480,
    location_name: str = "Vijayawada",
    db: Session = Depends(get_db),
):

    try:

        data = await fetch_current_weather(
            latitude=latitude,
            longitude=longitude
        )

        data["location_name"] = location_name

        observation = save_observation(
            db,
            data
        )

        return {
            "status": "stored",
            "id": observation.id,
            "location": observation.location_name,
            "temperature_c": observation.temperature_c,
            "humidity_pct": observation.humidity_pct,
            "pressure_hpa": observation.pressure_hpa,
            "wind_speed_kmh": observation.wind_speed_kmh,
            "wind_direction_deg": observation.wind_direction_deg,
            "rainfall_mm": observation.rainfall_mm,
            "source": observation.source,
            "observed_at": observation.observed_at,
        }

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Could not ingest weather data: {exc}"
        )
