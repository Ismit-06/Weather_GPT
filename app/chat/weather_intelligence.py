from typing import Any

from app.services.met_weather import get_weather
from app.services.sarvam_chat import chat as sarvam_chat
from app.services.sarvam_language import detect_language
from app.services.weather_intent import detect_weather_intent
from app.services.weather_query import parse_weather_query
from app.services.weather_time import normalize_time
from app.services.weather_forecast_interpolate import interpolate_forecast


def _safe(value: Any) -> Any:

    if value is None:
        return "unavailable"

    return value


async def build_weather_context(
    latitude: float,
    longitude: float,
) -> dict:

    weather = await get_weather(
        latitude=latitude,
        longitude=longitude,
    )

    forecast = weather.get(
        "forecast",
        [],
    )

    # MET gives a timeseries. Use its first point as the
    # current snapshot when a separate current object isn't present.
    current = (
        weather.get("current")
        or (
            forecast[0]
            if forecast
            else {}
        )
    )

    return {
        "location": {
            "latitude": latitude,
            "longitude": longitude,
        },

        "current": {
            "time": _safe(
                current.get("time")
            ),

            "temperature_c": _safe(
                current.get("temperature_c")
            ),

            "humidity_pct": _safe(
                current.get(
                    "relative_humidity_pct",
                    current.get("humidity_pct"),
                )
            ),

            "dew_point_c": _safe(
                current.get("dew_point_c")
            ),

            "pressure_hpa": _safe(
                current.get("pressure_hpa")
            ),

            "wind_speed_ms": _safe(
                current.get("wind_speed_ms")
            ),

            "wind_direction_deg": _safe(
                current.get(
                    "wind_direction_deg"
                )
            ),

            "wind_gust_ms": _safe(
                current.get("wind_gust_ms")
            ),

            "cloud_cover_pct": _safe(
                current.get("cloud_cover_pct")
            ),

            "rainfall_mm": _safe(
                current.get("precipitation_mm")
            ),

            "condition": _safe(
                current.get("symbol_code")
            ),
        },

        "next_hours": [
            {
                "time": item.get("time"),
                "temperature_c": item.get(
                    "temperature_c"
                ),
                "rainfall_mm": item.get(
                    "precipitation_mm"
                ),
                "humidity_pct": item.get(
                    "relative_humidity_pct"
                ),
                "wind_speed_ms": item.get(
                    "wind_speed_ms"
                ),
                "wind_direction_deg": item.get(
                    "wind_direction_deg"
                ),
                "condition": item.get(
                    "symbol_code"
                ),
            }
            for item in forecast[:48]
        ],

        "source": weather.get(
            "source",
            "MET Norway",
        ),

        "updated_at": weather.get(
            "updated_at"
        ),
    }


def format_weather_context(
    context: dict,
) -> str:

    current = context.get(
        "current",
        {},
    )

    target = context.get(
        "target_forecast"
    )

    lines = [
        "WEATHERGPT VERIFIED WEATHER DATA",
        "",
        f"Location latitude: "
        f"{context['location']['latitude']}",

        f"Location longitude: "
        f"{context['location']['longitude']}",

        f"Source: "
        f"{context.get('source')}",

        f"Updated at: "
        f"{context.get('updated_at')}",
    ]

    # ---------------------------------------------------------
    # Specific-time question
    #
    # IMPORTANT:
    # When target_forecast exists, ONLY the target forecast
    # is supplied to the LLM. This prevents timestamp mixing.
    # ---------------------------------------------------------

    if target:

        target_time = context.get(
            "target_local_time"
        )

        lines.extend([
            "",
            "SPECIFIC-TIME WEATHER REQUEST",
            "",
            f"Requested local time: "
            f"{target_time or 'unavailable'}",

            "",
            "AUTHORITATIVE FORECAST POINT:",
            f"Forecast UTC time: "
            f"{target.get('time')}",

            f"Temperature: "
            f"{target.get('temperature_c')} C",

            f"Rainfall: "
            f"{target.get('rainfall_mm')} mm",

            f"Humidity: "
            f"{target.get('humidity_pct')} %",

            f"Wind speed: "
            f"{target.get('wind_speed_ms')} m/s",

            f"Wind direction: "
            f"{target.get('wind_direction_deg')} degrees",

            f"Condition: "
            f"{target.get('condition')}",

            "",
            "GROUNDING RULE:",
            "For this specific-time question, use ONLY the "
            "AUTHORITATIVE FORECAST POINT above.",
            "Do not use another forecast timestamp.",
        ])

        return "\n".join(lines)

    # ---------------------------------------------------------
    # General/current question
    # ---------------------------------------------------------

    lines.extend([
        "",
        "CURRENT CONDITIONS:",
        f"Time: {current.get('time')}",
        f"Temperature: {current.get('temperature_c')} C",
        f"Humidity: {current.get('humidity_pct')} %",
        f"Dew point: {current.get('dew_point_c')} C",
        f"Pressure: {current.get('pressure_hpa')} hPa",
        f"Wind speed: {current.get('wind_speed_ms')} m/s",
        f"Wind direction: "
        f"{current.get('wind_direction_deg')} degrees",
        f"Cloud cover: "
        f"{current.get('cloud_cover_pct')} %",
        f"Rainfall: {current.get('rainfall_mm')} mm",
        f"Condition: {current.get('condition')}",
    ])

    next_hours = context.get(
        "next_hours",
        [],
    )

    if next_hours:

        lines.extend([
            "",
            "FORECAST TIMESERIES:",
        ])

        for item in next_hours:

            lines.append(
                f"{item.get('time')} | "
                f"{item.get('temperature_c')} C | "
                f"rain {item.get('rainfall_mm')} mm | "
                f"humidity {item.get('humidity_pct')} % | "
                f"wind {item.get('wind_speed_ms')} m/s | "
                f"{item.get('condition')}"
            )

    return "\n".join(lines)


