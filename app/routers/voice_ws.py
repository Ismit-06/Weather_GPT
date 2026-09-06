import json
import uuid
import base64
import asyncio
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from app.voice.session_manager import session_manager
from app.voice.stt import transcribe_audio_stream
from app.voice.tts import synthesize_voice_speech, chunk_speech_sentences
from app.voice.speech_processor import SpeechTextProcessor
from app.agent.weather_agent import WeatherAgent

router = APIRouter(
    tags=["Realtime Voice WebSocket"],
)

@router.websocket("/ws/voice")
async def voice_websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    session = None
    active_process_task: asyncio.Task | None = None

    try:
        while True:
            message = await websocket.receive()
            
            # 1. Binary Audio Chunk from Microphone (16kHz 16-bit Mono PCM)
            if "bytes" in message and message["bytes"]:
                chunk = message["bytes"]
                if session is None:
                    session = session_manager.get_or_create()

                # Feed chunk to Voice Activity Detector
                is_speaking, is_completed = session.vad.process_chunk(chunk)
                session.audio_buffer.extend(chunk)

                if is_speaking and not session.is_speaking:
                    session.is_speaking = True
                    await websocket.send_json({
                        "type": "speech_started",
                        "session_id": session.session_id,
                    })

                # If user finished speaking and we have collected audio
                if is_completed and len(session.audio_buffer) >= 3200:  # >= 100ms
                    recorded_audio = bytes(session.audio_buffer)
                    session.audio_buffer.clear()
                    session.is_speaking = False

                    # Cancel any prior turn still running
                    if active_process_task and not active_process_task.done():
                        active_process_task.cancel()

                    request_id = str(uuid.uuid4())
                    session.current_request_id = request_id

                    # Launch turn processing in background task to keep socket responsive
                    active_process_task = asyncio.create_task(
                        process_voice_turn(
                            websocket=websocket,
                            session=session,
                            audio_bytes=recorded_audio,
                            request_id=request_id,
                        )
                    )

            # 2. Text / JSON Command Messages
            elif "text" in message and message["text"]:
                try:
                    data = json.loads(message["text"])
                except Exception:
                    continue

                msg_type = data.get("type")

                if msg_type == "voice_session_start":
                    session = session_manager.get_or_create(
                        session_id=data.get("session_id"),
                        latitude=float(data.get("latitude", 20.9320)),
                        longitude=float(data.get("longitude", 77.7523)),
                        location_name=data.get("location_name", "Current Location"),
                        language_code=data.get("language", "auto"),
                    )
                    await websocket.send_json({
                        "type": "session_started",
                        "session_id": session.session_id,
                    })

                elif msg_type == "audio_chunk":
                    # Base64 encoded audio chunk
                    raw_b64 = data.get("data", "")
                    if raw_b64:
                        raw_bytes = base64.b64decode(raw_b64)
                        if session is None:
                            session = session_manager.get_or_create()
                        session.audio_buffer.extend(raw_bytes)
                        is_speaking, is_completed = session.vad.process_chunk(raw_bytes)
                        if is_completed and len(session.audio_buffer) >= 3200:
                            recorded_audio = bytes(session.audio_buffer)
                            session.audio_buffer.clear()
                            request_id = str(uuid.uuid4())
                            session.current_request_id = request_id
                            active_process_task = asyncio.create_task(
                                process_voice_turn(
                                    websocket=websocket,
                                    session=session,
                                    audio_bytes=recorded_audio,
                                    request_id=request_id,
                                )
                            )

                elif msg_type == "interrupt":
                    if session:
                        session_manager.interrupt(session.session_id)
                        if active_process_task and not active_process_task.done():
                            active_process_task.cancel()
                    await websocket.send_json({
                        "type": "interrupted",
                        "session_id": session.session_id if session else None,
                    })

                elif msg_type == "voice_session_stop":
                    if session:
                        session_manager.remove(session.session_id)
                    break

    except (WebSocketDisconnect, asyncio.CancelledError):
        pass
    except Exception as e:
        print(f"Voice WebSocket Error: {e}")
    finally:
        if session:
            session_manager.remove(session.session_id)


async def process_voice_turn(
    websocket: WebSocket,
    session,
    audio_bytes: bytes,
    request_id: str,
):
    """
    Orchestrates the voice turn: STT -> Language Resolution -> Weather Agent -> Split Display/Speech -> TTS Streaming.
    """
    try:
        # 1. Multi-lingual Speech-to-Text
        stt_result = await transcribe_audio_stream(
            pcm_bytes=audio_bytes,
            language_code=session.language_code,
        )

        transcript = stt_result.get("transcript", "").strip()
        detected_lang = stt_result.get("language_code", "en-IN")

        if not transcript:
            await websocket.send_json({
                "type": "error",
                "request_id": request_id,
                "code": "STT_EMPTY",
                "message": "I couldn't hear that clearly. Please try again.",
            })
            return

        # Emit final user transcript & detected language
        await websocket.send_json({
            "type": "transcript_final",
            "request_id": request_id,
            "text": transcript,
            "language_code": detected_lang,
        })

        if session.cancellation_event.is_set():
            return

        # 2. Weather Intelligence Agent Reasoning
        agent = WeatherAgent(timezone_name="Asia/Kolkata")
        weather_result = await agent.analyze(
            question=transcript,
            latitude=session.latitude,
            longitude=session.longitude,
            language=session.language_code if session.language_code != "auto" else detected_lang,
            history=session.history[-2:] if session.history else [],
            agent_state=session.agent_state,
        )

        raw_answer = weather_result.get("answer", "")
        detected_language = weather_result.get("language", "English")
        detected_language_code = weather_result.get("language_code", detected_lang)

        # Update session memory
        session.agent_state = weather_result.get("agent_state", session.agent_state)

        # 3. SEPARATE DISPLAY TEXT & SPEECH TEXT
        # Display Text: Contains rich markdown icons for UI
        display_text = raw_answer
        # Speech Text: Cleaned by SpeechTextProcessor (Hard invariant: NO emojis, NO markdown)
        speech_text = SpeechTextProcessor.process_for_speech(raw_answer, detected_language_code)

        if session.cancellation_event.is_set():
            return

        # Send assistant text for instant visual rendering
        await websocket.send_json({
            "type": "assistant_text",
            "request_id": request_id,
            "display_text": display_text,
            "speech_text": speech_text,
            "language": detected_language,
            "language_code": detected_language_code,
        })

        # 4. Stream TTS Audio Sentence Chunks
        sentences = chunk_speech_sentences(speech_text)
        for idx, sentence in enumerate(sentences):
            if session.cancellation_event.is_set():
                break

            audio_data = await synthesize_voice_speech(
                speech_text=sentence,
                language_code=detected_language_code,
                format="mp3",
            )

            if session.cancellation_event.is_set():
                break

            if audio_data:
                await websocket.send_json({
                    "type": "audio_chunk",
                    "request_id": request_id,
                    "chunk_index": idx,
                    "is_last_chunk": (idx == len(sentences) - 1),
                    "data": base64.b64encode(audio_data).decode("utf-8"),
                    "format": "mp3",
                })

        await websocket.send_json({
            "type": "speech_finished",
            "request_id": request_id,
        })

    except asyncio.CancelledError:
        pass
    except Exception as e:
        print(f"Turn processing error: {e}")
        try:
            await websocket.send_json({
                "type": "error",
                "request_id": request_id,
                "code": "PIPELINE_ERROR",
                "message": f"Error: {str(e)}",
            })
        except Exception:
            pass

