package com.example.weathergpt.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weathergpt.audio.VoiceAssistantManager
import com.example.weathergpt.data.LocationReverseClient
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LanguageStore
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.location.SelectedLocation
import com.example.weathergpt.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val uiState by chatViewModel.uiState.collectAsState()
    val message = remember { mutableStateOf("") }

    // Location state
    val storedLocation by LocationStore.location.collectAsState()
    val isManualFlow by LocationStore.isManualFlow.collectAsState()
    val activeLocation = storedLocation ?: remember { LocationStore.getLocation(context) }
    val isManual = isManualFlow || LocationStore.isManual(context)

    var showLocationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }

    // Language
    val selectedLanguage by LanguageStore.languageFlow.collectAsState()
    var suggestionPageIndex by remember { mutableIntStateOf(0) }

    // ----------------------------------------------------------------
    // GPS helpers
    // ----------------------------------------------------------------
    suspend fun detectGpsLocation() {
        isDetectingLocation = true
        try {
            val provider = DeviceLocationProvider(context)
            val devLoc = provider.getCurrentLocation()
            if (devLoc != null) {
                var cityName = "Current location"
                var stateName: String? = null
                var countryName: String? = null
                try {
                    val rev = LocationReverseClient.api.reverse(devLoc.latitude, devLoc.longitude)
                    if (!rev.name.isNullOrBlank()) cityName = rev.name
                    stateName = rev.state
                    countryName = rev.country
                } catch (e: Exception) {
                    Log.w("ChatScreen", "Reverse geocode error: ${e.message}")
                    try {
                        @Suppress("DEPRECATION")
                        val geocoder = android.location.Geocoder(context, Locale.getDefault())
                        val addrs = geocoder.getFromLocation(devLoc.latitude, devLoc.longitude, 1)
                        val a = addrs?.firstOrNull()
                        if (a != null) {
                            val n = a.locality ?: a.subAdminArea ?: a.adminArea
                            if (!n.isNullOrBlank()) cityName = n
                            stateName = a.adminArea
                            countryName = a.countryName
                        }
                    } catch (_: Exception) {}
                }
                val newLoc = SelectedLocation(
                    name = cityName,
                    latitude = devLoc.latitude,
                    longitude = devLoc.longitude,
                    country = countryName,
                    admin1 = stateName,
                    timezone = "Asia/Kolkata"
                )
                LocationStore.useGps(context, newLoc)
                Toast.makeText(context, "Location: $cityName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not acquire GPS location.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            Log.e("ChatScreen", "Location detection error", e)
        } finally {
            isDetectingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            coroutineScope.launch { detectGpsLocation() }
        } else {
            Toast.makeText(context, "Location permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        LocationStore.initialize(context)
        LanguageStore.initialize(context)
        if (!LocationStore.isManual(context)) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                detectGpsLocation()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    // ----------------------------------------------------------------
    // Voice assistant
    // ----------------------------------------------------------------
    val voiceAssistant = remember { VoiceAssistantManager(context) }
    DisposableEffect(Unit) { onDispose { voiceAssistant.destroy() } }

    val isListening by voiceAssistant.isListening.collectAsState()
    val isSpeaking by voiceAssistant.isSpeaking.collectAsState()
    val rmsLevel by voiceAssistant.rmsLevel.collectAsState()
    var autoSpeakEnabled by remember { mutableStateOf(true) }

    // ----------------------------------------------------------------
    // Orb state — derived from app state
    // ----------------------------------------------------------------
    val orbState = when {
        uiState.error?.contains("internet", ignoreCase = true) == true -> OrbState.OFFLINE
        uiState.error?.contains("connection", ignoreCase = true) == true -> OrbState.OFFLINE
        uiState.error?.contains("host", ignoreCase = true) == true -> OrbState.OFFLINE
        uiState.error != null -> OrbState.ERROR
        uiState.isLoading -> OrbState.PROCESSING
        isSpeaking -> OrbState.AI_SPEAKING
        isListening && rmsLevel > 0.04f -> OrbState.USER_SPEAKING
        isListening -> OrbState.LISTENING
        else -> OrbState.IDLE
    }

    // ----------------------------------------------------------------
    // Single-turn messaging helper (clears previous query to keep instant answer)
    // ----------------------------------------------------------------
    fun sendText(textToSend: String) {
        val value = textToSend.trim()
        if (value.isBlank() || uiState.isLoading) return

        // Clear previous chat history so only the fresh instant answer is kept
        chatViewModel.clearChat()

        val langToSend = if (selectedLanguage.equals("Auto", ignoreCase = true)) "auto" else selectedLanguage
        chatViewModel.sendMessage(
            question = value,
            latitude = activeLocation.latitude,
            longitude = activeLocation.longitude,
            locationName = activeLocation.name,
            language = langToSend
        )
        message.value = ""
    }

    fun sendMessage() { sendText(message.value) }

    val onVoiceResult: (String) -> Unit = { spokenText ->
        val trimmed = spokenText.trim()
        if (trimmed.isNotBlank()) sendText(trimmed)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceAssistant.startListening(
                languageCode = selectedLanguage,
                onResult = onVoiceResult,
                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
            )
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVoiceListening() {
        if (isSpeaking) voiceAssistant.stopSpeaking()
        if (isListening) {
            voiceAssistant.stopListening()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                voiceAssistant.startListening(
                    languageCode = selectedLanguage,
                    onResult = onVoiceResult,
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                )
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Orb tap — state-specific behaviour
    fun handleOrbTap() {
        when (orbState) {
            OrbState.IDLE, OrbState.PAUSED -> toggleVoiceListening()
            OrbState.LISTENING,
            OrbState.USER_SPEAKING -> voiceAssistant.stopListening()
            OrbState.PROCESSING -> { /* intentionally no-op */ }
            OrbState.AI_SPEAKING -> voiceAssistant.stopSpeaking()
            OrbState.ERROR -> {
                val lastUser = uiState.messages.lastOrNull { it.role == "user" }
                if (lastUser != null) sendText(lastUser.content)
            }
            OrbState.OFFLINE ->
                Toast.makeText(context, "No internet connection. Please check your connection.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-speak assistant responses
    LaunchedEffect(uiState.messages.size) {
        val lastMsg = uiState.messages.lastOrNull()
        if (autoSpeakEnabled && lastMsg != null
            && lastMsg.role.lowercase() != "user"
            && !uiState.isLoading
        ) {
            val speechLang = if (!selectedLanguage.equals("Auto", ignoreCase = true)) {
                selectedLanguage
            } else {
                uiState.detectedLanguageCode ?: "en-IN"
            }
            voiceAssistant.speak(lastMsg.content, speechLang)
        }
    }

    // Suggestions list
    val allSuggestions = listOf(
        listOf(
            Triple("☂️", "Will it rain?", "Will it rain today in ${activeLocation.name}?"),
            Triple("🧳", "What to pack?", "What should I pack for the weather in ${activeLocation.name}?"),
            Triple("🛣️", "Road conditions", "Are roads safe in ${activeLocation.name}?")
        ),
        listOf(
            Triple("🌡️", "Hourly forecast", "What is the hourly temperature in ${activeLocation.name}?"),
            Triple("💨", "Wind & air", "What is the air quality and wind in ${activeLocation.name}?"),
            Triple("🧭", "Radar map", "Give me a satellite radar summary for ${activeLocation.name}.")
        )
    )

    // Orb state text & subtitles
    val orbStateText = when (orbState) {
        OrbState.IDLE -> "Tap to speak"
        OrbState.LISTENING -> "Listening..."
        OrbState.USER_SPEAKING -> "Listening..."
        OrbState.PROCESSING -> "Thinking..."
        OrbState.AI_SPEAKING -> "Speaking..."
        OrbState.PAUSED -> "Paused"
        OrbState.ERROR -> "Something went wrong"
        OrbState.OFFLINE -> "Offline"
    }
    val orbStateSubtitle = when (orbState) {
        OrbState.IDLE -> "Ask WeatherGPT anything"
        OrbState.LISTENING -> "Speak naturally"
        OrbState.USER_SPEAKING -> "I'm listening"
        OrbState.PROCESSING -> "Synthesizing forecast"
        OrbState.AI_SPEAKING -> "WeatherGPT is responding"
        OrbState.PAUSED -> "Tap to resume"
        OrbState.ERROR -> "Tap orb to retry"
        OrbState.OFFLINE -> "Check your connection"
    }

    // Status dot colour
    val statusColor = when (orbState) {
        OrbState.OFFLINE -> Color(0xFF7E8B9F)
        OrbState.ERROR -> Color(0xFFFF6B6B)
        OrbState.PROCESSING -> Color(0xFF52D9FF)
        OrbState.AI_SPEAKING -> Color(0xFF8B7CFF)
        OrbState.LISTENING,
        OrbState.USER_SPEAKING -> Color(0xFF52D9FF)
        else -> Color(0xFF36E6A0)
    }
    val statusText = when (orbState) {
        OrbState.PROCESSING -> "Thinking"
        OrbState.AI_SPEAKING -> "Speaking"
        OrbState.LISTENING,
        OrbState.USER_SPEAKING -> "Listening"
        OrbState.ERROR -> "Error"
        OrbState.OFFLINE -> "Offline"
        else -> "AI Online"
    }

    // Filtered latest query and response for the Instant Answer Tab
    val latestUserMessage = uiState.messages.lastOrNull { it.role.lowercase() == "user" }
    val latestAssistantMessage = uiState.messages.lastOrNull { it.role.lowercase() != "user" }
    val hasActiveAnswer = latestAssistantMessage != null && !uiState.isLoading

    // ================================================================
    //  ROOT SINGLE-PAGE LAYOUT (No Multi-Bubble Scrolling)
    // ================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A12))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ============================================================
        // 1. TOP HEADER (Branding, Status, Controls)
        // ============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4DA3FF), Color(0xFF2563EB)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text("WeatherGPT", color = Color(0xFFF5F7FA), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }

            // Right actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0E1626))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Auto-speak toggle
                IconButton(onClick = { autoSpeakEnabled = !autoSpeakEnabled }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Toggle auto-speak",
                        tint = if (autoSpeakEnabled) Color(0xFF52D9FF) else Color(0xFF7E8B9F),
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Clear/Dismiss current answer
                if (hasActiveAnswer || uiState.messages.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            voiceAssistant.stopSpeaking()
                            voiceAssistant.stopListening()
                            chatViewModel.clearChat()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "New query", tint = Color(0xFF7E8B9F), modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        // ============================================================
        // 2. CENTER STAGE: Living AI Orb + State Title
        // ============================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Living AI Orb (Sized gracefully so it fits on all devices)
            WeatherAIOrb(
                orbState = orbState,
                audioAmplitude = rmsLevel,
                size = if (hasActiveAnswer) 150.dp else 175.dp,
                onTap = { handleOrbTap() }
            )

            Spacer(Modifier.height(10.dp))

            // State title & subtitle
            Text(
                text = orbStateText,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = orbStateSubtitle,
                color = Color(0xFF7E8B9F),
                fontSize = 11.5.sp
            )
        }

        // ============================================================
        // 3. INSTANT ANSWER TAB OR IDLE CONTEXT CARDS
        // ============================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            AnimatedContent(
                targetState = when {
                    uiState.isLoading -> "loading"
                    hasActiveAnswer -> "answer"
                    uiState.error != null -> "error"
                    else -> "idle"
                },
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInVertically { it / 4 }) togetherWith
                    (fadeOut(animationSpec = tween(180)) + slideOutVertically { -it / 4 })
                },
                label = "center_content"
            ) { state ->
                when (state) {
                    "loading" -> {
                        // Instant Thinking Tab
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0E1626))
                                .border(1.dp, Color(0x3352D9FF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF52D9FF),
                                    strokeWidth = 2.dp
                                )
                                Column {
                                    Text(
                                        text = "WeatherGPT is analyzing telemetry...",
                                        color = Color(0xFFF5F7FA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (latestUserMessage != null) {
                                        Text(
                                            text = "\"${latestUserMessage.content}\"",
                                            color = Color(0xFF7E8B9F),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "answer" -> {
                        // Instant Answer Tab (Latest Single Response)
                        if (latestAssistantMessage != null) {
                            InstantAnswerCard(
                                question = latestUserMessage?.content,
                                answer = latestAssistantMessage.content,
                                isSpeaking = isSpeaking,
                                onSpeak = {
                                    if (isSpeaking) {
                                        voiceAssistant.stopSpeaking()
                                    } else {
                                        voiceAssistant.speak(
                                            latestAssistantMessage.content,
                                            uiState.detectedLanguageCode
                                        )
                                    }
                                },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("WeatherGPT Response", latestAssistantMessage.content))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = {
                                    voiceAssistant.stopSpeaking()
                                    chatViewModel.clearChat()
                                }
                            )
                        }
                    }

                    "error" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1F1010))
                                .border(1.dp, Color(0x44FF6B6B), RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.error ?: "Unable to contact weather service.",
                                    color = Color(0xFFFF8A8A),
                                    fontSize = 12.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { chatViewModel.clearChat() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFFF8A8A), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    else -> {
                        // Idle Mode: Location + Language Bar & Quick Action Chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Location + Language Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Location Chip
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF0E1626))
                                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(18.dp))
                                        .clickable { showLocationDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = Color(0xFF4DA3FF), modifier = Modifier.size(18.dp))
                                        Column {
                                            Text(
                                                text = activeLocation.name,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isManual) "Custom" else "GPS Auto",
                                                color = Color(0xFF7E8B9F),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                // Language Chip
                                Box(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF0E1626))
                                        .border(1.dp, Color(0x338B7CFF), RoundedCornerShape(18.dp))
                                        .clickable { showLanguageDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF8B7CFF), modifier = Modifier.size(18.dp))
                                        Column {
                                            val langDisplay = LanguageStore.SUPPORTED_LANGUAGES.find {
                                                it.code.equals(selectedLanguage, ignoreCase = true)
                                            }
                                            Text(
                                                text = langDisplay?.englishName ?: selectedLanguage,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Language",
                                                color = Color(0xFF7E8B9F),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Suggestions Row
                            val currentSuggestions = allSuggestions[suggestionPageIndex % allSuggestions.size]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                currentSuggestions.forEach { (icon, title, query) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF0E1626))
                                            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp))
                                            .clickable { sendText(query) }
                                            .padding(horizontal = 6.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = icon, fontSize = 11.sp)
                                            Text(
                                                text = title,
                                                color = Color(0xFFF5F7FA),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Next suggestions page button
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0E1626))
                                        .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                                        .clickable { suggestionPageIndex = (suggestionPageIndex + 1) % allSuggestions.size },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "More", tint = Color(0xFF8896AB), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        // 4. BOTTOM COMPOSER DOCK (Minimal text / voice bar)
        // ============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF0E1626))
                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(32.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = message.value,
                onValueChange = { message.value = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                enabled = !uiState.isLoading,
                textStyle = TextStyle(color = Color(0xFFF5F7FA), fontSize = 13.5.sp),
                cursorBrush = SolidColor(Color(0xFF4DA3FF)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                decorationBox = { innerTextField ->
                    if (message.value.isEmpty()) {
                        Text(
                            text = when {
                                isListening -> "Listening... Speak now"
                                isSpeaking -> "WeatherGPT is speaking..."
                                else -> "Ask anything or tap mic..."
                            },
                            color = when {
                                isListening -> Color(0xFF52D9FF)
                                isSpeaking -> Color(0xFF36E6A0)
                                else -> Color(0xFF7E8B9F)
                            },
                            fontSize = 13.5.sp
                        )
                    }
                    innerTextField()
                }
            )

            // Mic Icon Button
            IconButton(onClick = { toggleVoiceListening() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = if (isListening) Color(0xFFFF6B6B) else Color(0xFF8896AB),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Send Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.value.isNotBlank() && !uiState.isLoading) Color(0xFF4DA3FF)
                        else Color(0xFF1E3A5F)
                    )
                    .clickable(enabled = message.value.isNotBlank() && !uiState.isLoading) { sendMessage() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (message.value.isNotBlank() && !uiState.isLoading) Color.White else Color(0xFF88A4C7),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    // ================================================================
    //  DIALOGS
    // ================================================================
    if (showLocationDialog) {
        LocationSearchDialog(
            currentLocation = activeLocation.name,
            onDismiss = { showLocationDialog = false },
            isManualMode = isManual,
            onUseCurrentLocation = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    CoroutineScope(Dispatchers.Main + SupervisorJob()).launch { detectGpsLocation() }
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onLocationSelected = { locationResult ->
                val lat = locationResult.latitude
                val lon = locationResult.longitude
                if (lat != null && lon != null) {
                    val sel = SelectedLocation(
                        name = locationResult.name ?: "Selected Location",
                        latitude = lat,
                        longitude = lon,
                        country = locationResult.country,
                        admin1 = locationResult.admin1,
                        timezone = "Asia/Kolkata"
                    )
                    LocationStore.saveLocation(context, sel, manual = true)
                    Toast.makeText(context, "Location set to ${sel.name}", Toast.LENGTH_SHORT).show()
                }
                showLocationDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguageCode = selectedLanguage,
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                LanguageStore.saveLanguage(context, lang.code)
                showLanguageDialog = false
            }
        )
    }
}

// ================================================================
//  INSTANT ANSWER CARD (Single-turn focused response tab)
// ================================================================

@Composable
private fun InstantAnswerCard(
    question: String?,
    answer: String,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanDisplayText = remember(answer) {
        var t = answer
        if (t.contains("<think>")) {
            t = t.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        }
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val filtered = lines.filterNot { line ->
            val l = line.lowercase()
            l.startsWith("the user is asking") ||
            l.startsWith("let me look at") ||
            l.startsWith("looking at the data") ||
            l.startsWith("wait, let me reconsider") ||
            l.startsWith("hmm") ||
            l.startsWith("i think i'm") ||
            l.startsWith("let me just respond") ||
            l.startsWith("i'll respond in") ||
            l.startsWith("the user has been communicating") ||
            l.contains("overthinking") ||
            l.contains("respond naturally") ||
            l.contains("weather advisory or committee") ||
            (l.contains("could it be") && l.endsWith("?")) ||
            l.startsWith("the weather data provided is")
        }
        if (filtered.isNotEmpty()) filtered.joinToString("\n\n") else t
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0E1626))
            .border(1.dp, Color(0x334DA3FF), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header: Question + Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF4DA3FF), Color(0xFF2563EB)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = if (!question.isNullOrBlank()) "\"$question\"" else "Instant Answer",
                        color = Color(0xFF52D9FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close answer", tint = Color(0xFF7E8B9F), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Body: scrollable if long text, but compact container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = cleanDisplayText,
                    color = Color(0xFFF5F7FA),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Footer: Speak, Copy, Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF7E8B9F), modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak",
                            tint = if (isSpeaking) Color(0xFF52D9FF) else Color(0xFF7E8B9F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "Single-Turn Weather AI",
                    color = Color(0xFF536074),
                    fontSize = 10.sp
                )
            }
        }
    }
}
