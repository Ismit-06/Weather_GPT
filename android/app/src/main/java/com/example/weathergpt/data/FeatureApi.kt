package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


data class AlertItem(
    val type: String?,
    val severity: String?,
    val source_type: String?,
    val time: String?,
    val value: Double?,
    val message: String?
)


data class AlertSummary(
    val total: Int?,
    val high: Int?,
    val medium: Int?
)


data class AlertsResponse(
    val status: String?,
    val alerts_present: Boolean?,
    val highest_severity: String?,
    val summary: AlertSummary?,
    val alerts: List<AlertItem>?,
    val official_warning: Boolean?,
    val source_type: String?,
    val warning_note: String?,
    val source: String?,
    val updated_at: String?
)


data class AgricultureHazard(
    val score: Double?,
    val level: String?,
    val reasons: List<String>?
)


data class AgricultureHazards(
    val heat: AgricultureHazard?,
    val heavy_rain: AgricultureHazard?,
    val strong_wind: AgricultureHazard?
)


data class RainfallAssessment(
    val total_mm: Double?,
    val maximum_hourly_mm: Double?,
    val classification: String?,
    val irrigation_impact: String?
)


data class AgricultureForecastSummary(
    val forecast_hours: Int?,
    val total_rainfall_mm: Double?,
    val maximum_hourly_rainfall_mm: Double?,
    val maximum_temperature_c: Double?,
    val maximum_wind_speed_ms: Double?
)


data class FeatureLocation(
    val latitude: Double?,
    val longitude: Double?
)


data class AgricultureResponse(
    val status: String?,
    val category: String?,
    val location: FeatureLocation?,
    val risk_score: Double?,
    val risk_level: String?,
    val suitability_score: Double?,
    val suitability_level: String?,
    val rainfall_assessment: RainfallAssessment?,
    val actions: List<String>?,
    val reasons: List<String>?,
    val hazards: AgricultureHazards?,
    val forecast_summary: AgricultureForecastSummary?,
    val source: String?,
    val updated_at: String?,
    val disclaimer: String?
)


interface FeatureApi {

    @GET("alerts")
    suspend fun getAlerts(
        @Query("latitude")
        latitude: Double,

        @Query("longitude")
        longitude: Double,

        @Query("hours")
        hours: Int = 48
    ): AlertsResponse


    @GET("flood")
    suspend fun getFlood(
        @Query("location")
        location: String,

        @Query("latitude")
        latitude: Double?,

        @Query("longitude")
        longitude: Double?
    ): FloodResponse


    @GET("agriculture")
    suspend fun getAgriculture(
        @Query("latitude")
        latitude: Double,

        @Query("longitude")
        longitude: Double,

        @Query("hours")
        hours: Int = 24
    ): AgricultureResponse
}


object FeatureClient {

    private const val BASE_URL =
        "https://weather-gpt-jfpk.onrender.com/"

    val api: FeatureApi by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(FeatureApi::class.java)
    }
}
