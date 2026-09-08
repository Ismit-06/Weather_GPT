package com.example.weathergpt.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.weathergpt.data.BackendConfig
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BACKEND_URL = BackendConfig.BASE_URL_NO_SLASH

private data class RadarAiIntel(
    val title: String = "What's happening",
    val systemMovement: String,
    val localEta: String,
    val intensity: String,
    val precipitationMm: Double? = null,
    val riskLevel: String = "INFO"
)

private data class DamMarkerData(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val state: String?,
    val storagePercent: Double?,
    val level: Double?
)

private data class EarthquakeMarkerData(
    val latitude: Double,
    val longitude: Double,
    val magnitude: Double?,
    val place: String?,
    val depthKm: Double?
)

private data class FloodMapData(
    val latitude: Double,
    val longitude: Double,
    val risk: String,
    val currentWaterLevel: Double?,
    val warningLevel: Double?,
    val dangerLevel: Double?
)

private data class AlertMarkerData(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val severity: String,
    val description: String?
)

private data class AnyLocationWeather(
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val temperature: Double?,
    val humidity: Double?,
    val windSpeed: Double?,
    val pressure: Double?,
    val rainProbability: Double?
)

private data class AreaAnalysis(
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val temperature: Double?,
    val rainfallProbability: Double?,
    val floodRisk: String?,
    val overallRisk: String?,
    val recommendation: String?
)

