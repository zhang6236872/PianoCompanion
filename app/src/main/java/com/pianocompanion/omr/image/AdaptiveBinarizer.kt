package com.pianocompanion.omr.image

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Local (adaptive) binarization robust to uneven lighting.
 *
 * The global [OtsuThresholder] picks a *single* threshold for the whole image.
 * That works for evenly-lit scans but fails for real photographs, which almost
 * always have a smooth illumination gradient — a phone shadow on one corner,
 * vignetting from the lens, or bright glare on another part of the page. A
 * global threshold that is correct for the bright region makes the dark region
 * collapse to solid black, and vice-versa, destroying the OMR pipeline before
 * it even starts.
 *
 * This binarizer divides the image into a grid of [tileSize]×[tileSize] tiles,
 * computes a local Otsu threshold for every tile, and then **bilinearly
 * interpolates** the per-tile thresholds to each pixel. The interpolated
 * threshold surface follows the slow lighting gradient while staying smooth,
 * so there are no hard tile-boundary seams.
 *
 * Tiles that contain essentially no contrast (a uniform patch of paper or a
 * solid block of ink) would produce a meaningless Otsu threshold, so they fall
 * back to the global threshold, which acts as a sensible default for those
 * regions.
 *
 * Pure Kotlin with **no Android dependency**, so the whole behavior is
 * unit-testable with synthetic grayscale images.
 */
object AdaptiveBinarizer {

    /** Default tile side length in pixels (≈ a few staff spaces in a typical photo). */
    const val DEFAULT_TILE = 40

    /**
     * A tile whose gray values span fewer than [MIN_RANGE] levels is treated as
     * contrast-free (uniform background/ink) and falls back to the global
     * threshold. Ink-vs-paper almost always differs by 40+ levels, so this only
     * rejects truly flat regions.
     */
    private const val MIN_RANGE = 8

