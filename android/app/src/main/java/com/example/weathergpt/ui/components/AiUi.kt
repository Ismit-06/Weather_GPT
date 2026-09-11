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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.BorderGlassSubtle
import com.example.weathergpt.ui.theme.CloudBlue
import com.example.weathergpt.ui.theme.CloudyGray
import com.example.weathergpt.ui.theme.DangerRed
import com.example.weathergpt.ui.theme.FogMuted
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.RainBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SnowIce
import com.example.weathergpt.ui.theme.StormSlate
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.SunnyGold
import com.example.weathergpt.ui.theme.SurfaceDark
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary

/**
 * Minimal & Classy Surface Card.
 * Clean #FFFFFF surface, subtle #E2E5E3 border, restrained 18dp corner radius.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = SurfaceDark,
    borderColor: Color = BorderGlass,
    borderWidth: Dp = 1.dp,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape = shape)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape
            )
            .padding(padding)
    ) {
        content()
    }
}

/**
 * Clean & restrained interactive Chip (12dp radius).
 */
@Composable
fun GlassChip(
    text: String,
    icon: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) PrimaryBlue else Color.White,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlue else BorderGlass,
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * Clean & Human-designed Button (12dp radius).
 */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (primary) PrimaryBlue else Color.White,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (primary) PrimaryBlue else BorderGlass,
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else PrimaryBlue,
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
        modifier = modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Clean & minimal weather illustration using the muted Quiet Sky palette.
 */
@Composable
fun RealisticWeatherIllustration(
    symbolCode: String?,
    modifier: Modifier = Modifier.size(90.dp)
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
            val moonCenter = androidx.compose.ui.geometry.Offset(w * 0.60f, h * 0.38f)
            val moonRadius = w * 0.24f
            drawCircle(
                color = SunnyGold,
                center = moonCenter,
                radius = moonRadius
            )
            drawCircle(
                color = SurfaceDark,
                center = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.34f),
                radius = moonRadius * 0.88f
            )
        } else if (!isCloudy && !isRain && !isSnow) {
            val sunCenter = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.50f)
            drawCircle(
                color = SunnyGold,
                center = sunCenter,
                radius = w * 0.28f
            )
        } else {
            val sunCenter = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.35f)
            drawCircle(
                color = SunnyGold,
                center = sunCenter,
                radius = w * 0.22f
            )
        }

        if (isCloudy || isRain || isSnow || isThunder) {
            val cloudColor = if (isThunder) StormSlate else if (isRain) RainBlue else if (isSnow) SnowIce else CloudBlue
            
            drawCircle(
                color = cloudColor,
                radius = w * 0.22f,
                center = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.54f)
            )
            drawCircle(
                color = cloudColor,
                radius = w * 0.18f,
                center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.58f)
            )
            drawCircle(
                color = cloudColor,
                radius = w * 0.16f,
                center = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.62f)
            )
        }

        if (isRain) {
            val dropPositions = listOf(
                androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.82f),
                androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.86f),
                androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.82f)
            )
            for (p in dropPositions) {
                drawLine(
                    color = RainBlue,
                    start = p,
                    end = androidx.compose.ui.geometry.Offset(p.x - w * 0.04f, p.y + h * 0.08f),
                    strokeWidth = w * 0.035f,
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
                if (positive) SuccessGreen.copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (positive) SuccessGreen.copy(alpha = 0.25f) else DangerRed.copy(alpha = 0.25f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(if (positive) SuccessGreen else DangerRed, CircleShape)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            color = if (positive) SuccessGreen else DangerRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
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
        shape = RoundedCornerShape(16.dp),
        padding = 14.dp
    ) {
        Column {
            Text(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.5.sp
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
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
        subtitle?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
