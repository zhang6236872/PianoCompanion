package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TempoMarkingDetector] 单元测试：使用合成二值图（synthetic BinaryImage）端到端验证
 * 速度记号检测的完整链路——等号检测 + 数字识别 + BPM 组合。
 *
 * 测试模式参考 [TupletDetectorTest] 和 [FingeringDetectorTest]：
 * - 用 `renderDigit` 将 5×7 数字模板按倍率画入合成图。
 * - 用 `drawEqualsSign` 画两条平行水平线段模拟 "=" 符号。
 * - 用 [ConnectedComponents.label] 提取 blobs。
 *
 * 几何约束：
 * - 谱线间距 s=20，五条谱线 y=60,80,100,120,140，顶线 center=60。
 * - 搜索区域：searchBottom = 60 - 0.5×20 = 50，searchTop = max(0, 50-100) = 0。
 * - 数字模板 scale=2：宽 10px、高 14px。
 * - "=" 每根线段：宽 8px、高 2px（宽高比=4.0 ≥ 1.5）。
 */
class TempoMarkingDetectorTest {

    private val width = 500
    private val height = 200
    private val s = 20 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** 五条谱线 y=60,80,100,120,140 → 间距=20。 */
    private val lineYs = listOf(60, 80, 100, 120, 140)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /**
     * 把某个数字的 5×7 模板按 [scale] 倍放大画入图像。
     * 8 连通保证合成字形为一个完整 blob。
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

    /** 把数字模板画入指定尺寸的图像（用于可变尺寸测试）。 */
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

    /**
     * 画 "=" 符号：两条平行的水平线段。
     *
     * @param x     左端 X
     * @param y     上线段的顶 Y
     * @param len   线段长度
     * @param thick 线段厚度
     * @param gap   两线段之间的垂直间距（上底线段之间的空隙）
     */
    private fun drawEqualsSign(
        img: BinaryImage, x: Int, y: Int, len: Int = 8, thick: Int = 2, gap: Int = 4
    ) {
        // 上线段
        for (dx in 0 until len) {
            for (dy in 0 until thick) {
                val px = x + dx
                val py = y + dy
                if (px in 0 until width && py in 0 until height) {
                    img.set(px, py, true)
                }
            }
        }
        // 下线段
        val bottomY = y + thick + gap
        for (dx in 0 until len) {
            for (dy in 0 until thick) {
                val px = x + dx
                val py = bottomY + dy
                if (px in 0 until width && py in 0 until height) {
                    img.set(px, py, true)
                }
            }
        }
    }

    /**
     * 在指定位置画一组速度记号 "= NNN"。
     *
     * @param startX   等号左端 X
     * @param equalsY  等号上 Y
     * @param digits   数字序列，如 listOf(1, 2, 0) 表示 120
     * @param scale    数字渲染倍率
     */
    private fun drawTempoMarking(
        img: BinaryImage, startX: Int, equalsY: Int, digits: List<Int>, scale: Int = 2
    ) {
        drawEqualsSign(img, startX, equalsY)
        val equalsWidth = 8
        val digitW = SignatureDetector.GRID_W * scale // 5*2 = 10
        val digitSpacing = 2
        var digitX = startX + equalsWidth + digitSpacing
        val digitY = equalsY - 2 // 数字略高于等号中心
        for (d in digits) {
            renderDigit(img, d, digitX, digitY, scale)
            digitX += digitW + digitSpacing
        }
    }

    private fun labelBlobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    private val system = makeSystem()

    // ---- 基础检测 -----------------------------------------------------------

