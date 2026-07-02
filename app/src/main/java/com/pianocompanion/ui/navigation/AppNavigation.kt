package com.pianocompanion.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.pianocompanion.ui.library.LibraryScreen
import com.pianocompanion.ui.metronome.MetronomeScreen
import com.pianocompanion.ui.practice.PracticeScreen
import com.pianocompanion.ui.settings.SettingsScreen
import com.pianocompanion.ui.omr.OmrScreen
import com.pianocompanion.ui.splash.SplashScreen
import com.pianocompanion.ui.stats.StatsScreen
import com.pianocompanion.ui.rhythm.RhythmScreen
import com.pianocompanion.ui.training.EarTrainingScreen
import com.pianocompanion.ui.chord.ChordDictionaryScreen
import com.pianocompanion.ui.scale.ScaleLibraryScreen
import com.pianocompanion.ui.progression.ChordProgressionScreen
import com.pianocompanion.ui.circle.CircleOfFifthsScreen
import com.pianocompanion.ui.cadence.CadenceLibraryScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Library : Screen("library", "乐谱", Icons.Filled.LibraryMusic)
    data object Practice : Screen("practice", "练习", Icons.Filled.Piano)
    data object Metronome : Screen("metronome", "节拍器", Icons.Filled.Timer)
    data object Stats : Screen("stats", "统计", Icons.Filled.BarChart)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
    data object Omr : Screen("omr", "拍照识谱", Icons.Filled.PhotoCamera)
    data object EarTraining : Screen("ear_training", "听音训练", Icons.Filled.Hearing)
    data object RhythmTraining : Screen("rhythm_training", "节奏训练", Icons.Filled.GraphicEq)
    data object ChordDictionary : Screen("chord_dictionary", "和弦词典", Icons.Filled.Piano)
    data object ScaleLibrary : Screen("scale_library", "音阶词典", Icons.Filled.MusicNote)
    data object ChordProgression : Screen("chord_progression", "和弦进行", Icons.Filled.QueueMusic)
    data object CircleOfFifths : Screen("circle_of_fifths", "五度圈", Icons.Filled.AllInclusive)
    data object CadenceLibrary : Screen("cadence_library", "终止式", Icons.Filled.Flag)
}

private val screens = listOf(
    Screen.Library, Screen.Practice, Screen.Metronome, Screen.Stats, Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onNavigate = { showSplash = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true
                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    // Show dot on practice when practicing
                                    // (placeholder for future notifications)
                                }
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    modifier = if (selected) Modifier.size(26.dp) else Modifier.size(22.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                screen.title,
                                fontSize = if (selected) 12.sp else 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(200)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(200)
                )
            }
        ) {
            composable(Screen.Library.route) { LibraryScreen(navController) }
            composable(Screen.Practice.route) { PracticeScreen() }
            composable(Screen.Metronome.route) { MetronomeScreen() }
            composable(Screen.Stats.route) { StatsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Omr.route) {
                OmrScreen(
                    onScoreRecognized = { score ->
                        navController.navigate(Screen.Practice.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.EarTraining.route) {
                EarTrainingScreen()
            }
            composable(Screen.RhythmTraining.route) {
                RhythmScreen()
            }
            composable(Screen.ChordDictionary.route) {
                ChordDictionaryScreen()
            }
            composable(Screen.ScaleLibrary.route) {
                ScaleLibraryScreen()
            }
            composable(Screen.ChordProgression.route) {
                ChordProgressionScreen()
            }
            composable(Screen.CircleOfFifths.route) {
                CircleOfFifthsScreen()
            }
            composable(Screen.CadenceLibrary.route) {
                CadenceLibraryScreen()
            }
        }
    }
}
