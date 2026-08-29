from fastapi import APIRouter

router = APIRouter(
    prefix="/chat",
    tags=["Chat"]
)

@router.get("")
def chat(
    message: str
):
    return {
        "message": message,
        "reply": "WeatherGPT backend received your message."
    }
