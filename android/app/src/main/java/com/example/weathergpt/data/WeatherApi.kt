package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


data class LocationResult(
    val id: Long?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
    val timezone: String?,
    val country: String?,
    val country_code: String?,
    val admin1: String?,
    val admin2: String?,
    val population: Long?,
    val source: String?,
    val display_name: String?
)

data class LocationSearchResponse(
    val status: String?,
    val query: String,
    val count: Int?,
    val results: List<LocationResult>
)

data class TemperatureForecastItem(
    val hour_ahead: Int,
    val predicted_temperature_c: Double,
    val model_mae_celsius: Double,
    val baseline_mae_celsius: Double
)

data class TemperatureForecastResponse(
    val status: String,
    val location: String,
    val reference_time: String,
    val current_temperature_c: Double,
    val forecast: List<TemperatureForecastItem>
)

data class RainfallForecastItem(
    val horizon_hours: Int,
    val predicted_rainfall_mm: Double,
    val rain_probability_pct: Double,
    val amount_model_mae_mm: Double,
    val amount_baseline_mae_mm: Double,
    val event_model_auc: Double?
)

data class RainfallForecastResponse(
    val status: String,
    val location: String,
    val reference_time: String,
    val current_rainfall_mm: Double?,
    val forecast: List<RainfallForecastItem>
)

data class FloodRainfallPoint(
    val horizon_hours: Int?,
    val predicted_rainfall_mm: Double?,
    val rain_probability_pct: Double?,
    val amount_model_mae_mm: Double?,
    val amount_baseline_mae_mm: Double?,
    val event_model_auc: Double?
)

data class FloodForecastItem(
    val horizon_hours: Int,
    val predicted_rainfall_mm: Double,
    val rain_probability_pct: Double,
    val effective_rainfall_mm: Double,
    val runoff_m3: Double,
    val water_level_m: Double,
    val stage_rise_m: Double,
    val distance_to_warning_level_m: Double,
    val flood_risk: String,
    val warning_level_m: Double,
    val danger_level_m: Double
)

data class FloodResponse(
    val status: String,
    val location: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val flood_risk: String?,
    val current_water_level_m: Double?,
    val warning_level_m: Double?,
    val danger_level_m: Double?,
    val rainfall_forecast: List<FloodRainfallPoint>? = null,
    val forecast: List<FloodForecastItem>? = null,
    val engine: String? = null,
    val source_type: String? = null,
    val official_warning: Boolean? = null,
    val important_note: String? = null,
    val message: String? = null
)

data class HazardItem(
    val hazard: String,
    val score: Double,
    val level: String,
    val reasons: List<String>
)

data class SafetyRecommendation(
    val category: String,
    val score: Double,
    val level: String,
    val actions: List<String>,
    val reasons: List<String>
)

data class FloodCurrent(
    val risk: String,
    val estimated_water_level_m: Double
)

data class SafetyData(
    val overall_risk_score: Double,
    val overall_risk_level: String,
    val highest_hazard: String?,
    val recommendations: List<SafetyRecommendation>,
    val engine: String
)

data class SafetyResponse(
    val status: String,
    val location: String,
    val reference_time: String,
    val hazards: List<HazardItem>,
    val flood: FloodCurrent,
    val safety: SafetyData,
    val engine: String,
    val important_note: String
)

interface WeatherApi {

    @GET("location/search")
    suspend fun getLocationSearch(
        @Query("query") query: String
    ): LocationSearchResponse

    @GET("prediction/temperature/direct")
    suspend fun getTemperatureForecast(
        @Query("location") location: String
    ): TemperatureForecastResponse

    @GET("prediction/rainfall")
    suspend fun getRainfallForecast(
        @Query("location") location: String
    ): RainfallForecastResponse

    @GET("prediction/flood")
    suspend fun getFloodForecast(
        @Query("location") location: String
    ): FloodResponse

    @GET("risk/safety")
    suspend fun getSafety(
        @Query("location") location: String,
        @Query("rainfall_probability") rainfallProbability: Double
    ): SafetyResponse
}

object WeatherApiClient {

    private const val BASE_URL =
        BackendConfig.BASE_URL

    val api: WeatherApi by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(WeatherApi::class.java)
    }
}