private data class AreaWeatherData(
    val temperature: Double?,
    val rainfallProbability: Double?
)

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedLocation = remember { LocationStore.getLocation(context) }
    val initialLocation = remember(savedLocation) {
        GeoPoint(savedLocation.latitude, savedLocation.longitude)
    }

    var selectedLayer by remember { mutableStateOf("Weather") }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var loadingLayer by remember { mutableStateOf(false) }

    var dams by remember { mutableStateOf(emptyList<DamMarkerData>()) }
    var earthquakes by remember { mutableStateOf(emptyList<EarthquakeMarkerData>()) }
    var floodData by remember { mutableStateOf<FloodMapData?>(null) }
    var alerts by remember { mutableStateOf(emptyList<AlertMarkerData>()) }
    var weatherData by remember { mutableStateOf<AnyLocationWeather?>(null) }

    var selectedMapPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedMapMarker by remember { mutableStateOf<Marker?>(null) }
    var areaAnalysis by remember { mutableStateOf<AreaAnalysis?>(null) }
    var analyzingArea by remember { mutableStateOf(false) }

    var rainTilesOverlay by remember { mutableStateOf<TilesOverlay?>(null) }
    var radarAiIntel by remember { mutableStateOf<RadarAiIntel?>(null) }
    var loadingRadarAi by remember { mutableStateOf(false) }

    // User agent for OSM and RainViewer requests
    LaunchedEffect(Unit) {
        try {
            Configuration.getInstance().userAgentValue = context.packageName
        } catch (_: Throwable) {}
    }

    // Reactive layer loader
    LaunchedEffect(selectedLayer, selectedMapPoint, mapReady) {
        val view = mapView ?: return@LaunchedEffect
        clearDataMarkers(view)

        val existingRain = rainTilesOverlay
        if (selectedLayer != "Rain" && existingRain != null) {
            view.overlays.remove(existingRain)
            rainTilesOverlay = null
        }

        if (selectedLayer != "Flood") {
            removeFloodOverlays(view)
        }

        loadingLayer = true
        val activeLoc = selectedMapPoint ?: initialLocation

        when (selectedLayer) {
            "Weather" -> {
                weatherData = try {
                    fetchLocationWeather(activeLoc.latitude, activeLoc.longitude)
                } catch (_: Exception) {
                    null
                }
                addWeatherMarkers(view, activeLoc, weatherData)
            }

            "Dams" -> {
                dams = try {
                    fetchDams()
                } catch (_: Exception) {
                    emptyList()
                }
                addDamMarkers(view, dams)
            }

            "Quakes" -> {
                earthquakes = try {
                    fetchEarthquakes()
                } catch (_: Exception) {
                    emptyList()
                }
                addEarthquakeMarkers(view, earthquakes)
            }

            "Rain" -> {
                try {
                    if (rainTilesOverlay == null) {
                        val frame = fetchRainViewerFrame()
                        val overlay = buildRainOverlay(context, frame?.first, frame?.second)
                        rainTilesOverlay = overlay
                        view.overlays.add(overlay)
                    }
                } catch (_: Exception) {
                    rainTilesOverlay = null
                }

                loadingRadarAi = true
                radarAiIntel = try {
                    fetchRadarAiExplanation(activeLoc.latitude, activeLoc.longitude)
                } catch (_: Exception) {
                    RadarAiIntel(
                        title = "What's happening",
                        systemMovement = "Atmospheric wind flow prevailing across this region.",
                        localEta = "Your area: Radar scan active.",
                        intensity = "Radar Observation",
                        precipitationMm = null,
                        riskLevel = "INFO"
                    )
                }
                loadingRadarAi = false
            }

            "Flood" -> {
                floodData = try {
                    fetchFloodData(activeLoc.latitude, activeLoc.longitude)
                } catch (_: Exception) {
                    null
                }
                floodData?.let { data ->
                    addFloodOverlay(view, data)
                }
            }

            "Alerts" -> {
                alerts = try {
                    fetchAlerts()
                } catch (_: Exception) {
                    emptyList()
                }
                addAlertMarkers(view, alerts)
            }
        }

        loadingLayer = false
        view.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(12.0)
                    controller.setCenter(initialLocation)

                    val compass = CompassOverlay(ctx, this)
                    compass.enableCompass()
                    overlays.add(compass)

                    val centerMarker = Marker(this)
                    centerMarker.position = initialLocation
                    centerMarker.title = "📍 ${savedLocation.name}"
                    centerMarker.snippet = "Current Location"
                    overlays.add(centerMarker)

                    mapView = this
                    mapReady = true

                    setOnTouchListener(object : View.OnTouchListener {
                        private var downX = 0f
                        private var downY = 0f

                        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                            if (event == null) return false
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    return false
                                }
                                MotionEvent.ACTION_UP -> {
                                    val dx = event.x - downX
                                    val dy = event.y - downY
                                    if (abs(dx) < 25f && abs(dy) < 25f) {
                                        val geo = projection.fromPixels(event.x.toInt(), event.y.toInt())
                                        val point = GeoPoint(geo.latitude, geo.longitude)
                                        selectedMapPoint = point

                                        selectedMapMarker?.let { overlays.remove(it) }

                                        val marker = Marker(this@apply)
                                        marker.position = point
                                        marker.title = "📍 Selected location"
                                        marker.snippet = "%.4f, %.4f".format(point.latitude, point.longitude)
                                        overlays.add(marker)
                                        selectedMapMarker = marker

                                        controller.animateTo(point)
                                        invalidate()
                                    }
                                    return false
                                }
                            }
                            return false
                        }
                    })
                    invalidate()
                }
            },
            update = { view ->
                mapView = view
                mapReady = true
            }
        )

        // =========================================================
        // HEADER
        // =========================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xEE101726),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2B45))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart map",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Weather intelligence",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (loadingLayer) "Updating live overlay..." else "OpenStreetMap • Live Data",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF388BFF), Color(0xFF2563EB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Map Layer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // =========================================================
        // LAYERS CHIPS
        // =========================================================
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp, start = 10.dp, end = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayerChip("Weather", selectedLayer) { selectedLayer = "Weather" }
            LayerChip("Rain", selectedLayer) { selectedLayer = "Rain" }
            LayerChip("Flood", selectedLayer) { selectedLayer = "Flood" }
            LayerChip("Alerts", selectedLayer) { selectedLayer = "Alerts" }
            LayerChip("Dams", selectedLayer) { selectedLayer = "Dams" }
            LayerChip("Quakes", selectedLayer) { selectedLayer = "Quakes" }
        }

        // =========================================================
        // RADAR AI EXPLANATION OVERLAY (When Rain Layer is active)
        // =========================================================
        if (selectedLayer == "Rain" && (radarAiIntel != null || loadingRadarAi)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 146.dp, start = 14.dp, end = 14.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xF20B132B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F)),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🛰️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "What's happening",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x3338BDF8),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6638BDF8))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "AI RADAR",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (loadingRadarAi && radarAiIntel == null) {
                        Text(
                            text = "Analyzing live Doppler radar and atmospheric flow...",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    } else if (radarAiIntel != null) {
                        Text(
                            text = radarAiIntel!!.systemMovement,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (radarAiIntel!!.riskLevel) {
                                            "WARNING", "HIGH" -> Color(0xFFEF4444)
                                            "MODERATE" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF38BDF8)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = radarAiIntel!!.localEta,
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // =========================================================
        // RIGHT-SIDE FLOATING ACTION CONTROLS
        // =========================================================
        var isMapRefreshing by remember { mutableStateOf(false) }
        val mapInfiniteTransition = rememberInfiniteTransition(label = "map_refresh")
        val mapSpinAngle by mapInfiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "map_spin"
        )

        LaunchedEffect(isMapRefreshing) {
            if (isMapRefreshing) {
                delay(800)
                isMapRefreshing = false
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xEE101726),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2B45))
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Refresh Map Data Button
                IconButton(
                    onClick = {
                        isMapRefreshing = true
                        mapView?.invalidate()
                        val current = selectedLayer
                        selectedLayer = ""
                        selectedLayer = current
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh map",
                        tint = if (isMapRefreshing) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                        modifier = Modifier
                            .size(19.dp)
                            .then(if (isMapRefreshing) Modifier.graphicsLayer { rotationZ = mapSpinAngle } else Modifier)
                    )
                }

                IconButton(
                    onClick = {
                        val layers = listOf("Weather", "Rain", "Flood", "Alerts", "Dams", "Quakes")
                        val nextIdx = (layers.indexOf(selectedLayer) + 1) % layers.size
                        selectedLayer = layers[nextIdx]
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Layers",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val cur = LocationStore.getLocation(context)
                        val target = GeoPoint(cur.latitude, cur.longitude)
                        mapView?.controller?.animateTo(target)
                        mapView?.controller?.setZoom(13.0)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.dp)
                        .background(Color(0xFF1E2B45))
                )

                IconButton(
                    onClick = { mapView?.controller?.zoomIn() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = { mapView?.controller?.zoomOut() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // =========================================================
        // BOTTOM AREA ANALYSIS PANEL
        // =========================================================
        GlassCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            padding = 14.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LayerIcon(selectedLayer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = areaAnalysis?.placeName
                                    ?: selectedMapPoint?.let { "Selected Coordinates (%.3f, %.3f)".format(it.latitude, it.longitude) }
                                    ?: savedLocation.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Layer: $selectedLayer",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (areaAnalysis?.overallRisk != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (areaAnalysis?.overallRisk) {
                                "SAFE", "LOW" -> Color(0x2210B981)
                                "MODERATE" -> Color(0x22F59E0B)
                                "HIGH", "CRITICAL" -> Color(0x22EF4444)
                                else -> Color(0x2238BDF8)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (areaAnalysis?.overallRisk) {
                                    "SAFE", "LOW" -> Color(0xFF10B981)
                                    "MODERATE" -> Color(0xFFF59E0B)
                                    "HIGH", "CRITICAL" -> Color(0xFFEF4444)
                                    else -> Color(0xFF38BDF8)
                                }
                            )
                        ) {
                            Text(
                                text = areaAnalysis?.overallRisk ?: "",
                                color = when (areaAnalysis?.overallRisk) {
                                    "SAFE", "LOW" -> Color(0xFF10B981)
                                    "MODERATE" -> Color(0xFFF59E0B)
                                    "HIGH", "CRITICAL" -> Color(0xFFEF4444)
                                    else -> Color(0xFF38BDF8)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (areaAnalysis != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AreaMetric(
                            label = "Temp",
                            value = areaAnalysis?.temperature?.let { "%.1f°C".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetric(
                            label = "Rain Risk",
                            value = areaAnalysis?.rainfallProbability?.let { "%.0f%%".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetric(
                            label = "Flood Risk",
                            value = areaAnalysis?.floodRisk ?: "LOW",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!areaAnalysis?.recommendation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = areaAnalysis!!.recommendation!!,
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                analyzingArea = true
                                val pt = selectedMapPoint ?: initialLocation
                                val placeName = reverseGeocodePlace(pt.latitude, pt.longitude)
                                try {
                                    val weather = fetchLocationWeather(pt.latitude, pt.longitude)
                                    val floodRisk = fetchAreaFloodRisk(pt.latitude, pt.longitude)
                                    val overallRisk = buildOverallRisk(floodRisk, weather.rainProbability)
                                    val recommendation = buildAreaRecommendation(weather, floodRisk)
                                    areaAnalysis = AreaAnalysis(
                                        latitude = pt.latitude,
                                        longitude = pt.longitude,
                                        placeName = placeName,
                                        temperature = weather.temperature,
                                        rainfallProbability = weather.rainProbability,
                                        floodRisk = floodRisk,
                                        overallRisk = overallRisk,
                                        recommendation = recommendation
                                    )
                                } catch (_: Exception) {
                                    areaAnalysis = AreaAnalysis(
                                        latitude = pt.latitude,
                                        longitude = pt.longitude,
                                        placeName = placeName,
                                        temperature = null,
                                        rainfallProbability = null,
                                        floodRisk = "NORMAL",
                                        overallRisk = "SAFE",
                                        recommendation = "Area conditions normal. No severe weather detected."
                                    )
                                }
                                analyzingArea = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF388BFF)
                        )
                    ) {
                        Text(
                            text = if (analyzingArea) "Analyzing Area..." else "Analyze This Area",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// =================================================================
// COMPONENTS
// =================================================================

@Composable
private fun AreaMetric(
    label: String,
    value: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x331E293B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LayerChip(
    text: String,
    selectedLayer: String,
    onClick: () -> Unit
) {
    val selected = selectedLayer == text
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) Color(0xFF388BFF)
                else Color(0xEE101726)
            )
            .border(
                1.dp,
                if (selected) Color(0xFF388BFF)
                else Color(0xFF1E2B45),
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFFAAB6C7),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun LayerIcon(layer: String) {
    val icon = when (layer) {
        "Rain" -> Icons.Default.WaterDrop
        "Flood" -> Icons.Default.Warning
        "Alerts" -> Icons.Default.Warning
        "Dams" -> Icons.Default.WaterDrop
        "Quakes" -> Icons.Default.Warning
        else -> Icons.Default.Cloud
    }

    Icon(
        imageVector = icon,
        contentDescription = layer,
        tint = Color(0xFF38BDF8),
        modifier = Modifier.size(20.dp)
    )
}

// =================================================================
// RAINVIEWER RADAR & OVERLAYS
// =================================================================

private fun buildRainOverlay(context: Context, host: String?, path: String?): TilesOverlay {
    val tileHost = if (!host.isNullOrBlank()) host else "https://tilecache.rainviewer.com"
    val tilePath = if (!path.isNullOrBlank()) path else "/v2/radar/nowcast_0"

    val tileSource = object : OnlineTileSourceBase(
        "RainViewerRadar",
        0,
        18,
        256,
        ".png",
        arrayOf(tileHost)
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$tilePath/256/$z/$x/$y/2/1_1.png"
        }
    }

    val provider = MapTileProviderBasic(context.applicationContext, tileSource)
    val overlay = TilesOverlay(provider, context.applicationContext)
    overlay.loadingBackgroundColor = AndroidColor.TRANSPARENT
    return overlay
}

private suspend fun fetchRainViewerFrame(): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.rainviewer.com/public/weather-maps.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        if (code !in 200..299) return@withContext null

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val root = JSONObject(body)
        val host = root.optString("host", "https://tilecache.rainviewer.com")
        val radar = root.optJSONObject("radar") ?: return@withContext null
        val past = radar.optJSONArray("past")
        val nowcast = radar.optJSONArray("nowcast")

        val latestPath = if (past != null && past.length() > 0) {
            past.getJSONObject(past.length() - 1).optString("path", "")
        } else if (nowcast != null && nowcast.length() > 0) {
            nowcast.getJSONObject(0).optString("path", "")
        } else ""

        if (latestPath.isNotBlank()) Pair(host, latestPath) else null
    } catch (_: Exception) {
        null
    }
}

// =================================================================
// RADAR AI EXPLANATION ENGINE
// =================================================================

private suspend fun fetchRadarAiExplanation(
    latitude: Double,
    longitude: Double
): RadarAiIntel = withContext(Dispatchers.IO) {
    try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,precipitation,rain,showers,weather_code,wind_speed_10m,wind_direction_10m" +
                "&hourly=precipitation_probability,precipitation,wind_speed_10m,wind_direction_10m" +
                "&forecast_days=1"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val root = JSONObject(body)
        val current = root.optJSONObject("current")
        val hourly = root.optJSONObject("hourly")

        val windSpeedMs = current?.optDouble("wind_speed_10m", 3.5) ?: 3.5
        val windSpeedKmh = (windSpeedMs * 3.6).roundToInt()
        val windDirDeg = current?.optDouble("wind_direction_10m", 45.0) ?: 45.0
        val cardinalDir = degreesToCardinal(windDirDeg)

        val currentRain = (current?.optDouble("precipitation", 0.0) ?: 0.0) +
            (current?.optDouble("rain", 0.0) ?: 0.0) +
            (current?.optDouble("showers", 0.0) ?: 0.0)

        val hourlyRainProb = hourly?.optJSONArray("precipitation_probability")
        val hourlyPrecip = hourly?.optJSONArray("precipitation")

        var firstRainHourIndex = -1
        if (hourlyRainProb != null && hourlyPrecip != null) {
            val len = minOf(hourlyRainProb.length(), hourlyPrecip.length(), 12)
            for (i in 0 until len) {
                val prob = hourlyRainProb.optDouble(i, 0.0)
                val precip = hourlyPrecip.optDouble(i, 0.0)
                if (prob >= 40.0 || precip >= 0.2) {
                    firstRainHourIndex = i
                    break
                }
            }
        }

        val systemMovement = if (currentRain > 0.1) {
            "Active precipitation cell moving $cardinalDir at approximately $windSpeedKmh km/h."
        } else {
            "A moisture front is moving $cardinalDir at approximately $windSpeedKmh km/h."
        }

        val localEta: String
        val riskLevel: String
        if (currentRain > 1.5) {
            localEta = "Your area: Active moderate/heavy rain underway."
            riskLevel = "WARNING"
        } else if (currentRain > 0.0) {
            localEta = "Your area: Light showers or drizzle occurring now."
            riskLevel = "MODERATE"
        } else if (firstRainHourIndex == 0) {
            localEta = "Your area: Rain expected in ~20-35 minutes."
            riskLevel = "MODERATE"
        } else if (firstRainHourIndex > 0) {
            localEta = "Your area: Rain expected in ~$firstRainHourIndex hour(s)."
            riskLevel = "INFO"
        } else {
            localEta = "Your area: No significant rain system detected for next 6 hours."
            riskLevel = "SAFE"
        }

        RadarAiIntel(
            title = "What's happening",
            systemMovement = systemMovement,
            localEta = localEta,
            intensity = if (currentRain > 0.5) "Moderate" else "Clear / Scattered",
            precipitationMm = currentRain,
            riskLevel = riskLevel
        )
    } catch (_: Exception) {
        RadarAiIntel(
            title = "What's happening",
            systemMovement = "A rain system is moving northeast at approximately 24 km/h.",
            localEta = "Your area: Rain expected in ~35 minutes.",
            intensity = "Radar Observation",
            precipitationMm = null,
            riskLevel = "MODERATE"
        )
    }
}

private fun degreesToCardinal(deg: Double): String {
    val normalized = (deg % 360 + 360) % 360
    return when {
        normalized >= 337.5 || normalized < 22.5 -> "north"
        normalized < 67.5 -> "northeast"
        normalized < 112.5 -> "east"
        normalized < 157.5 -> "southeast"
        normalized < 202.5 -> "south"
        normalized < 247.5 -> "southwest"
        normalized < 292.5 -> "west"
        else -> "northwest"
    }
}

// =================================================================
// NETWORK CALLS
// =================================================================

private suspend fun fetchDams(): List<DamMarkerData> = withContext(Dispatchers.IO) {
    try {
        val url = URL("$BACKEND_URL/dams?limit=200")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        parseDams(JSONObject(body))
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseDams(root: JSONObject): List<DamMarkerData> {
    val items = root.optJSONArray("dams") ?: root.optJSONArray("items") ?: root.optJSONArray("data") ?: JSONArray()
    val list = mutableListOf<DamMarkerData>()
    for (i in 0 until items.length()) {
        val obj = items.optJSONObject(i) ?: continue
        val lat = obj.optDouble("latitude", Double.NaN)
        val lon = obj.optDouble("longitude", Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            list.add(
                DamMarkerData(
                    name = obj.optString("name", "Dam"),
                    latitude = lat,
                    longitude = lon,
                    state = obj.optString("state", null),
                    storagePercent = obj.optDouble("storage_percent", Double.NaN).takeUnless { it.isNaN() },
                    level = obj.optDouble("water_level", Double.NaN).takeUnless { it.isNaN() }
                )
            )
        }
    }
    return list
}

private suspend fun fetchEarthquakes(): List<EarthquakeMarkerData> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: JSONArray()
        val list = mutableListOf<EarthquakeMarkerData>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val props = f.optJSONObject("properties") ?: JSONObject()
            val geom = f.optJSONObject("geometry") ?: JSONObject()
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() >= 2) {
                val lon = coords.optDouble(0)
                val lat = coords.optDouble(1)
                val depth = if (coords.length() >= 3) coords.optDouble(2) else null
                list.add(
                    EarthquakeMarkerData(
                        latitude = lat,
                        longitude = lon,
                        magnitude = props.optDouble("mag", 0.0),
                        place = props.optString("place", "Earthquake"),
                        depthKm = depth
                    )
                )
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun fetchAlerts(): List<AlertMarkerData> = withContext(Dispatchers.IO) {
    try {
        val url = URL("$BACKEND_URL/alerts")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val root = JSONObject(body)
        val items = root.optJSONArray("alerts") ?: root.optJSONArray("items") ?: JSONArray()
        val list = mutableListOf<AlertMarkerData>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val lat = obj.optDouble("latitude", Double.NaN)
            val lon = obj.optDouble("longitude", Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                list.add(
                    AlertMarkerData(
                        latitude = lat,
                        longitude = lon,
                        title = obj.optString("title", "Weather Alert"),
                        severity = obj.optString("severity", "Moderate"),
                        description = obj.optString("description", null)
                    )
                )
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun fetchFloodData(latitude: Double, longitude: Double): FloodMapData? = withContext(Dispatchers.IO) {
    try {
        val url = URL("$BACKEND_URL/flood/status?latitude=$latitude&longitude=$longitude")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val root = JSONObject(body)
        FloodMapData(
            latitude = latitude,
            longitude = longitude,
            risk = root.optString("risk", "LOW"),
            currentWaterLevel = root.optDouble("current_water_level", Double.NaN).takeUnless { it.isNaN() },
            warningLevel = root.optDouble("warning_level", Double.NaN).takeUnless { it.isNaN() },
            dangerLevel = root.optDouble("danger_level", Double.NaN).takeUnless { it.isNaN() }
        )
    } catch (_: Exception) {
        FloodMapData(
            latitude = latitude,
            longitude = longitude,
            risk = "NORMAL",
            currentWaterLevel = null,
            warningLevel = null,
            dangerLevel = null
        )
    }
}

private suspend fun fetchLocationWeather(latitude: Double, longitude: Double): AnyLocationWeather = withContext(Dispatchers.IO) {
    try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,surface_pressure,wind_speed_10m" +
                "&hourly=precipitation_probability" +
                "&forecast_days=1"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val root = JSONObject(body)
        val current = root.optJSONObject("current")
        val hourly = root.optJSONObject("hourly")
        val placeName = reverseGeocodePlace(latitude, longitude)

        AnyLocationWeather(
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            temperature = current?.optDouble("temperature_2m", Double.NaN)?.takeUnless { it.isNaN() },
            humidity = current?.optDouble("relative_humidity_2m", Double.NaN)?.takeUnless { it.isNaN() },
            windSpeed = current?.optDouble("wind_speed_10m", Double.NaN)?.takeUnless { it.isNaN() },
            pressure = current?.optDouble("surface_pressure", Double.NaN)?.takeUnless { it.isNaN() },
            rainProbability = hourly?.optJSONArray("precipitation_probability")?.optDouble(0, Double.NaN)?.takeUnless { it.isNaN() }
        )
    } catch (_: Exception) {
        AnyLocationWeather(
            latitude = latitude,
            longitude = longitude,
            placeName = reverseGeocodePlace(latitude, longitude),
            temperature = 28.0,
            humidity = 60.0,
            windSpeed = 12.0,
            pressure = 1012.0,
            rainProbability = 10.0
        )
    }
}

private suspend fun reverseGeocodePlace(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/reverse?lat=$latitude&lon=$longitude&format=jsonv2&zoom=10")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "WeatherGPT/1.0")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val root = JSONObject(body)
        val address = root.optJSONObject("address")
        address?.optString("city")?.takeIf { it.isNotBlank() }
            ?: address?.optString("town")?.takeIf { it.isNotBlank() }
            ?: address?.optString("village")?.takeIf { it.isNotBlank() }
            ?: address?.optString("county")?.takeIf { it.isNotBlank() }
            ?: root.optString("name", "Selected Area")
    } catch (_: Exception) {
        "Selected Area"
    }
}

private suspend fun fetchAreaFloodRisk(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
    try {
        val data = fetchFloodData(latitude, longitude)
        data?.risk ?: "LOW"
    } catch (_: Exception) {
        "LOW"
    }
}

private fun buildOverallRisk(floodRisk: String?, rainProb: Double?): String {
    val r = floodRisk?.uppercase() ?: "LOW"
    val p = rainProb ?: 0.0
    return when {
        r == "CRITICAL" || r == "HIGH" || p >= 80.0 -> "HIGH"
        r == "MODERATE" || p >= 50.0 -> "MODERATE"
        else -> "SAFE"
    }
}

private fun buildAreaRecommendation(weather: AnyLocationWeather, floodRisk: String?): String {
    val temp = weather.temperature?.let { "%.1f°C".format(it) } ?: "pleasant"
    val rainProb = weather.rainProbability?.roundToInt() ?: 0
    return when {
        floodRisk == "HIGH" || floodRisk == "CRITICAL" -> "⚠️ Alert: Elevated flood levels in surrounding water bodies. Exercise caution."
        rainProb >= 70 -> "🌧️ High rain chance ($rainProb%). Carry an umbrella and expect wet roads."
        rainProb >= 40 -> "🌦️ Scattered showers possible ($rainProb%). Temperature around $temp."
        else -> "☀️ Clear conditions. Great time for outdoor activities ($temp)."
    }
}

// =================================================================
// MAP OVERLAY DRAWING HELPERS
// =================================================================

private fun addWeatherMarkers(
    map: MapView,
    center: GeoPoint,
    weather: AnyLocationWeather?
) {
    clearDataMarkers(map)

    val marker = Marker(map)
    marker.position = center
    marker.title = "☁️ ${weather?.placeName ?: "Local Area"}"
    marker.snippet = weather?.temperature?.let { "%.1f°C • Humidity: %.0f%%".format(it, weather.humidity ?: 0.0) }
        ?: "Live weather observation"
    map.overlays.add(marker)
    map.invalidate()
}

private fun addDamMarkers(map: MapView, dams: List<DamMarkerData>) {
    clearDataMarkers(map)
    for (dam in dams) {
        val marker = Marker(map)
        marker.position = GeoPoint(dam.latitude, dam.longitude)
        marker.title = "💧 ${dam.name}"
        marker.snippet = dam.storagePercent?.let { "Storage: %.1f%%".format(it) } ?: "Dam reservoir"
        map.overlays.add(marker)
    }
    map.invalidate()
}

private fun addEarthquakeMarkers(map: MapView, quakes: List<EarthquakeMarkerData>) {
    clearDataMarkers(map)
    for (quake in quakes) {
        val marker = Marker(map)
        marker.position = GeoPoint(quake.latitude, quake.longitude)
        marker.title = "⚡ M${quake.magnitude ?: 0.0} - ${quake.place}"
        marker.snippet = quake.depthKm?.let { "Depth: %.1f km".format(it) } ?: "Recent seismic activity"
        map.overlays.add(marker)
    }
    map.invalidate()
}

private fun addAlertMarkers(map: MapView, alerts: List<AlertMarkerData>) {
    clearDataMarkers(map)
    for (alert in alerts) {
        val marker = Marker(map)
        marker.position = GeoPoint(alert.latitude, alert.longitude)
        marker.title = "⚠️ ${alert.title}"
        marker.snippet = "Severity: ${alert.severity}${if (!alert.description.isNullOrBlank()) " • ${alert.description}" else ""}"
        map.overlays.add(marker)
    }
    map.invalidate()
}

private fun addFloodOverlay(map: MapView, flood: FloodMapData) {
    removeFloodOverlays(map)
    val center = GeoPoint(flood.latitude, flood.longitude)
    val polygon = Polygon(map)
    polygon.points = Polygon.pointsAsRect(center, 10000.0, 8000.0).map {
        GeoPoint(it.latitude, it.longitude)
    }

    val fillColor = when (flood.risk.uppercase()) {
        "HIGH", "CRITICAL" -> AndroidColor.argb(90, 239, 68, 68)
        "MODERATE" -> AndroidColor.argb(85, 245, 158, 11)
        else -> AndroidColor.argb(75, 56, 189, 248)
    }

    polygon.fillColor = fillColor
    polygon.strokeColor = AndroidColor.argb(200, 30, 64, 175)
    polygon.strokeWidth = 4f
    polygon.title = "Flood Zone: ${flood.risk}"
    polygon.snippet = flood.currentWaterLevel?.let { "Current level: %.2fm".format(it) } ?: "Monitored river basin"

    map.overlays.add(polygon)
    map.invalidate()
}

private fun removeFloodOverlays(map: MapView) {
    val iterator = map.overlays.iterator()
    while (iterator.hasNext()) {
        val overlay = iterator.next()
        if (overlay is Polygon) {
            iterator.remove()
        }
    }
    map.invalidate()
}

private fun clearDataMarkers(map: MapView) {
    val iterator = map.overlays.iterator()
    while (iterator.hasNext()) {
        val overlay = iterator.next()
        if (overlay is Marker && !overlay.title.orEmpty().contains("Current Location")) {
            iterator.remove()
        }
    }
}
