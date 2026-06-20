package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Corrects perspective (keystone) distortion that remains after [Deskewer].
 *
 * When sheet music is photographed at a horizontal angle (camera **yaw**), the
 * page is no longer fronto-parallel: staff lines that are truly parallel
 * converge toward a vanishing point, and a staff system's vertical height
 * differs between the left and right edges of the image. [Deskewer] cannot fix
 * this — it only removes *uniform in-plane rotation* (every line tilted by the
 * same angle). Residual convergence has two harmful consequences:
 *
 *  1. It breaks [StaffLineDetector]'s uniform-spacing grouping (the system may
 *     no longer be recognised as 5 evenly-spaced lines).
 *  2. It corrupts the Y→pitch mapping: a notehead's vertical position no longer
 *     maps to a single, constant line spacing, so [PitchMapper] returns wrong
 *     notes (a note read on the "compressed" side maps a step or two off).
 *
 * **Detection.** The strongest, most reliable feature in a score image is the
 * staff lines. The module computes the horizontal black-pixel projection, finds
 * the topmost and bottommost staff-line bands, and measures each band's vertical
 * centroid in the *left* and *right* thirds of the image. Under perspective the
 * system height `span = bottomY − topY` differs between the two sides; the ratio
 * `rightSpan / leftSpan` quantifies the distortion. Below a configurable
 * threshold the image is considered fronto-parallel and returned **unchanged**
 * (no regression for clean/already-correct images — same contract as [Deskewer]).
 *
 * **Correction.** A per-column vertical affine remap ("rubber sheet"). For each
 * column x the measured staff band `[topY(x), bottomY(x)]` (linearly interpolated
 * between the two measured sides) is mapped to a uniform target band
 * `[topTarget, bottomTarget]`. This straightens the top and bottom staff lines
 * to horizontal and restores a constant line spacing across the whole width;
 * notes move with them. The horizontal (x) axis is left untouched — mild
 * horizontal compression from yaw only slightly affects the *estimated* timing
 * (already flagged as needing manual proofreading) and does not affect pitch,
 * which is the geometry that actually matters.
 *
 * Being pure Kotlin with **no Android dependency**, the module is fully
 * unit-testable in the plain JVM.
 */
object KeystoneCorrector {

    /** Minimum |ratio − 1| to treat as significant perspective (8% height change). */
    private const val DEFAULT_RATIO_THRESHOLD = 0.08

    /** Minimum ink pixels required on a side to trust its centroid measurement. */
    private const val DEFAULT_MIN_SIDE_INK = 5

    /** Outcome of a correction attempt. */
    data class CorrectionOutcome(
        val image: BinaryImage,
        val applied: Boolean,
        /** Measured rightSpan/leftSpan ratio (≈1 ⇒ no distortion). */
        val ratio: Double
    )

    /** Measured staff-band geometry across the left/right sides of the image. */
    data class Convergence(
        val topLeftY: Double,
        val topRightY: Double,
        val botLeftY: Double,
        val botRightY: Double,
        val xLeft: Int,
        val xRight: Int,
        val significant: Boolean,
        val ratio: Double
    ) {
        val leftSpan: Double get() = botLeftY - topLeftY
        val rightSpan: Double get() = botRightY - topRightY
    }

    /**
     * Correct perspective distortion if present.
     *
     * @return [CorrectionOutcome]; when no significant convergence is detected
     *         (or no usable staff bands exist) the original image is returned
     *         unchanged with `applied = false`.
     */
    fun correct(
        image: BinaryImage,
        ratioThreshold: Double = DEFAULT_RATIO_THRESHOLD,
        minSideInk: Int = DEFAULT_MIN_SIDE_INK
    ): CorrectionOutcome {
        val conv = estimateConvergence(image, ratioThreshold, minSideInk)
            ?: return CorrectionOutcome(image, false, 1.0)
        if (!conv.significant) return CorrectionOutcome(image, false, conv.ratio)
        val fixed = remap(image, conv)
        return CorrectionOutcome(fixed, true, conv.ratio)
    }

