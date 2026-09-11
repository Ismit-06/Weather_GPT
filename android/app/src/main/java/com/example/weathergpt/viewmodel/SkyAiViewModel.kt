package com.example.weathergpt.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.MetWeatherClient
import com.example.weathergpt.data.MetWeatherResponse
import com.example.weathergpt.data.SkyAiClient
import com.example.weathergpt.data.SkyAnalysisResponse
import com.example.weathergpt.data.radar.RadarMetadata
import com.example.weathergpt.data.radar.RadarRepository
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.location.SelectedLocation
import com.example.weathergpt.sky.SensorOrientationData
import com.example.weathergpt.sky.SkyDecisionState
import com.example.weathergpt.sky.SkyEvaluationResult
import com.example.weathergpt.sky.SkySensorsManager
import com.example.weathergpt.sky.SkyVisionAnalyzer
import com.example.weathergpt.sky.SkyWeatherFusionEngine
import com.example.weathergpt.sky.SkyWeatherFusionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.weathergpt.data.VisualCloudIntelligenceResponse
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

data class SkyDebugTelemetry(
    val requestId: String = "",
    val modelVersion: String = "",
    val confidence: Double = 0.0,
    val latencyMs: Long = 0,
    val sceneType: String = "",
    val skyDetected: Boolean = false,
    val networkStatus: String = "OK"
)

class SkyAiViewModel(application: Application) : AndroidViewModel(application) {

    private val visionAnalyzer = SkyVisionAnalyzer()
    private val sensorsManager = SkySensorsManager(application.applicationContext)
    private val radarRepo = RadarRepository()

    private val _liveDecisionState = MutableStateFlow(SkyDecisionState.UNCERTAIN)
    val liveDecisionState: StateFlow<SkyDecisionState> = _liveDecisionState.asStateFlow()

    private val _liveEvaluation = MutableStateFlow<SkyEvaluationResult?>(null)
    val liveEvaluation: StateFlow<SkyEvaluationResult?> = _liveEvaluation.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _fusionReport = MutableStateFlow<SkyWeatherFusionReport?>(null)
    val fusionReport: StateFlow<SkyWeatherFusionReport?> = _fusionReport.asStateFlow()

    private val _currentLocation = MutableStateFlow<SelectedLocation?>(null)
    val currentLocation: StateFlow<SelectedLocation?> = _currentLocation.asStateFlow()

    private val _weatherData = MutableStateFlow<MetWeatherResponse?>(null)
    val weatherData: StateFlow<MetWeatherResponse?> = _weatherData.asStateFlow()

    private val _radarMetadata = MutableStateFlow<RadarMetadata?>(null)
    val radarMetadata: StateFlow<RadarMetadata?> = _radarMetadata.asStateFlow()

    private val _debugTelemetry = MutableStateFlow<SkyDebugTelemetry?>(null)
    val debugTelemetry: StateFlow<SkyDebugTelemetry?> = _debugTelemetry.asStateFlow()

    val sensorData: StateFlow<SensorOrientationData> = sensorsManager.sensorData

    // Throttling live preview analysis
    private var lastAnalysisTimestamp = 0L
    private val SKY_AI_ANALYSIS_INTERVAL_MS = 1500L
    private var isFrameProcessing = false

    // Stale Request Protection: sequence numbers & coroutine job cancellation
    private val requestSequenceCounter = AtomicLong(0)
    private var activeAnalysisJob: Job? = null

    fun startSensors() {
        sensorsManager.start()
        refreshLocationAndWeather()
    }

    fun stopSensors() {
        sensorsManager.stop()
    }

    fun refreshLocationAndWeather() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val loc = LocationStore.getLocation(context)
            _currentLocation.value = loc

            try {
                val weather = MetWeatherClient.getFastWeather(loc.latitude, loc.longitude, context)
                _weatherData.value = weather
            } catch (e: Exception) {
                Log.e("SkyAiViewModel", "Error fetching weather telemetry", e)
            }

