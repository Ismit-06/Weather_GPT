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
    """
    Consolidates raw hourly alerts by hazard type.
    Instead of returning one alert card per hour (e.g. 10 MODERATE_RAIN cards),
    aggregates repeated alerts of the same hazard type into a single concise alert
    with peak intensity and active time range.
    """
    if not alerts:
        return []

    # Map raw type into human-friendly hazard labels
    type_labels = {
        "MODERATE_RAIN": "Moderate Rain",
        "HEAVY_RAIN": "Heavy Rain",
        "RAIN": "Rain Expected",
        "PRECIPITATION": "Precipitation",
        "HIGH_WIND": "High Winds",
        "STRONG_WIND": "Strong Winds",
        "WIND": "Wind Advisory",
        "THUNDERSTORM": "Thunderstorm",
        "EXTREME_HEAT": "Extreme Heat",
        "HEAT": "Elevated Temperature",
        "CYCLONE_WARNING": "Cyclone Warning",
        "CYCLONE_ALERT": "Cyclone Alert",
        "DEEP_DEPRESSION": "Deep Depression",
    }

    # Group by alert type
    grouped: dict[str, list[dict]] = {}
    for alert in alerts:
        t = alert.get("type") or "WEATHER_ALERT"
        grouped.setdefault(t, []).append(alert)

    result = []
    for alert_type, items in grouped.items():
        # Pick the most severe entry
        highest_item = max(items, key=lambda x: _severity_rank(x.get("severity")))
        severity = highest_item.get("severity") or "MEDIUM"
        
        # Determine peak numerical value if available
        num_values = [it.get("value") for it in items if isinstance(it.get("value"), (int, float))]
        peak_val = max(num_values) if num_values else highest_item.get("value")
        unit = highest_item.get("unit") or ""
        
        # Determine earliest and latest times
        times = [it.get("time") for it in items if it.get("time")]
        start_time = times[0] if times else None
        
        readable_title = type_labels.get(alert_type.upper(), alert_type.replace("_", " ").title())
        count = len(items)

        # Build clean message
        if "RAIN" in alert_type:
            val_str = f"up to {peak_val:.1f} mm/h" if isinstance(peak_val, (int, float)) else ""
            msg = f"{readable_title} forecasted across {count} hours ({val_str})." if count > 1 else f"{readable_title} forecasted ({val_str})."
            advisory = "Carry rain gear and drive with caution on wet roads."
        elif "WIND" in alert_type:
            val_str = f"gusts up to {peak_val:.0f} {unit}" if isinstance(peak_val, (int, float)) else ""
            msg = f"Wind speeds {val_str} expected over the coming hours."
            advisory = "Secure loose outdoor objects and stay clear of weak branches."
        elif "HEAT" in alert_type:
            val_str = f"reaching {peak_val:.1f}°C" if isinstance(peak_val, (int, float)) else ""
            msg = f"High temperatures {val_str} anticipated."
            advisory = "Keep hydrated and minimize direct sun exposure during peak hours."
        elif "THUNDER" in alert_type:
            msg = "Thunderstorm activity detected in local forecast models."
            advisory = "Remain indoors during lightning activity."
        else:
            msg = highest_item.get("message") or f"{readable_title} conditions detected."
            advisory = highest_item.get("advisory") or "Follow standard local safety guidance."

        result.append({
            "type": alert_type,
            "title": readable_title,
            "severity": severity,
            "source_type": highest_item.get("source_type", "FORECAST_SIGNAL"),
            "time": start_time,
            "value": peak_val,
            "unit": unit,
            "message": msg,
            "advisory": advisory,
            "pressure_hpa": highest_item.get("pressure_hpa"),
            "duration_hours": count,
        })

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
