"""
WeatherGPT Intelligent Fusion Engine (Phase 9)
Unifies Camera Visual Observations, Weather API Telemetry, Radar feeds, and Forecasts.
Applies rigorous source ownership and deterministic contradiction detection.
"""

from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from pydantic import BaseModel, Field


class LocationInfo(BaseModel):
    name: Optional[str] = None
    latitude: float
    longitude: float
    timezone: str = "Asia/Kolkata"


class CameraObservation(BaseModel):
    sky_detected: bool = False
    sky_confidence: float = Field(0.0, ge=0.0, le=1.0)
    scene_type: str = "unknown"
    cloud_condition: Optional[str] = None
    cloud_coverage: Optional[float] = None
    visible_precipitation: bool = False
    horizon_visible: bool = False
    obstruction: str = "none"
    image_quality: str = "good"
    model_version: str = "SKY-LORA-002"
    captured_at: Optional[str] = None  # ISO format


class WeatherMetrics(BaseModel):
    temperature_c: Optional[float] = None
    feels_like_c: Optional[float] = None
    humidity_pct: Optional[float] = None
    wind_speed_ms: Optional[float] = None
    pressure_hpa: Optional[float] = None
    reported_condition: Optional[str] = None
    reported_cloud_cover_pct: Optional[int] = None
    observed_at: Optional[str] = None  # ISO format
    source: str = "MET Norway"


class RadarObservation(BaseModel):
    precipitation_detected: bool = False
    intensity: Optional[str] = "none"  # none, light, moderate, heavy
    nearest_precip_km: Optional[float] = None
    updated_at: Optional[str] = None
    updated_minutes_ago: Optional[int] = None
    source: str = "RainViewer"


class ForecastObservation(BaseModel):
    next_1h_precipitation_prob_pct: Optional[float] = None
    next_6h_summary: Optional[str] = None
    forecast_generated_at: Optional[str] = None


class WeatherObservationContext(BaseModel):
    version: str = "1.0"
    location: LocationInfo
    camera: CameraObservation
    weather: Optional[WeatherMetrics] = None
    radar: Optional[RadarObservation] = None
    forecast: Optional[ForecastObservation] = None


class DisagreementDetail(BaseModel):
    has_cloud_disagreement: bool = False
    has_precipitation_disagreement: bool = False
    explanation: str = ""


class FusionExplanation(BaseModel):
    status: str = "success"
    context: WeatherObservationContext
    observed_summary: str
    reported_summary: str
    radar_summary: str
    disagreement: DisagreementDetail
    answer: str
    confidence_level: str  # High, Moderate, Low, Uncertain


