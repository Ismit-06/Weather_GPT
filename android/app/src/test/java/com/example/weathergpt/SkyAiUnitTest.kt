package com.example.weathergpt

import com.example.weathergpt.data.SkyAiClient
import com.example.weathergpt.data.SkyAnalysisResponse
import com.example.weathergpt.data.SkyAcceptanceGateStatus
import org.junit.Assert.*
import org.junit.Test

class SkyAiUnitTest {

    @Test
    fun testValidSkyResponseAcceptance() {
        val response = SkyAnalysisResponse(
            request_id = "req-001",
            sky_detected = true,
            sky_confidence = 0.95,
            scene_type = "outdoor_sky",
            cloud_condition = "partly_cloudy",
            cloud_coverage = 0.40,
            visible_precipitation = false,
            horizon_visible = true,
            obstruction = "none",
            image_quality = "good",
            model_version = "SKY-LORA-002"
        )

        val gate = SkyAiClient.evaluateGate(response, 0.80)
        assertTrue("Valid sky must be accepted", gate.isAccepted)
        assertEquals(SkyAcceptanceGateStatus.ACCEPTED, gate.status)
        assertEquals("partly_cloudy", gate.response.cloud_condition)
        assertEquals(0.40, gate.response.cloud_coverage ?: 0.0, 0.001)
    }

    @Test
    fun testBedsheetResponseRejection() {
        val response = SkyAnalysisResponse(
            request_id = "req-002",
            sky_detected = false,
            sky_confidence = 0.02,
            scene_type = "bedsheet",
            cloud_condition = null,
            cloud_coverage = null,
            visible_precipitation = false,
            horizon_visible = false,
            obstruction = "none",
            image_quality = "good",
            model_version = "SKY-LORA-002"
        )

        val gate = SkyAiClient.evaluateGate(response, 0.80)
        assertFalse("Bedsheet must be rejected", gate.isAccepted)
        assertEquals(SkyAcceptanceGateStatus.REJECTED_NOT_SKY, gate.status)
        assertNull("Cloud condition must be null when rejected", gate.response.cloud_condition)
        assertNull("Cloud coverage must be null when rejected", gate.response.cloud_coverage)
    }

    @Test
    fun testCeilingResponseRejection() {
        val response = SkyAnalysisResponse(
            request_id = "req-003",
            sky_detected = false,
            sky_confidence = 0.01,
            scene_type = "ceiling",
            cloud_condition = null,
            cloud_coverage = null,
            visible_precipitation = false,
            horizon_visible = false,
            obstruction = "none",
            image_quality = "good",
            model_version = "SKY-LORA-002"
        )

        val gate = SkyAiClient.evaluateGate(response, 0.80)
        assertFalse("Ceiling must be rejected", gate.isAccepted)
        assertEquals(SkyAcceptanceGateStatus.REJECTED_NOT_SKY, gate.status)
    }

    @Test
    fun testLowConfidenceResponseRejection() {
        val response = SkyAnalysisResponse(
            request_id = "req-004",
            sky_detected = true,
            sky_confidence = 0.65,
            scene_type = "outdoor_sky",
            cloud_condition = "overcast",
            cloud_coverage = 0.90,
            visible_precipitation = false,
            horizon_visible = false,
            obstruction = "partial",
            image_quality = "poor",
            model_version = "SKY-LORA-002"
        )

        val gate = SkyAiClient.evaluateGate(response, 0.80)
        assertFalse("Low confidence sky must be rejected", gate.isAccepted)
        assertEquals(SkyAcceptanceGateStatus.REJECTED_LOW_CONFIDENCE, gate.status)
    }

    @Test
    fun testSkyDetectedFalseCanNeverProduceValidSkyState() {
        val falseResponses = listOf(
            SkyAnalysisResponse("r1", false, 0.99, "fabric", "clear", 0.0, false, false, "none", "good", "SKY-LORA-002"),
            SkyAnalysisResponse("r2", false, 0.01, "ceiling", null, null, false, false, "none", "good", "SKY-LORA-002"),
            SkyAnalysisResponse("r3", false, 0.40, "window", null, null, false, false, "none", "good", "SKY-LORA-002")
        )

        for (resp in falseResponses) {
            val gate = SkyAiClient.evaluateGate(resp, 0.80)
            assertFalse("sky_detected=false MUST NEVEE be accepted", gate.isAccepted)
            assertNotEquals(SkyAcceptanceGateStatus.ACCEPTED, gate.status)
        }
    }

    @Test
    fun testNullCloudFieldsPreservation() {
        val response = SkyAnalysisResponse(
            request_id = "req-005",
            sky_detected = false,
            sky_confidence = 0.03,
            scene_type = "indoor_surface",
            cloud_condition = null,
            cloud_coverage = null,
            visible_precipitation = false,
            horizon_visible = false,
            obstruction = "none",
            image_quality = "good",
            model_version = "SKY-LORA-002"
        )

        assertNull("Null cloud condition must remain null", response.cloud_condition)
        assertNull("Null cloud coverage must remain null", response.cloud_coverage)
    }
}