async def answer_weather_question(
    latitude: float,
    longitude: float,
    question: str,
    language: str = "auto",
    history: list[dict] | None = None,
    timezone_name: str = "Asia/Kolkata",
) -> dict:

    # ---------------------------------------------------------
    # 1. Get live weather.
    # ---------------------------------------------------------

    context = await build_weather_context(
        latitude=latitude,
        longitude=longitude,
    )

    # ---------------------------------------------------------
    # 2. Detect language.
    # ---------------------------------------------------------

    if (
        not language
        or language.lower() == "auto"
    ):

        detection = await detect_language(
            question
        )

        language_name = detection.get(
            "language",
            "English",
        )

        language_code = detection.get(
            "language_code",
            "en-IN",
        )

        script_code = detection.get(
            "script_code"
        )

    else:

        language_name = language
        language_code = None
        script_code = None

    # ---------------------------------------------------------
    # 3. Detect intent.
    # ---------------------------------------------------------

    intent = detect_weather_intent(
        question
    )

    # ---------------------------------------------------------
    # 4. Extract activity/time/day.
    # ---------------------------------------------------------

    query = parse_weather_query(
        question=question,
        intent=intent,
    )

    # ---------------------------------------------------------
    # 5. Semantic fallback for ambiguous questions.
    # ---------------------------------------------------------

    if intent == "CURRENT_WEATHER":

        try:

            from app.services.semantic_intent import (
                classify_intent,
            )

            semantic = await classify_intent(
                question
            )

            if semantic:
                intent = semantic

                query = parse_weather_query(
                    question=question,
                    intent=intent,
                )

        except Exception:
            pass

    # ---------------------------------------------------------
    # 6. Normalize requested time when relevant.
    # ---------------------------------------------------------

    target_time = None

    has_specific_time_context = (
        query.day_text is not None
        or query.time_text is not None
    )

    if has_specific_time_context:

        try:

            target_time = normalize_time(
                time_text=query.time_text,
                day_text=query.day_text,
                timezone_name=timezone_name,
            )

        except Exception:
            target_time = None

    # ---------------------------------------------------------
    # 7. Select the closest real MET forecast point.
    # ---------------------------------------------------------

    target_forecast = None

    if target_time is not None:

        target_forecast = interpolate_forecast(
            forecast=context.get(
                "next_hours",
                [],
            ),
            target_local=target_time,
        )

        if target_forecast is not None:

            context["target_forecast"] = (
                target_forecast
            )

            context["target_local_time"] = (
                target_time.isoformat()
            )

    # ---------------------------------------------------------
    # 8. Build focused agent metadata.
    # ---------------------------------------------------------

    agent_query = {
        "intent": intent,
        "activity": query.activity,
        "time_text": query.time_text,
        "day_text": query.day_text,
        "target_local_time": (
            target_time.isoformat()
            if target_time
            else None
        ),
        "timezone": timezone_name,
        "target_forecast": target_forecast,
    }

    context["agent_query"] = agent_query

    weather_text = format_weather_context(
        context
    )

    # ---------------------------------------------------------
    # 9. Ask Sarvam to explain grounded data.
    # ---------------------------------------------------------

    answer = await sarvam_chat(
        question=question,
        language=language_name,
        weather_context=weather_text,
        history=history,
    )

    return {
        "status": "success",

        "question": question,

        "language": language_name,

        "language_code": language_code,

        "script_code": script_code,

        "location": {
            "latitude": latitude,
            "longitude": longitude,
        },

        "intent": intent,

        "agent_query": agent_query,

        "answer": answer,

        "weather": context,

        "engine":
            "MET-Norway + Weather-Agent + Sarvam",

        "source":
            context.get(
                "source",
                "MET Norway",
            ),

        "updated_at":
            context.get(
                "updated_at"
            ),
    }
