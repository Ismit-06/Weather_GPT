from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.services.location_weather import (
    build_feature_from_weather,
    fetch_location_weather,
    save_location_feature,
)

router = APIRouter(
    prefix="/location",
    tags=["Location Weather"]
)


@router.post("/ingest")
async def ingest_location(
    location: str = Query(
        ...,
        min_length=2,
        max_length=150
    ),
    latitude: float = Query(
        ...,
        ge=-90,
        le=90
    ),
    longitude: float = Query(
        ...,
        ge=-180,
        le=180
    ),
    db: Session = Depends(get_db)
):

    try:

        weather = await fetch_location_weather(
            latitude=latitude,
            longitude=longitude
        )

        feature = build_feature_from_weather(
            data=weather,
            location_name=location.strip(),
            latitude=latitude,
            longitude=longitude
        )

        saved = save_location_feature(
            db,
            feature
        )

        return {
            "status": "success",
            "location": location.strip(),
            "latitude": latitude,
            "longitude": longitude,
            "feature_id": saved.id,
            "feature_time": saved.feature_time,
            "source": saved.source
        }

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Location weather ingestion failed: {exc}"
        )
