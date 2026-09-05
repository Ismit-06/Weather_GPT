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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.viewmodel.ChatViewModel
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val uiState by chatViewModel.uiState.collectAsState()
    val message = remember { mutableStateOf("") }

    val storedLocation by LocationStore.location.collectAsState()
    val isManualFlow by LocationStore.isManualFlow.collectAsState()
    val activeLocation = storedLocation ?: remember { LocationStore.getLocation(context) }
    val isManual = isManualFlow || LocationStore.isManual(context)

    var showLocationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }

    val selectedLanguage by LanguageStore.languageFlow.collectAsState()

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
            coroutineScope.launch {
                detectGpsLocation()
            }
        } else {
            Toast.makeText(context, "Location permission denied. Using saved location.", Toast.LENGTH_SHORT).show()
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

    val voiceAssistant = remember { VoiceAssistantManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            voiceAssistant.destroy()
        }
    }

    val isListening by voiceAssistant.isListening.collectAsState()
    val isSpeaking by voiceAssistant.isSpeaking.collectAsState()
    var autoSpeakEnabled by remember { mutableStateOf(true) }

    fun sendText(textToSend: String) {
        val value = textToSend.trim()
        if (value.isBlank() || uiState.isLoading) return

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

    fun sendMessage() {
        sendText(message.value)
    }

    val onVoiceResult: (String) -> Unit = { spokenText ->
        val trimmed = spokenText.trim()
        if (trimmed.isNotBlank()) {
            sendText(trimmed)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceAssistant.startListening(
                languageCode = selectedLanguage,
                onResult = onVoiceResult,
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVoiceListening() {
        if (isSpeaking) {
            voiceAssistant.stopSpeaking()
        }
        if (isListening) {
            voiceAssistant.stopListening()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                voiceAssistant.startListening(
                    languageCode = selectedLanguage,
                    onResult = onVoiceResult,
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Auto-speak responses
    LaunchedEffect(uiState.messages.size) {
        val lastMessage = uiState.messages.lastOrNull()
        if (autoSpeakEnabled && lastMessage != null && lastMessage.role.lowercase() != "user" && !uiState.isLoading) {
            val speechLang = if (!selectedLanguage.equals("Auto", ignoreCase = true)) {
                selectedLanguage
            } else {
                uiState.detectedLanguageCode ?: "en-IN"
            }
            voiceAssistant.speak(lastMessage.content, speechLang)
        }
    }

    val currentAppLang = remember(selectedLanguage) {
        LanguageStore.SUPPORTED_LANGUAGES.find {
            it.code.equals(selectedLanguage, ignoreCase = true)
        } ?: LanguageStore.SUPPORTED_LANGUAGES.first()
    }

    // Determine current Orb state for the 220dp Living Orb
    val orbState = when {
        uiState.error != null -> OrbState.ERROR
        uiState.isLoading -> OrbState.PROCESSING
        isSpeaking -> OrbState.AI_SPEAKING
        isListening -> OrbState.LISTENING
        else -> OrbState.IDLE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ----------------------------------------------------
            // 1. CENTRAL INTERACTIVE 3D LIVING AI ORB (210dp)
            // ----------------------------------------------------
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WeatherAIOrb(
                        orbState = orbState,
                        audioAmplitude = if (isListening || isSpeaking) 0.65f else 0.05f,
                        size = 210.dp,
                        onTap = { toggleVoiceListening() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            isListening -> "Listening..."
                            isSpeaking -> "Responding..."
                            uiState.isLoading -> "Thinking..."
                            else -> "Tap to speak"
                        },
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when {
                            isListening -> "Speak naturally"
                            isSpeaking -> "Tap orb or mic to interrupt"
                            uiState.isLoading -> "Analyzing atmospheric telemetry"
                            else -> "Ask anything about the weather"
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // ----------------------------------------------------
            // 2. LOCATION & LANGUAGE GLASS CAPSULE CARDS (Row of 2)
            // ----------------------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Location Card [ 📍 Amravati > ]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xB30A1626))
                            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                            .clickable { showLocationDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = activeLocation.name.take(12).let { if (activeLocation.name.length > 12) "$it…" else it },
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Language Card [ 🌐 EN > ]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xB30A1626))
                            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                            .clickable { showLanguageDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Language",
                                    tint = SecondaryCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = currentAppLang.englishName.take(10),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // 3. 2x2 QUICK SUGGESTION GLASS CARDS
            // ----------------------------------------------------
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SuggestionGlassCard(
                            icon = "🌧️",
                            title = "Will it rain?",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                message.value = "Will it rain today in ${activeLocation.name}?"
                                sendMessage()
                            }
                        )
                        SuggestionGlassCard(
                            icon = "🧳",
                            title = "What to pack?",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                message.value = "What should I pack or wear for the weather in ${activeLocation.name}?"
                                sendMessage()
                            }
                        )
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SuggestionGlassCard(
                            icon = "🛣️",
                            title = "Road conditions",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                message.value = "Are roads and driving conditions safe in ${activeLocation.name}?"
                                sendMessage()
                            }
                        )
                        SuggestionGlassCard(
                            icon = "📖",
                            title = "Show on map",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                message.value = "Give me an overview of weather telemetry on map for ${activeLocation.name}."
                                sendMessage()
                            }
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // 4. CONVERSATION MESSAGES (Translucent Glass Bubbles)
            // ----------------------------------------------------
            if (uiState.messages.isNotEmpty()) {
                items(
                    items = uiState.messages,
                    key = { "${it.role}-${it.content.hashCode()}" }
                ) { messageItem ->
                    when (messageItem.role.lowercase()) {
                        "user" -> GlassUserBubble(text = messageItem.content)
                        else -> GlassAssistantBubble(
                            text = messageItem.content,
                            onSpeak = {
                                if (isSpeaking) {
                                    voiceAssistant.stopSpeaking()
                                } else {
                                    voiceAssistant.speak(
                                        messageItem.content,
                                        uiState.detectedLanguageCode
                                    )
                                }
                            },
                            isSpeaking = isSpeaking,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("WeatherGPT Response", messageItem.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (uiState.isLoading) {
                    item {
                        GlassThinkingBubble()
                    }
                }
            }
        }

        // ========================================================
        // 5. FLOATING GLASS COMPOSER DOCK
        // ========================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xD90A1626))
                    .border(1.dp, BorderGlass, RoundedCornerShape(26.dp))
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
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(PrimaryBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    decorationBox = { innerTextField ->
                        if (message.value.isEmpty()) {
                            Text(
                                text = when {
                                    isListening -> "Listening... Speak now"
                                    isSpeaking -> "WeatherGPT is speaking..."
                                    else -> "Type or speak..."
                                },
                                color = when {
                                    isListening -> SecondaryCyan
                                    isSpeaking -> Color(0xFF36E6A0)
                                    else -> TextMuted
                                },
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // Voice Mic Button
                val micPulse = rememberInfiniteTransition(label = "pulse")
                val micScale by micPulse.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                IconButton(
                    onClick = { toggleVoiceListening() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = if (isListening) Color(0xFFFF6B6B) else TextMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(if (isListening) micScale else 1f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Circular Bright Blue Send Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (message.value.isNotBlank() && !uiState.isLoading) PrimaryBlue
                            else Color(0xFF16253B)
                        )
                        .clickable(
                            enabled = message.value.isNotBlank() && !uiState.isLoading
                        ) {
                            sendMessage()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (message.value.isNotBlank() && !uiState.isLoading) Color.White else Color(0xFF7E8B9F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showLocationDialog) {
        LocationSearchDialog(
            currentLocation = activeLocation.name,
            onDismiss = { showLocationDialog = false },
            isManualMode = isManual,
            onUseCurrentLocation = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                        detectGpsLocation()
                    }
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
            onLanguageSelected = { appLang ->
                LanguageStore.saveLanguage(context, appLang.code)
                Toast.makeText(
                    context,
                    "Language set to ${appLang.nativeLabel} (${appLang.englishName})",
                    Toast.LENGTH_SHORT
                ).show()
                showLanguageDialog = false
            }
        )
    }
}

/**
 * 20dp Corner Radius Glass Suggestion Card
 */
@Composable
private fun SuggestionGlassCard(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xB30A1626))
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlassUserBubble(text: String) {
    val timeString = remember { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC0E1A2D))
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3557)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Text(
                text = timeString,
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Bottom)
            )
        }
    }
}

@Composable
private fun GlassAssistantBubble(
    text: String,
    onSpeak: () -> Unit,
    isSpeaking: Boolean,
    onCopy: () -> Unit
) {
    val cleanDisplayText = remember(text) {
        var t = text
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

    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xD90A1626))
            .border(1.dp, Color(0x334DA3FF), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(PrimaryBlue, Color(0xFF2563EB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = "WeatherGPT",
                    color = SecondaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = cleanDisplayText,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak",
                        tint = if (isSpeaking) SecondaryCyan else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF0E1A2D))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy full response", color = Color.White) },
                            onClick = {
                                onCopy()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassThinkingBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB30A1626))
            .border(1.dp, Color(0x334DA3FF), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = SecondaryCyan,
                strokeWidth = 2.dp
            )
            Text(
                text = "WeatherGPT is analyzing telemetry...",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
