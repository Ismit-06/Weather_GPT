package com.example.weathergpt.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.components.RealisticWeatherIllustration
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.viewmodel.ForecastState
import com.example.weathergpt.viewmodel.ForecastViewModel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ForecastScreen(
    viewModel: ForecastViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedLocation by remember {
        mutableStateOf(LocationStore.getLocation(context))
    }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        try {
            if (LocationStore.isManual(context)) {
                viewModel.loadForecast(
                    selectedLocation.latitude,
                    selectedLocation.longitude
                )
            } else {
                val provider = DeviceLocationProvider(context)
                val location = provider.getCurrentLocation()
                if (location != null) {
                    viewModel.loadForecast(
                        location.latitude,
                        location.longitude
                    )
                } else {
                    viewModel.loadForecast(
                        selectedLocation.latitude,
                        selectedLocation.longitude
                    )
                }
            }
        } catch (_: Exception) {
            viewModel.loadForecast(
                selectedLocation.latitude,
                selectedLocation.longitude
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        when (val currentState = state) {
            ForecastState.Loading -> {
                ForecastLoading()
            }
            is ForecastState.Error -> {
                ForecastError(
                    message = currentState.message,
                    onRetry = { viewModel.refreshNow() }
                )
            }
            is ForecastState.Success -> {
                ForecastContent(
                    locationName = selectedLocation.name,
                    forecast = currentState.weather.forecast,
                    onRefresh = { viewModel.refreshNow() }
                )
            }
        }
    }
}

@Composable
private fun ForecastLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Loading forecast",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Fetching live weather telemetry...",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ForecastError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Forecast unavailable",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            color = TextMuted,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text(text = "Retry")
        }
    }
}

