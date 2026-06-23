package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FermataDetector] using synthetic binary images.
 *
 * A **fermata** (延音记号/停留号) is a semi-circular arc `⌒` placed above
 * (upright) or below (inverted `⌣`) a notehead, indicating the performer should
 * hold the note longer than its written duration.
 *
 * These tests draw noteheads and fermata arcs pixel-by-pixel, then verify that
 * the detector correctly identifies (or rejects) fermatas based on dome shape
 * verification, size constraints, X-centering, and system boundaries.
 */
class FermataDetectorTest {

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
     * Draw a fermata arc (half-sine dome) centred at [cx], with the base at [baseY].
     *
     * For upright fermata: the arc peaks *above* baseY (toward smaller Y).
     * For inverted fermata: the arc peaks *below* baseY (toward larger Y).
     *
     * Thickness extends toward baseY so the arc forms a connected band.
     */
    private fun drawFermataArc(
        img: BinaryImage, cx: Int, baseY: Int,
        halfWidth: Int = 10, peakHeight: Int = 10,
        thickness: Int = 3, inverted: Boolean = false
    ) {
        val dir = if (inverted) 1 else -1
        for (x in (cx - halfWidth)..(cx + halfWidth)) {
            if (x !in 0 until width) continue
            val t = (x - (cx - halfWidth)).toDouble() / (2 * halfWidth)
            val offset = (peakHeight * kotlin.math.sin(Math.PI * t)).toInt()
            val arcY = baseY + dir * offset
            for (dy in 0 until thickness) {
                val y = if (inverted) arcY - dy else arcY + dy
                if (y in 0 until height) img.set(x, y, true)
            }
        }
    }