            try {
                val radar = radarRepo.getRadarMetadata()
                _radarMetadata.value = radar
            } catch (e: Exception) {
                Log.e("SkyAiViewModel", "Error fetching radar metadata", e)
            }
        }
    }

    /**
     * Called on each frame from CameraX ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST).
     * The ImageProxy is converted and closed immediately; heavy processing is on background coroutine.
     */
     fun processLiveFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTimestamp < SKY_AI_ANALYSIS_INTERVAL_MS || isFrameProcessing) {
            imageProxy.close()
            return
        }

        isFrameProcessing = true
        lastAnalysisTimestamp = now

        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = imageProxyToBitmap(imageProxy)
            imageProxy.close() // ALWAYS close immediately

            if (bitmap != null) {
                try {
                    val currentSensors = sensorsManager.sensorData.value
                    val result = visionAnalyzer.analyzeFrame(bitmap, currentSensors)
                    _liveDecisionState.value = result.state
                    _liveEvaluation.value = result
                } catch (e: Exception) {
                    Log.e("SkyAiViewModel", "Live frame processing error", e)
                } finally {
                    isFrameProcessing = false
                }
            } else {
                isFrameProcessing = false
            }
        }
    }

    /**
     * Executes full capture analysis:
     * 1. Downscales image for fast on-device processing.
     * 2. Evaluates on-device vision analyzer immediately with single-frame mode.
     * 3. Instantly synthesizes and displays the complete meteorological fusion prediction (sub-second!).
     * 4. Asynchronously refines with cloud model if available without blocking the UI.
     */
    fun analyzeCapturedBitmap(bitmap: Bitmap, onResultReady: () -> Unit = {}) {
        val requestId = requestSequenceCounter.incrementAndGet()

        // Cancel any pending stale analysis job
        activeAnalysisJob?.cancel()

        activeAnalysisJob = viewModelScope.launch {
            _isAnalyzing.value = true
            val startTime = System.currentTimeMillis()

            // 1. Efficiently downscale frame to 1080p max dimension for fast processing
            val analysisBitmap = withContext(Dispatchers.Default) {
                downscaleForAnalysis(bitmap, 1080)
            }

            // 2. Fast on-device ML vision analyzer (with single-frame evaluation enabled)
            val currentSensors = sensorsManager.sensorData.value
            val localEval = withContext(Dispatchers.Default) {
                visionAnalyzer.analyzeFrame(analysisBitmap, currentSensors, isSingleCapture = true)
            }

            // If local disqualification occurred (fabric, ceiling, indoor, extreme darkness)
            if (!localEval.state.isAnalyzable) {
                _liveDecisionState.value = localEval.state
                _liveEvaluation.value = localEval
                _isAnalyzing.value = false
                _fusionReport.value = null
                onResultReady()
                return@launch
            }

            // Valid sky confirmed on-device!
            _liveDecisionState.value = SkyDecisionState.VALID_SKY
            _liveEvaluation.value = localEval

            // 3. Generate meteorological fusion prediction IMMEDIATELY so user gets instant results!
            val initialReport = withContext(Dispatchers.Default) {
                SkyWeatherFusionEngine.fuse(
                    evaluation = localEval,
                    weatherResponse = _weatherData.value,
                    radarMetadata = _radarMetadata.value,
                    location = _currentLocation.value,
                    skyServerResponse = null
                )
            }

            // Deliver instant prediction to UI!
            _fusionReport.value = initialReport
            _isAnalyzing.value = false
            onResultReady()

            // 4. Optional asynchronous cloud refinement (strict 2.5s timeout, non-blocking)
            try {
                val jpegBytes = withContext(Dispatchers.Default) {
                    val bos = ByteArrayOutputStream()
                    analysisBitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    bos.toByteArray()
                }

                val visualCloudResp = withTimeoutOrNull(3000L) {
                    withContext(Dispatchers.IO) {
                        try {
                            val loc = _currentLocation.value
                            val latBody = loc?.latitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
                            val lonBody = loc?.longitude?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
                            val locBody = loc?.name?.toRequestBody("text/plain".toMediaTypeOrNull())
                            val part = SkyAiClient.createMultipartImage(jpegBytes, "sky_frame_$requestId.jpg")

                            SkyAiClient.api.analyzeVisualCloud(
                                image = part,
                                latitude = latBody,
                                longitude = lonBody,
                                locationName = locBody
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                if (visualCloudResp != null && requestId == requestSequenceCounter.get()) {
                    val latency = System.currentTimeMillis() - startTime
                    _debugTelemetry.value = SkyDebugTelemetry(
                        requestId = "sky_$requestId",
                        modelVersion = "Gemma 4 26B A4B",
                        confidence = visualCloudResp.observation.confidence,
                        latencyMs = latency,
                        sceneType = visualCloudResp.observation.dominant_cloud_type,
                        skyDetected = visualCloudResp.observation.analysis_status == "success",
                        networkStatus = "OK"
                    )

                    val refinedReport = withContext(Dispatchers.Default) {
                        SkyWeatherFusionEngine.fuse(
                            evaluation = localEval,
                            weatherResponse = _weatherData.value,
                            radarMetadata = _radarMetadata.value,
                            location = _currentLocation.value,
                            visualCloudResponse = visualCloudResp
                        )
                    }
                    _fusionReport.value = refinedReport
                }
            } catch (e: Exception) {
                Log.d("SkyAiViewModel", "Background cloud refinement skipped: ${e.message}")
            }
        }
    }

    private fun downscaleForAnalysis(bitmap: Bitmap, maxDim: Int = 1080): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(width, height)
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun clearResult() {
        _fusionReport.value = null
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val bitmap = image.toBitmap()
            val rotationDegrees = image.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("SkyAiViewModel", "imageProxyToBitmap error: ${e.message}")
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSensors()
    }
}
