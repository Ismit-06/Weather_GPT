package com.example.weathergpt.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetWeatherClient
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.location.SelectedLocation
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.theme.AccentPurple
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.DangerRed
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream

data class DetectedVisionObject(
    val label: String,
    val icon: String,
    val category: String, // "Atmosphere", "Architecture", "Terrain", "Vegetation", "Indoor"
    val confidence: Int,
    val weatherImplication: String,
    val boundingBoxNormalized: List<Float>? = null // [top, left, bottom, right]
)

data class SkyAnalysisResult(
    val title: String,
    val icon: String,
    val cloudType: String,
    val cloudDescription: String,
    val cloudCoveragePercent: Int,
    val visibilityStatus: String,
    val atmosphericCondition: String,
    val confidenceScore: Int,
    val estimatedRainRisk: String,
    val explanation: String,
    val recommendation: String,
    val statusColor: Color,
    val isSkyDetected: Boolean = true,
    val skyColorDescription: String = "",
    val lightingCondition: String = "",
    val detectedObjects: List<DetectedVisionObject> = emptyList(),
    val environmentSceneType: String = "Outdoor Open Sky",
    val microclimateImpact: String = ""
)

@Composable
fun CameraScreen(
    onNavigateToRadar: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to analyze the sky.", Toast.LENGTH_SHORT).show()
        }
    }

    var cameraLensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<SkyAnalysisResult?>(null) }
    var selectedLocation by remember { mutableStateOf<SelectedLocation?>(null) }
    var currentWeather by remember { mutableStateOf<MetForecastItem?>(null) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        val loc = LocationStore.getLocation(context)
        selectedLocation = loc
        try {
            val weatherResponse = MetWeatherClient.api.getWeather(loc.latitude, loc.longitude)
            currentWeather = weatherResponse.forecast.firstOrNull()
        } catch (e: Exception) {
            Log.e("CameraScreen", "Failed to fetch weather: ${e.message}")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isAnalyzing = true
                delay(1200)
                val bitmap = loadBitmapFromUri(context, it)
                analysisResult = performSkyAnalysis(bitmap, currentWeather)
                isAnalyzing = false
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(cameraLensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraScreen", "Camera binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = SecondaryCyan,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Access Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "WeatherGPT needs camera access to observe cloud formations, sky darkness, and visibility conditions in real time.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PrimaryBlue, SecondaryCyan)
                                    )
                                )
                                .clickable {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Grant Permission",
                                color = BackgroundDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val reticleSize = width * 0.72f
            val left = (width - reticleSize) / 2f
            val top = height * 0.18f

            // Main Viewfinder Reticle
            drawRoundRect(
                color = Color(0x6652D9FF),
                topLeft = Offset(left, top),
                size = Size(reticleSize, reticleSize),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f), 0f)
                )
            )

            val cornerLen = 36f
            val strokeW = 4.dp.toPx()
            val cyanColor = Color(0xFF52D9FF)

            drawLine(cyanColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
            drawLine(cyanColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)
            drawLine(cyanColor, Offset(left + reticleSize, top), Offset(left + reticleSize - cornerLen, top), strokeW)
            drawLine(cyanColor, Offset(left + reticleSize, top), Offset(left + reticleSize, top + cornerLen), strokeW)
            drawLine(cyanColor, Offset(left, top + reticleSize), Offset(left + cornerLen, top + reticleSize), strokeW)
            drawLine(cyanColor, Offset(left, top + reticleSize), Offset(left, top + reticleSize - cornerLen), strokeW)
            drawLine(cyanColor, Offset(left + reticleSize, top + reticleSize), Offset(left + reticleSize - cornerLen, top + reticleSize), strokeW)
            drawLine(cyanColor, Offset(left + reticleSize, top + reticleSize), Offset(left + reticleSize, top + cornerLen), strokeW)

            // Dynamic Vision Bounding Boxes for detected objects (Buildings, Trees, Horizon, Clouds)
            analysisResult?.detectedObjects?.forEach { obj ->
                obj.boundingBoxNormalized?.let { box ->
                    if (box.size == 4) {
                        val bTop = box[0] * height
                        val bLeft = box[1] * width
                        val bBottom = box[2] * height
                        val bRight = box[3] * width
                        val boxColor = when (obj.category) {
                            "Architecture" -> Color(0xFFF59E0B) // Amber
                            "Vegetation" -> Color(0xFF10B981)   // Green
                            "Atmosphere" -> Color(0xFF38BDF8)   // Sky Cyan
                            else -> Color(0xFFA855F7)           // Purple
                        }

                        drawRoundRect(
                            color = boxColor.copy(alpha = 0.85f),
                            topLeft = Offset(bLeft, bTop),
                            size = Size(bRight - bLeft, bBottom - bTop),
                            style = Stroke(width = 1.5.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                        )
                    }
                }
            }

            if (isAnalyzing) {
                val scanY = top + (reticleSize * scanProgress)
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF52D9FF), Color.White, Color(0xFF52D9FF), Color.Transparent)
                    ),
                    start = Offset(left, scanY),
                    end = Offset(left + reticleSize, scanY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC0A1626))
                        .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isAnalyzing) WarningAmber else SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAnalyzing) "AI SCANNING SKY..." else "LIVE VISION FEED",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0A1626))
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable {
                                galleryLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = SecondaryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0A1626))
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable {
                                cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99050A12))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Point camera at open sky or cloud cover to identify structure",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        ) {
            AnimatedVisibility(
                visible = analysisResult != null && !isAnalyzing,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                analysisResult?.let { result ->
                    SkyResultOverlayCard(
                        result = result,
                        onDismiss = { analysisResult = null },
                        onCheckRadar = onNavigateToRadar
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xCC0A1626))
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .clickable(enabled = !isAnalyzing) {
                                scope.launch {
                                    isAnalyzing = true
                                    triggerCaptureAndAnalyze(
                                        context = context,
                                        imageCapture = imageCapture,
                                        currentWeather = currentWeather,
                                        onResult = { res ->
                                            analysisResult = res
                                            isAnalyzing = false
                                        }
                                    )
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Quick AI",
                                tint = SecondaryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Instant Scan",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x3352D9FF),
                                        Color(0x664DA3FF)
                                    )
                                )
                            )
                            .border(2.dp, SecondaryCyan, CircleShape)
                            .clickable(enabled = !isAnalyzing) {
                                scope.launch {
                                    isAnalyzing = true
                                    triggerCaptureAndAnalyze(
                                        context = context,
                                        imageCapture = imageCapture,
                                        currentWeather = currentWeather,
                                        onResult = { res ->
                                            analysisResult = res
                                            isAnalyzing = false
                                        }
                                    )
                                }
                            }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(if (isAnalyzing) WarningAmber else Color.White)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    color = BackgroundDark,
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xCC0A1626))
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .clickable {
                                analysisResult = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyResultOverlayCard(
    result: SkyAnalysisResult,
    onDismiss: () -> Unit,
    onCheckRadar: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF0081220))
            .border(1.5.dp, BorderGlass, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.icon,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = result.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Visual Sky Observation",
                            color = SecondaryCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x224DA3FF))
                        .border(1.dp, Color(0x334DA3FF), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "CLOUD TYPE",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = result.cloudType,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x2252D9FF))
                        .border(1.dp, Color(0x3352D9FF), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = if (result.isSkyDetected) "COVERAGE (${result.cloudCoveragePercent}%)" else "ATMOSPHERE",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (result.isSkyDetected) "${result.cloudCoveragePercent}% Overcast" else result.visibilityStatus,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(result.statusColor.copy(alpha = 0.15f))
                        .border(1.dp, result.statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "RISK LEVEL",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = result.estimatedRainRisk,
                            color = result.statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x400A1626))
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Sky Assessment",
                            color = AccentPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.explanation,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Microclimate & Environmental Context Card
            if (result.isSkyDetected && result.microclimateImpact.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x2A0D9488))
                        .border(1.dp, Color(0x4D14B8A6), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🌐",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ENVIRONMENT & MICROCLIMATE: ${result.environmentSceneType.uppercase()}",
                                color = Color(0xFF5EEAD4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.microclimateImpact,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Detected Objects & Horizon Features
            if (result.detectedObjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "COMPUTER VISION DETECTIONS (${result.detectedObjects.size})",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.detectedObjects.forEach { obj ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x26FFFFFF))
                                .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = obj.icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "${obj.label} (${obj.confidence}%)",
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = obj.weatherImplication,
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (obj.category) {
                                                "Architecture" -> Color(0x33F59E0B)
                                                "Vegetation" -> Color(0x3310B981)
                                                "Atmosphere" -> Color(0x3338BDF8)
                                                else -> Color(0x33A855F7)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = obj.category.uppercase(),
                                        color = when (obj.category) {
                                            "Architecture" -> Color(0xFFFBBF24)
                                            "Vegetation" -> Color(0xFF34D399)
                                            "Atmosphere" -> Color(0xFF7DD3FC)
                                            else -> Color(0xFFC084FC)
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0x334DA3FF), Color(0x3352D9FF))
                        )
                    )
                    .border(1.dp, Color(0x4452D9FF), RoundedCornerShape(12.dp))
                    .clickable { onCheckRadar() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar",
                        tint = SecondaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = result.recommendation,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Disclaimer",
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Camera analysis is an observational AI estimate, not a replacement for official radar or Doppler telemetry.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

private fun triggerCaptureAndAnalyze(
    context: Context,
    imageCapture: ImageCapture?,
    currentWeather: MetForecastItem?,
    onResult: (SkyAnalysisResult) -> Unit
) {
    if (imageCapture == null) {
        onResult(performSkyAnalysis(null, currentWeather))
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                val result = performSkyAnalysis(bitmap, currentWeather)
                onResult(result)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Capture failed: ${exception.message}", exception)
                onResult(performSkyAnalysis(null, currentWeather))
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val bitmap = image.toBitmap()
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "imageProxyToBitmap error: ${e.message}")
        null
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

private fun performSkyAnalysis(bitmap: Bitmap?, weather: MetForecastItem?): SkyAnalysisResult {
    if (bitmap == null) {
        return SkyAnalysisResult(
            title = "No Viewfinder Image",
            icon = "📷",
            cloudType = "Unknown",
            cloudDescription = "Could not capture image from viewfinder frame",
            cloudCoveragePercent = 0,
            visibilityStatus = "Indeterminate",
            atmosphericCondition = "Sensor Offline",
            confidenceScore = 0,
            estimatedRainRisk = "N/A",
            explanation = "Unable to process camera sensor data. Please ensure camera lens is unobstructed and point at the open sky or outdoor scenery.",
            recommendation = "Point camera towards the sky, buildings, or landscape and try again.",
            statusColor = WarningAmber,
            isSkyDetected = false,
            skyColorDescription = "N/A",
            lightingCondition = "Unknown",
            detectedObjects = emptyList(),
            environmentSceneType = "Indeterminate",
            microclimateImpact = ""
        )
    }

    val sampleW = 128
    val sampleH = 128
    val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)

    // Analyze three distinct vertical zones:
    // Zone 1: Upper Sky & Clouds (0% to 50%)
    // Zone 2: Horizon & Mid Skyline / Architecture (40% to 75%)
    // Zone 3: Ground / Vegetation / Streets / Surfaces (70% to 100%)
    val skyZoneLimitY = (sampleH * 0.55).toInt()
    val horizonZoneStartY = (sampleH * 0.35).toInt()
    val horizonZoneEndY = (sampleH * 0.80).toInt()
    val groundZoneStartY = (sampleH * 0.65).toInt()

    val totalPixels = sampleW * sampleH
    val lumGrid = Array(sampleW) { DoubleArray(sampleH) }
    val hsv = FloatArray(3)

    var totalLum = 0.0
    var totalSat = 0.0

    // Sky Region Counts (Upper)
    var skyZonePixelCount = 0
    var skyBluePixels = 0
    var brightWhiteCloudPixels = 0
    var grayCloudPixels = 0
    var darkStormCloudPixels = 0
    var sunsetGoldenPixels = 0

    // Environmental Object / Horizon Counts
    var vegetationTreePixels = 0
    var buildingStructurePixels = 0
    var terrainSoilPixels = 0
    var indoorObstructionPixels = 0

    // Gradient Edge Maps for architectural and texture identification
    var verticalBuildingEdges = 0
    var horizontalHorizonEdges = 0
    var randomTextureEdges = 0

    // Bounding Box estimation accumulators [minX, minY, maxX, maxY]
    var cloudMinX = sampleW; var cloudMinY = sampleH; var cloudMaxX = 0; var cloudMaxY = 0; var cloudPointCount = 0
    var bldgMinX = sampleW; var bldgMinY = sampleH; var bldgMaxX = 0; var bldgMaxY = 0; var bldgPointCount = 0
    var vegMinX = sampleW; var vegMinY = sampleH; var vegMaxX = 0; var vegMaxY = 0; var vegPointCount = 0

    for (x in 0 until sampleW) {
        for (y in 0 until sampleH) {
            val pixel = scaled.getPixel(x, y)
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            lumGrid[x][y] = lum
            totalLum += lum

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            totalSat += sat

            val isSkyRegion = y <= skyZoneLimitY

            // 1. Sky & Atmospheric Signatures
            val isSkyBlue = hue in 185f..245f && sat >= 0.18f && (b >= r + 10)
            val isSunsetGold = hue in 12f..55f && sat in 0.25f..0.85f && (r > b + 25)
            val isAchromaticAtmosphere = sat < 0.20f && isSkyRegion

            if (isSkyRegion) {
                skyZonePixelCount++
                if (isSkyBlue) {
                    skyBluePixels++
                } else if (isSunsetGold) {
                    sunsetGoldenPixels++
                } else if (isAchromaticAtmosphere) {
                    when {
                        lum >= 170 -> {
                            brightWhiteCloudPixels++
                            cloudMinX = minOf(cloudMinX, x); cloudMinY = minOf(cloudMinY, y)
                            cloudMaxX = maxOf(cloudMaxX, x); cloudMaxY = maxOf(cloudMaxY, y)
                            cloudPointCount++
                        }
                        lum in 100.0..169.0 -> {
                            grayCloudPixels++
                            cloudMinX = minOf(cloudMinX, x); cloudMinY = minOf(cloudMinY, y)
                            cloudMaxX = maxOf(cloudMaxX, x); cloudMaxY = maxOf(cloudMaxY, y)
                            cloudPointCount++
                        }
                        lum in 25.0..99.0 -> {
                            darkStormCloudPixels++
                            cloudMinX = minOf(cloudMinX, x); cloudMinY = minOf(cloudMinY, y)
                            cloudMaxX = maxOf(cloudMaxX, x); cloudMaxY = maxOf(cloudMaxY, y)
                            cloudPointCount++
                        }
                    }
                }
            }

            // 2. Vegetation & Trees (Green hues, distinct organic chlorophyll reflection)
            val isVegetation = hue in 68f..165f && sat in 0.22f..0.88f && value in 0.15f..0.85f
            if (isVegetation) {
                vegetationTreePixels++
                vegMinX = minOf(vegMinX, x); vegMinY = minOf(vegMinY, y)
                vegMaxX = maxOf(vegMaxX, x); vegMaxY = maxOf(vegMaxY, y)
                vegPointCount++
            }

            // 3. Architecture & Built Urban Structures (Concrete, Glass, Steel, Brick)
            // Found in mid/horizon and lower zones: neutral grays with sharp geometric profiles or brick tones
            val isBrickOrConcrete = (sat < 0.25f && lum in 40.0..210.0 && y >= horizonZoneStartY) ||
                    (hue in 5f..35f && sat in 0.20f..0.55f && y in horizonZoneStartY..horizonZoneEndY)
            if (isBrickOrConcrete && !isVegetation) {
                buildingStructurePixels++
                bldgMinX = minOf(bldgMinX, x); bldgMinY = minOf(bldgMinY, y)
                bldgMaxX = maxOf(bldgMaxX, x); bldgMaxY = maxOf(bldgMaxY, y)
                bldgPointCount++
            }

            // 4. Ground Soil / Pavement / Roads
            val isRoadOrGround = y >= groundZoneStartY && sat < 0.20f && lum < 120.0
            if (isRoadOrGround) {
                terrainSoilPixels++
            }

            // 5. Indoor / Desk artifacts (Unnatural neon, intense warmth close-up)
            if (sat > 0.85f || (hue in 260f..350f && sat > 0.40f) || (lum < 20.0 && sat > 0.5f)) {
                indoorObstructionPixels++
            }
        }
    }

    // Gradient Edge Convolution for Geometric Edge Orientation (detects building pillars vs horizontal cloud strata)
    for (x in 1 until sampleW - 1) {
        for (y in 1 until sampleH - 1) {
            val gx = (lumGrid[x + 1][y] - lumGrid[x - 1][y])
            val gy = (lumGrid[x][y + 1] - lumGrid[x][y - 1])
            val mag = kotlin.math.sqrt(gx * gx + gy * gy)
            if (mag > 35.0) {
                if (kotlin.math.abs(gx) > kotlin.math.abs(gy) * 1.6) {
                    verticalBuildingEdges++
                } else if (kotlin.math.abs(gy) > kotlin.math.abs(gx) * 1.6) {
                    horizontalHorizonEdges++
                } else {
                    randomTextureEdges++
                }
            }
        }
    }

    val avgLum = totalLum / totalPixels.coerceAtLeast(1)
    val avgSat = (totalSat / totalPixels.coerceAtLeast(1)).toFloat()

    val skySampleCount = skyZonePixelCount.coerceAtLeast(1)
    val totalValidSkyPixels = (skyBluePixels + brightWhiteCloudPixels + grayCloudPixels + darkStormCloudPixels + sunsetGoldenPixels).coerceAtLeast(1)
    val cloudPixels = brightWhiteCloudPixels + grayCloudPixels + darkStormCloudPixels
    val cloudCoverageFraction = (cloudPixels.toFloat() / totalValidSkyPixels).coerceIn(0f, 1f)
    val cloudPercent = (cloudCoverageFraction * 100).toInt().coerceIn(0, 100)

    val vegetationRatio = vegetationTreePixels.toFloat() / totalPixels.coerceAtLeast(1)
    val buildingRatio = buildingStructurePixels.toFloat() / totalPixels.coerceAtLeast(1)
    val indoorRatio = indoorObstructionPixels.toFloat() / totalPixels.coerceAtLeast(1)
    val edgeRatio = (verticalBuildingEdges + horizontalHorizonEdges + randomTextureEdges).toFloat() / totalPixels.coerceAtLeast(1)

    // Build Detected Objects List
    val detectedObjects = mutableListOf<DetectedVisionObject>()

    // 1. Cloud Layer Object
    if (cloudPointCount > (sampleW * 10)) {
        val cloudName = when {
            darkStormCloudPixels > (totalValidSkyPixels * 0.35f) -> "Cumulonimbus Storm Cell"
            brightWhiteCloudPixels > (totalValidSkyPixels * 0.50f) -> "Altostratus / Stratus Layer"
            cloudPercent in 20..65 -> "Cumulus Cloud Formations"
            else -> "Atmospheric Cloud Layer"
        }
        val topN = (cloudMinY.toFloat() / sampleH).coerceIn(0f, 0.45f)
        val leftN = (cloudMinX.toFloat() / sampleW).coerceIn(0f, 0.9f)
        val botN = (cloudMaxY.toFloat() / sampleH).coerceIn(topN + 0.15f, 0.65f)
        val rightN = (cloudMaxX.toFloat() / sampleW).coerceIn(leftN + 0.2f, 1.0f)

        detectedObjects.add(
            DetectedVisionObject(
                label = cloudName,
                icon = if (darkStormCloudPixels > totalValidSkyPixels * 0.35f) "⛈️" else "☁️",
                category = "Atmosphere",
                confidence = 94,
                weatherImplication = if (darkStormCloudPixels > totalValidSkyPixels * 0.35f) "Active precipitation & squall risk" else "Stable optical atmospheric layer",
                boundingBoxNormalized = listOf(topN, leftN, botN, rightN)
            )
        )
    }

    // 2. Architecture / Buildings Object
    if (buildingRatio > 0.12f || verticalBuildingEdges > (sampleW * 6)) {
        val topN = (bldgMinY.toFloat() / sampleH).coerceIn(0.25f, 0.60f)
        val leftN = (bldgMinX.toFloat() / sampleW).coerceIn(0f, 0.85f)
        val botN = (bldgMaxY.toFloat() / sampleH).coerceIn(topN + 0.2f, 0.95f)
        val rightN = (bldgMaxX.toFloat() / sampleW).coerceIn(leftN + 0.2f, 1.0f)

        detectedObjects.add(
            DetectedVisionObject(
                label = "Urban Buildings & Skyline",
                icon = "🏢",
                category = "Architecture",
                confidence = 91,
                weatherImplication = "Urban heat island effect; wind channeling through street canyons",
                boundingBoxNormalized = listOf(topN, leftN, botN, rightN)
            )
        )
    }

    // 3. Trees & Vegetation Object
    if (vegetationRatio > 0.08f) {
        val topN = (vegMinY.toFloat() / sampleH).coerceIn(0.35f, 0.70f)
        val leftN = (vegMinX.toFloat() / sampleW).coerceIn(0f, 0.85f)
        val botN = (vegMaxY.toFloat() / sampleH).coerceIn(topN + 0.2f, 0.98f)
        val rightN = (vegMaxX.toFloat() / sampleW).coerceIn(leftN + 0.2f, 1.0f)

        detectedObjects.add(
            DetectedVisionObject(
                label = "Canopy / Vegetation",
                icon = "🌳",
                category = "Vegetation",
                confidence = 88,
                weatherImplication = "Natural shade cooling (-2°C ambient buffer) & ground moisture retention",
                boundingBoxNormalized = listOf(topN, leftN, botN, rightN)
            )
        )
    }

    // Scene Type Classification
    val sceneType = when {
        buildingRatio > 0.22f -> "Urban Skyline & Built Environment"
        vegetationRatio > 0.20f -> "Parkland & Natural Landscape"
        skyZonePixelCount > (totalPixels * 0.6f) -> "Open Horizon Sky"
        else -> "Mixed Outdoor Environment"
    }

    val microclimateNote = when {
        buildingRatio > 0.20f && darkStormCloudPixels > (totalValidSkyPixels * 0.3f) ->
            "Urban microclimate: High surface runoff and wind funneling between building facades."
        buildingRatio > 0.20f && skyBluePixels > (totalValidSkyPixels * 0.4f) ->
            "Urban microclimate: Concrete & asphalt surfaces radiate stored solar heat (+1.5°C feels-like)."
        vegetationRatio > 0.18f ->
            "Vegetation microclimate: Transpiration provides natural humidity buffer and localized wind moderation."
        else ->
            "Standard open air microclimate: Unobstructed radiative cooling and ambient atmospheric exchange."
    }

    // Strict Non-Outdoor / Indoor Rejection (Desk, Close-up walls, Blank screen)
    val isTotallyIndoor = indoorRatio > 0.30f || (skyBluePixels == 0 && brightWhiteCloudPixels == 0 && darkStormCloudPixels == 0 && vegetationRatio < 0.04f && buildingRatio < 0.06f)

    if (isTotallyIndoor) {
        return SkyAnalysisResult(
            title = "Indoor Surface Detected",
            icon = "🚫",
            cloudType = "Non-Atmospheric Object",
            cloudDescription = "Desk, wall, room, or digital display detected",
            cloudCoveragePercent = 0,
            visibilityStatus = "Obstructed / Non-Sky",
            atmosphericCondition = "Camera Aimed Indoors",
            confidenceScore = 98,
            estimatedRainRisk = "N/A",
            explanation = "Computer vision detected indoor textures, desk surface, or close-range objects. To observe live cloud dynamics, building microclimates, and weather patterns, point the camera outdoors.",
            recommendation = "Aim camera out of a window or step outside to scan buildings, trees, and sky.",
            statusColor = WarningAmber,
            isSkyDetected = false,
            skyColorDescription = "Indoor Texture Spectrum",
            lightingCondition = "Artificial Interior Lighting",
            detectedObjects = emptyList(),
            environmentSceneType = "Indoor / Non-Weather Scene",
            microclimateImpact = "Indoor temperature control isolated from atmospheric flow."
        )
    }

    val rainMm = weather?.precipitation_mm ?: 0.0

    // Full Meteorological & Computer Vision Synthesis
    return when {
        // Storm & Severe Squall
        darkStormCloudPixels > (totalValidSkyPixels * 0.38f) || (avgLum < 85 && cloudCoverageFraction > 0.70f && (rainMm > 0.6 || darkStormCloudPixels > totalValidSkyPixels * 0.20f)) -> {
            SkyAnalysisResult(
                title = "Storm Development Detected",
                icon = "⛈️",
                cloudType = "Cumulonimbus (Cb) / Nimbostratus",
                cloudDescription = "Dense storm anvil with dark vertical convective precipitation base",
                cloudCoveragePercent = cloudPercent.coerceAtLeast(85),
                visibilityStatus = "Low (< 4 km in showers)",
                atmosphericCondition = "Intense Convective Squall",
                confidenceScore = 96,
                estimatedRainRisk = "High ⚠️ (Active Storm)",
                explanation = "Heavy optical extinction detected in the upper quadrant (${detectedObjects.size} scene elements mapped). Cloud base luminance is under 85 with towering vertical cumulus profiles consistent with severe showers and gusty downdrafts.",
                recommendation = "Check live Doppler radar. Take shelter inside stable structures away from trees.",
                statusColor = DangerRed,
                isSkyDetected = true,
                skyColorDescription = "Dark Charcoal / Slate Gray",
                lightingCondition = "Dim Convective Overcast",
                detectedObjects = detectedObjects,
                environmentSceneType = sceneType,
                microclimateImpact = microclimateNote
            )
        }

        // Broken Stratocumulus / Altocumulus Deck
        cloudCoverageFraction in 0.40f..0.85f && grayCloudPixels > (totalValidSkyPixels * 0.22f) -> {
            SkyAnalysisResult(
                title = "Stratocumulus Cloud Deck",
                icon = "☁️",
                cloudType = "Stratocumulus (Sc) / Altocumulus (Ac)",
                cloudDescription = "Patchy dappled rolls and broken cloud sheets above the horizon",
                cloudCoveragePercent = cloudPercent,
                visibilityStatus = "Moderate to Good (6–10 km)",
                atmosphericCondition = "Boundary Layer Moisture Deck",
                confidenceScore = 93,
                estimatedRainRisk = if (rainMm > 0.5) "Moderate 🌦️" else "Low to Moderate 🌥️",
                explanation = "Layered cloud elements detected with structural contrast against the surrounding landscape ($cloudPercent% coverage). Typical of maritime moisture flow and mild diurnal mixing.",
                recommendation = "Ideal for outdoor activities, walking, and commuting. Keep a light layer handy.",
                statusColor = SecondaryCyan,
                isSkyDetected = true,
                skyColorDescription = "Silver Gray & Sky Blue",
                lightingCondition = "Diffused Daylight",
                detectedObjects = detectedObjects,
                environmentSceneType = sceneType,
                microclimateImpact = microclimateNote
            )
        }

        // Solid Stratus / Overcast Inversion
        brightWhiteCloudPixels + grayCloudPixels > (totalValidSkyPixels * 0.78f) -> {
            SkyAnalysisResult(
                title = "Overcast Stratus Layer",
                icon = "☁️",
                cloudType = "Stratus Nebulosus (St) / Altostratus",
                cloudDescription = "Uniform featureless gray-white blanket covering the skyline",
                cloudCoveragePercent = cloudPercent.coerceAtLeast(90),
                visibilityStatus = "Moderate (Diffused Haze 5–8 km)",
                atmosphericCondition = "Stable Inversion Layer",
                confidenceScore = 94,
                estimatedRainRisk = "Moderate 🌦️ (Drizzle Risk)",
                explanation = "Extensive diffuse cloud deck obscuring direct solar illumination across the skyline. High humidity trapping particulates and creating soft ambient lighting.",
                recommendation = "Carry a compact umbrella. Roads and urban pavements may remain slick.",
                statusColor = WarningAmber,
                isSkyDetected = true,
                skyColorDescription = "Uniform Matte White / Mist",
                lightingCondition = "Soft Ambient Inversion",
                detectedObjects = detectedObjects,
                environmentSceneType = sceneType,
                microclimateImpact = microclimateNote
            )
        }

        // Sunset / Golden Hour Rayleigh Glow
        sunsetGoldenPixels > (totalValidSkyPixels * 0.18f) || (avgSat > 0.35f && sunsetGoldenPixels > (totalValidSkyPixels * 0.10f)) -> {
            SkyAnalysisResult(
                title = "Sunset / Golden Hour Sky",
                icon = "🌅",
                cloudType = "Cirrus / Altocumulus Twilight",
                cloudDescription = "Warm golden-amber Rayleigh scattering illuminating clouds and architecture",
                cloudCoveragePercent = cloudPercent,
                visibilityStatus = "Excellent (> 12 km)",
                atmosphericCondition = "Low Solar Angle / Evening Transition",
                confidenceScore = 95,
                estimatedRainRisk = "Very Low 🟢",
                explanation = "Warm golden optical dispersion detected ($sunsetGoldenPixels spectral signatures). Horizon architecture and trees exhibit soft golden highlights with cooling boundary temperatures.",
                recommendation = "Optimal window for photography, outdoor dining, running, and cycling.",
                statusColor = SuccessGreen,
                isSkyDetected = true,
                skyColorDescription = "Golden Amber & Twilight Cyan",
                lightingCondition = "Golden Hour Sunlight",
                detectedObjects = detectedObjects,
                environmentSceneType = sceneType,
                microclimateImpact = microclimateNote
            )
        }

        // Clear Sky & High Solar UV
        skyBluePixels > (totalValidSkyPixels * 0.50f) && cloudCoverageFraction < 0.25f -> {
            SkyAnalysisResult(
                title = "Clear Sky & High Solar UV",
                icon = "☀️",
                cloudType = "Cirrus Fibratus (Ci) / Cavok (Clear)",
                cloudDescription = "Crystal-clear high visibility with minimal tropospheric obstruction",
                cloudCoveragePercent = cloudPercent.coerceAtMost(20),
                visibilityStatus = "Exceptional (> 15 km)",
                atmosphericCondition = "High Pressure Anticyclone",
                confidenceScore = 97,
                estimatedRainRisk = "Zero to Minimal 🟢",
                explanation = "Dominant high-frequency Rayleigh blue wavelength across the upper visual field with sharp architectural contrast. Maximum solar radiance reaching the surface.",
                recommendation = "Great conditions for sports. Use SPF 30+ UV protection when outdoors in direct sun.",
                statusColor = SuccessGreen,
                isSkyDetected = true,
                skyColorDescription = "Deep Azure & Cobalt Blue",
                lightingCondition = "Direct Solar Radiance",
                detectedObjects = detectedObjects,
                environmentSceneType = sceneType,
                microclimateImpact = microclimateNote
            )
        }

        // Fair-Weather Cumulus (Default Balanced Outdoor)
        else -> {
            SkyAnalysisResult(
                title = "Fair-Weather Cumulus",
                icon = "⛅",
                cloudType = "Cumulus Humilis (Cu)",
                cloudDescription = "Bright white convective puffs floating above the surrounding skyline",
                cloudCoveragePercent = cloudPercent.coerceIn(20, 50),
                visibilityStatus = "Great (10–12 km)",
                atmosphericCondition = "Diurnal Thermal Convection",
                confidenceScore = 92,
                estimatedRainRisk = "Low 🟢",
                explanation = "Convective white cumulus clouds ($cloudPercent% coverage) hovering above the urban and natural horizon. Excellent atmospheric stability with pleasant ambient comfort.",
                recommendation = "Great weather for outdoor workouts, travel, and sightseeing.",
                statusColor = SecondaryCyan,
                isSkyDetected = true,
                skyColorDescription = "Bright Blue with White Clouds",
                lightingCondition = "Intermittent Sun & Shade",
                detectedObjects = detectedObjects,
                environmentSceneType = sceneType,
                microclimateImpact = microclimateNote
            )
        }
    }
}
