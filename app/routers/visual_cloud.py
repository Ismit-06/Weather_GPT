import logging
from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.schemas.visual_cloud import (
    VisualCloudIntelligenceResponse,
    WeatherContext,
)
from app.services.image_validator import (
    ImageValidationError,
    validate_and_prepare_image,
)
from app.services.visual_cloud_analyzer import analyze_sky_image
from app.services.visual_cloud_fusion import fuse_cloud_and_weather
from app.services.met_weather import get_weather

logger = logging.getLogger("visual_cloud_router")

router = APIRouter(
    prefix="/api/v1/weather",
    tags=["Visual Cloud Intelligence"],
)


@router.post("/analyze-sky", response_model=VisualCloudIntelligenceResponse)
async def analyze_sky(
    image: UploadFile = File(..., description="Uploaded sky or cloud image (JPEG, PNG, WebP)"),
    latitude: float | None = Form(None, ge=-90, le=90, description="Optional GPS latitude"),
    longitude: float | None = Form(None, ge=-180, le=180, description="Optional GPS longitude"),
    location_name: str | None = Form(None, description="Optional name of location"),
    language: str = Form("English", description="Target response language"),
):
    """
    Analyzes a sky/cloud photograph using OpenRouter Gemma 4 vision model,
    retrieves live meteorological context for the location, and performs
    rule-based short-term weather fusion.
    """
    # 1. Read and validate image
    try:
        image_bytes = await image.read()
        _, optimized_bytes = validate_and_prepare_image(
            image_bytes=image_bytes,
            content_type=image.content_type,
        )
    except ImageValidationError as exc:
        raise HTTPException(
            status_code=400,
            detail=str(exc),
        )
    except Exception as exc:
        logger.error(f"Image read error: {exc}")
        raise HTTPException(
            status_code=400,
            detail="Unable to analyze this image. Please upload a clear sky/cloud photograph.",
        )

    # 2. Vision model analysis
    try:
        observation = await analyze_sky_image(image_bytes=optimized_bytes)
    except Exception as exc:
        logger.error(f"Vision model exception: {exc}")
        raise HTTPException(
            status_code=502,
            detail="Vision analysis service is temporarily unavailable. Please try again later.",
        )

    # 3. Retrieve live meteorological context if coordinates are available
    weather_context = None
    if latitude is not None and longitude is not None:
        try:
            met_data = await get_weather(latitude=latitude, longitude=longitude)
            current = met_data.get("current") or {}
            weather_context = WeatherContext(
                temperature=current.get("temperature_c"),
                feels_like=current.get("apparent_temperature_c"),
                humidity=current.get("relative_humidity_pct"),
                pressure=current.get("pressure_hpa"),
                wind_speed=current.get("wind_speed_ms"),
                wind_direction=current.get("wind_direction_deg"),
                precipitation_probability=current.get("precipitation_probability_pct"),
                current_precipitation=current.get("precipitation_mm"),
                forecast_precipitation=None,
                cloud_cover=current.get("cloud_cover_pct"),
                condition=current.get("symbol_code"),
                location_name=location_name or met_data.get("location", {}).get("name"),
                source="MET Norway",
            )
        except Exception as exc:
            logger.warning(f"Could not retrieve weather data for ({latitude}, {longitude}): {exc}")
            # Continue gracefully without weather data
            weather_context = None

    # 4. Perform weather fusion
    assessment, explanation = fuse_cloud_and_weather(
        observation=observation,
        weather=weather_context,
    )

    return VisualCloudIntelligenceResponse(
        status="success",
        observation=observation,
        weather_context=weather_context,
        assessment=assessment,
        explanation=explanation,
    )
