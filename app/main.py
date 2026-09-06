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



# Create database tables.
Base.metadata.create_all(bind=engine)


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

