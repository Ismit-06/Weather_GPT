package com.example.weathergpt.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File

data class SkyAnalysisResponse(
    val request_id: String,
    val sky_detected: Boolean,
    val sky_confidence: Double,
    val scene_type: String,
    val cloud_condition: String?,
    val cloud_coverage: Double?,
    val visible_precipitation: Boolean,
    val horizon_visible: Boolean,
    val obstruction: String,
    val image_quality: String,
    val model_version: String
)

enum class SkyAcceptanceGateStatus {
    ACCEPTED,
    REJECTED_NOT_SKY,
    REJECTED_LOW_CONFIDENCE
}

data class SkyAiGateEvaluation(
    val status: SkyAcceptanceGateStatus,
    val response: SkyAnalysisResponse,
    val isAccepted: Boolean
)

data class VisualCloudObservationDto(
    val analysis_status: String = "success",
    val dominant_cloud_type: String = "unknown",
    val secondary_cloud_type: String? = null,
    val cloud_coverage: Double? = null,
    val cloud_thickness: String = "unknown",
    val vertical_development: String = "unknown",
    val apparent_cloud_base: String = "unknown",
    val convective_appearance: Any? = false,
    val precipitation_appearance: String = "none_visible",
    val development_trend: String = "unknown",
    val confidence: Double = 0.5,
    val limitations: List<String> = emptyList()
)

data class VisualCloudWeatherContextDto(
    val temperature: Double? = null,
    val feels_like: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val wind_speed: Double? = null,
    val wind_direction: Double? = null,
    val precipitation_probability: Double? = null,
    val current_precipitation: Double? = null,
    val cloud_cover: Double? = null,
    val condition: String? = null,
    val location_name: String? = null,
    val source: String = "MET Norway"
)

data class VisualCloudAssessmentDto(
    val next_1_hour: String,
    val next_3_hours: String,
    val next_6_hours: String,
    val precipitation_signal: String,
    val convection_signal: String,
    val weather_change_signal: String,
    val overall_short_term_signal: String,
    val storm_signal: String = "none",
    val confidence: String,
    val is_weather_data_available: Boolean = true
)

data class VisualCloudIntelligenceResponse(
    val status: String = "success",
    val observation: VisualCloudObservationDto,
    val weather_context: VisualCloudWeatherContextDto? = null,
    val assessment: VisualCloudAssessmentDto,
    val explanation: String
)

interface SkyAiApi {
    @Multipart
    @POST("api/sky/analyze")
    suspend fun analyzeSky(
        @Part image: MultipartBody.Part
    ): SkyAnalysisResponse

    @Multipart
    @POST("api/v1/weather/analyze-sky")
    suspend fun analyzeVisualCloud(
        @Part image: MultipartBody.Part,
        @Part("latitude") latitude: okhttp3.RequestBody? = null,
        @Part("longitude") longitude: okhttp3.RequestBody? = null,
        @Part("location_name") locationName: okhttp3.RequestBody? = null,
        @Part("language") language: okhttp3.RequestBody? = null
    ): VisualCloudIntelligenceResponse
}

object SkyAiClient {

    private const val DEFAULT_SERVICE_URL = BackendConfig.BASE_URL
    var serviceUrl: String = DEFAULT_SERVICE_URL

    val api: SkyAiApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(serviceUrl)
            .client(BackendConfig.okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(SkyAiApi::class.java)
    }

    fun createMultipartImage(file: File): MultipartBody.Part {
        val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", file.name, requestBody)
    }

    fun createMultipartImage(bytes: ByteArray, filename: String = "camera_frame.jpg"): MultipartBody.Part {
        val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", filename, requestBody)
    }

    /**
     * Str-ict two-tier confidence gate
     */
    fun evaluateGate(
        response: SkyAnalysisResponse,
        confidenceThreshold: Double = 0.80
    ): SkyAiGateEvaluation {
        if (!response.sky_detected) {
            return SkyAiGateEvaluation(
                status = SkyAcceptanceGateStatus.REJECTED_NOT_SKY,
                response = response,
                isAccepted = false
            )
        }

        if (response.sky_confidence < confidenceThreshold) {
            return SkyAiGateEvaluation(
                status = SkyAcceptanceGateStatus.REJECTED_LOW_CONFIDENCE,
                response = response,
                isAccepted = false
            )
        }

        return SkyAiGateEvaluation(
            status = SkyAcceptanceGateStatus.ACCEPTED,
            response = response,
            isAccepted = true
        )
    }
}
