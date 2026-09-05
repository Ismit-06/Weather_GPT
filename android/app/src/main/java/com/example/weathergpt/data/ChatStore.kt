package com.example.weathergpt.data

import android.content.Context
import com.example.weathergpt.viewmodel.ChatUiMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ChatStore {

    private const val PREFS_NAME = "weathergpt_chat_store"
    private const val KEY_MESSAGES = "chat_messages"
    private const val KEY_AGENT_STATE = "chat_agent_state"
    private val gson = Gson()

    fun save(context: Context, messages: List<ChatUiMessage>, agentState: AgentState? = null) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(messages)
            val editor = prefs.edit().putString(KEY_MESSAGES, json)
            if (agentState != null) {
                editor.putString(KEY_AGENT_STATE, gson.toJson(agentState))
            }
            editor.apply()
        } catch (_: Exception) {}
    }

    fun loadMessages(context: Context): List<ChatUiMessage> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
            val type = object : TypeToken<List<ChatUiMessage>>() {}.type
            gson.fromJson<List<ChatUiMessage>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadAgentState(context: Context): AgentState {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_AGENT_STATE, null) ?: return AgentState()
            gson.fromJson(json, AgentState::class.java) ?: AgentState()
        } catch (_: Exception) {
            AgentState()
        }
    }

    fun clear(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Exception) {}
    }
}
