from app.schemas.visual_cloud import (
    CloudObservation,
    WeatherContext,
    CloudWeatherAssessment,
    CloudType,
    VerticalDevelopment,
    CloudThickness,
    PrecipitationAppearance,
)


def fuse_cloud_and_weather(
    observation: CloudObservation,
    weather: WeatherContext | None = None,
) -> tuple[CloudWeatherAssessment, str]:
    """
    Combines structured visual cloud observation with live meteorological data
    using conservative, rule-based reasoning and synthesizes an explainable report.
    """
    has_weather = weather is not None and (
        weather.temperature is not None or weather.humidity is not None
    )

    c_type = observation.dominant_cloud_type
    vert = observation.vertical_development
    thick = observation.cloud_thickness
    cov = observation.cloud_coverage or 0.0
    vis_precip = observation.precipitation_appearance
    is_convective = (
        observation.convective_appearance is True
        or str(observation.convective_appearance).lower() == "true"
    )

    humidity = weather.humidity if has_weather and weather.humidity is not None else -1.0
    precip_prob = weather.precipitation_probability if has_weather and weather.precipitation_probability is not None else -1.0
    cur_precip = weather.current_precipitation if has_weather and weather.current_precipitation is not None else 0.0
    temp = weather.temperature if has_weather and weather.temperature is not None else None

    # Initialize conservative signals
    precip_signal = "low"
    convection_signal = "none"
    change_signal = "stable"
    storm_signal = "none"
    overall_signal = "fair_conditions"

    next_1h = "Low probability of precipitation; conditions appear stable."
    next_3h = "No immediate significant weather change visually indicated."
    next_6h = "Expect prevailing seasonal conditions according to regional forecast."

    reasons = []

    # Rule 1: Unknown or unclassifiable cloud
    if c_type == CloudType.unknown or observation.analysis_status == "uncertain":
        overall_signal = "uncertain_visual_evidence"
        confidence = "Low" if has_weather else "Very Low"
        next_1h = "Visual cloud evidence is ambiguous or obscured. Rely on standard local weather forecasts."
        next_3h = "Short-term visual trend cannot be determined from this photograph."
        next_6h = "Refer to official regional weather forecasts."
        reasons.append("Cloud structure could not be clearly classified from the uploaded image.")

    # Rule 2: Cumulonimbus (Severe Convection)
    elif c_type == CloudType.cumulonimbus:
        convection_signal = "high"
        precip_signal = "high"
        change_signal = "significant"
        storm_signal = "moderate" if precip_prob < 50 else "high"
        overall_signal = "convective_storm_potential"

        next_1h = "Elevated risk of localized showers, gusty winds, or sudden precipitation."
        next_3h = "Continued risk of shower or thunderstorm development in the vicinity."
        next_6h = "Conditions likely to transition as convective cells dissipate or move downwind."

        reasons.append("Cumulonimbus formation visually indicates intense vertical convective motion.")
        if has_weather:
            if humidity > 65 or precip_prob > 40:
                reasons.append(f"Live atmospheric humidity ({humidity:.0f}%) and forecast rain probability ({precip_prob:.0f}%) strongly support active convection.")
            else:
                reasons.append("Atmospheric readings report lower ambient moisture; storm activity may be localized or passing nearby.")

    # Rule 3: Towering Cumulus (Developing Convection)
    elif c_type == CloudType.towering_cumulus or (c_type == CloudType.cumulus and vert in [VerticalDevelopment.strong, VerticalDevelopment.extreme]):
        convection_signal = "high" if vert in [VerticalDevelopment.strong, VerticalDevelopment.extreme] else "moderate"
        change_signal = "moderate"

        if has_weather and (humidity >= 70 or precip_prob >= 40):
            precip_signal = "elevated"
            overall_signal = "increasing_rain_risk"
            next_1h = "Moderate potential for developing showers as convective updrafts strengthen."
            next_3h = "Increasing shower or localized thunderstorm potential if vertical growth continues."
            next_6h = "Shower activity may taper off into the evening or give way to scattered cloudiness."
            reasons.append("Substantial vertical cloud development indicates growing buoyant updrafts.")
            reasons.append(f"Elevated atmospheric humidity ({humidity:.0f}%) and forecast rain probability ({precip_prob:.0f}%) provide favorable fuel for precipitation.")
        else:
            precip_signal = "moderate"
            overall_signal = "developing_convection"
            next_1h = "Isolated brief showers possible; vertical updrafts visible."
            next_3h = "Shower potential will depend on whether local updrafts can overcome ambient moisture limits."
            next_6h = "Expect partly cloudy conditions with scattered residual clouds."
            reasons.append("Visible vertical cloud growth suggests localized thermal convection.")
            if has_weather:
                reasons.append("Surrounding atmospheric moisture appears moderate, which may limit extensive shower development.")

    # Rule 4: Nimbostratus (Continuous Rain Clouds)
    elif c_type == CloudType.nimbostratus or (c_type == CloudType.stratus and thick in [CloudThickness.thick, CloudThickness.very_thick] and vis_precip in [PrecipitationAppearance.possible, PrecipitationAppearance.likely_visible]):
        precip_signal = "high"
        convection_signal = "none"
        change_signal = "stable"
        overall_signal = "steady_precipitation"

        next_1h = "High likelihood of continuous or steady light-to-moderate precipitation."
        next_3h = "Persistent overcast skies with steady rain or drizzle likely continuing."
        next_6h = "Gradual ceiling lifting may occur later if the precipitating system moves through."

        reasons.append("Dense, dark, featureless cloud deck is consistent with widespread stratiform precipitation.")
        if has_weather and cur_precip > 0:
            reasons.append("Live surface telemetry confirms active precipitation.")

    # Rule 5: Cirrus / Cirrostratus / Cirrocumulus (High-altitude Ice Clouds)
    elif c_type in [CloudType.cirrus, CloudType.cirrostratus, CloudType.cirrocumulus]:
        precip_signal = "low"
        convection_signal = "none"
        change_signal = "stable"
        overall_signal = "fair_conditions"

        next_1h = "No immediate precipitation expected from high-altitude ice crystal clouds."
        if has_weather and precip_prob >= 60:
            next_3h = "Cloud cover may thicken gradually as an incoming weather system approaches."
            next_6h = "Increasing chance of rain later according to regional forecast models."
            reasons.append("High cirrus clouds frequently precede large-scale weather systems by 12–24 hours.")
            reasons.append(f"While immediate visual precipitation is absent, forecast data indicates higher rain probability ({precip_prob:.0f}%) later.")
        else:
            next_3h = "Fair weather expected to persist with thin decorative cloud cover."
            next_6h = "Stable and predominantly dry conditions expected."
            reasons.append("High thin cirrus clouds are composed of ice crystals and do not produce ground-level rain.")

    # Rule 6: Stratocumulus / Stratus (Low Layered Clouds)
    elif c_type in [CloudType.stratocumulus, CloudType.stratus]:
        convection_signal = "none"
        change_signal = "stable"

        if vis_precip in [PrecipitationAppearance.possible, PrecipitationAppearance.likely_visible] or (has_weather and humidity > 85):
            precip_signal = "moderate"
            overall_signal = "drizzle_or_mist_potential"
            next_1h = "Overcast skies with potential for light patchy drizzle or damp mist."
            next_3h = "Continued low cloud ceilings with intermittent light drizzle."
            next_6h = "Slow cloud deck thinning or persistent gloom depending on daytime heating."
            reasons.append("Low, dense stratocumulus deck traps surface moisture, capable of generating light drizzle.")
        else:
            precip_signal = "low"
            overall_signal = "overcast_stable"
            next_1h = "Cool, overcast conditions without significant heavy precipitation."
            next_3h = "Stable cloud cover likely persisting with minimal immediate change."
            next_6h = "Possibility of cloud deck breaking up with afternoon warming."
            reasons.append("Layered stratocumulus clouds represent stable boundary layer moisture without strong vertical updrafts.")

    # Rule 7: Clear / Fair Weather Cumulus
    elif c_type in [CloudType.clear, CloudType.cumulus]:
        precip_signal = "low"
        convection_signal = "none" if c_type == CloudType.clear else "low"
        change_signal = "stable"
        overall_signal = "fair_conditions"

        next_1h = "Fair weather with bright daylight and dry conditions."
        next_3h = "Pleasant weather expected to continue with minimal cloud interference."
        next_6h = "Clear to partly cloudy conditions with comfortable outdoor weather."
        reasons.append("Absence of substantial vertical growth or dense overcast confirms stable, fair-weather conditions.")

    # Rule 8: Altocumulus / Altostratus (Mid-level Clouds)
    elif c_type in [CloudType.altocumulus, CloudType.altostratus]:
        convection_signal = "low"
        precip_signal = "low" if thick == CloudThickness.thin else "moderate"
        overall_signal = "filtered_sunshine_or_light_virga"
        next_1h = "Diffused sunlight with mid-level cloud layer; no heavy rain expected."
        next_3h = "Clouds may thicken gradually if a warm front is advancing."
        next_6h = "Evaluate regional forecast for potential rain development overnight."
        reasons.append("Mid-level altocumulus sheets represent middle-tropospheric moisture, occasionally yielding light virga.")

    # Default fallback
    else:
        overall_signal = "variable_cloudiness"
        next_1h = "Variable sky conditions observed."
        next_3h = "Conditions likely to remain consistent with prevailing forecast."
        next_6h = "Check standard forecast updates."
        reasons.append("Mixed or transitional cloud types observed.")

    # Compute overall confidence
    if not has_weather:
        reasons.append("Live weather telemetry was unavailable; assessment is derived solely from visible cloud patterns.")
        confidence = "Low" if observation.confidence >= 0.7 else "Very Low"
    else:
        # Check alignment between visual cloud type and weather data
        strong_alignment = (
            (c_type in [CloudType.cumulonimbus, CloudType.towering_cumulus, CloudType.nimbostratus] and (precip_prob >= 35 or humidity >= 65))
            or (c_type in [CloudType.clear, CloudType.cumulus, CloudType.cirrus] and (precip_prob < 35 or humidity < 65))
        )
        if observation.confidence >= 0.80 and strong_alignment:
            confidence = "High"
        elif observation.confidence >= 0.60:
            confidence = "Moderate"
        else:
            confidence = "Low"

    assessment = CloudWeatherAssessment(
        next_1_hour=next_1h,
        next_3_hours=next_3h,
        next_6_hours=next_6h,
        precipitation_signal=precip_signal,
        convection_signal=convection_signal,
        weather_change_signal=change_signal,
        overall_short_term_signal=overall_signal,
        storm_signal=storm_signal,
        confidence=confidence,
        is_weather_data_available=has_weather,
    )

    # Format human-readable explanation matching Section 22 specification
    cloud_display = c_type.value.replace("_", " ").title()
    cov_pct = int(cov * 100)
    vert_display = vert.value.replace("_", " ").title()

    explanation_lines = [
        "## Sky Analysis",
        f"**Cloud type:** {cloud_display}",
        f"**Cloud coverage:** ~{cov_pct}%",
        f"**Vertical development:** {vert_display}",
        f"**Visual confidence:** {int(observation.confidence * 100)}%",
        "",
        "## Short-Term Outlook",
        f"**Next 1 hour:** {next_1h}",
        f"**Next 1–3 hours:** {next_3h}",
        f"**Next 3–6 hours:** {next_6h}",
        "",
        "## Why?",
        " ".join(reasons),
        "",
        "## Confidence",
        f"**{confidence}**",
        "",
        "## Important",
        "This is a short-term visual weather assessment, not a guaranteed forecast."
    ]

    explanation = "\n".join(explanation_lines)
    return assessment, explanation
