package com.example.weathergpt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* ============================================================
   "QUIET SKY" PALETTE SCHEME (80% Neutral, 15% Muted, 5% Accent)
   ============================================================ */

private val QuietSkyColorScheme =
    darkColorScheme(
        primary = PrimaryBlue,
        onPrimary = Color.White,
        secondary = SecondaryCyan,
        onSecondary = Color.White,
        tertiary = SecondaryCyan,
        onTertiary = Color.White,
        background = BackgroundDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceElevated,
        onSurfaceVariant = TextSecondary,
        error = DangerRed,
        onError = Color.White
    )

private val QuietSkyLightColorScheme =
    lightColorScheme(
        primary = PrimaryBlue,
        onPrimary = Color.White,
        secondary = SecondaryCyan,
        onSecondary = Color.White,
        tertiary = SecondaryCyan,
        onTertiary = Color.White,
        background = BackgroundDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceElevated,
        onSurfaceVariant = TextSecondary,
        error = DangerRed,
        onError = Color.White
    )

private val ProWeatherTypography =
    Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            color = TextPrimary
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            color = TextPrimary
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = TextPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            color = TextPrimary
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            color = TextPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = TextPrimary
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = TextPrimary
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = TextPrimary
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = TextPrimary
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = TextSecondary
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = TextPrimary
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            color = TextSecondary
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = TextMuted
        )
    )

@Composable
fun WeatherGPTTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) QuietSkyColorScheme else QuietSkyLightColorScheme,
        typography = ProWeatherTypography,
        content = content
    )
}
