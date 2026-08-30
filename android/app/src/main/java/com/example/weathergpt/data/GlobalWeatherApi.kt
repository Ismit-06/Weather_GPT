package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class GlobalLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val timezone: String?,
    val timezone_abbreviation: String?,
    val elevation: Double?
)

data class GlobalCurrentWeather(
    val time: String?,
    val interval: Int?,
    val temperature_2m: Double?,
    val relative_humidity_2m: Double?,
    val apparent_temperature: Double?,
    val precipitation: Double?,
    val rain: Double?,
    val showers: Double?,
    val snowfall: Double?,
    val weather_code: Int?,
    val cloud_cover: Double?,
    val pressure_msl: Double?,
    val surface_pressure: Double?,
    val wind_speed_10m: Double?,
    val wind_direction_10m: Double?,
    val wind_gusts_10m: Double?,
    val is_day: Int?
)

data class GlobalHourlyWeather(
    val time: List<String>?,
    val temperature_2m: List<Double>?,
    val relative_humidity_2m: List<Double>?,
    val dew_point_2m: List<Double>?,
    val apparent_temperature: List<Double>?,
    val precipitation_probability: List<Double>?,
    val precipitation: List<Double>?,
    val rain: List<Double>?,
    val showers: List<Double>?,
    val snowfall: List<Double>?,
    val weather_code: List<Int>?,
    val cloud_cover: List<Double>?,
    val pressure_msl: List<Double>?,
    val surface_pressure: List<Double>?,
    val visibility: List<Double>?,
    val wind_speed_10m: List<Double>?,
    val wind_direction_10m: List<Double>?,
    val wind_gusts_10m: List<Double>?,
    val uv_index: List<Double>?,
    val is_day: List<Int>?,
    val evapotranspiration: List<Double>?,
    val vapour_pressure_deficit: List<Double>?,
    val cape: List<Double>?,
    val runoff: List<Double>?
)

data class GlobalDailyWeather(
    val time: List<String>?,
    val temperature_2m_max: List<Double>?,
    val temperature_2m_min: List<Double>?,
    val temperature_2m_mean: List<Double>?,
    val apparent_temperature_max: List<Double>?,
    val apparent_temperature_min: List<Double>?,
    val precipitation_sum: List<Double>?,
    val rain_sum: List<Double>?,
    val showers_sum: List<Double>?,
    val snowfall_sum: List<Double>?,
    val precipitation_hours: List<Double>?,
    val precipitation_probability_max: List<Double>?,
    val weather_code: List<Int>?,
    val sunrise: List<String>?,
    val sunset: List<String>?,
    val sunshine_duration: List<Double>?,
    val uv_index_max: List<Double>?,
    val wind_speed_10m_max: List<Double>?,
    val wind_gusts_10m_max: List<Double>?,
    val wind_direction_10m_dominant: List<Double>?
)

data class GlobalWeatherResponse(
    val status: String,
    val location: GlobalLocationInfo,
    val current: GlobalCurrentWeather,
    val current_units: Map<String, String>?,
    val hourly: GlobalHourlyWeather,
    val hourly_units: Map<String, String>?,
    val daily: GlobalDailyWeather,
    val daily_units: Map<String, String>?,
    val source: String?,
    val model: String?,
    val generationtime_ms: Double?
)

interface GlobalWeatherApi {

    @GET("global-weather/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("forecast_days") forecastDays: Int = 7
    ): GlobalWeatherResponse
}

object GlobalWeatherClient {

    private const val BASE_URL =
        "https://weather-gpt-jfpk.onrender.com/"

    val api: GlobalWeatherApi by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(GlobalWeatherApi::class.java)
    }
}
