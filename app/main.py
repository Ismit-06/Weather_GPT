from app.services.sarvam_speech import transcribe_audio
from app.chat.weather_intelligence import answer_weather_question
from app.services.reverse_geocoding import reverse_geocode
from app.services.met_weather import get_weather
from app.services.global_weather import (
    get_global_forecast,
    get_global_temperature,
)
from app.services.location_weather import (
    build_feature_from_weather,
    fetch_location_weather,
    save_location_feature,
)
from app.database import get_db
from sqlalchemy.orm import Session
from app.services.geocoding import search_location
from fastapi import FastAPI, Query, File, UploadFile, HTTPException
from fastapi.responses import HTMLResponse
import html
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.database import Base, engine
from app.models.reservoir import Reservoir

from app.routers.alerts import router as alerts_router
from app.routers.dams import router as dams_router
from app.routers.earthquakes import router as earthquakes_router
from app.routers.flood import router as flood_router
from app.routers.agriculture import router as agriculture_router
from app.routers.chat import router as chat_router
from app.routers.weather_chat import router as weather_chat_router

from app.routers.ingestion import router as ingestion_router
from app.routers.real_data import router as real_data_router
from app.routers.history import router as history_router
from app.routers.features import router as features_router

from app.routers.prediction import router as prediction_router
from app.routers.multi_prediction import router as multi_prediction_router
from app.routers.rainfall import router as rainfall_router

from app.routers.hydrology import router as hydrology_router
from app.routers.flood_prediction import router as flood_prediction_router

from app.routers.hazards import router as hazards_router
from app.routers.safety import router as safety_router
from app.routers.tts import router as tts_router
from app.routers.voice_ws import router as voice_ws_router
from app.routers.visual_cloud import router as visual_cloud_router



# Create database tables if database is reachable.
try:
    Base.metadata.create_all(bind=engine)
except Exception as _db_err:
    import logging
    logging.getLogger("main").warning(f"Database table initialization deferred: {_db_err}")


app = FastAPI(
    title="WeatherGPT API",
    description="Weather intelligence, prediction, hydrology and safety backend",
    version="1.0.0",
)

# Location weather ingestion.
# Development CORS.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def root():
    return {
        "status": "ok",
        "service": "WeatherGPT API",
        "version": "1.0.0",
    }


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "database": "connected",
    }


# Core application routers.
app.include_router(alerts_router)
app.include_router(dams_router)
app.include_router(earthquakes_router)
app.include_router(flood_router)
app.include_router(agriculture_router)
app.include_router(chat_router)
app.include_router(weather_chat_router)

# Data and historical weather.
app.include_router(ingestion_router)
app.include_router(real_data_router)
app.include_router(history_router)
app.include_router(features_router)

# Prediction engines.
app.include_router(prediction_router)
app.include_router(multi_prediction_router)
app.include_router(rainfall_router)

# Hydrology and flood intelligence.
app.include_router(hydrology_router)
app.include_router(flood_prediction_router)

# Hazard and safety engines.
app.include_router(hazards_router)
app.include_router(safety_router)
app.include_router(tts_router)
app.include_router(voice_ws_router)
app.include_router(visual_cloud_router)

# Conversational intelligence.
# New location search/geocoding API.


@app.get("/location/search")
async def location_search(query: str):
    from app.services.geocoding import search_location

    query = query.strip()

    if len(query) < 2:
        return {
            "status": "success",
            "query": query,
            "count": 0,
            "results": []
        }

    results = await search_location(
        query=query,
        language="en",
        count=5
    )

    india_results = [
        result
        for result in results
        if result.get("country_code") == "IN"
    ]

    if india_results:
        results = india_results

    return {
        "status": "success",
        "query": query,
        "count": len(results),
        "results": results
    }


