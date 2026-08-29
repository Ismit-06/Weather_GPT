from app.services.met_weather import get_weather
from app.risk.hazard_engine import (
    calculate_hazard_profile,
)


def _as_float(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


async def get_hazard_intelligence(
    latitude: float,
    longitude: float,
    hours: int = 24,
) -> dict:

    hours = max(1, min(hours, 72))

    weather = await get_weather(
        latitude=latitude,
        longitude=longitude,
    )

    forecast = (
        weather.get("forecast") or []
    )[:hours]

    if not forecast:
        return {
            "status": "error",
            "message": "No forecast data available.",
        }

    rainfall_values = [
        _as_float(
            item.get("precipitation_mm")
        )
        for item in forecast
    ]

    wind_values = [
        _as_float(
            item.get("wind_speed_ms")
        )
        for item in forecast
    ]

    temperature_values = [
        _as_float(
            item.get("temperature_c")
        )
        for item in forecast
    ]

    # Approximate accumulated rainfall for the available
    # forecast horizon.
    rainfall_24h = sum(
        rainfall_values[:24]
    )

    rainfall_3h = sum(
        rainfall_values[:3]
    )

    temperature = max(
        temperature_values
    ) if temperature_values else 0.0

    wind_ms = max(
        wind_values
    ) if wind_values else 0.0

    wind_kmh = wind_ms * 3.6

    # Forecast data does not currently provide the same
    # heat-index / pressure-change feature set used by the
    # historical hazard model, so use conservative defaults.
    hazards = calculate_hazard_profile(
        temperature_c=temperature,
        heat_index_c=temperature,
        rainfall_3h=rainfall_3h,
        rainfall_24h=rainfall_24h,
        rainfall_probability=None,
        wind_speed_kmh=wind_kmh,
        pressure_change_3h=0.0,
    )

    results = [
        {
            "hazard": result.hazard,
            "score": result.score,
            "level": result.level,
            "reasons": result.reasons,
        }
        for result in hazards
    ]

    active_results = [
        result
        for result in results
        if result["score"] > 0
    ]

    highest = (
        active_results[0]
        if active_results
        else None
    )

    return {
        "status": "success",
        "forecast_hours": len(forecast),
        "highest_hazard": highest,
        "hazards": results,
        "active_hazards": active_results,
        "source_type": "FORECAST_SIGNAL",
        "official_warning": False,
        "warning_note": (
            "These are forecast-derived hazard signals, "
            "not official emergency warnings."
        ),
        "source": weather.get(
            "source",
            "MET Norway",
        ),
        "updated_at": weather.get(
            "updated_at"
        ),
    }
