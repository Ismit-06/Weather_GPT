with open(r"e:/WORK/WeatherGPT/kotlin_dams.txt", "r", encoding="utf-8") as f:
    kotlin_dams = f.read()

header = """package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class DamItem(
    val id: Int?,
    val name: String?,
    val state: String?,
    val region: String?,
    val district: String?,
    val basin: String?,
    val latitude: Double?,
    val longitude: Double?,
    val frl_m: Double?,
    val current_level_m: Double?,
    val live_capacity_bcm: Double?,
    val live_storage_bcm: Double?,
    val storage_percent: Double?,
    val last_year_storage_percent: Double?,
    val normal_storage_percent: Double?,
    val irrigation_cca: Double?,
    val hydel_mw: Double?,
    val observation_date: String?,
    val source: String?,
    val source_type: String?,
    val official_warning: Boolean?
)

data class DamResponse(
    val status: String?,
    val count: Int?,
    val source: String?,
    val source_type: String?,
    val reservoirs: List<DamItem>?,
    val message: String?
)

interface DamService {

    @GET("dams")
    suspend fun getDams(
        @Query("state") state: String? = null,
        @Query("limit") limit: Int = 200
    ): DamResponse
}

object DamClient {

    val service: DamService by lazy {
        BackendConfig.createRetrofit().create(DamService::class.java)
    }

    val OFFICIAL_CWC_RESERVOIRS = """

footer = """
}
"""

target = r"e:/WORK/WeatherGPT/Weather_GPT/android/app/src/main/java/com/example/weathergpt/data/DamApi.kt"
with open(target, "w", encoding="utf-8") as f:
    f.write(header + kotlin_dams + footer)

print(f"Updated {target} successfully!")
