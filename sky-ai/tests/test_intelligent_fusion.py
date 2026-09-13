import pytest
from service.fusion import (
    WeatherObservationContext,
    CameraObservation,
    WeatherMetrics,
    RadarObservation,
    ForecastObservation,
    LocationInfo,
    WeatherGptFusionEngine,
)

@pytest.fixture
def base_location():
    return LocationInfo(name="Amaravati", latitude=16.5, longitude=80.6)

def test_scenario_1_clear_agreement(base_location):
    """
    Scenario 1: Camera valid clear sky + Weather clear + Radar no precipitation
    Expected: Consistent clear conditions.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=True,
            sky_confidence=0.96,
            scene_type="outdoor_sky",
            cloud_condition="clear",
            cloud_coverage=0.05,
            visible_precipitation=False,
        ),
        weather=WeatherMetrics(
            temperature_c=29.5,
            humidity_pct=65.0,
            reported_condition="clear",
            reported_cloud_cover_pct=10,
        ),
        radar=RadarObservation(precipitation_detected=False, updated_minutes_ago=5),
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="What does the sky look like?")
    assert exp.status == "success"
    assert "clear" in exp.answer.lower()
    assert exp.disagreement.has_cloud_disagreement is False
    assert exp.confidence_level == "High"
    assert "29.5" not in exp.observed_summary  # Rule 2: No temp in camera summary

def test_scenario_2_overcast_agreement(base_location):
    """
    Scenario 2: Camera overcast + Weather cloudy
    Expected: Full agreement.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=True,
            sky_confidence=0.94,
            scene_type="outdoor_sky",
            cloud_condition="overcast",
            cloud_coverage=0.92,
            visible_precipitation=False,
        ),
        weather=WeatherMetrics(
            temperature_c=27.0,
            humidity_pct=85.0,
            reported_condition="cloudy",
            reported_cloud_cover_pct=90,
        ),
        radar=RadarObservation(precipitation_detected=False),
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="Is it cloudy?")
    assert "overcast" in exp.answer.lower()
    assert exp.disagreement.has_cloud_disagreement is False
    assert exp.confidence_level == "High"

def test_scenario_3_overcast_cam_clear_api_contradiction(base_location):
    """
    Scenario 3: Camera overcast + Weather clear
    Expected: Explain disagreement without claiming one is definitively wrong.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=True,
            sky_confidence=0.92,
            scene_type="outdoor_sky",
            cloud_condition="overcast",
            cloud_coverage=0.95,
            visible_precipitation=False,
        ),
        weather=WeatherMetrics(
            temperature_c=31.0,
            humidity_pct=60.0,
            reported_condition="clear",
            reported_cloud_cover_pct=15,
        ),
        radar=RadarObservation(precipitation_detected=False),
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="Does the camera agree with the weather app?")
    assert exp.disagreement.has_cloud_disagreement is True
    assert "difference may be due to localized cloud cover" in exp.disagreement.explanation
    assert "overcast" in exp.answer.lower()

def test_scenario_4_bedsheet_invalid_sky(base_location):
    """
    Scenario 4: Camera bedsheet/fabric + Weather cloudy
    Expected: Camera observation unavailable; answers from weather data only.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=False,
            sky_confidence=0.02,
            scene_type="bedsheet",
            cloud_condition=None,
            cloud_coverage=None,
        ),
        weather=WeatherMetrics(
            temperature_c=28.0,
            humidity_pct=80.0,
            reported_condition="cloudy",
            reported_cloud_cover_pct=75,
        ),
        radar=RadarObservation(precipitation_detected=False),
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="What does my camera see?")
    assert "couldn't get a reliable view of the sky" in exp.answer.lower()
    assert "cloudy" in exp.answer.lower()
    assert "bedsheet" in exp.observed_summary.lower()

def test_scenario_5_radar_rain_cam_no_rain(base_location):
    """
    Scenario 5: Camera no rain visible + Radar rain nearby
    Expected: Distinguish radar rain from camera view.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=True,
            sky_confidence=0.90,
            scene_type="outdoor_sky",
            cloud_condition="mostly_cloudy",
            cloud_coverage=0.75,
            visible_precipitation=False,
        ),
        weather=WeatherMetrics(
            temperature_c=26.5,
            humidity_pct=88.0,
            reported_condition="light_rain",
        ),
        radar=RadarObservation(
            precipitation_detected=True,
            intensity="moderate",
            updated_minutes_ago=3,
        ),
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="Why does the radar show rain when I don't see any?")
    assert exp.disagreement.has_precipitation_disagreement is True
    assert "radar indicates precipitation nearby" in exp.answer.lower()
    assert "camera does not currently show visible rain" in exp.answer.lower()

def test_scenario_6_rain_agreement(base_location):
    """
    Scenario 6: Camera visible precipitation + Radar rain detected
    Expected: Strong agreement on active rain.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=True,
            sky_confidence=0.91,
            scene_type="outdoor_sky",
            cloud_condition="storm_clouds",
            cloud_coverage=0.98,
            visible_precipitation=True,
        ),
        weather=WeatherMetrics(
            temperature_c=24.0,
            humidity_pct=95.0,
            reported_condition="rain",
        ),
        radar=RadarObservation(
            precipitation_detected=True,
            intensity="heavy",
            updated_minutes_ago=2,
        ),
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="Is it raining now?")
    assert "both the camera view and regional radar" in exp.answer.lower()
    assert exp.confidence_level == "High"

def test_scenario_7_valid_sky_api_down(base_location):
    """
    Scenario 7: Camera valid sky + Weather API unavailable
    Expected: Answer visual observations only without inventing numerical weather.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=True,
            sky_confidence=0.95,
            scene_type="outdoor_sky",
            cloud_condition="clear",
            cloud_coverage=0.10,
            visible_precipitation=False,
        ),
        weather=None,
        radar=None,
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="What does the sky look like?")
    assert "clear" in exp.answer.lower()
    assert "telemetry is also unavailable" in exp.answer.lower() or "offline" in exp.answer.lower()
    assert exp.confidence_level == "Moderate"

def test_scenario_8_invalid_sky_api_down(base_location):
    """
    Scenario 8: Camera invalid + Weather API unavailable
    Expected: Honest uncertainty.
    """
    ctx = WeatherObservationContext(
        location=base_location,
        camera=CameraObservation(
            sky_detected=False,
            sky_confidence=0.01,
            scene_type="ceiling",
        ),
        weather=None,
        radar=None,
    )

    exp = WeatherGptFusionEngine.fuse(ctx, question="Can I go outside now?")
    assert "cannot provide a reliable weather assessment" in exp.answer.lower() or "couldn't get a reliable view" in exp.answer.lower()
    assert exp.confidence_level == "Uncertain"
