package com.pianocompanion.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.CompareArrows
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
import com.pianocompanion.ui.ornamenttraining.OrnamentTrainingScreen
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
import com.pianocompanion.ui.melodicdirectiontraining.MelodicDirectionTrainingScreen
import com.pianocompanion.ui.harmonicintervaltraining.HarmonicIntervalTrainingScreen
import com.pianocompanion.ui.texturerecognitiontraining.TextureRecognitionTrainingScreen
import com.pianocompanion.ui.articulationtraining.ArticulationTrainingScreen
import com.pianocompanion.ui.polyrhythmtraining.PolyrhythmTrainingScreen
import com.pianocompanion.ui.contrapuntalmotiontraining.ContrapuntalMotionTrainingScreen
import com.pianocompanion.ui.modulationrecognition.ModulationRecognitionTrainingScreen
import com.pianocompanion.ui.consonancetraining.ConsonanceTrainingScreen
import com.pianocompanion.ui.nonchordtonetraining.NonChordToneTrainingScreen
import com.pianocompanion.ui.sequencetraining.SequenceTrainingScreen
import com.pianocompanion.ui.scaledegreetraining.ScaleDegreeTrainingScreen
import com.pianocompanion.ui.dynamicsdirectiontraining.DynamicsDirectionTrainingScreen
import com.pianocompanion.ui.accentrecognition.AccentRecognitionTrainingScreen
import com.pianocompanion.ui.voicecounttraining.VoiceCountTrainingScreen
import com.pianocompanion.ui.subdivisionrecognition.SubdivisionRecognitionTrainingScreen
import com.pianocompanion.ui.melodiccontour.MelodicContourTrainingScreen
import com.pianocompanion.ui.swingfeel.SwingFeelTrainingScreen
import com.pianocompanion.ui.tempochangetraining.TempoChangeTrainingScreen
import com.pianocompanion.ui.harmonycolor.HarmonyColorTrainingScreen
import com.pianocompanion.ui.polyphonicmotion.PolyphonicMotionTrainingScreen
import com.pianocompanion.ui.timbrebrightness.TimbreBrightnessTrainingScreen
import com.pianocompanion.ui.motiftransformation.MotifTransformationTrainingScreen
import com.pianocompanion.ui.voiceentryorder.VoiceEntryTrainingScreen
import com.pianocompanion.ui.harmonicseries.HarmonicSeriesTrainingScreen
import com.pianocompanion.ui.modescale.ModeScaleTrainingScreen
import com.pianocompanion.ui.compoundmeter.CompoundMeterTrainingScreen
import com.pianocompanion.ui.chordinversion.ChordInversionTrainingScreen
import com.pianocompanion.ui.texturerecognition.TextureTypeRecognitionTrainingScreen
import com.pianocompanion.ui.rhythmmemory.RhythmPatternMemoryTrainingScreen

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
    data object OrnamentTraining : Screen("ornament_training", "装饰音辨识", Icons.Filled.Star)
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
    data object MelodicDirection : Screen("melodic_direction", "旋律方向", Icons.Filled.TrendingUp)
    data object HarmonicInterval : Screen("harmonic_interval", "和声音程", Icons.Filled.ShowChart)
    data object TextureRecognition : Screen("texture_recognition", "织体辨识", Icons.Filled.Layers)
    data object ArticulationTraining : Screen("articulation_training", "演奏法辨识", Icons.Filled.GraphicEq)
    data object PolyrhythmTraining : Screen("polyrhythm_training", "复合节奏辨识", Icons.Filled.GraphicEq)
    data object ModulationRecognition : Screen("modulation_recognition", "转调辨识", Icons.Filled.Cached)
    data object ContrapuntalMotion : Screen("contrapuntal_motion", "声部运动辨识", Icons.AutoMirrored.Filled.CompareArrows)
    data object ConsonanceTraining : Screen("consonance_training", "协和度辨识", Icons.Filled.GraphicEq)
    data object NonChordToneTraining : Screen("non_chord_tone_training", "和弦外音辨识", Icons.Filled.GraphicEq)
    data object SequenceRecognitionTraining : Screen("sequence_recognition_training", "模进辨识", Icons.Filled.Repeat)
    data object ScaleDegreeTraining : Screen("scale_degree_training", "调内音级", Icons.Filled.Tune)
    data object DynamicsDirectionTraining : Screen("dynamics_direction_training", "力度变化方向", Icons.Filled.TrendingUp)
    data object AccentRecognitionTraining : Screen("accent_recognition", "强拍辨识", Icons.Filled.GraphicEq)
    data object VoiceCountTraining : Screen("voice_count_recognition", "声部数量", Icons.Filled.LibraryMusic)
    data object SubdivisionRecognitionTraining : Screen("subdivision_recognition", "节奏细分", Icons.Filled.ViewWeek)
    data object MelodicContourTraining : Screen("melodic_contour_training", "旋律轮廓辨识", Icons.Filled.ShowChart)
    data object SwingFeelTraining : Screen("swing_feel_training", "摇摆感辨识", Icons.Filled.Waves)
    data object TempoChangeDirectionTraining : Screen("tempo_change_direction_training", "速度变化方向", Icons.Filled.Speed)
    data object HarmonyColorTraining : Screen("harmony_color_training", "和声色彩", Icons.Filled.Palette)
    data object PolyphonicMotionTraining : Screen("polyphonic_motion_training", "复调运动辨识", Icons.Filled.SwapHoriz)
    data object TimbreBrightnessTraining : Screen("timbre_brightness_training", "音色亮度辨识", Icons.Filled.Tune)
    data object MotifTransformationTraining : Screen("motif_transformation_training", "动机发展辨识", Icons.Filled.Loop)
    data object VoiceEntryOrderTraining : Screen("voice_entry_order_training", "声部进入顺序辨识", Icons.Filled.Queue)
    data object HarmonicSeriesTraining : Screen("harmonic_series_training", "泛音列辨识", Icons.Filled.Waves)
    data object ModeScaleTraining : Screen("mode_scale_training", "调式色彩对比", Icons.Filled.Palette)
    data object CompoundMeterTraining : Screen("compound_meter_training", "复合节拍听辨", Icons.Filled.MusicNote)
    data object ChordInversionTraining : Screen("chord_inversion_training", "和弦转位听辨", Icons.Filled.Piano)
    data object TextureTypeRecognitionTraining : Screen("texture_type_recognition_training", "织体类型辨识", Icons.Filled.Layers)
    data object RhythmPatternMemoryTraining : Screen("rhythm_pattern_memory_training", "节奏型记忆", Icons.Filled.Equalizer)
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
            composable(Screen.OrnamentTraining.route) {
                OrnamentTrainingScreen()
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
            composable(Screen.MelodicDirection.route) {
                MelodicDirectionTrainingScreen()
            }
            composable(Screen.HarmonicInterval.route) {
                HarmonicIntervalTrainingScreen()
            }
            composable(Screen.TextureRecognition.route) {
                TextureRecognitionTrainingScreen()
            }
            composable(Screen.ArticulationTraining.route) {
                ArticulationTrainingScreen()
            }
            composable(Screen.PolyrhythmTraining.route) {
                PolyrhythmTrainingScreen()
            }
            composable(Screen.ContrapuntalMotion.route) {
                ContrapuntalMotionTrainingScreen()
            }
            composable(Screen.ModulationRecognition.route) {
                ModulationRecognitionTrainingScreen()
            }
            composable(Screen.ConsonanceTraining.route) {
                ConsonanceTrainingScreen()
            }
            composable(Screen.NonChordToneTraining.route) {
                NonChordToneTrainingScreen()
            }
            composable(Screen.SequenceRecognitionTraining.route) {
                SequenceTrainingScreen()
            }
            composable(Screen.ScaleDegreeTraining.route) {
                ScaleDegreeTrainingScreen()
            }
            composable(Screen.DynamicsDirectionTraining.route) {
                DynamicsDirectionTrainingScreen()
            }
            composable(Screen.AccentRecognitionTraining.route) {
                AccentRecognitionTrainingScreen()
            }
            composable(Screen.VoiceCountTraining.route) {
                VoiceCountTrainingScreen()
            }
            composable(Screen.SubdivisionRecognitionTraining.route) {
                SubdivisionRecognitionTrainingScreen()
            }
            composable(Screen.MelodicContourTraining.route) {
                MelodicContourTrainingScreen()
            }
            composable(Screen.SwingFeelTraining.route) {
                SwingFeelTrainingScreen()
            }
            composable(Screen.TempoChangeDirectionTraining.route) {
                TempoChangeTrainingScreen()
            }
            composable(Screen.HarmonyColorTraining.route) {
                HarmonyColorTrainingScreen()
            }
            composable(Screen.PolyphonicMotionTraining.route) {
                PolyphonicMotionTrainingScreen()
            }
            composable(Screen.TimbreBrightnessTraining.route) {
                TimbreBrightnessTrainingScreen()
            }
            composable(Screen.MotifTransformationTraining.route) {
                MotifTransformationTrainingScreen()
            }
            composable(Screen.VoiceEntryOrderTraining.route) {
                VoiceEntryTrainingScreen()
            }
            composable(Screen.HarmonicSeriesTraining.route) {
                HarmonicSeriesTrainingScreen()
            }
            composable(Screen.ModeScaleTraining.route) {
                ModeScaleTrainingScreen()
            }
            composable(Screen.CompoundMeterTraining.route) {
                CompoundMeterTrainingScreen()
            }
            composable(Screen.ChordInversionTraining.route) {
                ChordInversionTrainingScreen()
            }
            composable(Screen.TextureTypeRecognitionTraining.route) {
                TextureTypeRecognitionTrainingScreen()
            }
            composable(Screen.RhythmPatternMemoryTraining.route) {
                RhythmPatternMemoryTrainingScreen()
            }
        }
    }
}
