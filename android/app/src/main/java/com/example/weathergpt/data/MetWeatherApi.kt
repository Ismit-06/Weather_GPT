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

    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MetWeatherResponse>>()
    private const val CACHE_VALIDITY_MS = 10 * 60 * 1000L // 10 minutes

    private fun cacheKey(lat: Double, lon: Double): String {
        return "${"%.3f".format(java.util.Locale.US, lat)}_${"%.3f".format(java.util.Locale.US, lon)}"
    }

    fun getCachedWeather(lat: Double, lon: Double): MetWeatherResponse? {
        val entry = memoryCache[cacheKey(lat, lon)] ?: return null
        // Return cached entry if within validity or as instant placeholder
        return entry.second
    }

    suspend fun getFastWeather(lat: Double, lon: Double, forceRefresh: Boolean = false): MetWeatherResponse {
        val key = cacheKey(lat, lon)
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = memoryCache[key]
            if (cached != null && (now - cached.first) < CACHE_VALIDITY_MS) {
                return cached.second
            }
        }
        val fresh = api.getWeather(lat, lon)
        memoryCache[key] = Pair(now, fresh)
        return fresh
    }
}
