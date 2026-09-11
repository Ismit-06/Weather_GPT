import pytest
from app.schemas.visual_cloud import (
    CloudObservation,
    WeatherContext,
    CloudType,
    CloudThickness,
    VerticalDevelopment,
    PrecipitationAppearance,
)
from app.services.visual_cloud_fusion import fuse_cloud_and_weather


def test_case_1_towering_cumulus_with_supporting_weather():
    obs = CloudObservation(
        dominant_cloud_type=CloudType.towering_cumulus,
        cloud_coverage=0.65,
        cloud_thickness=CloudThickness.thick,
        vertical_development=VerticalDevelopment.strong,
        convective_appearance=True,
        confidence=0.85,
    )
    weather = WeatherContext(
        temperature=31.0,
        humidity=78.0,
        precipitation_probability=65.0,
        current_precipitation=0.0,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather)
    assert assessment.precipitation_signal == "elevated"
    assert assessment.convection_signal == "high"
    assert assessment.overall_short_term_signal == "increasing_rain_risk"
    assert assessment.confidence == "High"
    assert "Sky Analysis" in explanation
    assert "Short-Term Outlook" in explanation
    assert "Why?" in explanation


def test_case_2_cumulonimbus_with_storm_signal():
    obs = CloudObservation(
        dominant_cloud_type=CloudType.cumulonimbus,
        cloud_coverage=0.90,
        cloud_thickness=CloudThickness.very_thick,
        vertical_development=VerticalDevelopment.extreme,
        convective_appearance=True,
        precipitation_appearance=PrecipitationAppearance.likely_visible,
        confidence=0.92,
    )
    weather = WeatherContext(
        temperature=27.0,
        humidity=85.0,
        precipitation_probability=80.0,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather)
    assert assessment.convection_signal == "high"
    assert assessment.precipitation_signal == "high"
    assert assessment.overall_short_term_signal == "convective_storm_potential"
    assert assessment.confidence == "High"
    # Never claim absolute certainty
    assert "Thunderstorm will definitely occur" not in explanation
    assert "Important" in explanation


def test_case_3_nimbostratus_with_steady_precipitation():
    obs = CloudObservation(
        dominant_cloud_type=CloudType.nimbostratus,
        cloud_coverage=1.0,
        cloud_thickness=CloudThickness.very_thick,
        vertical_development=VerticalDevelopment.moderate,
        precipitation_appearance=PrecipitationAppearance.likely_visible,
        confidence=0.88,
    )
    weather = WeatherContext(
        temperature=22.0,
        humidity=92.0,
        current_precipitation=2.5,
        precipitation_probability=90.0,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather)
    assert assessment.precipitation_signal == "high"
    assert assessment.overall_short_term_signal == "steady_precipitation"
    assert assessment.confidence == "High"


def test_case_4_cirrus_without_rain_context():
    obs = CloudObservation(
        dominant_cloud_type=CloudType.cirrus,
        cloud_coverage=0.30,
        cloud_thickness=CloudThickness.thin,
        vertical_development=VerticalDevelopment.none,
        confidence=0.82,
    )
    weather = WeatherContext(
        temperature=28.0,
        humidity=45.0,
        precipitation_probability=10.0,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather)
    assert assessment.precipitation_signal == "low"
    assert assessment.convection_signal == "none"
    assert assessment.overall_short_term_signal == "fair_conditions"
    assert assessment.confidence == "High"


def test_case_5_conflicting_visual_and_weather_evidence():
    # Towering cumulus visually, but dry air and 0% forecast rain
    obs = CloudObservation(
        dominant_cloud_type=CloudType.towering_cumulus,
        cloud_coverage=0.50,
        vertical_development=VerticalDevelopment.moderate,
        convective_appearance=True,
        confidence=0.75,
    )
    weather = WeatherContext(
        temperature=32.0,
        humidity=30.0,
        precipitation_probability=5.0,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather)
    # Does not blindly declare a downpour
    assert assessment.precipitation_signal == "moderate"
    assert assessment.overall_short_term_signal == "developing_convection"
    # Should explain the nuance
    assert "moisture" in explanation.lower()


def test_case_6_missing_weather_data():
    obs = CloudObservation(
        dominant_cloud_type=CloudType.cumulonimbus,
        cloud_coverage=0.80,
        confidence=0.85,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather=None)
    assert assessment.is_weather_data_available is False
    assert assessment.confidence in ["Low", "Very Low"]
    assert "Live weather telemetry was unavailable" in explanation


def test_case_7_unknown_cloud_classification():
    obs = CloudObservation(
        analysis_status="uncertain",
        dominant_cloud_type=CloudType.unknown,
        confidence=0.20,
        limitations=["Image is blurry and backlit."],
    )
    weather = WeatherContext(
        temperature=25.0,
        humidity=60.0,
    )
    assessment, explanation = fuse_cloud_and_weather(obs, weather)
    assert assessment.overall_short_term_signal == "uncertain_visual_evidence"
    assert assessment.confidence in ["Low", "Very Low"]
    assert "ambiguous or obscured" in assessment.next_1_hour
