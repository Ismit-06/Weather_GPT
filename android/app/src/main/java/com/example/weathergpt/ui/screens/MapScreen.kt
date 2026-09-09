package com.example.weathergpt.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weathergpt.audio.VoiceAssistantManager
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.ui.theme.WarningAmber
import com.example.weathergpt.viewmodel.AlertMarkerData
import com.example.weathergpt.viewmodel.AnyLocationWeather
import com.example.weathergpt.viewmodel.DamMarkerData
import com.example.weathergpt.viewmodel.EarthquakeMarkerData
import com.example.weathergpt.viewmodel.FloodMapData
import com.example.weathergpt.viewmodel.MapViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import kotlin.math.abs

// =================================================================
// TILE SOURCES (CartoDB Dark Matter with OSM attribution)
// =================================================================

private val CartoDarkTileSource = object : OnlineTileSourceBase(
    "CartoDarkMatter",
    0,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors, © CARTO"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$x/$y.png"
    }
}

@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by mapViewModel.uiState.collectAsState()

    val savedLocation = remember { LocationStore.getLocation(context) }
    val initialLocation = remember(savedLocation) {
        GeoPoint(savedLocation.latitude, savedLocation.longitude)
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var userLocationMarker by remember { mutableStateOf<Marker?>(null) }
    var selectedLocationMarker by remember { mutableStateOf<Marker?>(null) }
    var currentRadarOverlay by remember { mutableStateOf<TilesOverlay?>(null) }
    var showAiAnalysisDialog by remember { mutableStateOf(false) }

    val voiceAssistant = remember { VoiceAssistantManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            voiceAssistant.destroy()
        }
    }

    // Initialize MapViewModel with location
    LaunchedEffect(savedLocation) {
        mapViewModel.initialize(
            lat = savedLocation.latitude,
            lon = savedLocation.longitude,
            locName = savedLocation.name
        )
    }

    // Lifecycle observer for MapView
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try { mapView?.onResume() } catch (_: Throwable) {}
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try { mapView?.onPause() } catch (_: Throwable) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView?.onPause()
                mapView?.onDetach()
            } catch (_: Throwable) {}
        }
    }

    // Reactively update Radar TilesOverlay when selected frame or layer changes
    LaunchedEffect(uiState.selectedLayer, uiState.selectedFrameIndex, uiState.radarFrames, uiState.radarHost) {
        val view = mapView ?: return@LaunchedEffect

        if (uiState.selectedLayer != "Rain") {
            currentRadarOverlay?.let {
                try { view.overlays.remove(it) } catch (_: Throwable) {}
            }
            currentRadarOverlay = null
            try { view.invalidate() } catch (_: Throwable) {}
            return@LaunchedEffect
        }

        val host = uiState.radarHost
        val frames = uiState.radarFrames
        val idx = uiState.selectedFrameIndex

        if (!host.isNullOrBlank() && frames.isNotEmpty() && idx in frames.indices) {
            val frame = frames[idx]
            try {
                currentRadarOverlay?.let { view.overlays.remove(it) }

                val tileSource = object : OnlineTileSourceBase(
                    "RainViewer_${frame.time}",
                    0,
                    18,
                    256,
                    ".png",
                    arrayOf(host.trimEnd('/'))
                ) {
                    override fun getTileURLString(pMapTileIndex: Long): String {
                        val z = MapTileIndex.getZoom(pMapTileIndex)
                        val x = MapTileIndex.getX(pMapTileIndex)
                        val y = MapTileIndex.getY(pMapTileIndex)
                        val cleanPath = if (frame.path.startsWith("/")) frame.path else "/${frame.path}"
                        return "$baseUrl$cleanPath/256/$z/$x/$y/2/1_1.png"
                    }
                }

                val provider = MapTileProviderBasic(context.applicationContext, tileSource)
                val newOverlay = TilesOverlay(provider, context.applicationContext).apply {
                    loadingBackgroundColor = AndroidColor.TRANSPARENT
                }

                // Place radar overlay right above base map
                view.overlays.add(0, newOverlay)
                currentRadarOverlay = newOverlay
                view.invalidate()
            } catch (_: Throwable) {
                currentRadarOverlay = null
            }
        }
    }

    // Render Layer Markers (Dams, Quakes, Flood, Alerts, Weather)
    LaunchedEffect(
        uiState.selectedLayer,
        uiState.weatherData,
        uiState.dams,
        uiState.earthquakes,
        uiState.alerts,
        uiState.floodData
    ) {
        val view = mapView ?: return@LaunchedEffect
        clearFeatureOverlays(view)

        when (uiState.selectedLayer) {
            "Weather" -> {
                val pt = GeoPoint(uiState.selectedLatitude, uiState.selectedLongitude)
                addWeatherMarker(view, pt, uiState.weatherData)
            }
            "Dams" -> {
                addDamMarkers(view, uiState.dams)
            }
            "Quakes" -> {
                addEarthquakeMarkers(view, uiState.earthquakes)
            }
            "Alerts" -> {
                addAlertMarkers(view, uiState.alerts)
            }
            "Flood" -> {
                uiState.floodData?.let { addFloodPolygon(view, it) }
            }
        }
        try { view.invalidate() } catch (_: Throwable) {}
    }

    // Refresh spinning animation
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // =================================================================
        // MAP CANVAS
        // =================================================================
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    try {
                        val osmConfig = Configuration.getInstance()
                        osmConfig.userAgentValue = "WeatherGPT/1.0 (contact@weathergpt.app; Android)"
                        val osmBaseDir = File(ctx.cacheDir, "osmdroid")
                        if (!osmBaseDir.exists()) osmBaseDir.mkdirs()
                        val osmTileDir = File(osmBaseDir, "tiles")
                        if (!osmTileDir.exists()) osmTileDir.mkdirs()
                        osmConfig.osmdroidBasePath = osmBaseDir
                        osmConfig.osmdroidTileCache = osmTileDir
                    } catch (_: Throwable) {}

                    // High-performance dark map tiles
                    setTileSource(CartoDarkTileSource)
                    setMultiTouchControls(true)
                    controller.setZoom(12.0)
                    controller.setCenter(initialLocation)

                    // User Location Marker
                    try {
                        val marker = Marker(this).apply {
                            position = initialLocation
                            title = "📍 ${savedLocation.name}"
                            snippet = "Your Location"
                        }
                        overlays.add(marker)
                        userLocationMarker = marker
                    } catch (_: Throwable) {}

                    mapView = this

                    // Map click detector
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
                                        try {
                                            val geo = projection.fromPixels(event.x.toInt(), event.y.toInt())
                                            val point = GeoPoint(geo.latitude, geo.longitude)

                                            // Place / Move selected marker
                                            selectedLocationMarker?.let { overlays.remove(it) }
                                            val selMarker = Marker(this@apply).apply {
                                                position = point
                                                title = "📍 Selected Location"
                                                snippet = "%.4f, %.4f".format(point.latitude, point.longitude)
                                            }
                                            overlays.add(selMarker)
                                            selectedLocationMarker = selMarker

                                            controller.animateTo(point)
                                            invalidate()

                                            mapViewModel.selectMapLocation(point.latitude, point.longitude)
                                        } catch (_: Throwable) {}
                                    }
                                    return false
                                }
                            }
                            return false
                        }
                    })

                    try { invalidate() } catch (_: Throwable) {}
                }
            },
            update = { view ->
                mapView = view
            }
        )

        // =================================================================
        // HEADER BAR
        // =================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xD90A1626),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WEATHER INTELLIGENCE",
                        color = SecondaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Live Radar & Telemetry",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (uiState.isRefreshing || uiState.isRadarLoading) {
                            "Refreshing live data..."
                        } else {
                            "© OpenStreetMap · Radar by RainViewer"
                        },
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(PrimaryBlue, Color(0xFF1E40AF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getLayerIcon(uiState.selectedLayer),
                        contentDescription = "Map Layer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // =================================================================
        // LAYER SELECTION CHIPS
        // =================================================================
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp, start = 12.dp, end = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val layers = listOf("Weather", "Rain", "Flood", "Alerts", "Dams", "Quakes")
            layers.forEach { layer ->
                MapLayerChip(
                    name = layer,
                    isSelected = uiState.selectedLayer == layer,
                    onClick = { mapViewModel.selectLayer(layer) }
                )
            }
        }

        // =================================================================
        // RADAR TIMELINE & PLAYBACK CONTROLS (Active on Rain Layer)
        // =================================================================
        if (uiState.selectedLayer == "Rain") {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp, start = 14.dp, end = 14.dp)
                    .fillMaxWidth()
            ) {
                // Radar Player Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xE60A1626),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PRECIPITATION RADAR",
                                    color = SecondaryCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.radarTimestampText ?: "Scanning...",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.radarAgeText ?: "Checking frames...",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Playback Buttons: < | Play/Pause | > | Latest
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { mapViewModel.prevRadarFrame() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowLeft,
                                        contentDescription = "Previous frame",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlue)
                                        .clickable {
                                            if (uiState.isPlayingRadar) {
                                                mapViewModel.pauseRadar()
                                            } else {
                                                mapViewModel.playRadar()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isPlayingRadar) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (uiState.isPlayingRadar) "Pause radar" else "Play radar",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { mapViewModel.nextRadarFrame() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Next frame",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x334DA3FF))
                                        .border(1.dp, Color(0x664DA3FF), RoundedCornerShape(10.dp))
                                        .clickable { mapViewModel.selectLatestRadarFrame() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "LATEST",
                                        color = SecondaryCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Radar Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Light", color = TextMuted, fontSize = 9.sp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF00E5FF),
                                                Color(0xFF00E676),
                                                Color(0xFFFFEA00),
                                                Color(0xFFFF9100),
                                                Color(0xFFFF1744),
                                                Color(0xFFD500F9)
                                            )
                                        )
                                    )
                            )
                            Text(text = "Heavy", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // =================================================================
        // FLOATING MAP CONTROLS
        // =================================================================
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xD90A1626),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Refresh
                IconButton(
                    onClick = {
                        mapViewModel.refreshAll()
                        try { mapView?.invalidate() } catch (_: Throwable) {}
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh map",
                        tint = if (uiState.isRefreshing) SecondaryCyan else TextSecondary,
                        modifier = Modifier
                            .size(19.dp)
                            .then(if (uiState.isRefreshing) Modifier.graphicsLayer { rotationZ = spinAngle } else Modifier)
                    )
                }

                // Cycle layers
                IconButton(
                    onClick = {
                        val layers = listOf("Weather", "Rain", "Flood", "Alerts", "Dams", "Quakes")
                        val nextIdx = (layers.indexOf(uiState.selectedLayer) + 1) % layers.size
                        mapViewModel.selectLayer(layers[nextIdx])
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Toggle Layers",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Center on current location
                IconButton(
                    onClick = {
                        val cur = LocationStore.getLocation(context)
                        val target = GeoPoint(cur.latitude, cur.longitude)
                        try {
                            mapView?.controller?.animateTo(target)
                            mapView?.controller?.setZoom(13.0)
                            mapViewModel.centerOnUserLocation(cur.latitude, cur.longitude, cur.name)
                        } catch (_: Throwable) {}
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = SecondaryCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.dp)
                        .background(Color(0x3378BEFF))
                )

                // Zoom In
                IconButton(
                    onClick = { try { mapView?.controller?.zoomIn() } catch (_: Throwable) {} },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Zoom Out
                IconButton(
                    onClick = { try { mapView?.controller?.zoomOut() } catch (_: Throwable) {} },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // =================================================================
        // BOTTOM AREA CARD & ANALYZE BUTTON
        // =================================================================
        GlassCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
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
                        Icon(
                            imageVector = getLayerIcon(uiState.selectedLayer),
                            contentDescription = uiState.selectedLayer,
                            tint = SecondaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = uiState.selectedLocationName.ifBlank { "Selected Location" },
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Layer: ${uiState.selectedLayer}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Risk badge if available
                    val overallRisk = uiState.areaAnalysis?.overallRisk
                    if (overallRisk != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (overallRisk) {
                                "SAFE", "LOW" -> Color(0x2210B981)
                                "MODERATE" -> Color(0x22F59E0B)
                                else -> Color(0x22EF4444)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (overallRisk) {
                                    "SAFE", "LOW" -> SuccessGreen
                                    "MODERATE" -> WarningAmber
                                    else -> Color(0xFFFF6B6B)
                                }
                            )
                        ) {
                            Text(
                                text = overallRisk,
                                color = when (overallRisk) {
                                    "SAFE", "LOW" -> SuccessGreen
                                    "MODERATE" -> WarningAmber
                                    else -> Color(0xFFFF6B6B)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Quick metrics row
                val w = uiState.weatherData
                if (w != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AreaMetricBox(
                            label = "Temp",
                            value = w.temperature?.let { "%.1f°C".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Rain Risk",
                            value = w.rainProbability?.let { "%.0f%%".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Wind",
                            value = w.windSpeed?.let { "%.1f m/s".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Analyze This Area Button
                Button(
                    onClick = {
                        showAiAnalysisDialog = true
                        mapViewModel.analyzeCurrentArea()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isAnalyzingArea) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = if (uiState.isAnalyzingArea) "Analyzing Area..." else "Analyze This Area",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // =================================================================
        // AI ANALYSIS DIALOG (GROUNDED WEATHERGPT INTELLIGENCE)
        // =================================================================
        if (showAiAnalysisDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .clickable { showAiAnalysisDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xF20A1626),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🤖", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WeatherGPT Intelligence",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    voiceAssistant.stopSpeaking()
                                    showAiAnalysisDialog = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextMuted
                                )
                            }
                        }

                        Text(
                            text = uiState.selectedLocationName,
                            color = SecondaryCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (uiState.isAnalyzingArea) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = PrimaryBlue,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Synthesizing radar & weather telemetry...",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            val answer = uiState.aiAnalysisAnswer
                                ?: uiState.areaAnalysis?.recommendation
                                ?: "Conditions are normal around this location."

                            Text(
                                text = answer,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = {
                                        val speechText = uiState.aiAnalysisSpeech ?: answer
                                        voiceAssistant.speak(speechText, "en-IN")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speak analysis",
                                        tint = SecondaryCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================
// SUB-COMPONENTS
// =================================================================

@Composable
private fun MapLayerChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PrimaryBlue else Color(0xB30A1626))
            .border(
                1.dp,
                if (isSelected) Color(0xFF60A5FA) else BorderGlass,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun AreaMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x660A1626))
            .border(1.dp, Color(0x2278BEFF), RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = TextMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun getLayerIcon(layer: String): ImageVector {
    return when (layer) {
        "Rain" -> Icons.Default.Thunderstorm
        "Flood" -> Icons.Default.WaterDrop
        "Alerts" -> Icons.Default.Warning
        "Dams" -> Icons.Default.WaterDrop
        "Quakes" -> Icons.Default.Warning
        else -> Icons.Default.Cloud
    }
}

// =================================================================
// OVERLAY DRAWING HELPERS
// =================================================================

private fun clearFeatureOverlays(map: MapView) {
    try {
        val markersToRemove = map.overlays.filter { overlay ->
            overlay is Marker && !overlay.title.orEmpty().contains("Your Location") && !overlay.title.orEmpty().contains("Selected Location")
        }
        map.overlays.removeAll(markersToRemove)

        val polygonsToRemove = map.overlays.filterIsInstance<Polygon>()
        map.overlays.removeAll(polygonsToRemove)
    } catch (_: Throwable) {}
}

private fun addWeatherMarker(map: MapView, center: GeoPoint, weather: AnyLocationWeather?) {
    try {
        val marker = Marker(map).apply {
            position = center
            title = "☁️ ${weather?.placeName ?: "Local Area"}"
            snippet = weather?.temperature?.let { "%.1f°C • Humidity: %.0f%%".format(it, weather.humidity ?: 0.0) }
                ?: "Live weather observation"
        }
        map.overlays.add(marker)
    } catch (_: Throwable) {}
}

private fun addDamMarkers(map: MapView, dams: List<DamMarkerData>) {
    try {
        for (dam in dams) {
            val marker = Marker(map).apply {
                position = GeoPoint(dam.latitude, dam.longitude)
                title = "💧 ${dam.name}"
                snippet = dam.storagePercent?.let { "Storage: %.1f%%".format(it) } ?: "Dam reservoir"
            }
            map.overlays.add(marker)
        }
    } catch (_: Throwable) {}
}

private fun addEarthquakeMarkers(map: MapView, quakes: List<EarthquakeMarkerData>) {
    try {
        for (quake in quakes) {
            val marker = Marker(map).apply {
                position = GeoPoint(quake.latitude, quake.longitude)
                title = "⚡ M${quake.magnitude ?: 0.0} - ${quake.place}"
                snippet = quake.depthKm?.let { "Depth: %.1f km".format(it) } ?: "Recent seismic activity"
            }
            map.overlays.add(marker)
        }
    } catch (_: Throwable) {}
}

private fun addAlertMarkers(map: MapView, alerts: List<AlertMarkerData>) {
    try {
        for (alert in alerts) {
            val marker = Marker(map).apply {
                position = GeoPoint(alert.latitude, alert.longitude)
                title = "⚠️ ${alert.title}"
                snippet = "Severity: ${alert.severity}${if (!alert.description.isNullOrBlank()) " • ${alert.description}" else ""}"
            }
            map.overlays.add(marker)
        }
    } catch (_: Throwable) {}
}

private fun addFloodPolygon(map: MapView, flood: FloodMapData) {
    try {
        val center = GeoPoint(flood.latitude, flood.longitude)
        val polygon = Polygon(map).apply {
            points = Polygon.pointsAsRect(center, 12000.0, 9000.0).map {
                GeoPoint(it.latitude, it.longitude)
            }
            fillPaint.color = when (flood.risk.uppercase()) {
                "HIGH", "CRITICAL" -> AndroidColor.argb(90, 239, 68, 68)
                "MODERATE" -> AndroidColor.argb(85, 245, 158, 11)
                else -> AndroidColor.argb(75, 56, 189, 248)
            }
            fillPaint.style = Paint.Style.FILL
            outlinePaint.color = AndroidColor.argb(200, 30, 64, 175)
            outlinePaint.strokeWidth = 4f
            outlinePaint.style = Paint.Style.STROKE
            title = "Flood Zone: ${flood.risk}"
            snippet = flood.currentWaterLevel?.let { "Current level: %.2fm".format(it) } ?: "Monitored river basin"
        }
        map.overlays.add(polygon)
    } catch (_: Throwable) {}
}
