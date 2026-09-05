package com.example.weathergpt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WeatherDarkColors =
    darkColorScheme(

        primary =
            NeonBlue,

        onPrimary =
            BackgroundDeep,

        secondary =
            AIViolet,

        onSecondary =
            TextPrimary,

        tertiary =
            NeonCyan,

        onTertiary =
            BackgroundDeep,

        background =
            BackgroundDark,

        onBackground =
            TextPrimary,

        surface =
            SurfaceDark,

        onSurface =
            TextPrimary,

        surfaceVariant =
            SurfaceElevated,

        onSurfaceVariant =
            TextSecondary,

        error =
            RiskRed,

        onError =
            TextPrimary
    )

private val WeatherLightColors =
    lightColorScheme(

        primary =
            ElectricBlue,

        secondary =
            AIViolet,

        tertiary =
            NeonCyan,

        background =
            Color(0xFFF4F7FC),

        onBackground =
            Color(0xFF101522),

        surface =
            Color.White,

        onSurface =
            Color(0xFF101522),

        surfaceVariant =
            Color(0xFFE9EEF7),

        onSurfaceVariant =
            Color(0xFF4D596B),

        error =
            RiskRed
    )



private val ProWeatherTypography =
    Typography(

        displayLarge =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    48.sp,

                lineHeight =
                    52.sp
            ),

        displayMedium =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    38.sp,

                lineHeight =
                    42.sp
            ),

        headlineLarge =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.SemiBold,

                fontSize =
                    30.sp,

                lineHeight =
                    36.sp
            ),

        headlineMedium =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.SemiBold,

                fontSize =
                    24.sp,

                lineHeight =
                    30.sp
            ),

        headlineSmall =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.SemiBold,

                fontSize =
                    21.sp,

                lineHeight =
                    27.sp
            ),

        titleLarge =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.SemiBold,

                fontSize =
                    19.sp
            ),

        titleMedium =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.Medium,

                fontSize =
                    16.sp
            ),

        bodyLarge =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontSize =
                    15.sp,

                lineHeight =
                    22.sp
            ),

        bodyMedium =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontSize =
                    14.sp,

                lineHeight =
                    20.sp
            ),

        bodySmall =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontSize =
                    12.sp,

                lineHeight =
                    18.sp
            ),

        labelLarge =
            TextStyle(
                fontFamily =
                    FontFamily.SansSerif,

                fontWeight =
                    FontWeight.SemiBold,

                fontSize =
                    13.sp
            ),

        labelMedium =
            TextStyle(
                fontFamily =
                    FontFamily.Monospace,

                fontSize =
                    11.sp
            ),

        labelSmall =
            TextStyle(
                fontFamily =
                    FontFamily.Monospace,

                fontSize =
                    10.sp,

                letterSpacing =
                    0.8.sp
            )
    )


@Composable
fun WeatherGPTTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {

    MaterialTheme(

        colorScheme =
            if (darkTheme) {
                WeatherDarkColors
            } else {
                WeatherLightColors
            },

        typography = ProWeatherTypography,

        content =
            content
    )
}
