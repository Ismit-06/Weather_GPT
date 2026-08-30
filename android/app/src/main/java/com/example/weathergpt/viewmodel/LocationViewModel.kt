package com.example.weathergpt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.LocationResult
import com.example.weathergpt.data.WeatherApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LocationSearchState {

    data object Idle : LocationSearchState()

    data object Loading : LocationSearchState()

    data class Success(
        val results: List<LocationResult>
    ) : LocationSearchState()

    data class Error(
        val message: String
    ) : LocationSearchState()
}

class LocationViewModel : ViewModel() {

    private val _state =
        MutableStateFlow<LocationSearchState>(
            LocationSearchState.Idle
        )

    val state: StateFlow<LocationSearchState> =
        _state.asStateFlow()

    fun search(query: String) {

        val cleanQuery = query.trim()

        if (cleanQuery.length < 2) {
            _state.value =
                LocationSearchState.Idle
            return
        }

        viewModelScope.launch {

            _state.value =
                LocationSearchState.Loading

            try {

                val response =
                    WeatherApiClient.api
                        .getLocationSearch(
                            cleanQuery
                        )

                _state.value =
                    LocationSearchState.Success(
                        response.results
                    )

            } catch (exception: Exception) {

                _state.value =
                    LocationSearchState.Error(
                        exception.message
                            ?: "Location search failed"
                    )
            }
        }
    }

    fun clear() {
        _state.value =
            LocationSearchState.Idle
    }
}