@app.post("/location/ingest")
async def location_ingest(
    location: str = Query(
        ...,
        min_length=2,
        max_length=150
    ),
    latitude: float = Query(
        ...,
        ge=-90,
        le=90
    ),
    longitude: float = Query(
        ...,
        ge=-180,
        le=180
    ),
    db: Session = __import__("fastapi").Depends(get_db)
):
    try:
        weather = await fetch_location_weather(
            latitude=latitude,
            longitude=longitude
        )

        feature = build_feature_from_weather(
            data=weather,
            location_name=location.strip(),
            latitude=latitude,
            longitude=longitude
        )

        saved = save_location_feature(
            db=db,
            feature=feature
        )

        return {
            "status": "success",
            "location": location.strip(),
            "latitude": latitude,
            "longitude": longitude,
            "feature_id": saved.id,
            "feature_time": saved.feature_time,
            "source": saved.source
        }

    except Exception as exc:
        return {
            "status": "error",
            "message": str(exc)
        }


@app.get("/global-weather/forecast")
async def global_weather_forecast(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    forecast_days: int = Query(7, ge=1, le=16),
):
    try:
        return await get_global_forecast(
            latitude=latitude,
            longitude=longitude,
            forecast_days=forecast_days,
        )
    except Exception as exc:
        return {
            "status": "error",
            "message": f"Global forecast request failed: {exc}",
        }


@app.get("/global-weather/temperature")
async def global_weather_temperature(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
):
    try:
        return await get_global_temperature(
            latitude=latitude,
            longitude=longitude,
        )
    except Exception as exc:
        return {
            "status": "error",
            "message": f"Global temperature request failed: {exc}",
        }


@app.get("/weather/current")
async def weather_current(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
):
    try:

        data = await get_weather(
            latitude=latitude,
            longitude=longitude,
        )

        return {
            "status": "success",
            "location": data.get(
                "location",
                {
                    "latitude": latitude,
                    "longitude": longitude,
                }
            ),
            "current": data.get(
                "current"
            ),
            "forecast": data.get(
                "forecast",
                []
            ),
            "source": data.get(
                "source",
                "MET Norway"
            ),
            "updated_at": data.get(
                "updated_at"
            ),
        }

    except Exception as exc:

        return {
            "status": "error",
            "message": f"MET Norway request failed: {exc}",
        }


@app.get("/location/reverse")
async def location_reverse(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
):
    try:
        return await reverse_geocode(
            latitude=latitude,
            longitude=longitude,
        )
    except Exception as exc:
        return {
            "status": "error",
            "message": f"Reverse geocoding failed: {exc}",
        }


class DirectWeatherQuestion(BaseModel):

    question: str = Field(
        min_length=1,
        max_length=2000
    )

    latitude: float = Field(
        ge=-90,
        le=90
    )

    longitude: float = Field(
        ge=-180,
        le=180
    )

    language: str = "English"

    history: list[dict] = Field(
        default_factory=list
    )


@app.post("/speech/transcribe")
async def speech_transcribe(
    file: UploadFile = File(...)
):

    try:

        audio_bytes = await file.read()

        if not audio_bytes:
            raise HTTPException(
                status_code=400,
                detail="Audio file is empty."
            )

        result = await transcribe_audio(
            audio_bytes=audio_bytes,
            filename=file.filename or "recording.m4a",
            language_code="unknown",
        )

        return {
            "status": "success",
            **result,
        }

    except HTTPException:
        raise

    except Exception as exc:

        return {
            "status": "error",
            "message": f"Speech transcription failed: {exc}",
        }


