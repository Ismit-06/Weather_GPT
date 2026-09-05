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

import androidx.lifecycle.lifecycleScope
import com.example.weathergpt.data.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        // Asynchronously warm up the Render cloud server so it wakes up immediately
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                BackendConfig.warmUp()
            } catch (_: Exception) {}
        }

        val preferences =
            getSharedPreferences(
                "weather_gpt_settings",
                MODE_PRIVATE
            )

        setContent {
            WeatherGPTTheme(
                darkTheme = true
            ) {
                AppNavigation()
            }
        }
    }
}
