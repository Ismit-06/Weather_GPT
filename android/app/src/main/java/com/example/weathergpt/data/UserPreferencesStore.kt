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
    val summary: String,
    val suitability: String, // "EXCELLENT", "GOOD", "MODERATE", "CAUTION"
    val bestTimeWindow: String,
    val temperatureC: Int,
    val rainProbabilityPct: Int
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
     * Synthesizes actionable, to-the-point, activity-specific summaries based on learned preferences and forecast telemetry.
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
                summary = "Tomorrow morning looks excellent for $activity: 24°C, low rain probability and moderate humidity.",
                suitability = "EXCELLENT",
                bestTimeWindow = "6:00 AM – 8:30 AM",
                temperatureC = 24,
                rainProbabilityPct = 10
            )
        }

        val targetItem = forecast.getOrNull(6) ?: forecast.first()
        val temp = targetItem.temperature_c?.toInt() ?: 24
        val rainProb = targetItem.precipitation_probability_pct?.toInt() ?: 10
        val humidity = targetItem.relative_humidity_pct?.toInt() ?: 65
        val windSpeedKmh = targetItem.wind_speed_ms?.let { (it * 3.6).toInt() } ?: 12
        val cloudCover = targetItem.cloud_cover_pct?.toInt() ?: 25

        val (summary, suitability, window) = when (activity) {
            "Running" -> {
                when {
                    rainProb >= 40 -> Triple(
                        "Rain expected around morning run hours — consider an early run before 6:30 AM or an indoor cardio workout.",
                        "CAUTION",
                        "Before 6:30 AM or Indoor"
                    )
                    temp >= 30 -> Triple(
                        "Warm and humid conditions — run early before 7:00 AM and keep a steady hydration pace.",
                        "MODERATE",
                        "5:30 AM – 7:00 AM"
                    )
                    temp in 18..26 && rainProb < 25 -> Triple(
                        "Tomorrow morning looks excellent for running — cool 24°C, dry ground, and low rain probability.",
                        "EXCELLENT",
                        "6:00 AM – 8:30 AM"
                    )
                    else -> Triple(
                        "Good running weather ahead — mild temperatures with calm morning breeze.",
                        "GOOD",
                        "6:30 AM – 8:30 AM"
                    )
                }
            }
            "Cycling" -> {
                when {
                    windSpeedKmh >= 25 -> Triple(
                        "Gusty headwinds ($windSpeedKmh km/h) expected — stick to sheltered urban roads or lower-speed loops.",
                        "CAUTION",
                        "Early morning calm"
                    )
                    rainProb >= 35 -> Triple(
                        "Wet road caution — reduced tire traction and passing showers; check radar before heading out.",
                        "CAUTION",
                        "Dry afternoon slot"
                    )
                    windSpeedKmh < 15 && rainProb < 20 -> Triple(
                        "Prime cycling conditions — dry asphalt, calm winds ($windSpeedKmh km/h), and clear visibility.",
                        "EXCELLENT",
                        "6:00 AM – 9:00 AM & 4:30 PM – 6:30 PM"
                    )
                    else -> Triple(
                        "Favorable cycling weather — steady roads with mild breeze.",
                        "GOOD",
                        "7:00 AM – 9:30 AM"
                    )
                }
            }
            "Walking & Pets" -> {
                when {
                    rainProb >= 50 -> Triple(
                        "Scattered showers likely — take a short walk between rain intervals and carry an umbrella.",
                        "CAUTION",
                        "Short dry intervals"
                    )
                    temp >= 32 -> Triple(
                        "Hot pavement warning — walk pets before 8:00 AM or after sunset to protect paw pads.",
                        "MODERATE",
                        "Before 8:00 AM & After 6:30 PM"
                    )
                    temp in 19..28 -> Triple(
                        "Great conditions for an outdoor dog walk — cool ground temperatures and fresh morning air.",
                        "EXCELLENT",
                        "6:30 AM – 8:30 AM & 5:30 PM – 7:00 PM"
                    )
                    else -> Triple(
                        "Pleasant walking weather with comfortable breeze.",
                        "GOOD",
                        "Morning & Dusk"
                    )
                }
            }
            "Commute / College" -> {
                when {
                    rainProb >= 40 -> Triple(
                        "Wet commute ahead — carry an umbrella and factor in +15 minutes for traffic delays.",
                        "CAUTION",
                        "Leave 15 min earlier"
                    )
                    cloudCover > 85 -> Triple(
                        "Overcast with possible early morning mist — smooth transit with headlight visibility.",
                        "GOOD",
                        "On schedule"
                    )
                    temp >= 34 -> Triple(
                        "Warm afternoon transit expected — carry water and stay hydrated on your route.",
                        "MODERATE",
                        "Stay hydrated"
                    )
                    else -> Triple(
                        "Clear and smooth transit — no weather-related commute delays expected.",
                        "EXCELLENT",
                        "On schedule"
                    )
                }
            }
            "Outdoor Sports" -> {
                when {
                    rainProb >= 40 -> Triple(
                        "Slippery field risk — wet turf may affect ball control; wear studded cleats.",
                        "CAUTION",
                        "Dry morning window"
                    )
                    temp >= 34 -> Triple(
                        "High heat index — schedule games early and enforce frequent hydration breaks.",
                        "MODERATE",
                        "6:00 AM – 8:00 AM & 5:00 PM – 6:30 PM"
                    )
                    else -> Triple(
                        "Excellent sports weather — firm pitch, comfortable breeze, and optimal game temperature.",
                        "EXCELLENT",
                        "4:00 PM – 6:30 PM"
                    )
                }
            }
            "Photography & Stargazing" -> {
                when {
                    cloudCover < 30 -> Triple(
                        "Crystal-clear skies tonight — stellar stargazing visibility and golden hour light at sunrise.",
                        "EXCELLENT",
                        "Golden Hour (6:00 AM) & Night (9 PM)"
                    )
                    cloudCover in 30..80 -> Triple(
                        "Soft diffused overcast lighting — ideal for portraits and macro nature photography without harsh shadows.",
                        "GOOD",
                        "Morning & Late Afternoon"
                    )
                    else -> Triple(
                        "Moody atmospheric mist and heavy clouds — dramatic landscape opportunities; shield lenses from moisture.",
                        "MODERATE",
                        "Early Dawn"
                    )
                }
            }
            "Gardening" -> {
                when {
                    rainProb >= 40 -> Triple(
                        "Natural rainfall incoming — hold off on manual watering as rain will soak the soil.",
                        "GOOD",
                        "After rain clears"
                    )
                    windSpeedKmh >= 22 -> Triple(
                        "Breezy conditions ($windSpeedKmh km/h) — secure tall stalks and delay foliar spraying.",
                        "CAUTION",
                        "Sheltered morning hours"
                    )
                    temp >= 32 -> Triple(
                        "Water early near roots before 8:00 AM to prevent rapid evaporation and midday leaf scorch.",
                        "MODERATE",
                        "6:00 AM – 8:00 AM"
                    )
                    else -> Triple(
                        "Great morning for planting and pruning — humid air helps young roots settle without heat stress.",
                        "EXCELLENT",
                        "6:30 AM – 9:00 AM"
                    )
                }
            }
            else -> {
                Triple(
                    "Favorable weather conditions expected for $activity.",
                    "GOOD",
                    "Morning hours"
                )
            }
        }

        return PersonalActivityInsight(
            activity = activity,
            icon = icon,
            summary = summary,
            suitability = suitability,
            bestTimeWindow = window,
            temperatureC = temp,
            rainProbabilityPct = rainProb
        )
    }
}
