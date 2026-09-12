package com.example.weathergpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

        // Initialize OSMDroid configuration safely with internal cache paths and maximum performance
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

            // Turbo performance: 12 parallel download threads and 250 in-memory tile cache
            osmConfig.tileDownloadThreads = 12.toShort()
            osmConfig.tileFileSystemThreads = 12.toShort()
            osmConfig.tileDownloadMaxQueueSize = 120.toShort()
            osmConfig.cacheMapTileCount = 250.toShort()
            osmConfig.cacheMapTileOvershoot = 80.toShort()
            osmConfig.expirationExtendedDuration = 1000L * 60 * 60 * 24 * 60
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "OSMDroid safe init", e)
        }

        val preferences =
            getSharedPreferences(
                "weather_gpt_settings",
                MODE_PRIVATE
            )

        // Process deep link if launched via share link
        handleIncomingShareIntent(intent)

        setContent {
            WeatherGPTTheme(
                darkTheme = true
            ) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        try {
            val friendData = com.example.weathergpt.data.SharedFriendWeather.parseFromUri(uri.toString())
            if (friendData != null) {
                com.example.weathergpt.data.SharedFriendStore.setSharedFriend(friendData, triggerNavigation = true)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error parsing friend share deep link", e)
        }
    }
}
