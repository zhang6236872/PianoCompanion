package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TieDetector] using synthetic binary images.
 *
 * A **tie** (延音线) is a thin curved arc connecting two noteheads of the same
 * pitch. These tests draw noteheads and arcs pixel-by-pixel, then verify that
 * the detector correctly identifies (or rejects) ties based on pitch proximity,
 * arc coverage, and gap constraints.
 */
class TieDetectorTest {

    private val width = 400
    private val height = 120
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /**
     * Draw a filled notehead-sized ellipse.
     */
    private fun drawNotehead(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x in 0 until width && y in 0 until height) {
                    val ndx = (x - cx).toDouble() / rx
                    val ndy = (y - cy).toDouble() / ry
                    if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
                }
            }
        }
    }

    private fun makeNh(cx: Int, cy: Int, w: Int = 9, h: Int = 7): Notehead =
        Notehead(cx, cy, w, h, w * h)

    /**
     * Draw a thin (2 px) tie arc from (x1, y) to (x2, y), arcing above or below.
     * Uses a half-sine so the arc starts and ends at Y=y with maximum displacement
     * at the midpoint, mimicking a real tie curve.
     */
    private fun drawTieArc(
        img: BinaryImage, x1: Int, x2: Int, y: Int,
        maxOffset: Int, above: Boolean
    ) {
        val dir = if (above) -1 else 1
        for (x in x1..x2) {
            val t = if (x2 > x1) (x - x1).toDouble() / (x2 - x1) else 0.0
            val offset = (maxOffset * kotlin.math.sin(Math.PI * t)).toInt()
            val arcY = y + dir * offset
            if (arcY in 0 until height) {
                img.set(x, arcY, true)
                if (arcY + 1 in 0 until height) img.set(x, arcY + 1, true)
            }
        }
    }

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `tie arc below two same-pitch noteheads is detected`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)
        drawTieArc(img, 85, 195, 50, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, ties.size)
        assertEquals(0, ties[0].firstNoteIdx)
        assertEquals(1, ties[0].secondNoteIdx)
    }

    @Test
    fun `tie arc above two same-pitch noteheads is detected`() {
        val img = blank()
        drawNotehead(img, 80, 70)
        drawNotehead(img, 200, 70)
        drawTieArc(img, 85, 195, 70, maxOffset = 8, above = true)

        val nhs = listOf(makeNh(80, 70), makeNh(200, 70))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, ties.size)
    }

    @Test
    fun `no tie arc returns empty`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Two same-pitch notes without arc should have no tie", ties.isEmpty())
    }

    // ---- Pitch discrimination -----------------------------------------------

    @Test
    fun `different pitch noteheads not tied even with arc between them`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 60) // Y differs by 20 > 0.5*s=5 → different pitch
        drawTieArc(img, 85, 195, 50, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 60))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Different-pitch notes should not be tied", ties.isEmpty())
    }

    @Test
    fun `slightly different Y within pitch tolerance still detected`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 53) // Y differs by 3 ≤ 0.5*s=5 → same pitch
        drawTieArc(img, 85, 195, 51, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 53))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals("Notes within pitch tolerance should be tied", 1, ties.size)
    }

    // ---- Gap constraints ----------------------------------------------------

    @Test
    fun `noteheads too close are not tied`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 92, 50) // gap < 1.0*s = 10px → too close
        drawTieArc(img, 85, 87, 50, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(80, 50), makeNh(92, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Noteheads too close should not be tied", ties.isEmpty())
    }

    // ---- Multiple ties / chains ---------------------------------------------

    @Test
    fun `two consecutive ties among three same-pitch notes`() {
        val img = blank()
        drawNotehead(img, 50, 50)
        drawNotehead(img, 160, 50)
        drawNotehead(img, 270, 50)
        drawTieArc(img, 55, 155, 50, maxOffset = 8, above = false)
        drawTieArc(img, 165, 265, 50, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(50, 50), makeNh(160, 50), makeNh(270, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0, 0), s)

        assertEquals(2, ties.size)
        assertEquals(0, ties[0].firstNoteIdx)
        assertEquals(1, ties[0].secondNoteIdx)
        assertEquals(1, ties[1].firstNoteIdx)
        assertEquals(2, ties[1].secondNoteIdx)
    }

    @Test
    fun `middle note of different pitch breaks tie chain`() {
        val img = blank()
        drawNotehead(img, 50, 50)
        drawNotehead(img, 160, 40) // different pitch
        drawNotehead(img, 270, 50)

        val nhs = listOf(makeNh(50, 50), makeNh(160, 40), makeNh(270, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0, 0), s)

        // All adjacent pairs have different pitch → no ties.
        assertTrue(ties.isEmpty())
    }

    // ---- Robustness: false positive rejection -------------------------------

    @Test
    fun `vertical stem between notes does not create false tie`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)
        // Draw a vertical stem at x=140 from y=30 to y=70.
        for (y in 30..70) img.set(140, y, true)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        // The stem covers 1 column out of ~112 gap columns → <1% coverage.
        assertTrue("Single stem should not create false tie", ties.isEmpty())
    }

    @Test
    fun `partial arc below coverage threshold is not detected`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)
        // Draw arc only in the first ~30% of the gap (insufficient coverage).
        drawTieArc(img, 85, 120, 50, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Partial arc below 75% coverage should not be detected", ties.isEmpty())
    }

    @Test
    fun `horizontal ledger line between notes does not create false tie`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)
        // Draw a short horizontal ledger line at y=62, x=130..150 (20 px).
        for (x in 130..150) img.set(x, 62, true)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), s)

        // Ledger line covers 21 columns out of ~112 → ~19% < 75%.
        assertTrue("Short ledger line should not create false tie", ties.isEmpty())
    }

    // ---- Multi-system -------------------------------------------------------

    @Test
    fun `ties only detected within same system, not across systems`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 90) // different system
        drawTieArc(img, 85, 195, 70, maxOffset = 8, above = false)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 90))
        val ties = TieDetector.detect(img, nhs, listOf(0, 1), s)

        assertTrue("Cross-system noteheads should not be tied", ties.isEmpty())
    }

    @Test
    fun `ties detected in parallel for different systems`() {
        val img = blank()
        // System 0: two notes at y=40
        drawNotehead(img, 60, 40)
        drawNotehead(img, 160, 40)
        drawTieArc(img, 65, 155, 40, maxOffset = 6, above = false)
        // System 1: two notes at y=90
        drawNotehead(img, 60, 90)
        drawNotehead(img, 160, 90)
        drawTieArc(img, 65, 155, 90, maxOffset = 6, above = false)

        val nhs = listOf(
            makeNh(60, 40), makeNh(160, 40),  // indices 0, 1 → system 0
            makeNh(60, 90), makeNh(160, 90)   // indices 2, 3 → system 1
        )
        val sysIdx = listOf(0, 0, 1, 1)
        val ties = TieDetector.detect(img, nhs, sysIdx, s)

        assertEquals(2, ties.size)
        // Tie within system 0.
        assertTrue(ties.any { it.firstNoteIdx == 0 && it.secondNoteIdx == 1 })
        // Tie within system 1.
        assertTrue(ties.any { it.firstNoteIdx == 2 && it.secondNoteIdx == 3 })
        // No cross-system tie.
        assertTrue(ties.none { it.firstNoteIdx < 2 && it.secondNoteIdx >= 2 })
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    fun `single notehead returns no ties`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        val nhs = listOf(makeNh(80, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0), s)
        assertTrue(ties.isEmpty())
    }

    @Test
    fun `empty noteheads returns no ties`() {
        val img = blank()
        val ties = TieDetector.detect(img, emptyList(), emptyList(), s)
        assertTrue(ties.isEmpty())
    }

    @Test
    fun `zero line spacing returns no ties`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)
        drawTieArc(img, 85, 195, 50, maxOffset = 8, above = false)
        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val ties = TieDetector.detect(img, nhs, listOf(0, 0), 0)
        assertTrue(ties.isEmpty())
    }

    @Test
    fun `empty system indices defaults all to system 0`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50)
        drawTieArc(img, 85, 195, 50, maxOffset = 8, above = false)
        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        // Empty system indices → all treated as system 0 → tie should be detected.
        val ties = TieDetector.detect(img, nhs, emptyList(), s)
        assertEquals(1, ties.size)
    }
}
