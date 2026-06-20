package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Unit tests for [Deskewer] — the skew-correction preprocessing module.
 *
 * Tests use **independently-drawn** tilted staff-line images (not [Deskewer.rotate]
 * itself) to avoid circular verification. The core invariant tested is: a set of
 * horizontal staff lines that are tilted by a known angle can have that angle
 * recovered by [Deskewer.estimateSkewAngle], and [Deskewer.deskew] brings them
 * back to horizontal.
 */
class DeskewerTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Draw 5 staff lines at the given base Y values, tilted by [angleDeg] degrees. */
    private fun drawTiltedStaff(img: BinaryImage, lineYs: List<Int>, angleDeg: Double) {
        val tanA = tan(Math.toRadians(angleDeg))
        val midX = img.width / 2.0
        for (baseY in lineYs) {
            for (x in 0 until img.width) {
                val y = (baseY + (x - midX) * tanA).roundToInt()
                if (y in 0 until img.height) img.set(x, y, true)
            }
        }
    }

    /** Draw 5 perfectly horizontal staff lines. */
    private fun drawStaff(img: BinaryImage, lineYs: List<Int>) {
        for (y in lineYs) for (x in 0 until img.width) img.set(x, y, true)
    }

    /**
     * Measure how "horizontal" the black ink is by checking if black pixels cluster
     * into a small number of distinct rows (low spread = more horizontal).
     * Returns the set of Y rows that contain significant ink.
     */
    private fun inkRowSpread(img: BinaryImage): Int {
        val rowCounts = IntArray(img.height)
        for (y in 0 until img.height) rowCounts[y] = img.rowBlackCount(y)
        val maxCount = rowCounts.maxOrNull() ?: 0
        if (maxCount == 0) return 0
        // Count rows with at least 40% of the maximum row's ink (the staff-line bands).
        return rowCounts.count { it >= maxCount * 0.4 }
    }

    // ── estimateSkewAngle ────────────────────────────────────────────────────

    @Test
    fun `horizontal staff has near-zero skew estimate`() {
        val img = BinaryImage.blank(500, 200)
        drawStaff(img, listOf(80, 100, 120, 140, 160))
        val angle = Deskewer.estimateSkewAngle(img)
        assertTrue("horizontal image skew should be ~0, got $angle°", abs(angle) < 1.0)
    }

    @Test
    fun `positive tilt is detected`() {
        val img = BinaryImage.blank(500, 200)
        drawTiltedStaff(img, listOf(80, 100, 120, 140, 160), angleDeg = 5.0)
        val angle = Deskewer.estimateSkewAngle(img)
        assertTrue("5° tilt should estimate ~5°, got $angle°", abs(angle - 5.0) <= 1.0)
    }

    @Test
    fun `negative tilt is detected`() {
        val img = BinaryImage.blank(500, 200)
        drawTiltedStaff(img, listOf(80, 100, 120, 140, 160), angleDeg = -5.0)
        val angle = Deskewer.estimateSkewAngle(img)
        assertTrue("-5° tilt should estimate ~-5°, got $angle°", abs(angle - (-5.0)) <= 1.0)
    }

    @Test
    fun `larger tilt is detected`() {
        val img = BinaryImage.blank(600, 300)
        drawTiltedStaff(img, listOf(100, 130, 160, 190, 220), angleDeg = 8.0)
        val angle = Deskewer.estimateSkewAngle(img)
        assertTrue("8° tilt should estimate ~8°, got $angle°", abs(angle - 8.0) <= 1.0)
    }

    @Test
    fun `nearly-empty image returns zero skew`() {
        val img = BinaryImage.blank(200, 100)
        // Only a handful of pixels — below the 20-pixel minimum.
        img.set(50, 50, true)
        img.set(51, 50, true)
        img.set(52, 50, true)
        assertEquals(0.0, Deskewer.estimateSkewAngle(img), 0.001)
    }

    // ── rotate ───────────────────────────────────────────────────────────────

    @Test
    fun `rotate zero degrees returns the same image`() {
        val img = BinaryImage.blank(100, 80)
        img.set(50, 40, true)
        val rotated = Deskewer.rotate(img, 0.0)
        assertTrue("0° rotation should be identity", rotated === img)
    }

    @Test
    fun `rotate 90 degrees swaps dimensions`() {
        val img = BinaryImage.blank(100, 60)
        val rotated = Deskewer.rotate(img, 90.0)
        assertTrue("rotated width should be ~original height", abs(rotated.width - 60) <= 2)
        assertTrue("rotated height should be ~original width", abs(rotated.height - 100) <= 2)
    }

    @Test
    fun `rotate preserves total ink approximately`() {
        val img = BinaryImage.blank(200, 150)
        drawStaff(img, listOf(60, 70, 80, 90, 100))
        val original = img.totalBlack()
        val rotated = Deskewer.rotate(img, 5.0)
        val after = rotated.totalBlack()
        // Nearest-neighbour rotation may lose a few edge pixels but should preserve
        // the vast majority of ink.
        assertTrue(
            "rotation should preserve >90% of ink: before=$original after=$after",
            after >= original * 0.9
        )
    }

    @Test
    fun `rotate then inverse rotate recovers approximately the original`() {
        val img = BinaryImage.blank(200, 150)
        drawStaff(img, listOf(60, 70, 80, 90, 100))
        val tilted = Deskewer.rotate(img, 5.0)
        val recovered = Deskewer.rotate(tilted, -5.0)
        // The recovered image should have ink concentrated in the same ~5 rows.
        assertTrue(
            "recovered image should be wide enough: ${recovered.width}",
            recovered.width >= 180
        )
        // Row spread should be small (5 staff lines → ~5-8 rows with significant ink).
        val spread = inkRowSpread(recovered)
        assertTrue("recovered staff should be near-horizontal (spread $spread)", spread <= 12)
    }

    // ── deskew (end-to-end) ──────────────────────────────────────────────────

    @Test
    fun `deskew returns original when already horizontal`() {
        val img = BinaryImage.blank(500, 200)
        drawStaff(img, listOf(80, 100, 120, 140, 160))
        val result = Deskewer.deskew(img)
        assertTrue("already-horizontal image should be returned unchanged", result === img)
    }

    @Test
    fun `deskew corrects a tilted staff to horizontal`() {
        val img = BinaryImage.blank(500, 200)
        drawTiltedStaff(img, listOf(80, 100, 120, 140, 160), angleDeg = 5.0)

        // Before deskew: ink is spread across many rows (tilted).
        val spreadBefore = inkRowSpread(img)
        assertTrue("tilted staff should have wide row spread: $spreadBefore", spreadBefore > 20)

        val deskewed = Deskewer.deskew(img)

        // After deskew: ink should concentrate into ~5 bands (horizontal lines).
        val spreadAfter = inkRowSpread(deskewed)
        assertTrue(
            "deskewed staff should have narrow row spread (got $spreadAfter, was $spreadBefore)",
            spreadAfter <= 15
        )
    }

    @Test
    fun `deskew corrects negative tilt`() {
        val img = BinaryImage.blank(500, 200)
        drawTiltedStaff(img, listOf(80, 100, 120, 140, 160), angleDeg = -6.0)

        val deskewed = Deskewer.deskew(img)
        val spreadAfter = inkRowSpread(deskewed)
        assertTrue(
            "deskewed negative-tilt staff should have narrow row spread: $spreadAfter",
            spreadAfter <= 15
        )
    }

    @Test
    fun `deskewed staff is detectable by StaffLineDetector`() {
        val img = BinaryImage.blank(500, 200)
        drawTiltedStaff(img, listOf(80, 100, 120, 140, 160), angleDeg = 4.0)

        // Without deskew, the tilt may prevent clean detection.
        val deskewed = Deskewer.deskew(img)

        // After deskew, staff detection should find exactly one system of 5 lines.
        val systems = StaffLineDetector.detect(deskewed)
        assertTrue("deskewed image should have detectable staff systems: ${systems.size}", systems.isNotEmpty())
        assertEquals("should find 5 staff lines", 5, systems[0].lines.size)
    }

    @Test
    fun `tilted staff alone fails or degrades detection without deskew`() {
        // This test documents the motivation: a 6° tilt makes the raw horizontal
        // projection fail to detect a clean 5-line system.
        val img = BinaryImage.blank(500, 200)
        drawTiltedStaff(img, listOf(80, 100, 120, 140, 160), angleDeg = 6.0)

        val systems = StaffLineDetector.detect(img)
        // With 6° tilt on a 500px-wide image, lines shift ~26px — enough to break
        // the uniformity grouping. Either no system or a degraded one.
        assertTrue(
            "6° tilt should degrade staff detection (got ${systems.size} systems)",
            systems.isEmpty() || systems.all { it.lineSpacing !in setOf(20) }
        )
    }
}
