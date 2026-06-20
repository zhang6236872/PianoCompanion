package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Corrects the rotation (skew) of a binarized score image so that staff lines
 * are truly horizontal.
 *
 * Real photographs of sheet music are rarely perfectly level. Even a 2–5° tilt
 * is enough to spread a staff line's ink across many rows, diluting the
 * horizontal-projection peaks that [StaffLineDetector] relies on — causing
 * staff detection to fail entirely. Deskewing as a preprocessing step makes the
 * whole OMR pipeline dramatically more robust to handheld photos.
 *
 * **Algorithm — projection-profile variance maximization:**
 *
 * When staff lines are truly horizontal they concentrate ink into a few rows,
 * producing sharp, high-variance peaks in the horizontal black-pixel projection.
 * A tilted score, by contrast, spreads ink evenly → low-variance profile. So the
 * skew angle is recovered by finding the shear that maximises projection
 * "peakiness", then rotating the image by the negated angle.
 *
 * Being pure Kotlin with **no Android dependency**, the module is fully
 * unit-testable in the plain JVM.
 */
object Deskewer {

    /**
     * Estimate the skew angle and rotate the image to correct it.
     *
     * @param image      binarized score image (ink = black).
     * @param maxAngle   maximum absolute skew to search for, in degrees.
     * @param angleStep  granularity of the angle search, in degrees.
     * @return the deskewed image. When the estimated skew is below the search
     *         resolution the original image is returned unchanged (no copy).
     */
    fun deskew(
        image: BinaryImage,
        maxAngle: Double = 12.0,
        angleStep: Double = 0.5
    ): BinaryImage {
        val angle = estimateSkewAngle(image, maxAngle, angleStep)
        // Below the search resolution → already effectively straight.
        if (abs(angle) < angleStep / 2.0) return image
        return rotate(image, -angle)
    }

    /**
     * Estimate the dominant skew angle (degrees) using projection-profile
     * peakiness maximization via a fast shear approximation.
     *
     * For a test angle α every black pixel at (x, y) is accumulated into a bin
     * indexed by `round(y − x·tan α)`. When α matches the true skew, pixels on a
     * tilted staff line collapse into the same bin, producing sharp peaks. The
     * score is the sum of squared bin counts (a variance/peakiness surrogate
     * that is cheaper to compute than the full variance). The angle yielding the
     * highest score is returned.
     *
     * Positive result = image is rotated counter-clockwise (staff lines slope
     * up-left-to-down-right); negative = clockwise.
     *
     * @return the estimated skew angle in degrees, or 0.0 if the image has too
     *         little ink to estimate reliably.
     */
    fun estimateSkewAngle(
        image: BinaryImage,
        maxAngle: Double = 12.0,
        angleStep: Double = 0.5
    ): Double {
        val w = image.width
        val h = image.height

        // Too little ink → nothing meaningful to deskew.
        val totalBlack = image.totalBlack()
        if (totalBlack < 20) return 0.0

        // Subsample on large images to keep estimation fast.
        // Target ~50 000 sampled black pixels max.
        val sampleStride = if (totalBlack > 50_000) 2 else 1

        var bestAngle = 0.0
        var bestScore = -1.0

        // Iterate candidate angles from coarse-to-fine is overkill here; a single
        // pass at [angleStep] resolution is fast enough and simpler.
        var angle = -maxAngle
        while (angle <= maxAngle + 1e-9) {
            val score = projectionPeakiness(image, angle, sampleStride)
            if (score > bestScore) {
                bestScore = score
                bestAngle = angle
            }
            angle += angleStep
        }

        return roundToStep(bestAngle, angleStep)
    }

