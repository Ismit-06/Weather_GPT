def assess_travel_conditions(
    forecast: dict,
) -> dict:

    temperature = forecast.get(
        "temperature_c"
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
    risks = []
    reasons = []

    # Rain.
    if isinstance(
        rainfall,
        (int, float),
    ):

        if rainfall >= 5:
            score -= 45
            risks.append("HEAVY_RAIN")
            reasons.append(
                "Heavy rainfall may reduce travel safety."
            )

        elif rainfall >= 2:
            score -= 25
            risks.append("RAIN")
            reasons.append(
                "Moderate rainfall is expected."
            )

        elif rainfall > 0:
            score -= 10
            reasons.append(
                "Some rainfall is expected."
            )

        else:
            reasons.append(
                "No rainfall is expected."
            )

    # Wind.
    if isinstance(
        wind,
        (int, float),
    ):

        if wind >= 15:
            score -= 35
            risks.append("STRONG_WIND")
            reasons.append(
                "Strong winds may affect travel."
            )

        elif wind >= 10:
            score -= 15
            risks.append("WIND")
            reasons.append(
                "Moderately strong winds are expected."
            )

    # Severe conditions.
    severe_terms = [
        "thunder",
        "heavyrain",
        "storm",
    ]

    if any(
        term in condition
        for term in severe_terms
    ):

        score -= 35

        if "THUNDERSTORM" not in risks:
            risks.append(
                "SEVERE_WEATHER"
            )

        reasons.append(
            "Severe weather conditions are possible."
        )

    # Extreme heat.
    if isinstance(
        temperature,
        (int, float),
    ):

        if temperature >= 40:

            score -= 20
            risks.append("EXTREME_HEAT")

            reasons.append(
                "Extreme heat may make travel uncomfortable."
            )

    score = max(
        0.0,
        min(100.0, score),
    )

    if score >= 85:
        level = "GOOD"

    elif score >= 70:
        level = "ACCEPTABLE"

    elif score >= 50:
        level = "CAUTION"

    else:
        level = "UNFAVORABLE"

    return {
        "status": "success",
        "activity": "travel",
        "score": round(score, 1),
        "level": level,
        "risks": risks,
        "reasons": reasons,
        "weather": forecast,
    }
