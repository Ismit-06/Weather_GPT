package com.example.weathergpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.weathergpt.navigation.AppNavigation
import com.example.weathergpt.ui.theme.WeatherGPTTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val preferences =
            getSharedPreferences(
                "weather_gpt_settings",
                MODE_PRIVATE
            )

        setContent {

            var darkMode by remember {
                mutableStateOf(
                    preferences.getBoolean(
                        "dark_mode",
                        true
                    )
                )
            }

            WeatherGPTTheme(
                darkTheme = darkMode
            ) {

                AppNavigation(
                    darkMode = darkMode,
                    onToggleDarkMode = {

                        darkMode = !darkMode

                        preferences
                            .edit()
                            .putBoolean(
                                "dark_mode",
                                darkMode
                            )
                            .apply()
                    }
                )
            }
        }
    }
}
