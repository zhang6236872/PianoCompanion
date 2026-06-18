package com.pianocompanion.omr

import android.graphics.Bitmap
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff

/**
 * Optical Music Recognition interface.
 *
 * Phase 1 (current): Camera capture + UI flow.
 * Phase 2 (future): Integrate TFLite model or cloud API for actual recognition.
 */
interface OmrEngine {
    /**
     * Recognize sheet music from a bitmap image.
     * Returns a Score object or null if recognition fails.
     */
    suspend fun recognize(bitmap: Bitmap): OmrResult

    /** Whether this engine is ready (model loaded, etc.) */
    fun isAvailable(): Boolean

    /** Engine name for UI display */
    fun displayName(): String
}

/**
 * Result of OMR recognition.
 */
sealed class OmrResult {
    data class Success(val score: Score) : OmrResult()
    data class PartialSuccess(val score: Score, val warnings: List<String>) : OmrResult()
    data class Error(val message: String) : OmrResult()
    data object Processing : OmrResult()
}

/**
 * Stub implementation that will be replaced with a real OMR engine.
 * Generates a placeholder score so the camera flow can be tested end-to-end.
 */
class StubOmrEngine : OmrEngine {
    override suspend fun recognize(bitmap: Bitmap): OmrResult {
        // Simulate processing delay
        kotlinx.coroutines.delay(1500)

        // Return a placeholder score — real implementation will use
        // TFLite or Audiveris
        return OmrResult.PartialSuccess(
            score = Score(
                id = "omr_${System.currentTimeMillis()}",
                title = "拍照识别的乐谱",
                composer = "OMR",
                notes = listOf(
                    ScoreNote(midiNumber = 60, noteName = "C4", startTime = 0, duration = 500, staff = Staff.TREBLE, measureIndex = 0),
                    ScoreNote(midiNumber = 62, noteName = "D4", startTime = 500, duration = 500, staff = Staff.TREBLE, measureIndex = 0),
                    ScoreNote(midiNumber = 64, noteName = "E4", startTime = 1000, duration = 500, staff = Staff.TREBLE, measureIndex = 0),
                    ScoreNote(midiNumber = 65, noteName = "F4", startTime = 1500, duration = 500, staff = Staff.TREBLE, measureIndex = 0)
                ),
                tempo = 120,
                source = ScoreSource.OMR
            ),
            warnings = listOf("OMR 功能正在开发中，当前为占位结果", "后续将集成 TFLite 模型进行真实识别")
        )
    }

    override fun isAvailable(): Boolean = true
    override fun displayName(): String = "占位识谱引擎"
}

/**
 * Image preprocessing utilities for OMR.
 * Real implementation will use OpenCV or similar for:
 * - Grayscale conversion
 * - Binarization (adaptive threshold)
 * - Staff line detection and removal
 * - Connected component analysis
 */
object ImagePreprocessor {

    /**
     * Convert bitmap to grayscale.
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        output.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return output
    }

    /**
     * Apply simple adaptive thresholding for binarization.
     * threshold = 128, values above become white (255), below become black (0).
     */
    fun binarize(bitmap: Bitmap, threshold: Int = 128): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val gray = ((p shr 16) and 0xFF)
            val value = if (gray > threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            pixels[i] = value
        }

        output.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return output
    }
}
