package com.pianocompanion.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pianocompanion.ui.library.LibraryScreen
import com.pianocompanion.ui.practice.PracticeScreen
import com.pianocompanion.ui.settings.SettingsScreen
import com.pianocompanion.ui.stats.StatsScreen
import com.pianocompanion.ui.metronome.MetronomeScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Library : Screen("library", "乐谱库", Icons.Filled.LibraryMusic)
    data object Practice : Screen("practice", "练习", Icons.Filled.MusicNote)
    data object Metronome : Screen("metronome", "节拍器", Icons.Filled.Timer)
    data object Stats : Screen("stats", "统计", Icons.Filled.BarChart)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Library,
        Screen.Practice,
        Screen.Metronome,
        Screen.Stats,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) { LibraryScreen(navController) }
            composable(Screen.Practice.route) { PracticeScreen() }
            composable(Screen.Metronome.route) { MetronomeScreen() }
            composable(Screen.Stats.route) { StatsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
