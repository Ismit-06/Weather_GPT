from fastapi import (
    APIRouter,
    Query,
)

from app.tools.agriculture import (
    get_agriculture_assessment,
)


router = APIRouter(
    prefix="/agriculture",
    tags=["Agriculture Intelligence"],
)


@router.get("")
async def agriculture(
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
        24,
        ge=1,
        le=72,
    ),
):
    return await get_agriculture_assessment(
        latitude=latitude,
        longitude=longitude,
        hours=hours,
    )
