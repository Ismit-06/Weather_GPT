package com.example.weathergpt.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackendConfig {
    // Render Cloud Backend (Permanent host)
    const val BASE_URL = "https://weather-gpt-ymze.onrender.com/"
    const val BASE_URL_NO_SLASH = "https://weather-gpt-ymze.onrender.com"

    // Retry interceptor for cold-start 502/503/504 errors while Render spins up
    private class ColdStartRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request: Request = chain.request()
            var response: Response? = null
            var exception: Exception? = null
            var tryCount = 0
            val maxTries = 3

            while (tryCount < maxTries) {
                try {
                    response?.close()
                    response = chain.proceed(request)
                    // If Render is booting, it returns 502 Bad Gateway or 503 Service Unavailable temporarily
                    if (response.code in listOf(502, 503, 504)) {
                        tryCount++
                        if (tryCount < maxTries) {
                            Thread.sleep(2500L) // Wait for container to wake up
                            continue
                        }
                    }
                    return response
                } catch (e: Exception) {
                    exception = e
                    tryCount++
                    if (tryCount < maxTries) {
                        try {
                            Thread.sleep(2000L)
                        } catch (_: InterruptedException) {}
                    }
                }
            }
            if (response != null) return response
            throw exception ?: java.io.IOException("Network request failed after $maxTries attempts.")
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(ColdStartRetryInterceptor())
            .build()
    }

    fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun warmUp() {
        try {
            val request = Request.Builder()
                .url("${BASE_URL_NO_SLASH}/health")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { }
            }
        } catch (_: Exception) {}
    }
}