@app.get("/share", response_class=HTMLResponse)
async def share_weather_landing(
    n: str = Query(default="Friend"),
    lat: float = Query(default=0.0),
    lon: float = Query(default=0.0),
    c: str = Query(default="Current Location"),
    t: float = Query(default=0.0),
    cond: str = Query(default="Clear"),
    h: int = Query(default=0),
    w: float = Query(default=0.0),
    ts: int = Query(default=0),
):
    safe_name = html.escape(n)
    safe_city = html.escape(c)
    safe_cond = html.escape(cond)
    intent_url = f"intent://share?n={n}&lat={lat}&lon={lon}&c={c}&t={t}&cond={cond}&h={h}&w={w}&ts={ts}#Intent;scheme=weathergpt;package=com.example.weathergpt;end"

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{safe_name}'s Weather & Location • WeatherGPT</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }}
        body {{ background: #0A1626; color: #FFFFFF; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; }}
        .card {{ background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 24px; padding: 28px; max-width: 420px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.5); backdrop-filter: blur(12px); text-align: center; }}
        .avatar {{ width: 68px; height: 68px; border-radius: 50%; background: linear-gradient(135deg, #0284C7, #38BDF8); display: flex; align-items: center; justify-content: center; font-size: 32px; margin: 0 auto 16px; border: 2px solid #38BDF8; }}
        h1 {{ font-size: 22px; font-weight: 700; margin-bottom: 4px; }}
        .city {{ color: #94A3B8; font-size: 14px; margin-bottom: 20px; }}
        .weather-box {{ background: rgba(56, 189, 248, 0.08); border: 1px solid rgba(56, 189, 248, 0.25); border-radius: 16px; padding: 18px; margin-bottom: 22px; }}
        .temp {{ font-size: 38px; font-weight: 800; color: #38BDF8; }}
        .condition {{ font-size: 15px; font-weight: 600; color: #F8FAFC; margin-top: 4px; }}
        .metrics {{ display: flex; justify-content: space-around; margin-top: 14px; padding-top: 12px; border-top: 1px solid rgba(255,255,255,0.08); }}
        .metric-item span {{ display: block; }}
        .metric-label {{ font-size: 11px; color: #94A3B8; }}
        .metric-val {{ font-size: 14px; font-weight: 600; color: #F1F5F9; }}
        .btn {{ display: block; width: 100%; padding: 14px; border-radius: 14px; font-size: 15px; font-weight: 600; text-decoration: none; cursor: pointer; border: none; transition: all 0.2s ease; margin-bottom: 10px; }}
        .btn-primary {{ background: #0284C7; color: #FFFFFF; box-shadow: 0 4px 14px rgba(2, 132, 199, 0.4); }}
        .btn-primary:hover {{ background: #0369A1; }}
        .btn-secondary {{ background: rgba(255, 255, 255, 0.08); color: #94A3B8; border: 1px solid rgba(255, 255, 255, 0.12); }}
        .footer-tip {{ font-size: 11px; color: #64748B; margin-top: 16px; line-height: 1.4; }}
    </style>
</head>
<body>
    <div class="card">
        <div class="avatar">🧑</div>
        <h1>{safe_name}'s Location</h1>
        <p class="city">📍 {safe_city} ({lat:.4f}, {lon:.4f})</p>

        <div class="weather-box">
            <div class="temp">{t:.1f}°C</div>
            <div class="condition">🌤️ {safe_cond}</div>
            <div class="metrics">
                <div class="metric-item">
                    <span class="metric-label">Humidity</span>
                    <span class="metric-val">💧 {h}%</span>
                </div>
                <div class="metric-item">
                    <span class="metric-label">Wind</span>
                    <span class="metric-val">💨 {w:.1f} m/s</span>
                </div>
            </div>
        </div>

        <a id="openAppBtn" href="{intent_url}" class="btn btn-primary">📱 Open in WeatherGPT App</a>
        <a href="https://github.com/Ismit-06/Weather_GPT" class="btn btn-secondary">⬇️ Get WeatherGPT App</a>

        <p class="footer-tip">
            Opening WeatherGPT will automatically drop a custom pointer with {safe_name}'s live weather telemetry and distance from you.
        </p>
    </div>

    <script>
        // Automatic app launch redirect on Android
        const intentUrl = "{intent_url}";
        if (/android/i.test(navigator.userAgent)) {{
            setTimeout(() => {{
                window.location.href = intentUrl;
            }}, 300);
        }}
    </script>
</body>
</html>"""
    return HTMLResponse(content=html_content)


