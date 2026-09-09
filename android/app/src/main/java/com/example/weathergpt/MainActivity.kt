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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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

        // Initialize OSMDroid configuration safely with internal cache paths
        try {
            val osmConfig = org.osmdroid.config.Configuration.getInstance()
            osmConfig.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
            osmConfig.userAgentValue = "WeatherGPT/1.0 (contact@weathergpt.app; Android)"
            val osmBaseDir = java.io.File(cacheDir, "osmdroid")
            if (!osmBaseDir.exists()) osmBaseDir.mkdirs()
            val osmTileDir = java.io.File(osmBaseDir, "tiles")
            if (!osmTileDir.exists()) osmTileDir.mkdirs()
            osmConfig.osmdroidBasePath = osmBaseDir
            osmConfig.osmdroidTileCache = osmTileDir
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "OSMDroid safe init", e)
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
                var showSplash by remember { mutableStateOf(true) }

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation()

                    if (showSplash) {
                        com.example.weathergpt.ui.components.AnimatedSplashScreen(
                            onAnimationFinished = {
                                showSplash = false
                            }
                        )
                    }
                }
            }
        }
    }
}
