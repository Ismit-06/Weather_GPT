package com.example.weathergpt.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.weathergpt.data.LocationReverseClient
import com.example.weathergpt.data.MetForecastItem
import com.example.weathergpt.data.MetWeatherClient
import com.example.weathergpt.data.UserPreferencesStore
import com.example.weathergpt.location.DeviceLocationProvider
import com.example.weathergpt.location.LocationStore
import com.example.weathergpt.location.SelectedLocation
import com.example.weathergpt.ui.components.GlassCard
import com.example.weathergpt.ui.components.RealisticWeatherIllustration
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import com.example.weathergpt.ui.theme.WarningAmber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val userPreferences by UserPreferencesStore.preferences.collectAsState()
    var showPersonalizationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UserPreferencesStore.loadPreferences(context)
    }

    val initialLocation = remember { LocationStore.getLocation(context) }
    val initialCache = remember {
        MetWeatherClient.getCachedWeather(initialLocation.latitude, initialLocation.longitude, context)
    }

    var activeLocation by remember { mutableStateOf(initialLocation) }
    var currentWeather by remember { mutableStateOf<MetForecastItem?>(initialCache?.forecast?.firstOrNull()) }
    var forecastList by remember { mutableStateOf<List<MetForecastItem>>(initialCache?.forecast ?: emptyList()) }
    var selectedForecastTab by remember { mutableIntStateOf(0) }
    var selectedHourIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(initialCache == null) }
    var hasError by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var airQualityState by remember { mutableStateOf<com.example.weathergpt.data.AirQualityCurrent?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }

    // ── Weather loading effect (UNCHANGED) ───────────────────────────────────
    LaunchedEffect(refreshTrigger) {
        while (true) {
            try {
                activeLocation = LocationStore.getLocation(context)
                var latitude = activeLocation.latitude
                var longitude = activeLocation.longitude

                val instantCache = MetWeatherClient.getCachedWeather(latitude, longitude, context)
                if (instantCache != null && instantCache.forecast.isNotEmpty()) {
                    currentWeather = instantCache.forecast.firstOrNull()
                    forecastList = instantCache.forecast
                    isLoading = false
                    hasError = false
                } else {
                    isLoading = (currentWeather == null)
                }

                if (!LocationStore.isManual(context)) {
                    try {
                        val provider = DeviceLocationProvider(context)
                        val deviceLocation = provider.getCurrentLocation()
                        if (deviceLocation != null) {
                            latitude = deviceLocation.latitude
                            longitude = deviceLocation.longitude
                            try {
                                var cityName = activeLocation.name
                                var stateName = activeLocation.admin1
                                var countryName = activeLocation.country
                                val rev = LocationReverseClient.api.reverse(
                                    deviceLocation.latitude, deviceLocation.longitude
                                )
                                if (!rev.name.isNullOrBlank()) cityName = rev.name
                                if (!rev.state.isNullOrBlank()) stateName = rev.state
                                if (!rev.country.isNullOrBlank()) countryName = rev.country
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
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }

                val response = MetWeatherClient.getFastWeather(
                    lat = latitude,
                    lon = longitude,
                    context = context,
                    forceRefresh = (refreshTrigger > 0)
                )
                currentWeather = response.forecast.firstOrNull()
                forecastList = response.forecast
                hasError = false

                // Fetch real live Air Quality concurrently
                try {
                    val aqiData = com.example.weathergpt.data.AirQualityClient.getLiveAirQuality(latitude, longitude)
                    if (aqiData != null) {
                        airQualityState = aqiData
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {
                if (currentWeather == null) hasError = true
            } finally {
                isLoading = false
            }
            delay(10 * 60 * 1000L)
        }
    }

    // ── Spin animation for refresh icon ──────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "home_refresh")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "home_spin"
    )

    // ── Week selector data ────────────────────────────────────────────────────
    val today = remember { LocalDate.now() }
    val weekDays = remember(today) {
        val monday = today.with(DayOfWeek.MONDAY)
        (0..6).map { monday.plusDays(it.toLong()) }
    }

    // ── Root ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Atmospheric depth — ultra subtle soft sky tint
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 500f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // ══════════════════════════════════════════════════════════════════
            // LOCATION ROW
            // ══════════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left — city name + subtitle
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
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeLocation.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        val subtitle = listOfNotNull(activeLocation.admin1, activeLocation.country)
                            .filter { it.isNotBlank() }.joinToString(", ")
                        if (subtitle.isNotBlank()) {
                            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                // Right — action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI preferences
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (userPreferences.isOptInEnabled) PrimaryBlue.copy(alpha = 0.10f)
                                else Color.White
                            )
                            .border(
                                1.dp,
                                if (userPreferences.isOptInEnabled) PrimaryBlue else BorderGlass,
                                CircleShape
                            )
                            .clickable { showPersonalizationDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✦",
                            fontSize = 14.sp,
                            color = if (userPreferences.isOptInEnabled) PrimaryBlue else TextMuted
                        )
                    }
                    // Refresh
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, BorderGlass, CircleShape)
                            .clickable {
                                refreshTrigger++
                                Toast.makeText(
                                    context,
                                    "Updating weather for ${activeLocation.name}...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh weather",
                            tint = if (isLoading) PrimaryBlue else TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .then(
                                    if (isLoading) Modifier.graphicsLayer { rotationZ = spinAngle }
                                    else Modifier
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ══════════════════════════════════════════════════════════════════
            // WEEK / DATE SELECTOR
            // ══════════════════════════════════════════════════════════════════
            val weekOfMonth = (today.dayOfMonth - 1) / 7 + 1
            val monthLabel = today.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$monthLabel, Week $weekOfMonth",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(weekDays) { day ->
                    val isToday = day == today
                    val isPast = day.isBefore(today)
                    val dayName = day.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        .take(3)
                    val dayNum = day.dayOfMonth

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(42.dp)
                    ) {
                        Text(
                            text = dayName.uppercase(),
                            color = when {
                                isToday -> SecondaryCyan
                                isPast  -> TextMuted.copy(alpha = 0.45f)
                                else    -> TextMuted
                            },
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 0.3.sp
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isToday) PrimaryBlue else Color.White)
                                .border(
                                    1.dp,
                                    if (isToday) PrimaryBlue else BorderGlass,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                color = when {
                                    isToday -> Color.White
                                    isPast  -> TextMuted.copy(alpha = 0.45f)
                                    else    -> TextSecondary
                                },
                                fontSize = 13.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════════════════════════════════
            // WEATHER HERO CARD (Clean: Temp + Condition + Illustration)
            // ══════════════════════════════════════════════════════════════════
            val feelsLikeTemp = currentWeather?.dew_point_c?.roundToInt()
                ?: currentWeather?.temperature_c?.roundToInt()?.minus(2)
                ?: 27

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                padding = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // ── LEFT: Condition · Large Temp · Feels Like · Why? ──
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = weatherDescription(currentWeather?.symbol_code),
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = currentWeather?.temperature_c?.roundToInt()
                                ?.let { "$it°" } ?: "--°",
                            color = TextPrimary,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 68.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Feels like $feelsLikeTemp°",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.08f))
                                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                                    .clickable { onOpenChat() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Why? 💡",
                                    color = PrimaryBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // ── RIGHT: Weather Illustration ──
                    RealisticWeatherIllustration(
                        symbolCode = currentWeather?.symbol_code,
                        modifier = Modifier.size(105.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ══════════════════════════════════════════════════════════════════
            // COMPACT 4-METRICS ROW (SPACE-SAVING HORIZONTAL STRIP)
            // ══════════════════════════════════════════════════════════════════
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactHomeMetric(
                        icon = Icons.Default.WaterDrop,
                        value = currentWeather?.relative_humidity_pct?.roundToInt()?.let { "$it%" } ?: "--",
                        label = "Humidity",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(BorderGlass)
                    )
                    CompactHomeMetric(
                        icon = Icons.Default.Air,
                        value = currentWeather?.wind_speed_ms?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "--",
                        label = "Wind",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(BorderGlass)
                    )
                    CompactHomeMetric(
                        icon = Icons.Default.Speed,
                        value = currentWeather?.pressure_hpa?.roundToInt()?.let { "$it" } ?: "--",
                        label = "hPa",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(BorderGlass)
                    )
                    CompactHomeMetric(
                        icon = Icons.Default.CloudQueue,
                        value = currentWeather?.precipitation_mm?.let { "${"%.1f".format(it)} mm" }
                            ?: currentWeather?.precipitation_probability_pct?.roundToInt()?.let { "$it%" }
                            ?: "0.0 mm",
                        label = "Rain",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ══════════════════════════════════════════════════════════════════
            // AI ACTIVITY INSIGHT CARD
            // ══════════════════════════════════════════════════════════════════
            if (userPreferences.isOptInEnabled) {
                val insight = remember(userPreferences.primaryActivity, forecastList, currentWeather) {
                    UserPreferencesStore.evaluateActivityInsight(
                        userPreferences.primaryActivity,
                        forecastList
                    )
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    padding = 14.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x1E38BDF8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "✦", fontSize = 11.sp, color = SecondaryCyan)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI INSIGHT",
                                    color = SecondaryCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            // Suitability badge
                            val badgeBg = when (insight.suitability) {
                                "EXCELLENT" -> Color(0x2810B981)
                                "GOOD"      -> Color(0x2038BDF8)
                                "MODERATE"  -> Color(0x20F59E0B)
                                else        -> Color(0x20EF4444)
                            }
                            val badgeColor = when (insight.suitability) {
                                "EXCELLENT" -> SuccessGreen
                                "GOOD"      -> SecondaryCyan
                                "MODERATE"  -> WarningAmber
                                else        -> Color(0xFFF87171)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(badgeBg)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = insight.suitability,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "${insight.icon} ${insight.summary}",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Best window: ${insight.bestTimeWindow}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "⚙️ Change",
                                color = TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.clickable { showPersonalizationDialog = true }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            } else {
                // Compact "enable" banner
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPersonalizationDialog = true },
                    shape = RoundedCornerShape(18.dp),
                    padding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1638BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✦", fontSize = 11.sp, color = TextMuted)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Enable AI Activity Insights",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x2638BDF8))
                                .border(1.dp, Color(0x4838BDF8), RoundedCornerShape(10.dp))
                                .clickable { UserPreferencesStore.setOptIn(context, true) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Turn On",
                                color = SecondaryCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ══════════════════════════════════════════════════════════════════
            // FORECAST SECTION
            // ══════════════════════════════════════════════════════════════════
            if (forecastList.isNotEmpty()) {
                val next24Hours = remember(forecastList) { forecastList.take(24) }
                val activeItem = next24Hours.getOrNull(selectedHourIndex) ?: forecastList.firstOrNull()
                val next12Hours = remember(forecastList) { forecastList.take(12) }

                // Eyebrow label
                Text(
                    text = "FORECAST",
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── Apple-style segmented control ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimaryBlue.copy(alpha = 0.06f))
                        .padding(3.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Hourly", "7 Days").forEachIndexed { index, label ->
                            val active = selectedForecastTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (active) Color.White else Color.Transparent)
                                    .then(
                                        if (active) Modifier.border(1.dp, BorderGlass, RoundedCornerShape(11.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedForecastTab = index }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) TextPrimary else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedForecastTab == 0) {
                    // ── Hourly strip ─────────────────────────────────────────
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(next12Hours.size) { index ->
                            HourlyItemCard(
                                item = next12Hours[index],
                                isSelected = index == selectedHourIndex,
                                onClick = { selectedHourIndex = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Temperature trend card ────────────────────────────────
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        padding = 16.dp
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Temperature trend",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val headerActiveItem = next24Hours.getOrNull(selectedHourIndex.coerceIn(0, next24Hours.size - 1))
                                if (headerActiveItem != null) {
                                    val headerTemp = headerActiveItem.temperature_c?.roundToInt() ?: 0
                                    val headerTime = formatHour(headerActiveItem.time)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = PrimaryBlue.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f))
                                    ) {
                                        Text(
                                            text = "$headerTemp° · $headerTime",
                                            color = PrimaryBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "↗",
                                        color = PrimaryBlue,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            SplineTemperatureChart(
                                forecastItems = next24Hours,
                                selectedIndex = selectedHourIndex,
                                onSelectIndex = { selectedHourIndex = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Forecast metrics 2×2 (synced with chart pointer) ──────
                    val precipProb = activeItem?.precipitation_probability_pct?.roundToInt()
                    val precipAmount = activeItem?.precipitation_mm
                    val precipDisplay = when {
                        precipProb != null && precipAmount != null && precipAmount > 0.0 ->
                            "$precipProb% (${"%.1f".format(precipAmount)}mm)"
                        precipProb != null -> "$precipProb%"
                        precipAmount != null -> "${"%.1f".format(precipAmount)} mm"
                        else -> "0%"
                    }

                    val cloudPct = activeItem?.cloud_cover_pct ?: 20.0
                    val hourOfDay = try {
                        ZonedDateTime.parse(activeItem?.time).hour
                    } catch (_: Exception) { 12 }
                    val uvText = when {
                        hourOfDay < 6 || hourOfDay >= 18     -> "0 (Night)"
                        cloudPct > 80.0                      -> "Low (1-2)"
                        hourOfDay in 11..15 && cloudPct < 30 -> "Very High (8-9)"
                        hourOfDay in 10..16 && cloudPct < 60 -> "High (6-7)"
                        else                                  -> "Moderate (3-5)"
                    }
                    val windKmH = activeItem?.wind_speed_ms?.let { (it * 3.6).roundToInt() } ?: 6
                    val humidityVal = activeItem?.relative_humidity_pct
                        ?.roundToInt()?.let { "$it%" } ?: "--"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ForecastGridCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Air,
                            title = "Wind",
                            value = "$windKmH km/h"
                        )
                        ForecastGridCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Speed,
                            title = "Humidity",
                            value = humidityVal
                        )
                    }

                } else {
                    // ── Daily / 7 Days view ───────────────────────────────────
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
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ══════════════════════════════════════════════════════════════
                // ADDITIONAL INFO ROW  — UV · Air Quality · Sunrise/Sunset
                // ══════════════════════════════════════════════════════════════
                val curHour = try { ZonedDateTime.parse(currentWeather?.time).hour }
                    catch (_: Exception) { 12 }
                val curCloud = currentWeather?.cloud_cover_pct ?: 20.0
                val (uvValue, uvLabel) = when {
                    curHour < 6 || curHour >= 18          -> Pair("0",  "Night")
                    curCloud > 80.0                        -> Pair("1",  "Low")
                    curHour in 11..15 && curCloud < 30.0   -> Pair("8",  "Very High")
                    curHour in 10..16 && curCloud < 60.0   -> Pair("6",  "High")
                    else                                    -> Pair("4",  "Moderate")
                }
                val uvLabelColor = when (uvLabel) {
                    "Moderate"           -> WarningAmber
                    "High", "Very High"  -> Color(0xFFFF6B6B)
                    else                 -> TextSecondary
                }

                val (sunriseTime, sunsetTime) = remember(
                    activeLocation.latitude, activeLocation.longitude, activeLocation.timezone
                ) {
                    computeSunriseSunset(
                        lat = activeLocation.latitude,
                        lon = activeLocation.longitude,
                        timezoneId = activeLocation.timezone?.ifBlank { "Asia/Kolkata" } ?: "Asia/Kolkata"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // UV Index
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        padding = 12.dp
                    ) {
                        Column {
                            Text(text = "☀️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "UV Index", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = uvValue,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = uvLabel, color = uvLabelColor, fontSize = 10.sp)
                        }
                    }

                    // Air Quality — Real-Time Sensor Telemetry
                    val liveAqi = airQualityState?.usAqi ?: airQualityState?.europeanAqi
                    val aqiValueStr = liveAqi?.toInt()?.toString() ?: "--"
                    val (aqiLabel, aqiColor) = when {
                        liveAqi == null -> Pair("Live", SuccessGreen)
                        liveAqi <= 50   -> Pair("Good", SuccessGreen)
                        liveAqi <= 100  -> Pair("Moderate", WarningAmber)
                        liveAqi <= 150  -> Pair("Sensitive", Color(0xFFFF9800))
                        liveAqi <= 200  -> Pair("Unhealthy", Color(0xFFFF5252))
                        else            -> Pair("Hazardous", Color(0xFF9C27B0))
                    }

                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        padding = 12.dp
                    ) {
                        Column {
                            Text(text = "🌿", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Air Quality", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = aqiValueStr,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = aqiLabel, color = aqiColor, fontSize = 10.sp)
                        }
                    }

                    // Sunrise + Sunset
                    GlassCard(
                        modifier = Modifier.weight(1.25f),
                        shape = RoundedCornerShape(18.dp),
                        padding = 12.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "🌅", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "Sunrise", color = TextMuted, fontSize = 9.sp)
                                    Text(
                                        text = sunriseTime,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "Sunset", color = TextMuted, fontSize = 9.sp)
                                    Text(
                                        text = sunsetTime,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Location Search Dialog (UNCHANGED) ────────────────────────────────
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
                                    val rev = LocationReverseClient.api.reverse(
                                        devLoc.latitude, devLoc.longitude
                                    )
                                    if (!rev.name.isNullOrBlank()) cityName = rev.name
                                    stateName = rev.state
                                    countryName = rev.country
                                } catch (_: Exception) {
                                    try {
                                        val geocoder = android.location.Geocoder(
                                            context, java.util.Locale.getDefault()
                                        )
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            geocoder.getFromLocation(devLoc.latitude, devLoc.longitude, 1) { addrs ->
                                                val a = addrs.firstOrNull()
                                                if (a != null) {
                                                    val n = a.locality ?: a.subAdminArea ?: a.adminArea
                                                    if (!n.isNullOrBlank()) cityName = n
                                                    stateName = a.adminArea
                                                    countryName = a.countryName
                                                }
                                            }
                                        } else {
                                            @Suppress("DEPRECATION")
                                            val addrs = geocoder.getFromLocation(
                                                devLoc.latitude, devLoc.longitude, 1
                                            )
                                            val a = addrs?.firstOrNull()
                                            if (a != null) {
                                                val n = a.locality ?: a.subAdminArea ?: a.adminArea
                                                if (!n.isNullOrBlank()) cityName = n
                                                stateName = a.adminArea
                                                countryName = a.countryName
                                            }
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
                                Toast.makeText(
                                    context,
                                    "Could not acquire location. Please check device location.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            if (e is kotlin.coroutines.cancellation.CancellationException) return@launch
                            Toast.makeText(
                                context, "GPS location unavailable: ${e.message}", Toast.LENGTH_SHORT
                            ).show()
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

        // ── Personalization Dialog (UNCHANGED) ────────────────────────────────
        if (showPersonalizationDialog) {
            PersonalizationPreferencesDialog(
                userPreferences = userPreferences,
                onDismiss = { showPersonalizationDialog = false },
                onToggleOptIn = { enabled -> UserPreferencesStore.setOptIn(context, enabled) },
                onSelectPrimaryActivity = { activity ->
                    UserPreferencesStore.setPrimaryActivity(context, activity)
                    showPersonalizationDialog = false
                    Toast.makeText(context, "Focus activity set to $activity", Toast.LENGTH_SHORT).show()
                },
                onToggleActivity = { activity, enabled ->
                    UserPreferencesStore.toggleActivity(context, activity, enabled)
                }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// HERO METRIC ITEM — compact right-column metric (icon + value + label)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroMetricItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrimaryBlue,
                modifier = Modifier.size(11.dp)
            )
        }
        Column {
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = label,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}
// ════════════════════════════════════════════════════════════════════════════
// COMPACT HOME METRIC — sleek, vertical column with icon, value, and label
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun CompactHomeMetric(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrimaryBlue,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// METRIC GRID CARD — 2x2 grid (Home hero, unchanged)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun MockupGridMetricCard(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(20.dp), padding = 14.dp) {
        Column {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

private fun weatherDescription(symbol: String?): String {
    if (symbol.isNullOrBlank()) return "Clear sky"
    return symbol
        .replace("_", " ")
        .replace("-", " ")
        .replaceFirstChar { it.uppercase() }
}

@Suppress("unused")
private fun recommendation(weather: MetForecastItem?): String {
    if (weather == null) return "Conditions look relatively stable right now."
    val rain = weather.precipitation_probability_pct ?: 0.0
    val temp = weather.temperature_c ?: 0.0
    return when {
        rain >= 70.0 -> "Rain is likely. Keep an umbrella ready."
        temp >= 38.0 -> "High heat expected. Stay hydrated."
        temp >= 35.0 -> "Temperatures elevated. Limit afternoon exposure."
        rain >= 40.0 -> "Chance of rain. Keep rain protection nearby."
        else         -> "Conditions look relatively stable right now."
    }
}

// ════════════════════════════════════════════════════════════════════════════
// FORECAST GRID CARD — 2x2 below temperature trend chart
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun ForecastGridCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String
) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(18.dp), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// HOURLY ITEM CARD — horizontally scrollable forecast strip
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun HourlyItemCard(
    item: MetForecastItem,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val timeLabel = remember(item.time) { formatHour(item.time) }

    Box(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PrimaryBlue.copy(alpha = 0.08f) else Color.White)
            .border(
                1.dp,
                if (isSelected) PrimaryBlue else BorderGlass,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeLabel,
                color = if (isSelected) PrimaryBlue else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SPLINE TEMPERATURE CHART — interactive drag/tap pointer (UNCHANGED)
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

    // X-axis 5 interval time labels computed from actual forecast telemetry
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
                        colors = listOf(PrimaryBlue.copy(alpha = 0.18f), PrimaryBlue.copy(alpha = 0.02f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                drawPath(
                    path = path,
                    color = PrimaryBlue,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Vertical indicator line at selected pointer position
                drawLine(
                    color = PrimaryBlue.copy(alpha = 0.30f),
                    start = Offset(activeX, padY),
                    end = Offset(activeX, h),
                    strokeWidth = 1.dp.toPx()
                )

                // Active point on the spline curve
                drawCircle(
                    color = PrimaryBlue.copy(alpha = 0.20f),
                    radius = 7.dp.toPx(),
                    center = Offset(activeX, activeY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(activeX, activeY)
                )
                drawCircle(
                    color = PrimaryBlue,
                    radius = 2.5.dp.toPx(),
                    center = Offset(activeX, activeY)
                )
            }

            // Min temp label at bottom-left (dimmed if pointer tooltip is nearby to avoid overlap)
            val isPointerNearMinTemp = safeSelectedIndex == 0 && activeY > (canvasHeight - 60f)
            if (!isPointerNearMinTemp) {
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
            }

            // Dynamic Tooltip Callout tracking the active pointer X position smoothly
            val tooltipWidth = 64.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            val tooltipWidthPx = with(density) { tooltipWidth.toPx() }
            val clampedTooltipX = (activeX - tooltipWidthPx / 2f)
                .coerceIn(4f, (canvasWidth - tooltipWidthPx - 4f).coerceAtLeast(4f))
            val tooltipOffsetDp = with(density) { clampedTooltipX.toDp() }

            // Position tooltip badge floating above the active point or top of chart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(5f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .offset(x = tooltipOffsetDp, y = 4.dp)
                        .width(tooltipWidth)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${selectedTemp.roundToInt()}°",
                            color = PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = selectedTimeLabel,
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // X-axis timeline labels
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
        ZonedDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:00"))
    } catch (_: Exception) {
        if (raw.length >= 16 && raw.contains("T")) raw.substring(11, 16) else raw.take(5)
    }
}

private fun formatDay(raw: String?): String {
    if (raw.isNullOrBlank()) return "Today"
    return try {
        ZonedDateTime.parse(raw).format(DateTimeFormatter.ofPattern("EEEE"))
    } catch (_: Exception) { "Day" }
}

// ════════════════════════════════════════════════════════════════════════════
// SUNRISE / SUNSET — approximate computation from lat/lon + date
// ════════════════════════════════════════════════════════════════════════════
private fun computeSunriseSunset(lat: Double, lon: Double, timezoneId: String = "Asia/Kolkata"): Pair<String, String> {
    return try {
        val dayOfYear = LocalDate.now().dayOfYear
        val bRad = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))
        val declRad = Math.toRadians(23.45 * sin(bRad))
        val latRad = Math.toRadians(lat)
        val cosH = -tan(latRad) * tan(declRad)
        if (cosH > 1.0 || cosH < -1.0) return Pair("--:--", "--:--")
        val haDeg = Math.toDegrees(acos(cosH))
        val solarNoonUTC = 12.0 - lon / 15.0
        val zone = try { ZoneId.of(timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
        val offsetHours = zone.rules.getOffset(Instant.now()).totalSeconds / 3600.0
        val sunriseLocal = (solarNoonUTC - haDeg / 15.0 + offsetHours + 24.0) % 24.0
        val sunsetLocal  = (solarNoonUTC + haDeg / 15.0 + offsetHours + 24.0) % 24.0
        fun fmt(h: Double): String {
            val hh = h.toInt().coerceIn(0, 23)
            val mm = ((h - h.toInt()) * 60).roundToInt().coerceIn(0, 59)
            return "%02d:%02d".format(hh, mm)
        }
        Pair(fmt(sunriseLocal), fmt(sunsetLocal))
    } catch (_: Exception) {
        Pair("--:--", "--:--")
    }
}

// ════════════════════════════════════════════════════════════════════════════
// PERSONALIZATION PREFERENCES DIALOG (UNCHANGED)
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun PersonalizationPreferencesDialog(
    userPreferences: com.example.weathergpt.data.UserPreferences,
    onDismiss: () -> Unit,
    onToggleOptIn: (Boolean) -> Unit,
    onSelectPrimaryActivity: (String) -> Unit,
    onToggleActivity: (String, Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(20.dp),
            padding = 18.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🧠", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Activity Preferences",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x0A000000))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BackgroundDark)
                        .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Personalization",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Learns your favorite activities from questions (e.g. running, cycling, commute) to provide daily proactive insights.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = userPreferences.isOptInEnabled,
                        onCheckedChange = onToggleOptIn,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue,
                            uncheckedThumbColor = TextMuted.copy(alpha = 0.4f),
                            uncheckedTrackColor = BorderGlass
                        )
                    )
                }

                if (userPreferences.isOptInEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "PRIMARY FOCUS ACTIVITY",
                        color = PrimaryBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserPreferencesStore.AVAILABLE_ACTIVITIES.forEach { (name, icon) ->
                            val isSelected = userPreferences.primaryActivity == name
                            val isLearned  = userPreferences.learnedActivities.contains(name)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) PrimaryBlue.copy(alpha = 0.08f) else Color.White
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) PrimaryBlue else BorderGlass,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectPrimaryActivity(name) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = name,
                                            color = if (isSelected) PrimaryBlue else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (isLearned) {
                                            Text(
                                                text = "Learned preference",
                                                color = SuccessGreen,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PrimaryBlue.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = PrimaryBlue,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
