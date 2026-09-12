package com.example.weathergpt.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.*

/**
 * Represents a snapshot of User A's weather & location shared with User B.
 * Encoded entirely into a deep link without requiring any backend database.
 */
data class SharedFriendWeather(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val temperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCHEME_CUSTOM = "weathergpt"
        const val HOST_SHARE = "share"
        const val WEB_BASE_URL = "https://weather-gpt-ymze.onrender.com/share"

        /**
         * Encodes a SharedFriendWeather object into both custom scheme and universal web URLs.
         */
        fun encodeToWebUri(data: SharedFriendWeather): String {
            val query = buildQueryString(data)
            return "$WEB_BASE_URL?$query"
        }

        fun encodeToCustomUri(data: SharedFriendWeather): String {
            val query = buildQueryString(data)
            return "$SCHEME_CUSTOM://$HOST_SHARE?$query"
        }

        private fun buildQueryString(data: SharedFriendWeather): String {
            val enc = { s: String -> URLEncoder.encode(s, StandardCharsets.UTF_8.toString()) }
            return "n=${enc(data.name)}" +
                    "&lat=${data.latitude}" +
                    "&lon=${data.longitude}" +
                    "&c=${enc(data.cityName)}" +
                    "&t=${"%.1f".format(Locale.US, data.temperature)}" +
                    "&cond=${enc(data.condition)}" +
                    "&h=${data.humidity}" +
                    "&w=${"%.1f".format(Locale.US, data.windSpeed)}" +
                    "&ts=${data.timestamp}"
        }

        /**
         * Parses a deep link URL or raw pasted text into a SharedFriendWeather instance.
         */
        fun parseFromUri(rawInput: String): SharedFriendWeather? {
            return try {
                val clean = rawInput.trim()
                // If user pasted a full message containing a URL, extract the URL
                val urlRegex = Regex("""(https?://[^\s]+/share\?[^\s]+|weathergpt://share\?[^\s]+)""")
                val matchedUrl = urlRegex.find(clean)?.value ?: clean

                val uri = Uri.parse(matchedUrl)
                val name = uri.getQueryParameter("n") ?: uri.getQueryParameter("name") ?: "Friend"
                val latStr = uri.getQueryParameter("lat") ?: return null
                val lonStr = uri.getQueryParameter("lon") ?: return null
                val city = uri.getQueryParameter("c") ?: uri.getQueryParameter("city") ?: "Unknown Location"
                val tempStr = uri.getQueryParameter("t") ?: uri.getQueryParameter("temp") ?: "0.0"
                val cond = uri.getQueryParameter("cond") ?: uri.getQueryParameter("condition") ?: "Clear"
                val humStr = uri.getQueryParameter("h") ?: uri.getQueryParameter("humidity") ?: "0"
                val windStr = uri.getQueryParameter("w") ?: uri.getQueryParameter("wind") ?: "0.0"
                val tsStr = uri.getQueryParameter("ts") ?: uri.getQueryParameter("time")

                val lat = latStr.toDoubleOrNull() ?: return null
                val lon = lonStr.toDoubleOrNull() ?: return null
                val temp = tempStr.toDoubleOrNull() ?: 0.0
                val hum = humStr.toIntOrNull() ?: 0
                val wind = windStr.toDoubleOrNull() ?: 0.0
                val ts = tsStr?.toLongOrNull() ?: System.currentTimeMillis()

                SharedFriendWeather(
                    name = URLDecoder.decode(name, StandardCharsets.UTF_8.toString()),
                    latitude = lat,
                    longitude = lon,
                    cityName = URLDecoder.decode(city, StandardCharsets.UTF_8.toString()),
                    temperature = temp,
                    condition = URLDecoder.decode(cond, StandardCharsets.UTF_8.toString()),
                    humidity = hum,
                    windSpeed = wind,
                    timestamp = ts
                )
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * Haversine formula to compute distance in kilometers between two GPS coordinates.
         */
        fun computeDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0 // Earth radius in km
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        /**
         * Formats how long ago the location/weather was shared.
         */
        fun formatRelativeTime(timestamp: Long): String {
            val diffMs = System.currentTimeMillis() - timestamp
            if (diffMs < 0) return "Just now"
            val mins = diffMs / (1000 * 60)
            return when {
                mins < 2 -> "Just now"
                mins < 60 -> "$mins mins ago"
                mins < 1440 -> "${mins / 60} hours ago"
                else -> "${mins / 1440} days ago"
            }
        }
    }
}

/**
 * Singleton state holder for the currently active shared friend location.
 */
object SharedFriendStore {
    private const val PREFS_NAME = "weathergpt_share_prefs"
    private const val KEY_SAVED_USER_NAME = "saved_user_name"

    private val _sharedFriend = MutableStateFlow<SharedFriendWeather?>(null)
    val sharedFriend: StateFlow<SharedFriendWeather?> = _sharedFriend.asStateFlow()

    // Trigger to navigate to Map screen when deep link arrives
    private val _navigateToMapTrigger = MutableStateFlow<Boolean>(false)
    val navigateToMapTrigger: StateFlow<Boolean> = _navigateToMapTrigger.asStateFlow()

    fun setSharedFriend(friend: SharedFriendWeather, triggerNavigation: Boolean = true) {
        _sharedFriend.value = friend
        if (triggerNavigation) {
            _navigateToMapTrigger.value = true
        }
    }

    fun clearSharedFriend() {
        _sharedFriend.value = null
    }

    fun resetNavigationTrigger() {
        _navigateToMapTrigger.value = false
    }

    fun getSavedUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAVED_USER_NAME, "") ?: ""
    }

    fun saveUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_USER_NAME, name.trim()).apply()
    }
}
