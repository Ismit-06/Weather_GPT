package com.example.weathergpt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.ui.theme.AIViolet
import com.example.weathergpt.ui.theme.BackgroundDeep
import com.example.weathergpt.ui.theme.NeonBlue
import com.example.weathergpt.ui.theme.NeonCyan
import com.example.weathergpt.ui.theme.RiskRed
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.SurfaceDark
import com.example.weathergpt.ui.theme.SurfaceGlass
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary

@Composable
fun AiOrb(
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier.size(76.dp),
        contentAlignment =
            Alignment.Center
    ) {

        Box(
            modifier =
                Modifier
                    .size(70.dp)
                    .blur(18.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NeonCyan.copy(0.55f),
                                AIViolet.copy(0.35f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
        )

        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NeonCyan,
                                NeonBlue,
                                AIViolet
                            )
                        ),
                        CircleShape
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "✦",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier =
            modifier
                .background(
                    color = Color(0xFF111E2F),
                    shape = shape
                )
                .border(
                    width = 1.dp,
                    color = Color(0x2EFFFFFF), // 1px solid rgba(255, 255, 255, 0.18)
                    shape = shape
                )
                .padding(18.dp)
    ) {
        content()
    }
}

/**
 * 3D-styled realistic weather illustration using Compose Canvas.
 * Accurately renders glossy 3D clouds, sun, crescent moon, and raindrops
 * without depending on external raster assets.
 */
@Composable
fun RealisticWeatherIllustration(
    symbolCode: String?,
    modifier: Modifier = Modifier.size(100.dp)
) {
    val code = symbolCode?.lowercase() ?: "clearsky_day"
    val isNight = code.contains("night") || code.contains("polar")
    val isRain = code.contains("rain") || code.contains("drizzle")
    val isSnow = code.contains("snow") || code.contains("sleet")
    val isThunder = code.contains("thunder")
    val isCloudy = code.contains("cloud") || code.contains("fog") || code.contains("overcast")

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Draw Sun or Crescent Moon in background if applicable
        if (isNight) {
            // Golden crescent moon
            val moonCenter = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.32f)
            val moonRadius = w * 0.26f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF7A0), Color(0xFFFFD13B), Color(0xFFF59E0B)),
                    center = moonCenter,
                    radius = moonRadius
                ),
                radius = moonRadius,
                center = moonCenter
            )
            // Cut out inner circle with dark sky color to form a clean crescent
            drawCircle(
                color = Color(0xFF111E2F),
                radius = moonRadius * 0.82f,
                center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.38f, moonCenter.y - moonRadius * 0.28f)
            )
        } else if (!isCloudy || code.contains("fair") || code.contains("partly")) {
            // Radiant Golden 3D Sun
            val sunCenter = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.30f)
            val sunRadius = w * 0.24f

            // Outer sun glow
            drawCircle(
                color = Color(0x33FBBF24),
                radius = sunRadius * 1.55f,
                center = sunCenter
            )
            // Inner glowing 3D sun sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFBEB), Color(0xFFFDE047), Color(0xFFF59E0B)),
                    center = androidx.compose.ui.geometry.Offset(sunCenter.x - sunRadius * 0.3f, sunCenter.y - sunRadius * 0.3f),
                    radius = sunRadius
                ),
                radius = sunRadius,
                center = sunCenter
            )
        }

        // 2. Front 3D Fluffy Cloud (rendered with soft dimensional spheres and base)
        val cloudBaseY = h * 0.58f
        val cloudColorTop = Color(0xFFFFFFFF)
        val cloudColorMid = Color(0xFFE2E8F0)
        val cloudColorBottom = Color(0xFF94A3B8)

        val cloudBrush = Brush.verticalGradient(
            colors = listOf(cloudColorTop, cloudColorMid, cloudColorBottom),
            startY = h * 0.25f,
            endY = h * 0.85f
        )

        // Main cloud lobes
        // Base rounded pill
        drawRoundRect(
            brush = cloudBrush,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, cloudBaseY),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.26f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.13f, h * 0.13f)
        )
        // Center-left big puff
        drawCircle(
            brush = cloudBrush,
            radius = w * 0.22f,
            center = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.50f)
        )
        // Center-right secondary puff
        drawCircle(
            brush = cloudBrush,
            radius = w * 0.17f,
            center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.56f)
        )
        // Left small puff
        drawCircle(
            brush = cloudBrush,
            radius = w * 0.15f,
            center = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.62f)
        )

        // Soft cloud rim highlight
        drawCircle(
            color = Color(0x66FFFFFF),
            radius = w * 0.18f,
            center = androidx.compose.ui.geometry.Offset(w * 0.44f, h * 0.46f)
        )

        // 3. Raindrops / Thunder / Snow if applicable
        if (isRain) {
            val dropColor = Color(0xFF38BDF8)
            val dropPositions = listOf(
                androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.86f),
                androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.90f),
                androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.86f)
            )
            for (p in dropPositions) {
                drawLine(
                    color = dropColor,
                    start = p,
                    end = androidx.compose.ui.geometry.Offset(p.x - w * 0.05f, p.y + h * 0.10f),
                    strokeWidth = w * 0.04f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun IntelligenceBadge(
    text: String,
    positive: Boolean = true
) {

    Row(
        modifier =
            Modifier
                .background(
                    if (positive) {
                        SuccessGreen.copy(
                            alpha = 0.10f
                        )
                    } else {
                        RiskRed.copy(
                            alpha = 0.10f
                        )
                    },
                    RoundedCornerShape(50)
                )
                .border(
                    1.dp,
                    if (positive) {
                        SuccessGreen.copy(
                            alpha = 0.25f
                        )
                    } else {
                        RiskRed.copy(
                            alpha = 0.25f
                        )
                    },
                    RoundedCornerShape(50)
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = 5.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .background(
                        if (positive) {
                            SuccessGreen
                        } else {
                            RiskRed
                        },
                        CircleShape
                    )
        )

        Spacer(
            modifier =
                Modifier.size(6.dp)
        )

        Text(
            text = text,

            color =
                if (positive) {
                    SuccessGreen
                } else {
                    RiskRed
                },

            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    modifier: Modifier = Modifier
) {

    GlassCard(
        modifier = modifier
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
                    text = icon,
                    fontSize = androidx.compose.ui.unit
                        .TextUnit.Unspecified
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text = title,
                    color = TextMuted
                )

                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )

                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize =
                        22.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    color = TextSecondary
                )
            }
        }
    }
}


@Composable
fun AiSectionTitle(
    eyebrow: String,
    title: String,
    subtitle: String? = null
) {

    Column {

        Text(
            text = eyebrow.uppercase(),
            color = NeonCyan,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp
        )

        subtitle?.let {

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text = it,
                color = TextMuted
            )
        }
    }
}
