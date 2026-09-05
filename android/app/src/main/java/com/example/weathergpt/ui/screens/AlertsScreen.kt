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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.ui.components.GlassCard

@Composable
fun AlertsScreen(
    onOpenDams: () -> Unit
) {
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
            // HEADER
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weather safety center",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Live warning intelligence",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh alerts",
                        tint = Color(0xFF388BFF),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================
            // OVERALL STATUS CARD
            // =========================================================
            GlassCard(
                modifier = Modifier.fillMaxWidth()
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
                                    .background(Color(0xFF22C55E))
                            )

                            Spacer(modifier = Modifier.width(7.dp))

                            Text(
                                text = "Monitoring active",
                                color = Color(0xFF22C55E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // LIVE capsule badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF132036))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    color = Color(0xFF22C55E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Warning network online",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "No critical warning detected",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "WeatherGPT is monitoring official warning signals.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================
            // WHAT NEEDS ATTENTION
            // =========================================================
            Column {
                Text(
                    text = "What needs attention",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Warnings are ranked by potential user impact.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Alert 1: Moderate (Amber)
            MockupAlertCard(
                icon = Icons.Default.WaterDrop,
                title = "Heavy rain watch",
                severity = "MODERATE",
                explanation = "Periods of heavy rainfall may affect travel and low-lying areas.",
                action = "Keep rain protection ready.",
                accentColor = Color(0xFFF59E0B)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Alert 2: Monitor (Blue) - Clicking opens dams/flood module
            MockupAlertCard(
                icon = Icons.Default.Warning,
                title = "Flood intelligence",
                severity = "MONITOR",
                explanation = "Water-related risk should be checked using the live flood module.",
                action = "Review flood conditions and nearby infrastructure.",
                accentColor = Color(0xFF388BFF),
                onClick = onOpenDams
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Alert 3: Normal (Green)
            MockupAlertCard(
                icon = Icons.Default.Cloud,
                title = "Weather conditions",
                severity = "NORMAL",
                explanation = "No major severe-weather signal is currently displayed.",
                action = "Continue monitoring live conditions.",
                accentColor = Color(0xFF22C55E)
            )

            Spacer(modifier = Modifier.height(26.dp))
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
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
                            .size(34.dp)
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

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Severity pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = severity,
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = explanation,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ACTION",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = action,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
            }
        }
    }
}
