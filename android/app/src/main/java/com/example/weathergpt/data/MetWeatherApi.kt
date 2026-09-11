package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.math.roundToInt

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

data class DailyForecastSummary(
    val date: java.time.LocalDate,
    val dayLabel: String,
    val maxTempC: Int,
    val minTempC: Int,
    val symbolCode: String?,
    val precipitationProbMax: Int?,
    val precipitationMmSum: Double?
)

fun List<MetForecastItem>.extractDailyForecast(maxDays: Int = 7): List<DailyForecastSummary> {
    if (this.isEmpty()) return emptyList()

    val zone = try { java.time.ZoneId.systemDefault() } catch (_: Exception) { java.time.ZoneId.of("UTC") }
    val today = java.time.LocalDate.now(zone)

    // Group timesteps by their local calendar date
    val grouped = this.groupBy { item ->
        val timeStr = item.time ?: return@groupBy null
        try {
            java.time.ZonedDateTime.parse(timeStr).withZoneSameInstant(zone).toLocalDate()
        } catch (_: Exception) {
            try { java.time.LocalDate.parse(timeStr.substring(0, 10)) } catch (_: Exception) { null }
        }
    }.filterKeys { it != null } as Map<java.time.LocalDate, List<MetForecastItem>>

    // Only include today and upcoming days, sorted chronologically
    val sortedDates = grouped.keys.filter { !it.isBefore(today) }.sorted()

    return sortedDates.take(maxDays).mapIndexed { index, date ->
        val items = grouped[date] ?: emptyList()
        val temps = items.mapNotNull { it.temperature_c }
        val maxTemp = temps.maxOrNull()?.roundToInt() ?: 30
        val minTemp = temps.minOrNull()?.roundToInt() ?: (maxTemp - 5)

        // Select representative symbol: prefer rainy/stormy if occurring, or daytime (11-16h), else midday/first
        val repItem = items.firstOrNull { item ->
            val code = item.symbol_code ?: ""
            code.contains("rain") || code.contains("thunder") || code.contains("snow")
        } ?: items.firstOrNull { item ->
            try {
                val hour = java.time.ZonedDateTime.parse(item.time).withZoneSameInstant(zone).hour
                hour in 11..16
            } catch (_: Exception) { false }
        } ?: items.firstOrNull()

        val maxPrecipProb = items.mapNotNull { it.precipitation_probability_pct }.maxOrNull()?.roundToInt()
        val sumPrecipMm = items.mapNotNull { it.precipitation_mm }.sum()

        val dayLabel = when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
        }

        DailyForecastSummary(
            date = date,
            dayLabel = dayLabel,
            maxTempC = maxTemp,
            minTempC = minTemp,
            symbolCode = repItem?.symbol_code ?: "fair_day",
            precipitationProbMax = maxPrecipProb,
            precipitationMmSum = sumPrecipMm
        )
    }
}

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
    private const val DISK_PREF_NAME = "weathergpt_met_cache"
    private val gson = com.google.gson.Gson()

    private fun cacheKey(lat: Double, lon: Double): String {
        return "${"%.3f".format(java.util.Locale.US, lat)}_${"%.3f".format(java.util.Locale.US, lon)}"
    }

    fun getCachedWeather(lat: Double, lon: Double, context: android.content.Context? = null): MetWeatherResponse? {
        val key = cacheKey(lat, lon)
        val entry = memoryCache[key]
        if (entry != null) return entry.second

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(DISK_PREF_NAME, android.content.Context.MODE_PRIVATE)
                val json = prefs.getString(key, null)
                if (!json.isNullOrBlank()) {
                    val resp = gson.fromJson(json, MetWeatherResponse::class.java)
                    if (resp != null) {
                        memoryCache[key] = Pair(System.currentTimeMillis(), resp)
                        return resp
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun getFastWeather(lat: Double, lon: Double, context: android.content.Context? = null, forceRefresh: Boolean = false): MetWeatherResponse {
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

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(DISK_PREF_NAME, android.content.Context.MODE_PRIVATE)
                prefs.edit().putString(key, gson.toJson(fresh)).apply()
            } catch (_: Exception) {}
        }
        return fresh
    }
}
