package com.pianocompanion.omr.image

/**
 * Otsu's method: choose the binarization threshold that maximizes the
 * inter-class variance between foreground (ink) and background (paper).
 *
 * This gives an automatic, image-adaptive threshold instead of a fixed value,
 * which is far more robust to varying lighting/contrast in photographed scores.
 */
object OtsuThresholder {

    private const val BINS = 256

    /**
     * @param gray luminance values in the range 0..255 (row-major, any dimensions).
     * @return the optimal threshold (0..255). Ink pixels have luminance < threshold.
     */
    fun threshold(gray: IntArray): Int {
        if (gray.isEmpty()) return 128

        val hist = IntArray(BINS)
        for (g in gray) {
            val v = g.coerceIn(0, 255)
            hist[v]++
        }

        val total = gray.size
        var sumAll = 0.0
        for (i in 0 until BINS) sumAll += i.toDouble() * hist[i]

        var sumBg = 0.0
        var weightBg = 0
        var maxVariance = -1.0
        var bestThreshold = 127

        for (t in 0 until BINS) {
            weightBg += hist[t]
            if (weightBg == 0) continue
            val weightFg = total - weightBg
            if (weightFg == 0) break

            sumBg += t.toDouble() * hist[t]
            val meanBg = sumBg / weightBg
            val meanFg = (sumAll - sumBg) / weightFg
            val betweenClassVariance =
                weightBg.toDouble() * weightFg.toDouble() * (meanBg - meanFg) * (meanBg - meanFg)

            if (betweenClassVariance > maxVariance) {
                maxVariance = betweenClassVariance
                bestThreshold = t
            }
        }
        return bestThreshold
    }
}
