package com.example.weathergpt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weathergpt.data.DamClient
import com.example.weathergpt.data.DamItem
import com.example.weathergpt.data.DamResponse
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.NeonBlue
import com.example.weathergpt.ui.theme.NeonCyan
import com.example.weathergpt.ui.theme.RiskOrange
import com.example.weathergpt.ui.theme.RiskRed
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.SurfaceDark
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DamScreen(
    onBack: () -> Unit
) {

    var response by remember {
        mutableStateOf<DamResponse?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var refreshKey by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(refreshKey) {

        loading = true
        error = null

        try {

            val result =
                withContext(Dispatchers.IO) {
                    DamClient.service.getDams(
                        state = null,
                        limit = 100
                    )
                }

            response = result

            if (
                result.status
                    ?.lowercase()
                    != "success"
            ) {

                error =
                    result.message
                        ?: "Unable to load reservoir data."
            } else {
                error = null
            }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {

            error =
                when (exception) {
                    is java.net.SocketTimeoutException ->
                        "Connection timed out. The cloud server may be waking up, please tap Retry."
                    is java.net.UnknownHostException ->
                        "Unable to reach server. Please check your internet connection."
                    else ->
                        exception.message
                            ?: "Unable to connect to WeatherGPT."
                }

        } finally {

            loading = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    BackgroundDark
                )
    ) {

        DamHeader(
            onBack = onBack,
            onRefresh = {
                refreshKey++
            }
        )

        when {

            loading -> {
                LoadingState()
            }

            error != null -> {
                ErrorState(
                    message =
                        error
                            ?: "Unknown error",

                    onRetry = {
                        refreshKey++
                    }
                )
            }

            response != null -> {
                DamContent(
                    response = response!!
                )
            }

            else -> {
                EmptyState()
            }
        }
    }
}


/* ============================================================
   HEADER
   ============================================================ */

@Composable
private fun DamHeader(
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 14.dp
                )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        "DAM INTELLIGENCE",

                    color =
                        NeonCyan,

                    fontSize =
                        10.sp,

                    letterSpacing =
                        1.4.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )

                Text(
                    text =
                        "Reservoir network",

                    color =
                        TextPrimary,

                    fontSize =
                        28.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )

                Text(
                    text =
                        "Official CWC storage monitoring",

                    color =
                        TextMuted,

                    fontSize =
                        12.sp
                )
            }

            OutlinedButton(
                onClick =
                    onRefresh
            ) {

                Text(
                    text =
                        "Refresh"
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(9.dp)
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            StatusPill(
                text =
                    "LIVE",

                color =
                    SuccessGreen
            )

            StatusPill(
                text =
                    "CWC",

                color =
                    NeonCyan
            )
        }

        Spacer(
            modifier =
                Modifier.height(9.dp)
        )

        Button(
            onClick =
                onBack
        ) {

            Text(
                text =
                    "Back to WeatherGPT"
            )
        }
    }
}


/* ============================================================
   MAIN CONTENT
   ============================================================ */

@Composable
private fun DamContent(
    response: DamResponse
) {

    val dams =
        response.reservoirs
            ?: emptyList()

    val highCount =
        dams.count { dam ->
            (dam.storage_percent ?: 0.0) >= 80.0
        }

    val watchCount =
        dams.count { dam ->
            val storage =
                dam.storage_percent ?: 0.0

            storage >= 60.0
        }

    val warningCount =
        dams.count {
            it.official_warning == true
        }

    val normalCount =
        (
            dams.size
                - highCount
                - (
                    watchCount
                        - highCount
                )
        ).coerceAtLeast(0)

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),

        contentPadding =
            PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            NetworkOverview(
                response = response,

                total =
                    dams.size,

                normal =
                    normalCount,

                watch =
                    watchCount
                        - highCount,

                high =
                    highCount,

                warnings =
                    warningCount
            )
        }

        if (
            warningCount > 0
        ) {

            item {

                WarningBanner(
                    count =
                        warningCount
                )
            }
        }

        item {

            SectionHeading(
                eyebrow =
                    "RESERVOIRS",

                title =
                    "Live storage intelligence",

                subtitle =
                    "Storage percentage is used as a visual readiness signal."
            )
        }

        if (dams.isEmpty()) {

            item {
                EmptyState()
            }

        } else {

            items(
                items = dams,

                key = {
                    it.id
                        ?: it.name.hashCode()
                }
            ) { dam ->

                ImpactDamCard(
                    dam = dam
                )
            }
        }

        item {

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            SourceCard(
                response =
                    response
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )
        }
    }
}


/* ============================================================
   NETWORK OVERVIEW
   ============================================================ */

