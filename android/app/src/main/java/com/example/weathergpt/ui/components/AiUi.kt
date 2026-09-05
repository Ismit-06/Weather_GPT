package com.example.weathergpt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.ui.theme.AccentPurple
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.BorderGlassSubtle
import com.example.weathergpt.ui.theme.CardGlassSurface
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.RiskRed
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary

/**
 * Premium Glassmorphism Container Card.
 * Implements 24dp corner radius, subtle gradient background (translucent dark surface),
 * thin glass border rgba(120, 190, 255, 0.18), and soft inner highlight.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0xB80A1626), // 72% opacity dark surface
    borderColor: Color = BorderGlass,
    borderWidth: Dp = 1.dp,
    padding: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.60f)
                    )
                ),
                shape = shape
            )
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
            .padding(padding)
    ) {
        content()
    }
}

/**
 * Translucent interactive Glass Chip / Pill (16dp radius).
 */
@Composable
fun GlassChip(
    text: String,
    icon: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) Color(0x334DA3FF) else Color(0x14FFFFFF),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (selected) Color(0x4D52D9FF) else BorderGlassSubtle,
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Text(text = icon, fontSize = 13.sp)
            }
            Text(
                text = text,
                color = if (selected) Color.White else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/**
 * Translucent interactive Glass Button (20dp radius).
 */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (primary) {
                    Brush.horizontalGradient(
                        listOf(PrimaryBlue.copy(alpha = 0.85f), SecondaryCyan.copy(alpha = 0.85f))
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color(0x1AFFFFFF), Color(0x0FFFFFFF))
                    )
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (primary) Color(0x66FFFFFF) else BorderGlass,
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AiOrb(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .blur(18.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            SecondaryCyan.copy(0.45f),
                            AccentPurple.copy(0.30f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            SecondaryCyan,
                            PrimaryBlue,
                            AccentPurple
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 3D-styled realistic weather illustration using Compose Canvas.
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

        if (isNight) {
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
            drawCircle(
                color = Color(0xFF0A1626),
                radius = moonRadius * 0.82f,
                center = androidx.compose.ui.geometry.Offset(moonCenter.x - moonRadius * 0.38f, moonCenter.y - moonRadius * 0.28f)
            )
        } else if (!isCloudy || code.contains("fair") || code.contains("partly")) {
            val sunCenter = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.30f)
            val sunRadius = w * 0.24f

            drawCircle(
                color = Color(0x33FBBF24),
                radius = sunRadius * 1.55f,
                center = sunCenter
            )
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

        val cloudBaseY = h * 0.58f
        val cloudColorTop = Color(0xFFFFFFFF)
        val cloudColorMid = Color(0xFFE2E8F0)
        val cloudColorBottom = Color(0xFF94A3B8)

        val cloudBrush = Brush.verticalGradient(
            colors = listOf(cloudColorTop, cloudColorMid, cloudColorBottom),
            startY = h * 0.25f,
            endY = h * 0.85f
        )

        drawRoundRect(
            brush = cloudBrush,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, cloudBaseY),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.26f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.13f, h * 0.13f)
        )
        drawCircle(
            brush = cloudBrush,
            radius = w * 0.22f,
            center = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.50f)
        )
        drawCircle(
            brush = cloudBrush,
            radius = w * 0.17f,
            center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.56f)
        )
        drawCircle(
            brush = cloudBrush,
            radius = w * 0.15f,
            center = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.62f)
        )

        drawCircle(
            color = Color(0x66FFFFFF),
            radius = w * 0.18f,
            center = androidx.compose.ui.geometry.Offset(w * 0.44f, h * 0.46f)
        )

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
        modifier = Modifier
            .background(
                if (positive) SuccessGreen.copy(alpha = 0.12f) else RiskRed.copy(alpha = 0.12f),
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (positive) SuccessGreen.copy(alpha = 0.28f) else RiskRed.copy(alpha = 0.28f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (positive) SuccessGreen else RiskRed, CircleShape)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            color = if (positive) SuccessGreen else RiskRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
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
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        padding = 16.dp
    ) {
        Column {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
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
            color = SecondaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        subtitle?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
