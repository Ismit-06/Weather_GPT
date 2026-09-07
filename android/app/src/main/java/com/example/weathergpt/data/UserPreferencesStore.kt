package com.example.weathergpt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class UserPreferences(
    val isOptInEnabled: Boolean = true,
    val primaryActivity: String = "Running",
    val learnedActivities: Set<String> = setOf("Running"),
    val activityCounts: Map<String, Int> = emptyMap()
)

data class PersonalActivityInsight(
    val activity: String,
    val icon: String,
    val headline: String,
    val details: String,
    val suitability: String, // "EXCELLENT", "GOOD", "MODERATE", "CAUTION"
    val bestTimeWindow: String,
    val temperatureC: Int,
    val rainProbabilityPct: Int,
    val humidityPct: Int
)

object UserPreferencesStore {

    private const val PREFS_NAME = "weathergpt_user_preferences"
    private const val KEY_OPT_IN = "key_personalization_opt_in"
    private const val KEY_PRIMARY_ACTIVITY = "key_primary_activity"
    private const val KEY_LEARNED_ACTIVITIES = "key_learned_activities"
    private const val KEY_ACTIVITY_COUNTS = "key_activity_counts"

    val AVAILABLE_ACTIVITIES = listOf(
        "Running" to "🏃",
        "Cycling" to "🚴",
        "Walking & Pets" to "🚶",
        "Commute / College" to "🎒",
        "Outdoor Sports" to "⚽",
        "Photography & Stargazing" to "📸",
        "Gardening" to "🌱"
    )

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadPreferences(context: Context): UserPreferences {
        val prefs = getPrefs(context)
        val isOptIn = prefs.getBoolean(KEY_OPT_IN, true)
        val primary = prefs.getString(KEY_PRIMARY_ACTIVITY, "Running") ?: "Running"
        val learned = prefs.getStringSet(KEY_LEARNED_ACTIVITIES, setOf("Running")) ?: setOf("Running")

        val countsJson = prefs.getString(KEY_ACTIVITY_COUNTS, "{}") ?: "{}"
        val counts = mutableMapOf<String, Int>()
        try {
            val json = JSONObject(countsJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                counts[key] = json.optInt(key, 0)
            }
        } catch (_: Exception) {}

        val userPrefs = UserPreferences(
            isOptInEnabled = isOptIn,
            primaryActivity = primary,
            learnedActivities = learned,
            activityCounts = counts
        )
        _preferences.value = userPrefs
        return userPrefs
    }

    fun setOptIn(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(KEY_OPT_IN, enabled).apply()
        _preferences.value = _preferences.value.copy(isOptInEnabled = enabled)
    }

    fun setPrimaryActivity(context: Context, activity: String) {
        val prefs = getPrefs(context)
        val updatedSet = _preferences.value.learnedActivities + activity
        prefs.edit()
            .putString(KEY_PRIMARY_ACTIVITY, activity)
            .putStringSet(KEY_LEARNED_ACTIVITIES, updatedSet)
            .apply()
        _preferences.value = _preferences.value.copy(
            primaryActivity = activity,
            learnedActivities = updatedSet
        )
    }

    fun toggleActivity(context: Context, activity: String, enabled: Boolean) {
        val prefs = getPrefs(context)
        val current = _preferences.value.learnedActivities.toMutableSet()
        if (enabled) {
            current.add(activity)
        } else {
            current.remove(activity)
        }
        val newPrimary = if (current.contains(_preferences.value.primaryActivity)) {
            _preferences.value.primaryActivity
        } else {
            current.firstOrNull() ?: "Running"
        }

        prefs.edit()
            .putStringSet(KEY_LEARNED_ACTIVITIES, current)
            .putString(KEY_PRIMARY_ACTIVITY, newPrimary)
            .apply()

        _preferences.value = _preferences.value.copy(
            learnedActivities = current,
            primaryActivity = newPrimary
        )
    }

    /**
     * Inspects a user query, increments activity inquiry counters, and automatically
     * learns new preferences when asked multiple times.
     */
    fun recordActivityQuery(context: Context, query: String): String? {
        val currentPrefs = loadPreferences(context)
        if (!currentPrefs.isOptInEnabled) return null

        val q = query.lowercase()
        val detectedActivity = when {
            q.contains("run") || q.contains("jog") || q.contains("marathon") || q.contains("sprint") -> "Running"
            q.contains("cycle") || q.contains("cycling") || q.contains("bike") || q.contains("biking") -> "Cycling"
            q.contains("walk") || q.contains("dog") || q.contains("stroll") || q.contains("pet") -> "Walking & Pets"
            q.contains("college") || q.contains("office") || q.contains("commute") || q.contains("drive") || q.contains("travel") -> "Commute / College"
            q.contains("sport") || q.contains("cricket") || q.contains("football") || q.contains("badminton") || q.contains("tennis") -> "Outdoor Sports"
            q.contains("star") || q.contains("photo") || q.contains("night sky") || q.contains("astro") -> "Photography & Stargazing"
            q.contains("garden") || q.contains("plant") || q.contains("lawn") -> "Gardening"
            else -> null
        } ?: return null

        val counts = currentPrefs.activityCounts.toMutableMap()
        val newCount = (counts[detectedActivity] ?: 0) + 1
        counts[detectedActivity] = newCount

        val learned = currentPrefs.learnedActivities.toMutableSet()
        var newlyLearned: String? = null

        if (newCount >= 2 && !learned.contains(detectedActivity)) {
            learned.add(detectedActivity)
            newlyLearned = detectedActivity
        }

        val json = JSONObject()
        counts.forEach { (k, v) -> json.put(k, v) }

        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_ACTIVITY_COUNTS, json.toString())
            .putStringSet(KEY_LEARNED_ACTIVITIES, learned)
            .putString(KEY_PRIMARY_ACTIVITY, detectedActivity)
            .apply()