@Composable
private fun NetworkOverview(
    response: DamResponse,
    total: Int,
    normal: Int,
    watch: Int,
    high: Int,
    warnings: Int
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    SurfaceDark
            )
    ) {

        Column(
            modifier =
                Modifier.padding(18.dp)
        ) {

            Text(
                text =
                    "REGIONAL WATER NETWORK",

                color =
                    NeonCyan,

                fontSize =
                    9.sp,

                letterSpacing =
                    1.1.sp
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.Bottom
            ) {

                Column {

                    Text(
                        text =
                            total.toString(),

                        color =
                            TextPrimary,

                        fontSize =
                            38.sp
                    )

                    Text(
                        text =
                            "monitored reservoirs",

                        color =
                            TextMuted,

                        fontSize =
                            11.sp
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(
                        text =
                            if (
                                warnings > 0
                            ) {
                                "$warnings OFFICIAL WARNINGS"
                            } else {
                                "NO OFFICIAL WARNINGS"
                            },

                        color =
                            if (
                                warnings > 0
                            ) {
                                RiskRed
                            } else {
                                SuccessGreen
                            },

                        fontSize =
                            9.sp,

                        letterSpacing =
                            0.8.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(17.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {

                NetworkStat(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "NORMAL",

                    value =
                        normal.toString(),

                    color =
                        SuccessGreen
                )

                NetworkStat(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "WATCH",

                    value =
                        watch.toString(),

                    color =
                        RiskOrange
                )

                NetworkStat(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "HIGH",

                    value =
                        high.toString(),

                    color =
                        RiskRed
                )
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    "Storage readiness snapshot",

                color =
                    TextMuted,

                fontSize =
                    10.sp
            )

            Spacer(
                modifier =
                    Modifier.height(7.dp)
            )

            StorageDistribution(
                normal =
                    normal,

                watch =
                    watch,

                high =
                    high,

                total =
                    total
            )
        }
    }
}


@Composable
private fun NetworkStat(
    modifier: Modifier,
    label: String,
    value: String,
    color: Color
) {

    Card(
        modifier =
            modifier,

        shape =
            RoundedCornerShape(14.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    color.copy(
                        alpha = 0.08f
                    )
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 9.dp,
                    vertical = 10.dp
                )
        ) {

            Text(
                text =
                    value,

                color =
                    color,

                fontSize =
                    20.sp
            )

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )

            Text(
                text =
                    label,

                color =
                    TextMuted,

                fontSize =
                    8.sp,

                letterSpacing =
                    0.7.sp
            )
        }
    }
}


@Composable
private fun StorageDistribution(
    normal: Int,
    watch: Int,
    high: Int,
    total: Int
) {

    val safeTotal =
        total.coerceAtLeast(1)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
    ) {

        if (normal > 0) {
            Box(
                modifier =
                    Modifier
                        .weight(
                            normal.toFloat()
                        )
                        .fillMaxSize()
                        .background(
                            SuccessGreen
                        )
            )
        }

        if (watch > 0) {
            Box(
                modifier =
                    Modifier
                        .weight(
                            watch.toFloat()
                        )
                        .fillMaxSize()
                        .background(
                            RiskOrange
                        )
            )
        }

        if (high > 0) {
            Box(
                modifier =
                    Modifier
                        .weight(
                            high.toFloat()
                        )
                        .fillMaxSize()
                        .background(
                            RiskRed
                        )
            )
        }
    }
}


/* ============================================================
   WARNING BANNER
   ============================================================ */

@Composable
private fun WarningBanner(
    count: Int
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    RiskRed.copy(
                        alpha = 0.12f
                    )
            )
    ) {

        Row(
            modifier =
                Modifier.padding(15.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector =
                    Icons.Default.Warning,

                contentDescription =
                    null,

                tint =
                    RiskRed,

                modifier =
                    Modifier.size(27.dp)
            )

            Spacer(
                modifier =
                    Modifier.size(11.dp)
            )

            Column {

                Text(
                    text =
                        "OFFICIAL WARNING SIGNAL",

                    color =
                        RiskRed,

                    fontSize =
                        9.sp,

                    letterSpacing =
                        1.0.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )

                Text(
                    text =
                        "$count reservoir record(s) report an official emergency warning.",

                    color =
                        TextPrimary,

                    fontSize =
                        14.sp,

                    lineHeight =
                        20.sp
                )
            }
        }
    }
}


/* ============================================================
   SECTION HEADING
   ============================================================ */

