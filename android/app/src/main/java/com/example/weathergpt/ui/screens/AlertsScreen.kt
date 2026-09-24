package com.weathergpt.app.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.text.style.TextOverflow
import com.weathergpt.app.data.DamClient
import com.weathergpt.app.data.DamItem
import com.weathergpt.app.ui.components.GlassCard
import com.weathergpt.app.ui.theme.BackgroundDark
import com.weathergpt.app.ui.theme.BorderGlass
import com.weathergpt.app.ui.theme.DangerRed
import com.weathergpt.app.ui.theme.PrimaryBlue
import com.weathergpt.app.ui.theme.SecondaryCyan
import com.weathergpt.app.ui.theme.SuccessGreen
import com.weathergpt.app.ui.theme.TextMuted
import com.weathergpt.app.ui.theme.TextPrimary
import com.weathergpt.app.ui.theme.TextSecondary
import com.weathergpt.app.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.platform.LocalContext
import com.weathergpt.app.location.LocationStore
import com.weathergpt.app.data.MetWeatherClient

@Composable
fun AlertsScreen() {
    val context = LocalContext.current
    val activeLocation = remember { LocationStore.getLocation(context) }
    var damItems by remember { mutableStateOf(DamClient.OFFICIAL_CWC_RESERVOIRS) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var lastUpdatedText by remember { mutableStateOf("Just now") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showAllDams by remember { mutableStateOf(false) }

    var liveAlerts by remember { mutableStateOf<List<com.weathergpt.app.data.AlertItem>>(emptyList()) }
    var activeCycloneAlert by remember { mutableStateOf<com.weathergpt.app.data.AlertItem?>(null) }

    // Periodic time-to-time automatic updating (every 5 minutes) + immediate manual refresh
    LaunchedEffect(refreshKey, activeLocation) {
        while (isActive) {
            try {
                val alertsRes = withContext(Dispatchers.IO) {
                    com.weathergpt.app.data.FeatureClient.api.getAlerts(
                        latitude = activeLocation.latitude,
                        longitude = activeLocation.longitude
                    )
                }
                var alertsList = alertsRes.alerts ?: emptyList()

                // If backend alerts are empty, evaluate local forecast to generate real, accurate alerts
                if (alertsList.isEmpty()) {
                    val weather = withContext(Dispatchers.IO) {
                        try {
                            MetWeatherClient.getCachedWeather(activeLocation.latitude, activeLocation.longitude, context)
                                ?: MetWeatherClient.getFastWeather(activeLocation.latitude, activeLocation.longitude, context)
                        } catch (_: Exception) { null }
                    }
                    if (weather != null && weather.forecast.isNotEmpty()) {
                        val computed = mutableListOf<com.weathergpt.app.data.AlertItem>()
                        val firstForecast = weather.forecast.take(24)

                        // 1. Rain / Precipitation alert
                        val rainyItem = firstForecast.firstOrNull { (it.precipitation_mm ?: 0.0) >= 2.0 || (it.precipitation_probability_pct ?: 0.0) >= 65.0 }
                        if (rainyItem != null) {
                            val precip = rainyItem.precipitation_mm ?: 0.0
                            val prob = rainyItem.precipitation_probability_pct?.toInt() ?: 0
                            val isHigh = precip >= 10.0 || (rainyItem.symbol_code.orEmpty().contains("heavy"))
                            computed.add(
                                com.weathergpt.app.data.AlertItem(
                                    type = "RAIN",
                                    severity = if (isHigh) "HIGH" else "MEDIUM",
                                    source_type = "PRECIPITATION",
                                    time = rainyItem.time,
                                    value = precip,
                                    unit = "mm",
                                    message = if (isHigh) "Heavy rainfall expected (${"%.1f".format(precip)} mm)" else "Rainfall expected (${prob}% chance, ${"%.1f".format(precip)} mm)",
                                    advisory = "Carry an umbrella and avoid low-lying roads during showers.",
                                    pressure_hpa = rainyItem.pressure_hpa
                                )
                            )
                        }

                        // 2. Strong Winds / Gusts alert
                        val windyItem = firstForecast.firstOrNull { (it.wind_speed_ms ?: 0.0) >= 10.0 || (it.wind_gust_ms ?: 0.0) >= 14.0 }
                        if (windyItem != null) {
                            val windKmh = ((windyItem.wind_speed_ms ?: 0.0) * 3.6).toInt()
                            val gustKmh = ((windyItem.wind_gust_ms ?: 0.0) * 3.6).toInt()
                            val isHigh = windKmh >= 50 || gustKmh >= 60
                            computed.add(
                                com.weathergpt.app.data.AlertItem(
                                    type = "WIND",
                                    severity = if (isHigh) "HIGH" else "MEDIUM",
                                    source_type = "WIND",
                                    time = windyItem.time,
                                    value = windKmh,
                                    unit = "km/h",
                                    message = "Strong winds up to $windKmh km/h (gusts $gustKmh km/h)",
                                    advisory = "Secure loose outdoor belongings and take precautions while commuting.",
                                    pressure_hpa = windyItem.pressure_hpa
                                )
                            )
                        }

                        // 3. Extreme Heat or Low Temp
                        val maxTempItem = firstForecast.maxByOrNull { it.temperature_c ?: 0.0 }
                        if (maxTempItem != null && (maxTempItem.temperature_c ?: 0.0) >= 38.0) {
                            val temp = maxTempItem.temperature_c ?: 0.0
                            computed.add(
                                com.weathergpt.app.data.AlertItem(
                                    type = "HEAT",
                                    severity = if (temp >= 42.0) "HIGH" else "MEDIUM",
                                    source_type = "TEMPERATURE",
                                    time = maxTempItem.time,
                                    value = temp,
                                    unit = "°C",
                                    message = "Extreme heat condition (${"%.1f".format(temp)}°C)",
                                    advisory = "Stay hydrated and avoid prolonged midday sun exposure.",
                                    pressure_hpa = maxTempItem.pressure_hpa
                                )
                            )
                        }

                        alertsList = computed
                    }
                }

                liveAlerts = alertsList
                activeCycloneAlert = alertsList.firstOrNull { 
                    it.type == "CYCLONE_WARNING" || it.type == "CYCLONE_ALERT" || it.severity == "CRITICAL"
                }
            } catch (_: Exception) { }

            try {
                val response = withContext(Dispatchers.IO) {
                    DamClient.service.getDams(limit = 200)
                }
                val list = response.reservoirs
                if (!list.isNullOrEmpty()) {
                    damItems = list.sortedByDescending { it.storage_percent ?: 0.0 }
                } else {
                    damItems = DamClient.OFFICIAL_CWC_RESERVOIRS
                }
                val now = LocalTime.now()
                lastUpdatedText = now.format(DateTimeFormatter.ofPattern("h:mm:ss a"))
            } catch (_: Exception) {
                if (damItems.isEmpty()) {
                    damItems = DamClient.OFFICIAL_CWC_RESERVOIRS
                }
                val now = LocalTime.now()
                lastUpdatedText = now.format(DateTimeFormatter.ofPattern("h:mm:ss a"))
            }
            // Auto refresh every 5 minutes in background
            delay(5 * 60 * 1000L)
        }
    }

    val criticalDamsCount = damItems.count { (it.storage_percent ?: 0.0) >= 85.0 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            // =========================================================
            // HEADER (Safety Center)
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Safety Center",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Live warning intelligence",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• Updated $lastUpdatedText",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                var isSpinning by remember { mutableStateOf(false) }
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "alerts_refresh")
                val spinAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                    ),
                    label = "alerts_spin"
                )

                LaunchedEffect(isSpinning) {
                    if (isSpinning) {
                        kotlinx.coroutines.delay(800)
                        isSpinning = false
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable {
                            isSpinning = true
                            refreshKey++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh alerts",
                        tint = if (isSpinning) PrimaryBlue else TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .then(if (isSpinning) Modifier.graphicsLayer { rotationZ = spinAngle } else Modifier)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // =========================================================
            // OVERALL STATUS GLASS CARD
            // =========================================================
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                padding = 16.dp
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
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Warning network online",
                                color = SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // LIVE capsule badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x185A8E72))
                                .border(1.dp, Color(0x305A8E72), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Text(
                                    text = "LIVE",
                                    color = SuccessGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (criticalDamsCount > 0) "$criticalDamsCount critical reservoirs detected" else "No critical warning detected",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "WeatherGPT is monitoring official telemetry and national meteorological alerts.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================
            // AI ALERT PRIORITIZATION — WHAT ACTUALLY MATTERS
            // =========================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🚨 WHAT ACTUALLY MATTERS",
                    color = DangerRed,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // AI Contextual Advice Callout
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                padding = 14.dp
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "💡", fontSize = 18.sp)
                    Column {
                        Text(
                            text = "WeatherGPT Safety Intelligence (${activeLocation.name})",
                            color = SecondaryCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val adviceText = when {
                            activeCycloneAlert != null -> "CRITICAL: Cyclone alert active for this region. Strictly avoid coastal and exposed open areas."
                            liveAlerts.any { it.severity?.uppercase() == "HIGH" || it.severity?.uppercase() == "CRITICAL" } -> {
                                val top = liveAlerts.first { it.severity?.uppercase() == "HIGH" || it.severity?.uppercase() == "CRITICAL" }
                                top.advisory ?: top.message ?: "Hazardous conditions detected. Take necessary precautions."
                            }
                            liveAlerts.isNotEmpty() -> {
                                val top = liveAlerts.first()
                                top.advisory ?: top.message ?: "Moderate weather fluctuations active. Normal precautions advised."
                            }
                            else -> "No adverse weather hazards currently detected for ${activeLocation.name}. All clear for routine travel and outdoor activities."
                        }
                        Text(
                            text = adviceText,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (liveAlerts.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    padding = 16.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "All Clear",
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "All Clear in ${activeLocation.name}",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "No active meteorological warnings or high rain/wind hazards for the next 24 hours.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                // Aggregate by distinct hazard type to eliminate duplicate cards (e.g. repeated hourly MODERATE_RAIN)
                val distinctAlerts = liveAlerts
                    .groupBy { it.type?.uppercase() ?: "OTHER" }
                    .values
                    .map { items ->
                        // Take the item with highest severity
                        val rankMap = mapOf("CRITICAL" to 4, "HIGH" to 3, "MEDIUM" to 2, "MODERATE" to 2, "LOW" to 1)
                        items.maxByOrNull { rankMap[it.severity?.uppercase()] ?: 0 } ?: items.first()
                    }

                val highAlerts = distinctAlerts.filter { it.severity?.uppercase() in listOf("HIGH", "CRITICAL") }
                val mediumAlerts = distinctAlerts.filter { it.severity?.uppercase() in listOf("MEDIUM", "MODERATE") }
                val lowAlerts = distinctAlerts.filter { it !in highAlerts && it !in mediumAlerts }

                fun formatAlertTitle(alert: com.weathergpt.app.data.AlertItem, defaultIcon: String): String {
                    if (!alert.title.isNullOrBlank()) {
                        return "$defaultIcon ${alert.title}"
                    }
                    val type = alert.type?.uppercase() ?: ""
                    return when {
                        type.contains("RAIN") || type.contains("PRECIPITATION") -> {
                            if (alert.severity?.uppercase() in listOf("HIGH", "CRITICAL")) "🌧️ Heavy Rain Alert" else "🌧️ Moderate Rain"
                        }
                        type.contains("WIND") -> {
                            if (alert.severity?.uppercase() in listOf("HIGH", "CRITICAL")) "🌬️ High Winds Warning" else "🌬️ Wind Advisory"
                        }
                        type.contains("HEAT") -> "☀️ Heat Advisory"
                        type.contains("THUNDER") -> "⚡ Thunderstorm Warning"
                        type.contains("CYCLONE") -> "🌀 Cyclone Alert"
                        type.contains("DEPRESSION") -> "⚠️ Deep Depression"
                        else -> "$defaultIcon " + type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    }
                }

                if (highAlerts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "HIGH PRIORITY",
                            color = DangerRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        highAlerts.forEach { alert ->
                            val icon = when {
                                alert.type?.contains("RAIN", ignoreCase = true) == true -> Icons.Default.WaterDrop
                                alert.type?.contains("WIND", ignoreCase = true) == true -> Icons.Default.Warning
                                alert.type?.contains("HEAT", ignoreCase = true) == true -> Icons.Default.Warning
                                else -> Icons.Default.Warning
                            }
                            MockupAlertCard(
                                icon = icon,
                                title = formatAlertTitle(alert, "⚠️"),
                                severity = "HIGH",
                                explanation = alert.message ?: "Hazardous weather conditions detected.",
                                action = alert.advisory ?: "Follow local weather guidance and avoid travel if conditions deteriorate.",
                                accentColor = DangerRed
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (mediumAlerts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "MEDIUM",
                            color = WarningAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        mediumAlerts.forEach { alert ->
                            val icon = when {
                                alert.type?.contains("RAIN", ignoreCase = true) == true -> Icons.Default.WaterDrop
                                alert.type?.contains("WIND", ignoreCase = true) == true -> Icons.Default.Warning
                                alert.type?.contains("HEAT", ignoreCase = true) == true -> Icons.Default.Info
                                else -> Icons.Default.Info
                            }
                            MockupAlertCard(
                                icon = icon,
                                title = formatAlertTitle(alert, "ℹ️"),
                                severity = "MEDIUM",
                                explanation = alert.message ?: "Forecast indicates moderate conditions.",
                                action = alert.advisory ?: "Plan outdoor activities accordingly.",
                                accentColor = WarningAmber
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (lowAlerts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "LOW",
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        lowAlerts.forEach { alert ->
                            MockupAlertCard(
                                icon = Icons.Default.Cloud,
                                title = formatAlertTitle(alert, "🌤️"),
                                severity = "LOW",
                                explanation = alert.message ?: "Minor conditions observed.",
                                action = alert.advisory ?: "Normal routine can continue.",
                                accentColor = SuccessGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================
            // DAMS & RESERVOIRS DATA SECTION
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Reservoir Status",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "CWC Monitored Reservoirs (${damItems.size} across India)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1838BDF8))
                        .border(1.dp, Color(0x4038BDF8), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$criticalDamsCount Critical",
                        color = if (criticalDamsCount > 0) DangerRed else SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar for Reservoirs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search dams",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search by dam, state, district, or basin...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(PrimaryBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips: All, Critical, Southern, Northern, Western, Eastern, Central
            val filterOptions = listOf(
                "All",
                "Critical (>85%)",
                "Southern",
                "Northern",
                "Western",
                "Eastern",
                "Central"
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { option ->
                    val isSelected = selectedFilter == option
                    val chipBg = if (isSelected) PrimaryBlue else Color.White
                    val chipTextColor = if (isSelected) Color.White else TextPrimary
                    val chipBorderColor = if (isSelected) PrimaryBlue else BorderGlass

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorderColor, RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = option }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            color = chipTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter calculation
            val query = searchQuery.trim().lowercase()
            val filteredDams = damItems.filter { dam ->
                val matchesQuery = query.isEmpty() ||
                    (dam.name?.lowercase()?.contains(query) == true) ||
                    (dam.state?.lowercase()?.contains(query) == true) ||
                    (dam.district?.lowercase()?.contains(query) == true) ||
                    (dam.basin?.lowercase()?.contains(query) == true)

                val matchesFilter = when (selectedFilter) {
                    "Critical (>85%)" -> (dam.storage_percent ?: 0.0) >= 85.0
                    "Southern" -> dam.region.equals("Southern", ignoreCase = true)
                    "Northern" -> dam.region.equals("Northern", ignoreCase = true)
                    "Western" -> dam.region.equals("Western", ignoreCase = true)
                    "Eastern" -> dam.region.equals("Eastern", ignoreCase = true)
                    "Central" -> dam.region.equals("Central", ignoreCase = true)
                    else -> true
                }

                matchesQuery && matchesFilter
            }

            // Results count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${filteredDams.size} reservoirs",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (filteredDams.size > 10) {
                    Text(
                        text = if (showAllDams) "Collapse" else "View all ${filteredDams.size}",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showAllDams = !showAllDams }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredDams.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    padding = 24.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🔍", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No reservoirs found",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try clearing the search or changing the region filter.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val displayedDams = if (showAllDams) filteredDams else filteredDams.take(10)
                displayedDams.forEach { dam ->
                    DamAlertCard(dam = dam)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (!showAllDams && filteredDams.size > 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                            .clickable { showAllDams = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Show All ${filteredDams.size} Reservoirs",
                            color = PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Alert card matching the semantic glass design system.
 */
@Composable
private fun MockupAlertCard(
    icon: ImageVector,
    title: String,
    severity: String,
    explanation: String,
    action: String,
    accentColor: Color,
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        padding = 14.dp
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = severity,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = explanation,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Recommendation: $action",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DamAlertCard(
    dam: DamItem
) {
    val pct = dam.storage_percent ?: 0.0
    val statusColor = when {
        pct >= 85.0 -> DangerRed
        pct >= 70.0 -> WarningAmber
        else -> SuccessGreen
    }

    val riskLabel = when {
        pct >= 90.0 -> "CRITICAL SPILL RISK"
        pct >= 85.0 -> "HIGH FLOOD WATCH"
        pct >= 70.0 -> "MODERATE STORAGE"
        else -> "NORMAL CAPACITY"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        padding = 14.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dam.name ?: "Reservoir",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${dam.district?.let { "$it, " } ?: ""}${dam.state ?: ""} • ${dam.basin ?: ""}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(pct)}%",
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Storage Level Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((pct / 100.0).coerceIn(0.0, 1.0).toFloat())
                        .fillMaxSize()
                        .background(statusColor)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry Grid: Current Level vs FRL | Live Storage vs Capacity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "WATER LEVEL",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${dam.current_level_m?.let { "%.1f m".format(it) } ?: "N/A"} / ${dam.frl_m?.let { "%.1f m".format(it) } ?: "N/A"} FRL",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "LIVE STORAGE",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${dam.live_storage_bcm?.let { "%.2f".format(it) } ?: "N/A"} / ${dam.live_capacity_bcm?.let { "%.2f BCM".format(it) } ?: "N/A"}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Flood Risk Tag & Observation Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = riskLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Observed: ${dam.observation_date ?: "Live"}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