    /**
     * Measure staff-band convergence across the left/right thirds of the image.
     *
     * Detection is **per-column** rather than projection-based. A projection
     * profile fails under convergence because the middle staff line (at the yaw
     * pivot) stays perfectly horizontal and full-width, dominating the peak,
     * while the extreme top/bottom lines — the very ones whose asymmetry signals
     * perspective — are tilted the most, spreading their ink thinly across many
     * rows and dropping below any threshold tied to that peak.
     *
     * Instead, for each side we scan every column's *topmost* and *bottommost*
     * black pixel. In the vast majority of columns (those without notes above
     * the staff or long stems below it) the topmost pixel is the top staff line
     * and the bottommost is the bottom staff line — at exactly that column's
     * position, so this is robust to any per-line slope. Aggregating across the
     * side with a percentile (rather than a mean) rejects the minority of columns
     * containing high/low notes or stems.
     *
     * @return null if either side has too few inked columns to trust, or if the
     *         measured geometry is degenerate — in either case the image is
     *         treated as "no measurable perspective".
     */
    fun estimateConvergence(
        image: BinaryImage,
        ratioThreshold: Double = DEFAULT_RATIO_THRESHOLD,
        minSideInk: Int = DEFAULT_MIN_SIDE_INK
    ): Convergence? {
        val w = image.width
        val h = image.height
        if (w < 12 || h < 12) return null

        val xLeftEnd = (w / 3).coerceAtLeast(1)
        val xRightStart = (w * 2 / 3).coerceIn(1, w - 1)

        // Top staff line Y per side: the 60th-percentile of per-column topmost
        // ink (rejects the smaller-Y outliers from notes drawn above the staff).
        val topLeftY = columnBoundary(image, 0, xLeftEnd, findTop = true, minSideInk)
        val topRightY = columnBoundary(image, xRightStart, w, findTop = true, minSideInk)
        // Bottom staff line Y per side: the 40th-percentile of per-column
        // bottommost ink (rejects the larger-Y outliers from stems/notes below).
        val botLeftY = columnBoundary(image, 0, xLeftEnd, findTop = false, minSideInk)
        val botRightY = columnBoundary(image, xRightStart, w, findTop = false, minSideInk)
        if (topLeftY == null || topRightY == null ||
            botLeftY == null || botRightY == null
        ) return null

        // Sanity: top boundary must sit above the bottom boundary on both sides.
        if (topLeftY >= botLeftY || topRightY >= botRightY) return null

        val leftSpan = botLeftY - topLeftY
        val rightSpan = botRightY - topRightY
        if (leftSpan <= 0 || rightSpan <= 0) return null

        val ratio = rightSpan / leftSpan
        val significant = abs(ratio - 1.0) > ratioThreshold
        val xLeft = xLeftEnd / 2
        val xRight = (xRightStart + w) / 2
        return Convergence(
            topLeftY, topRightY, botLeftY, botRightY,
            xLeft, xRight, significant, ratio
        )
    }

    /**
     * Robust top/bottom staff boundary over columns [xA, xB).
     *
     * Collects the topmost (or bottommost) black Y of every inked column, then
     * returns a percentile: 0.60 for the top boundary (to reject notes drawn
     * above the staff, which are smaller-Y outliers), 0.40 for the bottom
     * boundary (to reject stems/notes below the staff, larger-Y outliers).
     *
     * @return null if fewer than [min] columns carry ink (too little to trust).
     */
    private fun columnBoundary(
        image: BinaryImage,
        xA: Int,
        xB: Int,
        findTop: Boolean,
        min: Int
    ): Double? {
        val vals = ArrayList<Int>()
        if (findTop) {
            for (x in xA until xB) {
                var hit = -1
                for (y in 0 until image.height) {
                    if (image.isBlack(x, y)) { hit = y; break }
                }
                if (hit >= 0) vals += hit
            }
        } else {
            for (x in xA until xB) {
                var hit = -1
                for (y in image.height - 1 downTo 0) {
                    if (image.isBlack(x, y)) { hit = y; break }
                }
                if (hit >= 0) vals += hit
            }
        }
        if (vals.size < min) return null
        vals.sort()
        val p = if (findTop) 0.60 else 0.40
        val idx = (p * (vals.size - 1)).roundToInt().coerceIn(0, vals.size - 1)
        return vals[idx].toDouble()
    }

    /**
     * Per-column vertical affine remap: map each column's measured staff band
     * `[topY(x), bottomY(x)]` to the uniform target band, straightening the
     * staff lines. Implemented as an output-driven nearest-neighbour inverse map
     * (the forward map within a column is affine, hence trivially invertible).
     */
    private fun remap(image: BinaryImage, conv: Convergence): BinaryImage {
        val w = image.width
        val h = image.height
        val out = BinaryImage.blank(w, h)

        val topTarget = (conv.topLeftY + conv.topRightY) / 2.0
        val botTarget = (conv.botLeftY + conv.botRightY) / 2.0
        val targetSpan = botTarget - topTarget
        if (targetSpan <= 0) return image

        // Boundary lines topY(x)/bottomY(x), linear between the two measured sides.
        val xL = conv.xLeft.toDouble()
        val xR = conv.xRight.toDouble()
        val dx = (xR - xL).coerceAtLeast(1.0)
        val topSlope = (conv.topRightY - conv.topLeftY) / dx
        val botSlope = (conv.botRightY - conv.botLeftY) / dx
        val t0 = conv.topLeftY - topSlope * xL
        val b0 = conv.botLeftY - botSlope * xL

        for (x in 0 until w) {
            val tx = topSlope * x + t0       // source top line Y at this column
            val bx = botSlope * x + b0       // source bottom line Y at this column
            val srcSpan = bx - tx
            if (srcSpan <= 0) continue
            for (yo in 0 until h) {
                // Inverse of the affine per-column map:
                //   yo = topTarget + (ys - tx) * targetSpan / srcSpan
                val ys = tx + (yo - topTarget) * srcSpan / targetSpan
                val iy = ys.roundToInt()
                if (iy in 0 until h && image.isBlack(x, iy)) {
                    out.set(x, yo, true)
                }
            }
        }
        return out
    }
}
