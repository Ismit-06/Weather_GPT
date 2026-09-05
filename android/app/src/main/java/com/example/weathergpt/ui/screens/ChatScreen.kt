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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
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
import com.example.weathergpt.viewmodel.ChatViewModel
import com.example.weathergpt.audio.VoiceAssistantManager
import com.example.weathergpt.data.LocationReverseClient
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LanguageStore
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.location.SelectedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    val context       = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val uiState by chatViewModel.uiState.collectAsState()
    val message  = remember { mutableStateOf("") }

    // Location state
    val storedLocation  by LocationStore.location.collectAsState()
    val isManualFlow    by LocationStore.isManualFlow.collectAsState()
    val activeLocation   = storedLocation ?: remember { LocationStore.getLocation(context) }
    val isManual         = isManualFlow || LocationStore.isManual(context)

    var showLocationDialog  by remember { mutableStateOf(false) }
    var showLanguageDialog  by remember { mutableStateOf(false) }
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
            val devLoc   = provider.getCurrentLocation()
            if (devLoc != null) {
                var cityName    = "Current location"
                var stateName: String?   = null
                var countryName: String? = null
                try {
                    val rev = LocationReverseClient.api.reverse(devLoc.latitude, devLoc.longitude)
                    if (!rev.name.isNullOrBlank()) cityName = rev.name
                    stateName   = rev.state
                    countryName = rev.country
                } catch (e: Exception) {
                    Log.w("ChatScreen", "Reverse geocode error: ${e.message}")
                    try {
                        @Suppress("DEPRECATION")
                        val geocoder = android.location.Geocoder(context, Locale.getDefault())
                        val addrs    = geocoder.getFromLocation(devLoc.latitude, devLoc.longitude, 1)
                        val a        = addrs?.firstOrNull()
                        if (a != null) {
                            val n = a.locality ?: a.subAdminArea ?: a.adminArea
                            if (!n.isNullOrBlank()) cityName = n
                            stateName   = a.adminArea
                            countryName = a.countryName
                        }
                    } catch (_: Exception) {}
                }
                val newLoc = SelectedLocation(
                    name      = cityName,
                    latitude  = devLoc.latitude,
                    longitude = devLoc.longitude,
                    country   = countryName,
                    admin1    = stateName,
                    timezone  = "Asia/Kolkata"
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
        val fine   = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
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
            val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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

    val isListening  by voiceAssistant.isListening.collectAsState()
    val isSpeaking   by voiceAssistant.isSpeaking.collectAsState()
    val rmsLevel     by voiceAssistant.rmsLevel.collectAsState()
    var autoSpeakEnabled by remember { mutableStateOf(true) }

    // ----------------------------------------------------------------
    // Orb state — derived from app state
    // ----------------------------------------------------------------
    val orbState = when {
        uiState.error?.contains("internet",    ignoreCase = true) == true -> OrbState.OFFLINE
        uiState.error?.contains("connection",  ignoreCase = true) == true -> OrbState.OFFLINE
        uiState.error?.contains("host",        ignoreCase = true) == true -> OrbState.OFFLINE
        uiState.error != null                                              -> OrbState.ERROR
        uiState.isLoading                                                  -> OrbState.PROCESSING
        isSpeaking                                                         -> OrbState.AI_SPEAKING
        isListening && rmsLevel > 0.04f                                    -> OrbState.USER_SPEAKING
        isListening                                                        -> OrbState.LISTENING
        else                                                               -> OrbState.IDLE
    }

    // ----------------------------------------------------------------
    // Messaging helpers
    // ----------------------------------------------------------------
    fun sendText(textToSend: String) {
        val value = textToSend.trim()
        if (value.isBlank() || uiState.isLoading) return
        val langToSend = if (selectedLanguage.equals("Auto", ignoreCase = true)) "auto" else selectedLanguage
        chatViewModel.sendMessage(
            question     = value,
            latitude     = activeLocation.latitude,
            longitude    = activeLocation.longitude,
            locationName = activeLocation.name,
            language     = langToSend
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
                onResult     = onVoiceResult,
                onError      = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
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
                    onResult     = onVoiceResult,
                    onError      = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
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
            OrbState.USER_SPEAKING         -> voiceAssistant.stopListening()
            OrbState.PROCESSING            -> { /* intentionally no-op */ }
            OrbState.AI_SPEAKING           -> voiceAssistant.stopSpeaking()
            OrbState.ERROR                 -> {
                // Retry last user message
                val lastUser = uiState.messages.lastOrNull { it.role == "user" }
                if (lastUser != null) sendText(lastUser.content)
            }
            OrbState.OFFLINE               ->
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

    // ----------------------------------------------------------------
    // Suggestion quick-prompts (paginated)
    // ----------------------------------------------------------------
    val allSuggestions = listOf(
        listOf(
            Triple("☂️", "Will it rain?",   "Will it rain today in ${activeLocation.name}?"),
            Triple("🧳", "What to pack?",   "What should I pack or wear for the weather in ${activeLocation.name}?"),
            Triple("🛣️", "Road conditions", "Are roads and driving conditions safe in ${activeLocation.name}?")
        ),
        listOf(
            Triple("🌡️", "Hourly forecast", "What is the hourly temperature forecast for ${activeLocation.name}?"),
            Triple("💨", "Wind & air",      "What is the wind speed and air quality in ${activeLocation.name}?"),
            Triple("🧭", "Show on map",     "Give me an overview of satellite radar maps for ${activeLocation.name}.")
        )
    )

    // ----------------------------------------------------------------
    // Orb state text
    // ----------------------------------------------------------------
    val orbStateText = when (orbState) {
        OrbState.IDLE          -> "Tap to speak"
        OrbState.LISTENING     -> "Listening..."
        OrbState.USER_SPEAKING -> "Listening..."
        OrbState.PROCESSING    -> "Thinking..."
        OrbState.AI_SPEAKING   -> "Speaking..."
        OrbState.PAUSED        -> "Paused"
        OrbState.ERROR         -> "Something went wrong"
        OrbState.OFFLINE       -> "Offline"
    }
    val orbStateSubtitle = when (orbState) {
        OrbState.IDLE          -> "Ask WeatherGPT anything"
        OrbState.LISTENING     -> "Speak naturally"
        OrbState.USER_SPEAKING -> "I'm listening"
        OrbState.PROCESSING    -> "Understanding your request"
        OrbState.AI_SPEAKING   -> "WeatherGPT is responding"
        OrbState.PAUSED        -> "Tap to resume"
        OrbState.ERROR         -> "Tap the orb to retry"
        OrbState.OFFLINE       -> "Check your connection"
    }

    // Status dot colour
    val statusColor = when (orbState) {
        OrbState.OFFLINE       -> Color(0xFF7E8B9F)
        OrbState.ERROR         -> Color(0xFFFF6B6B)
        OrbState.PROCESSING    -> Color(0xFF52D9FF)
        OrbState.AI_SPEAKING   -> Color(0xFF8B7CFF)
        OrbState.LISTENING,
        OrbState.USER_SPEAKING -> Color(0xFF52D9FF)
        else                   -> Color(0xFF36E6A0)
    }
    val statusText = when (orbState) {
        OrbState.PROCESSING    -> "Thinking"
        OrbState.AI_SPEAKING   -> "Speaking"
        OrbState.LISTENING,
        OrbState.USER_SPEAKING -> "Listening"
        OrbState.ERROR         -> "Error"
        OrbState.OFFLINE       -> "Offline"
        else                   -> "AI Online"
    }

    // ================================================================
    //  ROOT LAYOUT
    // ================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A12))
    ) {
        LazyColumn(
            modifier             = Modifier.weight(1f).fillMaxWidth(),
            contentPadding       = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 12.dp),
            verticalArrangement  = Arrangement.spacedBy(14.dp),
            horizontalAlignment  = Alignment.CenterHorizontally
        ) {

            // ============================================================
            // 1.  STATUS HEADER
            // ============================================================
            item {
                Row(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Left: branding
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                    // Right: status + controls
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // AI status pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF0E1626))
                                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
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
                        IconButton(onClick = { autoSpeakEnabled = !autoSpeakEnabled }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle auto-speak",
                                tint   = if (autoSpeakEnabled) Color(0xFF52D9FF) else Color(0xFF7E8B9F),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Clear chat
                        IconButton(
                            onClick = {
                                voiceAssistant.stopSpeaking()
                                voiceAssistant.stopListening()
                                message.value = ""
                                if (uiState.messages.isNotEmpty()) chatViewModel.clearChat()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear chat", tint = Color(0xFF7E8B9F), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ============================================================
            // 2.  HERO — Title + AI ORB + State text
            // ============================================================
            item {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Label
                    Text(
                        text          = "AI ASSISTANT",
                        color         = Color(0xFF52D9FF),
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                    Spacer(Modifier.height(6.dp))

                    // Title
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Ask WeatherGPT", color = Color(0xFFF5F7FA), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                        Text(".", color = Color(0xFF52D9FF), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text      = "Get real-time, personalised weather insights.",
                        color     = Color(0xFFAAB6C7),
                        fontSize  = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    // ---- THE ORB ----
                    WeatherAIOrb(
                        orbState      = orbState,
                        audioAmplitude = rmsLevel,
                        onTap         = { handleOrbTap() }
                    )

                    Spacer(Modifier.height(16.dp))

                    // State label
                    Text(
                        text       = orbStateText,
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text    = orbStateSubtitle,
                        color   = Color(0xFF7E8B9F),
                        fontSize = 12.sp
                    )
                }
            }

            // ============================================================
            // 3.  LOCATION CARD
            // ============================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0E1626))
                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(20.dp))
                        .clickable { showLocationDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = Color(0xFF4DA3FF), modifier = Modifier.size(22.dp))
                            Column {
                                Text(activeLocation.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text     = if (isManual) "Custom location" else "Auto-detected",
                                    color    = Color(0xFF7E8B9F),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.MyLocation,                          contentDescription = "GPS",    tint = Color(0xFF52D9FF), modifier = Modifier.size(18.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,      contentDescription = "Select", tint = Color(0xFF7E8B9F), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ============================================================
            // 4.  LANGUAGE SELECTOR PILL
            // ============================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0E1626))
                        .border(1.dp, Color(0x334DA3FF), RoundedCornerShape(20.dp))
                        .clickable { showLanguageDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Translate, contentDescription = "Language", tint = Color(0xFF8B7CFF), modifier = Modifier.size(22.dp))
                            Column {
                                Text("Voice & Chat Language", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                val langDisplay = LanguageStore.SUPPORTED_LANGUAGES.find {
                                    it.code.equals(selectedLanguage, ignoreCase = true)
                                }
                                Text(
                                    text     = langDisplay?.let { "${it.nativeLabel} — ${it.englishName}" } ?: selectedLanguage,
                                    color    = Color(0xFF7E8B9F),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Select", tint = Color(0xFF7E8B9F), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ============================================================
            // 5.  QUICK PROMPTS ROW
            // ============================================================
            item {
                val currentSuggestions = allSuggestions[suggestionPageIndex % allSuggestions.size]
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    currentSuggestions.forEach { (icon, title, query) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0E1626))
                                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(20.dp))
                                .clickable {
                                    message.value = query
                                    sendMessage()
                                }
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = icon, fontSize = 12.sp)
                                Text(
                                    text     = title,
                                    color    = Color(0xFFF5F7FA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Next page arrow
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0E1626))
                            .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                            .clickable { suggestionPageIndex = (suggestionPageIndex + 1) % allSuggestions.size },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "More", tint = Color(0xFF8896AB), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ============================================================
            // 6.  CHAT HISTORY
            // ============================================================
            if (uiState.messages.isNotEmpty()) {
                items(
                    items = uiState.messages,
                    key   = { "${it.role}-${it.content.hashCode()}" }
                ) { msg ->
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        when (msg.role.lowercase()) {
                            "user" -> MinimalUserBubble(text = msg.content)
                            else   -> MinimalAssistantBubble(
                                text     = msg.content,
                                onSpeak  = {
                                    if (isSpeaking) {
                                        voiceAssistant.stopSpeaking()
                                    } else {
                                        voiceAssistant.speak(msg.content, uiState.detectedLanguageCode)
                                    }
                                },
                                isSpeaking = isSpeaking,
                                onCopy     = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("WeatherGPT Response", msg.content))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    item { MinimalThinkingBubble() }
                }
            }

            // Error recovery message (when no messages, just orb shows ERROR/OFFLINE state)
            if (uiState.error != null && uiState.messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A0E0E))
                            .border(1.dp, Color(0x44FF6B6B), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text     = uiState.error ?: "",
                            color    = Color(0xFFFF9B9B),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ================================================================
        //  BOTTOM COMPOSER
        // ================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF0E1626))
                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(32.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value         = message.value,
                onValueChange = { message.value = it },
                modifier      = Modifier.weight(1f).padding(vertical = 8.dp),
                enabled       = !uiState.isLoading,
                textStyle     = TextStyle(color = Color(0xFFF5F7FA), fontSize = 14.sp),
                cursorBrush   = SolidColor(Color(0xFF4DA3FF)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                decorationBox   = { innerTextField ->
                    if (message.value.isEmpty()) {
                        Text(
                            text  = when {
                                isListening -> "Listening... Speak now"
                                isSpeaking  -> "WeatherGPT is speaking..."
                                else        -> "Ask anything..."
                            },
                            color = when {
                                isListening -> Color(0xFF52D9FF)
                                isSpeaking  -> Color(0xFF36E6A0)
                                else        -> Color(0xFF7E8B9F)
                            },
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            IconButton(onClick = { toggleVoiceListening() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector      = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint             = if (isListening) Color(0xFFFF6B6B) else Color(0xFF8896AB),
                    modifier         = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.value.isNotBlank() && !uiState.isLoading) Color(0xFF4DA3FF)
                        else Color(0xFF1E3A5F)
                    )
                    .clickable(enabled = message.value.isNotBlank() && !uiState.isLoading) { sendMessage() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint               = if (message.value.isNotBlank() && !uiState.isLoading) Color.White else Color(0xFF88A4C7),
                    modifier           = Modifier.size(18.dp)
                )
            }
        }
    }

    // ================================================================
    //  DIALOGS
    // ================================================================
    if (showLocationDialog) {
        LocationSearchDialog(
            currentLocation    = activeLocation.name,
            onDismiss          = { showLocationDialog = false },
            isManualMode       = isManual,
            onUseCurrentLocation = {
                val fine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                        name      = locationResult.name ?: "Selected Location",
                        latitude  = lat,
                        longitude = lon,
                        country   = locationResult.country,
                        admin1    = locationResult.admin1,
                        timezone  = "Asia/Kolkata"
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
            onDismiss           = { showLanguageDialog = false },
            onLanguageSelected  = { lang ->
                LanguageStore.saveLanguage(context, lang.code)
                showLanguageDialog = false
            }
        )
    }
}


/* ================================================================
   MINIMAL CHAT BUBBLES — preserved from previous build
   ================================================================ */

@Composable
private fun MinimalUserBubble(text: String) {
    val timeString = remember { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1626))
            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            verticalAlignment   = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF223554)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "User", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = text, color = Color(0xFFF5F7FA), fontSize = 13.sp, lineHeight = 19.sp)
            }

            Text(text = timeString, color = Color(0xFF7E8B9F), fontSize = 10.sp, modifier = Modifier.align(Alignment.Bottom))
        }
    }
}

@Composable
private fun MinimalAssistantBubble(
    text:      String,
    onSpeak:   () -> Unit,
    isSpeaking: Boolean,
    onCopy:    () -> Unit
) {
    val cleanDisplayText = remember(text) {
        var t = text
        if (t.contains("<think>")) {
            t = t.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        }
        val lines    = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
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

    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0E1626))
            .border(1.dp, Color(0x334DA3FF), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4DA3FF), Color(0xFF2563EB)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Text("WeatherGPT", color = Color(0xFF52D9FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))

            Text(text = cleanDisplayText, color = Color(0xFFF5F7FA), fontSize = 13.sp, lineHeight = 19.sp)

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF7E8B9F), modifier = Modifier.size(15.dp))
                }

                IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector      = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak",
                        tint             = if (isSpeaking) Color(0xFF52D9FF) else Color(0xFF7E8B9F),
                        modifier         = Modifier.size(17.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color(0xFF7E8B9F), modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded          = showMenu,
                        onDismissRequest  = { showMenu = false },
                        modifier          = Modifier.background(Color(0xFF111E2F))
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Copy full response", color = Color.White) },
                            onClick = { onCopy(); showMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalThinkingBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1626))
            .border(1.dp, Color(0x3352D9FF), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF52D9FF), strokeWidth = 2.dp)
            Text("WeatherGPT is analysing...", color = Color(0xFFAAB6C7), fontSize = 12.sp)
        }
    }
}
