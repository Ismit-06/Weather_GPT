from datetime import datetime

from app.services.met_weather import get_weather


def _to_float(
    value,
    default: float = 0.0,
) -> float:

    try:
        return float(value)

    except (
        TypeError,
        ValueError,
    ):
        return default


def classify_alert(
    item: dict,
) -> list[dict]:

    alerts = []

    rainfall = _to_float(
        item.get(
            "precipitation_mm"
        )
    )

    wind_ms = _to_float(
        item.get(
            "wind_speed_ms"
        )
    )

    temperature = item.get(
        "temperature_c"
    )

    temperature_c = (
        _to_float(
            temperature
        )
        if temperature is not None
        else None
    )

    condition = (
        item.get(
            "symbol_code"
        )
        or ""
    ).lower()

    time = item.get(
        "time"
    )

    pressure_hpa = _to_float(item.get("pressure_hpa"), 1013.25)

    # ---------------------------------------------------------
    # Cyclone / Deep Depression (IMD Bay of Bengal Criteria)
    # ---------------------------------------------------------
    wind_kmh = wind_ms * 3.6
    if wind_kmh >= 89 or (pressure_hpa < 985 and wind_kmh >= 75):
        alerts.append({
            "type": "CYCLONE_WARNING",
            "severity": "CRITICAL",
            "source_type": "IMD_HAZARD_ENGINE",
            "time": time,
            "value": round(wind_kmh, 1),
            "unit": "km/h",
            "pressure_hpa": pressure_hpa,
            "message": "SEVERE CYCLONIC STORM WARNING: High wind gusts & storm surge threat detected.",
            "advisory": "IMMEDIATE EVACUATION / STAY INDOORS: Move to designated cyclone shelters away from coastal zones."
        })
    elif wind_kmh >= 62 or (pressure_hpa < 995 and wind_kmh >= 50):
        alerts.append({
            "type": "CYCLONE_ALERT",
            "severity": "HIGH",
            "source_type": "IMD_HAZARD_ENGINE",
            "time": time,
            "value": round(wind_kmh, 1),
            "unit": "km/h",
            "pressure_hpa": pressure_hpa,
            "message": "CYCLONIC STORM SIGNAL DETECTED: Deep depression intensifying over coastal waters.",
            "advisory": "COASTAL ALERT: Fishermen advised not to venture into sea. Secure loose property and stay tuned."
        })
    elif wind_kmh >= 45 or (pressure_hpa < 1002 and wind_kmh >= 35):
        alerts.append({
            "type": "DEEP_DEPRESSION",
            "severity": "MEDIUM",
            "source_type": "IMD_HAZARD_ENGINE",
            "time": time,
            "value": round(wind_kmh, 1),
            "unit": "km/h",
            "pressure_hpa": pressure_hpa,
            "message": "DEEP DEPRESSION WATCH: Atmospheric pressure drop and gusty winds observed.",
            "advisory": "PRECAUTIONARY WATCH: Heavy localized squalls expected in coastal & adjacent districts."
        })

    # ---------------------------------------------------------
    # Rain
    # ---------------------------------------------------------

    if rainfall >= 5:

        alerts.append({
            "type":
                "HEAVY_RAIN",

            "severity":
                "HIGH",

            "source_type":
                "FORECAST_SIGNAL",

            "time":
                time,

            "value":
                rainfall,

            "unit":
                "mm",

            "message":
                "Heavy rainfall signal detected.",
        })

    elif rainfall >= 2:

        alerts.append({
            "type":
                "MODERATE_RAIN",

            "severity":
                "MEDIUM",

            "source_type":
                "FORECAST_SIGNAL",

            "time":
                time,

            "value":
                rainfall,

            "unit":
                "mm",

            "message":
                "Moderate rainfall signal detected.",
        })

    # ---------------------------------------------------------
    # Strong wind
    # ---------------------------------------------------------

    if wind_ms >= 15:

        alerts.append({
            "type":
                "STRONG_WIND",

            "severity":
                "HIGH",

            "source_type":
                "FORECAST_SIGNAL",

            "time":
                time,

            "value":
                wind_ms,

            "unit":
                "m/s",

            "message":
                "Strong wind signal detected.",
        })

    elif wind_ms >= 10:

        alerts.append({
            "type":
                "HIGH_WIND",

            "severity":
                "MEDIUM",

            "source_type":
                "FORECAST_SIGNAL",

            "time":
                time,

            "value":
                wind_ms,

            "unit":
                "m/s",

            "message":
                "Elevated wind signal detected.",
        })

    # ---------------------------------------------------------
    # Thunderstorm
    # ---------------------------------------------------------

    if "thunder" in condition:

        alerts.append({
            "type":
                "THUNDERSTORM",

            "severity":
                "HIGH",

            "source_type":
                "FORECAST_SIGNAL",

            "time":
                time,

            "value":
                condition,

            "message":
                "Thunderstorm signal detected.",
        })

    # ---------------------------------------------------------
    # Extreme heat
    # ---------------------------------------------------------

    if (
        temperature_c is not None
        and temperature_c >= 40
    ):

        alerts.append({
            "type":
                "EXTREME_HEAT",

            "severity":
                "HIGH",

            "source_type":
                "FORECAST_SIGNAL",

            "time":
                time,

            "value":
                temperature_c,

            "unit":
                "°C",

            "message":
                "Extreme heat signal detected.",
        })

    return alerts


