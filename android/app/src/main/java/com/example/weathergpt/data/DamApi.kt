package com.example.weathergpt.data

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
        @Query("limit") limit: Int = 100
    ): DamResponse
}

object DamClient {

    private const val BASE_URL =
        "https://weather-gpt-jfpk.onrender.com/"

    private val retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()

    val service: DamService =
        retrofit.create(
            DamService::class.java
        )
}
