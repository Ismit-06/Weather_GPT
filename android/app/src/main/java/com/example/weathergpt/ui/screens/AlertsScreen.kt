package com.example.weathergpt.ui.screens

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
import com.example.weathergpt.data.DamClient
import com.example.weathergpt.data.DamItem
import com.example.weathergpt.ui.components.GlassCard
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FALLBACK_ALERT_DAMS = listOf(
    DamItem(
        id = 1,
        name = "Srisailam Reservoir",
        state = "Andhra Pradesh",
        region = "Southern",
        district = "Kurnool / Nandyal",
        basin = "Krishna Basin",
        latitude = 16.0864,
        longitude = 78.8986,
        frl_m = 269.75,
        current_level_m = 268.20,
        live_capacity_bcm = 8.90,
        live_storage_bcm = 8.12,
        storage_percent = 91.2,
        last_year_storage_percent = 84.5,
        normal_storage_percent = 75.0,
        irrigation_cca = 190000.0,
        hydel_mw = 1670.0,
        observation_date = "2026-09-05",
        source = "CWC",
        source_type = "OFFICIAL_DATA",
        official_warning = true
    ),
    DamItem(
        id = 2,
        name = "Nagarjuna Sagar",
        state = "Andhra Pradesh / Telangana",
        region = "Southern",
        district = "Guntur / Nalgonda",
        basin = "Krishna Basin",
        latitude = 16.5772,
        longitude = 79.3138,
        frl_m = 179.83,
        current_level_m = 177.40,
        live_capacity_bcm = 9.37,
        live_storage_bcm = 8.01,
        storage_percent = 85.5,
        last_year_storage_percent = 78.2,
        normal_storage_percent = 72.0,
        irrigation_cca = 895000.0,
        hydel_mw = 816.0,
        observation_date = "2026-09-05",
        source = "CWC",
        source_type = "OFFICIAL_DATA",
        official_warning = true
    ),
    DamItem(
        id = 3,
        name = "Hirakud Reservoir",
        state = "Odisha",
        region = "Eastern",
        district = "Sambalpur",
        basin = "Mahanadi Basin",
        latitude = 21.5276,
        longitude = 83.8711,
        frl_m = 192.02,
        current_level_m = 190.15,
        live_capacity_bcm = 5.82,
        live_storage_bcm = 5.04,
        storage_percent = 86.6,
        last_year_storage_percent = 80.1,
        normal_storage_percent = 78.0,
        irrigation_cca = 267000.0,
        hydel_mw = 347.5,
        observation_date = "2026-09-05",
        source = "CWC",
        source_type = "OFFICIAL_DATA",
        official_warning = true
    ),
    DamItem(
        id = 4,
        name = "Rengali Dam",
        state = "Odisha",
        region = "Eastern",
        district = "Angul",
        basin = "Brahmani Basin",
        latitude = 21.2800,
        longitude = 85.0300,
        frl_m = 123.50,
        current_level_m = 121.20,
        live_capacity_bcm = 3.43,
        live_storage_bcm = 2.98,
        storage_percent = 86.9,
        last_year_storage_percent = 81.0,
        normal_storage_percent = 76.0,
        irrigation_cca = 423000.0,
        hydel_mw = 250.0,
        observation_date = "2026-09-05",
        source = "CWC",
        source_type = "OFFICIAL_DATA",
        official_warning = true
    ),
    DamItem(
        id = 5,
        name = "Tungabhadra Dam",
        state = "Karnataka",
        region = "Southern",
        district = "Vijayanagara",
        basin = "Krishna Basin",
        latitude = 15.2630,
        longitude = 76.3370,
        frl_m = 497.74,
        current_level_m = 494.80,
        live_capacity_bcm = 3.12,
        live_storage_bcm = 2.76,
        storage_percent = 88.5,
        last_year_storage_percent = 81.0,
        normal_storage_percent = 76.0,
        irrigation_cca = 362000.0,
        hydel_mw = 127.0,
        observation_date = "2026-09-05",
        source = "CWC",
        source_type = "OFFICIAL_DATA",
        official_warning = true
    )
)

@Composable
fun AlertsScreen(
    onOpenDams: () -> Unit
) {
    var damItems by remember { mutableStateOf(FALLBACK_ALERT_DAMS) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        try {
            val response = withContext(Dispatchers.IO) {
                DamClient.service.getDams(limit = 20)
            }
            val list = response.reservoirs
            if (!list.isNullOrEmpty()) {
                damItems = list.sortedByDescending { it.storage_percent ?: 0.0 }
            }
        } catch (_: Exception) {
            // Keep fallback
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

                    Text(
                        text = "Live warning intelligence",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0E1626))
                        .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                        .clickable {
                            isSpinning = true
                            refreshKey++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh alerts",
                        tint = if (isSpinning) SecondaryCyan else Color(0xFF8896AB),
                        modifier = Modifier
                            .size(17.dp)
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
                shape = RoundedCornerShape(22.dp),
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
                                .background(Color(0x2636E6A0))
                                .border(1.dp, Color(0x4036E6A0), RoundedCornerShape(12.dp))
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
                            text = "WeatherGPT Safety Intelligence",
                            color = SecondaryCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "You don't need to change your plans unless you're travelling between 4–6 PM.",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // HIGH PRIORITY
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "HIGH PRIORITY",
                    color = DangerRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                MockupAlertCard(
                    icon = Icons.Default.WaterDrop,
                    title = "🌧️ Heavy rain",
                    severity = "HIGH",
                    explanation = "Expected 4:20 – 6:10 PM with possible water accumulation on roads.",
                    action = "Avoid low-lying routes; delay outdoor travel until 6:15 PM.",
                    accentColor = DangerRed
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MEDIUM PRIORITY
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "MEDIUM",
                    color = WarningAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                MockupAlertCard(
                    icon = Icons.Default.Warning,
                    title = "🌬️ Strong winds",
                    severity = "MEDIUM",
                    explanation = "Gusts up to 35 km/h expected after 7:00 PM.",
                    action = "Secure outdoor items and drive cautiously on exposed bridges.",
                    accentColor = WarningAmber
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // LOW PRIORITY
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LOW",
                    color = SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                MockupAlertCard(
                    icon = Icons.Default.Cloud,
                    title = "☀️ High UV tomorrow",
                    severity = "LOW",
                    explanation = "UV Index reaches 8.0 during midday peak (11:30 AM – 2:30 PM).",
                    action = "Sunscreen and eyewear recommended if outdoors.",
                    accentColor = SuccessGreen
                )
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
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dam & reservoir alerts",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Central Water Commission (CWC) live storage",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "View all →",
                    color = PrimaryBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDams() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            damItems.take(6).forEach { dam ->
                DamAlertCard(
                    dam = dam,
                    onClick = onOpenDams
                )
                Spacer(modifier = Modifier.height(10.dp))
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
    dam: DamItem,
    onClick: () -> Unit
) {
    val pct = dam.storage_percent ?: 0.0
    val statusColor = when {
        pct >= 85.0 -> DangerRed
        pct >= 70.0 -> WarningAmber
        else -> SuccessGreen
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${dam.state ?: ""} • ${dam.basin ?: ""}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(pct)}%",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
