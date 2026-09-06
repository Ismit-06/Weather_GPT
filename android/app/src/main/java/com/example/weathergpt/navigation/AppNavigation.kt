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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        Screen.Forecast,
        Screen.Camera,
        Screen.Map,
        Screen.Alerts
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            FloatingGlassHeader()
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
private fun FloatingGlassHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xD90A1626), // 85% opacity dark navy glass
                            Color(0xBF0A1626)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = BorderGlass,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(PrimaryBlue, Color(0xFF2563EB))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "WeatherGPT",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "WeatherGPT",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "AI Weather Intelligence",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                // LIVE Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x2636E6A0))
                        .border(1.dp, Color(0x4036E6A0), RoundedCornerShape(16.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(
                            text = "LIVE",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

/*
 * ================================================================
 * FLOATING GLASS BOTTOM NAVIGATION DOCK (28dp radius)
 * ================================================================
 */

@Composable
private fun FloatingGlassBottomDock(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xE60A1626), // 90% opacity dark glass
                            Color(0xCC0A1626)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = BorderGlass,
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onNavigate(screen.route)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0x2E4DA3FF) else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) navIconFilled(screen) else navIconOutlined(screen),
                                contentDescription = screen.title,
                                tint = if (isSelected) SecondaryCyan else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = screen.title,
                            color = if (isSelected) SecondaryCyan else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
