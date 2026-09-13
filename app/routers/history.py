from datetime import date, timedelta

from fastapi import APIRouter, HTTPException
from sqlalchemy.orm import Session
from fastapi import Depends

from app.database import get_db
from app.services.open_meteo_history import (
    fetch_historical_weather,
    historical_to_observations,
)
from app.services.ingestion import save_observation

router = APIRouter(
    prefix="/history",
    tags=["Historical Weather Data"]
)


@router.post("/ingest")
async def ingest_history(
    latitude: float = 16.5062,
    longitude: float = 80.6480,
    location_name: str = "Vijayawada",
    days: int = 7,
    db: Session = Depends(get_db),
):

    if days < 1 or days > 30:
        raise HTTPException(
            status_code=400,
            detail="days must be between 1 and 30"
        )

    end_date = date.today() - timedelta(days=1)

    start_date = (
        end_date - timedelta(days=days - 1)
    )

    try:

        data = await fetch_historical_weather(
            latitude=latitude,
            longitude=longitude,
            start_date=start_date.isoformat(),
            end_date=end_date.isoformat(),
        )

        observations = historical_to_observations(
            data=data,
            location_name=location_name,
            latitude=latitude,
            longitude=longitude,
        )

        stored = 0
        skipped = 0

        for observation_data in observations:

            before = db.query(
                __import__(
                    "app.models.observation",
                    fromlist=[
                        "WeatherObservation"
                    ]
                ).WeatherObservation
            ).filter(
                __import__(
                    "app.models.observation",
                    fromlist=[
                        "WeatherObservation"
                    ]
                ).WeatherObservation.location_name
                == observation_data["location_name"],
                __import__(
                    "app.models.observation",
                    fromlist=[
                        "WeatherObservation"
                    ]
                ).WeatherObservation.observed_at
                == observation_data["observed_at"],
                __import__(
                    "app.models.observation",
                    fromlist=[
                        "WeatherObservation"
                    ]
                ).WeatherObservation.source
                == observation_data["source"],
            ).first()

            save_observation(
                db,
                observation_data
            )

            if before:
                skipped += 1
            else:
                stored += 1

        return {
            "status": "success",
            "location": location_name,
            "start_date": start_date.isoformat(),
            "end_date": end_date.isoformat(),
            "hours_received": len(observations),
            "new_records": stored,
            "existing_records": skipped,
            "source": "open_meteo_historical",
        }

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc)
        )

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Historical weather ingestion failed: {exc}"
        )
