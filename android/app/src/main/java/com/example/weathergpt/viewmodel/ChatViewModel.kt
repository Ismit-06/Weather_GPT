package com.example.weathergpt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.AgentState
import com.example.weathergpt.data.ChatClient
import com.example.weathergpt.data.ChatMessage
import com.example.weathergpt.data.ChatStore
import com.example.weathergpt.data.ChatWeatherRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class ChatUiMessage(
    val role: String,
    val content: String
)


data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val detectedLanguage: String? = null,
    val detectedLanguageCode: String? = null
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

        val currentState =
            _uiState.value

        // Only keep the current active question (no accumulating previous questions)
        val currentQuestionList = listOf(
            ChatUiMessage(
                role = "user",
                content = text
            )
        )

        _uiState.value =
            currentState.copy(
                messages = currentQuestionList,
                isLoading = true,
                error = null
            )

        val requestAgentState = if (!locationName.isNullOrBlank()) {
            agentState.copy(
                location_name = locationName,
                location_latitude = latitude,
                location_longitude = longitude
            )
        } else {
            agentState
        }

        viewModelScope.launch {

            try {

                val response =
                    ChatClient.api.askWeather(
                        ChatWeatherRequest(
                            question = text,
                            latitude = latitude,
                            longitude = longitude,
                            language = language,
                            history = emptyList(),
                            agent_state = requestAgentState
                        )
                    )

                // Save the latest structured agent context.
                if (response.agent_state != null) {
                    agentState =
                        response.agent_state
                }

                if (
                    response.status != "success"
                ) {

                    throw Exception(
                        "WeatherGPT returned an error."
                    )
                }

                val answer =
                    response.answer
                        ?.trim()
                        .takeUnless {
                            it.isNullOrEmpty()
                        }
                        ?: "I couldn't generate a response."

                val instantMessages = listOf(
                    ChatUiMessage(
                        role = "user",
                        content = text
                    ),
                    ChatUiMessage(
                        role = "assistant",
                        content = answer
                    )
                )

                _uiState.value =
                    ChatUiState(
                        messages = instantMessages,
                        isLoading = false,
                        error = null,
                        detectedLanguage =
                            response.language,
                        detectedLanguageCode =
                            response.language_code
                    )

            } catch (e: Exception) {

                val errorMessage = when (e) {
                    is java.net.SocketTimeoutException ->
                        "Connection timed out. The cloud server may be waking up, please tap Retry."
                    is java.net.UnknownHostException ->
                        "Unable to reach WeatherGPT. Please check your internet connection."
                    else ->
                        e.message
                            ?: "Unable to contact WeatherGPT."
                }

                _uiState.value =
                    ChatUiState(
                        messages = currentQuestionList,
                        isLoading = false,
                        error = errorMessage,
                        detectedLanguage =
                            currentState.detectedLanguage,
                        detectedLanguageCode =
                            currentState.detectedLanguageCode
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
