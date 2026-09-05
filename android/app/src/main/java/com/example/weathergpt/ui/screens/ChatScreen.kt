package com.example.weathergpt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.weathergpt.audio.VoiceAssistantManager
import com.example.weathergpt.data.LocationReverseClient
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LanguageStore
import com.example.weathergpt.location.SelectedLocation
import com.example.weathergpt.ui.theme.AIViolet
import com.example.weathergpt.ui.theme.SurfaceDark
import com.example.weathergpt.ui.theme.RiskRed
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.components.IntelligenceBadge
import com.example.weathergpt.ui.theme.NeonBlue
import com.example.weathergpt.ui.theme.NeonCyan
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.viewmodel.ChatUiMessage
import com.example.weathergpt.viewmodel.ChatViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch


@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    val uiState by
        chatViewModel
            .uiState
            .collectAsState()

    val message =
        remember {
            mutableStateOf("")
        }

    val storedLocation by
        LocationStore.location.collectAsState()

    val isManualFlow by
        LocationStore.isManualFlow.collectAsState()

    val activeLocation =
        storedLocation ?: remember { LocationStore.getLocation(context) }

    val isManual =
        isManualFlow || LocationStore.isManual(context)

    var showLocationDialog by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }

    val selectedLanguage by
        LanguageStore.languageFlow.collectAsState()

    var showLanguageDialog by
        remember { mutableStateOf(false) }

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
                    if (!rev.name.isNullOrBlank()) {
                        cityName = rev.name
                    }
                    stateName = rev.state
                    countryName = rev.country
                } catch (e: Exception) {
                    Log.w("ChatScreen", "Reverse geocode error: ${e.message}")
                    try {
                        @Suppress("DEPRECATION")
                        val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
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
                Toast.makeText(context, "Could not acquire location. Please check device location.", Toast.LENGTH_SHORT).show()
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

    // Auto-detect location on launch if in GPS mode
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceAssistant.startListening(
                languageCode = selectedLanguage,
                onResult = { text ->
                    message.value = text
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-speak new assistant responses like a real voice assistant in chosen language
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

    fun sendMessage() {

        val value =
            message.value.trim()

        if (
            value.isBlank() ||
            uiState.isLoading
        ) {
            return
        }

        val langToSend = if (selectedLanguage.equals("Auto", ignoreCase = true)) {
            "auto"
        } else {
            selectedLanguage
        }

        chatViewModel.sendMessage(
            question =
                value,

            latitude =
                activeLocation.latitude,

            longitude =
                activeLocation.longitude,

            locationName =
                activeLocation.name,

            language =
                langToSend
        )

        message.value = ""
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme
                        .colorScheme
                        .background
                )
    ) {

        // ========================================================
        // HEADER
        // ========================================================

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 14.dp
                    ),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text =
                        "WEATHERGPT",

                    color =
                        NeonCyan,

                    fontSize =
                        10.sp,

                    letterSpacing =
                        1.3.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Weather Intelligence",

                    color =
                        TextPrimary,

                    fontSize =
                        21.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        autoSpeakEnabled = !autoSpeakEnabled
                        if (!autoSpeakEnabled) {
                            voiceAssistant.stopSpeaking()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (autoSpeakEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (autoSpeakEnabled) "Voice output enabled" else "Voice output muted",
                        tint = if (isSpeaking) NeonCyan else if (autoSpeakEnabled) NeonBlue else TextMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (uiState.messages.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            voiceAssistant.stopSpeaking()
                            chatViewModel.clearChat()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear chat history",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IntelligenceBadge(
                    text =
                        if (
                            uiState.isLoading
                        ) {
                            "THINKING"
                        } else {
                            "AI ONLINE"
                        }
                )
            }
        }

        // ========================================================
        // INTRO
        // ========================================================

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp
                    )
        ) {

            Text(
                text =
                    "Ask WeatherGPT.",

                color =
                    TextPrimary,

                fontSize =
                    30.sp,

                lineHeight =
                    34.sp
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Get weather answers based on your location.",

                color =
                    TextSecondary,

                fontSize =
                    14.sp
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "RAIN · HEAT · TRAVEL · FLOOD · FORECAST",

                color =
                    TextMuted,

                fontSize =
                    9.sp,

                letterSpacing =
                    0.8.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        // ========================================================
        // LOCATION BAR (Auto GPS / Manual Selection)
        // ========================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clickable { showLocationDialog = true },
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark.copy(alpha = 0.85f),
            border = BorderStroke(
                1.dp,
                if (isManual) NeonBlue.copy(alpha = 0.4f) else NeonCyan.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isManual) Icons.Default.LocationOn else Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = if (isManual) NeonBlue else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = activeLocation.name,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isDetectingLocation) "Detecting GPS location..." else if (isManual) "Manual Location • Tap to change" else "Auto-detected GPS • Tap to change",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isManual) NeonBlue.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isManual) "MANUAL" else "AUTO GPS",
                            color = if (isManual) NeonBlue else NeonCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select location",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(5.dp)
        )

        // ========================================================
        // LANGUAGE SELECTOR BAR
        // ========================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clickable { showLanguageDialog = true },
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark.copy(alpha = 0.85f),
            border = BorderStroke(
                1.dp,
                AIViolet.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = AIViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val currentAppLang = LanguageStore.SUPPORTED_LANGUAGES.find {
                            it.code.equals(selectedLanguage, ignoreCase = true)
                        } ?: LanguageStore.SUPPORTED_LANGUAGES.first()

                        Text(
                            text = "${currentAppLang.nativeLabel} (${currentAppLang.englishName})",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "AI Voice & Chat Language • Tap to change",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AIViolet.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = selectedLanguage.uppercase(),
                            color = AIViolet,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select language",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        // ========================================================
        // BACKEND ERROR
        // ========================================================

        uiState.error?.let { errorMessage ->

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp
                        ),

                shape =
                    RoundedCornerShape(
                        12.dp
                    ),

                color =
                    Color(0x22FF5A5A)
            ) {

                Row(
                    modifier =
                        Modifier.padding(
                            11.dp
                        ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            errorMessage,

                        color =
                            TextPrimary,

                        fontSize =
                            11.sp,

                        modifier =
                            Modifier.weight(1f)
                    )

                    IconButton(
                        onClick =
                            {
                                val retry =
                                    message.value
                                        .takeIf {
                                            it.isNotBlank()
                                        }

                                if (
                                    retry != null
                                ) {
                                    sendMessage()
                                }
                            }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Refresh,

                            contentDescription =
                                "Retry",

                            tint =
                                NeonCyan
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )
        }

        // ========================================================
        // CONVERSATION
        // ========================================================

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),

            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            if (
                uiState.messages.isEmpty()
            ) {

                item {

                    GlassCard(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.Top
                        ) {

                            Surface(
                                modifier =
                                    Modifier.size(
                                        38.dp
                                    ),

                                shape =
                                    RoundedCornerShape(
                                        12.dp
                                    ),

                                color =
                                    NeonCyan.copy(
                                        alpha =
                                            0.12f
                                    )
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default.Cloud,

                                    contentDescription =
                                        null,

                                    tint =
                                        NeonCyan,

                                    modifier =
                                        Modifier.padding(
                                            8.dp
                                        )
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.size(
                                        10.dp
                                    )
                            )

                            Column {

                                Text(
                                    text =
                                        "WEATHERGPT",

                                    color =
                                        NeonCyan,

                                    fontSize =
                                        9.sp,

                                    letterSpacing =
                                        1.0.sp
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            6.dp
                                        )
                                )

                                Text(
                                    text =
                                        "Ask me anything about your weather.",

                                    color =
                                        TextPrimary,

                                    fontSize =
                                        16.sp
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            5.dp
                                        )
                                )

                                Text(
                                    text =
                                        "I can interpret rain, forecast, heat, " +
                                            "wind, travel and flood conditions.",

                                    color =
                                        TextSecondary,

                                    fontSize =
                                        12.sp,

                                    lineHeight =
                                        18.sp
                                )
                            }
                        }
                    }
                }
            }

            items(
                items =
                    uiState.messages,

                key = { messageItem ->
                    "${messageItem.role}-${messageItem.content.hashCode()}"
                }
            ) { messageItem ->

                when (
                    messageItem.role.lowercase()
                ) {

                    "user" ->
                        UserBubble(
                            text =
                                messageItem.content
                        )

                    else ->
                        BotBubble(
                            text =
                                messageItem.content,
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
                            isSpeaking = isSpeaking
                        )
                }
            }

            if (
                uiState.isLoading
            ) {

                item {

                    ThinkingBubble()
                }
            }
        }

        // ========================================================
        // QUICK QUESTIONS
        // ========================================================

        // ========================================================
        // QUICK QUESTIONS (Screen 2 Mockup: 2x2 prompt chips)
        // ========================================================

        if (!uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip(
                        text = "Best time to travel",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            message.value = "Best time to travel"
                            sendMessage()
                        }
                    )
                    QuickChip(
                        text = "What to pack?",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            message.value = "What to pack?"
                            sendMessage()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip(
                        text = "Will it rain?",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            message.value = "Will it rain today?"
                            sendMessage()
                        }
                    )
                    QuickChip(
                        text = "Road conditions",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            message.value = "What are the current road and travel conditions?"
                            sendMessage()
                        }
                    )
                }
            }
        }

        // ========================================================
        // COMPOSER (Screen 2 Mockup)
        // ========================================================

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    )
                    .clip(
                        RoundedCornerShape(28.dp)
                    )
                    .background(
                        Color(0xFF0E1626)
                    )
                    .border(
                        1.dp,
                        Color(0x2EFFFFFF),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(
                        start = 16.dp,
                        end = 6.dp,
                        top = 4.dp,
                        bottom = 4.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = message.value,
                onValueChange = { message.value = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                enabled = !uiState.isLoading,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFF5F7FA),
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                decorationBox = { innerTextField ->
                    if (message.value.isEmpty()) {
                        Text(
                            text = if (isListening) "Listening... Speak now" else "Ask anything...",
                            color = if (isListening) Color(0xFF52D9FF) else Color(0xFFAAB6C7),
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            val pulseTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            IconButton(
                onClick = {
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
                                onResult = { spokenText ->
                                    message.value = spokenText
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector =
                        if (isListening) {
                            Icons.Default.MicOff
                        } else {
                            Icons.Default.Mic
                        },
                    contentDescription =
                        if (isListening) {
                            "Stop listening"
                        } else {
                            "Voice input"
                        },
                    tint =
                        if (isListening) {
                            Color(0xFFFF6B6B)
                        } else {
                            Color(0xFFAAB6C7)
                        },
                    modifier =
                        if (isListening) {
                            Modifier.scale(pulseScale)
                        } else {
                            Modifier.size(20.dp)
                        }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.value.isNotBlank() && !uiState.isLoading) Color(0xFF4DA3FF)
                        else Color(0xFF16233B)
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
                    modifier = Modifier.size(20.dp)
                )
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
                    "AI language set to ${appLang.nativeLabel} (${appLang.englishName})",
                    Toast.LENGTH_SHORT
                ).show()
                showLanguageDialog = false
            }
        )
    }
}


/* ================================================================
   USER MESSAGE
   ================================================================ */

@Composable
private fun UserBubble(
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp
            ),
            color = Color(0xFF1B273E)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "9:41 PM",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                )
            }
        }
    }
}


