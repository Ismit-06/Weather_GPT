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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.window.Dialog
import com.example.weathergpt.data.SharedFriendStore
import com.example.weathergpt.data.SharedFriendWeather
import com.example.weathergpt.ui.theme.SurfaceDark
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

    // Shared Friend Weather & Location Pin state
    val sharedFriend by SharedFriendStore.sharedFriend.collectAsState()
    var friendMarkerOverlay by remember { mutableStateOf<Marker?>(null) }
    var isFriendCardVisible by remember { mutableStateOf(true) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    val friendDistanceKm = remember(sharedFriend, savedLocation) {
        sharedFriend?.let { f ->
            SharedFriendWeather.computeDistanceKm(
                lat1 = savedLocation.latitude,
                lon1 = savedLocation.longitude,
                lat2 = f.latitude,
                lon2 = f.longitude
            )
        }
    }

    // Sync Friend Location Pin on osmdroid Map
    LaunchedEffect(sharedFriend, mapView) {
        val view = mapView ?: return@LaunchedEffect
        val friend = sharedFriend

        friendMarkerOverlay?.let {
            try {
                view.overlays.remove(it)
                it.onDetach(view)
            } catch (_: Throwable) {}
            friendMarkerOverlay = null
        }

        if (friend != null) {
            try {
                val pt = GeoPoint(friend.latitude, friend.longitude)
                val marker = Marker(view).apply {
                    position = pt
                    title = "🧑 ${friend.name} • ${"%.1f".format(friend.temperature)}°C"
                    snippet = "${friend.condition} • ${friend.cityName}"
                    icon = createFriendMarkerDrawable(context, friend.name, friend.temperature)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        isFriendCardVisible = true
                        view.controller.animateTo(pt)
                        true
                    }
                }
                view.overlays.add(marker)
                friendMarkerOverlay = marker
                view.controller.animateTo(pt)
                view.controller.setZoom(13.5)
                view.invalidate()
                isFriendCardVisible = true
            } catch (_: Throwable) {}
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

            Spacer(modifier = Modifier.height(6.dp))

            // =================================================================
            // SHARE & FRIEND PIN ACTION BAR
            // =================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share My Weather Pin
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showShareDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share My Pin",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Friend's Pin / Locate Friend
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (sharedFriend != null) PrimaryBlue.copy(alpha = 0.12f) else Color.White,
                    border = BorderStroke(1.dp, if (sharedFriend != null) PrimaryBlue else BorderGlass),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (sharedFriend != null) {
                                sharedFriend?.let { f ->
                                    mapView?.controller?.animateTo(GeoPoint(f.latitude, f.longitude))
                                    mapView?.controller?.setZoom(13.5)
                                    isFriendCardVisible = true
                                }
                            } else {
                                showPasteDialog = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (sharedFriend != null) Icons.Default.LocationOn else Icons.Default.Link,
                            contentDescription = "Friend Pin",
                            tint = if (sharedFriend != null) PrimaryBlue else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sharedFriend != null) "🧑 ${sharedFriend!!.name}'s Pin" else "Locate Friend",
                            color = if (sharedFriend != null) PrimaryBlue else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
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
        // BOTTOM WEATHER DETAILS CARD (OR FRIEND WEATHER CARD)
        // =================================================================
        val activeFriend = sharedFriend
        if (activeFriend != null && isFriendCardVisible) {
            GlassCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(18.dp),
                padding = 12.dp
            ) {
                Column {
                    // Header Row: Friend Info + Distance + Close
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1845677D))
                                    .border(1.5.dp, PrimaryBlue, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🧑",
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${activeFriend.name}'s Location",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PrimaryBlue.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = "FRIEND PIN",
                                            color = PrimaryBlue,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${activeFriend.cityName} • 📍 ${friendDistanceKm?.let { "%.1f km away".format(it) } ?: "Shared location"}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { isFriendCardVisible = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize card",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Temperature and Condition row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.1f°C".format(activeFriend.temperature),
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shared ${SharedFriendWeather.formatRelativeTime(activeFriend.timestamp)}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x1245677D),
                            border = BorderStroke(1.dp, BorderGlass)
                        ) {
                            Text(
                                text = activeFriend.condition,
                                color = PrimaryBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3-column metric tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AreaMetricBox(
                            label = "Humidity",
                            value = "${activeFriend.humidity}%",
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Wind Speed",
                            value = "%.1f m/s".format(activeFriend.windSpeed),
                            modifier = Modifier.weight(1f)
                        )
                        AreaMetricBox(
                            label = "Distance",
                            value = friendDistanceKm?.let { "%.1f km".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                try {
                                    mapView?.controller?.animateTo(GeoPoint(activeFriend.latitude, activeFriend.longitude))
                                    mapView?.controller?.setZoom(14.0)
                                } catch (_: Throwable) {}
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Center", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=${activeFriend.latitude},${activeFriend.longitude}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    context.startActivity(mapIntent)
                                } catch (_: Throwable) {
                                    try {
                                        val webMap = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${activeFriend.latitude},${activeFriend.longitude}"))
                                        context.startActivity(webMap)
                                    } catch (_: Throwable) {}
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x1845677D)),
                            border = BorderStroke(1.dp, BorderGlass),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                SharedFriendStore.clearSharedFriend()
                                isFriendCardVisible = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x15EF4444)),
                            border = BorderStroke(1.dp, Color(0x33EF4444)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Pin", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
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

    // Dialogs
    if (showShareDialog) {
        ShareWeatherDialog(
            currentLocationName = savedLocation.name,
            latitude = savedLocation.latitude,
            longitude = savedLocation.longitude,
            weather = uiState.weatherData,
            onDismiss = { showShareDialog = false },
            onShare = { name ->
                val payload = SharedFriendWeather(
                    name = name,
                    latitude = savedLocation.latitude,
                    longitude = savedLocation.longitude,
                    cityName = savedLocation.name,
                    temperature = uiState.weatherData?.temperature ?: 0.0,
                    condition = uiState.weatherData?.conditionText ?: "Clear",
                    humidity = uiState.weatherData?.humidity?.toInt() ?: 0,
                    windSpeed = uiState.weatherData?.windSpeed ?: 0.0,
                    timestamp = System.currentTimeMillis()
                )
                val webUrl = SharedFriendWeather.encodeToWebUri(payload)
                val shareBody = "📍 ${payload.name} shared their weather & location from ${payload.cityName}!\n" +
                        "🌤️ ${payload.condition} • ${"%.1f".format(payload.temperature)}°C\n" +
                        "💧 Humidity: ${payload.humidity}% • 💨 Wind: ${"%.1f".format(payload.windSpeed)} m/s\n\n" +
                        "📱 View on WeatherGPT:\n$webUrl\n\n" +
                        "(Tip: In WeatherGPT ➔ Map ➔ tap 'Locate Friend' and paste this text to drop the pin!)"

                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareBody)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Weather & Location Pin"))
            }
        )
    }

    if (showPasteDialog) {
        PasteFriendLinkDialog(
            onDismiss = { showPasteDialog = false },
            onFriendLoaded = { friend ->
                SharedFriendStore.setSharedFriend(friend, triggerNavigation = false)
                isFriendCardVisible = true
            }
        )
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
            overlay is Marker &&
            !overlay.title.orEmpty().contains("Your Location") &&
            !overlay.title.orEmpty().contains("Selected Location") &&
            !overlay.title.orEmpty().contains("🧑")
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

private fun createFriendMarkerDrawable(context: Context, name: String, temp: Double): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (136 * density).toInt()
    val height = (52 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Pill background
    val rect = RectF(3 * density, 3 * density, (133 * density), (40 * density))
    val bgPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(245, 15, 23, 42)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(rect, 13 * density, 13 * density, bgPaint)

    // Neon border
    val borderPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(255, 56, 189, 248)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }
    canvas.drawRoundRect(rect, 13 * density, 13 * density, borderPaint)

    // Pointer needle
    val path = android.graphics.Path().apply {
        moveTo(width / 2f - 6 * density, 40 * density)
        lineTo(width / 2f, 48 * density)
        lineTo(width / 2f + 6 * density, 40 * density)
        close()
    }
    val needlePaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(255, 56, 189, 248)
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, needlePaint)

    // Text: 🧑 Name • 28°C
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        textSize = 11.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val cleanName = if (name.length > 7) name.take(6) + "…" else name
    val displayText = "🧑 $cleanName • ${"%.0f".format(temp)}°C"
    canvas.drawText(displayText, width / 2f, 25 * density, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
private fun ShareWeatherDialog(
    currentLocationName: String,
    latitude: Double,
    longitude: Double,
    weather: AnyLocationWeather?,
    onDismiss: () -> Unit,
    onShare: (name: String) -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf(SharedFriendStore.getSavedUserName(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📍 Share My Weather & Pin",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your friend will see a custom pointer on their map with your name, location, and live weather telemetry.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Your Name", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("Enter your name (e.g. Sai)", color = TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = Color(0x10FFFFFF),
                        unfocusedContainerColor = Color(0x08FFFFFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1245677D),
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PREVIEW OF SHARED TELEMETRY",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📍 $currentLocationName",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "🌡️ ${weather?.temperature?.let { "%.1f°C".format(it) } ?: "--"} • ${weather?.conditionText ?: "Live Weather"}",
                            color = PrimaryBlue,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "💧 Humidity: ${weather?.humidity?.let { "%.0f%%".format(it) } ?: "--"} • 💨 Wind: ${weather?.windSpeed?.let { "%.1f m/s".format(it) } ?: "--"}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val validName = nameInput.trim().ifBlank { "Friend" }
                        SharedFriendStore.saveUserName(context, validName)
                        onShare(validName)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share via WhatsApp / SMS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PasteFriendLinkDialog(
    onDismiss: () -> Unit,
    onFriendLoaded: (SharedFriendWeather) -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧑 Locate Friend's Weather Pin",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Paste the link or message you received from your friend to view their live pointer on your map.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorMessage = null
                    },
                    placeholder = { Text("Paste link or message here...", color = TextMuted, fontSize = 13.sp) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = Color(0x10FFFFFF),
                        unfocusedContainerColor = Color(0x08FFFFFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val parsed = SharedFriendWeather.parseFromUri(input)
                        if (parsed != null) {
                            onFriendLoaded(parsed)
                            onDismiss()
                            Toast.makeText(context, "📍 Found ${parsed.name}'s location in ${parsed.cityName}!", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = "Could not find a valid WeatherGPT share link in the input. Please check and try again."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Drop Pin on Map", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

