package com.example.weathergpt.sky

import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetLocation
import com.example.weathergpt.data.MetWeatherResponse
import com.example.weathergpt.data.radar.RadarMetadata
import com.example.weathergpt.location.SelectedLocation
import org.junit.Assert.*
import org.junit.Test

class SkyWeatherFusionEngineTest {

    @Test
    fun testFusionWithHighAgreement() {
        val eval = SkyEvaluationResult(
            state = SkyDecisionState.VALID_SKY,
            confidence = 0.88f,
            quality = SkyQualityMetrics(
                blurScore = 45.0,
                isBlurry = false,
                meanLuminance = 135.0,
                isTooDark = false,
                isOverexposed = false,
                isExcessiveGlare = false,
                glareFraction = 0.02f,
                qualityScore = 0.90f
            ),
            segmentation = SkySegmentationResult(
                skyCoverageFraction = 0.75f,
                cloudFractionWithinSky = 0.88f,
                visualCloudCondition = "Overcast",
                visualPrecipitationHint = false,
                visualHazeHint = false,
                averageLuminance = 120.0,
                blueSkyRatio = 0.05f
            ),
            indoorProbability = 0.05f,
            ceilingProbability = 0.02f,
            outdoorProbability = 0.95f,
            detectedMlLabels = listOf("Sky (95%)", "Cloud (90%)")
        )

        val weatherResponse = MetWeatherResponse(
            status = "ok",
            location = MetLocation(16.5, 80.6, 20.0),
            updated_at = "2026-09-10T12:00:00Z",
            forecast = listOf(
                MetForecastItem(
                    time = "2026-09-10T12:00:00Z",
                    temperature_c = 29.5,
                    relative_humidity_pct = 82.0,
                    dew_point_c = 26.0,
                    pressure_hpa = 1008.0,
                    wind_speed_ms = 4.2,
                    wind_direction_deg = 180.0,
                    wind_gust_ms = 6.0,
                    cloud_cover_pct = 85.0,
                    fog_area_pct = 0.0,
                    precipitation_mm = 0.0,
                    precipitation_probability_pct = 20.0,
                    symbol_code = "cloudy"
                )
            ),
            source = "test"
        )

        val radarMeta = RadarMetadata(
            host = "https://tilecache.rainviewer.com",
            frames = emptyList(),
            latestTimestamp = System.currentTimeMillis() / 1000L,
            lastFetchedAt = System.currentTimeMillis()
        )

        val loc = SelectedLocation("Amaravati", 16.5, 80.6, "India", "Andhra Pradesh", "Asia/Kolkata")

        val report = SkyWeatherFusionEngine.fuse(eval, weatherResponse, radarMeta, loc)

        assertEquals("Overcast", report.visualSkyCondition)
        assertEquals(75, report.visualSkyCoveragePct)
        assertEquals("High Agreement", report.cloudinessAgreement)
        assertTrue(report.isAgreementStrong)
        assertEquals("High Confidence", report.confidenceLevel)

        // Verify voice-safe speech text has NO emojis and NO markdown
        assertFalse(report.speechText.contains("*"))
        assertFalse(report.speechText.contains("#"))
        assertFalse(report.speechText.contains("`"))
        assertFalse(report.speechText.contains("•"))
        assertFalse(report.speechText.contains("⚠️"))
        assertFalse(report.speechText.contains("☁️"))
        assertFalse(report.speechText.contains("🌡️"))
    }

    @Test
    fun testConflictDetectionWhenCameraDiffersFromForecast() {
        // Camera sees Clear Sky, but API reports 90% cloud cover
        val eval = SkyEvaluationResult(
            state = SkyDecisionState.VALID_SKY,
            confidence = 0.85f,
            quality = SkyQualityMetrics(
                blurScore = 50.0,
                isBlurry = false,
                meanLuminance = 160.0,
                isTooDark = false,
                isOverexposed = false,
                isExcessiveGlare = false,
                glareFraction = 0.01f,
                qualityScore = 0.92f
            ),
            segmentation = SkySegmentationResult(
                skyCoverageFraction = 0.80f,
                cloudFractionWithinSky = 0.05f,
                visualCloudCondition = "Clear Sky",
                visualPrecipitationHint = false,
                visualHazeHint = false,
                averageLuminance = 160.0,
                blueSkyRatio = 0.95f
            ),
            indoorProbability = 0.02f,
            ceilingProbability = 0.01f,
            outdoorProbability = 0.98f,
            detectedMlLabels = listOf("Sky (98%)", "Blue (92%)")
        )

        val weatherResponse = MetWeatherResponse(
            status = "ok",
            location = MetLocation(16.5, 80.6, 20.0),
            updated_at = "2026-09-10T12:00:00Z",
            forecast = listOf(
                MetForecastItem(
                    time = "2026-09-10T12:00:00Z",
                    temperature_c = 31.0,
                    relative_humidity_pct = 75.0,
                    dew_point_c = 25.0,
                    pressure_hpa = 1010.0,
                    wind_speed_ms = 3.5,
                    wind_direction_deg = 150.0,
                    wind_gust_ms = 5.0,
                    cloud_cover_pct = 90.0, // High cloud cover from station
                    fog_area_pct = 0.0,
                    precipitation_mm = 0.0,
                    precipitation_probability_pct = 15.0,
                    symbol_code = "cloudy"
                )
            ),
            source = "test"
        )

        val loc = SelectedLocation("Vijayawada", 16.5, 80.6, "India", "Andhra Pradesh", "Asia/Kolkata")
        val report = SkyWeatherFusionEngine.fuse(eval, weatherResponse, null, loc)

        assertEquals("Local Variation Detected", report.cloudinessAgreement)
        assertFalse(report.isAgreementStrong)
        assertTrue(report.displayText.contains("Local clear breaks are common"))
    }

    @Test
    fun testClimateContextDoesNotClaimCameraDetermination() {
        val eval = SkyEvaluationResult(
            state = SkyDecisionState.VALID_SKY,
            confidence = 0.80f,
            quality = SkyQualityMetrics(30.0, false, 120.0, false, false, false, 0f, 0.8f),
            segmentation = SkySegmentationResult(0.6f, 0.4f, "Partly Cloudy", false, false, 120.0, 0.6f),
            indoorProbability = 0f,
            ceilingProbability = 0f,
            outdoorProbability = 0.9f,
            detectedMlLabels = emptyList()
        )

        val loc = SelectedLocation("Amaravati", 16.5, 80.6, "India", "Andhra Pradesh", "Asia/Kolkata")
        val report = SkyWeatherFusionEngine.fuse(eval, null, null, loc)

        assertTrue(report.climateContextSummary.contains("Monsoon") || report.climateContextSummary.contains("climatological"))
        // Check limitation is present
        assertTrue(report.limitsAndDisclaimer.contains("Camera visual observation alone cannot measure exact temperature"))
    }
}
