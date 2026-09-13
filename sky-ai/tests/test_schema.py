import pytest
from pydantic import ValidationError
from service.schemas import SkyAnalysisResponse

def test_valid_sky_schema():
    payload = {
        "request_id": "test-123",
        "sky_detected": True,
        "sky_confidence": 0.95,
        "scene_type": "outdoor_sky",
        "cloud_condition": "partly_cloudy",
        "cloud_coverage": 0.45,
        "visible_precipitation": False,
        "horizon_visible": True,
        "obstruction": "none",
        "image_quality": "good",
        "model_version": "SKY-LORA-002"
    }
    model = SkyAnalysisResponse.model_validate(payload)
    assert model.sky_detected is True
    assert model.cloud_coverage == 0.45

def test_non_sky_null_normalization():
    # If sky_detected is false, cloud details must be normalized to null
    payload = {
        "request_id": "test-456",
        "sky_detected": False,
        "sky_confidence": 0.02,
        "scene_type": "bedsheet",
        "cloud_condition": "overcast",  # Model hallucination
        "cloud_coverage": 0.80,         # Model hallucination
        "visible_precipitation": False,
        "horizon_visible": False,
        "obstruction": "unknown",
        "image_quality": "good",
        "model_version": "SKY-LORA-002"
    }
    model = SkyAnalysisResponse.model_validate(payload)
    assert model.sky_detected is False
    assert model.cloud_condition is None
    assert model.cloud_coverage is None
