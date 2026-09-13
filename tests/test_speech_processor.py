from app.voice.speech_processor import SpeechTextProcessor
from app.voice.vad import VoiceActivityDetector

def test_speech_text_processor_emoji_and_markdown():
    raw_input = "🌧️ **Rain forecast: 82%** for today! ☔ Carry an umbrella."
    speech_output = SpeechTextProcessor.process_for_speech(raw_input, "en-IN")
    
    assert "🌧️" not in speech_output
    assert "☔" not in speech_output
    assert "**" not in speech_output
    assert "82 percent" in speech_output
    assert "Carry an umbrella" in speech_output

def test_speech_text_processor_hindi_units():
    raw_input = "🌡️ आज तापमान 32°C रहेगा और 80% बारिश की संभावना है। 🌧️"
    speech_output = SpeechTextProcessor.process_for_speech(raw_input, "hi-IN")
    
    assert "🌡️" not in speech_output
    assert "🌧️" not in speech_output
    assert "32 डिग्री सेल्सियस" in speech_output
    assert "80 प्रतिशत" in speech_output

def test_vad_detection():
    vad = VoiceActivityDetector(sample_rate=16000, energy_threshold=300)
    # Silence chunk
    silence_pcm = b"\x00\x00" * 320
    is_speaking, is_completed = vad.process_chunk(silence_pcm)
    assert not is_speaking
    assert not is_completed

