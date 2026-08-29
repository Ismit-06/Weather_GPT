from fastapi import APIRouter, HTTPException, Query

from app.services.global_weather import (
    get_global_forecast,
    get_global_temperature,
)


router = APIRouter(
    prefix="/global-weather",
    tags=["Global Weather"]
)


@router.get("/forecast")
async def global_forecast(
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
    forecast_days: int = Query(
        7,
        ge=1,
        le=16
    ),
):

    try:

        return await get_global_forecast(
            latitude=latitude,
            longitude=longitude,
            forecast_days=forecast_days,
        )

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Global forecast request failed: {exc}"
        )


@router.get("/temperature")
async def global_temperature(
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

        return await get_global_temperature(
            latitude=latitude,
            longitude=longitude
        )

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Global temperature request failed: {exc}"
        )
