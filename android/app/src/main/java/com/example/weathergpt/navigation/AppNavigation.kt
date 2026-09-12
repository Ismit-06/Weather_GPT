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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.example.weathergpt.ui.screens.ForecastScreen
import com.example.weathergpt.ui.screens.HomeScreen
import com.example.weathergpt.ui.screens.MapScreen
import com.example.weathergpt.R
import com.example.weathergpt.ui.theme.BackgroundDark
import com.example.weathergpt.ui.theme.BorderGlass
import com.example.weathergpt.ui.theme.PrimaryBlue
import com.example.weathergpt.ui.theme.SecondaryCyan
import com.example.weathergpt.ui.theme.SuccessGreen
import com.example.weathergpt.ui.theme.SurfaceDark
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

    val shouldNavigateToMap by com.example.weathergpt.data.SharedFriendStore.navigateToMapTrigger.collectAsState()
    androidx.compose.runtime.LaunchedEffect(shouldNavigateToMap) {
        if (shouldNavigateToMap) {
            if (currentRoute != Screen.Map.route) {
                navController.navigate(Screen.Map.route) {
                    popUpTo(Screen.Home.route) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            com.example.weathergpt.data.SharedFriendStore.resetNavigationTrigger()
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            QuietSkyHeader(
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
            QuietSkyBottomNavigation(
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
                .background(BackgroundDark)
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
                    AlertsScreen()
                }
            }
        }
    }
}

/*
 * ================================================================
 * CLEAN QUIET SKY HEADER (Editorial, Minimal, Professional)
 * ================================================================
 */
@Composable
private fun QuietSkyHeader(
    onAlertsClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Clean Icon Badge + Refined Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "WeatherGPT Logo",
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
            )

            Column {
                Text(
                    text = "WeatherGPT",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.1.sp
                )
                Text(
                    text = "Weather Intelligence",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Right: [LIVE] [🔔]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Minimal Live indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuccessGreen.copy(alpha = 0.12f))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
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
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // Notification Bell
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable { onAlertsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Alerts & Notifications",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/*
 * ================================================================
 * CLEAN QUIET SKY BOTTOM NAVIGATION DOCK
 * ================================================================
 */
@Composable
private fun QuietSkyBottomNavigation(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = BorderGlass,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) PrimaryBlue else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) navIconFilled(screen) else navIconOutlined(screen),
                                contentDescription = screen.title,
                                tint = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = screen.title,
                            color = if (isSelected) PrimaryBlue else TextMuted,
                            fontSize = 9.5.sp,
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
    }
}

private fun navIconOutlined(screen: Screen): ImageVector {
    return when (screen) {
        Screen.Home -> Icons.Outlined.Home
        Screen.Chat -> Icons.AutoMirrored.Outlined.Chat
        Screen.Forecast -> Icons.Outlined.Cloud
        Screen.Camera -> Icons.Outlined.PhotoCamera
        Screen.Map -> Icons.Outlined.Map
        Screen.Alerts -> Icons.Outlined.Warning
    }
}
