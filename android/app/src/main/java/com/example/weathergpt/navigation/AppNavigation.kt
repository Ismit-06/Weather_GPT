package com.example.weathergpt.navigation

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.weathergpt.ui.screens.AlertsScreen
import com.example.weathergpt.ui.screens.ChatScreen
import com.example.weathergpt.ui.screens.DamScreen
import com.example.weathergpt.ui.screens.ForecastScreen
import com.example.weathergpt.ui.screens.HomeScreen
import com.example.weathergpt.ui.screens.MapScreen

sealed class Screen(
    val route: String,
    val title: String
) {

    data object Home : Screen(
        route = "home",
        title = "Home"
    )

    data object Chat : Screen(
        route = "chat",
        title = "Chat"
    )

    data object Forecast : Screen(
        route = "forecast",
        title = "Forecast"
    )

    data object Map : Screen(
        route = "map",
        title = "Map"
    )

    data object Alerts : Screen(
        route = "alerts",
        title = "Alerts"
    )

    data object Dams : Screen(
        route = "dams",
        title = "Dams"
    )
}


@Composable
fun AppNavigation(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {

    val navController =
        rememberNavController()

    val screens =
        listOf(
            Screen.Home,
            Screen.Chat,
            Screen.Forecast,
            Screen.Map,
            Screen.Alerts
        )

    val backStackEntry by
        navController
            .currentBackStackEntryAsState()

    val currentRoute =
        backStackEntry
            ?.destination
            ?.route

    Scaffold(

        containerColor =
            MaterialTheme.colorScheme.background,

        topBar = {

            ModernHeader(
                darkMode = darkMode,
                onToggleDarkMode = onToggleDarkMode
            )
        },

        bottomBar = {

            ModernBottomBar(
                screens = screens,
                currentRoute = currentRoute,
                onNavigate = { route ->

                    if (currentRoute != route) {

                        navController.navigate(route) {

                            popUpTo(
                                Screen.Home.route
                            ) {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme
                                    .colorScheme
                                    .background,

                                MaterialTheme
                                    .colorScheme
                                    .surface,

                                MaterialTheme
                                    .colorScheme
                                    .background
                            )
                        )
                    )
        ) {

            NavHost(

                navController =
                    navController,

                startDestination =
                    Screen.Home.route,

                modifier =
                    Modifier.fillMaxSize()
            ) {

                composable(
                    Screen.Home.route
                ) {

                    HomeScreen(
                        onOpenChat = {

                            navController.navigate(
                                Screen.Chat.route
                            )
                        }
                    )
                }


                composable(
                    Screen.Chat.route
                ) {

                    ChatScreen()
                }


                composable(
                    Screen.Forecast.route
                ) {

                    ForecastScreen()
                }


                composable(
                    Screen.Map.route
                ) {

                    MapScreen()
                }


                composable(
                    Screen.Alerts.route
                ) {

                    AlertsScreen(
                        onOpenDams = {

                            navController.navigate(
                                Screen.Dams.route
                            )
                        }
                    )
                }


                composable(
                    Screen.Dams.route
                ) {

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
 * MODERN HEADER
 * ================================================================
 */

@Composable
private fun ModernHeader(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    Surface(
        color = Color(0xFF080C14),
        shadowElevation = 0.dp,
        modifier = Modifier.statusBarsPadding()
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 8.dp
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
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(
                                    RoundedCornerShape(12.dp)
                                )
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF388BFF),
                                            Color(0xFF2563EB)
                                        )
                                    )
                                ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Cloud,
                            contentDescription = null,
                            tint =
                                Color.White,
                            modifier =
                                Modifier.size(22.dp)
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.size(10.dp)
                    )

                    Column {
                        Text(
                            text = "WeatherGPT",
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Text(
                            text = "Weather intelligence",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Surface(
                        shape =
                            RoundedCornerShape(16.dp),
                        color =
                            Color(0xFF132036)
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(
                                    horizontal = 9.dp,
                                    vertical = 5.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .clip(
                                            androidx.compose.foundation
                                                .shape
                                                .CircleShape
                                        )
                                        .background(
                                            Color(0xFF22C55E)
                                        )
                            )

                            Spacer(
                                modifier =
                                    Modifier.size(5.dp)
                            )

                            Text(
                                text = "LIVE",
                                color = Color(0xFF22C55E),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.size(4.dp)
                    )

                    IconButton(
                        onClick =
                            onToggleDarkMode
                    ) {
                        Icon(
                            imageVector =
                                if (darkMode) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                            contentDescription =
                                if (darkMode) {
                                    "Light mode"
                                } else {
                                    "Dark mode"
                                },
                            tint =
                                Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}


/*
 * ================================================================
 * MODERN BOTTOM NAVIGATION
 * ================================================================
 */

@Composable
private fun ModernBottomBar(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = Color(0xFF090E1A),
        shadowElevation = 8.dp,
        modifier = Modifier
            .background(Color(0xFF090E1A))
            .navigationBarsPadding()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF172033))
            )
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier =
                    Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 2.dp
                    )
            ) {
                screens.forEach { screen ->
                    val selected =
                        currentRoute == screen.route

                    NavigationBarItem(
                        selected =
                            selected,
                        onClick = {
                            onNavigate(
                                screen.route
                            )
                        },
                        icon = {
                            Icon(
                                imageVector =
                                    navigationIcon(
                                        screen
                                    ),
                                contentDescription =
                                    screen.title,
                                modifier =
                                    Modifier.size(
                                        22.dp
                                    )
                            )
                        },
                        label = {
                            Text(
                                text =
                                    screen.title,
                                fontSize =
                                    10.sp,
                                fontWeight =
                                    if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                    else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor =
                                    Color(0xFF388BFF),
                                selectedTextColor =
                                    Color(0xFF388BFF),
                                unselectedIconColor =
                                    Color(0xFF64748B),
                                unselectedTextColor =
                                    Color(0xFF64748B),
                                indicatorColor =
                                    Color.Transparent
                            )
                    )
                }
            }
        }
    }
}


private fun navigationIcon(
    screen: Screen
): androidx.compose.ui.graphics.vector.ImageVector {

    return when (screen) {

        Screen.Home ->
            Icons.Default.Home

        Screen.Chat ->
            Icons.AutoMirrored.Filled.Chat

        Screen.Forecast ->
            Icons.Default.Cloud

        Screen.Map ->
            Icons.Default.Map

        Screen.Alerts ->
            Icons.Default.Warning

        Screen.Dams ->
            Icons.Default.WaterDrop
    }
}
