from fastapi import APIRouter, HTTPException, Response, Query
from pydantic import BaseModel, Field
import os

router = APIRouter(
    prefix="/api/v1/tts",
    tags=["TTS Voice Synthesis"],
)

class TtsRequest(BaseModel):
    text: str = Field(..., description="The text to synthesize into neural speech")
    language: str | None = Field(None, description="Optional language code")
    format: str = Field("mp3", description="Audio format: mp3")

async def get_neural_audio_bytes(text: str, language: str | None, fmt: str = "mp3") -> bytes:
    # 1. Primary: Sarvam Bulbul v3 for human-like Indian & English voices
    if os.getenv("SARVAM_API_KEY"):
        try:
            from app.services.sarvam_tts import synthesize_speech_sarvam
            audio = await synthesize_speech_sarvam(text=text, language=language)
            if audio and len(audio) > 100:
                return audio
        except Exception as e:
            print(f"Sarvam TTS warning: {e}, falling back to OpenRouter TTS...")

    # 2. Fallback: OpenRouter Neural TTS
    if os.getenv("OPENROUTER_API_KEY"):
        try:
            from app.services.openrouter_tts import synthesize_speech_openrouter
            audio = await synthesize_speech_openrouter(text=text, response_format=fmt or "mp3")
            if audio and len(audio) > 100:
                return audio
        except Exception as e:
            print(f"OpenRouter TTS warning: {e}")

    raise RuntimeError("All neural TTS audio providers failed.")

@router.post("")
async def synthesize_speech_endpoint(req: TtsRequest):
    """Synthesizes text into high-quality human neural speech."""
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="Text cannot be empty.")

    try:
        audio_bytes = await get_neural_audio_bytes(
            text=req.text,
            language=req.language,
            fmt=req.format or "mp3",
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
        audio_bytes = await get_neural_audio_bytes(
            text=text,
            language=language,
            fmt="mp3",
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
