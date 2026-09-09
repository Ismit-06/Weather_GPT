package com.example.weathergpt.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.AgentState
import com.example.weathergpt.data.BackendConfig
import com.example.weathergpt.data.ChatClient
import com.example.weathergpt.data.ChatWeatherRequest
import com.example.weathergpt.data.DamClient
import com.example.weathergpt.data.FeatureClient
import com.example.weathergpt.data.radar.RadarFrame
import com.example.weathergpt.data.radar.RadarRepository
import com.example.weathergpt.location.LocationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class AnyLocationWeather(
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val temperature: Double?,
    val apparentTemperature: Double? = null,
    val humidity: Double?,
    val windSpeed: Double?,
    val pressure: Double?,
    val rainProbability: Double?,
    val weatherCode: Int? = null,
    val conditionText: String? = null
)

fun getWeatherConditionText(code: Int?): String {
    return when (code) {
        0 -> "Clear Sky"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Clear"
    }
}

data class DamMarkerData(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val state: String?,
    val storagePercent: Double?,
    val level: Double?
)

data class EarthquakeMarkerData(
    val latitude: Double,
    val longitude: Double,
    val magnitude: Double?,
    val place: String?,
    val depthKm: Double?
)

data class FloodMapData(
    val latitude: Double,
    val longitude: Double,
    val risk: String,
    val currentWaterLevel: Double?,
    val warningLevel: Double?,
    val dangerLevel: Double?
)

data class AlertMarkerData(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val severity: String,
    val description: String?
)

data class AreaAnalysis(
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val temperature: Double?,
    val rainfallProbability: Double?,
    val floodRisk: String?,
    val overallRisk: String?,
    val recommendation: String?
)

