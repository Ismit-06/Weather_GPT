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
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import kotlin.math.abs

// =================================================================
// TILE SOURCES (MapTiler Hybrid Satellite Imagery with Roads & Cities)
// =================================================================

private const val MAPTILER_API_KEY = "PdoGf1NbNNBb5nF6CYHI"

private val MapTilerHybridTileSource = object : OnlineTileSourceBase(
    "MapTilerHybrid",
    0,
    20,
    256,
    ".jpg",
    arrayOf("https://api.maptiler.com/maps/hybrid-v4/256/"),
    "© MapTiler © OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$x/$y.jpg?key=$MAPTILER_API_KEY"
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

    // Initialize MapViewModel with location
    LaunchedEffect(savedLocation) {
        mapViewModel.initialize(
            lat = savedLocation.latitude,
            lon = savedLocation.longitude,
            locName = savedLocation.name
        )
    }

    // Lifecycle observer for MapView - DO NOT call onDetach() here as that nulls mWriter and breaks tile loading permanently
    DisposableEffect(lifecycleOwner) {
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
            } catch (_: Throwable) {}
        }
    }

    // Reactively update Radar TilesOverlay when selected frame or layer changes
    LaunchedEffect(uiState.selectedLayer, uiState.selectedFrameIndex, uiState.radarFrames, uiState.radarHost) {
        val view = mapView ?: return@LaunchedEffect

        if (uiState.selectedLayer != "Rain") {
            currentRadarOverlay?.let {
                try {
                    view.overlays.remove(it)
                    it.onDetach(view)
                } catch (_: Throwable) {}
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
                currentRadarOverlay?.let {
                    view.overlays.remove(it)
                    it.onDetach(view)
                }

                val cleanHost = host.trimEnd('/')
                val cleanPath = if (frame.path.startsWith("/")) frame.path else "/${frame.path}"
                val tileSource = XYTileSource(
                    "RainViewer_${frame.time}",
                    0,
                    7,
                    256,
                    "/2/1_1.png",
                    arrayOf("$cleanHost$cleanPath/256/"),
                    "Weather data by RainViewer"
                )

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
                        osmConfig.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                        osmConfig.userAgentValue = "WeatherGPT/1.0 (contact@weathergpt.app; Android)"
                        val osmBaseDir = File(ctx.cacheDir, "osmdroid")
                        if (!osmBaseDir.exists()) osmBaseDir.mkdirs()
                        val osmTileDir = File(osmBaseDir, "tiles")
                        if (!osmTileDir.exists()) osmTileDir.mkdirs()
                        osmConfig.osmdroidBasePath = osmBaseDir
                        osmConfig.osmdroidTileCache = osmTileDir
                        osmConfig.tileDownloadThreads = 12.toShort()
                        osmConfig.tileFileSystemThreads = 12.toShort()
                        osmConfig.tileDownloadMaxQueueSize = 120.toShort()
                        osmConfig.cacheMapTileCount = 250.toShort()
                        osmConfig.cacheMapTileOvershoot = 80.toShort()
                    } catch (_: Throwable) {}

                    // High-resolution MapTiler Satellite Hybrid tiles
                    setTileSource(MapTilerHybridTileSource)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)

                    // Critical: Do NOT scale to DPI; this avoids 4x tile explosion per screen
                    isTilesScaledToDpi = false
                    isHorizontalMapRepetitionEnabled = true
                    isVerticalMapRepetitionEnabled = false

                    // Seamless dark loading canvas instead of flashing white grid
                    overlayManager.tilesOverlay.loadingBackgroundColor = AndroidColor.argb(255, 10, 22, 38)
                    overlayManager.tilesOverlay.loadingLineColor = AndroidColor.TRANSPARENT

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
        // COMPACT TOP OVERLAYS (One-line description & Layer chips)
        // =================================================================
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
        ) {
            // Minimal 1-line description badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = getLayerIcon(uiState.selectedLayer),
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (uiState.isRefreshing || uiState.isRadarLoading) {
                            "Updating live telemetry..."
                        } else {
                            "Live Satellite & ${uiState.selectedLayer} Telemetry"
                        },
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Text(
                    text = "RainViewer • MapTiler",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =================================================================
            // LAYER SELECTION CHIPS
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
        }

        // =================================================================
        // RADAR TIMELINE & PLAYBACK CONTROLS (Active on Rain Layer)
        // =================================================================
        if (uiState.selectedLayer == "Rain") {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
            ) {
                // Radar Player Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
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
                                    text = if (uiState.isPlayingRadar) "Live Animation" else "Radar Telemetry",
                                    color = PrimaryBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.radarTimestampText ?: "Checking frames...",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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

                                IconButton(
                                    onClick = {
                                        if (uiState.isPlayingRadar) {
                                            mapViewModel.pauseRadar()
                                        } else {
                                            mapViewModel.playRadar()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isPlayingRadar) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(22.dp)
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1845677D))
                                        .border(1.dp, PrimaryBlue, RoundedCornerShape(8.dp))
                                        .clickable { mapViewModel.selectLatestRadarFrame() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "LATEST",
                                        color = PrimaryBlue,
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
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                        tint = if (uiState.isRefreshing) PrimaryBlue else TextSecondary,
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
                        tint = PrimaryBlue,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.dp)
                        .background(BorderGlass)
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
        // BOTTOM WEATHER DETAILS CARD (COMPACT INLINE GLASS CARD)
        // =================================================================
        GlassCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(18.dp),
            padding = 12.dp
        ) {
            Column {
                // Header Row: Layer & Location Info + Risk Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x1245677D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getLayerIcon(uiState.selectedLayer),
                                contentDescription = uiState.selectedLayer,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = uiState.selectedLocationName.ifBlank { "Selected Location" },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "%.4f° N, %.4f° E • %s".format(
                                    uiState.selectedLatitude,
                                    uiState.selectedLongitude,
                                    uiState.selectedLayer
                                ),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Risk badge if available
                    val overallRisk = uiState.areaAnalysis?.overallRisk
                    if (overallRisk != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (overallRisk) {
                                "SAFE", "LOW" -> Color(0x185A8E72)
                                "MODERATE" -> Color(0x18C69A5B)
                                else -> Color(0x18B85D5D)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (overallRisk) {
                                    "SAFE", "LOW" -> SuccessGreen
                                    "MODERATE" -> WarningAmber
                                    else -> Color(0xFFB85D5D)
                                }
                            )
                        ) {
                            Text(
                                text = overallRisk,
                                color = when (overallRisk) {
                                    "SAFE", "LOW" -> SuccessGreen
                                    "MODERATE" -> WarningAmber
                                    else -> Color(0xFFB85D5D)
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val w = uiState.weatherData
                if (w != null) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Temperature and Condition row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = w.temperature?.let { "%.1f°C".format(it) } ?: "--",
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            w.apparentTemperature?.let {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Feels like %.1f°C".format(it),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x1245677D),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                        ) {
                            Text(
                                text = w.conditionText ?: "Live Weather",
                                color = PrimaryBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4-column compact metric tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AreaMetricBox(
                            label = "Rain Risk",
                            value = w.rainProbability?.let { "%.0f%%".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Humidity",
                            value = w.humidity?.let { "%.0f%%".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Wind",
                            value = w.windSpeed?.let { "%.1f m/s".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Pressure",
                            value = w.pressure?.let { "%.0f hPa".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Radar status line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF7F8F7))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Radar Observation",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = if (w.rainProbability != null && w.rainProbability > 50.0) {
                                "Active precipitation detected"
                            } else {
                                "Clear radar returns"
                            },
                            color = if (w.rainProbability != null && w.rainProbability > 50.0) {
                                WarningAmber
                            } else {
                                SuccessGreen
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryBlue,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading weather telemetry...",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) PrimaryBlue else Color.White)
            .border(
                1.dp,
                if (isSelected) PrimaryBlue else BorderGlass,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF7F8F7))
            .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = TextMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailMetricTile(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = TextMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
