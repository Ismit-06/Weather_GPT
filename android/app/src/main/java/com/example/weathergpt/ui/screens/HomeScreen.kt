package com.example.weathergpt.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            .background(Color(0xFF080C14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // ====================================================
            // LOCATION SELECTOR ROW
            // ====================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLocationDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = Color(0xFF4DA3FF),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = activeLocation.name,
                    color = Color(0xFFF5F7FA),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ====================================================
            // HERO WEATHER CARD
            // ====================================================
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = weatherDescription(currentWeather?.symbol_code),
                                color = Color(0xFFAAB6C7),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentWeather?.temperature_c?.roundToInt()?.let { "$it°" } ?: "29°",
                                color = Color(0xFFF5F7FA),
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 72.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val feelsLikeTemp = currentWeather?.dew_point_c?.roundToInt()
                                ?: currentWeather?.temperature_c?.roundToInt()?.minus(2)
                                ?: 27
                            Text(
                                text = "Feels like $feelsLikeTemp°",
                                color = Color(0xFFAAB6C7),
                                fontSize = 13.sp
                            )
                        }

                        RealisticWeatherIllustration(
                            symbolCode = currentWeather?.symbol_code,
                            modifier = Modifier.size(98.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isLoading -> "Updating live conditions"
                                hasError -> "Unable to refresh"
                                else -> "Live conditions"
                            },
                            color = Color(0xFF7E8B9F),
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
                                color = Color(0xFF7E8B9F),
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.width(5.dp))

                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF4DA3FF),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ====================================================
            // 2X2 METRICS GRID
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

            Spacer(modifier = Modifier.height(14.dp))

            // ====================================================
            // ASK WEATHERGPT CARD (Screen 1 Mockup)
            // ====================================================
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChat() },
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Ask WeatherGPT",
                            color = Color(0xFFF5F7FA),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "What would you like to know?",
                            color = Color(0xFFAAB6C7),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF4DA3FF),
                                        Color(0xFF2563EB)
                                    )
                                )
                            )
                            .clickable { onOpenChat() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Ask",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card 2: Smart recommendation
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x20FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Smart recommendation",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = recommendation(currentWeather),
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Based on the latest live weather data.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
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
        modifier = modifier
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16233B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFF52D9FF),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                color = Color(0xFFF5F7FA),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                color = Color(0xFFAAB6C7),
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