    /**
     * Binarize a grayscale image using per-tile Otsu thresholds with bilinear
     * interpolation. Pixels whose luminance is **<= interpolated threshold**
     * become black, matching the inclusive boundary used by [OtsuThresholder]
     * and [BinaryImage.fromGrayscale].
     *
     * @param width  image width in pixels
     * @param height image height in pixels
     * @param gray   row-major luminance array (0..255), length == width*height
     * @param tileSize side length of each analysis tile; degenerate images
     *                 (smaller than one tile) fall back to a single global Otsu.
     * @return a [BinaryImage] where `true` = black (ink).
     */
    fun binarize(
        width: Int,
        height: Int,
        gray: IntArray,
        tileSize: Int = DEFAULT_TILE
    ): BinaryImage {
        require(width > 0 && height > 0) { "width and height must be > 0" }
        require(gray.size == width * height) { "gray size does not match width*height" }
        require(tileSize > 0) { "tileSize must be > 0" }

        val globalThreshold = OtsuThresholder.threshold(gray)

        // Degenerate case: the whole image fits in a single tile → plain global Otsu.
        if (width <= tileSize && height <= tileSize) {
            return BinaryImage.fromGrayscale(width, height, gray, globalThreshold)
        }

        val cols = ceil(width.toDouble() / tileSize).toInt().coerceAtLeast(1)
        val rows = ceil(height.toDouble() / tileSize).toInt().coerceAtLeast(1)

        // Per-tile threshold surface (already filled with global for invalid tiles).
        val thresholdGrid = Array(rows) { DoubleArray(cols) { globalThreshold.toDouble() } }

        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val x0 = i * tileSize
                val y0 = j * tileSize
                val x1 = minOf(x0 + tileSize, width)
                val y1 = minOf(y0 + tileSize, height)
                val tileThreshold = localThreshold(gray, width, x0, y0, x1, y1, globalThreshold)
                thresholdGrid[j][i] = tileThreshold
            }
        }

        // Bilinearly interpolate the threshold to every pixel.
        val pixels = BooleanArray(width * height)
        for (y in 0 until height) {
            val gyGrid = y / tileSize.toDouble() - 0.5
            val j0 = floor(gyGrid).toInt().coerceIn(0, rows - 1)
            val j1 = (j0 + 1).coerceIn(0, rows - 1)
            val fy = (gyGrid - j0).coerceIn(0.0, 1.0)
            val rowBase = y * width
            for (x in 0 until width) {
                val gxGrid = x / tileSize.toDouble() - 0.5
                val i0 = floor(gxGrid).toInt().coerceIn(0, cols - 1)
                val i1 = (i0 + 1).coerceIn(0, cols - 1)
                val fx = (gxGrid - i0).coerceIn(0.0, 1.0)

                val t00 = thresholdGrid[j0][i0]
                val t10 = thresholdGrid[j0][i1]
                val t01 = thresholdGrid[j1][i0]
                val t11 = thresholdGrid[j1][i1]

                val top = t00 + (t10 - t00) * fx
                val bottom = t01 + (t11 - t01) * fx
                val threshold = top + (bottom - top) * fy

                pixels[rowBase + x] = gray[rowBase + x] <= threshold
            }
        }
        return BinaryImage(width, height, pixels)
    }

    /**
     * Compute the local threshold for the tile spanning [x0,y0)–[x1,y1).
     *
     * - If the tile has meaningful contrast (gray range ≥ [MIN_RANGE]), run Otsu
     *   to find the ink/paper split and return the **midpoint of the two class
     *   means**. Using the midpoint (rather than Otsu's raw boundary, which sits
     *   at the ink value) places the threshold centrally between ink and paper,
     *   which is essential because bilinear interpolation between neighbouring
     *   tiles pulls the per-pixel threshold away from the tile centre — a central
     *   threshold keeps a safe margin on both sides of the boundary.
     * - If the tile is contrast-free (uniform paper/ink, range < [MIN_RANGE]),
     *   fall back to the [globalThreshold], which knows the overall ink/paper
     *   split and correctly classifies uniform bright regions as paper and
     *   uniform dark regions as ink.
     */
    private fun localThreshold(
        gray: IntArray,
        width: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        globalThreshold: Int
    ): Double {
        // Build a histogram over the tile and track min/max for the range check.
        val hist = IntArray(256)
        var minV = 255
        var maxV = 0
        var count = 0
        for (y in y0 until y1) {
            val base = y * width
            for (x in x0 until x1) {
                val v = gray[base + x].coerceIn(0, 255)
                hist[v]++
                if (v < minV) minV = v
                if (v > maxV) maxV = v
                count++
            }
        }
        if (count == 0) return globalThreshold.toDouble()

        // No contrast → degenerate; use global threshold.
        if (maxV - minV < MIN_RANGE) return globalThreshold.toDouble()

        // Otsu over this tile's histogram, tracking the class means at the
        // optimum so we can return their midpoint (central threshold).
        var sumAll = 0.0
        for (g in 0..255) sumAll += g.toDouble() * hist[g]

        var sumBg = 0.0
        var weightBg = 0
        var maxVariance = -1.0
        var bestMeanBg = globalThreshold.toDouble()
        var bestMeanFg = globalThreshold.toDouble()
        for (t in 0..255) {
            weightBg += hist[t]
            if (weightBg == 0) continue
            val weightFg = count - weightBg
            if (weightFg == 0) break
            sumBg += t.toDouble() * hist[t]
            val meanBg = sumBg / weightBg
            val meanFg = (sumAll - sumBg) / weightFg
            val between = weightBg.toDouble() * weightFg.toDouble() *
                (meanBg - meanFg) * (meanBg - meanFg)
            if (between > maxVariance) {
                maxVariance = between
                bestMeanBg = meanBg
                bestMeanFg = meanFg
            }
        }
        // Central threshold between the ink class and the paper class.
        return (bestMeanBg + bestMeanFg) / 2.0
    }

    /**
     * Convenience: build a [BinaryImage] directly from a luminance array using
     * adaptive binarization. Equivalent to [binarize] but reads more naturally
     * at call sites that already hold a grayscale buffer.
     */
    fun fromGrayscale(width: Int, height: Int, gray: IntArray, tileSize: Int = DEFAULT_TILE): BinaryImage =
        binarize(width, height, gray, tileSize)
}
