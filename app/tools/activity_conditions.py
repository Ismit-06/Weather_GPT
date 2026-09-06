def calculate_heat_index(temperature_c: float, humidity_pct: float) -> float:
    """Computes approximate Heat Index / Apparent Temperature in Celsius."""
    if temperature_c < 20 or humidity_pct < 40:
        return temperature_c
    # Rothfusz regression equation converted to Celsius
    t = temperature_c * 9.0 / 5.0 + 32.0
    r = humidity_pct
    hi = (-42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r
          - 0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r
          + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r)
    return round((hi - 32.0) * 5.0 / 9.0, 1)


def assess_activity_conditions(
    activity: str,
    forecast: dict,
) -> dict:

    activity = (
        activity or "outdoor"
    ).lower().strip()

    temperature = forecast.get("temperature_c")
    humidity = forecast.get("humidity_pct")
    rainfall = forecast.get("rainfall_mm")
    wind = forecast.get("wind_speed_ms")
    condition = (forecast.get("condition") or "").lower()
    uv = forecast.get("uv_index")
    aqi = forecast.get("aqi")

    score = 100.0
    reasons = []

    # Calculate heat index if temp and humidity present
    heat_index = None
    if isinstance(temperature, (int, float)) and isinstance(humidity, (int, float)):
        heat_index = calculate_heat_index(temperature, humidity)

    # 1. Rain Evaluation
    if isinstance(rainfall, (int, float)):
        if rainfall >= 5:
            score -= 60
            reasons.append("Heavy rainfall is expected.")
        elif rainfall >= 2:
            score -= 40
            reasons.append("Moderate rainfall is expected.")
        elif rainfall >= 0.5:
            score -= 25
            reasons.append("Light rain / drizzle is expected.")
        elif rainfall > 0:
            score -= 10
            reasons.append("Slight chance of rain.")
        else:
            reasons.append("Dry conditions with no rain expected.")

    # 2. Thunderstorm & Severe Weather
    if "thunder" in condition:
        score -= 65
        reasons.append("Thunderstorm conditions make outdoor activity unsafe.")
    elif "heavyrain" in condition or "storm" in condition:
        score -= 45
        reasons.append("Severe rain/storm conditions expected.")

    # 3. Activity specific rules
    # Sports & High Exertion (Running, Cycling, Football, Cricket, Walking, Gym)
    if activity in {"running", "jogging", "cycling", "football", "cricket", "walking", "gym", "exercise", "workout"}:
        if isinstance(temperature, (int, float)):
            if temperature >= 38:
                score -= 45
                reasons.append("Dangerous heat conditions for strenuous physical exertion.")
            elif temperature >= 34:
                score -= 25
                reasons.append("Very hot conditions; high risk of dehydration.")
            elif temperature >= 30:
                score -= 12
                reasons.append("Warm and humid; stay well hydrated.")
            elif 16 <= temperature <= 25:
                score += 5
                reasons.append("Pleasant and ideal temperature for workouts.")
            elif temperature < 8:
                score -= 20
                reasons.append("Cold temperatures; warm athletic layers recommended.")

        if isinstance(humidity, (int, float)):
            if humidity >= 88:
                score -= 20
                reasons.append("High humidity will make perspiration evaporate slowly.")
            elif humidity <= 65:
                score += 3

        if isinstance(wind, (int, float)):
            if activity == "cycling" and wind >= 10:
                score -= 25
                reasons.append("Strong headwinds may make cycling difficult.")
            elif activity in {"cricket", "football"} and wind >= 12:
                score -= 20
                reasons.append("Gusty winds may affect ball trajectory.")
            elif wind >= 15:
                score -= 20
                reasons.append("Strong wind gusts present.")

    # Photography
    elif activity in {"photography", "photoshoot", "photo"}:
        if "fog" in condition or "mist" in condition:
            score -= 10
            reasons.append("Foggy/misty atmospheric lighting.")
        elif "cloud" in condition:
            score += 5
            reasons.append("Diffused soft cloud lighting, great for outdoor portraits.")
        elif isinstance(rainfall, (int, float)) and rainfall > 0:
            score -= 40
            reasons.append("Rain will endanger camera equipment outdoors.")
        else:
            score += 5
            reasons.append("Good natural light and dry conditions for photography.")

    # Beach & Water activities
    elif activity in {"beach", "swimming", "swim"}:
        if "thunder" in condition:
            score = 0.0
            reasons.append("Never go to the beach or water during lightning or thunderstorms.")
        elif isinstance(rainfall, (int, float)) and rainfall >= 2:
            score -= 40
            reasons.append("Rainy beach conditions.")
        elif isinstance(wind, (int, float)) and wind >= 12:
            score -= 30
            reasons.append("Rough surf and strong beach winds.")
        elif isinstance(temperature, (int, float)) and 24 <= temperature <= 33:
            score += 5
            reasons.append("Warm and sunny beach weather.")

    # Hiking & Trekking
    elif activity in {"hiking", "hike", "trekking", "trek"}:
        if isinstance(rainfall, (int, float)) and rainfall > 0.5:
            score -= 45
            reasons.append("Trails will be muddy and slippery due to rain.")
        if "thunder" in condition:
            score = 0.0
            reasons.append("High lightning risk on elevated mountain trails.")
        if isinstance(temperature, (int, float)) and temperature >= 35:
            score -= 30
            reasons.append("High heat exhaustion risk on hiking trails.")

    # Driving & Travel
    elif activity in {"driving", "drive", "travel", "road_trip", "commute", "leaving_college", "leaving_office"}:
        if isinstance(rainfall, (int, float)) and rainfall >= 3:
            score -= 35
            reasons.append("Heavy rain reduces road visibility and creates waterlogging risks.")
        elif isinstance(rainfall, (int, float)) and rainfall > 0:
            score -= 15
            reasons.append("Wet road surfaces; drive with caution.")
        elif "fog" in condition:
            score -= 25
            reasons.append("Dense fog reduces driving visibility.")
        else:
            score += 5
            reasons.append("Clear roads and good driving visibility.")

    # Vehicle washing & Laundry & Umbrella
    elif activity in {"washing_bike", "washing_car", "bike_wash", "car_wash", "wash_bike", "wash_car"}:
        if rainfall and rainfall > 0:
            score = 10.0
            reasons.append("Rain is predicted soon, which will make your vehicle dirty again. Hold off on washing.")
        else:
            score = 90.0
            reasons.append("Dry weather ahead. It is a good time to wash your vehicle.")

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

    elif activity in {"umbrella", "carry_umbrella", "raincoat"}:
        if rainfall and rainfall > 0:
            score = 10.0
            reasons.append("Rain is likely. Definitely carry an umbrella or raincoat.")
        else:
            score = 95.0
            reasons.append("No rain detected in the forecast. An umbrella is unlikely to be needed.")

    # UV Index adjustment
    if isinstance(uv, (int, float)) and uv >= 8:
        if activity in {"running", "cycling", "beach", "hiking", "walking", "cricket", "football"}:
            score -= 10
            reasons.append(f"Very High UV Index ({uv}); sunscreen and sun protection recommended.")

    # AQI adjustment
    if isinstance(aqi, (int, float)) and aqi > 200:
        if activity in {"running", "cycling", "walking", "cricket", "football", "hiking"}:
            score -= 25
            reasons.append(f"Poor Air Quality Index ({aqi}); outdoor strenuous exertion not recommended.")

    score = max(0.0, min(100.0, score))

    if score >= 85:
        level = "EXCELLENT"
        decision = f"🟢 Good time for {activity}"
    elif score >= 65:
        level = "GOOD"
        decision = f"🟢 Favorable conditions for {activity}"
    elif score >= 45:
        level = "MODERATE"
        decision = f"🟡 Moderate conditions for {activity}"
    else:
        level = "POOR"
        decision = f"🔴 Not recommended for {activity}"

    return {
        "status": "success",
        "activity": activity,
        "score": round(score, 1),
        "level": level,
        "decision": decision,
        "heat_index_c": heat_index,
        "reasons": reasons,
        "weather": forecast,
    }