def _deduplicate_alerts(
    alerts: list[dict],
) -> list[dict]:

    seen = set()
    result = []

    for alert in alerts:

        key = (
            alert.get("type"),
            alert.get("time"),
        )

        if key in seen:
            continue

        seen.add(key)
        result.append(alert)

    return result


def _severity_rank(
    severity: str | None,
) -> int:

    return {
        "CRITICAL": 4,
        "HIGH": 3,
        "MEDIUM": 2,
        "LOW": 1,
    }.get(
        (severity or "").upper(),
        0,
    )


async def get_weather_alerts(
    latitude: float,
    longitude: float,
    hours: int = 48,
    timezone_name: str = "Asia/Kolkata",
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
        weather.get("forecast")
        or []
    )[:hours]

    alerts = []

    for item in forecast:

        alerts.extend(
            classify_alert(item)
        )

    alerts = _deduplicate_alerts(
        alerts
    )

    alerts.sort(
        key=lambda item: (
            _severity_rank(
                item.get("severity")
            ),
            item.get("time")
            or "",
        ),
        reverse=True,
    )

    # ---------------------------------------------------------
    # Severity summary
    # ---------------------------------------------------------

    high_count = sum(
        1
        for alert in alerts
        if (
            alert.get("severity")
            or ""
        ).upper() == "HIGH"
    )

    medium_count = sum(
        1
        for alert in alerts
        if (
            alert.get("severity")
            or ""
        ).upper() == "MEDIUM"
    )

    low_count = sum(
        1
        for alert in alerts
        if (
            alert.get("severity")
            or ""
        ).upper() == "LOW"
    )

    if high_count > 0:

        highest_severity = "HIGH"

    elif medium_count > 0:

        highest_severity = "MEDIUM"

    elif low_count > 0:

        highest_severity = "LOW"

    else:

        highest_severity = None

    # ---------------------------------------------------------
    # Time window
    # ---------------------------------------------------------

    alert_times = []

    for alert in alerts:

        parsed = None

        value = alert.get(
            "time"
        )

        if value:

            try:
                parsed = datetime.fromisoformat(
                    value.replace(
                        "Z",
                        "+00:00",
                    )
                )

            except ValueError:
                parsed = None

        if parsed is not None:
            alert_times.append(
                parsed
            )

    alert_time_start = None
    alert_time_end = None

    if alert_times:

        alert_time_start = (
            min(alert_times).isoformat()
        )

        alert_time_end = (
            max(alert_times).isoformat()
        )

    return {
        "status":
            "success",

        "alerts_present":
            bool(alerts),

        "highest_severity":
            highest_severity,

        "summary": {
            "total":
                len(alerts),

            "high":
                high_count,

            "medium":
                medium_count,

            "low":
                low_count,
        },

        "time_window": {
            "start":
                alert_time_start,

            "end":
                alert_time_end,
        },

        "alerts":
            alerts,

        # This endpoint currently uses forecast data,
        # not an official emergency warning feed.
        "official_warning":
            False,

        "source_type":
            "FORECAST_SIGNAL",

        "warning_note": (
            "These are forecast-derived hazard "
            "signals, not official emergency warnings."
        ),

        "location": {
            "latitude":
                latitude,

            "longitude":
                longitude,

            "timezone":
                timezone_name,
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
    }