class WeatherGptFusionEngine:
    """
    Reasoning engine implementing Phase 9 fusion rules.
    """

    @classmethod
    def analyze_disagreement(
        cls,
        camera: CameraObservation,
        weather: Optional[WeatherMetrics],
        radar: Optional[RadarObservation],
    ) -> DisagreementDetail:
        has_cloud_dis = False
        has_precip_dis = False
        notes = []

        # Check cloud disagreement ONLY if sky was reliably observed
        if camera.sky_detected and weather and weather.reported_cloud_cover_pct is not None:
            cam_cloudy = camera.cloud_condition in ["mostly_cloudy", "overcast", "storm_clouds"] or (
                camera.cloud_coverage is not None and camera.cloud_coverage >= 0.70
            )
            api_clear = weather.reported_cloud_cover_pct <= 25

            cam_clear = camera.cloud_condition == "clear" or (
                camera.cloud_coverage is not None and camera.cloud_coverage <= 0.20
            )
            api_cloudy = weather.reported_cloud_cover_pct >= 75

            if cam_cloudy and api_clear:
                has_cloud_dis = True
                notes.append(
                    f"The camera currently appears heavily overcast ({int((camera.cloud_coverage or 0.9)*100)}% cover), "
                    f"while the weather service reports clearer conditions ({weather.reported_cloud_cover_pct}% cloud cover). "
                    "The difference may be due to localized cloud cover or timing."
                )
            elif cam_clear and api_cloudy:
                has_cloud_dis = True
                notes.append(
                    f"The camera view appears clear overhead, while regional weather models report higher cloudiness ({weather.reported_cloud_cover_pct}%). "
                    "You may be experiencing a localized break in the cloud deck."
                )

        # Check precipitation disagreement
        if radar and radar.precipitation_detected and not camera.visible_precipitation:
            has_precip_dis = True
            notes.append(
                "The radar indicates precipitation nearby, although the camera does not currently show visible rain falling in the immediate frame."
            )
        elif radar and not radar.precipitation_detected and camera.visible_precipitation:
            has_precip_dis = True
            notes.append(
                "The camera appears to observe visible precipitation, but recent radar echoes do not indicate significant reflectivity directly overhead."
            )

        return DisagreementDetail(
            has_cloud_disagreement=has_cloud_dis,
            has_precipitation_disagreement=has_precip_dis,
            explanation=" ".join(notes) if notes else "Observations and weather models are consistent.",
        )

    @classmethod
    def fuse(
        cls,
        context: WeatherObservationContext,
        question: Optional[str] = None,
    ) -> FusionExplanation:
        camera = context.camera
        weather = context.weather
        radar = context.radar
        forecast = context.forecast

        # 1. Evaluate Disagreements
        disagreement = cls.analyze_disagreement(camera, weather, radar)

        # 2. Build Source Summaries strictly adhering to ownership
        # Camera
        if camera.sky_detected:
            cond_str = (camera.cloud_condition or "sky").replace("_", " ")
            cov_pct = int((camera.cloud_coverage or 0.0) * 100)
            rain_str = "with visible precipitation" if camera.visible_precipitation else "no visible rain"
            obs_summary = f"Camera directly observes {cond_str} ({cov_pct}% cloud coverage, {rain_str})."
        else:
            obs_summary = (
                f"Camera did not detect genuine sky (scene identified as {camera.scene_type or 'non-sky'}). "
                "No visual sky observation available."
            )

        # Weather API
        if weather and weather.temperature_c is not None:
            rep_summary = (
                f"Station reports {weather.temperature_c:.1f}°C, "
                f"{weather.humidity_pct or 0:.0f}% humidity, wind {weather.wind_speed_ms or 0:.1f} m/s, "
                f"and {weather.reported_condition or 'variable'} conditions."
            )
        else:
            rep_summary = "Current weather station telemetry is currently unavailable."

        # Radar
        if radar:
            if radar.precipitation_detected:
                rad_summary = f"Radar reports active precipitation echoes in the area (updated {radar.updated_minutes_ago or 5} min ago)."
            else:
                rad_summary = f"Radar reports no precipitation echoes nearby (updated {radar.updated_minutes_ago or 5} min ago)."
        else:
            rad_summary = "Radar data unavailable."

        # 3. Formulate Conversational Answer based on user question or general query
        q_lower = (question or "").lower()

        # Rule enforcement for confidence level
        if camera.sky_detected and weather:
            conf_level = "High" if not disagreement.has_cloud_disagreement else "Moderate"
        elif camera.sky_detected or weather:
            conf_level = "Moderate"
        else:
            conf_level = "Uncertain"

        answer_parts = []

        # Question: What does the sky look like? / Is it cloudy?
        if any(w in q_lower for w in ["what does the sky look like", "is it cloudy", "why does it look cloudy", "what does my camera see"]):
            if camera.sky_detected:
                cov_pct = int((camera.cloud_coverage or 0.0) * 100)
                cond_name = (camera.cloud_condition or "cloudy").replace("_", " ")
                answer_parts.append(
                    f"The camera is detecting {cond_name} skies with roughly {cov_pct}% cloud coverage."
                )
                if weather and weather.reported_condition:
                    if disagreement.has_cloud_disagreement:
                        answer_parts.append(disagreement.explanation)
                    else:
                        answer_parts.append(f"Current weather reports also indicate {weather.reported_condition}, confirming consistent conditions.")
                else:
                    answer_parts.append("Weather station telemetry is currently offline.")
            else:
                answer_parts.append("I couldn't get a reliable view of the sky from the camera.")
                if weather and weather.reported_condition:
                    answer_parts.append(f"Current weather data, however, reports {weather.reported_condition} with {weather.temperature_c}°C.")
                else:
                    answer_parts.append("Weather telemetry is also unavailable at this time.")

        # Question: Rain inquiry
        elif any(w in q_lower for w in ["rain", "raining", "going to rain", "radar"]):
            if radar and radar.precipitation_detected:
                if camera.sky_detected and not camera.visible_precipitation:
                    answer_parts.append(
                        "The radar indicates precipitation nearby, although the camera does not currently show visible rain."
                    )
                elif camera.sky_detected and camera.visible_precipitation:
                    answer_parts.append(
                        "Both the camera view and regional radar indicate active precipitation in your area."
                    )
                else:
                    answer_parts.append("Radar detects precipitation in the surrounding vicinity.")
            else:
                if camera.sky_detected and camera.visible_precipitation:
                    answer_parts.append("The camera shows visible rain, but recent radar data does not show heavy echoes.")
                else:
                    answer_parts.append("Neither the camera nor radar currently indicates active precipitation nearby.")

            if forecast and forecast.next_1h_precipitation_prob_pct is not None:
                answer_parts.append(f"The short-term forecast models a {forecast.next_1h_precipitation_prob_pct:.0f}% chance of rain in the next hour.")

        # General / default question
        else:
            if not camera.sky_detected and not weather:
                answer_parts.append(
                    "I cannot provide a reliable weather assessment right now because the camera view does not show genuine sky and weather telemetry is unavailable."
                )
            elif not camera.sky_detected and weather:
                answer_parts.append(
                    f"The camera cannot see genuine sky, but current weather telemetry reports {weather.reported_condition or 'fair'} conditions at {weather.temperature_c}°C with {weather.humidity_pct}% humidity."
                )
            elif camera.sky_detected and not weather:
                cov_pct = int((camera.cloud_coverage or 0.0) * 100)
                cond_name = (camera.cloud_condition or "clear").replace("_", " ")
                answer_parts.append(
                    f"The camera observes {cond_name} conditions with {cov_pct}% cloud cover. Numerical weather telemetry (temperature and pressure) is currently offline."
                )
            else:
                # Both camera and weather present
                cov_pct = int((camera.cloud_coverage or 0.0) * 100)
                cond_name = (camera.cloud_condition or "clear").replace("_", " ")
                if disagreement.has_cloud_disagreement or disagreement.has_precipitation_disagreement:
                    answer_parts.append(
                        f"The camera observes {cond_name} conditions ({cov_pct}% cover), while station telemetry reports {weather.temperature_c}°C. {disagreement.explanation}"
                    )
                else:
                    answer_parts.append(
                        f"Conditions are consistent: the camera shows {cond_name} skies ({cov_pct}% cover), and weather data reports {weather.temperature_c}°C with {weather.humidity_pct}% humidity."
                    )

        return FusionExplanation(
            status="success",
            context=context,
            observed_summary=obs_summary,
            reported_summary=rep_summary,
            radar_summary=rad_summary,
            disagreement=disagreement,
            answer=" ".join(answer_parts),
            confidence_level=conf_level,
        )