        _preferences.value = currentPrefs.copy(
            activityCounts = counts,
            learnedActivities = learned,
            primaryActivity = detectedActivity
        )

        return newlyLearned
    }

    /**
     * Synthesizes proactive activity insights based on learned preferences and forecast telemetry.
     */
    fun evaluateActivityInsight(
        activity: String,
        forecast: List<MetForecastItem>
    ): PersonalActivityInsight {
        val icon = AVAILABLE_ACTIVITIES.firstOrNull { it.first == activity }?.second ?: "🏃"
        if (forecast.isEmpty()) {
            return PersonalActivityInsight(
                activity = activity,
                icon = icon,
                headline = "Tomorrow morning looks pleasant for $activity",
                details = "24°C, low rain probability and moderate humidity.",
                suitability = "EXCELLENT",
                bestTimeWindow = "6:00 AM – 8:30 AM",
                temperatureC = 24,
                rainProbabilityPct = 10,
                humidityPct = 65
            )
        }

        // Look at upcoming 6 AM - 9 AM or tomorrow morning slot
        val targetItem = forecast.getOrNull(6) ?: forecast.first()
        val temp = targetItem.temperature_c?.toInt() ?: 24
        val rainProb = targetItem.precipitation_probability_pct?.toInt() ?: 10
        val humidity = targetItem.relative_humidity_pct?.toInt() ?: 65
        val windSpeedKmh = targetItem.wind_speed_ms?.let { (it * 3.6).toInt() } ?: 12

        val (headline, suitability, window) = when (activity) {
            "Running" -> {
                when {
                    rainProb > 50 -> Triple("Caution: Rain likely tomorrow morning during your usual run time", "CAUTION", "Consider indoor treadmill or wait until 10 AM")
                    temp > 32 -> Triple("Warm conditions expected: Hydrate well if running after 8 AM", "MODERATE", "5:30 AM – 7:00 AM")
                    temp in 18..26 && rainProb < 20 -> Triple("Tomorrow morning looks excellent for running", "EXCELLENT", "6:00 AM – 8:30 AM")
                    else -> Triple("Good morning running conditions expected", "GOOD", "6:00 AM – 8:00 AM")
                }
            }
            "Cycling" -> {
                when {
                    rainProb > 40 || windSpeedKmh > 30 -> Triple("Challenging cycling conditions: Gusty winds and wet roads possible", "CAUTION", "Check live radar before heading out")
                    windSpeedKmh < 15 && rainProb < 15 -> Triple("Ideal cycling conditions: Dry roads and gentle breeze", "EXCELLENT", "6:00 AM – 9:00 AM or 4:30 PM – 6:30 PM")
                    else -> Triple("Moderate cycling conditions tomorrow", "GOOD", "7:00 AM – 9:30 AM")
                }
            }
            "Walking & Pets" -> {
                when {
                    rainProb > 60 -> Triple("Bring an umbrella if walking pets in the morning", "CAUTION", "Short walks between showers")
                    temp in 20..28 -> Triple("Pleasant weather for outdoor walks and pet time", "EXCELLENT", "6:30 AM – 8:30 AM & 5:30 PM – 7:00 PM")
                    else -> Triple("Good walking conditions ahead", "GOOD", "Early morning or dusk")
                }
            }
            "Commute / College" -> {
                when {
                    rainProb > 50 -> Triple("Wet commute expected: Carry rain protection and allow +15 min", "CAUTION", "Leave 15-20 min earlier")
                    else -> Triple("Smooth weather conditions for your daily commute", "EXCELLENT", "Normal schedule")
                }
            }
            "Outdoor Sports" -> {
                when {
                    rainProb > 45 -> Triple("Ground may be wet: Check field conditions before game", "MODERATE", "Morning dry window")
                    else -> Triple("Great weather for outdoor games and fitness", "EXCELLENT", "4:00 PM – 6:30 PM")
                }
            }
            "Photography & Stargazing" -> {
                val cloud = targetItem.cloud_cover_pct?.toInt() ?: 20
                if (cloud < 30) Triple("Clear skies expected: Outstanding conditions for photography", "EXCELLENT", "Golden hour (6:00 AM) & Stargazing (9 PM)")
                else Triple("Partly cloudy skies with soft diffused lighting", "GOOD", "Morning Golden Hour")
            }
            else -> {
                Triple("Conditions look favorable for $activity", "GOOD", "Morning hours")
            }
        }

        val details = "${temp}°C, ${if (rainProb < 20) "low rain probability ($rainProb%)" else "rain chance $rainProb%"}, ${humidity}% humidity and ${windSpeedKmh} km/h wind."

        return PersonalActivityInsight(
            activity = activity,
            icon = icon,
            headline = headline,
            details = details,
            suitability = suitability,
            bestTimeWindow = window,
            temperatureC = temp,
            rainProbabilityPct = rainProb,
            humidityPct = humidity
        )
    }
}
