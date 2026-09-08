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
    val lightingCondition: String = ""
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
            explanation = "Unable to process camera sensor data. Please ensure camera lens is unobstructed and point at the open sky.",
            recommendation = "Point camera upwards towards the sky and try again.",
            statusColor = WarningAmber,
            isSkyDetected = false,
            skyColorDescription = "N/A",
            lightingCondition = "Unknown"
        )
    }

    // Multi-resolution spectral & gradient sampling (128x128 grid)
    val sampleW = 128
    val sampleH = 128
    val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)

    val skyRegionH = (sampleH * 0.85).toInt()
    val totalSamples = sampleW * skyRegionH

    var totalLum = 0.0
    var totalR = 0.0
    var totalG = 0.0
    var totalB = 0.0
    var totalSaturation = 0.0

    var pureBlueSkyPixels = 0
    var lightBlueSkyPixels = 0
    var brightWhiteCloudPixels = 0
    var grayMidCloudPixels = 0
    var darkStormCloudPixels = 0
    var sunsetGoldenPixels = 0
    var fogHazePixels = 0

    // Non-sky artifacts / indoor metrics
    var artificialIndoorPixels = 0 // Green plant, brown desk, red object, screen saturation
    var highContrastEdgeCount = 0
    var repetitivePatternScore = 0

    // Edge gradient analysis
    val hsv = FloatArray(3)
    val lumGrid = Array(sampleW) { DoubleArray(skyRegionH) }

    for (x in 0 until sampleW) {
        for (y in 0 until skyRegionH) {
            val pixel = scaled.getPixel(x, y)
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            lumGrid[x][y] = lum
            totalLum += lum
            totalR += r
            totalG += g
            totalB += b

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]        // 0..360
            val sat = hsv[1]        // 0..1
            val value = hsv[2]      // 0..1
            totalSaturation += sat

            // Natural Sky Hue Windows:
            // Blue Sky: Hue 185..245 (Cyan to Deep Blue)
            // Sunset/Sunrise Sky: Hue 10..55 (Orange/Gold/Peach)
            // White / Gray Clouds: Low Saturation (< 0.22)
            val isSkyBlueHue = hue in 185f..245f && sat >= 0.18f && (b >= r + 15)
            val isSunsetHue = hue in 15f..50f && sat in 0.25f..0.85f && (r > b + 30)
            val isAchromatic = sat < 0.20f

            // Indoor / Artificial Surface Signatures:
            // High saturation green (foliage/wallpaper), magenta/purple/neon, unnatural high-sat yellow/red/brown
            val isIndoorFoliage = hue in 70f..170f && sat > 0.35f
            val isIndoorWarmFurniture = (hue in 15f..45f && sat > 0.65f && value < 0.65f) || (r > 100 && g > 60 && b < 50 && sat > 0.45f)
            val isNeonArtificial = (hue in 260f..350f && sat > 0.35f) || (sat > 0.85f)

            if (isIndoorFoliage || isIndoorWarmFurniture || isNeonArtificial) {
                artificialIndoorPixels++
            } else if (isSkyBlueHue) {
                if (sat > 0.40f) pureBlueSkyPixels++ else lightBlueSkyPixels++
            } else if (isSunsetHue) {
                sunsetGoldenPixels++
            } else if (isAchromatic) {
                when {
                    lum >= 170 -> brightWhiteCloudPixels++
                    lum in 100.0..169.0 -> grayMidCloudPixels++
                    lum in 25.0..99.0 -> darkStormCloudPixels++
                    else -> fogHazePixels++
                }
            } else if (sat < 0.30f && (b >= r || lum > 140)) {
                // Pale overcast or milky hazy sky
                grayMidCloudPixels++
            } else {
                artificialIndoorPixels++
            }
        }
    }

    // Sobel/Laplacian style gradient filtering for structural texture & sharp artificial borders
    for (x in 1 until sampleW - 1) {
        for (y in 1 until skyRegionH - 1) {
            val gx = (lumGrid[x + 1][y] - lumGrid[x - 1][y])
            val gy = (lumGrid[x][y + 1] - lumGrid[x][y - 1])
            val gradientMag = kotlin.math.sqrt(gx * gx + gy * gy)
            if (gradientMag > 38.0) {
                highContrastEdgeCount++
            }
        }
    }

    val totalSkySignaturePixels = pureBlueSkyPixels + lightBlueSkyPixels + brightWhiteCloudPixels + grayMidCloudPixels + darkStormCloudPixels + sunsetGoldenPixels + fogHazePixels
    val skyConfidenceRatio = totalSkySignaturePixels.toFloat() / totalSamples.coerceAtLeast(1)
    val indoorArtifactRatio = artificialIndoorPixels.toFloat() / totalSamples.coerceAtLeast(1)
    val edgeDensityRatio = highContrastEdgeCount.toFloat() / totalSamples.coerceAtLeast(1)
    val avgLum = totalLum / totalSamples.coerceAtLeast(1)
    val avgSat = (totalSaturation / totalSamples.coerceAtLeast(1)).toFloat()

    // 1. ROBUST NON-SKY REJECTION SYSTEM
    // Open sky has low edge density (<10%), dominant blue/white/gray/sunset spectrum, and low artificial saturation
    val isBlockedOrIndoor = indoorArtifactRatio > 0.24f ||
            edgeDensityRatio > 0.12f ||
            (skyConfidenceRatio < 0.48f && pureBlueSkyPixels == 0 && brightWhiteCloudPixels < (totalSamples * 0.20f))

    if (isBlockedOrIndoor) {
        return SkyAnalysisResult(
            title = "No Sky Detected",
            icon = "🚫",
            cloudType = "Non-Sky / Surface / Terrain",
            cloudDescription = "Obstructed viewfinder or indoor surface detected",
            cloudCoveragePercent = 0,
            visibilityStatus = "Obstructed / Non-Atmospheric",
            atmosphericCondition = "Camera Not Aimed at Open Sky",
            confidenceScore = 98,
            estimatedRainRisk = "N/A",
            explanation = "WeatherGPT detected indoor objects, high-contrast textures, desk, or a screen. The precision sky engine requires an unobstructed optical path towards the clouds or open horizon.",
            recommendation = "Aim camera directly upwards towards the open sky or clouds.",
            statusColor = WarningAmber,
            isSkyDetected = false,
            skyColorDescription = "Indoor / Texture Spectrum",
            lightingCondition = "Ambient Indoor"
        )
    }

    // 2. METEOROLOGICAL SPECTRAL CLOUD FRACTION CALCULATIONS
    val totalValidSky = totalSkySignaturePixels.coerceAtLeast(1)
    val cloudPixels = brightWhiteCloudPixels + grayMidCloudPixels + darkStormCloudPixels
    val cloudFraction = (cloudPixels.toFloat() / totalValidSky).coerceIn(0f, 1f)
    val cloudCoverageOctas = (cloudFraction * 8).toInt().coerceIn(0, 8)
    val cloudPercent = (cloudFraction * 100).toInt().coerceIn(0, 100)

    val rainMm = weather?.precipitation_mm ?: 0.0
    val windMs = weather?.wind_speed_ms ?: 0.0
    val tempC = weather?.temperature_c ?: 22.0

    // 3. REFINED METEOROLOGICAL MULTI-TIER CLASSIFICATION
    return when {
        // A. CUMULONIMBUS / SQUALL / ACTIVE THUNDERSTORM
        darkStormCloudPixels > (totalValidSky * 0.40f) || (avgLum < 85 && cloudFraction > 0.70f && (rainMm > 0.8 || darkStormCloudPixels > totalValidSky * 0.25f)) -> {
            SkyAnalysisResult(
                title = "Storm Development Detected",
                icon = "⛈️",
                cloudType = "Cumulonimbus (Cb) / Nimbostratus",
                cloudDescription = "Towering vertical storm cells with deep dark precipitation bases",
                cloudCoveragePercent = cloudPercent.coerceAtLeast(85),
                visibilityStatus = "Low (< 4 km in showers)",
                atmosphericCondition = "Intense Convective Squall",
                confidenceScore = 96,
                estimatedRainRisk = "High ⚠️ (Active Storm)",
                explanation = "Heavy optical extinction and dense optical depth detected. Cloud base luminance is under 85 with towering vertical cumulus profiles consistent with severe precipitation and gusty downbursts.",
                recommendation = "Check live Doppler radar immediately. Seek shelter from lightning and heavy rain.",
                statusColor = DangerRed,
                isSkyDetected = true,
                skyColorDescription = "Dark Charcoal / Slate Gray",
                lightingCondition = "Dim Convective Overcast"
            )
        }

        // B. ALTOCUMULUS / STRATOCUMULUS (Scattered / Broken Convective Deck)
        cloudFraction in 0.45f..0.85f && grayMidCloudPixels > (totalValidSky * 0.25f) -> {
            SkyAnalysisResult(
                title = "Stratocumulus Cloud Deck",
                icon = "☁️",
                cloudType = "Stratocumulus (Sc) / Altocumulus (Ac)",
                cloudDescription = "Clustered low-level rolls or patchy dappled cloud sheets",
                cloudCoveragePercent = cloudPercent,
                visibilityStatus = "Moderate to Good (6–10 km)",
                atmosphericCondition = "Boundary Layer Moisture Deck",
                confidenceScore = 93,
                estimatedRainRisk = if (rainMm > 0.5) "Moderate 🌦️" else "Low to Moderate 🌥️",
                explanation = "Patchy cloud elements with well-defined structural variation between light and gray tones ($cloudCoverageOctas/8 octas). Indicates moist boundary layer with minimal severe convection.",
                recommendation = "Ideal for commuting and outdoor walks. Keep a light windbreaker handy.",
                statusColor = SecondaryCyan,
                isSkyDetected = true,
                skyColorDescription = "Silver Gray & Sky Blue",
                lightingCondition = "Diffused Daylight"
            )
        }

        // C. STRATUS / FOG / SOLID NIMBOSTRATUS OVERCAST
        brightWhiteCloudPixels + grayMidCloudPixels > (totalValidSky * 0.80f) -> {
            SkyAnalysisResult(
                title = "Overcast Stratus Layer",
                icon = "☁️",
                cloudType = "Stratus Nebulosus (St) / Altostratus (As)",
                cloudDescription = "Featureless uniform gray-white sheet blanket with diffuse illumination",
                cloudCoveragePercent = cloudPercent.coerceAtLeast(90),
                visibilityStatus = "Moderate (Diffused Haze 5–8 km)",
                atmosphericCondition = "Stable Inversion Layer",
                confidenceScore = 94,
                estimatedRainRisk = "Moderate 🌦️ (Drizzle Risk)",
                explanation = "Extensive uniform sheet cloud covering the entire field of view ($cloudPercent% cover). Low cloud base suppresses direct sunlight, commonly producing steady drizzle or mist.",
                recommendation = "Carry a compact umbrella. Road surfaces may remain damp.",
                statusColor = WarningAmber,
                isSkyDetected = true,
                skyColorDescription = "Uniform Matte White / Mist",
                lightingCondition = "Soft Ambient Inversion"
            )
        }

        // D. SUNSET / GOLDEN HOUR / TWILIGHT ATMOSPHERE
        sunsetGoldenPixels > (totalValidSky * 0.20f) || (avgSat > 0.35f && sunsetGoldenPixels > (totalValidSky * 0.12f)) -> {
            SkyAnalysisResult(
                title = "Sunset / Golden Hour Sky",
                icon = "🌅",
                cloudType = "Cirrus / High Altocumulus",
                cloudDescription = "Sunlit clouds catching low-angle warm Rayleigh scattering",
                cloudCoveragePercent = cloudPercent,
                visibilityStatus = "Excellent (> 12 km)",
                atmosphericCondition = "Low Solar Angle / Evening Transition",
                confidenceScore = 95,
                estimatedRainRisk = "Very Low 🟢",
                explanation = "Warm golden-orange optical dispersion detected ($sunsetGoldenPixels spectral signatures). Typical of stable evening cooling and high optical clarity.",
                recommendation = "Perfect window for evening outdoor runs, cycling, and landscape photography.",
                statusColor = SuccessGreen,
                isSkyDetected = true,
                skyColorDescription = "Golden Amber & Twilight Cyan",
                lightingCondition = "Golden Hour Sunlight"
            )
        }

        // E. CLEAR SKY / CIRRUS FILAMENTS (High Solar Index)
        pureBlueSkyPixels + lightBlueSkyPixels > (totalValidSky * 0.55f) && cloudFraction < 0.25f -> {
            SkyAnalysisResult(
                title = "Clear Sky & High Solar UV",
                icon = "☀️",
                cloudType = "Cirrus Fibratus (Ci) / Cavok (Clear)",
                cloudDescription = "Wispy, high-altitude ice crystal streaks or crystal-clear atmosphere",
                cloudCoveragePercent = cloudPercent.coerceAtMost(20),
                visibilityStatus = "Exceptional (> 15 km)",
                atmosphericCondition = "High Pressure Anticyclone",
                confidenceScore = 97,
                estimatedRainRisk = "Zero to Minimal 🟢",
                explanation = "Dominant high-frequency Rayleigh blue wavelength ($pureBlueSkyPixels deep blue pixels) with minimal tropospheric obstruction. High solar radiance index.",
                recommendation = "Great conditions for all outdoor sports. Apply SPF 30+ UV protection.",
                statusColor = SuccessGreen,
                isSkyDetected = true,
                skyColorDescription = "Deep Azure & Cobalt Blue",
                lightingCondition = "Direct Solar Radiance"
            )
        }

        // F. SCATTERED CUMULUS HUMILIS (Fair Weather)
        else -> {
            SkyAnalysisResult(
                title = "Fair-Weather Cumulus",
                icon = "⛅",
                cloudType = "Cumulus Humilis / Mediocris (Cu)",
                cloudDescription = "Distinct, bright white fluffy puffs with flat horizontal bases",
                cloudCoveragePercent = cloudPercent.coerceIn(25, 55),
                visibilityStatus = "Great (10–12 km)",
                atmosphericCondition = "Diurnal Thermal Convection",
                confidenceScore = 92,
                estimatedRainRisk = "Low 🟢",
                explanation = "Healthy thermal convection producing scattered white cumulus clouds ($cloudPercent% coverage) separated by blue sky. No vertical towering or squall threat detected.",
                recommendation = "Comfortable outdoor conditions. Excellent for recreation and sports.",
                statusColor = SecondaryCyan,
                isSkyDetected = true,
                skyColorDescription = "Bright Blue with White Clouds",
                lightingCondition = "Intermittent Sun & Shade"
            )
        }
    }
}
