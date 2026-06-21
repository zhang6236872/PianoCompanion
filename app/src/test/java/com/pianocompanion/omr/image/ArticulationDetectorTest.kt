package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Articulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ArticulationDetector] using synthetic binary images.
 *
 * **Staccato dots** (•) are small compact blobs placed above or below a notehead
 * (opposite side from the stem), distinct from augmentation dots which are to the right.
 *
 * **Tenuto lines** (—) are short horizontal lines (much wider than tall) in the same
 * position region.
 *
 * **Accent wedges** (>) are hollow/triangular marks with lower fill ratio than a
 * solid dot.
 */
class ArticulationDetectorTest {

    private val width = 300
    private val height = 120
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /**
     * Draw a small filled square (staccato dot / augmentation dot).
     */
    private fun drawDot(img: BinaryImage, cx: Int, cy: Int, r: Int = 1) {
        for (y in (cy - r)..(cy + r)) {
            for (x in (cx - r)..(cx + r)) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }
    }

    /**
     * Draw a notehead-sized filled ellipse.
     */
    private fun drawNotehead(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val ndx = (x - cx).toDouble() / rx
                val ndy = (y - cy).toDouble() / ry
                if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
            }
        }
    }

    private fun makeNh(cx: Int, cy: Int, w: Int = 9, h: Int = 7): Notehead =
        Notehead(cx, cy, w, h, w * h)

    /** Rhythm features for a stem-up note (search below for staccato dot). */
    private fun stemUpFeatures() = RhythmFeatures(
        filled = true, hasStem = true, stemUp = true,
        stemEndX = 0, stemEndY = 0, beamCount = 0, flagCount = 0,
        dotCount = 0, duration = NoteDuration.QUARTER
    )

    /** Rhythm features for a stem-down note (search above for staccato dot). */
    private fun stemDownFeatures() = RhythmFeatures(
        filled = true, hasStem = true, stemUp = false,
        stemEndX = 0, stemEndY = 0, beamCount = 0, flagCount = 0,
        dotCount = 0, duration = NoteDuration.QUARTER
    )

    /** Rhythm features for a whole note (no stem → search both sides). */
    private fun noStemFeatures() = RhythmFeatures(
        filled = false, hasStem = false, stemUp = false,
        stemEndX = 0, stemEndY = 0, beamCount = 0, flagCount = 0,
        dotCount = 0, duration = NoteDuration.WHOLE
    )

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `staccato dot below stem-up notehead is detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Dot ~1.5 spacings below notehead center (cy=50, dot at y=65)
        drawDot(img, 100, 65)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0), result)
    }

    @Test
    fun `no staccato dot returns empty set`() {
        val img = blank()
        drawNotehead(img, 100, 50)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Should detect no staccato", result.isEmpty())
    }

    @Test
    fun `staccato dot above stem-down notehead is detected`() {
        val img = blank()
        drawNotehead(img, 100, 70)
        // Dot above the notehead (stem goes down, dot on opposite side = above)
        drawDot(img, 100, 55)

        val nhs = listOf(makeNh(100, 70))
        val rhythms = listOf(stemDownFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0), result)
    }

    @Test
    fun `staccato dot above whole note (no stem) is detected`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        // Dot above for a whole note (search both sides)
        drawDot(img, 100, 45)

        val nhs = listOf(makeNh(100, 60))
        val rhythms = listOf(noStemFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0), result)
    }

    @Test
    fun `staccato dot below whole note (no stem) is detected`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawDot(img, 100, 75)

        val nhs = listOf(makeNh(100, 60))
        val rhythms = listOf(noStemFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0), result)
    }

    // ---- Disambiguation from augmentation dots ------------------------------

    @Test
    fun `augmentation dot to the right is not detected as staccato`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Augmentation dot: to the RIGHT at same Y level
        drawDot(img, 112, 50)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Augmentation dot should not trigger staccato", result.isEmpty())
    }

    @Test
    fun `both augmentation dot and staccato dot coexist`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawDot(img, 112, 50)   // augmentation dot (right)
        drawDot(img, 100, 65)   // staccato dot (below)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals("Staccato detected despite augmentation dot", setOf(0), result)
    }

    // ---- Multiple noteheads -------------------------------------------------

    @Test
    fun `multiple noteheads with selective staccato`() {
        val img = blank()
        drawNotehead(img, 80, 50)
        drawNotehead(img, 160, 50)
        drawNotehead(img, 240, 50)
        // Only first and third have staccato dots
        drawDot(img, 80, 65)
        drawDot(img, 240, 65)

        val nhs = listOf(makeNh(80, 50), makeNh(160, 50), makeNh(240, 50))
        val rhythms = listOf(stemUpFeatures(), stemUpFeatures(), stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0, 2), result)
    }

    // ---- Negative cases (no false positives) --------------------------------

    @Test
    fun `dot too far below is not detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Dot at 3+ spacings below — outside search range
        drawDot(img, 100, 90)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Dot too far away should not be detected", result.isEmpty())
    }

    @Test
    fun `single noise pixel is not detected as staccato`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Single pixel (area=1, below minPixels=2)
        img.set(100, 68, true)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Single pixel should not be staccato", result.isEmpty())
    }

    @Test
    fun `dot offset to the side is not detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Dot well to the side of the notehead column
        drawDot(img, 115, 65)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Off-column dot should not be staccato", result.isEmpty())
    }

    @Test
    fun `tall vertical line below notehead is not staccato`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Draw a tall vertical line (like a stem segment) below the notehead
        for (y in 65..90) img.set(100, y, true)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Tall line should not be detected as staccato dot", result.isEmpty())
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    fun `empty notehead list returns empty set`() {
        val img = blank()
        val result = ArticulationDetector.detectStaccato(img, emptyList(), emptyList(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty set`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawDot(img, 100, 65)
        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty rhythms list searches both sides`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawDot(img, 100, 75) // below

        val nhs = listOf(makeNh(100, 60))
        val result = ArticulationDetector.detectStaccato(img, nhs, emptyList(), s)

        assertEquals(setOf(0), result)
    }

    @Test
    fun `staccato dot at minimum search distance is detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Notehead bottom edge ~ y=53. Search starts at 53 + 4 = 57.
        // Dot at y=58 (barely within range).
        drawDot(img, 100, 58)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0), result)
    }

    @Test
    fun `staccato dot at maximum search distance is detected`() {
        val img = blank()
        drawNotehead(img, 100, 30)
        // Notehead bottom edge ~ y=33. Search ends at 33 + 4 + 20 = 57.
        // Dot at y=55 (near the edge of range).
        drawDot(img, 100, 55)

        val nhs = listOf(makeNh(100, 30))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertEquals(setOf(0), result)
    }

    @Test
    fun `wide horizontal line below notehead is not staccato`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Wide horizontal line (like a ledger line remnant)
        for (x in 92..108) img.set(x, 68, true)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)

        assertTrue("Wide line should not be detected as staccato", result.isEmpty())
    }

    // ========================================================================
    //  Tenuto detection
    // ========================================================================

    /** Draw a tenuto mark (short horizontal line) centered at (cx, cy). */
    private fun drawTenuto(img: BinaryImage, cx: Int, cy: Int, halfWidth: Int = 4, thickness: Int = 2) {
        for (y in cy..(cy + thickness - 1)) {
            for (x in (cx - halfWidth)..(cx + halfWidth)) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }
    }

    @Test
    fun `tenuto line below stem-up notehead is detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Tenuto: 9px wide, 2px tall → AR ≈ 4.5 (clearly horizontal line)
        drawTenuto(img, 100, 62, halfWidth = 4, thickness = 2)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.TENUTO), result)
    }

    @Test
    fun `tenuto line above stem-down notehead is detected`() {
        val img = blank()
        drawNotehead(img, 100, 70)
        drawTenuto(img, 100, 50, halfWidth = 4, thickness = 2)

        val nhs = listOf(makeNh(100, 70))
        val rhythms = listOf(stemDownFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.TENUTO), result)
    }

    @Test
    fun `tenuto line above whole note (no stem) is detected`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawTenuto(img, 100, 44, halfWidth = 4, thickness = 2)

        val nhs = listOf(makeNh(100, 60))
        val rhythms = listOf(noStemFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.TENUTO), result)
    }

    @Test
    fun `thin single-row tenuto line is detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // Single-row line, 8px wide → AR = 8 (very clear horizontal line)
        drawTenuto(img, 100, 62, halfWidth = 4, thickness = 1)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.TENUTO), result)
    }

    // ========================================================================
    //  Accent detection
    // ========================================================================

    /**
     * Draw an accent mark (wedge ">") centered horizontally at cx, top at cy.
     * The shape is a downward-pointing triangle: two strokes converging to a point.
     * Bounding box 5×3, pixel count 5, fill ratio ≈ 0.33 (well below solid-dot fill).
     */
    private fun drawAccent(img: BinaryImage, cx: Int, cy: Int) {
        // Row 0: X...X  (2 pixels)
        // Row 1: .X.X.  (2 pixels)  — gap in the middle creates low fill ratio
        // Row 2: ..X..  (1 pixel)
        val points = listOf(
            cx - 2 to cy, cx + 2 to cy,
            cx - 1 to cy + 1, cx + 1 to cy + 1,
            cx to cy + 2
        )
        for ((px, py) in points) {
            if (px in 0 until width && py in 0 until height) img.set(px, py, true)
        }
    }

    @Test
    fun `accent wedge below stem-up notehead is detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawAccent(img, 100, 62)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.ACCENT), result)
    }

    @Test
    fun `accent wedge above stem-down notehead is detected`() {
        val img = blank()
        drawNotehead(img, 100, 70)
        // Accent above notehead (stem goes down, search above)
        drawAccent(img, 100, 50)

        val nhs = listOf(makeNh(100, 70))
        val rhythms = listOf(stemDownFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.ACCENT), result)
    }

    @Test
    fun `accent wedge above whole note (no stem) is detected`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawAccent(img, 100, 42)

        val nhs = listOf(makeNh(100, 60))
        val rhythms = listOf(noStemFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(mapOf(0 to Articulation.ACCENT), result)
    }

    // ========================================================================
    //  Disambiguation: staccato vs tenuto vs accent
    // ========================================================================

    @Test
    fun `staccato dot tenuto line and accent wedge on separate noteheads`() {
        val img = blank()
        drawNotehead(img, 60, 50)    // staccato
        drawNotehead(img, 140, 50)   // tenuto
        drawNotehead(img, 220, 50)   // accent

        drawDot(img, 60, 65)         // 3×3 solid dot
        drawTenuto(img, 140, 65, halfWidth = 4, thickness = 2)  // 9×2 line
        drawAccent(img, 220, 65)     // 5×3 wedge

        val nhs = listOf(makeNh(60, 50), makeNh(140, 50), makeNh(220, 50))
        val rhythms = listOf(stemUpFeatures(), stemUpFeatures(), stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(
            mapOf(0 to Articulation.STACCATO, 1 to Articulation.TENUTO, 2 to Articulation.ACCENT),
            result
        )
    }

    @Test
    fun `tenuto line is not classified as staccato`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawTenuto(img, 100, 62)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        // detectStaccato should NOT include this note (it's tenuto, not staccato)
        val staccatoResult = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)
        assertTrue("Tenuto should not appear in staccato set", staccatoResult.isEmpty())

        // detectArticulations should classify it as TENUTO
        val artResult = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)
        assertEquals(Articulation.TENUTO, artResult[0])
    }

    @Test
    fun `accent wedge is not classified as staccato`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawAccent(img, 100, 62)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val staccatoResult = ArticulationDetector.detectStaccato(img, nhs, rhythms, s)
        assertTrue("Accent should not appear in staccato set", staccatoResult.isEmpty())

        val artResult = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)
        assertEquals(Articulation.ACCENT, artResult[0])
    }

    @Test
    fun `no articulation mark returns empty map`() {
        val img = blank()
        drawNotehead(img, 100, 50)

        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertTrue(result.isEmpty())
    }

    // ========================================================================
    //  Edge cases
    // ========================================================================

    @Test
    fun `detectArticulations empty noteheads returns empty map`() {
        val img = blank()
        val result = ArticulationDetector.detectArticulations(img, emptyList(), emptyList(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectArticulations zero line spacing returns empty map`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawDot(img, 100, 65)
        val nhs = listOf(makeNh(100, 50))
        val rhythms = listOf(stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `selective articulations across multiple noteheads`() {
        val img = blank()
        drawNotehead(img, 80, 50)    // staccato
        drawNotehead(img, 160, 50)   // no mark
        drawNotehead(img, 240, 50)   // tenuto

        drawDot(img, 80, 65)
        drawTenuto(img, 240, 65, halfWidth = 4, thickness = 2)

        val nhs = listOf(makeNh(80, 50), makeNh(160, 50), makeNh(240, 50))
        val rhythms = listOf(stemUpFeatures(), stemUpFeatures(), stemUpFeatures())
        val result = ArticulationDetector.detectArticulations(img, nhs, rhythms, s)

        assertEquals(
            mapOf(0 to Articulation.STACCATO, 2 to Articulation.TENUTO),
            result
        )
    }

    @Test
    fun `empty rhythms list searches both sides for articulations`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawTenuto(img, 100, 75, halfWidth = 4, thickness = 2)

        val nhs = listOf(makeNh(100, 60))
        val result = ArticulationDetector.detectArticulations(img, nhs, emptyList(), s)

        assertEquals(mapOf(0 to Articulation.TENUTO), result)
    }
}
