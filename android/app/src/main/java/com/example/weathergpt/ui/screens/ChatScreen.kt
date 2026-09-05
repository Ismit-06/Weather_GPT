package com.example.weathergpt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.scale
import androidx.core.content.ContextCompat
import com.example.weathergpt.audio.VoiceAssistantManager
import com.example.weathergpt.ui.theme.RiskRed
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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


@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {

    val context =
        LocalContext.current

    val uiState by
        chatViewModel
            .uiState
            .collectAsState()

    val message =
        remember {
            mutableStateOf("")
        }

    val location =
        remember {
            LocationStore.getLocation(
                context
            )
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

    // Auto-speak new assistant responses like a real voice assistant
    LaunchedEffect(uiState.messages.size) {
        val lastMessage = uiState.messages.lastOrNull()
        if (autoSpeakEnabled && lastMessage != null && lastMessage.role.lowercase() != "user" && !uiState.isLoading) {
            voiceAssistant.speak(lastMessage.content, uiState.detectedLanguageCode)
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

        chatViewModel.sendMessage(
            question =
                value,

            latitude =
                location.latitude,

            longitude =
                location.longitude,

            language =
                "en"
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
                Modifier.height(10.dp)
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

        if (
            uiState.messages.isEmpty() &&
            !uiState.isLoading
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 4.dp
                        ),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        7.dp
                    )
            ) {

                QuickChip(
                    text =
                        "Rain today",

                    modifier =
                        Modifier.weight(1f),

                    onClick = {
                        message.value =
                            "Will it rain today?"
                    }
                )

                QuickChip(
                    text =
                        "Travel",

                    modifier =
                        Modifier.weight(1f),

                    onClick = {
                        message.value =
                            "Is it good to travel today?"
                    }
                )

                QuickChip(
                    text =
                        "Flood",

                    modifier =
                        Modifier.weight(1f),

                    onClick = {
                        message.value =
                            "Is there any flood risk?"
                    }
                )
            }
        }

        // ========================================================
        // COMPOSER
        // ========================================================

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            18.dp
                        )
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surface
                            .copy(
                                alpha =
                                    0.97f
                            )
                    )
                    .padding(
                        start = 7.dp,
                        end = 5.dp,
                        top = 5.dp,
                        bottom = 5.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            OutlinedTextField(

                value =
                    message.value,

                onValueChange = {
                    message.value = it
                },

                modifier =
                    Modifier.weight(1f),

                enabled =
                    !uiState.isLoading,

                placeholder = {

                    Text(
                        text =
                            if (isListening) {
                                "Listening... Speak your weather question"
                            } else {
                                "Ask WeatherGPT..."
                            },

                        color =
                            if (isListening) {
                                NeonCyan
                            } else {
                                TextMuted
                            }
                    )
                },

                singleLine = true,

                shape =
                    RoundedCornerShape(
                        15.dp
                    ),

                keyboardOptions =
                    KeyboardOptions(
                        imeAction =
                            ImeAction.Send
                    ),

                keyboardActions =
                    KeyboardActions(
                        onSend = {
                            sendMessage()
                        }
                    )
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
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .background(
                        color = if (isListening) RiskRed.copy(alpha = 0.2f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
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
                            RiskRed
                        } else {
                            NeonCyan
                        },

                    modifier =
                        if (isListening) {
                            Modifier.scale(pulseScale)
                        } else {
                            Modifier
                        }
                )
            }

            Button(
                onClick =
                    {
                        sendMessage()
                    },

                enabled =
                    message.value
                        .isNotBlank() &&
                        !uiState.isLoading,

                modifier =
                    Modifier.size(
                        width = 64.dp,
                        height = 46.dp
                    ),

                contentPadding =
                    PaddingValues(0.dp),

                shape =
                    RoundedCornerShape(
                        14.dp
                    ),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            NeonBlue,

                        disabledContainerColor =
                            NeonBlue.copy(
                                alpha =
                                    0.22f
                            )
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.AutoMirrored.Filled.Send,

                    contentDescription =
                        "Send",

                    tint =
                        Color.White,

                    modifier =
                        Modifier.size(
                            19.dp
                        )
                )
            }
        }
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
        modifier =
            Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.End
    ) {

        Surface(
            modifier =
                Modifier.fillMaxWidth(
                    0.82f
                ),

            shape =
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 5.dp
                ),

            color =
                NeonBlue.copy(
                    alpha =
                        0.18f
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(
                        15.dp
                    )
            ) {

                Text(
                    text =
                        "YOU",

                    color =
                        NeonBlue,

                    fontSize =
                        9.sp,

                    letterSpacing =
                        0.8.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            5.dp
                        )
                )

                Text(
                    text =
                        text,

                    color =
                        TextPrimary,

                    fontSize =
                        15.sp,

                    lineHeight =
                        21.sp
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

    GlassCard(
        modifier =
            Modifier.fillMaxWidth(
                0.94f
            )
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

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

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
                                    NeonCyan
                                } else {
                                    TextMuted
                                },

                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )

                Text(
                    text =
                        cleanDisplayText,

                    color =
                        TextPrimary,

                    fontSize =
                        15.sp,

                    lineHeight =
                        22.sp
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
    modifier: Modifier,
    onClick: () -> Unit
) {

    androidx.compose.material3.AssistChip(
        onClick =
            onClick,

        modifier =
            modifier,

        label = {

            Text(
                text =
                    text,

                fontSize =
                    10.sp
            )
        },

        shape =
            RoundedCornerShape(
                12.dp
            )
    )
}
