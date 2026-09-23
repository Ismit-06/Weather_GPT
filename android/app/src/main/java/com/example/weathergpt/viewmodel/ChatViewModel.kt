package com.weathergpt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weathergpt.app.data.AgentState
import com.weathergpt.app.data.ChatClient
import com.weathergpt.app.data.ChatMessage
import com.weathergpt.app.data.ChatStore
import com.weathergpt.app.data.ChatWeatherRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


data class ChatUiMessage(
    val role: String,
    val content: String
)


data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val detectedLanguage: String? = null,
    val detectedLanguageCode: String? = null,
    val latestSpeechText: String? = null
)


class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context =
        application.applicationContext

    private val _uiState =
        MutableStateFlow(
            ChatUiState(
                messages = emptyList()
            )
        )

    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    // Structured context returned by the backend.
    private var agentState =
        AgentState()


    fun sendMessage(
        question: String,
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        language: String
    ) {

        val text =
            question.trim()

        if (text.isEmpty()) {
            return
        }

        // Track and learn activity preferences (Opt-in)
        try {
            com.weathergpt.app.data.UserPreferencesStore.recordActivityQuery(context, text)
        } catch (_: Exception) {}

        val currentState = _uiState.value

        // Maintain conversation history for multi-turn context
        val existingMessages = currentState.messages
        val historyToSend = existingMessages.takeLast(6).map {
            ChatMessage(role = it.role, content = it.content)
        }

        // Show user's question and indicate thinking
        val updatedMessages = existingMessages + ChatUiMessage(role = "user", content = text)
        _uiState.value = currentState.copy(
            messages = updatedMessages,
            isLoading = true,
            error = null
        )

        val selectedEngine = com.weathergpt.app.location.LlmEngineStore.getEngine(context)
        val requestAgentState = if (!locationName.isNullOrBlank()) {
            agentState.copy(
                location_name = locationName,
                location_latitude = latitude,
                location_longitude = longitude,
                llm_engine = selectedEngine
            )
        } else {
            agentState.copy(
                llm_engine = selectedEngine
            )
        }

        viewModelScope.launch {
            try {
                // Query Backend AI with conversational history and location context
                val response = ChatClient.api.askWeather(
                    ChatWeatherRequest(
                        question = text,
                        latitude = latitude,
                        longitude = longitude,
                        language = language,
                        history = historyToSend,
                        agent_state = requestAgentState
                    )
                )

                if (response.agent_state != null) {
                    agentState = response.agent_state
                }

                val displayText = response.display_text
                    ?.trim()
                    .takeUnless { it.isNullOrEmpty() }
                    ?: response.clarification
                        ?.trim()
                        .takeUnless { it.isNullOrEmpty() }
                    ?: response.answer
                        ?.trim()
                        .takeUnless { it.isNullOrEmpty() }

                if (!displayText.isNullOrEmpty()) {
                    val speechText = response.speech_text
                        ?.trim()
                        .takeUnless { it.isNullOrEmpty() }
                        ?: displayText

                    val finalMessages = updatedMessages + ChatUiMessage(role = "assistant", content = displayText)

                    _uiState.value = ChatUiState(
                        messages = finalMessages,
                        isLoading = false,
                        error = null,
                        detectedLanguage = response.language ?: language,
                        detectedLanguageCode = response.language_code ?: language,
                        latestSpeechText = speechText
                    )
                    return@launch
                } else {
                    throw IllegalStateException("Empty response from AI assistant.")
                }
            } catch (cloudErr: Exception) {
                // If cloud is unavailable or timed out, gracefully fall back to local instant weather intelligence
                try {
                    val cachedWeather = com.weathergpt.app.data.MetWeatherClient.getCachedWeather(latitude, longitude, context)
                        ?: com.weathergpt.app.data.MetWeatherClient.getFastWeather(latitude, longitude, context)

                    val fallback = com.weathergpt.app.data.FastWeatherAssistant.generateInstantResponse(
                        query = text,
                        weather = cachedWeather,
                        locationName = locationName,
                        languageCode = language
                    )

                    if (fallback != null) {
                        val fbMessages = updatedMessages + ChatUiMessage(role = "assistant", content = fallback.first)
                        _uiState.value = ChatUiState(
                            messages = fbMessages,
                            isLoading = false,
                            error = null,
                            detectedLanguage = language,
                            detectedLanguageCode = language,
                            latestSpeechText = fallback.second
                        )
                        return@launch
                    }
                } catch (_: Exception) {}

                val errorMessage = when (cloudErr) {
                    is java.net.SocketTimeoutException ->
                        "Connection timed out. Please tap retry or check internet."
                    is java.net.UnknownHostException ->
                        "Unable to reach WeatherGPT. Please check your internet connection."
                    else ->
                        cloudErr.message ?: "Unable to contact WeatherGPT."
                }

                _uiState.value = ChatUiState(
                    messages = updatedMessages,
                    isLoading = false,
                    error = errorMessage,
                    detectedLanguage = currentState.detectedLanguage,
                    detectedLanguageCode = currentState.detectedLanguageCode
                )
            }
        }
    }


    fun updateVoiceInteraction(
        userQuery: String,
        answerText: String,
        speechText: String? = null,
        language: String?,
        languageCode: String?
    ) {
        val instantMessages = listOf(
            ChatUiMessage(
                role = "user",
                content = userQuery
            ),
            ChatUiMessage(
                role = "assistant",
                content = answerText
            )
        )
        _uiState.value = ChatUiState(
            messages = instantMessages,
            isLoading = false,
            error = null,
            detectedLanguage = language,
            detectedLanguageCode = languageCode,
            latestSpeechText = speechText ?: answerText
        )
    }

    fun setVoiceProcessing(userQuery: String) {
        _uiState.value = ChatUiState(
            messages = listOf(
                ChatUiMessage(
                    role = "user",
                    content = userQuery
                )
            ),
            isLoading = true,
            error = null
        )
    }

    fun analyzeSkyImage(
        bitmap: android.graphics.Bitmap,
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        language: String = "English"
    ) {
        val currentState = _uiState.value
        val updatedMessages = currentState.messages + ChatUiMessage(
            role = "user",
            content = "Analyze this sky photograph for cloud patterns and short-term weather."
        )
        _uiState.value = currentState.copy(
            messages = updatedMessages,
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val bos = java.io.ByteArrayOutputStream()
                val scaled = if (maxOf(bitmap.width, bitmap.height) > 1280) {
                    val ratio = 1280f / maxOf(bitmap.width, bitmap.height)
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt().coerceAtLeast(1),
                        (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else bitmap
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                val bytes = bos.toByteArray()

                val part = com.weathergpt.app.data.SkyAiClient.createMultipartImage(bytes, "chat_sky_upload.jpg")
                val latBody = latitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val lonBody = longitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val locBody = locationName?.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = language.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = com.weathergpt.app.data.SkyAiClient.api.analyzeVisualCloud(
                    image = part,
                    latitude = latBody,
                    longitude = lonBody,
                    locationName = locBody,
                    language = langBody
                )

                val displayText = response.explanation.ifBlank {
                    "Sky Analysis: ${response.observation.dominant_cloud_type.replace('_', ' ').capitalize()}\n${response.assessment.next_1_hour}"
                }
                val speechText = "Sky analysis: ${response.observation.dominant_cloud_type.replace('_', ' ')}. ${response.assessment.next_1_hour}"

                _uiState.value = ChatUiState(
                    messages = updatedMessages + ChatUiMessage(role = "assistant", content = displayText),
                    isLoading = false,
                    error = null,
                    detectedLanguage = language,
                    detectedLanguageCode = language,
                    latestSpeechText = speechText
                )
            } catch (e: Exception) {
                _uiState.value = ChatUiState(
                    messages = updatedMessages + ChatUiMessage(
                        role = "assistant",
                        content = "Unable to analyze this image. Please ensure it is a clear photograph of an open sky and try again."
                    ),
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearChat() {

        agentState =
            AgentState()

        ChatStore.clear(context)

        _uiState.value =
            ChatUiState()
    }
}
