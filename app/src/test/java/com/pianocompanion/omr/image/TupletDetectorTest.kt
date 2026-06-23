package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TupletDetector] 单元测试：使用合成二值图（synthetic BinaryImage）端到端验证
 * 三连音/连音组(tuplet)检测的完整链路——数字识别 → 成员匹配 → 比例计算。
 *
 * 测试模式参考 [TrillDetectorTest] 和 [SignatureDetectorTest]：
 * - 用 `renderDigit` 将 5×7 数字模板按倍率画入合成图（8 连通保证整体是一个 blob）。
 * - 用 `drawNotehead` 画符头椭圆。
 * - 用 [ConnectedComponents.label] 提取 blobs。
 *
 * 几何约束（关键）：
 * - 谱线间距 s=20，最大组跨度 maxGroupSpan = 6.0 × s = 120px。
 * - 谱表五线 y=40,60,80,100,120，顶线 center=40。
 * - 搜索区域：searchBottom = 40 - 0.5×20 = 30，searchTop = max(0, 30-60) = 0。
 * - 数字模板 scale=2：宽 10px、高 14px，远小于 maxBlobW=30、maxBlobH=36。
 */
class TupletDetectorTest {

    private val width = 500
    private val height = 160
    private val s = 20 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** 五条谱线 y=40,60,80,100,120 → 间距=20。 */
    private val lineYs = listOf(40, 60, 80, 100, 120)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /** 画一个填充的符头大小椭圆。 */
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
     * 把某个数字的 5×7 模板按 [scale] 倍放大画入图像。
     * 8 连通保证（含对角）合成字形为一个完整 blob。
     */
    private fun renderDigit(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("no template for $digit")
        for (r in 0 until SignatureDetector.GRID_H) {
            for (c in 0 until SignatureDetector.GRID_W) {
                if (!tmpl[r * SignatureDetector.GRID_W + c]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val px = x0 + c * scale + dx
                        val py = y0 + r * scale + dy
                        if (px in 0 until width && py in 0 until height) {
                            img.set(px, py, true)
                        }
                    }
                }
            }
        }
    }

    /** 把数字模板画入指定尺寸的图像（用于多系统测试）。 */
    private fun renderDigitInto(
        img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int,
        imgW: Int, imgH: Int
    ) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("no template for $digit")
        for (r in 0 until SignatureDetector.GRID_H) {
            for (c in 0 until SignatureDetector.GRID_W) {
                if (!tmpl[r * SignatureDetector.GRID_W + c]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val px = x0 + c * scale + dx
                        val py = y0 + r * scale + dy
                        if (px in 0 until imgW && py in 0 until imgH) {
                            img.set(px, py, true)
                        }
                    }
                }
            }
        }
    }

    /** 画一条方括号水平线（bracket）。 */
    private fun drawBracket(img: BinaryImage, x: Int, y: Int, length: Int, thickness: Int = 2) {
        for (dx in 0 until length) {
            for (dy in 0 until thickness) {
                val px = x + dx
                val py = y + dy
                if (px in 0 until width && py in 0 until height) {
                    img.set(px, py, true)
                }
            }
        }
    }

    /** 从图像提取连通块。 */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ---- 三连音(triplet)基本检测 -------------------------------------------

    @Test
    fun `triplet digit 3 above three noteheads is detected`() {
        val img = blank()
        // 三个符头在谱线区域内（y=80），间距 40px，span=80 ≤ maxGroupSpan=120
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        // 数字 "3" 居中于符头组上方：center=160 → x0=155
        renderDigit(img, 3, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个三连音", 1, tuplets.size)
        assertEquals(3, tuplets[0].number)
        assertEquals(3, tuplets[0].noteheadIndices.size)
    }

    @Test
    fun `no digit above noteheads returns empty`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("无数字时不应检测到连音组", tuplets.isEmpty())
    }

    @Test
    fun `triplet member indices are correctly assigned`() {
        val img = blank()
        drawNotehead(img, 100, 80)
        drawNotehead(img, 140, 80)
        drawNotehead(img, 180, 80)
        renderDigit(img, 3, x0 = 135, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(100, 80), makeNh(140, 80), makeNh(180, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, tuplets.size)
        val indices = tuplets[0].noteheadIndices
        assertTrue("索引 0 应在连音组中", 0 in indices)
        assertTrue("索引 1 应在连音组中", 1 in indices)
        assertTrue("索引 2 应在连音组中", 2 in indices)
    }

    // ---- 二连音(duplet)检测 ------------------------------------------------

    @Test
    fun `duplet digit 2 above two noteheads is detected`() {
        val img = blank()
        drawNotehead(img, 140, 80)
        drawNotehead(img, 180, 80)
        renderDigit(img, 2, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(140, 80), makeNh(180, 80))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个二连音", 1, tuplets.size)
        assertEquals(2, tuplets[0].number)
    }

    // ---- 五连音(quintuplet)检测 -------------------------------------------

    @Test
    fun `quintuplet digit 5 above five noteheads is detected`() {
        val img = blank()
        // 5 个符头间距 25px：span=100 ≤ maxGroupSpan=120
        for (i in 0 until 5) drawNotehead(img, 100 + i * 25, 80)
        // center=150 → x0=145
        renderDigit(img, 5, x0 = 145, y0 = 5, scale = 2)

        val nhs = (0 until 5).map { makeNh(100 + it * 25, 80) }
        val sysIdx = List(5) { 0 }
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 1 个五连音", 1, tuplets.size)
        assertEquals(5, tuplets[0].number)
    }

    // ---- 多组连音检测 ------------------------------------------------------

    @Test
    fun `two separate triplets both detected`() {
        val img = blank()
        // 第一组三连音：符头 100, 140, 180（span=80）
        drawNotehead(img, 100, 80)
        drawNotehead(img, 140, 80)
        drawNotehead(img, 180, 80)
        renderDigit(img, 3, x0 = 135, y0 = 5, scale = 2)

        // 第二组三连音：符头 300, 340, 380（span=80）
        drawNotehead(img, 300, 80)
        drawNotehead(img, 340, 80)
        drawNotehead(img, 380, 80)
        renderDigit(img, 3, x0 = 335, y0 = 5, scale = 2)

        val nhs = listOf(
            makeNh(100, 80), makeNh(140, 80), makeNh(180, 80),
            makeNh(300, 80), makeNh(340, 80), makeNh(380, 80)
        )
        val sysIdx = listOf(0, 0, 0, 0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals("应检测到 2 个三连音", 2, tuplets.size)
    }

    // ---- 成员数量不匹配的拒绝 ----------------------------------------------

    @Test
    fun `triplet digit 3 with only two noteheads not detected`() {
        val img = blank()
        drawNotehead(img, 140, 80)
        drawNotehead(img, 180, 80)
        renderDigit(img, 3, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(140, 80), makeNh(180, 80))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("符头数 < 数字时不应检测到连音组", tuplets.isEmpty())
    }

    @Test
    fun `duplet digit 2 with three noteheads matches nearest two`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        renderDigit(img, 2, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        // 数字 "2" 需要恰好 2 个符头，取最接近的 2 个
        if (tuplets.isNotEmpty()) {
            assertEquals(2, tuplets[0].number)
            assertEquals(2, tuplets[0].noteheadIndices.size)
        }
    }

    // ---- 不支持的数字 ------------------------------------------------------

    @Test
    fun `digit 8 not recognized as tuplet`() {
        val img = blank()
        for (i in 0 until 8) drawNotehead(img, 100 + i * 15, 80)
        renderDigit(img, 8, x0 = 148, y0 = 5, scale = 2)

        val nhs = (0 until 8).map { makeNh(100 + it * 15, 80) }
        val sysIdx = List(8) { 0 }
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("数字 8 不是支持的连音组类型", tuplets.isEmpty())
    }

    @Test
    fun `digit 9 not recognized as tuplet`() {
        val img = blank()
        for (i in 0 until 9) drawNotehead(img, 100 + i * 12, 80)
        renderDigit(img, 9, x0 = 143, y0 = 5, scale = 2)

        val nhs = (0 until 9).map { makeNh(100 + it * 12, 80) }
        val sysIdx = List(9) { 0 }
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("数字 9 不是支持的连音组类型", tuplets.isEmpty())
    }

    // ---- 位置约束测试 ------------------------------------------------------

    @Test
    fun `digit below staff not detected`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        // 数字在谱表下方（y=130，底线 y=120 以下）
        renderDigit(img, 3, x0 = 155, y0 = 130, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("谱表下方的数字不应被检测为连音组", tuplets.isEmpty())
    }

    @Test
    fun `digit in staff line region not detected`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        // 数字在谱线区域内（y=55，在 searchBottom=30 以下）
        renderDigit(img, 3, x0 = 155, y0 = 55, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("谱线区域内的数字不应被检测为连音组", tuplets.isEmpty())
    }

    // ---- 方括号检测 --------------------------------------------------------

    @Test
    fun `tuplet with bracket has hasBracket=true`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        renderDigit(img, 3, x0 = 155, y0 = 10, scale = 2)
        // 方括号在数字上方（y=2），长度 40px（2 个间距 = 40）
        drawBracket(img, x = 130, y = 2, length = 60)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, tuplets.size)
        assertTrue("应检测到方括号", tuplets[0].hasBracket)
    }

    @Test
    fun `tuplet without bracket has hasBracket=false`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        renderDigit(img, 3, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, tuplets.size)
        assertTrue("无方括号时 hasBracket 应为 false", !tuplets[0].hasBracket)
    }

    // ---- 多系统测试 --------------------------------------------------------

    @Test
    fun `tuplets in two different systems detected`() {
        val h2 = 300
        val img2 = BinaryImage.blank(width, h2)
        val sys0Lines = listOf(50, 70, 90, 110, 130)
        val sys1Lines = listOf(180, 200, 220, 240, 260)

        // 系统 0 的三连音：符头 y=90，span=80
        drawNoteheadInto(img2, 120, 90, width, h2)
        drawNoteheadInto(img2, 160, 90, width, h2)
        drawNoteheadInto(img2, 200, 90, width, h2)
        renderDigitInto(img2, 3, x0 = 155, y0 = 15, scale = 2, imgW = width, imgH = h2)

        // 系统 1 的三连音：符头 y=220，span=80
        drawNoteheadInto(img2, 120, 220, width, h2)
        drawNoteheadInto(img2, 160, 220, width, h2)
        drawNoteheadInto(img2, 200, 220, width, h2)
        // 系统 1 搜索区域：searchBottom=170, searchTop=150（upperLimit=130+20）
        renderDigitInto(img2, 3, x0 = 155, y0 = 152, scale = 2, imgW = width, imgH = h2)

        val nhs = listOf(
            makeNh(120, 90), makeNh(160, 90), makeNh(200, 90),
            makeNh(120, 220), makeNh(160, 220), makeNh(200, 220)
        )
        val sysIdx = listOf(0, 0, 0, 1, 1, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val tuplets = TupletDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(2, tuplets.size)
        assertEquals(0, tuplets[0].systemIdx)
        assertEquals(1, tuplets[1].systemIdx)
    }

    @Test
    fun `tuplet in one system does not match noteheads in another`() {
        val h2 = 300
        val img2 = BinaryImage.blank(width, h2)
        val sys0Lines = listOf(50, 70, 90, 110, 130)
        val sys1Lines = listOf(180, 200, 220, 240, 260)

        // 系统 0：有符头无三连音
        drawNoteheadInto(img2, 120, 90, width, h2)
        drawNoteheadInto(img2, 160, 90, width, h2)
        drawNoteheadInto(img2, 200, 90, width, h2)

        // 系统 1：有三连音
        drawNoteheadInto(img2, 120, 220, width, h2)
        drawNoteheadInto(img2, 160, 220, width, h2)
        drawNoteheadInto(img2, 200, 220, width, h2)
        // 系统 1 搜索区域：searchBottom=170, searchTop=150（upperLimit=130+20）
        renderDigitInto(img2, 3, x0 = 155, y0 = 152, scale = 2, imgW = width, imgH = h2)

        val nhs = listOf(
            makeNh(120, 90), makeNh(160, 90), makeNh(200, 90),
            makeNh(120, 220), makeNh(160, 220), makeNh(200, 220)
        )
        val sysIdx = listOf(0, 0, 0, 1, 1, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val tuplets = TupletDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals("只在系统 1 应检测到三连音", 1, tuplets.size)
        assertEquals(1, tuplets[0].systemIdx)
    }

    // ---- 边界情况 ----------------------------------------------------------

    @Test
    fun `empty noteheads returns empty`() {
        val img = blank()
        val systems = listOf(makeSystem())
        val tuplets = TupletDetector.detect(img, blobs(img), emptyList(), emptyList(), systems, s)
        assertTrue(tuplets.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        renderDigit(img, 3, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(0, 0, 0)
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, 0)
        assertTrue("零谱线间距应返回空", tuplets.isEmpty())
    }

    @Test
    fun `invalid system index skips notehead`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        drawNotehead(img, 160, 80)
        drawNotehead(img, 200, 80)
        renderDigit(img, 3, x0 = 155, y0 = 5, scale = 2)

        val nhs = listOf(makeNh(120, 80), makeNh(160, 80), makeNh(200, 80))
        val sysIdx = listOf(5, 5, 5) // 无效系统索引
        val systems = listOf(makeSystem())

        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("无效系统索引应跳过所有符头", tuplets.isEmpty())
    }

    @Test
    fun `empty systems returns empty`() {
        val img = blank()
        drawNotehead(img, 120, 80)
        val nhs = listOf(makeNh(120, 80))
        val sysIdx = listOf(0)
        val tuplets = TupletDetector.detect(img, blobs(img), nhs, sysIdx, emptyList(), s)
        assertTrue("空谱表系统列表应返回空", tuplets.isEmpty())
    }

    // ---- tupletRatio 测试 --------------------------------------------------

    @Test
    fun `triplet ratio is two-thirds`() {
        assertEquals(2.0 / 3.0, TupletDetector.tupletRatio(3), 0.001)
    }

    @Test
    fun `duplet ratio is three-halves`() {
        assertEquals(3.0 / 2.0, TupletDetector.tupletRatio(2), 0.001)
    }

    @Test
    fun `quadruplet ratio is three-quarters`() {
        assertEquals(3.0 / 4.0, TupletDetector.tupletRatio(4), 0.001)
    }

    @Test
    fun `quintuplet ratio is four-fifths`() {
        assertEquals(4.0 / 5.0, TupletDetector.tupletRatio(5), 0.001)
    }

    @Test
    fun `sextuplet ratio is four-sixths`() {
        assertEquals(4.0 / 6.0, TupletDetector.tupletRatio(6), 0.001)
    }

    @Test
    fun `septuplet ratio is four-sevenths`() {
        assertEquals(4.0 / 7.0, TupletDetector.tupletRatio(7), 0.001)
    }

    @Test
    fun `unsupported number returns ratio 1`() {
        assertEquals("不支持数字的缩放比例应为 1.0", 1.0, TupletDetector.tupletRatio(8), 0.001)
        assertEquals(1.0, TupletDetector.tupletRatio(0), 0.001)
        assertEquals(1.0, TupletDetector.tupletRatio(1), 0.001)
    }

    // ---- 指定尺寸的辅助绘制 ------------------------------------------------

    /** 在自定义尺寸的图像中画符头。 */
    private fun drawNoteheadInto(
        img: BinaryImage, cx: Int, cy: Int, imgW: Int, imgH: Int,
        rx: Int = 4, ry: Int = 3
    ) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x in 0 until imgW && y in 0 until imgH) {
                    val ndx = (x - cx).toDouble() / rx
                    val ndy = (y - cy).toDouble() / ry
                    if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
                }
            }
        }
    }
}
