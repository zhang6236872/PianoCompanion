package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TrillDetector] using synthetic binary images.
 *
 * A **trill** (颤音) is marked by the letters "tr" above a notehead, indicating
 * rapid alternation between the written note and the note above it. It may be
 * followed by a wavy line (trill extension) indicating duration.
 *
 * These tests render letter templates ('t', 'r') and noteheads pixel-by-pixel,
 * then verify that the detector correctly identifies (or rejects) trills based on
 * letter template matching, size constraints, X-centering, and system boundaries.
 */
class TrillDetectorTest {

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
     * Render a letter template at the given position and integer scale.
     * Each template cell is drawn as a scale×scale block of black pixels.
     */
    private fun renderLetter(img: BinaryImage, char: Char, x: Int, y: Int, scale: Int) {
        val tmpl = TrillDetector.LETTER_TEMPLATES[char] ?: return
        for (r in 0 until 7) {
            for (c in 0 until 5) {
                if (tmpl[r * 5 + c]) {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = x + c * scale + dx
                            val py = y + r * scale + dy
                            if (px in 0 until width && py in 0 until height) {
                                img.set(px, py, true)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Draw a horizontal wavy line (zigzag) starting at [x], [y].
     * The wave oscillates with amplitude [amp] and wavelength [waveLen].
     */
    private fun drawWavyLine(
        img: BinaryImage, x: Int, y: Int, length: Int,
        amp: Int = 3, waveLen: Int = 8, thickness: Int = 2
    ) {
        for (i in 0 until length) {
            val px = x + i
            if (px !in 0 until width) continue
            val phase = (i.toDouble() / waveLen * 2 * Math.PI)
            val py = y + (amp * kotlin.math.sin(phase)).toInt()
            for (dy in 0 until thickness) {
                if (py + dy in 0 until height) img.set(px, py + dy, true)
            }
        }
    }

    /** Convenience: build blobs from image. */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ---- Basic detection ----------------------------------------------------

    @Test
    fun `tr above notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // "tr" above staff: t at x=190, r at x=204, scale=2 → 10px wide each
        renderLetter(img, 't', 190, 10, 2)
        renderLetter(img, 'r', 204, 10, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个颤音", 1, trills.size)
        assertEquals(0, trills[0].noteIdx)
    }

    @Test
    fun `no trill returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 50)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("无 tr 文字时不应检测到颤音", trills.isEmpty())
    }

    @Test
    fun `tr with wavy line detected with hasWavyLine=true`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        renderLetter(img, 't', 190, 10, 2)
        renderLetter(img, 'r', 204, 10, 2)
        // Wavy line starting after 'r' (x≥216), length=30px (3 spacings)
        drawWavyLine(img, 216, 14, length = 30)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, trills.size)
        assertTrue("应检测到波浪线", trills[0].hasWavyLine)
    }

    @Test
    fun `tr without wavy line has hasWavyLine=false`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        renderLetter(img, 't', 190, 10, 2)
        renderLetter(img, 'r', 204, 10, 2)
        // No wavy line

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, trills.size)
        assertTrue("无波浪线时 hasWavyLine 应为 false", !trills[0].hasWavyLine)
    }

    // ---- Multiple / selective -----------------------------------------------

    @Test
    fun `two noteheads each with trill detected`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawNotehead(img, 300, 50)
        renderLetter(img, 't', 90, 10, 2)
        renderLetter(img, 'r', 104, 10, 2)
        renderLetter(img, 't', 290, 10, 2)
        renderLetter(img, 'r', 304, 10, 2)

        val nhs = listOf(makeNh(100, 50), makeNh(300, 50))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(2, trills.size)
        assertEquals(0, trills[0].noteIdx)
        assertEquals(1, trills[1].noteIdx)
    }

    @Test
    fun `selective trill - only one of two noteheads has trill`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawNotehead(img, 300, 50)
        // Only first notehead has trill
        renderLetter(img, 't', 90, 10, 2)
        renderLetter(img, 'r', 104, 10, 2)

        val nhs = listOf(makeNh(100, 50), makeNh(300, 50))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, trills.size)
        assertEquals(0, trills[0].noteIdx)
    }

    // ---- Rejection: non-matching shapes ------------------------------------

    @Test
    fun `single blob not matching tr rejected`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Only one letter 't' (not a complete "tr")
        renderLetter(img, 't', 195, 10, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("单个字母不应被识别为颤音", trills.isEmpty())
    }

    @Test
    fun `reversed letters rt not matched as trill`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // 'r' then 't' — should not match "tr"
        renderLetter(img, 'r', 190, 10, 2)
        renderLetter(img, 't', 204, 10, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("'rt' 不应被识别为颤音（必须是 'tr'）", trills.isEmpty())
    }

    @Test
    fun `fermata-like arc not mistaken for trill`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // Draw a dome-like arc (fermata shape) — wide, low
        for (x in 188..212) {
            val t = (x - 188).toDouble() / 24
            val offset = (8 * kotlin.math.sin(Math.PI * t)).toInt()
            val y = 22 - offset
            for (dy in 0 until 3) {
                if (y + dy in 0 until height) img.set(x, y + dy, true)
            }
        }

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("弧形不应被误判为颤音", trills.isEmpty())
    }

    @Test
    fun `letters too far from notehead X not matched`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // "tr" offset 80px from notehead (beyond tolerance of 15px)
        renderLetter(img, 't', 120, 10, 2)
        renderLetter(img, 'r', 134, 10, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("远离符头的 tr 不应匹配", trills.isEmpty())
    }

    @Test
    fun `letters below staff not matched`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // "tr" below the staff (y=85, below bottom line y=70)
        renderLetter(img, 't', 190, 85, 2)
        renderLetter(img, 'r', 204, 85, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("谱表下方的 tr 不应匹配", trills.isEmpty())
    }

    // ---- Multi-system -------------------------------------------------------

    @Test
    fun `trills in two different systems detected`() {
        val h2 = 220
        val img2 = BinaryImage.blank(width, h2)
        val sys0Lines = listOf(30, 40, 50, 60, 70)
        val sys1Lines = listOf(130, 140, 150, 160, 170)

        drawNotehead(img2, 100, 60)
        drawNotehead(img2, 100, 160)

        // Trill above system 0 (search region y=0~25)
        renderLetterInto(img2, 't', 90, 10, 2, width, h2)
        renderLetterInto(img2, 'r', 104, 10, 2, width, h2)
        // Trill above system 1 (search region y=95~125)
        renderLetterInto(img2, 't', 90, 100, 2, width, h2)
        renderLetterInto(img2, 'r', 104, 100, 2, width, h2)

        val nhs = listOf(makeNh(100, 60), makeNh(100, 160))
        val sysIdx = listOf(0, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val trills = TrillDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(2, trills.size)
        assertEquals(0, trills[0].noteIdx)
        assertEquals(1, trills[1].noteIdx)
    }

    @Test
    fun `trill in one system does not match notehead in another`() {
        val h2 = 220
        val img2 = BinaryImage.blank(width, h2)
        val sys0Lines = listOf(30, 40, 50, 60, 70)
        val sys1Lines = listOf(130, 140, 150, 160, 170)

        drawNotehead(img2, 100, 60)   // system 0, no trill
        drawNotehead(img2, 100, 160)  // system 1, has trill
        renderLetterInto(img2, 't', 90, 100, 2, width, h2)
        renderLetterInto(img2, 'r', 104, 100, 2, width, h2)

        val nhs = listOf(makeNh(100, 60), makeNh(100, 160))
        val sysIdx = listOf(0, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val trills = TrillDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(1, trills.size)
        assertEquals("应匹配系统 1 的符头（索引 1）", 1, trills[0].noteIdx)
    }

    // ---- Edge cases ---------------------------------------------------------

    @Test
    fun `empty noteheads returns empty`() {
        val img = blank()
        val systems = listOf(makeSystem())
        val trills = TrillDetector.detect(img, blobs(img), emptyList(), emptyList(), systems, s)
        assertTrue(trills.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        renderLetter(img, 't', 190, 10, 2)
        renderLetter(img, 'r', 204, 10, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, 0)
        assertTrue("零谱线间距应返回空", trills.isEmpty())
    }

    @Test
    fun `invalid system index skips notehead`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        renderLetter(img, 't', 190, 10, 2)
        renderLetter(img, 'r', 204, 10, 2)

        val nhs = listOf(makeNh(200, 50))
        val sysIdx = listOf(5) // invalid
        val systems = listOf(makeSystem())

        val trills = TrillDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("无效系统索引应跳过符头", trills.isEmpty())
    }

    // ---- Template validation ------------------------------------------------

    @Test
    fun `letter templates for t and r exist`() {
        assertNotNull("字母 't' 模板应存在", TrillDetector.LETTER_TEMPLATES['t'])
        assertNotNull("字母 'r' 模板应存在", TrillDetector.LETTER_TEMPLATES['r'])
    }

    @Test
    fun `all templates have valid dimensions`() {
        for (ch in listOf('t', 'r')) {
            val tmpl = TrillDetector.LETTER_TEMPLATES[ch]!!
            assertEquals("字母 '$ch' 模板应有 35 个元素", 35, tmpl.size)
            // 验证所有 5 列至少有 1 个像素
            for (col in 0 until 5) {
                var found = false
                for (row in 0 until 7) {
                    if (tmpl[row * 5 + col]) found = true
                }
                assertTrue("字母 '$ch' 列 $col 必须至少有 1 个像素", found)
            }
            // 验证所有 7 行至少有 1 个像素
            for (row in 0 until 7) {
                var found = false
                for (col in 0 until 5) {
                    if (tmpl[row * 5 + col]) found = true
                }
                assertTrue("字母 '$ch' 行 $row 必须至少有 1 个像素", found)
            }
        }
    }

    @Test
    fun `t and r templates are distinct`() {
        val tTmpl = TrillDetector.LETTER_TEMPLATES['t']!!
        val rTmpl = TrillDetector.LETTER_TEMPLATES['r']!!
        var dist = 0
        for (i in tTmpl.indices) if (tTmpl[i] != rTmpl[i]) dist++
        assertTrue(
            "'t' 和 'r' 模板的汉明距离 $dist 应 ≥ 10",
            dist >= 10
        )
    }

    @Test
    fun `each letter matches itself at scale 2`() {
        for (ch in listOf('t', 'r')) {
            val img = blank()
            renderLetter(img, ch, 190, 10, 2)
            drawNotehead(img, 200, 50)

            val nhs = listOf(makeNh(200, 50))
            val sysIdx = listOf(0)
            val systems = listOf(makeSystem())

            // Single letter won't make a trill (need 'tr' pair), but should produce a blob
            val allBlobs = blobs(img)
            assertTrue("字母 '$ch' 应产生至少 1 个 blob", allBlobs.isNotEmpty())
        }
    }

    // ---- Helper for multi-system tests -------------------------------------

    /**
     * Render a letter into an image with custom dimensions (for multi-system tests).
     */
    private fun renderLetterInto(
        img: BinaryImage, char: Char, x: Int, y: Int, scale: Int,
        imgW: Int, imgH: Int
    ) {
        val tmpl = TrillDetector.LETTER_TEMPLATES[char] ?: return
        for (r in 0 until 7) {
            for (c in 0 until 5) {
                if (tmpl[r * 5 + c]) {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = x + c * scale + dx
                            val py = y + r * scale + dy
                            if (px in 0 until imgW && py in 0 until imgH) {
                                img.set(px, py, true)
                            }
                        }
                    }
                }
            }
        }
    }
}
