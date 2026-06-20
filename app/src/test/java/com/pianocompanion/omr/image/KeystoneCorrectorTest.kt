package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unit tests for [KeystoneCorrector] — the perspective (keystone / yaw)
 * correction preprocessing module.
 *
 * Tests draw **independently-synthesised** converging staff images (not the
 * corrector's own warp) to avoid circular verification. The core invariant:
 * a set of parallel staff lines distorted by a known convergence can have that
 * convergence detected by [KeystoneCorrector.estimateConvergence], and
 * [KeystoneCorrector.correct] straightens them back so the left/right system
 * heights match and [StaffLineDetector] sees a clean uniform system.
 */
class KeystoneCorrectorTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Draw 5 staff lines whose system height varies left-to-right by factor k.
     * At column x, each original line y is remapped to
     *   y' = yc + (y - yc) * (1 + k * (x/w - 0.5))
     * k > 0 ⇒ the right side is taller (lines fan apart toward the right);
     * k < 0 ⇒ the left side is taller. This is a simple, independent model of
     * yaw perspective that produces exactly the left/right span asymmetry the
     * corrector is built to detect and undo.
     */
    private fun drawConvergingStaff(img: BinaryImage, lineYs: List<Int>, k: Double, yc: Double) {
        val w = img.width
        for (baseY in lineYs) {
            for (x in 0 until w) {
                val scale = 1.0 + k * (x.toDouble() / w - 0.5)
                val y = (yc + (baseY - yc) * scale).roundToInt()
                if (y in 0 until img.height) img.set(x, y, true)
            }
        }
    }

    /** Draw perfectly parallel horizontal staff lines. */
    private fun drawStaff(img: BinaryImage, lineYs: List<Int>) {
        for (y in lineYs) for (x in 0 until img.width) img.set(x, y, true)
    }

    /**
     * Independently measure the left/right system heights of [img] from pixels,
     * to verify straightening without depending on the corrector's own internals.
     * Uses per-column topmost/bottommost black pixel + percentile aggregation
     * (same robust idea as the module, re-implemented here so the test is not
     * circular). Returns (leftSpan, rightSpan), or null if not measurable.
     */
    private fun measureSpans(img: BinaryImage): Pair<Double, Double>? {
        val w = img.width
        val xLeftEnd = (w / 3).coerceAtLeast(1)
        val xRightStart = (w * 2 / 3).coerceIn(1, w - 1)
        val tl = columnPercentile(img, 0, xLeftEnd, findTop = true) ?: return null
        val tr = columnPercentile(img, xRightStart, w, findTop = true) ?: return null
        val bl = columnPercentile(img, 0, xLeftEnd, findTop = false) ?: return null
        val br = columnPercentile(img, xRightStart, w, findTop = false) ?: return null
        return (bl - tl) to (br - tr)
    }

    private fun columnPercentile(img: BinaryImage, xA: Int, xB: Int, findTop: Boolean): Double? {
        val vals = ArrayList<Int>()
        if (findTop) {
            for (x in xA until xB) {
                var hit = -1
                for (y in 0 until img.height) if (img.isBlack(x, y)) { hit = y; break }
                if (hit >= 0) vals += hit
            }
        } else {
            for (x in xA until xB) {
                var hit = -1
                for (y in img.height - 1 downTo 0) if (img.isBlack(x, y)) { hit = y; break }
                if (hit >= 0) vals += hit
            }
        }
        if (vals.size < 5) return null
        vals.sort()
        val p = if (findTop) 0.60 else 0.40
        val idx = (p * (vals.size - 1)).roundToInt().coerceIn(0, vals.size - 1)
        return vals[idx].toDouble()
    }

    // ── estimateConvergence ──────────────────────────────────────────────────

    @Test
    fun `parallel staff has ratio near one and is not significant`() {
        val img = BinaryImage.blank(500, 200)
        drawStaff(img, listOf(80, 100, 120, 140, 160))
        val conv = KeystoneCorrector.estimateConvergence(img)
        assertNotNull("parallel staff should be measurable", conv)
        conv!!
        assertTrue("ratio should be ~1.0, got ${conv.ratio}", abs(conv.ratio - 1.0) < 0.05)
        assertFalse("parallel staff must not be flagged significant", conv.significant)
    }

    @Test
    fun `diverging staff right taller is detected`() {
        val img = BinaryImage.blank(500, 240)
        // k > 0 ⇒ right side taller ⇒ rightSpan > leftSpan ⇒ ratio > 1.
        drawConvergingStaff(img, listOf(80, 100, 120, 140, 160), k = 0.4, yc = 120.0)
        val conv = KeystoneCorrector.estimateConvergence(img)
        assertNotNull(conv); conv!!
        assertTrue("diverging staff ratio should exceed 1.2, got ${conv.ratio}", conv.ratio > 1.2)
        assertTrue("diverging staff should be significant", conv.significant)
    }

    @Test
    fun `diverging staff left taller is detected`() {
        val img = BinaryImage.blank(500, 240)
        // k < 0 ⇒ left side taller ⇒ rightSpan < leftSpan ⇒ ratio < 1.
        drawConvergingStaff(img, listOf(80, 100, 120, 140, 160), k = -0.4, yc = 120.0)
        val conv = KeystoneCorrector.estimateConvergence(img)
        assertNotNull(conv); conv!!
        assertTrue("left-taller staff ratio should be below 0.85, got ${conv.ratio}", conv.ratio < 0.85)
        assertTrue("left-taller staff should be significant", conv.significant)
    }

    @Test
    fun `nearly-empty image returns null convergence`() {
        val img = BinaryImage.blank(300, 200)
        img.set(50, 50, true); img.set(51, 50, true)
        assertNull(KeystoneCorrector.estimateConvergence(img))
    }

    @Test
    fun `too-few bands returns null`() {
        // A single horizontal line is not enough to measure a system height.
        val img = BinaryImage.blank(400, 200)
        drawStaff(img, listOf(100))
        assertNull(KeystoneCorrector.estimateConvergence(img))
    }

    // ── correct (end-to-end) ─────────────────────────────────────────────────

    @Test
    fun `correct leaves a parallel staff unchanged no regression`() {
        val img = BinaryImage.blank(500, 200)
        drawStaff(img, listOf(80, 100, 120, 140, 160))
        val outcome = KeystoneCorrector.correct(img)
        assertFalse("parallel staff must not be 'corrected'", outcome.applied)
        assertTrue("unchanged image should be the same instance", outcome.image === img)
    }

    @Test
    fun `correct straightens a diverging staff to uniform spans`() {
        val img = BinaryImage.blank(500, 260)
        drawConvergingStaff(img, listOf(90, 115, 140, 165, 190), k = 0.5, yc = 140.0)

        // Before: clearly asymmetric.
        val (leftBefore, rightBefore) = measureSpans(img)!!
        val ratioBefore = rightBefore / leftBefore
        assertTrue("before correction ratio should be > 1.2, got $ratioBefore", ratioBefore > 1.2)

        val outcome = KeystoneCorrector.correct(img)
        assertTrue("correction should be applied", outcome.applied)

        // After: left/right spans should match to within a few percent.
        val (leftAfter, rightAfter) = measureSpans(outcome.image)!!
        val ratioAfter = rightAfter / leftAfter
        assertTrue(
            "after correction ratio should be ~1.0 (got $ratioAfter, was $ratioBefore)",
            abs(ratioAfter - 1.0) < 0.08
        )
    }

    @Test
    fun `correct straightens a left-taller diverging staff`() {
        val img = BinaryImage.blank(500, 260)
        drawConvergingStaff(img, listOf(90, 115, 140, 165, 190), k = -0.5, yc = 140.0)
        val (leftBefore, rightBefore) = measureSpans(img)!!
        val ratioBefore = rightBefore / leftBefore
        assertTrue("before correction ratio should be < 0.85, got $ratioBefore", ratioBefore < 0.85)

        val outcome = KeystoneCorrector.correct(img)
        assertTrue(outcome.applied)
        val (leftAfter, rightAfter) = measureSpans(outcome.image)!!
        val ratioAfter = rightAfter / leftAfter
        assertTrue(
            "after correction ratio should be ~1.0 (got $ratioAfter)",
            abs(ratioAfter - 1.0) < 0.08
        )
    }

    @Test
    fun `corrected staff is detectable as a clean uniform system`() {
        val img = BinaryImage.blank(500, 240)
        drawConvergingStaff(img, listOf(80, 105, 130, 155, 180), k = 0.45, yc = 130.0)
        val outcome = KeystoneCorrector.correct(img)
        assertTrue(outcome.applied)
        val systems = StaffLineDetector.detect(outcome.image)
        assertTrue(
            "corrected staff should yield a detectable system (got ${systems.size})",
            systems.isNotEmpty()
        )
        assertEquals("a single clean system of 5 lines", 5, systems[0].lines.size)
    }

    @Test
    fun `mild convergence below threshold is not corrected`() {
        // k = 0.03 ⇒ ~3% height change across the width, below the 8% threshold.
        val img = BinaryImage.blank(500, 200)
        drawConvergingStaff(img, listOf(80, 100, 120, 140, 160), k = 0.03, yc = 120.0)
        val outcome = KeystoneCorrector.correct(img)
        assertFalse("mild (<8%) convergence should not trigger correction", outcome.applied)
        assertTrue("image unchanged", outcome.image === img)
    }

    @Test
    fun `correct preserves ink approximately`() {
        val img = BinaryImage.blank(400, 220)
        drawConvergingStaff(img, listOf(70, 95, 120, 145, 170), k = 0.4, yc = 120.0)
        val before = img.totalBlack()
        val outcome = KeystoneCorrector.correct(img)
        val after = outcome.image.totalBlack()
        // Nearest-neighbour remap may drop/dupe a handful of pixels but keeps
        // the vast majority of the staff-line ink.
        assertTrue(
            "correction should preserve >92% of ink: before=$before after=$after",
            after >= before * 0.92
        )
    }

    @Test
    fun `correct handles staff near the top edge`() {
        // Staff hugging the top of the canvas: extrapolation above maps out of
        // range (→ white) but the staff itself must still be straightened.
        val img = BinaryImage.blank(500, 160)
        drawConvergingStaff(img, listOf(20, 45, 70, 95, 120), k = 0.4, yc = 70.0)
        val outcome = KeystoneCorrector.correct(img)
        assertTrue(outcome.applied)
        val systems = StaffLineDetector.detect(outcome.image)
        assertTrue("top-edge staff should still detect after correction", systems.isNotEmpty())
    }
}
