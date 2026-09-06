package com.example.weathergpt.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.example.weathergpt.data.BackendConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Production-grade Real-time Multilingual Voice Assistant Manager.
 * 
 * Supports:
 * 1. Low-latency WebSocket streaming (/ws/voice) with 16kHz 16-bit Mono PCM
 * 2. Real-time audio RMS metering for living 3D Orb visualizer
 * 3. Streaming neural TTS audio chunk playback with queuing
 * 4. Sub-200ms Barge-In / Interruption handling
 * 5. Display Text != Speech Text separation (Zero emoji/markdown read aloud)
 * 6. Multi-language detection and local fallback when offline
 */
class VoiceAssistantManager(private val context: Context) {

    private val tag = "RealtimeVoice"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State flows for UI binding
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    // Real-time Audio Capture (16kHz 16-bit Mono PCM)
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // WebSocket connection
    private var webSocket: WebSocket? = null
    private var isWsConnected = false
    private var activeSessionId: String? = null

    // Audio Playback Queue
    private val audioChunkQueue = ConcurrentLinkedQueue<File>()
    private var currentPlayingPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var rmsSimulationJob: Job? = null

    // Callbacks
    var onAssistantAnswerReceived: ((displayText: String, speechText: String, lang: String?, langCode: String?) -> Unit)? = null
    var onUserTranscriptFinal: ((transcript: String, langCode: String?) -> Unit)? = null
    var onErrorOccurred: ((errorMessage: String) -> Unit)? = null