    @Test
    fun `detects tempo marking 120 BPM`() {
        val img = blank()
        // "= 120" 在 y=30 处（搜索区内）
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(1, 2, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(120, result!!.bpm)
    }

    @Test
    fun `detects tempo marking 90 BPM`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(9, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(90, result!!.bpm)
    }

    @Test
    fun `detects tempo marking 60 BPM`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(6, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(60, result!!.bpm)
    }

    @Test
    fun `detects fast tempo 200 BPM`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(2, 0, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(200, result!!.bpm)
    }

    @Test
    fun `detects slow tempo 40 BPM`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(4, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(40, result!!.bpm)
    }

    @Test
    fun `detects 3-digit tempo 144 BPM`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(1, 4, 4))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(144, result!!.bpm)
    }

    // ---- 无速度记号场景 -----------------------------------------------------

    @Test
    fun `returns null when no tempo marking present`() {
        val img = blank()
        val blobs = labelBlobs(img)
        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `returns null with only digits but no equals sign`() {
        val img = blank()
        // 只有数字 "120"，没有 "=" 符号
        renderDigit(img, 1, 100, 20, 2)
        renderDigit(img, 2, 112, 20, 2)
        renderDigit(img, 0, 124, 20, 2)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `returns null with only equals sign but no digits`() {
        val img = blank()
        // 只有 "="，没有数字
        drawEqualsSign(img, x = 100, y = 28)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `returns null when single digit after equals`() {
        val img = blank()
        // "= 8" 只有1位数字
        drawEqualsSign(img, x = 100, y = 28)
        renderDigit(img, 8, 112, 26, 2)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    // ---- BPM 范围过滤 -------------------------------------------------------

    @Test
    fun `rejects BPM below minimum 20`() {
        val img = blank()
        // "= 15" → 15 < 20
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(1, 5))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `rejects BPM above maximum 400`() {
        val img = blank()
        // "= 450" → 450 > 400
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(4, 5, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    // ---- 等号位置变化 -------------------------------------------------------

    @Test
    fun `detects tempo with equals at different X position`() {
        val img = blank()
        // "= 80" 在右侧位置
        drawTempoMarking(img, startX = 300, equalsY = 28, digits = listOf(8, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(80, result!!.bpm)
    }

    @Test
    fun `detects tempo with equals at different Y position`() {
        val img = blank()
        // "= 75" 在更高位置（y=10）
        drawTempoMarking(img, startX = 100, equalsY = 10, digits = listOf(7, 5))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(75, result!!.bpm)
    }

    // ---- 多系统场景 ---------------------------------------------------------

    @Test
    fun `detects tempo above first system in multi-system page`() {
        val img = blank()
        // 第一个系统 y=60..140，第二个系统 y=180..260
        val lineYs2 = listOf(180, 200, 220, 240, 260)
        val system2 = makeSystem(lineYs2)
        // 速度记号在第一个系统上方
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(1, 0, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system, system2), s)

        assertNotNull(result)
        assertEquals(100, result!!.bpm)
    }

    @Test
    fun `does not detect tempo below first system`() {
        val img = blank()
        // 速度记号在两个系统之间（不在第一个系统上方）
        drawTempoMarking(img, startX = 100, equalsY = 155, digits = listOf(1, 0, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    // ---- 与其他数字标注区分 -------------------------------------------------

    @Test
    fun `bar number without equals is not detected as tempo`() {
        val img = blank()
        // 小节号 "3" 在左侧，没有等号
        renderDigit(img, 3, 10, 70, 2)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `fingering number without equals is not detected`() {
        val img = blank()
        // 指法数字 "5" 在搜索区域
        renderDigit(img, 5, 100, 30, 2)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `multiple digit groups only matches the one with equals`() {
        val img = blank()
        // 左侧有裸数字 "45"（无等号），右侧有 "= 120"
        renderDigit(img, 4, 50, 30, 2)
        renderDigit(img, 5, 62, 30, 2)
        drawTempoMarking(img, startX = 200, equalsY = 28, digits = listOf(1, 2, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(120, result!!.bpm)
    }

    // ---- 边界情况 -----------------------------------------------------------

    @Test
    fun `returns null for empty systems list`() {
        val img = blank()
        val blobs = labelBlobs(img)
        val result = TempoMarkingDetector.detect(img, blobs, emptyList(), s)
        assertNull(result)
    }

    @Test
    fun `returns null for zero line spacing`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(1, 2, 0))
        val blobs = labelBlobs(img)
        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), 0)
        assertNull(result)
    }

    @Test
    fun `returns null for empty image`() {
        val img = blank()
        val blobs = labelBlobs(img)
        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)
        assertNull(result)
    }

    @Test
    fun `detects tempo with larger scale digits`() {
        val img = blank()
        // scale=3：数字 15×21px，仍小于 maxBlobW=60、maxBlobH=40
        drawTempoMarking(img, startX = 100, equalsY = 20, digits = listOf(7, 5), scale = 3)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(75, result!!.bpm)
    }

    @Test
    fun `detects first tempo marking when multiple exist`() {
        val img = blank()
        // 两组速度记号，取第一组（左边的）
        drawTempoMarking(img, startX = 50, equalsY = 28, digits = listOf(6, 0))
        drawTempoMarking(img, startX = 250, equalsY = 28, digits = listOf(1, 2, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        // 应取第一组 60 BPM（按 X 从左到右搜索）
        assertNotNull(result)
        assertEquals(60, result!!.bpm)
    }

    @Test
    fun `centerX is within expected range`() {
        val img = blank()
        drawTempoMarking(img, startX = 100, equalsY = 28, digits = listOf(1, 2, 0))
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        // centerX 应在 100 (equals 左端) 到 ~160 (最后一个数字右端) 之间
        assertTrue("centerX=${result!!.centerX} should be >= 95", result.centerX >= 95)
        assertTrue("centerX=${result.centerX} should be <= 170", result.centerX <= 170)
    }

    // ---- 不同等号线段宽度 ---------------------------------------------------

    @Test
    fun `detects with wider equals sign`() {
        val img = blank()
        drawEqualsSign(img, x = 100, y = 28, len = 12, thick = 2, gap = 4)
        renderDigit(img, 1, 116, 26, 2)
        renderDigit(img, 2, 128, 26, 2)
        renderDigit(img, 0, 140, 26, 2)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(120, result!!.bpm)
    }

    @Test
    fun `detects with thicker equals bars`() {
        val img = blank()
        // 每根线段厚 3px
        drawEqualsSign(img, x = 100, y = 26, len = 8, thick = 3, gap = 3)
        renderDigit(img, 1, 112, 24, 2)
        renderDigit(img, 0, 124, 24, 2)
        renderDigit(img, 0, 136, 24, 2)
        val blobs = labelBlobs(img)

        val result = TempoMarkingDetector.detect(img, blobs, listOf(system), s)

        assertNotNull(result)
        assertEquals(100, result!!.bpm)
    }
}
