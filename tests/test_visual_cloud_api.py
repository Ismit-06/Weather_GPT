import io
from unittest.mock import AsyncMock, patch
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.schemas.visual_cloud import (
    CloudObservation,
    CloudType,
    CloudThickness,
    VerticalDevelopment,
)

client = TestClient(app)


def get_test_image_bytes(format="JPEG", size=(200, 200)) -> bytes:
    buf = io.BytesIO()
    img = Image.new("RGB", size, color=(120, 180, 240))
    img.save(buf, format=format)
    return buf.getvalue()


@pytest.mark.asyncio
async def test_api_successful_analysis():
    mock_obs = CloudObservation(
        analysis_status="success",
        dominant_cloud_type=CloudType.towering_cumulus,
        cloud_coverage=0.70,
        cloud_thickness=CloudThickness.thick,
        vertical_development=VerticalDevelopment.strong,
        convective_appearance=True,
        confidence=0.88,
    )
    mock_met = {
        "status": "success",
        "current": {
            "temperature_c": 30.0,
            "apparent_temperature_c": 34.0,
            "relative_humidity_pct": 75.0,
            "pressure_hpa": 1010.0,
            "wind_speed_ms": 3.5,
            "wind_direction_deg": 180.0,
            "precipitation_probability_pct": 60.0,
            "precipitation_mm": 0.0,
            "cloud_cover_pct": 75.0,
            "symbol_code": "partlycloudy_day",
        },
    }

    with patch("app.routers.visual_cloud.analyze_sky_image", new=AsyncMock(return_value=mock_obs)), \
         patch("app.routers.visual_cloud.get_weather", new=AsyncMock(return_value=mock_met)):

        img_bytes = get_test_image_bytes()
        response = client.post(
            "/api/v1/weather/analyze-sky",
            files={"image": ("sky.jpg", img_bytes, "image/jpeg")},
            data={"latitude": "20.2961", "longitude": "85.8245", "location_name": "Bhubaneswar"},
        )

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "success"
        assert data["observation"]["dominant_cloud_type"] == "towering_cumulus"
        assert data["weather_context"]["temperature"] == 30.0
        assert data["assessment"]["precipitation_signal"] == "elevated"
        assert "Sky Analysis" in data["explanation"]


def test_api_invalid_image():
    response = client.post(
        "/api/v1/weather/analyze-sky",
        files={"image": ("bad.txt", b"not an image", "text/plain")},
    )
    assert response.status_code == 400
    assert "Unable to analyze this image" in response.json()["detail"]


@pytest.mark.asyncio
async def test_api_vision_failure():
    with patch("app.routers.visual_cloud.analyze_sky_image", side_effect=RuntimeError("OpenRouter down")):
        img_bytes = get_test_image_bytes()
        response = client.post(
            "/api/v1/weather/analyze-sky",
            files={"image": ("sky.jpg", img_bytes, "image/jpeg")},
        )
        assert response.status_code == 502
        assert "temporarily unavailable" in response.json()["detail"]


@pytest.mark.asyncio
async def test_api_weather_failure_graceful_fallback():
    mock_obs = CloudObservation(
        analysis_status="success",
        dominant_cloud_type=CloudType.cumulus,
        cloud_coverage=0.40,
        confidence=0.85,
    )

    with patch("app.routers.visual_cloud.analyze_sky_image", new=AsyncMock(return_value=mock_obs)), \
         patch("app.routers.visual_cloud.get_weather", side_effect=Exception("MET service timeout")):

        img_bytes = get_test_image_bytes()
        response = client.post(
            "/api/v1/weather/analyze-sky",
            files={"image": ("sky.jpg", img_bytes, "image/jpeg")},
            data={"latitude": "20.2961", "longitude": "85.8245"},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "success"
        assert data["weather_context"] is None
        assert data["assessment"]["is_weather_data_available"] is False
        assert "Live weather telemetry was unavailable" in data["explanation"]
