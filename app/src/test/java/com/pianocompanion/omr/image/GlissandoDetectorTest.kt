package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GlissandoDetector] using synthetic binary images.
 *
 * A **glissando** (滑音/刮奏) is a diagonal line connecting two notes, indicating
 * the player should slide rapidly between them. In piano music this produces a
 * cascade of rapid onsets as the finger slides across keys.
 *
 * These tests draw noteheads and glissando lines pixel-by-pixel, then verify
 * detection based on geometry (pitch difference, horizontal gap), ink coverage
 * along the diagonal path, and exclusion rules.
 */
class GlissandoDetectorTest {

    private val width = 400
    private val height = 150
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    private fun makeNh(cx: Int, cy: Int, w: Int = 9, h: Int = 7): Notehead =
        Notehead(cx, cy, w, h, w * h)

    /**
     * Draw a straight diagonal line from (x0, y0) to (x1, y1).
     * Uses Bresenham-style sampling; each point draws a small blob for thickness.
     */
    private fun drawDiagonalLine(
        img: BinaryImage,
        x0: Int, y0: Int,
        x1: Int, y1: Int,
        thickness: Int = 1
    ) {
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val steps = kotlin.math.max(dx, dy)
        if (steps == 0) return
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val cx = (x0 + t * (x1 - x0)).toInt()
            val cy = (y0 + t * (y1 - y0)).toInt()
            for (ty in (cy - thickness)..(cy + thickness)) {
                for (tx in (cx - thickness)..(cx + thickness)) {
                    if (tx in 0 until width && ty in 0 until height) {
                        img.set(tx, ty, true)
                    }
                }
            }
        }
    }

    /**
     * Draw a wavy diagonal line (slight oscillation perpendicular to the main
     * direction) to simulate a hand-drawn glissando squiggle.
     */
    private fun drawWavyDiagonal(
        img: BinaryImage,
        x0: Int, y0: Int,
        x1: Int, y1: Int,
        amplitude: Int = 2
    ) {
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val steps = kotlin.math.max(dx, dy)
        if (steps == 0) return
        val signY = if (y1 > y0) 1 else -1
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val cx = (x0 + t * (x1 - x0)).toInt()
            val cy = (y0 + t * (y1 - y0)).toInt()
            // Perpendicular offset oscillates
            val offset = ((i / 3) % (2 * amplitude + 1)) - amplitude
            // Offset perpendicular to diagonal: shift both X and Y
            val px = cx - offset * signY
            val py = cy + offset
            if (px in 0 until width && py in 0 until height) {
                img.set(px, py, true)
            }
            if (px + 1 in 0 until width && py in 0 until height) {
                img.set(px + 1, py, true)
            }
        }
    }

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `detects ascending glissando line between two notes`() {
        val img = blank()
        // Note A at x=100, y=80 (low pitch); Note B at x=190, y=40 (high pitch)
        // dy=40px=4*s, dx=90px=9*s (< 10*s max gap)
        val nhs = listOf(
            makeNh(100, 80),   // low note
            makeNh(190, 40)    // high note (4 line-spacings above)
        )
        // Draw diagonal line from right edge of A to left edge of B
        drawDiagonalLine(img, 104, 80, 186, 40)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertEquals(1, result.size)
        assertEquals(0, result[0].fromNoteheadIdx)
        assertEquals(1, result[0].toNoteheadIdx)
    }

    @Test
    fun `detects descending glissando line`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 40),   // high note
            makeNh(190, 80)    // low note (4 line-spacings below)
        )
        drawDiagonalLine(img, 104, 40, 186, 80)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertEquals(1, result.size)
    }

    @Test
    fun `detects wavy glissando line`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        drawWavyDiagonal(img, 104, 80, 186, 40, amplitude = 2)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertEquals(1, result.size)
    }

    @Test
    fun `no glissando line returns empty`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        // No line drawn between notes
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    // --- Pitch difference constraints ---------------------------------------

    @Test
    fun `notes too close in pitch do not trigger`() {
        val img = blank()
        // dy=10px=1*s < MIN_PITCH_DIFF_FRAC=1.5*s=15px
        val nhs = listOf(
            makeNh(100, 50),
            makeNh(190, 60)   // only 1 line-spacing apart
        )
        drawDiagonalLine(img, 104, 50, 186, 60)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `notes at minimum pitch difference are detected`() {
        val img = blank()
        // dy=16px ≈ 1.6*s > MIN_PITCH_DIFF_FRAC=1.5*s=15px
        val nhs = listOf(
            makeNh(100, 60),
            makeNh(190, 44)
        )
        drawDiagonalLine(img, 104, 60, 186, 44)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertEquals(1, result.size)
    }

    // --- Horizontal gap constraints ------------------------------------------

    @Test
    fun `chord members at same X do not trigger`() {
        val img = blank()
        // dx=0 — chord members, not sequential notes
        val nhs = listOf(
            makeNh(200, 40),
            makeNh(200, 80)
        )
        drawDiagonalLine(img, 196, 40, 196, 80)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `notes too far apart horizontally do not trigger`() {
        val img = blank()
        // dx=350px=35*s > MAX_GAP_FRAC=10*s=100px
        val nhs = listOf(
            makeNh(20, 80),
            makeNh(370, 40)
        )
        drawDiagonalLine(img, 24, 80, 366, 40)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    // --- Ink coverage constraints --------------------------------------------

    @Test
    fun `sparse ink between notes does not trigger`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        // Draw only a few scattered pixels (not a continuous line)
        img.set(120, 72, true)
        img.set(140, 65, true)
        img.set(160, 58, true)
        img.set(170, 52, true)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `broken line with large gaps does not trigger`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        // Draw line only in first ~30% of the path, leave rest empty
        drawDiagonalLine(img, 104, 80, 125, 67)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `vertical stem between notes does not trigger glissando`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        // Draw a vertical stem at x=145 (not a diagonal line)
        for (y in 40..80) {
            img.set(145, y, true)
        }
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        // A vertical line along the diagonal path gives low coverage
        assertTrue(result.isEmpty())
    }

    @Test
    fun `horizontal beam between notes does not trigger glissando`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        // Draw a horizontal beam at y=60 (not a diagonal line)
        for (x in 104..186) {
            img.set(x, 60, true)
        }
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        // A horizontal line along the diagonal path gives low coverage
        assertTrue(result.isEmpty())
    }

    // --- Multi-note / multi-system -------------------------------------------

    @Test
    fun `multiple glissandos detected independently`() {
        val img = blank()
        // Glissando 1: x=50→150, y=80→40
        val nhs = listOf(
            makeNh(50, 80),
            makeNh(150, 40),
            // Glissando 2: x=250→350, y=90→50
            makeNh(250, 90),
            makeNh(350, 50)
        )
        drawDiagonalLine(img, 54, 80, 146, 40)
        drawDiagonalLine(img, 254, 90, 346, 50)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0, 0, 0), s)
        assertEquals(2, result.size)
    }

    @Test
    fun `glissando in one pair does not affect adjacent pair`() {
        val img = blank()
        // Three sequential notes: A→B has glissando, B→C does not
        val nhs = listOf(
            makeNh(50, 80),
            makeNh(150, 40),
            makeNh(250, 50)   // C is close in pitch to B (no glissando)
        )
        drawDiagonalLine(img, 54, 80, 146, 40)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0, 0), s)
        assertEquals(1, result.size)
        assertEquals(0, result[0].fromNoteheadIdx)
        assertEquals(1, result[0].toNoteheadIdx)
    }

    @Test
    fun `different systems detect independently`() {
        val img = blank()
        // System 0: A→B with glissando
        // System 1: C→D without glissando
        val nhs = listOf(
            makeNh(50, 80),
            makeNh(150, 40),
            makeNh(50, 130),   // System 1
            makeNh(150, 100)
        )
        drawDiagonalLine(img, 54, 80, 146, 40)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0, 1, 1), s)
        assertEquals(1, result.size)
    }

    @Test
    fun `glissando detected with three sequential notes`() {
        val img = blank()
        // Three descending notes: A(high) → B(mid) → C(low), each pair has glissando
        val nhs = listOf(
            makeNh(50, 30),
            makeNh(130, 60),
            makeNh(210, 90)
        )
        drawDiagonalLine(img, 54, 30, 126, 60)
        drawDiagonalLine(img, 134, 60, 206, 90)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0, 0), s)
        assertEquals(2, result.size)
        assertEquals(0, result[0].fromNoteheadIdx)
        assertEquals(1, result[0].toNoteheadIdx)
        assertEquals(1, result[1].fromNoteheadIdx)
        assertEquals(2, result[1].toNoteheadIdx)
    }

    // --- Edge cases ----------------------------------------------------------

    @Test
    fun `single notehead returns empty`() {
        val img = blank()
        val nhs = listOf(makeNh(100, 50))
        val result = GlissandoDetector.detect(img, nhs, listOf(0), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty notehead list returns empty`() {
        val img = blank()
        val result = GlissandoDetector.detect(img, emptyList(), emptyList(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        val nhs = listOf(makeNh(100, 80), makeNh(190, 40))
        drawDiagonalLine(img, 104, 80, 186, 40)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `glissando with thicker line detected`() {
        val img = blank()
        val nhs = listOf(
            makeNh(100, 80),
            makeNh(190, 40)
        )
        drawDiagonalLine(img, 104, 80, 186, 40, thickness = 2)
        val result = GlissandoDetector.detect(img, nhs, listOf(0, 0), s)
        assertEquals(1, result.size)
    }
}