    /** Convenience: build blobs from image with a sensible minimum area. */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `upright fermata above notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        // Fermata arc above notehead: base at y=20, peak at y=10
        drawFermataArc(img, 200, baseY = 20)

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, fermatas.size)
        assertEquals(0, fermatas[0].noteIdx)
        assertTrue("Should be upright (not inverted)", !fermatas[0].inverted)
    }

    @Test
    fun `inverted fermata below notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        // Inverted fermata below notehead: base at y=78, peak at y=88
        drawFermataArc(img, 200, baseY = 78, inverted = true)

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, fermatas.size)
        assertEquals(0, fermatas[0].noteIdx)
        assertTrue("Should be inverted", fermatas[0].inverted)
    }

    @Test
    fun `no fermata returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 60)

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("No fermata arc should yield empty list", fermatas.isEmpty())
    }

    // ---- Multiple / selective -----------------------------------------------

    @Test
    fun `two noteheads each with fermata detected`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawNotehead(img, 300, 60)
        drawFermataArc(img, 100, baseY = 20)
        drawFermataArc(img, 300, baseY = 20)

        val nhs = listOf(makeNh(100, 60), makeNh(300, 60))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(2, fermatas.size)
        assertEquals(0, fermatas[0].noteIdx)
        assertEquals(1, fermatas[1].noteIdx)
    }

    @Test
    fun `selective fermata - only one of two noteheads has fermata`() {
        val img = blank()
        drawNotehead(img, 100, 60)
        drawNotehead(img, 300, 60)
        // Only the first notehead has a fermata
        drawFermataArc(img, 100, baseY = 20)

        val nhs = listOf(makeNh(100, 60), makeNh(300, 60))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, fermatas.size)
        assertEquals(0, fermatas[0].noteIdx)
    }

    // ---- Rejection: non-dome shapes ----------------------------------------

    @Test
    fun `flat horizontal bar not mistaken for fermata`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        // Draw a flat horizontal bar (no dome shape) above the notehead
        for (x in 190..210) {
            for (y in 14..20) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue(
            "Flat bar (no dome) should not be detected as fermata",
            fermatas.isEmpty()
        )
    }

    @Test
    fun `tall vertical blob not mistaken for fermata`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        // Draw a tall blob (height > width → aspect ratio fails)
        for (x in 196..203) {
            for (y in 8..20) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue(
            "Tall vertical blob (width < height) should not be detected as fermata",
            fermatas.isEmpty()
        )
    }

    @Test
    fun `too narrow blob rejected`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        // Draw a small blob (width < 0.7 * s = 7)
        for (x in 198..201) {
            for (y in 14..18) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("Too narrow blob should be rejected", fermatas.isEmpty())
    }

    @Test
    fun `off-center blob not matched to notehead`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        // Draw a valid fermata arc but offset 12px from notehead center
        drawFermataArc(img, 212, baseY = 20)

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue(
            "Fermata arc 12px off-center should not match notehead (tolerance = 5)",
            fermatas.isEmpty()
        )
    }

    // ---- Multi-system -------------------------------------------------------

    @Test
    fun `fermatas in two different systems detected`() {
        val h2 = 200
        val img2 = BinaryImage.blank(width, h2)
        // System 0: lines at y=30..70
        val sys0Lines = listOf(30, 40, 50, 60, 70)
        // System 1: lines at y=110..150
        val sys1Lines = listOf(110, 120, 130, 140, 150)

        // Noteheads
        drawNotehead(img2, 100, 60)
        drawNotehead(img2, 100, 140)

        // Fermata above system 0 notehead (search region y=0..25)
        drawFermataArc(img2, 100, baseY = 20)
        // Fermata above system 1 notehead (search region y=70..105)
        drawFermataArc(img2, 100, baseY = 100)

        val nhs = listOf(makeNh(100, 60), makeNh(100, 140))
        val sysIdx = listOf(0, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val fermatas = FermataDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(2, fermatas.size)
        assertEquals(0, fermatas[0].noteIdx)
        assertEquals(1, fermatas[1].noteIdx)
    }

    @Test
    fun `fermata in one system does not match notehead in another`() {
        val h2 = 200
        val img2 = BinaryImage.blank(width, h2)
        val sys0Lines = listOf(30, 40, 50, 60, 70)
        val sys1Lines = listOf(110, 120, 130, 140, 150)

        // Only system 1 has a fermata
        drawNotehead(img2, 100, 60)   // system 0, no fermata
        drawNotehead(img2, 100, 140)  // system 1, has fermata
        drawFermataArc(img2, 100, baseY = 100)

        val nhs = listOf(makeNh(100, 60), makeNh(100, 140))
        val sysIdx = listOf(0, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val fermatas = FermataDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(1, fermatas.size)
        assertEquals(
            "Fermata should match system 1 notehead (index 1), not system 0",
            1, fermatas[0].noteIdx
        )
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    fun `empty noteheads returns empty`() {
        val img = blank()
        val systems = listOf(makeSystem())
        val fermatas = FermataDetector.detect(img, blobs(img), emptyList(), emptyList(), systems, s)
        assertTrue(fermatas.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        drawFermataArc(img, 200, baseY = 20)

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, 0)
        assertTrue("Zero line spacing should return empty", fermatas.isEmpty())
    }

    @Test
    fun `invalid system index skips notehead`() {
        val img = blank()
        drawNotehead(img, 200, 60)
        drawFermataArc(img, 200, baseY = 20)

        val nhs = listOf(makeNh(200, 60))
        val sysIdx = listOf(5)  // invalid: only 1 system
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("Invalid system index should skip notehead", fermatas.isEmpty())
    }

    @Test
    fun `inverted fermata takes priority - upright checked first then inverted`() {
        // A notehead with only an inverted fermata (below), no upright fermata (above)
        val img = blank()
        drawNotehead(img, 200, 50)
        drawFermataArc(img, 200, baseY = 78, inverted = true)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val fermatas = FermataDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, fermatas.size)
        assertTrue("Should detect inverted fermata", fermatas[0].inverted)
    }
}