def calculate_personal_comfort(forecast: dict) -> dict:
    """
    Computes a Personal Comfort Score from 0 to 100 based on:
    - Temperature & Perceived Heat Index
    - Relative Humidity
    - Wind Speed
    - Rain / Precipitation
    - UV Index
    - Air Quality Index (AQI)
    """
    temperature = forecast.get("temperature_c", forecast.get("temperature"))
    humidity = forecast.get("humidity_pct", forecast.get("relative_humidity_pct"))
    wind_speed = forecast.get("wind_speed_ms", forecast.get("wind_speed"))
    rainfall = forecast.get("rainfall_mm", forecast.get("precipitation_mm", 0.0))
    condition = (forecast.get("condition") or forecast.get("symbol_code") or "").lower()
    uv = forecast.get("uv_index", 5.0)
    aqi = forecast.get("aqi", 50.0)

    score = 100.0
    factors = []

    # 1. Temperature & Heat Index Evaluation (Optimal: 21°C - 26°C)
    heat_index = None
    if isinstance(temperature, (int, float)):
        temp_val = float(temperature)
        if isinstance(humidity, (int, float)):
            heat_index = calculate_heat_index(temp_val, float(humidity))
        else:
            heat_index = temp_val

        effective_temp = heat_index if heat_index is not None else temp_val

        if effective_temp > 40:
            score -= 40
            factors.append(f"Extreme thermal heat stress ({effective_temp:.1f}°C feels like).")
        elif effective_temp > 35:
            score -= 28
            factors.append(f"Oppressive heat ({effective_temp:.1f}°C feels like).")
        elif effective_temp > 30:
            score -= 16
            factors.append(f"Warm apparent temperature ({effective_temp:.1f}°C feels like).")
        elif effective_temp < 10:
            score -= 25
            factors.append(f"Chilly temperatures ({effective_temp:.1f}°C).")
        elif effective_temp < 16:
            score -= 12
            factors.append(f"Cool temperatures ({effective_temp:.1f}°C).")
        else:
            factors.append(f"Pleasant thermal range ({effective_temp:.1f}°C).")

    # 2. Humidity Evaluation (Optimal: 40% - 60%)
    if isinstance(humidity, (int, float)):
        hum_val = float(humidity)
        if hum_val > 80:
            score -= 18
            factors.append(f"Very high humidity ({hum_val:.0f}%) creates sticky feeling.")
        elif hum_val > 65:
            score -= 8
            factors.append(f"Elevated humidity ({hum_val:.0f}%).")
        elif hum_val < 30:
            score -= 10
            factors.append(f"Dry air ({hum_val:.0f}%) may cause dehydration.")

    # 3. Wind Evaluation (Optimal: 2.0 - 5.0 m/s)
    if isinstance(wind_speed, (int, float)):
        wind_val = float(wind_speed)
        if wind_val > 12.0:
            score -= 20
            factors.append(f"Strong gusty winds ({wind_val:.1f} m/s).")
        elif wind_val > 8.0:
            score -= 8
            factors.append(f"Breezy conditions ({wind_val:.1f} m/s).")
        elif wind_val < 1.0 and isinstance(temperature, (int, float)) and temperature > 28:
            score -= 6
            factors.append("Stagnant air increases perceived heat.")

    # 4. Rain & Storms
    if "thunder" in condition:
        score -= 40
        factors.append("Thunderstorms present.")
    elif isinstance(rainfall, (int, float)) and rainfall > 0:
        if rainfall >= 4.0:
            score -= 35
            factors.append("Heavy rain causing severe outdoor discomfort.")
        elif rainfall >= 1.0:
            score -= 20
            factors.append("Moderate rain active.")
        else:
            score -= 10
            factors.append("Light drizzle / showers.")

    # 5. UV Index
    if isinstance(uv, (int, float)) and uv >= 8.0:
        score -= 10
        factors.append(f"Very high UV Index ({uv:.0f}).")
    elif isinstance(uv, (int, float)) and uv >= 6.0:
        score -= 5

    # 6. AQI
    if isinstance(aqi, (int, float)):
        if aqi > 200:
            score -= 25
            factors.append(f"Poor Air Quality ({aqi:.0f} AQI).")
        elif aqi > 100:
            score -= 10
            factors.append(f"Moderate Air Quality ({aqi:.0f} AQI).")

    score = max(5.0, min(100.0, score))
    final_score = int(round(score))

    if final_score >= 85:
        level_label = "Highly comfortable"
        level_tag = "HIGHLY_COMFORTABLE"
        emoji = "🟢"
        clothing = "Light, breathable cotton clothing recommended."
        outdoor_advice = "Excellent time for outdoor walks and sports."
    elif final_score >= 70:
        level_label = "Moderately comfortable"
        level_tag = "MODERATELY_COMFORTABLE"
        emoji = "🟡"
        clothing = "Light clothing recommended."
        outdoor_advice = "Avoid prolonged outdoor activity between 12–3 PM."
    elif final_score >= 50:
        level_label = "Mildly uncomfortable"
        level_tag = "MILDLY_UNCOMFORTABLE"
        emoji = "🟡"
        clothing = "Breathable loose fabrics recommended; carry water."
        outdoor_advice = "Limit strenuous outdoor exertion and stay in shade."
    else:
        level_label = "Uncomfortable"
        level_tag = "UNCOMFORTABLE"
        emoji = "🔴"
        clothing = "Protective indoor wear or rain gear depending on conditions."
        outdoor_advice = "Stay indoors if possible; stay hydrated."

    return {
        "status": "success",
        "score": final_score,
        "level_label": level_label,
        "level_tag": level_tag,
        "emoji": emoji,
        "clothing_recommendation": clothing,
        "outdoor_advice": outdoor_advice,
        "breakdown": {
            "temperature_c": temperature,
            "heat_index_c": heat_index,
            "humidity_pct": humidity,
            "wind_speed_ms": wind_speed,
            "uv_index": uv,
            "rainfall_mm": rainfall,
            "aqi": aqi,
        },
        "factors": factors,
    }
