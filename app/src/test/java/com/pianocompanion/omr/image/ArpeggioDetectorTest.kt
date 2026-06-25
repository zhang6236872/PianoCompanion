package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ArpeggioDetector] using synthetic binary images.
 *
 * An **arpeggio** (琶音 / rolled chord) is a vertical wavy line placed before a chord,
 * indicating the notes should be played sequentially from bottom to top rather than
 * simultaneously. These tests draw noteheads and arpeggio lines pixel-by-pixel,
 * then verify detection based on geometry, position, and exclusion rules.
 */
class ArpeggioDetectorTest {

    private val width = 400
    private val height = 120
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** Staff lines at y = 30,40,50,60,70 → spacing = 10. */
    private val lineYs = listOf(30, 40, 50, 60, 70)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /** Draw a filled notehead-sized ellipse. */
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
     * Draw a vertical arpeggio line (wavy/zigzag) at approximately [x], spanning
     * from [topY] to [bottomY]. Uses a zigzag pattern to simulate the wavy line.
     *
     * @param wavy if true, draws a zigzag (classic arpeggio squiggle);
     *             if false, draws a straight vertical line
     */
    private fun drawArpeggioLine(
        img: BinaryImage, x: Int, topY: Int, bottomY: Int,
        wavy: Boolean = true, amplitude: Int = 2
    ) {
        for (y in topY..bottomY) {
            if (y !in 0 until height) continue
            if (wavy) {
                // Zigzag: horizontal offset oscillates with y
                val offset = ((y - topY) / 3 % (2 * amplitude + 1)) - amplitude
                val px = x + offset
                if (px in 0 until width) img.set(px, y, true)
            } else {
                if (x in 0 until width) img.set(x, y, true)
            }
        }
    }

    /** Convenience: build blobs from image with a sensible minimum area. */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `detects wavy arpeggio line before chord`() {
        val img = blank()
        // Chord: 3 noteheads at x=200, y=35,50,65 (span=30px = 3 staff spacings)
        val nhs = listOf(
            makeNh(200, 35),
            makeNh(200, 50),
            makeNh(200, 65)
        )
        // Arpeggio line at x=188, spanning y=33..67
        drawArpeggioLine(img, 188, 33, 67, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0, 0), s)
        assertEquals(1, result.size)
        assertEquals(3, result[0].noteheadIndices.size)
    }

    @Test
    fun `detects straight vertical arpeggio line`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 60)
        )
        drawArpeggioLine(img, 190, 38, 62, wavy = false)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        assertEquals(1, result.size)
        assertEquals(2, result[0].noteheadIndices.size)
    }

    @Test
    fun `no arpeggio line returns empty`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 60)
        )
        // No arpeggio line drawn
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    // ---- Size / geometry constraints ---------------------------------------

    @Test
    fun `single notehead does not trigger arpeggio`() {
        val img = blank()
        val nhs = listOf(makeNh(200, 50))
        drawArpeggioLine(img, 190, 45, 55, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `two noteheads too close vertically do not trigger`() {
        val img = blank()
        // Span = 8px < 1.5*s = 15px
        val nhs = listOf(
            makeNh(200, 46),
            makeNh(200, 54)
        )
        drawArpeggioLine(img, 190, 44, 56, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `arpeggio line too far left is not detected`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 60)
        )
        // Line at x=170, which is 30px left of chord left edge (~195)
        // 30px = 3.0 * s > SEARCH_RANGE_FRAC=1.5*s=15px
        drawArpeggioLine(img, 170, 38, 62, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `arpeggio line too close to noteheads is not detected`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 60)
        )
        // Line at x=197, only 3px left of notehead center
        // chord left edge ≈ 200-4 = 196, line at 197 is RIGHT of left edge → outside search
        drawArpeggioLine(img, 197, 38, 62, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    // ---- Bar line exclusion ------------------------------------------------

    @Test
    fun `solid bar line is not mistaken for arpeggio`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 60)
        )
        // Draw a solid bar line (fill rate ~1.0): full width column
        for (y in 20..80) {
            for (x in 191..194) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        // Bar line has fill rate > 0.65, should be excluded
        assertTrue(result.isEmpty())
    }

    // ---- Multi-chord / multi-system ----------------------------------------

    @Test
    fun `two arpeggiated chords detected independently`() {
        val img = blank()
        // Chord 1 at x=100
        drawNotehead(img, 100, 35)
        drawNotehead(img, 100, 60)
        drawArpeggioLine(img, 88, 33, 62, wavy = true)
        // Chord 2 at x=300
        drawNotehead(img, 300, 35)
        drawNotehead(img, 300, 60)
        drawArpeggioLine(img, 288, 33, 62, wavy = true)
        val bl = blobs(img)
        val nhs = listOf(
            makeNh(100, 35), makeNh(100, 60),
            makeNh(300, 35), makeNh(300, 60)
        )
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0, 0, 0), s)
        assertEquals(2, result.size)
    }

    @Test
    fun `arpeggio in one chord does not affect adjacent non-arpeggiated chord`() {
        val img = blank()
        // Arpeggiated chord at x=100
        drawNotehead(img, 100, 35)
        drawNotehead(img, 100, 60)
        drawArpeggioLine(img, 88, 33, 62, wavy = true)
        // Non-arpeggiated chord at x=300
        drawNotehead(img, 300, 35)
        drawNotehead(img, 300, 60)
        val bl = blobs(img)
        val nhs = listOf(
            makeNh(100, 35), makeNh(100, 60),
            makeNh(300, 35), makeNh(300, 60)
        )
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0, 0, 0), s)
        assertEquals(1, result.size)
        // Only the first 2 noteheads should be in the arpeggio
        assertEquals(2, result[0].noteheadIndices.size)
    }

    @Test
    fun `different systems detect independently`() {
        val img = blank()
        // System 0 chord at x=100
        val nhs = listOf(
            makeNh(100, 35), makeNh(100, 60),
            makeNh(100, 85), makeNh(100, 110)  // System 1
        )
        // Arpeggio only for system 0
        drawArpeggioLine(img, 88, 33, 62, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0, 1, 1), s)
        assertEquals(1, result.size)
        assertEquals(2, result[0].noteheadIndices.size)
    }

    // ---- Edge cases --------------------------------------------------------

    @Test
    fun `empty notehead list returns empty`() {
        val img = blank()
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, emptyList(), emptyList(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        val nhs = listOf(makeNh(200, 40), makeNh(200, 60))
        drawArpeggioLine(img, 190, 38, 62, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `noteheads at different X are not treated as chord`() {
        val img = blank()
        // Noteheads at different X positions (not a chord)
        val nhs = listOf(
            makeNh(100, 40),
            makeNh(200, 60)
        )
        drawArpeggioLine(img, 88, 38, 62, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        // X difference is 100px = 10*s >> xTolerance, not a chord
        assertTrue(result.isEmpty())
    }

    @Test
    fun `arpeggio with wide span (5 noteheads) detected`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 30),
            makeNh(200, 40),
            makeNh(200, 50),
            makeNh(200, 60),
            makeNh(200, 70)
        )
        drawArpeggioLine(img, 188, 28, 72, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0, 0, 0, 0), s)
        assertEquals(1, result.size)
        assertEquals(5, result[0].noteheadIndices.size)
    }

    @Test
    fun `stem-like blob is not mistaken for arpeggio when no chord overlap`() {
        val img = blank()
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 60)
        )
        // Draw a vertical line far to the left, not overlapping chord vertically
        drawArpeggioLine(img, 190, 0, 10, wavy = true)
        val bl = blobs(img)
        val result = ArpeggioDetector.detect(bl, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }
}
