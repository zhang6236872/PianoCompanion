package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OrnamentDetector] using synthetic binary images.
 *
 * **Ornaments** (装饰音) are short embellishment marks placed above a notehead:
 *
 * - **Mordent** (波音): a short zigzag/wavy line. **Upper mordent** (pralltriller)
 *   is a plain zigzag; **lower mordent** has a vertical slash through the centre.
 * - **Turn** (回音 / gruppetto): a horizontal S-curve (∽).
 *
 * These tests render zigzag patterns, S-curve shapes, and control shapes (domes,
 * dots, arcs) pixel-by-pixel, then verify the detector correctly classifies or
 * rejects them based on the per-column vertical-centre reversal analysis.
 */
class OrnamentDetectorTest {

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

    /** Convenience: build blobs from image. */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    /**
     * Draw a zigzag (sine-wave) line centred at (cx, cy) with the given width,
     * amplitude, and thickness. One full cycle produces 2 direction reversals.
     */
    private fun drawZigzag(
        img: BinaryImage, cx: Int, cy: Int, halfWidth: Int,
        amp: Int, thickness: Int
    ) {
        for (x in -halfWidth..halfWidth) {
            val px = cx + x
            if (px !in 0 until width) continue
            val phase = (x.toDouble() / halfWidth) * Math.PI
            val py = cy + (amp * kotlin.math.sin(phase)).toInt()
            for (dy in 0 until thickness) {
                val yy = py + dy
                if (yy in 0 until height) img.set(px, yy, true)
            }
        }
    }

    /**
     * Draw a vertical slash line through the given x, from yTop to yBottom.
     */
    private fun drawSlash(img: BinaryImage, x: Int, yTop: Int, yBottom: Int, thickness: Int = 1) {
        for (y in yTop..yBottom) {
            for (dx in 0 until thickness) {
                if (x + dx in 0 until width && y in 0 until height) {
                    img.set(x + dx, y, true)
                }
            }
        }
    }

    /**
     * Draw a turn (∽) as a thick S-curve path centred at (cx, cy).
     * The curve smoothly transitions from upper-left to lower-right using a
     * half-sine profile, producing a monotonic vertical-centre trajectory
     * with significant left-right asymmetry that the detector classifies as TURN.
     */
    private fun drawTurn(
        img: BinaryImage, cx: Int, cy: Int,
        halfW: Int = 8, yOffset: Int = 4, thickness: Int = 4
    ) {
        for (x in -halfW..halfW) {
            val px = cx + x
            if (px !in 0 until width) continue
            // Half-sine: phase from -π/2 to π/2, sin goes from -1 to +1 monotonically
            val phase = (x.toDouble() / halfW) * Math.PI / 2
            val py = cy + (yOffset * kotlin.math.sin(phase)).toInt()
            for (dy in 0 until thickness) {
                val yy = py + dy
                if (yy in 0 until height) img.set(px, yy, true)
            }
        }
    }