    // Fallbacks
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        initFallbackTts()
    }

    private fun initFallbackTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(0.96f)
                    isTtsInitialized = true
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    startRmsSimulation()
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopRmsSimulation()
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopRmsSimulation()
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Fallback TTS init error", e)
        }
    }

    /**
     * Start real-time voice conversation session over WebSocket
     */
    @SuppressLint("MissingPermission")
    fun startRealtimeVoice(
        latitude: Double,
        longitude: Double,
        locationName: String,
        language: String
    ) {
        stopPlayback()
        stopAudioRecording()

        connectWebSocket(latitude, longitude, locationName, language) { success ->
            if (success) {
                startPcmAudioRecording()
            } else {
                Log.w(tag, "WebSocket connect failed, falling back to standard recognition")
                startSpeechRecognizerFallback(language)
            }
        }
    }

    private fun connectWebSocket(
        latitude: Double,
        longitude: Double,
        locationName: String,
        language: String,
        onConnected: (Boolean) -> Unit
    ) {
        closeWebSocket()

        try {
            val request = Request.Builder()
                .url(BackendConfig.WS_URL)
                .build()

            webSocket = BackendConfig.okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(tag, "Real-time Voice WebSocket Connected")
                    isWsConnected = true

                    // Send session start configuration
                    val startMeta = JSONObject().apply {
                        put("type", "voice_session_start")
                        put("latitude", latitude)
                        put("longitude", longitude)
                        put("location_name", locationName)
                        put("language", if (language.equals("Auto", ignoreCase = true)) "auto" else language)
                    }
                    ws.send(startMeta.toString())
                    coroutineScope.launch { onConnected(true) }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleWsJsonMessage(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(tag, "WebSocket connection failure: ${t.message}")
                    isWsConnected = false
                    coroutineScope.launch {
                        onConnected(false)
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket Closed: $code $reason")
                    isWsConnected = false
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Error initiating WebSocket", e)
            onConnected(false)
        }
    }

    private fun handleWsJsonMessage(jsonText: String) {
        try {
            val data = JSONObject(jsonText)
            val type = data.optString("type")

            when (type) {
                "session_started" -> {
                    activeSessionId = data.optString("session_id")
                    _isListening.value = true
                    _isProcessing.value = false
                    Log.d(tag, "Session started: $activeSessionId")
                }

                "speech_started" -> {
                    // User has begun speaking into microphone
                    _isListening.value = true
                    _isProcessing.value = false
                }

                "transcript_final" -> {
                    val text = data.optString("text")
                    val langCode = data.optString("language_code")
                    _currentTranscript.value = text
                    _isListening.value = false
                    _isProcessing.value = true
                    coroutineScope.launch {
                        onUserTranscriptFinal?.invoke(text, langCode)
                    }
                }

                "assistant_text" -> {
                    val displayText = data.optString("display_text")
                    val speechText = data.optString("speech_text")
                    val lang = data.optString("language")
                    val langCode = data.optString("language_code")

                    _isProcessing.value = false
                    coroutineScope.launch {
                        onAssistantAnswerReceived?.invoke(displayText, speechText, lang, langCode)
                    }
                }

                "audio_chunk" -> {
                    val rawB64 = data.optString("data")
                    val isLast = data.optBoolean("is_last_chunk", false)
                    if (rawB64.isNotEmpty()) {
                        val audioBytes = Base64.decode(rawB64, Base64.DEFAULT)
                        enqueueAudioChunk(audioBytes, isLast)
                    }
                }

                "speech_finished" -> {
                    Log.d(tag, "Server finished generating speech chunks")
                }

                "interrupted" -> {
                    stopPlayback()
                }

                "error" -> {
                    val msg = data.optString("message", "Voice processing error")
                    _isListening.value = false
                    _isProcessing.value = false
                    coroutineScope.launch {
                        onErrorOccurred?.invoke(msg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing WebSocket JSON", e)
        }
    }

    /**
     * Records 16kHz 16-bit Mono PCM audio and streams binary chunks over WebSocket
     */
    @SuppressLint("MissingPermission")
    private fun startPcmAudioRecording() {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, 4096)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            _isListening.value = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048) // 64ms chunk at 16kHz 16-bit Mono
                while (isActive && _isListening.value && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        // Calculate real-time RMS amplitude for visual orb
                        val rms = calculatePcmRms(buffer, readBytes)
                        withContext(Dispatchers.Main) {
                            _rmsLevel.value = (rms / 32768f * 3.5f).coerceIn(0f, 1f)
                        }

                        // Send binary audio chunk to server
                        if (isWsConnected && webSocket != null) {
                            val byteString = buffer.toByteString(0, readBytes)
                            webSocket?.send(byteString)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Audio recording failed", e)
            _isListening.value = false
        }
    }

    private fun calculatePcmRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        return sqrt(sum / sampleCount).toFloat()
    }

    /**
     * Sub-200ms Barge-In: Interrupts playback, cancels ongoing speech, and resets state.
     */
    fun interrupt() {
        stopPlayback()
        if (isWsConnected && webSocket != null) {
            val interruptMsg = JSONObject().apply {
                put("type", "interrupt")
            }
            webSocket?.send(interruptMsg.toString())
        }
    }

    private fun enqueueAudioChunk(audioBytes: ByteArray, isLast: Boolean) {
        try {
            val chunkFile = File(context.cacheDir, "speech_chunk_${System.currentTimeMillis()}_${Random.nextInt(1000)}.mp3")
            FileOutputStream(chunkFile).use { it.write(audioBytes) }
            audioChunkQueue.offer(chunkFile)

            if (!_isSpeaking.value) {
                startAudioChunkPlaybackQueue()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error enqueuing audio chunk", e)
        }
    }

    private fun startAudioChunkPlaybackQueue() {
        playbackJob?.cancel()
        playbackJob = coroutineScope.launch {
            _isSpeaking.value = true
            startRmsSimulation()

            while (isActive && (!audioChunkQueue.isEmpty() || _isProcessing.value)) {
                val nextFile = audioChunkQueue.poll()
                if (nextFile == null) {
                    delay(50)
                    continue
                }

                val completed = kotlinx.coroutines.CompletableDeferred<Boolean>()
                withContext(Dispatchers.Main) {
                    try {
                        currentPlayingPlayer?.release()
                        currentPlayingPlayer = MediaPlayer().apply {
                            setDataSource(nextFile.absolutePath)
                            setOnCompletionListener {
                                try { nextFile.delete() } catch (_: Exception) {}
                                completed.complete(true)
                            }
                            setOnErrorListener { _, _, _ ->
                                try { nextFile.delete() } catch (_: Exception) {}
                                completed.complete(false)
                                true
                            }
                            prepare()
                            start()
                        }
                    } catch (e: Exception) {
                        try { nextFile.delete() } catch (_: Exception) {}
                        completed.complete(false)
                    }
                }

                completed.await()
            }

            _isSpeaking.value = false
            stopRmsSimulation()
        }
    }

    fun stopAudioRecording() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _rmsLevel.value = 0f
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        while (!audioChunkQueue.isEmpty()) {
            val file = audioChunkQueue.poll()
            try { file?.delete() } catch (_: Exception) {}
        }
        try {
            currentPlayingPlayer?.stop()
            currentPlayingPlayer?.release()
        } catch (_: Exception) {}
        currentPlayingPlayer = null

        try {
            tts?.stop()
        } catch (_: Exception) {}

        stopRmsSimulation()
        _isSpeaking.value = false
        _isProcessing.value = false
    }

    private fun closeWebSocket() {
        try {
            webSocket?.close(1000, "Closed by client")
        } catch (_: Exception) {}
        webSocket = null
        isWsConnected = false
    }

    // ----------------------------------------------------
    // Fallback Methods for Offline / Disconnected States
    // ----------------------------------------------------
    private fun startSpeechRecognizerFallback(languageCode: String?) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorOccurred?.invoke("Speech recognition is not available.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsLevel.value = (rmsdB.coerceIn(0f, 10f) / 10f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        onErrorOccurred?.invoke("Speech recognition error ($error)")
                    }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) {
                            onUserTranscriptFinal?.invoke(text, languageCode)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                val isAuto = languageCode.isNullOrBlank() || languageCode.equals("Auto", ignoreCase = true)
                val recognitionLocale = if (isAuto) Locale.getDefault().toLanguageTag().ifBlank { "en-IN" } else languageCode
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onErrorOccurred?.invoke("Microphone error: ${e.message}")
        }
    }

    fun speak(text: String, languageCode: String? = null) {
        stopPlayback()
        val cleanSpeech = cleanMarkdownForSpeech(text)
        if (cleanSpeech.isBlank()) return

        coroutineScope.launch {
            try {
                _isSpeaking.value = true
                startRmsSimulation()

                val audioFile = withContext(Dispatchers.IO) {
                    val jsonBody = JSONObject().apply {
                        put("text", cleanSpeech)
                        put("language", languageCode)
                        put("format", "mp3")
                    }

                    val request = Request.Builder()
                        .url("${BackendConfig.BASE_URL_NO_SLASH}/api/v1/tts")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = BackendConfig.okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw IllegalStateException("TTS error ${response.code}")

                    val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty audio")
                    val tempFile = File(context.cacheDir, "speech_fallback_${System.currentTimeMillis()}.mp3")
                    FileOutputStream(tempFile).use { it.write(bytes) }
                    tempFile
                }

                withContext(Dispatchers.Main) {
                    currentPlayingPlayer?.release()
                    currentPlayingPlayer = MediaPlayer().apply {
                        setDataSource(audioFile.absolutePath)
                        setOnCompletionListener {
                            _isSpeaking.value = false
                            stopRmsSimulation()
                            try { audioFile.delete() } catch (_: Exception) {}
                        }
                        setOnErrorListener { _, _, _ ->
                            _isSpeaking.value = false
                            stopRmsSimulation()
                            try { audioFile.delete() } catch (_: Exception) {}
                            speakFallbackTts(cleanSpeech, languageCode)
                            true
                        }
                        prepare()
                        start()
                    }
                }
            } catch (e: Exception) {
                speakFallbackTts(cleanSpeech, languageCode)
            }
        }
    }

    private fun speakFallbackTts(cleanText: String, languageCode: String?) {
        try {
            val locale = if (languageCode.isNullOrBlank() || languageCode.equals("Auto", ignoreCase = true)) {
                Locale.getDefault()
            } else {
                Locale.forLanguageTag(languageCode)
            }
            tts?.language = locale
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "WeatherGPT_Speak_${System.currentTimeMillis()}")
            _isSpeaking.value = true
            startRmsSimulation()
        } catch (e: Exception) {
            _isSpeaking.value = false
            stopRmsSimulation()
        }
    }

    private fun startRmsSimulation() {
        rmsSimulationJob?.cancel()
        rmsSimulationJob = coroutineScope.launch {
            while (isActive && _isSpeaking.value) {
                val base = 0.35f + Random.nextFloat() * 0.40f
                _rmsLevel.value = base
                delay(80)
            }
            _rmsLevel.value = 0f
        }
    }

    private fun stopRmsSimulation() {
        rmsSimulationJob?.cancel()
        rmsSimulationJob = null
        _rmsLevel.value = 0f
    }

    fun cleanMarkdownForSpeech(markdown: String): String {
        var text = markdown
        if (text.contains("<think>")) {
            text = text.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val filtered = lines.filterNot { line ->
            val l = line.lowercase()
            l.startsWith("the user is asking") ||
            l.startsWith("let me look at") ||
            l.startsWith("looking at the data") ||
            l.startsWith("wait, let me reconsider") ||
            l.startsWith("hmm") ||
            l.startsWith("i think i'm") ||
            l.startsWith("let me just respond") ||
            l.startsWith("i'll respond in") ||
            l.startsWith("the user has been communicating") ||
            l.contains("overthinking") ||
            l.contains("respond naturally") ||
            l.contains("weather advisory or committee") ||
            (l.contains("could it be") && l.endsWith("?")) ||
            l.startsWith("the weather data provided is")
        }
        var content = if (filtered.isNotEmpty()) filtered.joinToString(". ") else text

        // 1. Remove all Emoji characters completely
        content = content.replace(Regex("[\\p{So}\\p{Cn}\\p{Cs}\\x{1F300}-\\x{1F9FF}\\x{2600}-\\x{27BF}\\x{FE00}-\\x{FE0F}]"), "")
        content = content.replace(Regex("[\uD83C-\uDBFF\uDC00-\uDFFF]"), "")

        // 2. Expand units phonetically
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*°\\s*C\\b"), "$1 degrees Celsius")
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*°\\s*F\\b"), "$1 degrees Fahrenheit")
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*°\\b"), "$1 degrees")
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*km/h\\b"), "$1 kilometers per hour")
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*m/s\\b"), "$1 meters per second")
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*mm\\b"), "$1 millimeters")
        content = content.replace(Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*%\\b"), "$1 percent")

        // 3. Strip markdown symbols
        content = content.replace(Regex("(?m)^\\s*[-*•]\\s*"), "")
        content = content.replace(Regex("[#*`_~>\\[\\]()]"), " ")
        content = content.replace(Regex("\\bhttps?://\\S+"), "")

        // 4. Clean whitespace
        content = content.replace(Regex("\\s+"), " ")
        content = content.replace(Regex("\\.{2,}"), ".")
        content = content.replace(Regex("\\s+([.,!?])"), "$1")
        return content.trim()
    }

    fun stopSpeaking() {
        stopPlayback()
    }

    fun stopListening() {
        stopAudioRecording()
    }

    fun destroy() {
        stopAudioRecording()
        stopPlayback()
        closeWebSocket()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
    }
}
