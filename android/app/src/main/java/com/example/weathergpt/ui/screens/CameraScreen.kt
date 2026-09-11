package com.example.weathergpt.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weathergpt.sky.SkyDecisionState
import com.example.weathergpt.sky.SkyWeatherFusionReport
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
import com.example.weathergpt.viewmodel.SkyAiViewModel
import kotlinx.coroutines.launch
import java.io.InputStream

@Composable
fun CameraScreen(
    onNavigateToRadar: () -> Unit = {},
    skyViewModel: SkyAiViewModel = viewModel()
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
            Toast.makeText(context, "Camera permission needed to observe the sky.", Toast.LENGTH_SHORT).show()
        }
    }

    var cameraLensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val liveState by skyViewModel.liveDecisionState.collectAsState()
    val liveEvaluation by skyViewModel.liveEvaluation.collectAsState()
    val isAnalyzing by skyViewModel.isAnalyzing.collectAsState()
    val fusionReport by skyViewModel.fusionReport.collectAsState()
    val debugTelemetry by skyViewModel.debugTelemetry.collectAsState()

    DisposableEffect(Unit) {
        skyViewModel.startSensors()
        onDispose {
            skyViewModel.stopSensors()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        skyViewModel.refreshLocationAndWeather()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bitmap = loadBitmapFromUri(context, it)
                if (bitmap != null) {
                    skyViewModel.analyzeCapturedBitmap(bitmap)
                } else {
                    Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen Live Viewfinder
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

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also {
                                it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    skyViewModel.processLiveFrame(imageProxy)
                                }
                            }

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(cameraLensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                analysis
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
            // Minimal Permission State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC0D1520))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                        .padding(28.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Access Required",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enable camera to observe cloud conditions and sky features in real time.",
                            fontSize = 13.sp,
                            color = Color(0xFF9EABB9),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Allow Camera",
                                color = Color(0xFF0D1520),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Minimal Clean Focus Bracket (Classy subtle white/green corners)
        val bracketColor = when {
            liveState == SkyDecisionState.VALID_SKY -> Color.White.copy(alpha = 0.75f)
            liveState == SkyDecisionState.CEILING || liveState == SkyDecisionState.INDOOR || liveState == SkyDecisionState.BEDSHEET_OR_FABRIC || liveState == SkyDecisionState.NO_SKY_DETECTED -> DangerRed.copy(alpha = 0.85f)
            liveState == SkyDecisionState.TOO_BLURRY || liveState == SkyDecisionState.CAMERA_MOVING || liveState == SkyDecisionState.INSUFFICIENT_SKY || liveState == SkyDecisionState.OBSTRUCTED_SKY -> WarningAmber.copy(alpha = 0.85f)
            else -> Color.White.copy(alpha = 0.45f)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val frameSize = width * 0.72f
            val left = (width - frameSize) / 2f
            val top = height * 0.22f
            val cornerLen = 24f
            val strokeW = 1.5.dp.toPx()

            // Top-left corner
            drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)

            // Top-right corner
            drawLine(bracketColor, Offset(left + frameSize, top), Offset(left + frameSize - cornerLen, top), strokeW)
            drawLine(bracketColor, Offset(left + frameSize, top), Offset(left + frameSize - cornerLen, top), strokeW)

            // Bottom-left corner
            drawLine(bracketColor, Offset(left, top + frameSize), Offset(left + cornerLen, top + frameSize), strokeW)
            drawLine(bracketColor, Offset(left, top + frameSize), Offset(left, top + frameSize - cornerLen), strokeW)

            // Bottom-right corner
            drawLine(bracketColor, Offset(left + frameSize, top + frameSize), Offset(left + frameSize - cornerLen, top + frameSize), strokeW)
            drawLine(bracketColor, Offset(left + frameSize, top + frameSize), Offset(left + frameSize, top + frameSize - cornerLen), strokeW)
        }

        // Minimal Top Floating Bar (Clean pill badge & utility controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Classy minimal status capsule
                val statusDotColor = when {
                    isAnalyzing -> WarningAmber
                    liveState == SkyDecisionState.VALID_SKY -> SuccessGreen
                    liveState == SkyDecisionState.CEILING || liveState == SkyDecisionState.INDOOR || liveState == SkyDecisionState.BEDSHEET_OR_FABRIC || liveState == SkyDecisionState.NO_SKY_DETECTED -> DangerRed
                    liveState == SkyDecisionState.TOO_BLURRY || liveState == SkyDecisionState.CAMERA_MOVING || liveState == SkyDecisionState.INSUFFICIENT_SKY || liveState == SkyDecisionState.OBSTRUCTED_SKY -> WarningAmber
                    else -> Color.White.copy(alpha = 0.6f)
                }

                val statusLabel = when {
                    isAnalyzing -> "Analyzing..."
                    liveState == SkyDecisionState.VALID_SKY -> "Sky in view"
                    liveState == SkyDecisionState.BEDSHEET_OR_FABRIC -> "Fabric detected"
                    liveState == SkyDecisionState.CEILING -> "Ceiling detected"
                    liveState == SkyDecisionState.INDOOR -> "Indoor scene"
                    liveState == SkyDecisionState.NO_SKY_DETECTED -> "No sky in view"
                    liveState == SkyDecisionState.INSUFFICIENT_SKY -> "Tilt camera up"
                    liveState == SkyDecisionState.OBSTRUCTED_SKY -> "Sky obstructed"
                    liveState == SkyDecisionState.TOO_BLURRY -> "Blurry"
                    liveState == SkyDecisionState.CAMERA_MOVING -> "Stabilizing..."
                    liveState == SkyDecisionState.TOO_DARK -> "Too dark"
                    liveState == SkyDecisionState.OVEREXPOSED || liveState == SkyDecisionState.EXCESSIVE_GLARE -> "High glare"
                    else -> "Aim at sky"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xB30B121C))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = statusLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Clean Action buttons (Photo Library, Flip Camera)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xB30B121C))
                            .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Import Image",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xB30B121C))
                            .border(1.dp, Color(0x1AFFFFFF), CircleShape)
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
                            contentDescription = "Flip Camera",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Subtle Hint strip (only if non-sky or issue)
            if (liveState != SkyDecisionState.VALID_SKY && liveState != SkyDecisionState.UNCERTAIN && !isAnalyzing) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD90B121C))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = liveState.userMessage,
                        color = Color(0xFFCAD5E2),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Development Debug Telemetry Badge (if available from server response)
            debugTelemetry?.let { dbg ->
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC050A10))
                        .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Sky AI Debug: ${dbg.modelVersion} | Sky: ${dbg.skyDetected} (${(dbg.confidence * 100).toInt()}%) | Scene: ${dbg.sceneType} | ${dbg.latencyMs}ms",
                        color = Color(0xFF7DD3FC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // Bottom Controls & Clean Slide-up Sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 76.dp)
        ) {
            // Elegant Result Sheet
            AnimatedVisibility(
                visible = fusionReport != null && !isAnalyzing,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                fusionReport?.let { report ->
                    SkyFusionMinimalResult(
                        report = report,
                        onDismiss = { skyViewModel.clearResult() },
                        onCheckRadar = onNavigateToRadar
                    )
                }
            }

            // Shutter Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Quick Observe Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xB30B121C))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                            .clickable(enabled = !isAnalyzing) {
                                triggerCapture(context, imageCapture) { bmp ->
                                    if (bmp != null) {
                                        skyViewModel.analyzeCapturedBitmap(bmp)
                                    } else {
                                        Toast.makeText(context, "Could not capture viewfinder frame.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Observe",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Center: Minimal Shutter Button
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            .clickable(enabled = !isAnalyzing) {
                                triggerCapture(context, imageCapture) { bmp ->
                                    if (bmp != null) {
                                        skyViewModel.analyzeCapturedBitmap(bmp)
                                    } else {
                                        Toast.makeText(context, "Failed to capture.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }

                    // Right: Clear/Reset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xB30B121C))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                            .clickable { skyViewModel.clearResult() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Reset",
                            color = Color(0xFFAAB6C7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimal, Classy, Lightweight Weather Observation Card
 */
@Composable
private fun SkyFusionMinimalResult(
    report: SkyWeatherFusionReport,
    onDismiss: () -> Unit,
    onCheckRadar: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xEE0B121C))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.visualSkyCondition,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val coverageText = report.visualSkyCoveragePct?.let { "$it% Sky Visibility  •  " } ?: ""
                    val modelInfo = report.modelVersion?.let { " ($it)" } ?: ""
                    Text(
                        text = "$coverageText${report.aiConfidenceLabel}$modelInfo",
                        color = Color(0xFF9EABB9),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color(0xFFCAD5E2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Observation & Context Paragraph
            Text(
                text = report.visualObservationSummary,
                color = Color(0xFFE2E8F0),
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Short-Term Outlook Section (0-1h, 1-3h, 3-6h)
            if (report.shortTermOutlook1h != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x331E293B))
                        .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SHORT-TERM OUTLOOK",
                            color = Color(0xFF7DD3FC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Text(text = "Next 1h: ", color = Color(0xFFBAE6FD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = report.shortTermOutlook1h, color = Color.White, fontSize = 12.sp)
                        }
                        if (report.shortTermOutlook3h != null) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(text = "Next 1–3h: ", color = Color(0xFFBAE6FD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = report.shortTermOutlook3h, color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (report.shortTermOutlook6h != null) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(text = "Next 3–6h: ", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                Text(text = report.shortTermOutlook6h, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Telemetry Clean Minimal Chip Row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x44141E2D))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = report.meteorologicalSummary,
                    color = Color(0xFFA5B4CB),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Disclaimer strip
            Text(
                text = report.limitsAndDisclaimer,
                color = Color(0xFF64748B),
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Radar Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x3338BDF8))
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(12.dp))
                    .clickable { onCheckRadar() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = Color(0xFF7DD3FC),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View Precipitation Radar",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun triggerCapture(
    context: Context,
    imageCapture: ImageCapture?,
    onBitmapReady: (Bitmap?) -> Unit
) {
    if (imageCapture == null) {
        onBitmapReady(null)
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                onBitmapReady(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Capture failed: ${exception.message}", exception)
                onBitmapReady(null)
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val bitmap = image.toBitmap()
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
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
