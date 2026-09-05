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
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val speechRecognizerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    message.value = spokenText
                }
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
                                messageItem.content
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
                            "Ask WeatherGPT...",

                        color =
                            TextMuted
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

            IconButton(
                onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                "Speak your weather question..."
                            )
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Voice recognition not available on this device",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Mic,

                    contentDescription =
                        "Voice input",

                    tint =
                        NeonCyan
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
    text: String
) {

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
                        text,

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
