def assess_activity_conditions(
    activity: str,
    forecast: dict,
) -> dict:

    activity = (
        activity or "outdoor"
    ).lower().strip()

    temperature = forecast.get(
        "temperature_c"
    )
    humidity = forecast.get(
        "humidity_pct"
    )
    rainfall = forecast.get(
        "rainfall_mm"
    )
    wind = forecast.get(
        "wind_speed_ms"
    )
    condition = (
        forecast.get("condition")
        or ""
    ).lower()

    score = 100.0
    reasons = []

    # Rain.
    if isinstance(
        rainfall,
        (int, float),
    ):

        if rainfall >= 5:
            score -= 50
            reasons.append(
                "Heavy rainfall is expected."
            )

        elif rainfall >= 2:
            score -= 35
            reasons.append(
                "Moderate rainfall is expected."
            )

        elif rainfall >= 1:
            score -= 25
            reasons.append(
                "Rainfall is expected."
            )

        elif rainfall > 0:
            score -= 12
            reasons.append(
                "Some rainfall is expected."
            )

        else:
            reasons.append(
                "No rainfall is expected."
            )

    exercise_activities = {
        "running",
        "cycling",
        "football",
        "cricket",
        "exercise",
    }

    # Temperature.
    if isinstance(
        temperature,
        (int, float),
    ):

        if activity in exercise_activities:

            if temperature >= 40:
                score -= 40
                reasons.append(
                    "Extreme heat."
                )

            elif temperature >= 36:
                score -= 28
                reasons.append(
                    "Very hot conditions."
                )

            elif temperature >= 33:
                score -= 18
                reasons.append(
                    "Hot conditions."
                )

            elif temperature >= 30:
                score -= 8
                reasons.append(
                    "Warm conditions."
                )

            elif 15 <= temperature <= 24:
                score += 3

            elif temperature < 10:
                score -= 20
                reasons.append(
                    "Cold conditions."
                )

        elif activity in {
            "walking",
            "hiking",
            "picnic",
            "outdoor",
        }:

            if temperature >= 38:
                score -= 30
                reasons.append(
                    "Very warm outdoor conditions."
                )

    # Humidity.
    if isinstance(
        humidity,
        (int, float),
    ):

        if activity in exercise_activities:

            if humidity >= 90:
                score -= 25
                reasons.append(
                    "Very high humidity may make exercise uncomfortable."
                )

            elif humidity >= 85:
                score -= 18
                reasons.append(
                    "High humidity may make exercise feel harder."
                )

            elif humidity >= 75:
                score -= 10
                reasons.append(
                    "Moderately high humidity."
                )

            elif humidity >= 65:
                score -= 5
                reasons.append(
                    "Some humidity is present."
                )

        elif humidity >= 90:

            score -= 10
            reasons.append(
                "High humidity."
            )

    # Wind.
    if isinstance(
        wind,
        (int, float),
    ):

        if activity == "cycling":

            if wind >= 12:
                score -= 30
                reasons.append(
                    "Strong wind may make cycling difficult."
                )

            elif wind >= 8:
                score -= 15
                reasons.append(
                    "Moderate wind may affect cycling."
                )

        elif activity == "running":

            if wind >= 15:
                score -= 25
                reasons.append(
                    "Strong wind may affect running comfort."
                )

            elif wind >= 10:
                score -= 12
                reasons.append(
                    "Moderate wind may affect running comfort."
                )

        elif wind >= 15:

            score -= 25
            reasons.append(
                "Strong winds are expected."
            )

    # Severe conditions.
    if "thunder" in condition:

        score -= 50
        reasons.append(
            "Thunderstorm conditions are unsuitable "
            "for outdoor activity."
        )

    elif "heavyrain" in condition:

        score -= 30
        reasons.append(
            "Heavy rain conditions are expected."
        )

    # Specific real-life decisions
    if activity in {"umbrella", "carry_umbrella", "raincoat"}:
        if rainfall and rainfall > 0:
            score = 10.0
            reasons.append("Rain is likely. Definitely carry an umbrella or raincoat.")
        else:
            score = 95.0
            reasons.append("No rain detected in the forecast. An umbrella is unlikely to be needed.")

    elif activity in {"hanging_clothes", "drying_clothes", "clothes", "laundry"}:
        if rainfall and rainfall > 0:
            score = 15.0
            reasons.append("Rain is expected. Hang clothes indoors or under a shed.")
        elif humidity and humidity > 80:
            score = 50.0
            reasons.append("High humidity will slow down drying, but no rain is expected.")
        else:
            score = 95.0
            reasons.append("Clear and breezy conditions make it ideal to hang clothes outside.")

    elif activity in {"washing_bike", "washing_car", "bike_wash", "car_wash", "wash_bike", "wash_car"}:
        if rainfall and rainfall > 0:
            score = 10.0
            reasons.append("Rain is predicted soon, which will make your vehicle dirty again. Hold off on washing.")
        else:
            score = 90.0
            reasons.append("Dry weather ahead. It is a good time to wash your vehicle.")

    elif activity in {"leaving_college", "leaving_office", "commute", "travel"}:
        if rainfall and rainfall >= 2:
            score = 30.0
            reasons.append("Moderate to heavy rain during commute hours. Travel with rain gear.")
        elif rainfall and rainfall > 0:
            score = 60.0
            reasons.append("Light rain possible during commute. Keep an umbrella handy.")
        else:
            score = 95.0
            reasons.append("Clear road conditions for travel.")

    score = max(
        0.0,
        min(100.0, score),
    )

    if score >= 90:
        level = "EXCELLENT"
    elif score >= 75:
        level = "GOOD"
    elif score >= 55:
        level = "MODERATE"
    elif score >= 35:
        level = "POOR"
    else:
        level = "UNFAVORABLE"

    return {
        "status": "success",
        "activity": activity,
        "score": round(score, 1),
        "level": level,
        "reasons": reasons,
        "weather": forecast,
    }
