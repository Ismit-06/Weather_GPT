package com.example.weathergpt.data

import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetWeatherResponse
import java.util.Calendar
import java.util.Locale

/**
 * High-Speed Instant Rule-Based Weather Intelligence Engine.
 * 
 * Provides sub-50ms instant answers for common weather queries (temperature, rain risk,
 * umbrella recommendation, outdoor safety, clothes, humidity, wind) using cached/live telemetry.
 * Serves as an ultra-fast path and full offline resilience backup.
 */
object FastWeatherAssistant {

    fun canAnswerInstantly(query: String): Boolean {
        val q = query.lowercase().trim()
        if (q.length < 2) return false
        
        val quickKeywords = listOf(
            "temp", "temperature", "rain", "raining", "umbrella", "hot", "cold",
            "wear", "jacket", "wind", "windy", "humidity", "weather", "forecast",
            "today", "outside", "walk", "jog", "run", "cycle", "cricket", "sports",
            "barish", "mausam", "garmi", "thand", "chata", "chhatri",
            "tapman", "hawa", "climate", "now", "condition", "pressure"
        )
        return quickKeywords.any { q.contains(it) }
    }

    fun generateInstantResponse(
        query: String,
        weather: MetWeatherResponse?,
        locationName: String?,
        languageCode: String?
    ): Pair<String, String>? {
        val forecast = weather?.forecast?.firstOrNull() ?: return null
        val nextHours = weather.forecast.take(6)
        
        val temp = forecast.temperature_c ?: 28.0
        val humidity = forecast.relative_humidity_pct ?: 60.0
        val wind = forecast.wind_speed_ms ?: 3.5
        val rainProb = nextHours.maxOfOrNull { it.precipitation_probability_pct ?: 0.0 } ?: 0.0
        val rainMm = nextHours.sumOf { it.precipitation_mm ?: 0.0 }
        val pressure = forecast.pressure_hpa ?: 1012.0
        val symbol = forecast.symbol_code ?: "clearsky"

        val loc = locationName?.ifBlank { "your location" } ?: "your location"
        val q = query.lowercase().trim()

        // Detect Query Intent
        val isRainQuery = listOf("rain", "raining", "umbrella", "barish", "chata", "chhatri", "precipitation").any { q.contains(it) }
        val isTempQuery = listOf("temp", "temperature", "hot", "cold", "garmi", "thand", "tapman", "degree").any { q.contains(it) }
        val isWindQuery = listOf("wind", "windy", "breeze", "storm", "hawa", "speed").any { q.contains(it) }
        val isHumidityQuery = listOf("humidity", "humid", "sweat", "moisture", "dew").any { q.contains(it) }
        val isOutdoorQuery = listOf("wear", "cloth", "jacket", "outside", "walk", "jog", "run", "sports", "cricket", "car", "drive").any { q.contains(it) }

        val displayText: String
        val speechText: String

        when {
            isRainQuery -> {
                if (rainProb > 40 || rainMm > 0.5) {
                    displayText = "🌧️ **Rain Expected in $loc**\n\n• **Precipitation Probability**: ${"%.0f".format(rainProb)}%\n• **Expected Rainfall**: ${"%.1f".format(rainMm)} mm\n• **Recommendation**: Yes, carry an umbrella! Rain or showers are likely in the next few hours."
                    speechText = "Rain is likely in $loc with a ${"%.0f".format(rainProb)}% chance. It is recommended to carry an umbrella."
                } else {
                    displayText = "☀️ **No Significant Rain Expected in $loc**\n\n• **Rain Probability**: Low (${"%.0f".format(rainProb)}%)\n• **Conditions**: Mostly dry and clear\n• **Recommendation**: No umbrella needed right now. Enjoy your day!"
                    speechText = "There is very low chance of rain in $loc today. Conditions are mostly dry."
                }
            }
            isTempQuery -> {
                val feel = if (temp > 32 && humidity > 60) temp + 3.5 else if (temp < 15 && wind > 5) temp - 2.5 else temp
                displayText = "🌡️ **Current Temperature in $loc**\n\n• **Temperature**: ${"%.1f".format(temp)}°C (Feels like ${"%.1f".format(feel)}°C)\n• **Humidity**: ${"%.0f".format(humidity)}%\n• **Conditions**: ${symbol.replace("_", " ").replaceFirstChar { it.uppercase() }}"
                speechText = "The temperature in $loc is currently ${"%.0f".format(temp)} degrees Celsius, with a feels-like temperature of ${"%.0f".format(feel)} degrees."
            }
            isWindQuery -> {
                displayText = "💨 **Wind Conditions in $loc**\n\n• **Wind Speed**: ${"%.1f".format(wind)} m/s (${"%.1f".format(wind * 3.6)} km/h)\n• **Atmospheric Pressure**: ${"%.0f".format(pressure)} hPa\n• **Status**: ${if (wind > 8) "Breezy / Strong winds" else "Gentle calm breeze"}"
                speechText = "The wind speed in $loc is ${"%.1f".format(wind * 3.6)} kilometers per hour."
            }
            isHumidityQuery -> {
                displayText = "💧 **Humidity & Moisture in $loc**\n\n• **Relative Humidity**: ${"%.0f".format(humidity)}%\n• **Dew Point**: ${"%.1f".format(forecast.dew_point_c ?: (temp - 4.0))}°C\n• **Comfort Index**: ${if (humidity > 70) "High moisture / humid" else if (humidity < 30) "Dry air" else "Comfortable"}"
                speechText = "The relative humidity in $loc is currently ${"%.0f".format(humidity)} percent."
            }
            isOutdoorQuery -> {
                val advice = when {
                    rainProb > 45 -> "Carry waterproof gear or an umbrella. Outdoor sports may be interrupted by rain."
                    temp > 35 -> "Wear light cotton clothing and stay hydrated under direct sunlight."
                    temp < 18 -> "Wear a light jacket or warm layers."
                    else -> "Great weather for outdoor activities, jogging, and walking!"
                }
                displayText = "🏃 **Outdoor & Activity Advisory for $loc**\n\n• **Current**: ${"%.1f".format(temp)}°C, Humidity ${"%.0f".format(humidity)}%\n• **Rain Risk**: ${"%.0f".format(rainProb)}%\n• **Advisory**: $advice"
                speechText = "For $loc, current temperature is ${"%.0f".format(temp)} degrees. $advice"
            }
            else -> {
                displayText = "🌤️ **Current Weather Overview in $loc**\n\n• **Temperature**: ${"%.1f".format(temp)}°C\n• **Humidity**: ${"%.0f".format(humidity)}%\n• **Wind**: ${"%.1f".format(wind)} m/s\n• **Rain Risk**: ${"%.0f".format(rainProb)}%\n• **Summary**: ${symbol.replace("_", " ").replaceFirstChar { it.uppercase() }}"
                speechText = "In $loc, the temperature is ${"%.0f".format(temp)} degrees Celsius with ${"%.0f".format(humidity)} percent humidity and low rain risk."
            }
        }

        return Pair(displayText, speechText)
    }
}
