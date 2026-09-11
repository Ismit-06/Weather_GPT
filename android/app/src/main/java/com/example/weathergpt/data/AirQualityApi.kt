package com.example.weathergpt.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class AirQualityCurrent(
    val time: String?,
    @SerializedName("european_aqi")
    val europeanAqi: Double?,
    @SerializedName("us_aqi")
    val usAqi: Double?,
    val pm10: Double?,
    @SerializedName("pm2_5")
    val pm25: Double?,
    @SerializedName("carbon_monoxide")
    val carbonMonoxide: Double?,
    @SerializedName("nitrogen_dioxide")
    val nitrogenDioxide: Double?,
    @SerializedName("sulphur_dioxide")
    val sulphurDioxide: Double?,
    val ozone: Double?
)

data class AirQualityResponse(
    val latitude: Double?,
    val longitude: Double?,
    val current: AirQualityCurrent?
)

interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone"
    ): AirQualityResponse
}

object AirQualityClient {
    private const val BASE_URL = "https://air-quality-api.open-meteo.com/"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val api: AirQualityApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityApi::class.java)
    }

    private var cachedAqi: Pair<String, AirQualityCurrent>? = null
    private var lastFetchTime = 0L
    private const val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 mins

    suspend fun getLiveAirQuality(lat: Double, lon: Double): AirQualityCurrent? {
        val key = "${"%.2f".format(java.util.Locale.US, lat)}_${"%.2f".format(java.util.Locale.US, lon)}"
        val now = System.currentTimeMillis()
        if (cachedAqi?.first == key && (now - lastFetchTime < CACHE_DURATION_MS)) {
            return cachedAqi?.second
        }

        return try {
            val res = api.getAirQuality(latitude = lat, longitude = lon)
            if (res.current != null) {
                cachedAqi = Pair(key, res.current)
                lastFetchTime = now
            }
            res.current ?: cachedAqi?.second
        } catch (_: Exception) {
            cachedAqi?.second
        }
    }
}
