package com.example.weathergpt.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.weathergpt.ui.screens.AlertsScreen
import com.example.weathergpt.ui.screens.CameraScreen
import com.example.weathergpt.ui.screens.ChatScreen
import com.example.weathergpt.ui.screens.DamScreen
import com.example.weathergpt.ui.screens.ForecastScreen
import com.example.weathergpt.ui.screens.HomeScreen
import com.example.weathergpt.ui.screens.MapScreen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoCamera
import com.example.weathergpt.ui.theme.AccentPurple
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.TextMuted
import com.example.weathergpt.ui.theme.TextPrimary
import com.example.weathergpt.ui.theme.TextSecondary

sealed class Screen(
    val route: String,
    val title: String
) {
    data object Home : Screen("home", "Home")
    data object Chat : Screen("chat", "Chat")
    data object Forecast : Screen("forecast", "Forecast")
    data object Camera : Screen("camera", "Sky AI")
    data object Map : Screen("map", "Map")
    data object Alerts : Screen("alerts", "Alerts")
    data object Dams : Screen("dams", "Dams")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val screens = listOf(
        Screen.Home,
        Screen.Chat,
        Screen.Camera,
        Screen.Map,
        Screen.Alerts
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            FloatingGlassHeader(
                onAlertsClick = {
                    if (currentRoute != Screen.Alerts.route) {
                        navController.navigate(Screen.Alerts.route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        },
        bottomBar = {
            FloatingGlassBottomDock(
                screens = screens,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0D1B2E), // Subtle dark blue atmospheric core
                            Color(0xFF081220),
                            BackgroundDark
                        ),
                        radius = 1600f
                    )
                )
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onOpenChat = {
                            navController.navigate(Screen.Chat.route)
                        }
                    )
                }

                composable(Screen.Chat.route) {
                    ChatScreen()
                }

                composable(Screen.Forecast.route) {
                    ForecastScreen()
                }

                composable(Screen.Camera.route) {
                    CameraScreen(
                        onNavigateToRadar = {
                            navController.navigate(Screen.Map.route)
                        }
                    )
                }

                composable(Screen.Map.route) {
                    MapScreen()
                }

                composable(Screen.Alerts.route) {
                    AlertsScreen(
                        onOpenDams = {
                            navController.navigate(Screen.Dams.route)
                        }
                    )
                }

                composable(Screen.Dams.route) {
                    DamScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

/*
 * ================================================================
 * SLIM FLOATING GLASS HEADER (56-64px height)
 * ================================================================
 */

@Composable
private fun FloatingGlassHeader(
    onAlertsClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Logo + Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF4DA3FF),
                                Color(0xFF1E60E2)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "WeatherGPT",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "WeatherGPT",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "AI Weather Intelligence",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Right: [● LIVE] [🔔•] [☰]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // LIVE Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x1F10B981))
                    .border(1.dp, Color(0x3810B981), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(
                        text = "LIVE",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Notification Bell with unread dot
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xB30A1626))
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable { onAlertsClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Alerts & Notifications",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .size(19.dp)
                            .padding(end = 1.dp, top = 1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252))
                    )
                }
            }

            // Menu Button with dropdown
            Box {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xB30A1626))
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(Color(0xF00A1626))
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("🗺️ Radar Map", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Map.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📷 Sky AI Camera", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Camera.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("💧 Dam Telemetry", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Dams.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("⚠️ Weather Alerts", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onAlertsClick()
                        }
                    )
                }
            }
        }
    }
}

/*
 * ================================================================
 * FLOATING GLASS BOTTOM NAVIGATION DOCK — Glassmorphism Pill
 * ================================================================
 */

@Composable
private fun FloatingGlassBottomDock(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    // Outer wrapper: transparent background so the pill visually floats
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // The floating pill container
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xCC0D1B2E), // ~80% dark navy glass
                            Color(0xBF081220)  // ~75% deeper navy
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x40FFFFFF), // subtle top highlight
                            Color(0x14FFFFFF)  // fading bottom edge
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onNavigate(screen.route)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        // Active = filled blue circle; inactive = bare icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) PrimaryBlue else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) navIconFilled(screen) else navIconOutlined(screen),
                                contentDescription = screen.title,
                                tint = if (isSelected) Color.White else Color(0xFF7A8FA6),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = screen.title,
                            color = if (isSelected) Color.White else Color(0xFF4E6070),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}

private fun navIconFilled(screen: Screen): ImageVector {
    return when (screen) {
        Screen.Home -> Icons.Default.Home
        Screen.Chat -> Icons.AutoMirrored.Filled.Chat
        Screen.Forecast -> Icons.Default.Cloud
        Screen.Camera -> Icons.Default.PhotoCamera
        Screen.Map -> Icons.Default.Map
        Screen.Alerts -> Icons.Default.Warning
        Screen.Dams -> Icons.Default.WaterDrop
    }
}

private fun navIconOutlined(screen: Screen): ImageVector {
    return when (screen) {
        Screen.Home -> Icons.Outlined.Home
        Screen.Chat -> Icons.Outlined.Chat
        Screen.Forecast -> Icons.Outlined.Cloud
        Screen.Camera -> Icons.Outlined.PhotoCamera
        Screen.Map -> Icons.Outlined.Map
        Screen.Alerts -> Icons.Outlined.Warning
        Screen.Dams -> Icons.Default.WaterDrop
    }
}
