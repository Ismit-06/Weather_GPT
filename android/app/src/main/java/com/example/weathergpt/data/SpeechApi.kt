package com.example.weathergpt.data

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit


data class SpeechResponse(
    val status: String?,
    val transcript: String?,
    val language_code: String?,
    val request_id: String?,
    val message: String?
)


interface SpeechApi {

    @Multipart
    @POST("speech/transcribe")
    suspend fun transcribe(
        @Part file: MultipartBody.Part
    ): SpeechResponse
}


object SpeechClient {

    private const val BASE_URL =
        "https://weather-gpt-jfpk.onrender.com/"

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                60,
                TimeUnit.SECONDS
            )
            .readTimeout(
                60,
                TimeUnit.SECONDS
            )
            .build()

    val api: SpeechApi by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(SpeechApi::class.java)
    }
}
