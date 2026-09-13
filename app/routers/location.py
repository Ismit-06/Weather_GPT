from fastapi import APIRouter

router = APIRouter(
    prefix="/location",
    tags=["Location"]
)

@router.get("/info")
def location_info():
    return {
        "status": "ok",
        "service": "Location service"
    }