    /**
     * Sum-of-squared-bin-counts for the sheared horizontal projection at [angleDeg].
     * Higher = sharper peaks = better line alignment at this angle.
     */
    private fun projectionPeakiness(
        image: BinaryImage,
        angleDeg: Double,
        stride: Int
    ): Double {
        val w = image.width
        val h = image.height
        val tanA = tan(Math.toRadians(angleDeg))

        // Bin index = round(y − x·tanA) shifted to be non-negative.
        // Range of (y − x·tanA) for x ∈ [0,w), y ∈ [0,h):
        //   • tanA ≥ 0: min ≈ −(w−1)·tanA  (at x=w−1, y=0),  max ≈ h−1 (at x=0,y=h−1)
        //   • tanA < 0: min ≈ 0,            max ≈ (h−1)+(w−1)·|tanA|
        // A single shift = ceil(|(w−1)·tanA|) covers both cases.
        val shift = ceil(abs((w - 1) * tanA)).toInt()
        val numBins = h + 2 * shift
        if (numBins <= 0) return 0.0
        val bins = IntArray(numBins)

        for (y in 0 until h step stride) {
            val base = y * w
            for (x in 0 until w step stride) {
                if (image.pixels[base + x]) {
                    val bin = (y - x * tanA + shift).roundToInt()
                        .coerceIn(0, numBins - 1)
                    bins[bin]++
                }
            }
        }

        var sumSq = 0.0
        for (b in bins) sumSq += b.toDouble() * b
        return sumSq
    }

    /** Round [value] to the nearest multiple of [step] (avoids 0.5° → 0.4999° artifacts). */
    private fun roundToStep(value: Double, step: Double): Double {
        return (value / step).roundToInt().toDouble() * step
    }

    /**
     * Rotate the image by [angleDeg] degrees (positive = counter-clockwise)
     * using nearest-neighbour inverse mapping.
     *
     * The output canvas is sized to fully contain the rotated source (bounding
     * box of the four rotated corners). Pixels that map outside the source are
     * left white. Nearest-neighbour is chosen over bilinear interpolation
     * because the source is already a hard-threshold binary image — there is no
     * sub-pixel grey information to interpolate.
     *
     * @return a new [BinaryImage]. For [angleDeg] = 0 the original is returned.
     */
    fun rotate(image: BinaryImage, angleDeg: Double): BinaryImage {
        if (angleDeg == 0.0) return image
        val w = image.width
        val h = image.height
        val rad = Math.toRadians(angleDeg)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val cx = (w - 1) / 2.0
        val cy = (h - 1) / 2.0

        // Forward-map the four corners to find the output bounding box.
        val cornersX = doubleArrayOf(
            forwardX(0.0, 0.0, cx, cy, cosA, sinA),
            forwardX(w - 1.0, 0.0, cx, cy, cosA, sinA),
            forwardX(0.0, h - 1.0, cx, cy, cosA, sinA),
            forwardX(w - 1.0, h - 1.0, cx, cy, cosA, sinA)
        )
        val cornersY = doubleArrayOf(
            forwardY(0.0, 0.0, cx, cy, cosA, sinA),
            forwardY(w - 1.0, 0.0, cx, cy, cosA, sinA),
            forwardY(0.0, h - 1.0, cx, cy, cosA, sinA),
            forwardY(w - 1.0, h - 1.0, cx, cy, cosA, sinA)
        )
        val minX = cornersX.min(); val maxX = cornersX.max()
        val minY = cornersY.min(); val maxY = cornersY.max()
        val newW = max(1, ceil(maxX - minX).toInt())
        val newH = max(1, ceil(maxY - minY).toInt())

        val result = BinaryImage.blank(newW, newH)
        for (ny in 0 until newH) {
            for (nx in 0 until newW) {
                // World coordinate of this output pixel.
                val wx = nx + minX
                val wy = ny + minY
                // Inverse-rotate back to source coordinates.
                val dx = wx - cx
                val dy = wy - cy
                val sx = dx * cosA + dy * sinA + cx
                val sy = -dx * sinA + dy * cosA + cy
                val ix = sx.roundToInt()
                val iy = sy.roundToInt()
                if (ix in 0 until w && iy in 0 until h && image.isBlack(ix, iy)) {
                    result.set(nx, ny, true)
                }
            }
        }
        return result
    }

    private fun forwardX(
        x: Double, y: Double, cx: Double, cy: Double,
        cosA: Double, sinA: Double
    ): Double = cx + (x - cx) * cosA - (y - cy) * sinA

    private fun forwardY(
        x: Double, y: Double, cx: Double, cy: Double,
        cosA: Double, sinA: Double
    ): Double = cy + (x - cx) * sinA + (y - cy) * cosA
}
