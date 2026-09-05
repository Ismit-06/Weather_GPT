package com.example.weathergpt.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class LocationReverseResponse(
    val name: String?,
    val state: String?,
    val country: String?,
    val display_name: String?,
    val latitude: Double?,
    val longitude: Double?
)

interface LocationReverseApi {

    @GET("location/reverse")
    suspend fun reverse(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): LocationReverseResponse
}

object LocationReverseClient {

    val api: LocationReverseApi by lazy {
        BackendConfig.createRetrofit().create(LocationReverseApi::class.java)
    }
}
