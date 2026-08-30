package com.example.weathergpt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathergpt.data.AgentState
import com.example.weathergpt.data.ChatClient
import com.example.weathergpt.data.ChatMessage
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


class ChatViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ChatUiState()
        )

    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    // Structured context returned by the backend.
    // This survives across chat messages inside this ViewModel.
    private var agentState =
        AgentState()


    fun sendMessage(
        question: String,
        latitude: Double,
        longitude: Double,
        language: String
    ) {

        val text =
            question.trim()

        if (text.isEmpty()) {
            return
        }

        val currentState =
            _uiState.value

        val currentMessages =
            currentState.messages

        val updatedMessages =
            currentMessages +
                ChatUiMessage(
                    role = "user",
                    content = text
                )

        _uiState.value =
            currentState.copy(
                messages = updatedMessages,
                isLoading = true,
                error = null
            )

        viewModelScope.launch {

            try {

                val history =
                    currentMessages
                        .takeLast(12)
                        .map {
                            ChatMessage(
                                role = it.role,
                                content = it.content
                            )
                        }

                val response =
                    ChatClient.api.askWeather(
                        ChatWeatherRequest(
                            question = text,
                            latitude = latitude,
                            longitude = longitude,
                            language = language,
                            history = history,
                            agent_state = agentState
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

                _uiState.value =
                    ChatUiState(
                        messages =
                            updatedMessages +
                                ChatUiMessage(
                                    role = "assistant",
                                    content = answer
                                ),
                        isLoading = false,
                        error = null,
                        detectedLanguage =
                            response.language,
                        detectedLanguageCode =
                            response.language_code
                    )

            } catch (e: Exception) {

                _uiState.value =
                    ChatUiState(
                        messages = updatedMessages,
                        isLoading = false,
                        error =
                            e.message
                                ?: "Unable to contact WeatherGPT.",
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

        _uiState.value =
            ChatUiState()
    }
}
