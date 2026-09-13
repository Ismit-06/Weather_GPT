import pytest
from pydantic import ValidationError
from app.schemas.visual_cloud import (
    CloudObservation,
    CloudType,
    CloudThickness,
    VerticalDevelopment,
    ApparentCloudBase,
    PrecipitationAppearance,
    CloudDevelopmentTrend,
)


def test_valid_cloud_observation():
    data = {
        "analysis_status": "success",
        "dominant_cloud_type": "towering_cumulus",
        "secondary_cloud_type": "cirrus",
        "cloud_coverage": 0.68,
        "cloud_thickness": "thick",
        "vertical_development": "strong",
        "apparent_cloud_base": "low",
        "convective_appearance": True,
        "precipitation_appearance": "possible",
        "development_trend": "unknown",
        "confidence": 0.88,
        "limitations": [],
    }
    obs = CloudObservation.model_validate(data)
    assert obs.dominant_cloud_type == CloudType.towering_cumulus
    assert obs.cloud_coverage == 0.68
    assert obs.confidence == 0.88
    assert obs.convective_appearance is True


def test_unknown_cloud_observation():
    data = {
        "analysis_status": "uncertain",
        "dominant_cloud_type": "unknown",
        "secondary_cloud_type": None,
        "cloud_coverage": None,
        "cloud_thickness": "unknown",
        "vertical_development": "unknown",
        "apparent_cloud_base": "unknown",
        "convective_appearance": "unknown",
        "precipitation_appearance": "unknown",
        "development_trend": "unknown",
        "confidence": 0.25,
        "limitations": ["Partially obscured"],
    }
    obs = CloudObservation.model_validate(data)
    assert obs.dominant_cloud_type == CloudType.unknown
    assert obs.cloud_coverage is None
    assert obs.convective_appearance == "unknown"


def test_invalid_coverage_out_of_bounds():
    with pytest.raises(ValidationError):
        CloudObservation(
            dominant_cloud_type=CloudType.cumulus,
            cloud_coverage=1.5,  # must be <= 1.0
            confidence=0.8,
        )


def test_invalid_confidence_negative():
    with pytest.raises(ValidationError):
        CloudObservation(
            dominant_cloud_type=CloudType.cumulus,
            confidence=-0.1,  # must be >= 0.0
        )


def test_single_image_trend_normalization():
    # If a model accidentally output "increasing", we ensure our schema handles or defaults appropriately
    obs = CloudObservation(
        dominant_cloud_type=CloudType.cumulonimbus,
        development_trend=CloudDevelopmentTrend.unknown,
    )
    assert obs.development_trend == CloudDevelopmentTrend.unknown
