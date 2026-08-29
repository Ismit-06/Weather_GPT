from fastapi import (
    APIRouter,
    Query,
)

from app.services.earthquakes import (
    get_earthquakes,
)


router = APIRouter(
    prefix="/earthquakes",
    tags=["Earthquakes"],
)


@router.get("")
async def earthquakes(
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

    limit: int = Query(
        20,
        ge=1,
        le=100,
    ),

    radius_km: float = Query(
        500.0,
        gt=0,
        le=5000,
    ),
):

    return await get_earthquakes(
        latitude=latitude,
        longitude=longitude,
        limit=limit,
        radius_km=radius_km,
    )
