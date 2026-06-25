package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TremoloDetector] using synthetic binary images.
 *
 * A **tremolo** (颤音记号/震音) is 2–3 short diagonal strokes drawn through a
 * note's stem, indicating the note should be played as rapid repeated notes.
 * These tests draw noteheads, stems, and tremolo slashes pixel-by-pixel, then
 * verify detection, slash counting, direction handling, and exclusion rules.
 */
class TremoloDetectorTest {

    private val width = 400
    private val height = 100
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** Construct a Notehead at the given center. */
    private fun makeNh(cx: Int, cy: Int, w: Int = 9, h: Int = 7): Notehead =
        Notehead(cx, cy, w, h, w * h)

    /** Construct RhythmFeatures with stem info. */
    private fun rhythm(
        hasStem: Boolean = true,
        stemUp: Boolean = true,
        stemEndX: Int = 0,
        stemEndY: Int = 0,
        beamCount: Int = 0
    ): RhythmFeatures = RhythmFeatures(
        filled = true,
        hasStem = hasStem,
        stemUp = stemUp,
        stemEndX = stemEndX,
        stemEndY = stemEndY,
        beamCount = beamCount,
        flagCount = 0,
        dotCount = 0,
        duration = NoteDuration.QUARTER
    )

    /** Draw a filled notehead ellipse. */
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

    /**
     * Draw a vertical stem at [stemX] spanning from [y1] to [y2].
     */
    private fun drawStem(img: BinaryImage, stemX: Int, y1: Int, y2: Int) {
        for (y in minOf(y1, y2)..maxOf(y1, y2)) {
            if (stemX in 0 until width && y in 0 until height) {
                img.set(stemX, y, true)
            }
        }
    }

    /**
     * Draw a tremolo slash (thick diagonal line) centered at ([cx], [cy]).
     * The slash goes from bottom-left to top-right at ~45°.
     *
     * @param halfLen half-length in pixels (total span ≈ 2 × halfLen).
     * @param thickness number of parallel diagonal lines for thickness.
     */
    private fun drawSlash(img: BinaryImage, cx: Int, cy: Int, halfLen: Int = 4, thickness: Int = 2) {
        for (offset in 0 until thickness) {
            for (t in -halfLen..halfLen) {
                val x = cx + t
                val y = cy - t + offset
                if (x in 0 until width && y in 0 until height) {
                    img.set(x, y, true)
                }
            }
        }
    }

    /**
     * Draw a tremolo slash going from top-left to bottom-right (negative slope).
     */
    private fun drawSlashNegative(img: BinaryImage, cx: Int, cy: Int, halfLen: Int = 4, thickness: Int = 2) {
        for (offset in 0 until thickness) {
            for (t in -halfLen..halfLen) {
                val x = cx + t
                val y = cy + t + offset
                if (x in 0 until width && y in 0 until height) {
                    img.set(x, y, true)
                }
            }
        }
    }

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `detects two-slash tremolo on up-stem`() {
        val img = blank()
        // Notehead at (200, 65), up-stem at x=204 from y=65 to y=15
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // Two slashes through the stem
        drawSlash(img, 204, 33)
        drawSlash(img, 204, 47)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(2, result[0].slashCount)
        assertEquals(0, result[0].noteheadIdx)
    }

