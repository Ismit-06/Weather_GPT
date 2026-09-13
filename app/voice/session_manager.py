import uuid
import asyncio
from dataclasses import dataclass, field
from app.voice.vad import VoiceActivityDetector

@dataclass
class VoiceSession:
    session_id: str
    latitude: float = 20.9320
    longitude: float = 77.7523
    location_name: str = "Current Location"
    language_code: str = "auto"
    current_request_id: str | None = None
    is_speaking: bool = False
    vad: VoiceActivityDetector = field(default_factory=VoiceActivityDetector)
    audio_buffer: bytearray = field(default_factory=bytearray)
    cancellation_event: asyncio.Event = field(default_factory=asyncio.Event)
    history: list[dict] = field(default_factory=list)
    agent_state: dict = field(default_factory=dict)

class VoiceSessionManager:
    """
    Manages state, audio buffers, barge-in cancellation and telemetry for real-time voice sessions.
    """
    def __init__(self):
        self._sessions: dict[str, VoiceSession] = {}

    def get_or_create(
        self,
        session_id: str | None = None,
        latitude: float = 20.9320,
        longitude: float = 77.7523,
        location_name: str = "Current Location",
        language_code: str = "auto",
    ) -> VoiceSession:
        sid = session_id or str(uuid.uuid4())
        if sid not in self._sessions:
            self._sessions[sid] = VoiceSession(
                session_id=sid,
                latitude=latitude,
                longitude=longitude,
                location_name=location_name,
                language_code=language_code,
            )
        else:
            s = self._sessions[sid]
            s.latitude = latitude
            s.longitude = longitude
            s.location_name = location_name
            if language_code:
                s.language_code = language_code
        return self._sessions[sid]

    def remove(self, session_id: str):
        if session_id in self._sessions:
            del self._sessions[session_id]

    def interrupt(self, session_id: str) -> None:
        """Interrupts and cancels any currently active TTS / reasoning job."""
        if session_id in self._sessions:
            session = self._sessions[session_id]
            session.cancellation_event.set()
            session.is_speaking = False
            session.audio_buffer.clear()
            session.vad.reset()
            # Replace cancellation event with fresh one for new turn
            session.cancellation_event = asyncio.Event()

session_manager = VoiceSessionManager()

