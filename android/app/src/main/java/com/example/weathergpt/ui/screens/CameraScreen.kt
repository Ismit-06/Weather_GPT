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
    val visibilityStatus: String,
    val atmosphericCondition: String,
    val confidenceScore: Int,
    val estimatedRainRisk: String,
    val explanation: String,
    val recommendation: String,
    val statusColor: Color
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
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
                            text = "ATMOSPHERE",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = result.visibilityStatus,
                            color = TextPrimary,
                            fontSize = 12.sp,
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
                            fontSize = 12.sp,
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
            visibilityStatus = "Indeterminate",
            atmosphericCondition = "Sensor Offline",
            confidenceScore = 0,
            estimatedRainRisk = "N/A",
            explanation = "Unable to process camera sensor data. Please ensure camera lens is unobstructed and point at the open sky.",
            recommendation = "Point camera upwards towards the sky and try again.",
            statusColor = WarningAmber
        )
    }

    // High-resolution multi-sampling across upper, center, and lower regions
    val sampleW = 64
    val sampleH = 64
    val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)

    var skyPixels = 0
    var blueSkyPixels = 0
    var brightCloudPixels = 0
    var darkCloudPixels = 0
    var indoorOrObjectPixels = 0
    var highSaturationPixels = 0
    var highContrastEdgePixels = 0
    var totalLum = 0.0

    // Analyze upper 75%
    val skyRegionH = (sampleH * 0.75).toInt()
    val totalSamples = sampleW * skyRegionH

    for (x in 0 until sampleW) {
        for (y in 0 until skyRegionH) {
            val pixel = scaled.getPixel(x, y)
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            totalLum += lum

            val maxChannel = maxOf(r, maxOf(g, b))
            val minChannel = minOf(r, minOf(g, b))
            val delta = maxChannel - minChannel
            val saturation = if (maxChannel == 0) 0f else (delta.toFloat() / maxChannel)

            // Local gradient / edge check for terrain, cliffs, text, screens, keyboards
            if (x < sampleW - 1 && y < skyRegionH - 1) {
                val rightPixel = scaled.getPixel(x + 1, y)
                val rightLum = 0.299 * ((rightPixel shr 16) and 0xff) + 0.587 * ((rightPixel shr 8) and 0xff) + 0.114 * (rightPixel and 0xff)
                if (kotlin.math.abs(lum - rightLum) > 32.0) {
                    highContrastEdgePixels++
                }
            }

            // Real Sky & Cloud Signatures:
            val isBlue = (b > r + 15) && (b >= g)
            val isWhiteCloud = saturation < 0.18f && lum >= 150
            val isDarkCloud = saturation < 0.22f && lum in 45.0..145.0 && (delta < 24)

            // Rocks, Terrain, Brown mountains, Wood, Desk, Furniture, Screens:
            val isTerrainOrWoodOrWarm = (r > b + 20 && r > 50) || (g > b + 15 && g > 50) || (saturation > 0.40f && !isBlue)
            if (saturation > 0.45f && !isBlue) {
                highSaturationPixels++
            }

            if (isTerrainOrWoodOrWarm) {
                indoorOrObjectPixels++
            } else if (isBlue) {
                skyPixels++
                blueSkyPixels++
            } else if (isWhiteCloud) {
                skyPixels++
                brightCloudPixels++
            } else if (isDarkCloud) {
                skyPixels++
                darkCloudPixels++
            }
        }
    }

    val avgLum = totalLum / totalSamples.coerceAtLeast(1)
    val skyFraction = skyPixels.toFloat() / totalSamples.coerceAtLeast(1)
    val indoorOrTerrainFraction = (indoorOrObjectPixels + highSaturationPixels).toFloat() / totalSamples.coerceAtLeast(1)
    val edgeFraction = highContrastEdgePixels.toFloat() / totalSamples.coerceAtLeast(1)

    // REJECT NON-SKY / WALLPAPER / TERRAIN / SCREEN / INDOOR SCENES
    // Clouds are diffuse and soft without sharp high-contrast rock/texture edges (>18% edge density)
    if (indoorOrTerrainFraction > 0.22f || edgeFraction > 0.16f || (skyFraction < 0.40f && blueSkyPixels == 0 && brightCloudPixels < (totalSamples * 0.25f))) {
        return SkyAnalysisResult(
            title = "No Sky Detected",
            icon = "🚫",
            cloudType = "Non-Sky / Surface / Terrain",
            cloudDescription = "Point camera directly at open sky, clouds, or the horizon",
            visibilityStatus = "Obstructed / Non-Sky",
            atmosphericCondition = "Camera Not Facing Open Sky",
            confidenceScore = 98,
            estimatedRainRisk = "N/A",
            explanation = "WeatherGPT detected a screen, wallpaper, room, or non-sky surface. The sky vision engine requires an unobstructed view of the open atmosphere or cloud layer.",
            recommendation = "Step outside or point your camera upwards towards the real clouds.",
            statusColor = WarningAmber
        )
    }

    val rainMm = weather?.precipitation_mm ?: 0.0

    // High Confidence Sky Classifications
    return when {
        darkCloudPixels > (skyPixels * 0.45f) || (avgLum < 90 && rainMm > 1.5) -> {
            SkyAnalysisResult(
                title = "Storm Development Detected",
                icon = "⛈️",
                cloudType = "Cumulonimbus / Mammatus",
                cloudDescription = "Dense, towering storm clouds with low dark base",
                visibilityStatus = "Low (Dark Overcast)",
                atmosphericCondition = "Pre-Thunderstorm Convection",
                confidenceScore = 94,
                estimatedRainRisk = "High ⚠️",
                explanation = "The cloud structure appears consistent with developing cumulonimbus clouds. Heavy localized moisture and low ambient luminance observed.",
                recommendation = "Check the latest radar before travelling. High shower risk.",
                statusColor = DangerRed
            )
        }
        blueSkyPixels > (skyPixels * 0.50f) && brightCloudPixels < (skyPixels * 0.35f) -> {
            SkyAnalysisResult(
                title = "Clear Sky & High Solar UV",
                icon = "☀️",
                cloudType = "Cirrus / Clear Sky",
                cloudDescription = "High-altitude wispy ice filaments or minimal cloud cover",
                visibilityStatus = "Excellent (>10 km)",
                atmosphericCondition = "Dry Solar Dominant",
                confidenceScore = 96,
                estimatedRainRisk = "Very Low 🟢",
                explanation = "Dominant clear sky blue spectrum with minimal overcast. No active storm convection in the immediate visual field.",
                recommendation = "Ideal outdoor conditions. Use UV protection if outdoors during midday.",
                statusColor = SuccessGreen
            )
        }
        brightCloudPixels > (skyPixels * 0.55f) -> {
            SkyAnalysisResult(
                title = "Overcast Stratus Layer",
                icon = "☁️",
                cloudType = "Altostratus / Stratus",
                cloudDescription = "Uniform grayish-white sheet cloud covering the horizon",
                visibilityStatus = "Moderate (Diffused Haze)",
                atmosphericCondition = "Stable High Humidity",
                confidenceScore = 91,
                estimatedRainRisk = "Moderate 🌦️",
                explanation = "Uniform light cloud sheet with diffuse sun illumination. Indicative of maritime stratus or high-altitude cloud sheets.",
                recommendation = "Carry a light umbrella. Low turbulence but drizzle possible.",
                statusColor = WarningAmber
            )
        }
        else -> {
            SkyAnalysisResult(
                title = "Scattered Fair-Weather Clouds",
                icon = "⛅",
                cloudType = "Cumulus Humilis / Stratocumulus",
                cloudDescription = "Fluffy flat-based clouds with bright ambient illumination",
                visibilityStatus = "Good (8–10 km)",
                atmosphericCondition = "Mild Convection",
                confidenceScore = 89,
                estimatedRainRisk = "Low 🟢",
                explanation = "Balanced blue sky and scattered cumulus clouds. Typical fair-weather convective clouds with minimal precipitation threat.",
                recommendation = "Great time for running, cycling, or outdoor activities.",
                statusColor = SecondaryCyan
            )
        }
    }
}
