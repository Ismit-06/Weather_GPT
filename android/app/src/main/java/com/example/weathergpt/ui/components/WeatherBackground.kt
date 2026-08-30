package com.example.weathergpt.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.weathergpt.R
import java.time.LocalTime

enum class WeatherVisual {
    MORNING,
    DAY,
    EVENING,
    NIGHT,
    CLOUDY,
    RAIN,
    HEAVY_RAIN,
    STORM,
    FOG,
    SNOW,
    HOT,
    COLD
}

fun chooseWeatherVisual(
    symbolCode: String?,
    temperature: Double?,
    hour: Int = LocalTime.now().hour
): WeatherVisual {

    val symbol = symbolCode?.lowercase().orEmpty()

    if (
        symbol.contains("thunder") ||
        symbol.contains("storm")
    ) {
        return WeatherVisual.STORM
    }

    if (symbol.contains("snow")) {
        return WeatherVisual.SNOW
    }

    if (
        symbol.contains("fog") ||
        symbol.contains("mist")
    ) {
        return WeatherVisual.FOG
    }

    if (
        symbol.contains("heavy") &&
        symbol.contains("rain")
    ) {
        return WeatherVisual.HEAVY_RAIN
    }

    if (
        symbol.contains("rain") ||
        symbol.contains("drizzle")
    ) {
        return WeatherVisual.RAIN
    }

    if (
        symbol.contains("cloud") ||
        symbol.contains("overcast")
    ) {
        return WeatherVisual.CLOUDY
    }

    if (temperature != null) {

        if (temperature >= 40.0) {
            return WeatherVisual.HOT
        }

        if (temperature <= 5.0) {
            return WeatherVisual.COLD
        }
    }

    return when (hour) {

        in 5..9 ->
            WeatherVisual.MORNING

        in 10..15 ->
            WeatherVisual.DAY

        in 16..18 ->
            WeatherVisual.EVENING

        else ->
            WeatherVisual.NIGHT
    }
}

@DrawableRes
private fun WeatherVisual.image(): Int {

    return when (this) {

        WeatherVisual.MORNING ->
            R.drawable.weather_clear_morning

        WeatherVisual.DAY ->
            R.drawable.weather_clear_day

        WeatherVisual.EVENING ->
            R.drawable.weather_clear_evening

        WeatherVisual.NIGHT ->
            R.drawable.weather_clear_night

        WeatherVisual.CLOUDY ->
            R.drawable.weather_cloudy

        WeatherVisual.RAIN ->
            R.drawable.weather_rain

        WeatherVisual.HEAVY_RAIN ->
            R.drawable.weather_heavy_rain

        WeatherVisual.STORM ->
            R.drawable.weather_storm

        WeatherVisual.FOG ->
            R.drawable.weather_fog

        WeatherVisual.SNOW ->
            R.drawable.weather_snow

        WeatherVisual.HOT ->
            R.drawable.weather_hot

        WeatherVisual.COLD ->
            R.drawable.weather_cold
    }
}

@Composable
fun WeatherBackground(
    visual: WeatherVisual,
    darkMode: Boolean,
    content: @Composable () -> Unit
) {

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Image(
            painter = painterResource(visual.image()),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            if (darkMode) {
                                listOf(
                                    Color(0x55000000),
                                    Color(0x88030A16),
                                    Color(0xE6050B16)
                                )
                            } else {
                                listOf(
                                    Color(0x20000000),
                                    Color(0x30000000),
                                    Color(0x80FFFFFF)
                                )
                            }
                        )
                    )
        )

        content()
    }
}
