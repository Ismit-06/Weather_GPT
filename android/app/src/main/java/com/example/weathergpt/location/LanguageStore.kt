package com.example.weathergpt.location

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLanguage(
    val code: String,
    val englishName: String,
    val nativeLabel: String,
    val ttsLocaleTag: String
)

object LanguageStore {

    private const val PREF_NAME =
        "weathergpt_preferences"

    private const val LANGUAGE_KEY =
        "preferred_language"

    const val DEFAULT_LANGUAGE =
        "Auto"

    val SUPPORTED_LANGUAGES = listOf(
        AppLanguage("Auto", "Auto Detect", "स्वचालित", "en-IN"),
        AppLanguage("English", "English", "English", "en-IN"),
        AppLanguage("Hindi", "Hindi", "हिन्दी", "hi-IN"),
        AppLanguage("Hinglish", "Hinglish", "Hinglish (Roman)", "hi-IN"),
        AppLanguage("Telugu", "Telugu", "తెలుగు", "te-IN"),
        AppLanguage("Tamil", "Tamil", "தமிழ்", "ta-IN"),
        AppLanguage("Kannada", "Kannada", "ಕನ್ನಡ", "kn-IN"),
        AppLanguage("Bengali", "Bengali", "বাংলা", "bn-IN"),
        AppLanguage("Marathi", "Marathi", "मराठी", "mr-IN"),
        AppLanguage("Gujarati", "Gujarati", "ગુજરાતી", "gu-IN"),
        AppLanguage("Malayalam", "Malayalam", "മലയാളം", "ml-IN"),
        AppLanguage("Punjabi", "Punjabi", "ਪੰਜਾਬੀ", "pa-IN")
    )

    private val _language =
        MutableStateFlow(DEFAULT_LANGUAGE)

    val languageFlow: StateFlow<String> =
        _language.asStateFlow()


    fun initialize(context: Context) {
        _language.value = getLanguage(context)
    }


    fun getLanguage(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                LANGUAGE_KEY,
                DEFAULT_LANGUAGE
            )
            ?: DEFAULT_LANGUAGE
    }


    fun saveLanguage(
        context: Context,
        language: String
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                LANGUAGE_KEY,
                language
            )
            .apply()

        _language.value = language
    }
}
