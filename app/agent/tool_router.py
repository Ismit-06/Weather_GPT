from datetime import datetime

from app.tools.activity_conditions import (
    assess_activity_conditions,
)

from app.tools.agriculture import (
    assess_agriculture,
)

from app.tools.flood_risk import (
    get_flood_risk,
)

from app.tools.hazard_intelligence import (
    get_hazard_intelligence,
)

from app.tools.best_time import (
    get_best_activity_time,
)

from app.tools.current_weather import (
    get_current_weather,
)

from app.tools.forecast_at_time import (
    get_forecast_at_time,
)

from app.tools.hourly_forecast import (
    get_hourly_forecast,
)

from app.tools.rain import (
    get_rain_window,
)

from app.tools.travel_conditions import (
    assess_travel_conditions,
)

from app.tools.weather_alerts import (
    get_weather_alerts,
)


async def run_weather_tool(
    *,
    intent: str,
    latitude: float,
    longitude: float,
    target_local_time: datetime | None = None,
    activity: str | None = None,
    best_time_request: bool = False,
    date_text: str = "tomorrow",
    hours: int = 24,
    timezone_name: str = "Asia/Kolkata",
) -> dict:

    intent = (
        intent or "CURRENT_WEATHER"
    ).upper()

    # ---------------------------------------------------------
    # CURRENT WEATHER
    # ---------------------------------------------------------

    if intent == "CURRENT_WEATHER":

        return await get_current_weather(
            latitude=latitude,
            longitude=longitude,
        )

    # ---------------------------------------------------------
    # RAIN
    # ---------------------------------------------------------

    if intent == "RAIN":

        return await get_rain_window(
            latitude=latitude,
            longitude=longitude,
            hours=hours,
            timezone_name=timezone_name,
        )

    # ---------------------------------------------------------
    # HAZARD INTELLIGENCE
    # ---------------------------------------------------------

    if intent in {
        "ALERTS",
        "HAZARDS",
    }:

        return await get_hazard_intelligence(
            latitude=latitude,
            longitude=longitude,
            hours=max(hours, 48),
        )

    # ---------------------------------------------------------
    # FLOOD INTELLIGENCE
    # ---------------------------------------------------------

    if intent == "FLOOD":

        return await get_flood_risk(
            latitude=latitude,
            longitude=longitude,
        )

    # ---------------------------------------------------------
    # AGRICULTURE
    # ---------------------------------------------------------

    if intent == "AGRICULTURE":

        return await assess_agriculture(
            latitude=latitude,
            longitude=longitude,
            hours=max(hours, 24),
        )

    # ---------------------------------------------------------
    # ALERTS / FLOOD SIGNALS
    # ---------------------------------------------------------

    if intent in {
        "ALERTS",
        "FLOOD",
    }:

        result = await get_weather_alerts(
            latitude=latitude,
            longitude=longitude,
            hours=max(hours, 48),
        )

        result["intent"] = intent

        return result

    # ---------------------------------------------------------
    # ACTIVITY
    # ---------------------------------------------------------

    if intent == "ACTIVITY":

        if activity is None:
            activity = "outdoor"

        if best_time_request:

            result = (
                await get_best_activity_time(
                    latitude=latitude,
                    longitude=longitude,
                    activity=activity,
                    date_text=date_text,
                    timezone_name=timezone_name,
                )
            )

            result["intent"] = intent

            return result

        if target_local_time is None:

            return {
                "status": "needs_clarification",
                "intent": intent,
                "message": (
                    "A specific time is required "
                    "for this activity assessment."
                ),
            }

        result = await get_forecast_at_time(
            latitude=latitude,
            longitude=longitude,
            target_local_time=target_local_time,
        )

        if result.get("status") != "success":
            return result

        forecast = result.get(
            "forecast"
        ) or {}

        result["activity_assessment"] = (
            assess_activity_conditions(
                activity=activity,
                forecast=forecast,
            )
        )

        result["grounding"] = {
            "type": "AUTHORITATIVE_TARGET",
            "requested_local_time":
                result.get(
                    "requested_local_time"
                ),
            "forecast_time_utc":
                forecast.get("time"),
            "instruction": (
                "Use only this target forecast "
                "for weather facts."
            ),
        }

        result["intent"] = intent

        return result

    # ---------------------------------------------------------
    # TRAVEL
    # ---------------------------------------------------------

    if intent == "TRAVEL":

        if target_local_time is None:

            return {
                "status": "needs_clarification",
                "intent": intent,
                "message": (
                    "A travel time is required "
                    "for a time-specific travel assessment."
                ),
            }

        result = await get_forecast_at_time(
            latitude=latitude,
            longitude=longitude,
            target_local_time=target_local_time,
        )

        if result.get("status") != "success":
            return result

        forecast = result.get(
            "forecast"
        ) or {}

        result["travel_assessment"] = (
            assess_travel_conditions(
                forecast
            )
        )

        result["grounding"] = {
            "type": "AUTHORITATIVE_TARGET",
            "requested_local_time":
                result.get(
                    "requested_local_time"
                ),
            "forecast_time_utc":
                forecast.get("time"),
            "instruction": (
                "Use only this target forecast "
                "for travel weather facts."
            ),
        }

        result["intent"] = intent

        return result

    # ---------------------------------------------------------
    # GENERAL FORECAST
    # ---------------------------------------------------------

    if intent == "FORECAST":

        return await get_hourly_forecast(
            latitude=latitude,
            longitude=longitude,
            hours=max(
                hours,
                24,
            ),
        )

    # ---------------------------------------------------------
    # TEMPERATURE / WIND / HUMIDITY
    # ---------------------------------------------------------

    if intent in {
        "TEMPERATURE",
        "WIND",
        "HUMIDITY",
    }:

        if target_local_time is not None:

            result = await get_forecast_at_time(
                latitude=latitude,
                longitude=longitude,
                target_local_time=target_local_time,
            )

            result["intent"] = intent

            return result

        return await get_current_weather(
            latitude=latitude,
            longitude=longitude,
        )

    # ---------------------------------------------------------
    # FALLBACK
    # ---------------------------------------------------------

    return await get_current_weather(
        latitude=latitude,
        longitude=longitude,
    )
