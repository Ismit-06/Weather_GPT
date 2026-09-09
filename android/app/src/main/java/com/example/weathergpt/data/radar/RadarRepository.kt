package com.example.weathergpt.data.radar

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RadarRepository {

    private var cachedMetadata: RadarMetadata? = null

    suspend fun getRadarMetadata(forceRefresh: Boolean = false): RadarMetadata? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedMetadata
        // Cache valid for 2 minutes unless forced
        if (!forceRefresh && cached != null && (now - cached.lastFetchedAt) < 120_000L) {
            return@withContext cached
        }

        try {
            val url = URL("https://api.rainviewer.com/public/weather-maps.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "WeatherGPT/1.0 (Android)")

            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w("RadarRepository", "RainViewer returned HTTP $code")
                return@withContext cached
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val root = JSONObject(body)
            val host = root.optString("host", "https://tilecache.rainviewer.com")
            val radar = root.optJSONObject("radar") ?: return@withContext cached
            val past = radar.optJSONArray("past")

            val frameList = mutableListOf<RadarFrame>()
            if (past != null) {
                for (i in 0 until past.length()) {
                    val item = past.optJSONObject(i) ?: continue
                    val time = item.optLong("time", 0L)
                    val path = item.optString("path", "")
                    if (time > 0L && path.isNotBlank()) {
                        frameList.add(RadarFrame(time = time, path = path))
                    }
                }
            }

            // Sort chronologically ascending
            frameList.sortBy { it.time }

            // Keep the last 12 frames (~2 hours of radar history)
            val trimmedFrames = if (frameList.size > 12) {
                frameList.takeLast(12)
            } else {
                frameList
            }

            val latestTime = trimmedFrames.lastOrNull()?.time ?: 0L
            val newMetadata = RadarMetadata(
                host = host,
                frames = trimmedFrames,
                latestTimestamp = latestTime,
                lastFetchedAt = now
            )
            cachedMetadata = newMetadata
            newMetadata
        } catch (e: Exception) {
            Log.e("RadarRepository", "Failed to fetch RainViewer metadata", e)
            cached
        }
    }

    fun buildTileUrl(host: String, path: String, zoom: Int, x: Int, y: Int): String {
        val cleanHost = host.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        // 256 tile size, Universal/TITAN color palette 2, smooth with snow 1_1
        return "$cleanHost$cleanPath/256/$zoom/$x/$y/2/1_1.png"
    }

    companion object {
        fun formatFrameTime(epochSeconds: Long, timeZone: String = "Asia/Kolkata"): String {
            if (epochSeconds <= 0L) return "Unavailable"
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
                this.timeZone = TimeZone.getTimeZone(timeZone)
            }
            return sdf.format(Date(epochSeconds * 1000L))
        }

        fun formatFrameAge(epochSeconds: Long): String {
            if (epochSeconds <= 0L) return "Radar unavailable"
            val diffMs = System.currentTimeMillis() - (epochSeconds * 1000L)
            val minutes = (diffMs / 60_000L).coerceAtLeast(0)
            return when {
                minutes < 2 -> "Radar updated just now"
                minutes < 60 -> "Radar updated $minutes min ago"
                else -> {
                    val hours = minutes / 60
                    "Radar updated ${hours}h ago"
                }
            }
        }
    }
}