@Composable
private fun SectionHeading(
    eyebrow: String,
    title: String,
    subtitle: String
) {

    Column {

        Text(
            text =
                eyebrow,

            color =
                NeonCyan,

            fontSize =
                9.sp,

            letterSpacing =
                1.2.sp
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                title,

            color =
                TextPrimary,

            fontSize =
                22.sp
        )

        Spacer(
            modifier =
                Modifier.height(3.dp)
        )

        Text(
            text =
                subtitle,

            color =
                TextMuted,

            fontSize =
                11.sp,

            lineHeight =
                17.sp
        )
    }
}


/* ============================================================
   IMPACT DAM CARD
   ============================================================ */

@Composable
private fun ImpactDamCard(
    dam: DamItem
) {

    val storage =
        dam.storage_percent

    val isWarning =
        dam.official_warning == true

    val status =
        when {

            isWarning ->
                "OFFICIAL WARNING"

            storage == null ->
                "UNKNOWN"

            storage >= 80.0 ->
                "HIGH STORAGE"

            storage >= 60.0 ->
                "WATCH"

            else ->
                "NORMAL"
        }

    val statusColor =
        when {

            isWarning ->
                RiskRed

            storage == null ->
                TextSecondary

            storage >= 80.0 ->
                RiskRed

            storage >= 60.0 ->
                RiskOrange

            else ->
                SuccessGreen
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    SurfaceDark
            )
    ) {

        Column(
            modifier =
                Modifier.padding(17.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.Top
            ) {

                Row(
                    modifier =
                        Modifier.weight(1f),

                    verticalAlignment =
                        Alignment.Top
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(
                                    RoundedCornerShape(
                                        13.dp
                                    )
                                )
                                .background(
                                    statusColor.copy(
                                        alpha = 0.12f
                                    )
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(
                            imageVector =
                                if (
                                    isWarning
                                ) {
                                    Icons.Default.Warning
                                } else {
                                    Icons.Default.WaterDrop
                                },

                            contentDescription =
                                null,

                            tint =
                                statusColor,

                            modifier =
                                Modifier.size(22.dp)
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.size(11.dp)
                    )

                    Column {

                        Text(
                            text =
                                dam.name
                                    ?: "Unknown Reservoir",

                            color =
                                TextPrimary,

                            fontSize =
                                17.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                listOfNotNull(
                                    dam.state,
                                    dam.region
                                )
                                    .joinToString(
                                        " • "
                                    ),

                            color =
                                TextMuted,

                            fontSize =
                                10.sp
                        )
                    }
                }

                Text(
                    text =
                        status,

                    color =
                        statusColor,

                    fontSize =
                        8.sp,

                    letterSpacing =
                        0.7.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(17.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.Bottom
            ) {

                Column {

                    Text(
                        text =
                            "STORAGE",

                        color =
                            TextMuted,

                        fontSize =
                            8.sp,

                        letterSpacing =
                            0.9.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            storage
                                ?.let {
                                    String.format(
                                        "%.1f%%",
                                        it
                                    )
                                }
                                ?: "N/A",

                        color =
                            statusColor,

                        fontSize =
                            30.sp
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(
                        text =
                            "CURRENT LEVEL",

                        color =
                            TextMuted,

                        fontSize =
                            8.sp,

                        letterSpacing =
                            0.8.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            dam.current_level_m
                                ?.let {
                                    String.format(
                                        "%.2f m",
                                        it
                                    )
                                }
                                ?: "N/A",

                        color =
                            TextPrimary,

                        fontSize =
                            16.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(9.dp)
            )

            StorageGauge(
                percent =
                    storage,

                color =
                    statusColor
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                DamMiniMetric(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "FRL",

                    value =
                        dam.frl_m
                            ?.let {
                                String.format(
                                    "%.2f m",
                                    it
                                )
                            }
                            ?: "N/A"
                )

                DamMiniMetric(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "LIVE STORAGE",

                    value =
                        dam.live_storage_bcm
                            ?.let {
                                String.format(
                                    "%.3f BCM",
                                    it
                                )
                            }
                            ?: "N/A"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                DamMiniMetric(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "CAPACITY",

                    value =
                        dam.live_capacity_bcm
                            ?.let {
                                String.format(
                                    "%.3f BCM",
                                    it
                                )
                            }
                            ?: "N/A"
                )

                DamMiniMetric(
                    modifier =
                        Modifier.weight(1f),

                    label =
                        "HYDEL",

                    value =
                        dam.hydel_mw
                            ?.let {
                                String.format(
                                    "%.1f MW",
                                    it
                                )
                            }
                            ?: "N/A"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        "Normal ${
                            dam.normal_storage_percent
                                ?.let {
                                    String.format(
                                        "%.1f%%",
                                        it
                                    )
                                }
                                ?: "N/A"
                        }",

                    color =
                        TextMuted,

                    fontSize =
                        9.sp
                )

                Text(
                    text =
                        "Last year ${
                            dam.last_year_storage_percent
                                ?.let {
                                    String.format(
                                        "%.1f%%",
                                        it
                                    )
                                }
                                ?: "N/A"
                        }",

                    color =
                        TextMuted,

                    fontSize =
                        9.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            Text(
                text =
                    "Observed: ${
                        dam.observation_date
                            ?: "Unknown"
                    }",

                color =
                    TextMuted,

                fontSize =
                    8.sp
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "Source: ${
                        dam.source
                            ?: "CWC"
                    } · ${
                        dam.source_type
                            ?: "OFFICIAL_DATA"
                    }",

                color =
                    TextMuted,

                fontSize =
                    8.sp
            )
        }
    }
}


/* ============================================================
   STORAGE GAUGE
   ============================================================ */

@Composable
private fun StorageGauge(
    percent: Double?,
    color: Color
) {

    val fraction =
        (percent ?: 0.0)
            .div(100.0)
            .coerceIn(
                0.0,
                1.0
            )
            .toFloat()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .background(
                    color.copy(
                        alpha = 0.10f
                    )
                )
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth(
                        fraction
                    )
                    .height(
                        8.dp
                    )
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        color
                    )
        )
    }
}


/* ============================================================
   MINI METRIC
   ============================================================ */

@Composable
private fun DamMiniMetric(
    modifier: Modifier,
    label: String,
    value: String
) {

    Card(
        modifier =
            modifier,

        shape =
            RoundedCornerShape(13.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White.copy(
                        alpha = 0.035f
                    )
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                )
        ) {

            Text(
                text =
                    label,

                color =
                    TextMuted,

                fontSize =
                    7.sp,

                letterSpacing =
                    0.7.sp
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text =
                    value,

                color =
                    TextSecondary,

                fontSize =
                    12.sp
            )
        }
    }
}


