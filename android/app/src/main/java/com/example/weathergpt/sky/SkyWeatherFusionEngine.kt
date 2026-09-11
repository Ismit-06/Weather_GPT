package com.example.weathergpt.sky

import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetWeatherResponse
import com.example.weathergpt.data.SkyAnalysisResponse
import com.example.weathergpt.data.radar.RadarMetadata
import com.example.weathergpt.data.radar.RadarRepository
import com.example.weathergpt.location.SelectedLocation
import java.util.Calendar

data class SkyWeatherFusionReport(
    val title: String,
    val visualSkyCondition: String,
    val visualSkyCoveragePct: Int,
    val cloudinessAgreement: String, // "High Agreement", "Moderate Agreement", "Local Variation Detected"
    val visualObservationSummary: String,
    val meteorologicalSummary: String,
    val radarValidationSummary: String,
    val climateContextSummary: String,
    val limitsAndDisclaimer: String,
    val displayText: String,
    val speechText: String,          // Clean text for voice output: NO emojis, NO markdown, NO asterisks, NO bullets
    val confidenceLevel: String,     // "High Confidence", "Moderate Confidence", "Low Confidence"
    val isAgreementStrong: Boolean,
    val aiConfidenceLabel: String = "AI Confidence",
    val modelVersion: String? = null,
    val dominantCloudType: String? = null,
    val verticalDevelopment: String? = null,
    val cloudThickness: String? = null,
    val shortTermOutlook1h: String? = null,
    val shortTermOutlook3h: String? = null,
    val shortTermOutlook6h: String? = null,
    val whatItSuggests: String? = null,
    val whatWeatherSays: String? = null
)

object SkyWeatherFusionEngine {

