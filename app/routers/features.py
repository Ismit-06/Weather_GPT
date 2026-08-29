from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.features.engineering import calculate_features
from app.models.features import WeatherFeature

router = APIRouter(
    prefix="/features",
    tags=["Feature Engineering"]
)


@router.post("/generate")
def generate_features(
    location: str = "Vijayawada",
    db: Session = Depends(get_db)
):

    try:
        created = calculate_features(
            db,
            location
        )

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Feature generation failed: {exc}"
        )

    total = (
        db.query(WeatherFeature)
        .filter(
            WeatherFeature.location_name
            == location
        )
        .count()
    )

    return {
        "status": "success",
        "location": location,
        "new_features": created,
        "total_features": total,
        "engine": "weather_engine_v1"
    }


@router.get("/latest")
def latest_features(
    location: str = "Vijayawada",
    limit: int = 24,
    db: Session = Depends(get_db)
):

    if limit < 1 or limit > 168:
        raise HTTPException(
            status_code=400,
            detail="limit must be between 1 and 168"
        )

    features = (
        db.query(WeatherFeature)
        .filter(
            WeatherFeature.location_name
            == location
        )
        .order_by(
            WeatherFeature.feature_time.desc()
        )
        .limit(limit)
        .all()
    )

    return {
        "location": location,
        "count": len(features),
        "features": [
            {
                "time": feature.feature_time,
                "temperature_c": feature.temperature_c,
                "temperature_change_1h": feature.temperature_change_1h,
                "temperature_avg_3h": feature.temperature_avg_3h,
                "temperature_avg_6h": feature.temperature_avg_6h,
                "humidity_pct": feature.humidity_pct,
                "humidity_change_3h": feature.humidity_change_3h,
                "pressure_hpa": feature.pressure_hpa,
                "pressure_change_3h": feature.pressure_change_3h,
                "wind_speed_kmh": feature.wind_speed_kmh,
                "wind_change_3h": feature.wind_change_3h,
                "rainfall_mm": feature.rainfall_mm,
                "rainfall_3h": feature.rainfall_3h,
                "rainfall_6h": feature.rainfall_6h,
                "rainfall_24h": feature.rainfall_24h,
                "rainfall_intensity_1h": feature.rainfall_intensity_1h,
                "heat_index_c": feature.heat_index_c,
                "source": feature.source
            }
            for feature in features
        ]
    }
