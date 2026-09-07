package com.example.weathergpt.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.weathergpt.data.LocationReverseClient
import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetWeatherClient
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.location.SelectedLocation
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.components.RealisticWeatherIllustration
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.ui.theme.WarningAmber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeLocation by remember {
        mutableStateOf(LocationStore.getLocation(context))
    }

    var currentWeather by remember {
        mutableStateOf<MetForecastItem?>(null)
    }

    var forecastList by remember {
        mutableStateOf<List<MetForecastItem>>(emptyList())
    }

    var selectedForecastTab by remember {
        mutableIntStateOf(0)
    }

    var selectedHourIndex by remember {
        mutableIntStateOf(0)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var hasError by remember {
        mutableStateOf(false)
    }

    var refreshTrigger by remember {
        mutableIntStateOf(0)
    }

    var showLocationDialog by remember {
        mutableStateOf(false)
    }

    // Weather Loading effect
    LaunchedEffect(refreshTrigger) {
        while (true) {
            try {
                isLoading = true
                activeLocation = LocationStore.getLocation(context)
                var latitude = activeLocation.latitude
                var longitude = activeLocation.longitude

                if (!LocationStore.isManual(context)) {
                    try {
                        val provider = DeviceLocationProvider(context)
                        val deviceLocation = provider.getCurrentLocation()
                        if (deviceLocation != null) {
                            latitude = deviceLocation.latitude
                            longitude = deviceLocation.longitude
                            var cityName = activeLocation.name
                            var stateName = activeLocation.admin1
                            var countryName = activeLocation.country
                            try {
                                val rev = LocationReverseClient.api.reverse(deviceLocation.latitude, deviceLocation.longitude)
                                if (!rev.name.isNullOrBlank()) cityName = rev.name
                                if (!rev.state.isNullOrBlank()) stateName = rev.state
                                if (!rev.country.isNullOrBlank()) countryName = rev.country
                            } catch (_: Exception) {
                                try {
                                    @Suppress("DEPRECATION")
                                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                    val addrs = geocoder.getFromLocation(deviceLocation.latitude, deviceLocation.longitude, 1)
                                    val a = addrs?.firstOrNull()
                                    if (a != null) {
                                        val n = a.locality ?: a.subAdminArea ?: a.adminArea
                                        if (!n.isNullOrBlank()) cityName = n
                                        if (!a.adminArea.isNullOrBlank()) stateName = a.adminArea
                                        if (!a.countryName.isNullOrBlank()) countryName = a.countryName
                                    }
                                } catch (_: Exception) {}
                            }
                            val updated = SelectedLocation(
                                name = cityName,
                                latitude = deviceLocation.latitude,
                                longitude = deviceLocation.longitude,
                                country = countryName,
                                admin1 = stateName,
                                timezone = "Asia/Kolkata"
                            )
                            LocationStore.useGps(context, updated)
                            activeLocation = updated
                        }
                    } catch (_: Exception) {
                        // Fallback to saved location
                    }
                }

                val response = MetWeatherClient.api.getWeather(
                    latitude = latitude,
                    longitude = longitude
                )
                currentWeather = response.forecast.firstOrNull()
                forecastList = response.forecast
                hasError = false
            } catch (_: Exception) {
                hasError = true
            } finally {
                isLoading = false
            }

            delay(10 * 60 * 1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            // ====================================================
            // LOCATION SELECTOR & REFRESH ROW
            // ====================================================
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "home_refresh")
            val spinAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "home_spin"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showLocationDialog = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = activeLocation.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Refresh Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0E1626))
                        .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                        .clickable {
                            refreshTrigger++
                            Toast.makeText(context, "Updating weather for ${activeLocation.name}...", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh weather",
                        tint = if (isLoading) SecondaryCyan else Color(0xFF8896AB),
                        modifier = Modifier
                            .size(17.dp)
                            .then(if (isLoading) Modifier.graphicsLayer { rotationZ = spinAngle } else Modifier)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ====================================================
            // PRIMARY HERO GLASS WEATHER CARD
            // ====================================================
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                padding = 20.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = weatherDescription(currentWeather?.symbol_code),
                                color = TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentWeather?.temperature_c?.roundToInt()?.let { "$it°" } ?: "29°",
                                color = TextPrimary,
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 72.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val feelsLikeTemp = currentWeather?.dew_point_c?.roundToInt()
                                ?: currentWeather?.temperature_c?.roundToInt()?.minus(2)
                                ?: 27
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Feels like $feelsLikeTemp°",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x3338BDF8))
                                        .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(10.dp))
                                        .clickable { onOpenChat() }
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Why? 💡",
                                        color = SecondaryCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        RealisticWeatherIllustration(
                            symbolCode = currentWeather?.symbol_code,
                            modifier = Modifier.size(100.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isLoading -> "Updating live conditions..."
                                hasError -> "Unable to refresh"
                                else -> "Live conditions"
                            },
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                refreshTrigger++
                            }
                        ) {
                            Text(
                                text = "Updated 2 min ago",
                                color = TextMuted,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.width(5.dp))

                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ====================================================
            // 2X2 SECONDARY METRICS GRID
            // ====================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MockupGridMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WaterDrop,
                    value = currentWeather?.relative_humidity_pct?.roundToInt()?.let { "$it%" } ?: "80%",
                    label = "Humidity"
                )

                MockupGridMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Air,
                    value = currentWeather?.wind_speed_ms?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "6 km/h",
                    label = "Wind"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MockupGridMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speed,
                    value = currentWeather?.pressure_hpa?.roundToInt()?.let { "$it hPa" } ?: "1010 hPa",
                    label = "Pressure"
                )

                MockupGridMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CloudQueue,
                    value = currentWeather?.precipitation_mm?.let { "${"%.1f".format(it)} mm" }
                        ?: currentWeather?.precipitation_probability_pct?.roundToInt()?.let { "$it%" }
                        ?: "0.0 mm",
                    label = "Rain chance"
                )
            }

            // ====================================================
            // MERGED FORECAST INTELLIGENCE SECTION (ON SCROLL DOWN)
            // ====================================================
            if (forecastList.isNotEmpty()) {
                val next24Hours = remember(forecastList) { forecastList.take(24) }
                val activeItem = next24Hours.getOrNull(selectedHourIndex) ?: forecastList.firstOrNull()
                val next12Hours = remember(forecastList) { forecastList.take(12) }

                Spacer(modifier = Modifier.height(20.dp))

                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FORECAST INTELLIGENCE",
                            color = SecondaryCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Hourly & 7-Day Outlook",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = activeLocation.name,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3 SEGMENTED TAB SWITCHER (Glass Capsules)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Hourly", "Daily", "7 Days").forEachIndexed { index, label ->
                        val active = selectedForecastTab == index
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
                                .clickable { selectedForecastTab = index }
                                .padding(vertical = 9.dp),
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

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedForecastTab == 0) {
                    // HOURLY STRIP CAROUSEL
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(next12Hours.size) { index ->
                            val item = next12Hours[index]
                            HourlyItemCard(
                                item = item,
                                isSelected = index == selectedHourIndex,
                                onClick = { selectedHourIndex = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // TEMPERATURE TREND SPLINE CHART
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Temperature trend",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Drag or tap graph to inspect",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        padding = 16.dp
                    ) {
                        SplineTemperatureChart(
                            forecastItems = next24Hours,
                            selectedIndex = selectedHourIndex,
                            onSelectIndex = { selectedHourIndex = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2X2 METRICS GRID (Live synced with active point in time)
                    val precipProb = activeItem?.precipitation_probability_pct?.roundToInt()
                    val precipAmount = activeItem?.precipitation_mm
                    val precipDisplay = when {
                        precipProb != null && precipAmount != null && precipAmount > 0.0 -> "$precipProb% (${"%.1f".format(precipAmount)}mm)"
                        precipProb != null -> "$precipProb%"
                        precipAmount != null -> "${"%.1f".format(precipAmount)} mm"
                        else -> "0%"
                    }

                    val cloudPct = activeItem?.cloud_cover_pct ?: 20.0
                    val hourOfDay = try {
                        val zdt = ZonedDateTime.parse(activeItem?.time)
                        zdt.hour
                    } catch (_: Exception) { 12 }
                    val uvText = when {
                        hourOfDay < 6 || hourOfDay >= 18 -> "0 (Night)"
                        cloudPct > 80.0 -> "Low (1-2)"
                        hourOfDay in 11..15 && cloudPct < 30.0 -> "Very High (8-9)"
                        hourOfDay in 10..16 && cloudPct < 60.0 -> "High (6-7)"
                        else -> "Moderate (3-5)"
                    }

                    val windSpeedKmH = activeItem?.wind_speed_ms?.let { (it * 3.6).roundToInt() } ?: 6
                    val humidityVal = activeItem?.relative_humidity_pct?.roundToInt()?.let { "$it%" } ?: "80%"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ForecastGridCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.WaterDrop,
                            title = "Precipitation",
                            value = precipDisplay
                        )

                        ForecastGridCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Cloud,
                            title = "UV Index",
                            value = uvText
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
                            value = "$windSpeedKmH km/h"
                        )

                        ForecastGridCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Speed,
                            title = "Humidity",
                            value = humidityVal
                        )
                    }
                } else {
                    // Daily / 7 Days view
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        forecastList.take(7).forEach { item ->
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
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Location Search Dialog
        if (showLocationDialog) {
            LocationSearchDialog(
                currentLocation = activeLocation.name,
                onDismiss = { showLocationDialog = false },
                isManualMode = LocationStore.isManual(context),
                onUseCurrentLocation = {
                    CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                        try {
                            val provider = DeviceLocationProvider(context)
                            val devLoc = provider.getCurrentLocation()
                            if (devLoc != null) {
                                var cityName = "Current location"
                                var stateName: String? = null
                                var countryName: String? = null
                                try {
                                    val rev = LocationReverseClient.api.reverse(devLoc.latitude, devLoc.longitude)
                                    if (!rev.name.isNullOrBlank()) cityName = rev.name
                                    stateName = rev.state
                                    countryName = rev.country
                                } catch (_: Exception) {
                                    try {
                                        @Suppress("DEPRECATION")
                                        val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                        val addrs = geocoder.getFromLocation(devLoc.latitude, devLoc.longitude, 1)
                                        val a = addrs?.firstOrNull()
                                        if (a != null) {
                                            val n = a.locality ?: a.subAdminArea ?: a.adminArea
                                            if (!n.isNullOrBlank()) cityName = n
                                            stateName = a.adminArea
                                            countryName = a.countryName
                                        }
                                    } catch (_: Exception) {}
                                }

                                val sel = SelectedLocation(
                                    name = cityName,
                                    latitude = devLoc.latitude,
                                    longitude = devLoc.longitude,
                                    country = countryName,
                                    admin1 = stateName,
                                    timezone = "Asia/Kolkata"
                                )
                                LocationStore.useGps(context, sel)
                                activeLocation = sel
                                refreshTrigger++
                                Toast.makeText(context, "Location updated: $cityName", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not acquire location. Please check device location.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            if (e is kotlin.coroutines.cancellation.CancellationException) return@launch
                            Toast.makeText(context, "GPS location unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showLocationDialog = false
                },
                onLocationSelected = { locResult ->
                    val lat = locResult.latitude
                    val lon = locResult.longitude
                    if (lat != null && lon != null) {
                        val sel = SelectedLocation(
                            name = locResult.name ?: "Selected Location",
                            latitude = lat,
                            longitude = lon,
                            country = locResult.country,
                            admin1 = locResult.admin1,
                            timezone = "Asia/Kolkata"
                        )
                        LocationStore.saveLocation(context, sel, manual = true)
                        activeLocation = sel
                        refreshTrigger++
                        Toast.makeText(context, "Location set to ${sel.name}", Toast.LENGTH_SHORT).show()
                    }
                    showLocationDialog = false
                }
            )
        }
    }
}

/**
 * Metric card matching the 2x2 grid in Screen 1 (Home).
 */
@Composable
private fun MockupGridMetricCard(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        padding = 14.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x264DA3FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = SecondaryCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

private fun weatherDescription(symbol: String?): String {
    if (symbol.isNullOrBlank()) {
        return "Clearsky night"
    }
    return symbol
        .replace("_", " ")
        .replace("-", " ")
        .replaceFirstChar { it.uppercase() }
}

private fun recommendation(weather: MetForecastItem?): String {
    if (weather == null) {
        return "Conditions look relatively stable right now."
    }
    val rain = weather.precipitation_probability_pct ?: 0.0
    val temp = weather.temperature_c ?: 0.0

    return when {
        rain >= 70.0 -> "Rain is likely. Keep an umbrella ready and plan outdoor travel carefully."
        temp >= 38.0 -> "High heat is expected. Stay hydrated and limit prolonged afternoon exposure."
        temp >= 35.0 -> "Temperatures are elevated. Take precautions during the hottest part of the day."
        rain >= 40.0 -> "There is a chance of rain. Keep rain protection nearby."
        else -> "Conditions look relatively stable right now."
    }
}

/**
 * 2x2 Metric card used under temperature trend in HomeScreen.
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Hourly forecast glass card with selectable active highlight.
 */
@Composable
private fun HourlyItemCard(
    item: MetForecastItem,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val timeLabel = remember(item.time) {
        formatHour(item.time)
    }

    Box(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0x334DA3FF) else Color(0xB30A1626))
            .border(
                1.dp,
                if (isSelected) Color(0x8052D9FF) else BorderGlass,
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeLabel,
                color = if (isSelected) SecondaryCyan else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
 * interactive pointer indicator that tracks user touch / drag, and real-time
 * floating tooltip callout displaying exact temperature and time.
 */
@Composable
private fun SplineTemperatureChart(
    forecastItems: List<MetForecastItem>,
    selectedIndex: Int = 0,
    onSelectIndex: (Int) -> Unit = {}
) {
    val temps = forecastItems.mapNotNull { it.temperature_c }
    if (temps.size < 2) {
        Text(text = "Not enough forecast data", color = TextMuted, fontSize = 12.sp)
        return
    }

    val minTemp = temps.minOrNull() ?: 20.0
    val maxTemp = temps.maxOrNull() ?: 35.0
    val tempRange = (maxTemp - minTemp).coerceAtLeast(4.0)

    val safeSelectedIndex = selectedIndex.coerceIn(0, forecastItems.size - 1)
    val selectedItem = forecastItems.getOrNull(safeSelectedIndex)
    val selectedTemp = selectedItem?.temperature_c ?: temps[safeSelectedIndex]
    val selectedTimeLabel = formatHour(selectedItem?.time)

    val xAxisLabels = remember(forecastItems) {
        if (forecastItems.size >= 5) {
            val step = (forecastItems.size - 1) / 4.0
            (0..4).map { i ->
                val idx = (i * step).roundToInt().coerceIn(0, forecastItems.size - 1)
                if (i == 0) "Now" else formatHour(forecastItems[idx].time)
            }
        } else {
            listOf("Now", "6H", "12H", "18H", "24H")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .pointerInput(forecastItems.size) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val step = w / (forecastItems.size - 1).coerceAtLeast(1)
                        val nearest = (offset.x / step).roundToInt().coerceIn(0, forecastItems.size - 1)
                        onSelectIndex(nearest)
                    }
                }
                .pointerInput(forecastItems.size) {
                    detectHorizontalDragGestures { change, _ ->
                        val w = size.width.toFloat()
                        val step = w / (forecastItems.size - 1).coerceAtLeast(1)
                        val nearest = (change.position.x / step).roundToInt().coerceIn(0, forecastItems.size - 1)
                        onSelectIndex(nearest)
                    }
                }
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()
            val padY = 32f
            val chartH = (canvasHeight - padY * 2).coerceAtLeast(10f)

            val stepX = canvasWidth / (temps.size - 1).coerceAtLeast(1)
            val activeX = safeSelectedIndex * stepX
            val normY = (selectedTemp - minTemp) / tempRange
            val activeY = padY + chartH * (1.0 - normY).toFloat()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val points = temps.mapIndexed { idx, t ->
                    val x = idx * (w / (temps.size - 1).coerceAtLeast(1))
                    val nY = (t - minTemp) / tempRange
                    val y = padY + chartH * (1.0 - nY).toFloat()
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

                drawLine(
                    color = Color(0x6652D9FF),
                    start = Offset(activeX, padY),
                    end = Offset(activeX, h),
                    strokeWidth = 1.5.dp.toPx()
                )

                drawCircle(
                    color = Color(0x3352D9FF),
                    radius = 8.dp.toPx(),
                    center = Offset(activeX, activeY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = Offset(activeX, activeY)
                )
                drawCircle(
                    color = PrimaryBlue,
                    radius = 2.5.dp.toPx(),
                    center = Offset(activeX, activeY)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "${minTemp.roundToInt()}°",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val tooltipWidth = 64.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            val tooltipWidthPx = with(density) { tooltipWidth.toPx() }
            val clampedTooltipX = (activeX - tooltipWidthPx / 2f)
                .coerceIn(0f, (canvasWidth - tooltipWidthPx).coerceAtLeast(0f))
            val tooltipOffsetDp = with(density) { clampedTooltipX.toDp() }

            Box(
                modifier = Modifier
                    .padding(start = tooltipOffsetDp, top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(tooltipWidth)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE60A1626))
                        .border(1.dp, Color(0x4D52D9FF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${selectedTemp.roundToInt()}°",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedTimeLabel,
                            color = SecondaryCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xAxisLabels.forEach { label ->
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
