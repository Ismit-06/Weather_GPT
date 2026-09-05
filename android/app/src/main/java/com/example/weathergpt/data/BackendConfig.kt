package com.example.weathergpt.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object BackendConfig {
    // Render Cloud Backend (Permanent host)
    const val BASE_URL = "https://weather-gpt-ymze.onrender.com/"
    const val BASE_URL_NO_SLASH = "https://weather-gpt-ymze.onrender.com"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

