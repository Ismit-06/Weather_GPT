package com.weathergpt.app.location

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LlmEngine(
    val id: String,
    val name: String,
    val description: String
)

object LlmEngineStore {

    private const val PREF_NAME = "weathergpt_preferences"
    private const val ENGINE_KEY = "preferred_llm_engine"

    const val ENGINE_OPENROUTER = "openrouter"
    const val ENGINE_LOCAL = "local"

    val SUPPORTED_ENGINES = listOf(
        LlmEngine(ENGINE_OPENROUTER, "OpenRouter (Cloud)", "Qwen 2.5 72B / Llama 3.3"),
        LlmEngine(ENGINE_LOCAL, "Local WeatherAI", "On-device / Local PyTorch Model")
    )

    private val _engine = MutableStateFlow(ENGINE_OPENROUTER)
    val engineFlow: StateFlow<String> = _engine.asStateFlow()

    fun initialize(context: Context) {
        _engine.value = getEngine(context)
    }

    fun getEngine(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(ENGINE_KEY, ENGINE_OPENROUTER) ?: ENGINE_OPENROUTER
    }

    fun saveEngine(context: Context, engineId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ENGINE_KEY, engineId)
            .apply()
        _engine.value = engineId
    }
}
