from fastapi import APIRouter

router = APIRouter(
    prefix="/weather",
    tags=["Weather"]
)


@router.get("/info")
def weather_info():
    return {
        "status": "ok",
        "service": "WeatherGPT real-time weather",
        "provider": "OpenWeather",
    }
