package com.example.weathergpt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetWeatherClient
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.ui.components.AiOrb
import com.example.weathergpt.ui.components.AiSectionTitle
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.components.IntelligenceBadge
import com.example.weathergpt.ui.components.WeatherBackground
import com.example.weathergpt.ui.components.chooseWeatherVisual
import com.example.weathergpt.ui.theme.NeonBlue
import com.example.weathergpt.ui.theme.NeonCyan
import com.example.weathergpt.ui.theme.RiskOrange
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


@Composable
fun HomeScreen(
    onOpenChat: () -> Unit
) {

    val context =
        androidx.compose.ui.platform.LocalContext.current

    // ============================================================
    // LOCATION
    // ============================================================

    var activeLocation by remember {
        mutableStateOf(
            LocationStore.getLocation(
                context
            )
        )
    }

    // ============================================================
    // LIVE WEATHER
    // ============================================================

    var currentWeather by remember {
        mutableStateOf<MetForecastItem?>(null)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var hasError by remember {
        mutableStateOf(false)
    }

    // ============================================================
    // WEATHER LOADING
    // ============================================================

    LaunchedEffect(Unit) {

        while (true) {

            try {

                isLoading = true

                activeLocation =
                    LocationStore.getLocation(
                        context
                    )

                var latitude =
                    activeLocation.latitude

                var longitude =
                    activeLocation.longitude

                if (
                    !LocationStore.isManual(
                        context
                    )
                ) {

                    try {

                        val provider =
                            DeviceLocationProvider(
                                context
                            )

                        val deviceLocation =
                            provider.getCurrentLocation()

                        if (
                            deviceLocation != null
                        ) {

                            latitude =
                                deviceLocation.latitude

                            longitude =
                                deviceLocation.longitude
                        }

                    } catch (_: Exception) {
                        // Saved location remains fallback.
                    }
                }

                val response =
                    MetWeatherClient.api.getWeather(
                        latitude = latitude,
                        longitude = longitude
                    )

                currentWeather =
                    response.forecast.firstOrNull()

                hasError = false

            } catch (_: Exception) {

                hasError = true

            } finally {

                isLoading = false
            }

            delay(
                10 * 60 * 1000L
            )
        }
    }

    // ============================================================
    // DYNAMIC WEATHER BACKGROUND
    // ============================================================

    val weatherVisual =
        chooseWeatherVisual(
            symbolCode =
                currentWeather?.symbol_code,

            temperature =
                currentWeather?.temperature_c
        )

    WeatherBackground(
        visual =
            weatherVisual,

        darkMode =
            true
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 18.dp,
                        vertical = 14.dp
                    )
        ) {

            // ====================================================
            // TOP HEADER
            // ====================================================

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            "WEATHERGPT",

                        color =
                            NeonCyan,

                        fontSize =
                            10.sp,

                        letterSpacing =
                            1.4.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            activeLocation.name,

                        color =
                            TextPrimary,

                        fontSize =
                            25.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp)
                    )

                    Text(
                        text =
                            "Weather intelligence",

                        color =
                            TextMuted,

                        fontSize =
                            11.sp
                    )
                }

                LiveIndicator(
                    isLive =
                        !isLoading &&
                            !hasError
                )
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // ====================================================
            // HERO
            // ====================================================

            GlassCard(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.Top
                    ) {

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    "CURRENT INTELLIGENCE",

                                color =
                                    NeonCyan,

                                fontSize =
                                    9.sp,

                                letterSpacing =
                                    1.0.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(7.dp)
                            )

                            Text(
                                text =
                                    weatherDescription(
                                        currentWeather?.symbol_code
                                    ),

                                color =
                                    TextSecondary,

                                fontSize =
                                    16.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(2.dp)
                            )

                            Text(
                                text =
                                    currentWeather
                                        ?.temperature_c
                                        ?.roundToInt()
                                        ?.let {
                                            "$it°"
                                        }
                                        ?: "--°",

                                color =
                                    TextPrimary,

                                fontSize =
                                    60.sp,

                                lineHeight =
                                    64.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(
                                text =
                                    when {

                                        isLoading ->
                                            "Updating live conditions"

                                        hasError ->
                                            "Unable to refresh"

                                        else ->
                                            "Live conditions"
                                    },

                                color =
                                    TextMuted,

                                fontSize =
                                    11.sp
                            )
                        }

                        Column(
                            horizontalAlignment =
                                Alignment.End
                        ) {

                            Icon(
                                imageVector =
                                    weatherIcon(
                                        currentWeather?.symbol_code
                                    ),

                                contentDescription =
                                    null,

                                tint =
                                    NeonBlue,

                                modifier =
                                    Modifier.size(58.dp)
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )

                            IntelligenceBadge(
                                text =
                                    when {

                                        !isLoading &&
                                            !hasError ->
                                            "LIVE"

                                        isLoading ->
                                            "SYNC"

                                        else ->
                                            "OFFLINE"
                                    }
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )

                    Text(
                        text =
                            "Predict · Understand · Act",

                        color =
                            TextSecondary,

                        fontSize =
                            12.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            // ====================================================
            // FOUR CORE METRICS
            // ====================================================

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {

                HomeMetricCard(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "HUMIDITY",

                    value =
                        currentWeather
                            ?.relative_humidity_pct
                            ?.let {
                                "${it.roundToInt()}%"
                            }
                            ?: "--"
                )

                HomeMetricCard(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "WIND",

                    value =
                        currentWeather
                            ?.wind_speed_ms
                            ?.let {
                                "${(it * 3.6).roundToInt()} km/h"
                            }
                            ?: "--"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(9.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(9.dp)
            ) {

                HomeMetricCard(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "PRESSURE",

                    value =
                        currentWeather
                            ?.pressure_hpa
                            ?.let {
                                "${it.roundToInt()} hPa"
                            }
                            ?: "--"
                )

                HomeMetricCard(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "RAIN CHANCE",

                    value =
                        currentWeather
                            ?.precipitation_probability_pct
                            ?.let {
                                "${it.roundToInt()}%"
                            }
                            ?: "--"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(26.dp)
            )

            // ====================================================
            // AI COMMAND
            // ====================================================

            Text(
                text =
                    "WEATHERGPT",

                color =
                    NeonCyan,

                fontSize =
                    10.sp,

                letterSpacing =
                    1.2.sp
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "Ask about your weather",

                color =
                    TextPrimary,

                fontSize =
                    23.sp
            )

            Spacer(
                modifier =
                    Modifier.height(9.dp)
            )

            GlassCard(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column {

                    Text(
                        text =
                            "AI WEATHER COMMAND",

                        color =
                            NeonBlue,

                        fontSize =
                            9.sp,

                        letterSpacing =
                            1.0.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(7.dp)
                    )

                    Text(
                        text =
                            "What should I know right now?",

                        color =
                            TextPrimary,

                        fontSize =
                            21.sp,

                        lineHeight =
                            27.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            "Ask about rain, heat, travel, flooding " +
                                "or today's conditions.",

                        color =
                            TextSecondary,

                        fontSize =
                            12.sp,

                        lineHeight =
                            18.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    androidx.compose.material3.Button(
                        onClick =
                            onOpenChat,

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(13.dp)
                    ) {

                        Text(
                            text =
                                "Ask WeatherGPT  ✦"
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(26.dp)
            )

            // ====================================================
            // RISK INTELLIGENCE
            // ====================================================

            Text(
                text =
                    "AI RISK ENGINE",

                color =
                    NeonCyan,

                fontSize =
                    10.sp,

                letterSpacing =
                    1.2.sp
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "What matters right now",

                color =
                    TextPrimary,

                fontSize =
                    23.sp
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "WeatherGPT watches upcoming conditions.",

                color =
                    TextMuted,

                fontSize =
                    12.sp
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            HomeRiskCard(
                title =
                    "RAIN",

                status =
                    rainRisk(
                        currentWeather
                            ?.precipitation_probability_pct
                    ),

                detail =
                    currentWeather
                        ?.precipitation_probability_pct
                        ?.let {
                            "${
                                it.roundToInt()
                            }% precipitation probability"
                        }
                        ?: "No live rain probability",

                accent =
                    NeonBlue
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HomeRiskCard(
                title =
                    "HEAT",

                status =
                    heatRisk(
                        currentWeather
                            ?.temperature_c
                    ),

                detail =
                    currentWeather
                        ?.temperature_c
                        ?.let {
                            "Current temperature ${
                                it.roundToInt()
                            }°"
                        }
                        ?: "No live temperature",

                accent =
                    RiskOrange
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HomeRiskCard(
                title =
                    "FLOOD INTELLIGENCE",

                status =
                    "MAP",

                detail =
                    "Open the Smart Map for flood zones, dams and alerts.",

                accent =
                    SuccessGreen
            )

            Spacer(
                modifier =
                    Modifier.height(26.dp)
            )

            // ====================================================
            // SMART RECOMMENDATION
            // ====================================================

            GlassCard(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column {

                    Text(
                        text =
                            "SMART RECOMMENDATION",

                        color =
                            NeonCyan,

                        fontSize =
                            9.sp,

                        letterSpacing =
                            1.0.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(7.dp)
                    )

                    Text(
                        text =
                            recommendation(
                                currentWeather
                            ),

                        color =
                            TextPrimary,

                        fontSize =
                            16.sp,

                        lineHeight =
                            22.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Based on the latest live weather data.",

                        color =
                            TextMuted,

                        fontSize =
                            10.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(34.dp)
            )
        }
    }
}


/* ================================================================
   HOME METRIC CARD
   ================================================================ */

@Composable
private fun HomeMetricCard(
    modifier: Modifier,
    label: String,
    value: String
) {

    GlassCard(
        modifier =
            modifier
    ) {

        Column {

            Text(
                text =
                    label,

                color =
                    TextMuted,

                fontSize =
                    8.sp,

                letterSpacing =
                    0.8.sp
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    value,

                color =
                    TextPrimary,

                fontSize =
                    17.sp
            )
        }
    }
}


/* ================================================================
   HOME RISK CARD
   ================================================================ */

@Composable
private fun HomeRiskCard(
    title: String,
    status: String,
    detail: String,
    accent: Color
) {

    GlassCard(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.Top
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        title,

                    color =
                        TextPrimary,

                    fontSize =
                        14.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        detail,

                    color =
                        TextMuted,

                    fontSize =
                        11.sp,

                    lineHeight =
                        17.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.size(10.dp)
            )

            Text(
                text =
                    status.uppercase(),

                color =
                    accent,

                fontSize =
                    9.sp,

                letterSpacing =
                    0.9.sp
            )
        }
    }
}


// LIVE INDICATOR
// =================================================================

@Composable
private fun LiveIndicator(
    isLive: Boolean
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .then(
                        Modifier
                    )
                    .background(
                        if (isLive) {
                            Color(0xFF36D98A)
                        } else {
                            Color(0xFFFFA24A)
                        },
                        CircleShape
                    )
        )

        Spacer(
            modifier =
                Modifier.size(6.dp)
        )

        Text(
            text =
                if (isLive) {
                    "LIVE"
                } else {
                    "SYNCING"
                },

            color =
                if (isLive) {
                    Color(0xFF36D98A)
                } else {
                    Color(0xFFFFA24A)
                },

            fontSize = 10.sp
        )
    }
}


// =================================================================
// WEATHER METRIC
// =================================================================

@Composable
private fun WeatherMetric(
    modifier: Modifier,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {

    GlassCard(
        modifier = modifier
    ) {

        Icon(
            imageVector = icon,

            contentDescription =
                label,

            tint =
                NeonCyan,

            modifier =
                Modifier.size(21.dp)
        )

        Spacer(
            modifier =
                Modifier.height(9.dp)
        )

        Text(
            text =
                label.uppercase(),

            color =
                TextMuted,

            fontSize = 10.sp
        )

        Spacer(
            modifier =
                Modifier.height(3.dp)
        )

        Text(
            text = value,

            color =
                TextPrimary,

            fontSize = 17.sp
        )
    }
}


// =================================================================
// RISK ROW
// =================================================================

@Composable
private fun RiskRow(
    title: String,
    value: String,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {

    GlassCard(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = icon,

                    contentDescription = title,

                    tint = accent,

                    modifier =
                        Modifier.size(22.dp)
                )

                Spacer(
                    modifier =
                        Modifier.size(10.dp)
                )

                Text(
                    text = title,

                    color =
                        TextPrimary,

                    fontSize = 15.sp
                )
            }

            Text(
                text = value,

                color = accent,

                fontSize = 13.sp
            )
        }
    }
}


// =================================================================
// WEATHER ICON
// =================================================================

private fun weatherIcon(
    symbol: String?
): androidx.compose.ui.graphics.vector.ImageVector {

    val value =
        symbol
            ?.lowercase()
            .orEmpty()

    return when {

        value.contains("thunder") ||
        value.contains("storm") ->
            Icons.Default.Warning

        value.contains("snow") ->
            Icons.Default.Cloud

        value.contains("rain") ||
        value.contains("drizzle") ->
            Icons.Default.WaterDrop

        value.contains("fog") ||
        value.contains("mist") ->
            Icons.Default.Cloud

        value.contains("cloud") ||
        value.contains("overcast") ->
            Icons.Default.Cloud

        else ->
            Icons.Default.Cloud
    }
}


// =================================================================
// WEATHER DESCRIPTION
// =================================================================

private fun weatherDescription(
    symbol: String?
): String {

    if (symbol.isNullOrBlank()) {
        return "Current conditions"
    }

    return symbol
        .replace("_", " ")
        .replace("-", " ")
        .replaceFirstChar {
            it.uppercase()
        }
}


// =================================================================
// RAIN RISK
// =================================================================

private fun rainRisk(
    probability: Double?
): String {

    if (probability == null) {
        return "--"
    }

    return when {

        probability >= 70.0 ->
            "HIGH"

        probability >= 40.0 ->
            "MEDIUM"

        probability >= 20.0 ->
            "LOW"

        else ->
            "MINIMAL"
    }
}


// =================================================================
// HEAT RISK
// =================================================================

private fun heatRisk(
    temperature: Double?
): String {

    if (temperature == null) {
        return "--"
    }

    return when {

        temperature >= 40.0 ->
            "HIGH"

        temperature >= 35.0 ->
            "WATCH"

        temperature >= 30.0 ->
            "MODERATE"

        else ->
            "LOW"
    }
}


// =================================================================
// AI RECOMMENDATION
// =================================================================

private fun recommendation(
    weather: MetForecastItem?
): String {

    if (weather == null) {
        return "Waiting for the latest weather data."
    }

    val rain =
        weather
            .precipitation_probability_pct
            ?: 0.0

    val temperature =
        weather
            .temperature_c
            ?: 0.0

    return when {

        rain >= 70.0 ->
            "Rain is likely. Keep an umbrella ready and plan outdoor travel carefully."

        temperature >= 38.0 ->
            "High heat is expected. Stay hydrated and limit prolonged afternoon exposure."

        temperature >= 35.0 ->
            "Temperatures are elevated. Take precautions during the hottest part of the day."

        rain >= 40.0 ->
            "There is a meaningful chance of rain. Keep rain protection nearby."

        else ->
            "Conditions look relatively stable right now."
    }
}
