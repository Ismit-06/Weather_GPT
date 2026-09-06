import math
import struct

class VoiceActivityDetector:
    """
    Lightweight, low-latency Voice Activity Detection (VAD) for 16kHz 16-bit mono PCM streams.
    Detects speech onset, tracks voice energy, and signals speech completion upon silence window.
    """

    def __init__(
        self,
        sample_rate: int = 16000,
        energy_threshold: float = 450.0,
        silence_duration_ms: int = 700,
        min_speech_duration_ms: int = 400,
    ):
        self.sample_rate = sample_rate
        self.energy_threshold = energy_threshold
        self.silence_duration_ms = silence_duration_ms
        self.min_speech_duration_ms = min_speech_duration_ms

        self.bytes_per_sample = 2  # 16-bit PCM
        self.is_speech_active = False
        self.speech_samples_count = 0
        self.silence_samples_count = 0

    def reset(self):
        self.is_speech_active = False
        self.speech_samples_count = 0
        self.silence_samples_count = 0

    def compute_rms(self, pcm_bytes: bytes) -> float:
        """Computes Root Mean Square (RMS) energy of PCM 16-bit LE audio."""
        num_samples = len(pcm_bytes) // 2
        if num_samples == 0:
            return 0.0

        fmt = f"<{num_samples}h"
        try:
            samples = struct.unpack(fmt, pcm_bytes[:num_samples * 2])
            sum_sq = sum(s * s for s in samples)
            return math.sqrt(sum_sq / num_samples)
        except Exception:
            return 0.0

    def process_chunk(self, pcm_bytes: bytes) -> tuple[bool, bool]:
        """
        Processes a PCM audio chunk.
        Returns:
            (is_speaking, is_speech_completed)
        """
        rms = self.compute_rms(pcm_bytes)
        chunk_samples = len(pcm_bytes) // 2

        if rms >= self.energy_threshold:
            # Voice energy detected
            self.silence_samples_count = 0
            self.speech_samples_count += chunk_samples
            speech_ms = (self.speech_samples_count / self.sample_rate) * 1000.0
            if speech_ms >= self.min_speech_duration_ms:
                self.is_speech_active = True
            return (True, False)
        else:
            # Silence or background noise
            if self.is_speech_active:
                self.silence_samples_count += chunk_samples
                silence_ms = (self.silence_samples_count / self.sample_rate) * 1000.0
                if silence_ms >= self.silence_duration_ms:
                    # User finished speaking!
                    self.reset()
                    return (False, True)
                return (True, False)
            else:
                return (False, False)

