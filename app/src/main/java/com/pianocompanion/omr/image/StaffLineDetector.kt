package com.pianocompanion.omr.image

/**
 * A single horizontal staff line, located as a horizontal band of ink.
 */
data class StaffLine(val yTop: Int, val yBottom: Int, val blackRatio: Double) {
    val center: Int get() = (yTop + yBottom) / 2
    val thickness: Int get() = yBottom - yTop + 1
}

/**
 * A staff system: a group of (usually 5) evenly-spaced staff lines.
 * [lines] are sorted top-to-bottom.
 */
data class StaffSystem(val lines: List<StaffLine>) {
    val topLine: StaffLine get() = lines.first()
    val bottomLine: StaffLine get() = lines.last()

    /**
     * Average vertical pixel spacing between adjacent staff lines.
     * Returns 0 when fewer than 2 lines are present.
     */
    val lineSpacing: Int
        get() {
            if (lines.size < 2) return 0
            return ((bottomLine.center - topLine.center).toDouble() / (lines.size - 1)).toInt().coerceAtLeast(1)
        }

    val centerY: Int get() = (topLine.center + bottomLine.center) / 2
}

/**
 * Detects staff systems via a horizontal black-pixel projection profile.
 *
 * Rows that are mostly ink (the staff lines) form peaks in the profile; these
 * are grouped into systems of [linesPerSystem] evenly-spaced lines.
 */
object StaffLineDetector {

    /**
     * @param image binarized score image.
     * @param minBlackRatio minimum fraction (0..1) of black pixels in a row for it to count as a staff line.
     * @param linesPerSystem expected lines per system (5 for standard notation).
     */
    fun detect(
        image: BinaryImage,
        minBlackRatio: Double = 0.45,
        linesPerSystem: Int = 5
    ): List<StaffSystem> {
        val threshold = (image.width * minBlackRatio).toInt().coerceAtLeast(1)

        // 1) Find horizontal bands where rows exceed the black-pixel threshold.
        val bands = ArrayList<StaffLine>()
        var runStart = -1
        for (y in 0 until image.height) {
            val black = image.rowBlackCount(y)
            if (black >= threshold) {
                if (runStart < 0) runStart = y
            } else {
                if (runStart >= 0) {
                    bands += StaffLine(runStart, y - 1, image.rowBlackCount((runStart + y - 1) / 2).toDouble() / image.width)
                    runStart = -1
                }
            }
        }
        if (runStart >= 0) {
            bands += StaffLine(runStart, image.height - 1, image.rowBlackCount((runStart + image.height - 1) / 2).toDouble() / image.width)
        }

        if (bands.size < linesPerSystem) return emptyList()

        // 2) Group bands into systems of `linesPerSystem` evenly-spaced lines.
        return groupIntoSystems(bands, linesPerSystem)
    }

    private fun groupIntoSystems(lines: List<StaffLine>, linesPerSystem: Int): List<StaffSystem> {
        val systems = ArrayList<StaffSystem>()
        var i = 0
        while (i <= lines.size - linesPerSystem) {
            val window = lines.subList(i, i + linesPerSystem)
            val spacings = (1 until window.size).map { window[it].center - window[it - 1].center }
            val medianSpacing = spacings.sorted()[spacings.size / 2].coerceAtLeast(1)
            // Accept the window only if every gap is close to the median gap.
            val uniform = spacings.all { sp ->
                kotlin.math.abs(sp - medianSpacing).toDouble() <= medianSpacing * 0.4 + 2.0
            }
            if (uniform) {
                systems += StaffSystem(window.toList())
                i += linesPerSystem
            } else {
                i++
            }
        }
        return systems
    }
}
