package com.example.weathergpt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.AgricultureResponse
import com.example.weathergpt.data.AlertsResponse
import com.example.weathergpt.data.FeatureClient
import com.example.weathergpt.data.FloodResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class AlertsUiState(
    val isLoading: Boolean = false,
    val data: AlertsResponse? = null,
    val error: String? = null
)


class AlertsViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(AlertsUiState())

    val uiState: StateFlow<AlertsUiState> =
        _uiState.asStateFlow()

    fun load(
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {

            _uiState.value =
                AlertsUiState(
                    isLoading = true
                )

            try {
                val response =
                    FeatureClient.api.getAlerts(
                        latitude = latitude,
                        longitude = longitude
                    )

                _uiState.value =
                    AlertsUiState(
                        data = response
                    )

            } catch (e: Exception) {
                _uiState.value =
                    AlertsUiState(
                        error = e.message
                            ?: "Unable to load alerts."
                    )
            }
        }
    }
}


data class FloodUiState(
    val isLoading: Boolean = false,
    val data: FloodResponse? = null,
    val error: String? = null
)


class FloodViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(FloodUiState())

    val uiState: StateFlow<FloodUiState> =
        _uiState.asStateFlow()

    fun load(
        location: String,
        latitude: Double?,
        longitude: Double?
    ) {
        viewModelScope.launch {

            _uiState.value =
                FloodUiState(
                    isLoading = true
                )

            try {
                val response =
                    FeatureClient.api.getFlood(
                        location = location,
                        latitude = latitude,
                        longitude = longitude
                    )

                _uiState.value =
                    FloodUiState(
                        data = response
                    )

            } catch (e: Exception) {
                _uiState.value =
                    FloodUiState(
                        error = e.message
                            ?: "Unable to load flood data."
                    )
            }
        }
    }
}


data class AgricultureUiState(
    val isLoading: Boolean = false,
    val data: AgricultureResponse? = null,
    val error: String? = null
)


class AgricultureViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(AgricultureUiState())

    val uiState: StateFlow<AgricultureUiState> =
        _uiState.asStateFlow()

    fun load(
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {

            _uiState.value =
                AgricultureUiState(
                    isLoading = true
                )

            try {
                val response =
                    FeatureClient.api.getAgriculture(
                        latitude = latitude,
                        longitude = longitude
                    )

                _uiState.value =
                    AgricultureUiState(
                        data = response
                    )

            } catch (e: Exception) {
                _uiState.value =
                    AgricultureUiState(
                        error = e.message
                            ?: "Unable to load agriculture data."
                    )
            }
        }
    }
}
