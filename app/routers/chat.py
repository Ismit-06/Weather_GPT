from fastapi import APIRouter

router = APIRouter(
    prefix="/api/v1/chat_legacy",
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