@Composable
private fun ForecastContent(
    locationName: String,
    forecast: List<MetForecastItem>,
    onRefresh: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val current = forecast.firstOrNull()
    val nextHours = forecast.take(12)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        // =========================================================
        // LOCATION ROW & REFRESH
        // =========================================================
        var isSpinning by remember { mutableStateOf(false) }
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "forecast_refresh")
        val spinAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "forecast_spin"
        )

        LaunchedEffect(isSpinning) {
            if (isSpinning) {
                kotlinx.coroutines.delay(800)
                isSpinning = false
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = locationName,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0E1626))
                    .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                    .clickable {
                        isSpinning = true
                        onRefresh()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh forecast",
                    tint = if (isSpinning) SecondaryCyan else Color(0xFF8896AB),
                    modifier = Modifier
                        .size(16.dp)
                        .then(if (isSpinning) Modifier.graphicsLayer { rotationZ = spinAngle } else Modifier)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =========================================================
        // 3 SEGMENTED TAB SWITCHER (Glass Capsules)
        // =========================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Hourly", "Daily", "7 Days").forEachIndexed { index, label ->
                val active = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) Color(0x334DA3FF) else Color(0xB30A1626))
                        .border(
                            1.dp,
                            if (active) Color(0x6652D9FF) else BorderGlass,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // =========================================================
            // HOURLY STRIP CAROUSEL
            // =========================================================
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nextHours) { item ->
                    HourlyItemCard(item = item)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================
            // TEMPERATURE TREND SPLINE CHART
            // =========================================================
            Text(
                text = "Temperature trend",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                padding = 16.dp
            ) {
                SplineTemperatureChart(
                    forecastItems = forecast.take(24)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================
            // 2X2 METRICS GRID
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ForecastGridCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WaterDrop,
                    title = "Precipitation",
                    value = current?.precipitation_probability_pct?.roundToInt()?.let { "$it%" } ?: "10%"
                )

                ForecastGridCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Cloud,
                    title = "UV Index",
                    value = "Moderate"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ForecastGridCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Air,
                    title = "Wind",
                    value = current?.wind_speed_ms?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "6 km/h"
                )

                ForecastGridCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speed,
                    title = "Humidity",
                    value = current?.relative_humidity_pct?.roundToInt()?.let { "$it%" } ?: "80%"
                )
            }
        } else {
            // Daily / 7 Days view
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                forecast.take(7).forEach { item ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        padding = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formatDay(item.time),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = weatherDescription(item.symbol_code),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            RealisticWeatherIllustration(
                                symbolCode = item.symbol_code,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = item.temperature_c?.roundToInt()?.let { "$it°" } ?: "--°",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 2x2 Metric card used under temperature trend in ForecastScreen.
 */
@Composable
private fun ForecastGridCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        padding = 12.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x264DA3FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = SecondaryCyan,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Hourly forecast glass card.
 */
@Composable
private fun HourlyItemCard(
    item: MetForecastItem
) {
    val timeLabel = remember(item.time) {
        formatHour(item.time)
    }

    Box(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB30A1626))
            .border(1.dp, BorderGlass, RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeLabel,
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            RealisticWeatherIllustration(
                symbolCode = item.symbol_code,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.temperature_c?.roundToInt()?.let { "$it°" } ?: "--°",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Spline temperature chart with smooth cubic bezier curve, gradient fill,
 * min/max callouts, peak tooltip, and X-axis labels.
 */
@Composable
private fun SplineTemperatureChart(
    forecastItems: List<MetForecastItem>
) {
    val temps = forecastItems.mapNotNull { it.temperature_c }
    if (temps.size < 2) {
        Text(text = "Not enough forecast data", color = TextMuted, fontSize = 12.sp)
        return
    }

    val minTemp = temps.minOrNull() ?: 20.0
    val maxTemp = temps.maxOrNull() ?: 35.0
    val tempRange = (maxTemp - minTemp).coerceAtLeast(4.0)

    val peakIndex = temps.indexOf(maxTemp).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padY = 24f
                val chartH = h - padY * 2

                val points = temps.mapIndexed { idx, t ->
                    val x = idx * (w / (temps.size - 1).coerceAtLeast(1))
                    val normY = (t - minTemp) / tempRange
                    val y = padY + chartH * (1.0 - normY).toFloat()
                    Offset(x, y)
                }

                val path = Path()
                path.moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx1 = (p0.x + p1.x) / 2f
                    val cx2 = cx1
                    path.cubicTo(cx1, p0.y, cx2, p1.y, p1.x, p1.y)
                }

                val fillPath = Path()
                fillPath.addPath(path)
                fillPath.lineTo(w, h)
                fillPath.lineTo(0f, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x334DA3FF), Color(0x054DA3FF), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                drawPath(
                    path = path,
                    color = PrimaryBlue,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                if (peakIndex in points.indices) {
                    val peakPoint = points[peakIndex]
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = peakPoint
                    )
                    drawCircle(
                        color = PrimaryBlue,
                        radius = 2.dp.toPx(),
                        center = peakPoint
                    )
                }
            }

            // Min temp label at bottom-left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "${minTemp.roundToInt()}°",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Tooltip callout pill at peak point
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xD90A1626))
                        .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${maxTemp.roundToInt()}°\n18:00",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Now", "6H", "12H", "18H", "24H").forEach { label ->
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatHour(raw: String?): String {
    if (raw.isNullOrBlank()) return "--:--"
    return try {
        val parsed = ZonedDateTime.parse(raw)
        parsed.format(DateTimeFormatter.ofPattern("HH:00"))
    } catch (_: Exception) {
        if (raw.length >= 16 && raw.contains("T")) {
            raw.substring(11, 16)
        } else {
            raw.take(5)
        }
    }
}

private fun formatDay(raw: String?): String {
    if (raw.isNullOrBlank()) return "Today"
    return try {
        val parsed = ZonedDateTime.parse(raw)
        parsed.format(DateTimeFormatter.ofPattern("EEEE"))
    } catch (_: Exception) {
        "Day"
    }
}

private fun weatherDescription(symbol: String?): String {
    if (symbol.isNullOrBlank()) {
        return "Partly cloudy"
    }
    return symbol
        .replace("_", " ")
        .replace("-", " ")
        .replaceFirstChar { it.uppercase() }
}
