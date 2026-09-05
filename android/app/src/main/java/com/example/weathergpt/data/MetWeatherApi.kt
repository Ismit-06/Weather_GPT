package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class MetForecastItem(
    val time: String?,
    val temperature_c: Double?,
    val relative_humidity_pct: Double?,
    val dew_point_c: Double?,
    val pressure_hpa: Double?,
    val wind_speed_ms: Double?,
    val wind_direction_deg: Double?,
    val wind_gust_ms: Double?,
    val cloud_cover_pct: Double?,
    val fog_area_pct: Double?,
    val precipitation_mm: Double?,
    val precipitation_probability_pct: Double?,
    val symbol_code: String?
)

data class MetLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?
)

data class MetWeatherResponse(
    val status: String,
    val location: MetLocation,
    val updated_at: String?,
    val forecast: List<MetForecastItem>,
    val source: String?
)

interface MetWeatherApi {

    @GET("weather/current")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): MetWeatherResponse
}

object MetWeatherClient {

    val api: MetWeatherApi by lazy {
        BackendConfig.createRetrofit().create(MetWeatherApi::class.java)
    }
}