    fun fuse(
        evaluation: SkyEvaluationResult,
        weatherResponse: MetWeatherResponse?,
        radarMetadata: RadarMetadata?,
        location: SelectedLocation?,
        skyServerResponse: SkyAnalysisResponse? = null,
        visualCloudResponse: com.example.weathergpt.data.VisualCloudIntelligenceResponse? = null
    ): SkyWeatherFusionReport {
        val current = weatherResponse?.forecast?.firstOrNull()
        val nextHours = weatherResponse?.forecast?.drop(1)?.take(4) ?: emptyList()
        val seg = evaluation.segmentation

        // Authoritative condition from Visual Cloud Intelligence or Gemma 4 server model if available
        val visualCondition = visualCloudResponse?.observation?.dominant_cloud_type?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
            ?: skyServerResponse?.cloud_condition?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
            ?: seg.visualCloudCondition

        val rawCoverage = visualCloudResponse?.observation?.cloud_coverage ?: skyServerResponse?.cloud_coverage
        val visualCoveragePct = if (rawCoverage != null) (rawCoverage * 100).toInt() else (seg.skyCoverageFraction * 100).toInt()
        val apiCloudCoverPct = current?.cloud_cover_pct?.toInt() ?: -1

        // 1. Evaluate Agreement between Camera View and Weather API
        val (agreementLabel, isAgreementStrong, agreementExplanation) = evaluateCloudAgreement(
            visualCondition,
            apiCloudCoverPct
        )

        // 2. Evaluate Precipitation & Radar Agreement
        val radarSummary = evaluateRadarAgreement(
            seg.visualPrecipitationHint,
            current?.precipitation_mm ?: 0.0,
            current?.precipitation_probability_pct ?: 0.0,
            radarMetadata
        )

        // 3. Meteorological Context
        val temp = current?.temperature_c?.let { "%.1f°C".format(it) } ?: "Unavailable"
        val humidity = current?.relative_humidity_pct?.let { "%.0f%%".format(it) } ?: "Unavailable"
        val wind = current?.wind_speed_ms?.let { "%.1f m/s".format(it) } ?: "Unavailable"
        val pressure = current?.pressure_hpa?.let { "%.0f hPa".format(it) } ?: "Unavailable"

        val metSummary = buildString {
            append("Temperature: $temp | Humidity: $humidity | Wind: $wind | Pressure: $pressure")
            if (apiCloudCoverPct >= 0) {
                append(" | Forecast Cloud Cover: $apiCloudCoverPct%")
            }
        }

        // 4. Climatological Context (Historical/Seasonal - clearly distinguished from instant camera view)
        val climateSummary = getHistoricalClimateContext(location)

        // 5. Visual Observation Summary
        val visualSummary = buildString {
            append("Camera view reveals $visualCondition with approximately $visualCoveragePct% sky visibility in the frame.")
            if (seg.visualHazeHint) {
                append(" Visual appearance is consistent with optical haze or diffused atmospheric light.")
            }
            if (seg.visualPrecipitationHint) {
                append(" Darker cloud textures suggest possible localized rain development.")
            }
        }

        // 6. Limitations & Boundaries
        val limitation = "Camera visual observation alone cannot measure exact temperature, air pressure, wind velocity, or pollutant concentration. Meteorological measurements are provided by calibrated sensors and models."

        // 7. AI Confidence Category
        val confidenceScore = skyServerResponse?.sky_confidence?.toFloat() ?: evaluation.confidence
        val confidenceLevel = when {
            confidenceScore >= 0.85f -> "High Confidence"
            confidenceScore >= 0.70f -> "Moderate Confidence"
            else -> "Low Confidence"
        }
        val aiConfidencePercent = (confidenceScore * 100).toInt()
        val aiConfidenceLabel = "AI Confidence: $aiConfidencePercent% ($confidenceLevel)"

        // 8. Build Display Text (Rich presentation)
        val displayText = buildString {
            append("### $visualCondition ($aiConfidenceLabel)\n\n")
            append("**Visual Observation**: $visualSummary\n\n")
            append("**Meteorological Telemetry**: $metSummary\n\n")
            append("**Data Agreement**: $agreementExplanation\n\n")
            append("**Radar Verification**: $radarSummary\n\n")
            append("**Regional Climate Context**: $climateSummary\n\n")
            append("> **Limitation**: $limitation")
        }

        // 9. Build Voice-Safe Speech Text (NO emojis, NO markdown, NO asterisks, NO bullets)
        val speechText = buildString {
            append("The camera view shows a $visualCondition with about $visualCoveragePct percent visible sky. ")
            append(agreementExplanation.replace("*", "").replace("⚠️", "").replace("✓", "").trim())
            append(" ")
            append("Current temperature is $temp with $humidity humidity and wind speed of $wind. ")
            append(radarSummary.replace("*", "").replace("⚠️", "").replace("✓", "").trim())
            append(" ")
            append("Historical climate patterns indicate that ")
            append(climateSummary.replace("*", "").trim())
        }.replace(Regex("[*#_`~>\\[\\]()•\\-]|[\uD83C-\uDBFF\uDC00-\uDFFF]+"), " ").replace(Regex("\\s+"), " ").trim()

        val dominantCloud = visualCloudResponse?.observation?.dominant_cloud_type?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: visualCondition
        val verticalDev = visualCloudResponse?.observation?.vertical_development?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Normal"
        val thickness = visualCloudResponse?.observation?.cloud_thickness?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Moderate"

        val outlook1h = visualCloudResponse?.assessment?.next_1_hour ?: when {
            visualCondition.contains("Storm", ignoreCase = true) -> "Higher potential for localized rain showers."
            visualCondition.contains("Overcast", ignoreCase = true) -> "Overcast skies with slight drizzle potential."
            visualCondition.contains("Clear", ignoreCase = true) -> "Fair and dry conditions expected."
            else -> "Conditions expected to remain largely steady over the next hour."
        }
        val outlook3h = visualCloudResponse?.assessment?.next_3_hours ?: when {
            visualCondition.contains("Storm", ignoreCase = true) -> "Potential shower activity developing nearby."
            visualCondition.contains("Clear", ignoreCase = true) -> "Stable conditions expected to continue."
            else -> "Scattered cloudiness continuing."
        }
        val outlook6h = visualCloudResponse?.assessment?.next_6_hours ?: "Refer to official regional forecasts for extended trends."

        return SkyWeatherFusionReport(
            title = visualCondition,
            visualSkyCondition = visualCondition,
            visualSkyCoveragePct = visualCoveragePct,
            cloudinessAgreement = agreementLabel,
            visualObservationSummary = visualSummary,
            meteorologicalSummary = metSummary,
            radarValidationSummary = radarSummary,
            climateContextSummary = climateSummary,
            limitsAndDisclaimer = limitation,
            displayText = displayText,
            speechText = speechText,
            confidenceLevel = visualCloudResponse?.assessment?.confidence?.let { "$it Confidence" } ?: confidenceLevel,
            isAgreementStrong = isAgreementStrong,
            aiConfidenceLabel = aiConfidenceLabel,
            modelVersion = visualCloudResponse?.let { "Gemma 4 Visual Intelligence" } ?: skyServerResponse?.model_version,
            dominantCloudType = dominantCloud,
            verticalDevelopment = verticalDev,
            cloudThickness = thickness,
            shortTermOutlook1h = outlook1h,
            shortTermOutlook3h = outlook3h,
            shortTermOutlook6h = outlook6h,
            whatItSuggests = visualCloudResponse?.let { "Cloud pattern exhibits ${it.observation.vertical_development} vertical development and ${it.observation.cloud_thickness} thickness." },
            whatWeatherSays = agreementExplanation
        )
    }

