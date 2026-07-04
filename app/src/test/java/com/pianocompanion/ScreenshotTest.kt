package com.pianocompanion

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paparazzi 截图测试 - 渲染 PianoCompanion 关键页面为 PNG。
 * 截图自动保存到: app/build/reports/paparazzi/debug/images/
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "Theme.PianoCompanion"
    )

    companion object {
        val LightColors = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF7D5260),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFD8E4),
            onTertiaryContainer = Color(0xFF31111D),
            background = Color(0xFFFEF7FF),
            onBackground = Color(0xFF1D1B20),
            surface = Color(0xFFFEF7FF),
            onSurface = Color(0xFF1D1B20),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E),
            error = Color(0xFFB3261E),
            onError = Color.White,
        )

        val AppShapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp),
        )
    }

    @Test
    fun renderLibraryScreen() {
        paparazzi.snapshot("library") {
            MaterialTheme(colorScheme = LightColors, shapes = AppShapes) {
                LibraryScreenPreviewContent()
            }
        }
    }

    @Test
    fun renderCircleOfFifths() {
        paparazzi.snapshot("circle_of_fifths") {
            MaterialTheme(colorScheme = LightColors, shapes = AppShapes) {
                CircleOfFifthsPreviewContent()
            }
        }
    }

    @Test
    fun renderNoteReadingTrainer() {
        paparazzi.snapshot("note_reading") {
            MaterialTheme(colorScheme = LightColors, shapes = AppShapes) {
                NoteReadingPreviewContent()
            }
        }
    }

    @Test
    fun renderIntervalTrainer() {
        paparazzi.snapshot("interval_trainer") {
            MaterialTheme(colorScheme = LightColors, shapes = AppShapes) {
                IntervalTrainerPreviewContent()
            }
        }
    }

    @Test
    fun renderChordReadingTrainer() {
        paparazzi.snapshot("chord_reading") {
            MaterialTheme(colorScheme = LightColors, shapes = AppShapes) {
                ChordReadingPreviewContent()
            }
        }
    }

    @Test
    fun renderTrainingSummary() {
        paparazzi.snapshot("training_summary") {
            MaterialTheme(colorScheme = LightColors, shapes = AppShapes) {
                TrainingSummaryPreviewContent()
            }
        }
    }
}
