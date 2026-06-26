package com.pianocompanion.omr

import android.graphics.Bitmap
import com.pianocompanion.omr.image.AdaptiveBinarizer
import com.pianocompanion.omr.image.BinaryImage

/**
 * Real optical-music-recognition engine backed by [OmrPipeline].
 *
 * It performs genuine pixel-level analysis of the photographed score:
 * grayscale → **adaptive (local) Otsu binarization** → **deskew (rotation
 * correction)** → **denoise (pepper/salt removal)** → **keystone (perspective /
 * yaw correction)** → staff detection/removal → notehead localization → pitch
 * mapping → score assembly. No external model file is required, so it works
 * fully on-device and offline.
 *
 * Adaptive binarization divides the image into tiles, computes a local Otsu
 * threshold per tile and bilinearly interpolates it across pixels, which keeps
 * the pipeline robust to uneven lighting (shadows, vignetting, glare) that
 * defeats a single global threshold.
 *
 * The deskew step automatically corrects tilted photos (up to ±12°) by
 * maximizing the horizontal-projection peakiness, making the pipeline robust
 * to handheld photography without requiring a perfectly level shot.
 *
 * The denoise step removes isolated black specks (sensor/JPEG noise, binarizer
 * tile-boundary artefacts) and fills white holes inside solid strokes, which
 * keeps the horizontal projection clean and preserves notehead fill ratios used
 * by the rhythm analyser.
 *
 * The keystone step corrects perspective (yaw) distortion that deskew cannot:
 * when the score is photographed at an angle the staff lines converge and the
 * system height differs between the left and right edges, corrupting the
 * Y→pitch mapping. It measures that convergence and applies a per-column
 * vertical remap to restore uniform line spacing, so notehead pitches map
 * consistently across the whole width.
 *
 * Limitations (documented for the user via warnings):
 *  - note durations are estimated via stem/beam/flag/dot/rest analysis;
 *  - complex real-world scores may still require manual proofreading.
 */
class RealOmrEngine : OmrEngine {

    override fun isAvailable(): Boolean = true

    override fun displayName(): String = "真实图像识谱引擎（本地算法）"

    override suspend fun recognize(bitmap: Bitmap): OmrResult {
        return try {
            val scaled = downscale(bitmap, maxDim = 1600)
            val gray = toGrayscaleArray(scaled)
            val binary = AdaptiveBinarizer.binarize(scaled.width, scaled.height, gray)

            val result = OmrPipeline.recognize(binary)
            when {
                result.isEmpty -> OmrResult.Error(result.warnings.firstOrNull() ?: "未能识别出音符")
                result.warnings.isEmpty() -> OmrResult.Success(result.score, result.quality)
                else -> OmrResult.PartialSuccess(result.score, result.warnings, result.quality)
            }
        } catch (e: Exception) {
            OmrResult.Error("识谱失败: ${e.message ?: "未知错误"}")
        }
    }

    /** Convert an ARGB bitmap to a row-major grayscale (luminance) array. */
    private fun toGrayscaleArray(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
        }
        return gray
    }

    /** Downscale so the longest side is at most [maxDim] (keeps processing fast). */
    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
