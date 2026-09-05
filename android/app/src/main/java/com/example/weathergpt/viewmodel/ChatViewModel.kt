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
                messages = ChatStore.loadMessages(context)
            )
        )

    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    // Structured context returned by the backend.
    // This survives across chat messages and app sessions.
    private var agentState =
        ChatStore.loadAgentState(context)


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

        // Persist user question immediately
        ChatStore.save(context, updatedMessages, agentState)

        viewModelScope.launch {

            try {

                val history =
                    updatedMessages
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

                val finalMessages =
                    updatedMessages +
                        ChatUiMessage(
                            role = "assistant",
                            content = answer
                        )

                _uiState.value =
                    ChatUiState(
                        messages = finalMessages,
                        isLoading = false,
                        error = null,
                        detectedLanguage =
                            response.language,
                        detectedLanguageCode =
                            response.language_code
                    )

                // Persist full conversation with response and state
                ChatStore.save(context, finalMessages, agentState)

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
                        messages = updatedMessages,
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