/* ============================================================
   SOURCE CARD
   ============================================================ */

@Composable
private fun SourceCard(
    response: DamResponse
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    SurfaceDark
            )
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp)
        ) {

            Text(
                text =
                    "DATA PROVENANCE",

                color =
                    NeonCyan,

                fontSize =
                    9.sp,

                letterSpacing =
                    1.0.sp
            )

            Spacer(
                modifier =
                    Modifier.height(7.dp)
            )

            Text(
                text =
                    "Central Water Commission",

                color =
                    TextPrimary,

                fontSize =
                    16.sp
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "Source: ${
                        response.source
                            ?: "CWC"
                    }",

                color =
                    TextSecondary,

                fontSize =
                    11.sp
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text =
                    "Official reservoir storage information. " +
                        "Use the latest official warning for critical decisions.",

                color =
                    TextMuted,

                fontSize =
                    10.sp,

                lineHeight =
                    16.sp
            )
        }
    }
}


/* ============================================================
   STATUS PILL
   ============================================================ */

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {

    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(10.dp)
                )
                .background(
                    color.copy(
                        alpha = 0.10f
                    )
                )
                .padding(
                    horizontal = 9.dp,
                    vertical = 5.dp
                )
    ) {

        Text(
            text =
                text,

            color =
                color,

            fontSize =
                8.sp,

            letterSpacing =
                0.7.sp
        )
    }
}


/* ============================================================
   LOADING
   ============================================================ */

@Composable
private fun LoadingState() {

    Box(
        modifier =
            Modifier.fillMaxSize(),

        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            CircularProgressIndicator(
                color =
                    NeonCyan
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "Loading reservoir intelligence...",

                color =
                    TextMuted,

                fontSize =
                    12.sp
            )
        }
    }
}


/* ============================================================
   ERROR
   ============================================================ */

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {

    Box(
        modifier =
            Modifier.fillMaxSize(),

        contentAlignment =
            Alignment.Center
    ) {

        Column(
            modifier =
                Modifier.padding(24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text =
                    "Reservoir intelligence unavailable",

                color =
                    TextPrimary,

                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    message,

                color =
                    TextMuted,

                fontSize =
                    12.sp
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Button(
                onClick =
                    onRetry
            ) {

                Text(
                    text =
                        "Retry"
                )
            }
        }
    }
}


/* ============================================================
   EMPTY
   ============================================================ */

@Composable
private fun EmptyState() {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(28.dp),

        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector =
                    Icons.Default.WaterDrop,

                contentDescription =
                    null,

                tint =
                    TextMuted,

                modifier =
                    Modifier.size(32.dp)
            )

            Spacer(
                modifier =
                    Modifier.height(9.dp)
            )

            Text(
                text =
                    "No reservoir records available.",

                color =
                    TextMuted,

                fontSize =
                    12.sp
            )
        }
    }
}
