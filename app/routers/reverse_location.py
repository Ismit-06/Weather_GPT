from fastapi import APIRouter, HTTPException, Query

from app.services.reverse_geocoding import reverse_geocode

router = APIRouter(
    prefix="/location",
    tags=["Location"]
)


@router.get("/reverse")
async def reverse_location(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
):
    try:
        return await reverse_geocode(
            latitude=latitude,
            longitude=longitude
        )
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Reverse geocoding failed: {exc}"
        )
