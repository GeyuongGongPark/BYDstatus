package com.ggpark.bydstats.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.ggpark.bydstats.android.ui.battery.BatteryHistoryScreen
import com.ggpark.bydstats.android.ui.charging.ChargingSessionsScreen
import com.ggpark.bydstats.android.ui.dashboard.DashboardScreen
import com.ggpark.bydstats.android.ui.driving.DrivingSessionsScreen
import com.ggpark.bydstats.android.ui.log.LogScreen
import com.ggpark.bydstats.android.ui.settings.SettingsScreen
import com.ggpark.bydstats.android.viewmodel.AppViewModel

private sealed class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    object Dashboard : Tab("dashboard", "대시보드", Icons.Default.Home)
    object Battery   : Tab("battery",   "배터리",   Icons.Default.BatteryFull)
    object Charging  : Tab("charging",  "충전",     Icons.Default.BatteryChargingFull)
    object Driving   : Tab("driving",   "주행",     Icons.Default.DirectionsCar)
    object Settings  : Tab("settings",  "설정",     Icons.Default.Settings)
}

private val TABS = listOf(Tab.Dashboard, Tab.Battery, Tab.Charging, Tab.Driving, Tab.Settings)

@Composable
fun MainNavHost(vm: AppViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = Tab.Dashboard.route
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStack by navController.currentBackStackEntryAsState()
                val currentDest = navBackStack?.destination
                TABS.forEach { tab ->
                    NavigationBarItem(
                        icon    = { Icon(tab.icon, contentDescription = tab.label) },
                        label   = { Text(tab.label) },
                        selected = currentDest?.hierarchy?.any { it.route == tab.route } == true,
                        onClick  = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Dashboard.route) { DashboardScreen(vm) }
            composable(Tab.Battery.route)   { BatteryHistoryScreen(vm) }
            composable(Tab.Charging.route)  { ChargingSessionsScreen(vm) }
            composable(Tab.Driving.route)   { DrivingSessionsScreen(vm) }
            composable(Tab.Settings.route)  { SettingsScreen(vm, onNavigateToLog = { navController.navigate("log") }) }
            composable("log")               { LogScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