    @Test
    fun `detects three-slash tremolo`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // Three slashes (spaced far enough to form separate bands)
        drawSlash(img, 204, 29)
        drawSlash(img, 204, 42)
        drawSlash(img, 204, 55)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(3, result[0].slashCount)
    }

    @Test
    fun `detects tremolo on down-stem`() {
        val img = blank()
        // Notehead at (200, 15), down-stem at x=196 from y=15 to y=65
        drawNotehead(img, 200, 15)
        drawStem(img, 196, 15, 65)
        // Two slashes through the down-stem
        drawSlash(img, 196, 33)
        drawSlash(img, 196, 47)

        val nhs = listOf(makeNh(200, 15))
        val rhythms = listOf(rhythm(stemUp = false, stemEndX = 196, stemEndY = 65))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(2, result[0].slashCount)
    }

    @Test
    fun `detects negative-slope slashes`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // Two negative-slope slashes (top-left to bottom-right)
        drawSlashNegative(img, 204, 33)
        drawSlashNegative(img, 204, 47)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(2, result[0].slashCount)
    }

    // ---- Negative cases -----------------------------------------------------

    @Test
    fun `bare stem without slashes returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `single slash does not trigger tremolo`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // Only one slash
        drawSlash(img, 204, 35)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `notehead without stem is skipped`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // No stem drawn, rhythm says no stem
        val nhs = listOf(makeNh(200, 50))
        val rhythms = listOf(rhythm(hasStem = false))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `beamed notehead is skipped`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        drawSlash(img, 204, 33)
        drawSlash(img, 204, 47)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15, beamCount = 1))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `short stem is skipped`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Very short stem (12px = 1.2s, below MIN_STEM_LEN_FRAC=1.5s=15px)
        drawStem(img, 204, 50, 38)
        drawSlash(img, 204, 42)
        drawSlash(img, 204, 46)

        val nhs = listOf(makeNh(200, 50))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 38))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertTrue(result.isEmpty())
    }

    // ---- Multi-notehead / independence --------------------------------------

    @Test
    fun `multiple tremolos detected independently`() {
        val img = blank()
        // Note 1 at x=100 with tremolo
        drawNotehead(img, 100, 65)
        drawStem(img, 104, 65, 15)
        drawSlash(img, 104, 33)
        drawSlash(img, 104, 47)
        // Note 2 at x=300 with tremolo
        drawNotehead(img, 300, 65)
        drawStem(img, 304, 65, 15)
        drawSlash(img, 304, 33)
        drawSlash(img, 304, 47)

        val nhs = listOf(makeNh(100, 65), makeNh(300, 65))
        val rhythms = listOf(
            rhythm(stemUp = true, stemEndX = 104, stemEndY = 15),
            rhythm(stemUp = true, stemEndX = 304, stemEndY = 15)
        )
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(2, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(1, result[1].noteheadIdx)
    }

    @Test
    fun `tremolo and non-tremolo notes coexist`() {
        val img = blank()
        // Note 1 with tremolo
        drawNotehead(img, 100, 65)
        drawStem(img, 104, 65, 15)
        drawSlash(img, 104, 33)
        drawSlash(img, 104, 47)
        // Note 2 without tremolo
        drawNotehead(img, 300, 65)
        drawStem(img, 304, 65, 15)

        val nhs = listOf(makeNh(100, 65), makeNh(300, 65))
        val rhythms = listOf(
            rhythm(stemUp = true, stemEndX = 104, stemEndY = 15),
            rhythm(stemUp = true, stemEndX = 304, stemEndY = 15)
        )
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(0, result[0].noteheadIdx)
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    fun `empty notehead list returns empty`() {
        val img = blank()
        val result = TremoloDetector.detect(img, emptyList(), emptyList(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `barline far from stem does not cause false detection`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // Draw a solid barline at x=350 (far from stem at x=204)
        for (y in 10..80) {
            for (x in 349..352) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        // No tremolo slashes on the stem → empty
        assertTrue(result.isEmpty())
    }

    @Test
    fun `wider slash spacing still detected`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // Slashes widely spaced
        drawSlash(img, 204, 28, halfLen = 5)
        drawSlash(img, 204, 52, halfLen = 5)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(2, result[0].slashCount)
    }

    @Test
    fun `thicker slashes detected`() {
        val img = blank()
        drawNotehead(img, 200, 65)
        drawStem(img, 204, 65, 15)
        // 3px-thick slashes
        drawSlash(img, 204, 33, thickness = 3)
        drawSlash(img, 204, 47, thickness = 3)

        val nhs = listOf(makeNh(200, 65))
        val rhythms = listOf(rhythm(stemUp = true, stemEndX = 204, stemEndY = 15))
        val result = TremoloDetector.detect(img, nhs, rhythms, s)

        assertEquals(1, result.size)
        assertEquals(2, result[0].slashCount)
    }
}