    /** Draw a filled ellipse. */
    private fun drawEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int, ry: Int) {
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
     * Draw a symmetric dome (fermata-like arc) centred at (cx, cy).
     * The arc is wider than tall with the centre higher than both edges.
     */
    private fun drawDome(img: BinaryImage, cx: Int, cy: Int, halfW: Int, height: Int, thickness: Int = 2) {
        for (x in -halfW..halfW) {
            val px = cx + x
            if (px !in 0 until width) continue
            // Parabolic dome: highest at centre, lowest at edges
            val frac = x.toDouble() / halfW
            val py = cy + (height * frac * frac).toInt()
            for (dy in 0 until thickness) {
                val yy = py + dy
                if (yy in 0 until height) img.set(px, yy, true)
            }
        }
    }

    // =========================================================================
    //  Basic detection
    // =========================================================================

    @Test
    fun `upper mordent zigzag above notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Zigzag at y=16, width=14 (halfWidth=7), amp=3, thickness=2
        drawZigzag(img, 200, 16, halfWidth = 7, amp = 3, thickness = 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个装饰音", 1, ornaments.size)
        assertEquals(
            "应为顺波音(upper mordent)",
            OrnamentDetector.OrnamentType.MORDENT_UPPER,
            ornaments[0].type
        )
        assertEquals(0, ornaments[0].noteIdx)
    }

    @Test
    fun `lower mordent with slash is detected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Zigzag
        drawZigzag(img, 200, 16, halfWidth = 7, amp = 3, thickness = 2)
        // Slash through centre: from y=10 to y=22 at x=200 (overlaps with zigzag)
        drawSlash(img, 200, 10, 22, thickness = 1)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个装饰音", 1, ornaments.size)
        assertEquals(
            "应为逆波音(lower mordent)",
            OrnamentDetector.OrnamentType.MORDENT_LOWER,
            ornaments[0].type
        )
    }

    @Test
    fun `turn S-curve above notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Turn: S-curve centred at (200, 16)
        drawTurn(img, 200, 16)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个装饰音", 1, ornaments.size)
        assertEquals(
            "应为回音(turn)",
            OrnamentDetector.OrnamentType.TURN,
            ornaments[0].type
        )
    }

    @Test
    fun `no ornament returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 50)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("无装饰音时应返回空列表", ornaments.isEmpty())
    }

    // =========================================================================
    //  Rejection tests
    // =========================================================================

    @Test
    fun `fermata dome is not mistaken for ornament`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Dome: symmetric, centre higher than edges → 1 reversal, symmetric → rejected
        drawDome(img, 200, 14, halfW = 7, height = 5, thickness = 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("穹顶(fermata)不应被误判为装饰音", ornaments.isEmpty())
    }

    @Test
    fun `ornament offset too far from notehead X is rejected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Zigzag at x=250 — far from notehead at x=200 (50px apart > 1.0*s=10)
        drawZigzag(img, 250, 16, halfWidth = 7, amp = 3, thickness = 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("X 偏移过远的装饰音应被拒绝", ornaments.isEmpty())
    }

    @Test
    fun `staccato dot too small is rejected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Tiny 2x2 dot above notehead — too small for ornament size constraint
        img.set(199, 14, true); img.set(200, 14, true)
        img.set(199, 15, true); img.set(200, 15, true)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("断奏点(过小)不应被误判为装饰音", ornaments.isEmpty())
    }

    @Test
    fun `ornament below staff is rejected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Zigzag below the staff — outside search region (above staff)
        drawZigzag(img, 200, 85, halfWidth = 7, amp = 3, thickness = 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("谱表下方的形状不应被检测为装饰音", ornaments.isEmpty())
    }

    @Test
    fun `flat horizontal line is not zigzag`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Flat horizontal line: no zigzag reversals, not asymmetric → rejected
        for (x in 193..207) {
            for (dy in 0 until 2) {
                img.set(x, 16 + dy, true)
            }
        }

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("平坦水平线不应被误判为装饰音", ornaments.isEmpty())
    }

    // =========================================================================
    //  Multiple / multi-system detection
    // =========================================================================

    @Test
    fun `multiple ornaments on different noteheads detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawNotehead(img, 300, 50)
        // Upper mordent above first notehead
        drawZigzag(img, 100, 16, halfWidth = 7, amp = 3, thickness = 2)
        // Turn above second notehead
        drawTurn(img, 300, 16)

        val nhs = listOf(makeNh(100, 50), makeNh(300, 50))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 2 个装饰音", 2, ornaments.size)
        // Results sorted by notehead index
        assertEquals(0, ornaments[0].noteIdx)
        assertEquals(OrnamentDetector.OrnamentType.MORDENT_UPPER, ornaments[0].type)
        assertEquals(1, ornaments[1].noteIdx)
        assertEquals(OrnamentDetector.OrnamentType.TURN, ornaments[1].type)
    }

    @Test
    fun `multi-system ornaments detected separately`() {
        // Taller image for two systems
        val w2 = 400
        val h2 = 200
        val img2 = BinaryImage.blank(w2, h2)
        // System 1: lines at y=30,40,50,60,70
        // System 2: lines at y=120,130,140,150,160
        val sys1 = StaffSystem(listOf(30, 40, 50, 60, 70).map { StaffLine(it - 1, it + 1, 1.0) })
        val sys2 = StaffSystem(listOf(120, 130, 140, 150, 160).map { StaffLine(it - 1, it + 1, 1.0) })

        drawNotehead2(img2, w2, h2, 100, 50)
        drawNotehead2(img2, w2, h2, 300, 140)

        // Upper mordent above system 1 notehead (above y=30 staff → around y=16)
        drawZigzag2(img2, w2, h2, 100, 16, halfWidth = 7, amp = 3, thickness = 2)
        // Upper mordent above system 2 notehead (above y=120 staff → around y=106)
        drawZigzag2(img2, w2, h2, 300, 106, halfWidth = 7, amp = 3, thickness = 2)

        val nhs = listOf(Notehead(100, 50, 9, 7, 63), Notehead(300, 140, 9, 7, 63))
        val sysIdx = listOf(0, 1)
        val systems = listOf(sys1, sys2)
        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)

        val ornaments = OrnamentDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals("应检测到 2 个装饰音（每系统各 1 个）", 2, ornaments.size)
        assertEquals(0, ornaments[0].noteIdx)
        assertEquals(0, ornaments[0].systemIdx)
        assertEquals(1, ornaments[1].noteIdx)
        assertEquals(1, ornaments[1].systemIdx)
    }

    // =========================================================================
    //  Edge cases
    // =========================================================================

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 50)

        val nhs = listOf(makeNh(200, 50))
        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, listOf(0), listOf(makeSystem()), 0)

        assertTrue("谱线间距为 0 时应返回空", ornaments.isEmpty())
    }

    @Test
    fun `empty noteheads returns empty`() {
        val img = blank()
        val ornaments = OrnamentDetector.detect(img, blobs(img), emptyList(), emptyList(), listOf(makeSystem()), s)
        assertTrue("无符头时应返回空", ornaments.isEmpty())
    }

    @Test
    fun `notehead with invalid system index is skipped`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        drawZigzag(img, 200, 16, halfWidth = 7, amp = 3, thickness = 2)

        val nhs = listOf(makeNh(200, 50))
        // System index out of range
        val sysIdx = listOf(5)
        val systems = listOf(makeSystem())

        val ornaments = OrnamentDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("系统索引越界的符头应被跳过", ornaments.isEmpty())
    }

    // ---- Helpers for multi-system (custom image dimensions) -----------------

    private fun drawNotehead2(img: BinaryImage, w: Int, h: Int, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x in 0 until w && y in 0 until h) {
                    val ndx = (x - cx).toDouble() / rx
                    val ndy = (y - cy).toDouble() / ry
                    if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
                }
            }
        }
    }

    private fun drawZigzag2(
        img: BinaryImage, w: Int, h: Int, cx: Int, cy: Int,
        halfWidth: Int, amp: Int, thickness: Int
    ) {
        for (x in -halfWidth..halfWidth) {
            val px = cx + x
            if (px !in 0 until w) continue
            val phase = (x.toDouble() / halfWidth) * Math.PI
            val py = cy + (amp * kotlin.math.sin(phase)).toInt()
            for (dy in 0 until thickness) {
                val yy = py + dy
                if (yy in 0 until h) img.set(px, yy, true)
            }
        }
    }
}
