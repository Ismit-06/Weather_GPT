import os
import re
import httpx
from pathlib import Path
from dotenv import load_dotenv
from app.services.openrouter_tts import synthesize_speech_openrouter
from app.voice.speech_processor import SpeechTextProcessor

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

SARVAM_TTS_URL = "https://api.sarvam.ai/text-to-speech"

def chunk_speech_sentences(speech_text: str) -> list[str]:
    """
    Segments speech text into natural spoken sentence chunks for streaming audio generation.
    Preserves whole thought units rather than arbitrary token cuts.
    """
    if not speech_text:
        return []
    # Split on sentence terminals while keeping units intact
    raw = re.split(r"(?<=[.!?।])\s+", speech_text.strip())
    chunks = [c.strip() for c in raw if c.strip()]
    return chunks if chunks else [speech_text.strip()]

async def synthesize_voice_speech(
    speech_text: str,
    language_code: str = "en-IN",
    format: str = "mp3",
) -> bytes:
    """
    Synthesizes speech into high-quality audio bytes.
    Primary: OpenRouter neural Fish Audio (fish-audio/s2.1-pro-free:free).
    Fallback: Sarvam AI Indian Neural TTS for native scripts.
    """
    clean_speech = SpeechTextProcessor.process_for_speech(speech_text, language_code)
    if not clean_speech:
        return b""

    # 1. Primary: OpenRouter Neural TTS
    try:
        audio_bytes = await synthesize_speech_openrouter(
            text=clean_speech,
            response_format=format or "mp3",
        )
        if audio_bytes and len(audio_bytes) > 100:
            return audio_bytes
    except Exception as e:
        print(f"OpenRouter TTS warning: {e}, attempting Sarvam TTS fallback...")

    # 2. Fallback: Sarvam AI Multilingual TTS
    sarvam_key = os.getenv("SARVAM_API_KEY", "").strip()
    if sarvam_key:
        try:
            headers = {
                "api-subscription-key": sarvam_key,
                "Content-Type": "application/json",
            }
            payload = {
                "inputs": [clean_speech],
                "target_language_code": language_code if language_code != "auto" else "en-IN",
                "speaker": "meera",
                "model": "bulbul:v1",
            }
            async with httpx.AsyncClient(timeout=15.0) as client:
                res = await client.post(SARVAM_TTS_URL, headers=headers, json=payload)
                if res.status_code == 200:
                    import base64
                    data = res.json()
                    audios = data.get("audios", [])
                    if audios and audios[0]:
                        return base64.b64decode(audios[0])
        except Exception as e2:
            print(f"Sarvam TTS fallback error: {e2}")

    raise RuntimeError("All TTS audio generation providers failed.")

