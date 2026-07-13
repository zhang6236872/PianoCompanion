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
import com.pianocompanion.ui.notation.NoteReadingScreen
import com.pianocompanion.ui.interval.IntervalTrainerScreen
import com.pianocompanion.ui.chordreading.ChordReadingScreen
import com.pianocompanion.ui.keysig.KeySigScreen
import com.pianocompanion.ui.rhythmreading.RhythmReadingScreen
import com.pianocompanion.ui.trainingsummary.TrainingSummaryScreen
import com.pianocompanion.ui.mixedpractice.MixedPracticeScreen
import com.pianocompanion.ui.musicalterms.MusicalTermsScreen
import com.pianocompanion.ui.moderecognition.ModeRecognitionScreen
import com.pianocompanion.ui.chordtraining.ChordTrainingScreen
import com.pianocompanion.ui.rhythmpattern.RhythmPatternScreen
import com.pianocompanion.ui.melodymemory.MelodyMemoryScreen
import com.pianocompanion.ui.intervaltraining.IntervalTrainingScreen
import com.pianocompanion.ui.pitchtraining.PitchTrainingScreen
import com.pianocompanion.ui.cadencetraining.CadenceTrainingScreen
import com.pianocompanion.ui.scaletraining.ScaleTrainingScreen
import com.pianocompanion.ui.inversiontraining.InversionTrainingScreen
import com.pianocompanion.ui.progressiontraining.ProgressionTrainingScreen
import com.pianocompanion.ui.keyidentificationtraining.KeyIdentificationTrainingScreen
import com.pianocompanion.ui.seventhchordtraining.SeventhChordTrainingScreen
import com.pianocompanion.ui.suspendedchordtraining.SuspendedChordTrainingScreen
import com.pianocompanion.ui.ninthchordtraining.NinthChordTrainingScreen
import com.pianocompanion.ui.eleventhchordtraining.EleventhChordTrainingScreen
import com.pianocompanion.ui.thirteenthchordtraining.ThirteenthChordTrainingScreen
import com.pianocompanion.ui.chordfunctiontraining.ChordFunctionTrainingScreen
import com.pianocompanion.ui.nonscaletonetraining.NonScaleToneTrainingScreen
import com.pianocompanion.ui.rhythmdictation.RhythmDictationScreen
import com.pianocompanion.ui.meterrecognition.MeterRecognitionScreen
import com.pianocompanion.ui.tempotraining.TempoTrainingScreen
import com.pianocompanion.ui.timbretraining.TimbreTrainingScreen
import com.pianocompanion.ui.dynamicstraining.DynamicsTrainingScreen
import com.pianocompanion.ui.registertraining.RegisterTrainingScreen

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
    data object NoteReading : Screen("note_reading", "识谱训练", Icons.Filled.MenuBook)
    data object IntervalTrainer : Screen("interval_trainer", "音程训练", Icons.Filled.Straighten)
    data object ChordReading : Screen("chord_reading", "和弦识别", Icons.Filled.Dashboard)
    data object KeySignature : Screen("key_signature", "调号识别", Icons.Filled.Tune)
    data object RhythmReading : Screen("rhythm_reading", "节奏视读", Icons.Filled.ViewAgenda)
    data object TrainingSummary : Screen("training_summary", "训练汇总", Icons.Filled.Insights)
    data object MixedPractice : Screen("mixed_practice", "综合练习", Icons.Filled.School)
    data object MusicalTerms : Screen("musical_terms", "音乐术语", Icons.Filled.MenuBook)
    data object ModeRecognition : Screen("mode_recognition", "调式听辨", Icons.Filled.GraphicEq)
    data object ChordTraining : Screen("chord_training", "和弦听辨", Icons.Filled.Piano)
    data object RhythmPattern : Screen("rhythm_pattern", "节奏型听辨", Icons.Filled.GraphicEq)
    data object MelodyMemory : Screen("melody_memory", "旋律记忆", Icons.Filled.MusicNote)
    data object IntervalTraining : Screen("interval_training", "音程听辨", Icons.Filled.Tune)
    data object PitchTraining : Screen("pitch_training", "绝对音高", Icons.Filled.GraphicEq)
    data object CadenceTraining : Screen("cadence_training", "终止式听辨", Icons.Filled.AccountTree)
    data object ScaleTraining : Screen("scale_training", "音阶听辨", Icons.Filled.Stairs)
    data object InversionTraining : Screen("inversion_training", "和弦转位听辨", Icons.Filled.Layers)
    data object ProgressionTraining : Screen("progression_training", "和弦进行听辨", Icons.Filled.AutoAwesome)
    data object KeyIdentificationTraining : Screen("key_identification_training", "调性辨识", Icons.Filled.MusicNote)
    data object SeventhChordTraining : Screen("seventh_chord_training", "七和弦听辨", Icons.Filled.GraphicEq)
    data object SuspendedChordTraining : Screen("suspended_chord_training", "挂留和弦听辨", Icons.Filled.Waves)
    data object NinthChordTraining : Screen("ninth_chord_training", "九和弦听辨", Icons.Filled.GraphicEq)
    data object EleventhChordTraining : Screen("eleventh_chord_training", "十一和弦听辨", Icons.Filled.GraphicEq)
    data object ThirteenthChordTraining : Screen("thirteenth_chord_training", "十三和弦听辨", Icons.Filled.GraphicEq)
    data object ChordFunctionTraining : Screen("chord_function_training", "和弦功能听辨", Icons.Filled.GraphicEq)
    data object NonScaleToneTraining : Screen("non_scale_tone_training", "调外音听辨", Icons.Filled.GraphicEq)
    data object RhythmDictation : Screen("rhythm_dictation", "节奏听写", Icons.Filled.GraphicEq)
    data object MeterRecognition : Screen("meter_recognition", "拍号听辨", Icons.Filled.Tune)
    data object TempoTraining : Screen("tempo_training", "速度辨识", Icons.Filled.Speed)
    data object TimbreTraining : Screen("timbre_training", "音色辨识", Icons.Filled.MusicNote)
    data object DynamicsTraining : Screen("dynamics_training", "力度辨识", Icons.Filled.VolumeUp)
    data object RegisterTraining : Screen("register_training", "音区辨识", Icons.Filled.Piano)
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
            composable(Screen.NoteReading.route) {
                NoteReadingScreen()
            }
            composable(Screen.IntervalTrainer.route) {
                IntervalTrainerScreen()
            }
            composable(Screen.ChordReading.route) {
                ChordReadingScreen()
            }
            composable(Screen.KeySignature.route) {
                KeySigScreen()
            }
            composable(Screen.RhythmReading.route) {
                RhythmReadingScreen()
            }
            composable(Screen.TrainingSummary.route) {
                TrainingSummaryScreen()
            }
            composable(Screen.MixedPractice.route) {
                MixedPracticeScreen()
            }
            composable(Screen.MusicalTerms.route) {
                MusicalTermsScreen()
            }
            composable(Screen.ModeRecognition.route) {
                ModeRecognitionScreen()
            }
            composable(Screen.ChordTraining.route) {
                ChordTrainingScreen()
            }
            composable(Screen.RhythmPattern.route) {
                RhythmPatternScreen()
            }
            composable(Screen.MelodyMemory.route) {
                MelodyMemoryScreen()
            }
            composable(Screen.IntervalTraining.route) {
                IntervalTrainingScreen()
            }
            composable(Screen.PitchTraining.route) {
                PitchTrainingScreen()
            }
            composable(Screen.CadenceTraining.route) {
                CadenceTrainingScreen()
            }
            composable(Screen.ScaleTraining.route) {
                ScaleTrainingScreen()
            }
            composable(Screen.InversionTraining.route) {
                InversionTrainingScreen()
            }
            composable(Screen.ProgressionTraining.route) {
                ProgressionTrainingScreen()
            }
            composable(Screen.KeyIdentificationTraining.route) {
                KeyIdentificationTrainingScreen()
            }
            composable(Screen.SeventhChordTraining.route) {
                SeventhChordTrainingScreen()
            }
            composable(Screen.SuspendedChordTraining.route) {
                SuspendedChordTrainingScreen()
            }
            composable(Screen.NinthChordTraining.route) {
                NinthChordTrainingScreen()
            }
            composable(Screen.EleventhChordTraining.route) {
                EleventhChordTrainingScreen()
            }
            composable(Screen.ThirteenthChordTraining.route) {
                ThirteenthChordTrainingScreen()
            }
            composable(Screen.ChordFunctionTraining.route) {
                ChordFunctionTrainingScreen()
            }
            composable(Screen.NonScaleToneTraining.route) {
                NonScaleToneTrainingScreen()
            }
            composable(Screen.RhythmDictation.route) {
                RhythmDictationScreen()
            }
            composable(Screen.MeterRecognition.route) {
                MeterRecognitionScreen()
            }
            composable(Screen.TempoTraining.route) {
                TempoTrainingScreen()
            }
            composable(Screen.TimbreTraining.route) {
                TimbreTrainingScreen()
            }
            composable(Screen.DynamicsTraining.route) {
                DynamicsTrainingScreen()
            }
            composable(Screen.RegisterTraining.route) {
                RegisterTrainingScreen()
            }
        }
    }
}
