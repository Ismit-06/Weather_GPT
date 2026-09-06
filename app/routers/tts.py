from fastapi import APIRouter, HTTPException, Response, Query
from pydantic import BaseModel, Field
from app.services.openrouter_tts import synthesize_speech_openrouter

router = APIRouter(
    prefix="/api/v1/tts",
    tags=["TTS Voice Synthesis"],
)

class TtsRequest(BaseModel):
    text: str = Field(..., description="The text to synthesize into neural speech")
    language: str | None = Field(None, description="Optional language code")
    format: str = Field("mp3", description="Audio format: mp3")

@router.post("")
async def synthesize_speech_endpoint(req: TtsRequest):
    """Synthesizes text into neural voice audio using fish-audio/s2.1-pro-free:free."""
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="Text cannot be empty.")

    try:
        audio_bytes = await synthesize_speech_openrouter(
            text=req.text,
            response_format=req.format or "mp3",
        )
        return Response(
            content=audio_bytes,
            media_type="audio/mpeg",
            headers={
                "Content-Disposition": "inline; filename=speech.mp3",
                "Cache-Control": "public, max-age=3600",
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"TTS synthesis failed: {str(e)}",
        )

@router.get("")
async def synthesize_speech_get(
    text: str = Query(..., description="Text to speak"),
    language: str | None = Query(None, description="Optional language"),
):
    """GET endpoint for direct streaming into audio players."""
    if not text.strip():
        raise HTTPException(status_code=400, detail="Text cannot be empty.")

    try:
        audio_bytes = await synthesize_speech_openrouter(
            text=text,
            response_format="mp3",
        )
        return Response(
            content=audio_bytes,
            media_type="audio/mpeg",
            headers={
                "Content-Disposition": "inline; filename=speech.mp3",
                "Cache-Control": "public, max-age=3600",
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"TTS synthesis failed: {str(e)}",
        )
