package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


data class ChatMessage(
    val role: String,
    val content: String
)


data class AgentState(
    val intent: String? = null,
    val activity: String? = null,
    val target_local_time: String? = null,
    val target_date: String? = null,
    val timezone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val language: String? = null,

    val location_name: String? = null,
    val location_latitude: Double? = null,
    val location_longitude: Double? = null,
    val location_timezone: String? = null,
    val location_country: String? = null,
    val location_admin1: String? = null
)


data class ChatWeatherRequest(
    val question: String,
    val latitude: Double,
    val longitude: Double,
    val language: String,
    val history: List<ChatMessage> = emptyList(),
    val agent_state: AgentState = AgentState()
)


data class ChatWeatherResponse(
    val status: String?,
    val type: String?,
    val question: String?,
    val intent: String?,
    val activity: String?,
    val target_local_time: String?,
    val language: String?,
    val language_code: String?,
    val script_code: String?,
    val answer: String?,
    val source: String?,
    val updated_at: String?,
    val location: ChatLocation?,
    val weather: ChatWeatherContext?,
    val tool: Any?,
    val context: Any?,
    val agent_state: AgentState?
)


data class ChatLocation(
    val latitude: Double?,
    val longitude: Double?
)


data class ChatWeatherContext(
    val location: ChatLocation?,
    val current: ChatCurrentWeather?,
    val next_hours: List<ChatHourlyWeather>?,
    val source: String?,
    val updated_at: String?
)


data class ChatCurrentWeather(
    val time: String?,
    val temperature_c: Double?,
    val humidity_pct: Double?,
    val dew_point_c: Double?,
    val pressure_hpa: Double?,
    val wind_speed_ms: Double?,
    val wind_direction_deg: Double?,
    val wind_gust_ms: Any?,
    val cloud_cover_pct: Double?,
    val rainfall_mm: Double?,
    val condition: String?
)


data class ChatHourlyWeather(
    val time: String?,
    val temperature_c: Double?,
    val rainfall_mm: Double?,
    val humidity_pct: Double?,
    val wind_speed_ms: Double?,
    val wind_direction_deg: Double?,
    val condition: String?
)


interface ChatApi {

    @POST("chat/weather")
    suspend fun askWeather(
        @Body request: ChatWeatherRequest
    ): ChatWeatherResponse
}


object ChatClient {

    val api: ChatApi by lazy {
        BackendConfig.createRetrofit().create(ChatApi::class.java)
    }
}
