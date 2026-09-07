package com.example.weathergpt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.MetWeatherClient
import com.example.weathergpt.data.MetWeatherResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ForecastState {

    data object Loading : ForecastState()

    data class Success(
        val weather: MetWeatherResponse
    ) : ForecastState()

    data class Error(
        val message: String
    ) : ForecastState()
}


class ForecastViewModel : ViewModel() {

    private val _state =
        MutableStateFlow<ForecastState>(
            ForecastState.Loading
        )

    val state: StateFlow<ForecastState> =
        _state.asStateFlow()

    private var refreshJobStarted = false

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null


    fun loadForecast(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ) {
        lastLatitude = latitude
        lastLongitude = longitude

        // Check for instant cache hit
        val cached = MetWeatherClient.getCachedWeather(latitude, longitude)
        if (cached != null && _state.value !is ForecastState.Success) {
            _state.value = ForecastState.Success(weather = cached)
        }

        viewModelScope.launch {
            fetchWeather(
                latitude = latitude,
                longitude = longitude,
                showLoading = _state.value !is ForecastState.Success,
                forceRefresh = forceRefresh
            )
        }

        startAutoRefresh()
    }

    private suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        showLoading: Boolean,
        forceRefresh: Boolean = false
    ) {
        if (showLoading) {
            _state.value = ForecastState.Loading
        }

        try {
            val response = MetWeatherClient.getFastWeather(
                lat = latitude,
                lon = longitude,
                forceRefresh = forceRefresh
            )

            _state.value = ForecastState.Success(
                weather = response
            )
        } catch (e: Exception) {
            if (_state.value !is ForecastState.Success) {
                _state.value = ForecastState.Error(
                    e.message ?: "Unable to load weather"
                )
            }
        }
    }


    private fun startAutoRefresh() {

        if (refreshJobStarted) {
            return
        }

        refreshJobStarted = true

        viewModelScope.launch {

            while (true) {

                delay(
                    10 * 60 * 1000L
                )

                val latitude =
                    lastLatitude

                val longitude =
                    lastLongitude

                if (
                    latitude != null &&
                    longitude != null
                ) {

                    fetchWeather(
                        latitude = latitude,
                        longitude = longitude,
                        showLoading = false
                    )
                }
            }
        }
    }

    fun refreshNow() {

        val latitude =
            lastLatitude

        val longitude =
            lastLongitude

        if (
            latitude == null ||
            longitude == null
        ) {
            return
        }

        viewModelScope.launch {

            fetchWeather(
                latitude = latitude,
                longitude = longitude,
                showLoading = false
            )
        }
    }
}
