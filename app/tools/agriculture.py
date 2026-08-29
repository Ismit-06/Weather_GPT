from app.services.met_weather import (
    get_weather,
)

from app.risk.hazard_engine import (
    heat_risk,
    heavy_rain_risk,
    strong_wind_risk,
)

from app.safety.safety_engine import (
    agriculture_safety,
)


def _num(value) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def classify_rainfall(
    total_mm: float,
    maximum_hourly_mm: float,
) -> str:

    if (
        maximum_hourly_mm >= 20
        or total_mm >= 50
    ):
        return "HEAVY"

    if (
        maximum_hourly_mm >= 5
        or total_mm >= 20
    ):
        return "MODERATE"

    if (
        maximum_hourly_mm > 0
        or total_mm > 0
    ):
        return "LIGHT"

    return "NONE"


def irrigation_impact(
    rainfall_class: str,
) -> str:

    if rainfall_class == "HEAVY":
        return "STRONGLY_REDUCED"

    if rainfall_class == "MODERATE":
        return "REDUCED"

    if rainfall_class == "LIGHT":
        return "SLIGHTLY_REDUCED"

    return "NORMAL"


def build_rainfall_actions(
    rainfall_class: str,
) -> list[str]:

    if rainfall_class == "HEAVY":
        return [
            (
                "Avoid unnecessary irrigation while "
                "heavy rainfall is occurring."
            ),
            (
                "Monitor fields for waterlogging and "
                "poor drainage."
            ),
            (
                "Avoid unnecessary field operations "
                "during intense rainfall."
            ),
        ]

    if rainfall_class == "MODERATE":
        return [
            (
                "Consider reducing irrigation while "
                "rainfall is expected."
            ),
            (
                "Monitor soil moisture before adding "
                "additional irrigation."
            ),
        ]

    if rainfall_class == "LIGHT":
        return [
            (
                "Check soil moisture before deciding "
                "whether additional irrigation is needed."
            ),
        ]

    return [
        (
            "Irrigation demand should be evaluated "
            "using soil moisture and crop requirements."
        ),
    ]


async def get_agriculture_assessment(
    latitude: float,
    longitude: float,
    hours: int = 24,
) -> dict:

    hours = max(
        1,
        min(hours, 72),
    )

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

    rainfall = [
        _num(
            item.get(
                "precipitation_mm"
            )
        )
        for item in forecast
    ]

    temperatures = [
        _num(
            item.get(
                "temperature_c"
            )
        )
        for item in forecast
    ]

    winds_ms = [
        _num(
            item.get(
                "wind_speed_ms"
            )
        )
        for item in forecast
    ]

    total_rainfall = sum(
        rainfall
    )

    maximum_hourly_rainfall = (
        max(rainfall)
        if rainfall
        else 0.0
    )

    rainfall_3h = sum(
        rainfall[:3]
    )

    maximum_temperature = (
        max(temperatures)
        if temperatures
        else 0.0
    )

    maximum_wind_ms = (
        max(winds_ms)
        if winds_ms
        else 0.0
    )

    maximum_wind_kmh = (
        maximum_wind_ms * 3.6
    )

    # ---------------------------------------------------------
    # Existing hazard engine
    # ---------------------------------------------------------

    heat = heat_risk(
        temperature_c=
            maximum_temperature,
        heat_index_c=
            maximum_temperature,
    )

    heavy_rain = heavy_rain_risk(
        rainfall_24h=
            total_rainfall,
        rainfall_3h=
            rainfall_3h,
        rain_probability=
            None,
    )

    strong_wind = strong_wind_risk(
        wind_speed_kmh=
            maximum_wind_kmh,
    )

    safety = agriculture_safety(
        heat=
            heat.score,
        heavy_rain=
            heavy_rain.score,
        wind=
            strong_wind.score,
    )

    risk_score = round(
        safety.score,
        1,
    )

    suitability_score = round(
        100.0 - risk_score,
        1,
    )

    if suitability_score >= 80:
        suitability_level = "FAVORABLE"

    elif suitability_score >= 60:
        suitability_level = "MODERATE"

    elif suitability_score >= 40:
        suitability_level = "CAUTION"

    else:
        suitability_level = "UNFAVORABLE"

    # ---------------------------------------------------------
    # Agriculture-specific rainfall interpretation
    # ---------------------------------------------------------

    rainfall_class = classify_rainfall(
        total_mm=total_rainfall,
        maximum_hourly_mm=maximum_hourly_rainfall,
    )

    irrigation = irrigation_impact(
        rainfall_class
    )

    rainfall_actions = build_rainfall_actions(
        rainfall_class
    )

    actions = list(
        dict.fromkeys(
            rainfall_actions
            + safety.actions
        )
    )

    reasons = list(
        dict.fromkeys(
            safety.reasons
        )
    )

    if rainfall_class == "HEAVY":

        reasons.append(
            "Significant rainfall is expected."
        )

    elif rainfall_class == "MODERATE":

        reasons.append(
            "Moderate rainfall is expected."
        )

    elif rainfall_class == "LIGHT":

        reasons.append(
            "Light rainfall is expected."
        )

    return {
        "status": "success",

        "category": "AGRICULTURE",

        "location": {
            "latitude": latitude,
            "longitude": longitude,
        },

        "risk_score": risk_score,

        "risk_level": safety.level,

        "suitability_score":
            suitability_score,

        "suitability_level":
            suitability_level,

        "rainfall_assessment": {
            "total_mm":
                round(
                    total_rainfall,
                    2,
                ),

            "maximum_hourly_mm":
                round(
                    maximum_hourly_rainfall,
                    2,
                ),

            "classification":
                rainfall_class,

            "irrigation_impact":
                irrigation,
        },

        "actions": actions,

        "reasons": reasons,

        "hazards": {
            "heat": {
                "score": heat.score,
                "level": heat.level,
                "reasons": heat.reasons,
            },

            "heavy_rain": {
                "score":
                    heavy_rain.score,
                "level":
                    heavy_rain.level,
                "reasons":
                    heavy_rain.reasons,
            },

            "strong_wind": {
                "score":
                    strong_wind.score,
                "level":
                    strong_wind.level,
                "reasons":
                    strong_wind.reasons,
            },
        },

        "forecast_summary": {
            "forecast_hours":
                len(forecast),

            "total_rainfall_mm":
                round(
                    total_rainfall,
                    2,
                ),

            "maximum_hourly_rainfall_mm":
                round(
                    maximum_hourly_rainfall,
                    2,
                ),

            "maximum_temperature_c":
                round(
                    maximum_temperature,
                    1,
                ),

            "maximum_wind_speed_ms":
                round(
                    maximum_wind_ms,
                    2,
                ),
        },

        "source":
            weather.get(
                "source",
                "MET Norway",
            ),

        "updated_at":
            weather.get(
                "updated_at"
            ),

        "disclaimer": (
            "General weather-based agricultural "
            "decision support. Crop-specific decisions "
            "require crop, soil, growth-stage and "
            "local agronomic information."
        ),
    }


# -------------------------------------------------------------
# Backwards-compatible function used by WeatherAgent.
# -------------------------------------------------------------

async def assess_agriculture(
    latitude: float,
    longitude: float,
    hours: int = 24,
) -> dict:

    return await get_agriculture_assessment(
        latitude=latitude,
        longitude=longitude,
        hours=hours,
    )
