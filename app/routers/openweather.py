from fastapi import APIRouter, HTTPException, Query

from app.services.openweather import (
    get_weather
)

router = APIRouter(
    prefix="/weather",
    tags=["Real-Time Weather"]
)


@router.get("/current")
async def weather_current(
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
):

    try:

        data = await get_weather(
            latitude=latitude,
            longitude=longitude
        )

        return data

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"OpenWeather request failed: {exc}"
        )
