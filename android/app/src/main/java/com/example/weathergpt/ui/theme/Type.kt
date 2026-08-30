package com.example.weathergpt.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WeatherTypography =
    Typography(

        headlineLarge =
            TextStyle(
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold
            ),

        headlineMedium =
            TextStyle(
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Bold
            ),

        headlineSmall =
            TextStyle(
                fontSize = 23.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold
            ),

        titleLarge =
            TextStyle(
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold
            ),

        titleMedium =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold
            ),

        bodyLarge =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 23.sp
            ),

        bodyMedium =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),

        bodySmall =
            TextStyle(
                fontSize = 12.sp,
                lineHeight = 17.sp
            ),

        labelLarge =
            TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),

        labelSmall =
            TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
    )