data class MapUiState(
    val selectedLayer: String = "Weather", // Weather, Rain, Flood, Alerts, Dams, Quakes
    val isMapLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRadarLoading: Boolean = false,
    val radarHost: String? = null,
    val radarFrames: List<RadarFrame> = emptyList(),
    val selectedFrameIndex: Int = -1,
    val isPlayingRadar: Boolean = false,
    val radarTimestampText: String? = null,
    val radarAgeText: String? = null,
    val selectedLocationName: String = "",
    val selectedLatitude: Double = 0.0,
    val selectedLongitude: Double = 0.0,
    val currentLocationName: String = "",
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val weatherData: AnyLocationWeather? = null,
    val dams: List<DamMarkerData> = emptyList(),
    val earthquakes: List<EarthquakeMarkerData> = emptyList(),
    val floodData: FloodMapData? = null,
    val alerts: List<AlertMarkerData> = emptyList(),
    val areaAnalysis: AreaAnalysis? = null,
    val isAnalyzingArea: Boolean = false,
    val aiAnalysisAnswer: String? = null,
    val aiAnalysisSpeech: String? = null,
    val errorMessage: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val radarRepo = RadarRepository()
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var isInitialized = false

    fun initialize(lat: Double, lon: Double, locName: String) {
        if (isInitialized) return
        isInitialized = true

        _uiState.update {
            it.copy(
                selectedLocationName = locName,
                selectedLatitude = lat,
                selectedLongitude = lon,
                currentLocationName = locName,
                currentLatitude = lat,
                currentLongitude = lon
            )
        }

        loadLayerData("Weather")
        startRadarAutoRefresh()
    }

    fun selectLayer(layer: String) {
        if (_uiState.value.selectedLayer == layer) return
        _uiState.update { it.copy(selectedLayer = layer) }
        loadLayerData(layer)
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val currentLayer = _uiState.value.selectedLayer
            if (currentLayer == "Rain") {
                loadRadar(forceRefresh = true)
            }
            loadLayerData(currentLayer)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectMapLocation(lat: Double, lon: Double, preferredName: String? = null) {
        viewModelScope.launch {
            val name = preferredName ?: reverseGeocodePlace(lat, lon)
            _uiState.update {
                it.copy(
                    selectedLatitude = lat,
                    selectedLongitude = lon,
                    selectedLocationName = name,
                    areaAnalysis = null,
                    aiAnalysisAnswer = null,
                    aiAnalysisSpeech = null
                )
            }
            fetchWeatherForLocation(lat, lon, name)
        }
    }

    fun centerOnUserLocation(lat: Double, lon: Double, name: String) {
        selectMapLocation(lat, lon, name)
    }

    // =================================================================
    // RADAR PLAYBACK & TIMELINE CONTROLS
    // =================================================================

    fun playRadar() {
        val frames = _uiState.value.radarFrames
        if (frames.isEmpty()) return

        playbackJob?.cancel()
        _uiState.update { it.copy(isPlayingRadar = true) }

        playbackJob = viewModelScope.launch {
            while (isActive) {
                delay(900L)
                val state = _uiState.value
                val nextIdx = if (state.selectedFrameIndex >= state.radarFrames.size - 1) {
                    0
                } else {
                    state.selectedFrameIndex + 1
                }
                selectFrameByIndex(nextIdx)
            }
        }
    }

    fun pauseRadar() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlayingRadar = false) }
    }

    fun nextRadarFrame() {
        pauseRadar()
        val state = _uiState.value
        if (state.radarFrames.isEmpty()) return
        val nextIdx = (state.selectedFrameIndex + 1).coerceAtMost(state.radarFrames.size - 1)
        selectFrameByIndex(nextIdx)
    }

    fun prevRadarFrame() {
        pauseRadar()
        val state = _uiState.value
        if (state.radarFrames.isEmpty()) return
        val prevIdx = (state.selectedFrameIndex - 1).coerceAtLeast(0)
        selectFrameByIndex(prevIdx)
    }

    fun selectLatestRadarFrame() {
        pauseRadar()
        val state = _uiState.value
        if (state.radarFrames.isEmpty()) return
        selectFrameByIndex(state.radarFrames.size - 1)
    }

    fun selectFrameByIndex(index: Int) {
        val frames = _uiState.value.radarFrames
        if (index !in frames.indices) return
        val frame = frames[index]
        _uiState.update {
            it.copy(
                selectedFrameIndex = index,
                radarTimestampText = RadarRepository.formatFrameTime(frame.time),
                radarAgeText = RadarRepository.formatFrameAge(frame.time)
            )
        }
    }

    // =================================================================
    // LAYER DATA FETCHING
    // =================================================================

    private fun loadLayerData(layer: String) {
        val lat = _uiState.value.selectedLatitude
        val lon = _uiState.value.selectedLongitude
        val locName = _uiState.value.selectedLocationName

        when (layer) {
            "Weather" -> {
                fetchWeatherForLocation(lat, lon, locName)
            }
            "Rain" -> {
                loadRadar(forceRefresh = false)
            }
            "Dams" -> {
                fetchDams()
            }
            "Quakes" -> {
                fetchEarthquakes(lat, lon)
            }
            "Alerts" -> {
                fetchAlerts(lat, lon)
            }
            "Flood" -> {
                fetchFloodData(lat, lon)
            }
        }
    }

    private fun loadRadar(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRadarLoading = true) }
            val meta = radarRepo.getRadarMetadata(forceRefresh)
            if (meta != null && meta.frames.isNotEmpty()) {
                val latestIndex = meta.frames.size - 1
                val latestFrame = meta.frames[latestIndex]
                _uiState.update {
                    it.copy(
                        isRadarLoading = false,
                        radarHost = meta.host,
                        radarFrames = meta.frames,
                        selectedFrameIndex = latestIndex,
                        radarTimestampText = RadarRepository.formatFrameTime(latestFrame.time),
                        radarAgeText = RadarRepository.formatFrameAge(latestFrame.time)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRadarLoading = false,
                        radarAgeText = "Radar unavailable"
                    )
                }
            }
        }
    }

    private fun startRadarAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(300_000L) // 5 minutes
                if (_uiState.value.selectedLayer == "Rain") {
                    loadRadar(forceRefresh = true)
                }
            }
        }
    }

    private fun fetchWeatherForLocation(lat: Double, lon: Double, placeName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,wind_speed_10m,weather_code" +
                        "&hourly=precipitation_probability&forecast_days=1"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = JSONObject(body)
                val curr = root.optJSONObject("current")
                val hourly = root.optJSONObject("hourly")
                val wCode = curr?.optInt("weather_code", -1)?.takeIf { it >= 0 }

                val weather = AnyLocationWeather(
                    latitude = lat,
                    longitude = lon,
                    placeName = placeName,
                    temperature = curr?.optDouble("temperature_2m", Double.NaN)?.takeUnless { it.isNaN() },
                    apparentTemperature = curr?.optDouble("apparent_temperature", Double.NaN)?.takeUnless { it.isNaN() },
                    humidity = curr?.optDouble("relative_humidity_2m", Double.NaN)?.takeUnless { it.isNaN() },
                    windSpeed = curr?.optDouble("wind_speed_10m", Double.NaN)?.takeUnless { it.isNaN() },
                    pressure = curr?.optDouble("surface_pressure", Double.NaN)?.takeUnless { it.isNaN() },
                    rainProbability = hourly?.optJSONArray("precipitation_probability")?.optDouble(0, Double.NaN)?.takeUnless { it.isNaN() },
                    weatherCode = wCode,
                    conditionText = getWeatherConditionText(wCode)
                )
                _uiState.update { it.copy(weatherData = weather) }
            } catch (e: Exception) {
                Log.w("MapViewModel", "Weather fetch error: ${e.message}")
            }
        }
    }

    private fun fetchDams() {
        viewModelScope.launch {
            try {
                val res = DamClient.service.getDams(limit = 60)
                val items = res.reservoirs.orEmpty().mapNotNull { item ->
                    val lat = item.latitude
                    val lon = item.longitude
                    val name = item.name
                    if (lat != null && lon != null && !name.isNullOrBlank()) {
                        DamMarkerData(
                            name = name,
                            latitude = lat,
                            longitude = lon,
                            state = item.state,
                            storagePercent = item.storage_percent,
                            level = item.current_level_m
                        )
                    } else null
                }
                _uiState.update { it.copy(dams = items) }
            } catch (e: Exception) {
                Log.w("MapViewModel", "Dam fetch error: ${e.message}")
            }
        }
    }

    private fun fetchEarthquakes(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${BackendConfig.BASE_URL_NO_SLASH}/earthquakes?latitude=$lat&longitude=$lon&radius_km=2000&limit=30")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val items = mutableListOf<EarthquakeMarkerData>()
                    val quakes = root.optJSONArray("earthquakes") ?: root.optJSONArray("data")
                    if (quakes != null) {
                        for (i in 0 until quakes.length()) {
                            val q = quakes.optJSONObject(i) ?: continue
                            items.add(
                                EarthquakeMarkerData(
                                    latitude = q.optDouble("latitude"),
                                    longitude = q.optDouble("longitude"),
                                    magnitude = q.optDouble("magnitude", Double.NaN).takeUnless { it.isNaN() },
                                    place = q.optString("place"),
                                    depthKm = q.optDouble("depth_km", Double.NaN).takeUnless { it.isNaN() }
                                )
                            )
                        }
                    }
                    _uiState.update { it.copy(earthquakes = items) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("MapViewModel", "Quakes fetch error: ${e.message}")
            }
        }
    }

    private fun fetchAlerts(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val res = FeatureClient.api.getAlerts(latitude = lat, longitude = lon)
                val list = res.alerts.orEmpty().map {
                    AlertMarkerData(
                        latitude = lat,
                        longitude = lon,
                        title = it.type ?: "Weather Alert",
                        severity = it.severity ?: "INFO",
                        description = it.message
                    )
                }
                _uiState.update { it.copy(alerts = list) }
            } catch (e: Exception) {
                Log.w("MapViewModel", "Alerts fetch error: ${e.message}")
            }
        }
    }

    private fun fetchFloodData(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${BackendConfig.BASE_URL_NO_SLASH}/flood/risk?latitude=$lat&longitude=$lon")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val data = FloodMapData(
                        latitude = lat,
                        longitude = lon,
                        risk = root.optString("risk", root.optString("flood_risk", "LOW")),
                        currentWaterLevel = root.optDouble("current_water_level", Double.NaN).takeUnless { it.isNaN() },
                        warningLevel = root.optDouble("warning_level", Double.NaN).takeUnless { it.isNaN() },
                        dangerLevel = root.optDouble("danger_level", Double.NaN).takeUnless { it.isNaN() }
                    )
                    _uiState.update { it.copy(floodData = data) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("MapViewModel", "Flood fetch error: ${e.message}")
            }
        }
    }

    private suspend fun reverseGeocodePlace(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2&zoom=10")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "WeatherGPT/1.0 (contact@weathergpt.app; Android)")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val root = JSONObject(body)
            val address = root.optJSONObject("address")
            address?.optString("city")?.takeIf { it.isNotBlank() }
                ?: address?.optString("town")?.takeIf { it.isNotBlank() }
                ?: address?.optString("village")?.takeIf { it.isNotBlank() }
                ?: address?.optString("state_district")?.takeIf { it.isNotBlank() }
                ?: root.optString("name", "Selected Location")
        } catch (_: Exception) {
            "Selected Location"
        }
    }

    // =================================================================
    // ANALYZE THIS AREA (LOCAL TELEMETRY SUMMARY - NO CHATBOT ESSAYS)
    // =================================================================

    fun analyzeCurrentArea() {
        val state = _uiState.value
        val lat = state.selectedLatitude
        val lon = state.selectedLongitude
        val locName = state.selectedLocationName.ifBlank { "Selected Location" }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingArea = true, errorMessage = null) }

            if (state.weatherData == null) {
                fetchWeatherForLocation(lat, lon, locName)
            }

            val weather = _uiState.value.weatherData
            val rainRisk = weather?.rainProbability ?: 0.0
            val overallRisk = when {
                rainRisk >= 75.0 -> "HIGH"
                rainRisk >= 40.0 -> "MODERATE"
                else -> "SAFE"
            }

            val localAnalysis = AreaAnalysis(
                latitude = lat,
                longitude = lon,
                placeName = locName,
                temperature = weather?.temperature,
                rainfallProbability = weather?.rainProbability,
                floodRisk = state.floodData?.risk ?: "LOW",
                overallRisk = overallRisk,
                recommendation = "Weather telemetry for $locName"
            )

            _uiState.update {
                it.copy(
                    isAnalyzingArea = false,
                    areaAnalysis = localAnalysis,
                    aiAnalysisAnswer = null,
                    aiAnalysisSpeech = null
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        autoRefreshJob?.cancel()
    }
}