    private fun evaluateCloudAgreement(
        visualCondition: String,
        apiCloudCoverPct: Int
    ): Triple<String, Boolean, String> {
        if (apiCloudCoverPct < 0) {
            return Triple(
                "Telemetry Pending",
                false,
                "Visual sky observation recorded. Weather station cloud telemetry is currently unavailable."
            )
        }

        val visualIsCloudy = visualCondition in listOf("Mostly Cloudy", "Overcast", "Dark Storm Cloud")
        val apiIsCloudy = apiCloudCoverPct >= 65

        val visualIsClear = visualCondition == "Clear Sky"
        val apiIsClear = apiCloudCoverPct <= 30

        return when {
            (visualIsCloudy && apiIsCloudy) || (visualIsClear && apiIsClear) -> {
                Triple(
                    "High Agreement",
                    true,
                    "Camera visual observation closely matches weather data reporting $apiCloudCoverPct% cloud cover."
                )
            }
            visualIsClear && apiIsCloudy -> {
                Triple(
                    "Local Variation Detected",
                    false,
                    "The sky visible from your camera looks clear right now, while weather telemetry reports $apiCloudCoverPct% cloud cover across the broader area. Local clear breaks are common."
                )
            }
            visualIsCloudy && apiIsClear -> {
                Triple(
                    "Local Variation Detected",
                    false,
                    "The camera view indicates $visualCondition overhead, whereas area telemetry reports lower cloud cover ($apiCloudCoverPct%). Isolated cloud formation may be present."
                )
            }
            else -> {
                Triple(
                    "Moderate Agreement",
                    true,
                    "Camera view and regional weather model are broadly consistent with partly cloudy atmospheric conditions."
                )
            }
        }
    }

    private fun evaluateRadarAgreement(
        visualPrecipitationHint: Boolean,
        currentRainMm: Double,
        rainProbPct: Double,
        radarMetadata: RadarMetadata?
    ): String {
        val radarAge = radarMetadata?.latestTimestamp?.let { RadarRepository.formatFrameAge(it) } ?: "Radar feed unavailable"

        return when {
            visualPrecipitationHint && (currentRainMm > 0.4 || rainProbPct > 50) -> {
                "Dark convective cloud textures align with weather model data indicating active precipitation risk (${rainProbPct.toInt()}% chance). $radarAge."
            }
            visualPrecipitationHint && currentRainMm <= 0.1 && rainProbPct < 30 -> {
                "Cloud deck appears dark and developed, but latest radar and telemetry do not indicate significant rainfall reaching your coordinates right now ($radarAge)."
            }
            !visualPrecipitationHint && currentRainMm > 1.0 -> {
                "Camera view does not visibly show rain, but regional telemetry indicates rain is possible in the surrounding vicinity. $radarAge."
            }
            else -> {
                "No visual or radar precipitation detected in the immediate area. $radarAge."
            }
        }
    }

    /**
     * Provides factual climatological context based on latitude, longitude, and current month.
     * Clearly labels this as historical statistics, NEVER claiming the camera determined it.
     */
    private fun getHistoricalClimateContext(location: SelectedLocation?): String {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) // 0-based: 0 = Jan, 8 = Sept

        val lat = location?.latitude ?: 16.5
        val isTropicalIndia = lat in 8.0..32.0

        return if (isTropicalIndia) {
            when (month) {
                in 5..8 -> "September falls within the late South-West Monsoon transition, historically characterized by intermittent convective showers, high relative humidity, and variable cloud decks."
                in 9..10 -> "October to November corresponds to the Post-Monsoon / North-East Monsoon period, typically featuring retreating rain bands and moderating temperatures."
                in 11..1 -> "December to February represents the winter dry season, historically seeing low rainfall, cooler night temperatures, and generally clear morning skies."
                else -> "March to May represents the hot pre-monsoon summer, with historically high solar UV irradiance, thermal convection, and isolated pre-monsoon squalls."
            }
        } else {
            "Climatological averages for this region reflect standard seasonal transitions. Historical trends show typical diurnal variations for this time of year."
        }
    }
}
