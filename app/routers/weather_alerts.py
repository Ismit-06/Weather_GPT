from fastapi import APIRouter, Query

from app.tools.weather_alerts import (
    get_weather_alerts,
)


router = APIRouter(
    prefix="/alerts",
    tags=["Weather Alerts"],
)


@router.get("")
async def alerts(
    latitude: float = Query(
        ...,
        ge=-90,
        le=90,
    ),
    longitude: float = Query(
        ...,
        ge=-180,
        le=180,
    ),
    hours: int = Query(
        48,
        ge=1,
        le=72,
    ),
):
    return await get_weather_alerts(
        latitude=latitude,
        longitude=longitude,
        hours=hours,
    )