/* ================================================================
   BOT MESSAGE
   ================================================================ */

@Composable
private fun BotBubble(
    text: String,
    onSpeak: () -> Unit = {},
    isSpeaking: Boolean = false
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
            l.startsWith("hmm, this is a bit confusing") ||
            l.startsWith("the weather data provided is")
        }
        if (filtered.isNotEmpty()) filtered.joinToString("\n\n") else t
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF388BFF), Color(0xFF2563EB))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "WeatherGPT",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "9:41 PM",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector =
                                if (isSpeaking) {
                                    Icons.Default.VolumeOff
                                } else {
                                    Icons.Default.VolumeUp
                                },
                            contentDescription =
                                if (isSpeaking) {
                                    "Stop reading"
                                } else {
                                    "Read aloud"
                                },
                            tint =
                                if (isSpeaking) {
                                    Color(0xFF388BFF)
                                } else {
                                    Color(0xFF64748B)
                                },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = cleanDisplayText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}


/* ================================================================
   THINKING
   ================================================================ */

@Composable
private fun ThinkingBubble() {

    GlassCard(
        modifier =
            Modifier.fillMaxWidth(
                0.72f
            )
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            CircularProgressIndicatorSmall()

            Spacer(
                modifier =
                    Modifier.size(
                        9.dp
                    )
            )

            Text(
                text =
                    "WeatherGPT is analyzing...",

                color =
                    TextSecondary,

                fontSize =
                    12.sp
            )
        }
    }
}


@Composable
private fun CircularProgressIndicatorSmall() {

    androidx.compose.material3.CircularProgressIndicator(
        modifier =
            Modifier.size(
                18.dp
            ),

        color =
            NeonCyan,

        strokeWidth =
            2.dp
    )
}


/* ================================================================
   QUICK CHIP
   ================================================================ */

@Composable
private fun QuickChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E1626),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2EFFFFFF))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color(0xFFF5F7FA),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
