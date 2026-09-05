package com.example.weathergpt.ui.screens

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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.data.DamClient
import com.example.weathergpt.data.DamItem
import com.example.weathergpt.ui.components.GlassCard
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
    val highDamsCount = damItems.count { (it.storage_percent ?: 0.0) in 75.0..84.9 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // =========================================================
            // HEADER (Screen 5 Mockup)
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Safety Center",
                        color = Color(0xFFF5F7FA),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Live warning intelligence",
                        color = Color(0xFFAAB6C7),
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = { refreshKey++ },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh alerts",
                        tint = Color(0xFF4DA3FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================
            // OVERALL STATUS CARD (Screen 5 Mockup)
            // =========================================================
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
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
                                    .background(Color(0xFF36E6A0))
                            )

                            Spacer(modifier = Modifier.width(7.dp))

                            Text(
                                text = "Monitoring active",
                                color = Color(0xFF36E6A0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // LIVE capsule badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0E1626))
                                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF36E6A0))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    color = Color(0xFF36E6A0),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (criticalDamsCount > 0) "$criticalDamsCount critical reservoirs detected" else "No critical warning detected",
                        color = Color(0xFFF5F7FA),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "WeatherGPT is monitoring official sources.",
                        color = Color(0xFFAAB6C7),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================
            // WHAT NEEDS ATTENTION (Screen 5 Mockup)
            // =========================================================
            Text(
                text = "What needs attention",
                color = Color(0xFFF5F7FA),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Alert 1: Moderate (Amber)
            MockupAlertCard(
                icon = Icons.Default.WaterDrop,
                title = "Heavy rain watch",
                severity = "MODERATE",
                explanation = "Periods of heavy rainfall may affect travel and low-lying areas.",
                action = "Keep rain protection ready.",
                accentColor = Color(0xFFFFB84D)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Alert 2: Monitor (Blue) - Clicking opens dams/flood module
            MockupAlertCard(
                icon = Icons.Default.Warning,
                title = "Flood intelligence",
                severity = "MONITOR",
                explanation = "Water-related risk should be checked using live flood modules.",
                action = "Review flood conditions and nearby reservoir infrastructure.",
                accentColor = Color(0xFF4DA3FF),
                onClick = onOpenDams
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Alert 3: Normal (Green)
            MockupAlertCard(
                icon = Icons.Default.Cloud,
                title = "Weather conditions",
                severity = "NORMAL",
                explanation = "No major severe-weather signal is currently displayed.",
                action = "Continue monitoring live conditions.",
                accentColor = Color(0xFF36E6A0)
            )

            Spacer(modifier = Modifier.height(26.dp))

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
                            tint = Color(0xFF388BFF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dam & reservoir alerts",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Central Water Commission (CWC) live storage",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "View all →",
                    color = Color(0xFF388BFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDams() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            damItems.take(6).forEach { dam ->
                DamAlertCard(
                    dam = dam,
                    onClick = onOpenDams
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Alert card matching Screen 5 (Alerts) in the reference mockup.
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
        shape = RoundedCornerShape(20.dp)
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
                        color = Color(0xFFF5F7FA),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Severity pill (Screen 5 Mockup)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
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
                color = Color(0xFFAAB6C7),
                fontSize = 12.sp,
                lineHeight = 17.sp
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
    val isCritical = pct >= 85.0
    val isHigh = pct >= 75.0 && !isCritical

    val statusColor = when {
        isCritical -> Color(0xFFFF4D4D)
        isHigh -> Color(0xFFFFA500)
        else -> Color(0xFF388BFF)
    }

    val statusText = when {
        isCritical -> "CRITICAL"
        isHigh -> "HIGH"
        else -> "NORMAL"
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = dam.name ?: "Reservoir",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val subInfo = listOfNotNull(dam.state, dam.basin).joinToString(" • ")
                        if (subInfo.isNotBlank()) {
                            Text(
                                text = subInfo,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$statusText ${String.format(java.util.Locale.US, "%.1f", pct)}%",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Storage progress bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current Fill",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", pct)}% of full capacity",
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((pct / 100.0).coerceIn(0.0, 1.0).toFloat())
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        statusColor.copy(alpha = 0.7f),
                                        statusColor
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Level
                if (dam.current_level_m != null) {
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        label = "CURRENT LEVEL",
                        value = "${dam.current_level_m}m" + if (dam.frl_m != null) " (FRL ${dam.frl_m}m)" else ""
                    )
                }

                // Metric 2: Storage
                if (dam.live_storage_bcm != null) {
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        label = "LIVE STORAGE",
                        value = "${dam.live_storage_bcm} BCM"
                    )
                }

                // Metric 3: Power
                if (dam.hydel_mw != null && dam.hydel_mw > 0) {
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        label = "HYDEL POWER",
                        value = "${dam.hydel_mw.toInt()} MW"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advisory line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS:",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCritical) {
                        "Approaching Full Reservoir Level (FRL). Discharge gates under active watch."
                    } else if (isHigh) {
                        "High reservoir storage buffer. Inflow monitoring active."
                    } else {
                        "Normal seasonal water holding capacity."
                    },
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                color = Color(0xFF64748B),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
