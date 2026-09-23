package com.weathergpt.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class AlertItem(
    @SerializedName("type") val type: String?,
    @SerializedName("severity") val severity: String?,
    @SerializedName("source_type") val sourceType: String?,
    @SerializedName("time") val time: String?,
    @SerializedName("value") val value: Any?,
    @SerializedName("unit") val unit: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("advisory") val advisory: String?,
    @SerializedName("pressure_hpa") val pressureHpa: Double?
)

data class AlertSummary(
    @SerializedName("total") val total: Int?,
    @SerializedName("high") val high: Int?,
    @SerializedName("medium") val medium: Int?,
    @SerializedName("low") val low: Int?
)

data class AlertsResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("alerts_present") val alertsPresent: Boolean?,
    @SerializedName("highest_severity") val highestSeverity: String?,
    @SerializedName("summary") val summary: AlertSummary?,
    @SerializedName("alerts") val alerts: List<AlertItem>?
)

interface AlertsApiService {
    @GET("alerts")
    suspend fun getAlerts(
        @Query("latitude") latitude: Double = 17.6868, // Default Visakhapatnam / East Coast
        @Query("longitude") longitude: Double = 83.2185,
        @Query("hours") hours: Int = 48
    ): AlertsResponse
}

object AlertsClient {
    // Uses WeatherGPT Cloud / Local API
    private const val BASE_URL = "https://weather-gpt-ymze.onrender.com/"

    val service: AlertsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlertsApiService::class.java)
    }
}
