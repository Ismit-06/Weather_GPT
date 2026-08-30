package com.example.weathergpt.location

import android.content.Context

object LanguageStore {

    private const val PREF_NAME =
        "weathergpt_preferences"

    private const val LANGUAGE_KEY =
        "preferred_language"

    private const val DEFAULT_LANGUAGE =
        "Auto"


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
    }
}
