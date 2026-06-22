package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SlurDetector] using synthetic binary images.
 *
 * A **slur** (连音) is a thin curved arc connecting noteheads of **different**
 * pitch, indicating legato playing. These tests draw noteheads and arcs
 * pixel-by-pixel, then verify the detector correctly identifies (or rejects)
 * slurs based on pitch difference, arc coverage, and gap constraints.
 *
 * Key difference from [TieDetectorTest]: slurs connect notes at **different**
 * Y positions, so the arc slopes between them. Tests draw arcs that follow
 * this slope.
 */
class SlurDetectorTest {

    private val width = 400
    private val height = 160
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
     * Draw a thin (2 px) slur arc from (x1, y1) to (x2, y2), arcing above or
     * below the line connecting the two endpoints. Uses a half-sine bulge
     * superimposed on the linear interpolation between y1 and y2, mimicking a
     * real slur curve between notes at different heights.
     */
    private fun drawSlurArc(
        img: BinaryImage,
        x1: Int, y1: Int,
        x2: Int, y2: Int,
        maxBulge: Int,
        above: Boolean
    ) {
        val dir = if (above) -1 else 1
        val span = (x2 - x1).coerceAtLeast(1)
        for (x in x1..x2) {
            val t = (x - x1).toDouble() / span
            // Linear interpolation between the two endpoint Ys.
            val baseY = (y1 + (y2 - y1) * t).toInt()
            // Half-sine bulge outward.
            val bulge = (maxBulge * kotlin.math.sin(Math.PI * t)).toInt()
            val arcY = baseY + dir * bulge
            if (arcY in 0 until height) {
                img.set(x, arcY, true)
                if (arcY + 1 in 0 until height) img.set(x, arcY + 1, true)
            }
        }
    }

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `slur arc above two different-pitch noteheads is detected`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70) // Y differs by 30 > 0.5*s=5 → different pitch
        drawSlurArc(img, 85, 40, 195, 70, maxBulge = 12, above = true)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, slurs.size)
        assertEquals(0, slurs[0].firstNoteIdx)
        assertEquals(1, slurs[0].lastNoteIdx)
    }

    @Test
    fun `slur arc below two different-pitch noteheads is detected`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)
        drawSlurArc(img, 85, 40, 195, 70, maxBulge = 12, above = false)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, slurs.size)
    }

    @Test
    fun `no slur arc returns empty`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Different-pitch notes without arc should have no slur", slurs.isEmpty())
    }

    // ---- Pitch discrimination (complement of TieDetector) -------------------

    @Test
    fun `same-pitch noteheads with arc are not slur-detected`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 200, 50) // same Y → same pitch → tie candidate, not slur
        drawSlurArc(img, 85, 50, 195, 50, maxBulge = 12, above = false)

        val nhs = listOf(makeNh(80, 50), makeNh(200, 50))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Same-pitch notes should not be slur-detected", slurs.isEmpty())
    }

    @Test
    fun `ascending pitch slur detected`() {
        val img = blank()
        drawNotehead(img, 80, 60)
        drawNotehead(img, 200, 30) // second note is higher (smaller Y) → ascending
        drawSlurArc(img, 85, 60, 195, 30, maxBulge = 10, above = true)

        val nhs = listOf(makeNh(80, 60), makeNh(200, 30))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, slurs.size)
    }

    @Test
    fun `descending pitch slur detected`() {
        val img = blank()
        drawNotehead(img, 80, 30)
        drawNotehead(img, 200, 60) // second note is lower (larger Y) → descending
        drawSlurArc(img, 85, 30, 195, 60, maxBulge = 10, above = false)

        val nhs = listOf(makeNh(80, 30), makeNh(200, 60))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, slurs.size)
    }

    // ---- Gap constraints ----------------------------------------------------

    @Test
    fun `noteheads too close are not slurred`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 92, 70) // gap < 1.0*s = 10px → too close
        drawSlurArc(img, 85, 40, 87, 70, maxBulge = 8, above = true)

        val nhs = listOf(makeNh(80, 40), makeNh(92, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Noteheads too close should not be slurred", slurs.isEmpty())
    }

    // ---- Multi-note slurs (grouping) ---------------------------------------

    @Test
    fun `three-note ascending slur merged into single group`() {
        val img = blank()
        drawNotehead(img, 50, 70)
        drawNotehead(img, 150, 50)
        drawNotehead(img, 250, 30)
        // Arcs between each pair
        drawSlurArc(img, 55, 70, 145, 50, maxBulge = 8, above = true)
        drawSlurArc(img, 155, 50, 245, 30, maxBulge = 8, above = true)

        val nhs = listOf(makeNh(50, 70), makeNh(150, 50), makeNh(250, 30))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0, 0), s)

        assertEquals("Should merge into one slur group", 1, slurs.size)
        assertEquals(0, slurs[0].firstNoteIdx)
        assertEquals(2, slurs[0].lastNoteIdx)
    }

    @Test
    fun `two separate slurs remain as separate groups`() {
        val img = blank()
        drawNotehead(img, 40, 60)
        drawNotehead(img, 120, 40)
        // Gap between pair 1 and pair 2 (no arc)
        drawNotehead(img, 200, 50)
        drawNotehead(img, 280, 70)
        // Arcs only within each pair
        drawSlurArc(img, 45, 60, 115, 40, maxBulge = 8, above = true)
        drawSlurArc(img, 205, 50, 275, 70, maxBulge = 8, above = true)

        val nhs = listOf(
            makeNh(40, 60), makeNh(120, 40),
            makeNh(200, 50), makeNh(280, 70)
        )
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0, 0, 0), s)

        assertEquals(2, slurs.size)
        assertTrue(slurs.any { it.firstNoteIdx == 0 && it.lastNoteIdx == 1 })
        assertTrue(slurs.any { it.firstNoteIdx == 2 && it.lastNoteIdx == 3 })
    }

    // ---- Robustness: false positive rejection ------------------------------

    @Test
    fun `vertical stem between notes does not create false slur`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)
        // Draw a vertical stem at x=140 from y=20 to y=90.
        for (y in 20..90) img.set(140, y, true)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Single stem should not create false slur", slurs.isEmpty())
    }

    @Test
    fun `partial arc below coverage threshold is not detected`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)
        // Draw arc only in the first ~30% of the gap (insufficient coverage).
        drawSlurArc(img, 85, 40, 120, 52, maxBulge = 8, above = true)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Partial arc below 75% coverage should not be detected", slurs.isEmpty())
    }

    @Test
    fun `horizontal ledger line between notes does not create false slur`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)
        // Draw a short horizontal line at y=55, x=130..150 (20 px).
        for (x in 130..150) img.set(x, 55, true)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertTrue("Short ledger line should not create false slur", slurs.isEmpty())
    }

    // ---- Multi-system -------------------------------------------------------

    @Test
    fun `slurs only detected within same system, not across systems`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70) // different system
        drawSlurArc(img, 85, 40, 195, 70, maxBulge = 12, above = true)

        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 1), s)

        assertTrue("Cross-system noteheads should not be slurred", slurs.isEmpty())
    }

    @Test
    fun `slurs detected in parallel for different systems`() {
        val img = blank()
        // System 0: notes at y=40, y=60
        drawNotehead(img, 60, 40)
        drawNotehead(img, 160, 60)
        drawSlurArc(img, 65, 40, 155, 60, maxBulge = 8, above = true)
        // System 1: notes at y=100, y=120
        drawNotehead(img, 60, 100)
        drawNotehead(img, 160, 120)
        drawSlurArc(img, 65, 100, 155, 120, maxBulge = 8, above = true)

        val nhs = listOf(
            makeNh(60, 40), makeNh(160, 60),  // indices 0, 1 → system 0
            makeNh(60, 100), makeNh(160, 120)  // indices 2, 3 → system 1
        )
        val sysIdx = listOf(0, 0, 1, 1)
        val slurs = SlurDetector.detect(img, nhs, sysIdx, s)

        assertEquals(2, slurs.size)
        assertTrue(slurs.any { it.firstNoteIdx == 0 && it.lastNoteIdx == 1 })
        assertTrue(slurs.any { it.firstNoteIdx == 2 && it.lastNoteIdx == 3 })
        assertTrue(slurs.none { it.firstNoteIdx < 2 && it.lastNoteIdx >= 2 })
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    fun `single notehead returns no slurs`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        val nhs = listOf(makeNh(80, 50))
        val slurs = SlurDetector.detect(img, nhs, listOf(0), s)
        assertTrue(slurs.isEmpty())
    }

    @Test
    fun `empty noteheads returns no slurs`() {
        val img = blank()
        val slurs = SlurDetector.detect(img, emptyList(), emptyList(), s)
        assertTrue(slurs.isEmpty())
    }

    @Test
    fun `zero line spacing returns no slurs`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)
        drawSlurArc(img, 85, 40, 195, 70, maxBulge = 12, above = true)
        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), 0)
        assertTrue(slurs.isEmpty())
    }

    @Test
    fun `empty system indices defaults all to system 0`() {
        val img = blank()
        drawNotehead(img, 80, 40)
        drawNotehead(img, 200, 70)
        drawSlurArc(img, 85, 40, 195, 70, maxBulge = 12, above = true)
        val nhs = listOf(makeNh(80, 40), makeNh(200, 70))
        // Empty system indices → all treated as system 0 → slur should be detected.
        val slurs = SlurDetector.detect(img, nhs, emptyList(), s)
        assertEquals(1, slurs.size)
    }

    // ---- Large pitch difference --------------------------------------------

    @Test
    fun `large pitch difference slur detected`() {
        val img = blank()
        drawNotehead(img, 80, 30)
        drawNotehead(img, 200, 110) // Y diff = 80 = 8 line-spacings (over an octave)
        drawSlurArc(img, 85, 30, 195, 110, maxBulge = 15, above = true)

        val nhs = listOf(makeNh(80, 30), makeNh(200, 110))
        val slurs = SlurDetector.detect(img, nhs, listOf(0, 0), s)

        assertEquals(1, slurs.size)
    }
}
