package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FingeringDetector] 的单元测试——使用合成二值图像。
 *
 * 指法数字(fingering number)是乐谱中标注在音符上方或下方的小型数字（1–5），
 * 指示演奏者用哪根手指弹奏。本测试集验证：
 *
 * - 基本检测：符头上方/下方的数字 1–5 被正确识别
 * - 多音符/选择性检测：部分音符有指法、部分没有
 * - 数字范围过滤：仅接受 1–5，拒绝 0、6–9
 * - 尺寸/X 对齐过滤：过大/过小/偏心的墨块被拒绝
 * - 边界情况：空输入、零间距、无效系统索引
 *
 * 测试模式参考 [FermataDetectorTest] 和 [SignatureDetectorTest]：
 * 用 `renderDigit` 将 5×7 数字模板按倍率画入合成图，用 [ConnectedComponents.label]
 * 提取 blobs。
 */
class FingeringDetectorTest {

    private val width = 400
    private val height = 200
    private val s = 20 // 谱线间距

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** 谱表五条线 y 坐标：30, 50, 70, 90, 110 → 间距 = 20。 */
    private val lineYs = listOf(30, 50, 70, 90, 110)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /** 绘制填充的椭圆符头。 */
    private fun drawNotehead(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x in 0 until img.width && y in 0 until img.height) {
                    val ndx = (x - cx).toDouble() / rx
                    val ndy = (y - cy).toDouble() / ry
                    if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
                }
            }
        }
    }

    private fun makeNh(cx: Int, cy: Int, w: Int = 9, h: Int = 7): Notehead =
        Notehead(cx, cy, w, h, w * h)

    /** 把某个数字的 5×7 模板按 [scale] 倍放大画入图像（与 SignatureDetectorTest 一致）。 */
    private fun renderDigit(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("no template for $digit")
        for (r in 0 until SignatureDetector.GRID_H) {
            for (c in 0 until SignatureDetector.GRID_W) {
                if (!tmpl[r * SignatureDetector.GRID_W + c]) continue
                for (dy in 0 until scale) for (dx in 0 until scale) {
                    val x = x0 + c * scale + dx
                    val y = y0 + r * scale + dy
                    if (x in 0 until img.width && y in 0 until img.height) img.set(x, y, true)
                }
            }
        }
    }

    /** 从图像提取 blobs（最小面积 4，与 FermataDetectorTest 一致）。 */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ---- 基本检测 -----------------------------------------------------------

    @Test
    fun `digit 3 above notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        // 数字 3 在符头上方：5×7 模板按 scale=3 放大 → 15×21 像素
        // 放在 y=[2, 22]（searchAbove 区域 y=[0, 24] 内）
        renderDigit(img, 3, x0 = 192, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(3, result[0].finger)
        assertTrue("应在上方", result[0].above)
    }

    @Test
    fun `digit 1 below notehead is detected`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        // 数字 1 在符头下方：放在 y=[130, 150]（searchBelow 区域 y=[116, 176] 内）
        renderDigit(img, 1, x0 = 192, y0 = 130, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(1, result[0].finger)
        assertTrue("应在下方", !result[0].above)
    }

    @Test
    fun `no digit returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 80)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("无指法数字应返回空列表", result.isEmpty())
    }

    // ---- 多音符 / 选择性检测 -------------------------------------------------

    @Test
    fun `two noteheads each with fingering detected`() {
        val img = blank()
        drawNotehead(img, 100, 80)
        drawNotehead(img, 300, 80)
        renderDigit(img, 2, x0 = 92, y0 = 2, scale = 3)   // 第一个符头上方
        renderDigit(img, 5, x0 = 292, y0 = 2, scale = 3)   // 第二个符头上方

        val nhs = listOf(makeNh(100, 80), makeNh(300, 80))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(2, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(2, result[0].finger)
        assertEquals(1, result[1].noteheadIdx)
        assertEquals(5, result[1].finger)
    }

    @Test
    fun `selective - only one of two noteheads has fingering`() {
        val img = blank()
        drawNotehead(img, 100, 80)
        drawNotehead(img, 300, 80)
        // 只有第一个符头有指法
        renderDigit(img, 4, x0 = 92, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(100, 80), makeNh(300, 80))
        val sysIdx = listOf(0, 0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(4, result[0].finger)
    }

    // ---- 数字范围 1-5 全部可识别 --------------------------------------------

    @Test
    fun `all valid finger digits 1 through 5 are detected`() {
        for (d in 1..5) {
            val img = blank()
            drawNotehead(img, 200, 80)
            renderDigit(img, d, x0 = 192, y0 = 2, scale = 3)

            val nhs = listOf(makeNh(200, 80))
            val sysIdx = listOf(0)
            val systems = listOf(makeSystem())

            val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

            assertEquals("数字 $d 应被检测为指法", 1, result.size)
            assertEquals("数字 $d 的 finger 值应匹配", d, result[0].finger)
        }
    }

    // ---- 无效数字拒绝（0, 6-9） ---------------------------------------------

    @Test
    fun `digit 6 above notehead is rejected - not a finger number`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        renderDigit(img, 6, x0 = 192, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("数字 6 不是有效指法编号，应被拒绝", result.isEmpty())
    }

    @Test
    fun `digit 9 above notehead is rejected - not a finger number`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        renderDigit(img, 9, x0 = 192, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("数字 9 不是有效指法编号，应被拒绝", result.isEmpty())
    }

    @Test
    fun `digit 0 above notehead is rejected - not a finger number`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        renderDigit(img, 0, x0 = 192, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("数字 0 不是有效指法编号，应被拒绝", result.isEmpty())
    }

    // ---- 尺寸过滤 -----------------------------------------------------------

    @Test
    fun `oversized digit rejected`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        // scale=5 → 25×35 像素，高度 35 > maxHeight(24) → 被拒绝
        renderDigit(img, 3, x0 = 185, y0 = 2, scale = 5)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("过大的数字（高度超过 1.2 间距）应被拒绝", result.isEmpty())
    }

    @Test
    fun `tiny digit rejected`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        // scale=1 → 5×7 像素，高度 7 < minHeight(8) → 被拒绝
        renderDigit(img, 3, x0 = 197, y0 = 5, scale = 1)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("过小的数字（高度低于 0.4 间距）应被拒绝", result.isEmpty())
    }

    // ---- X 对齐过滤 ---------------------------------------------------------

    @Test
    fun `off-center digit not matched to notehead`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        // 数字偏右 25px（中心偏差 25 > centerXTol=10）
        renderDigit(img, 3, x0 = 220, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertTrue("偏心的数字（中心偏差 > 0.5 间距）不应匹配到符头", result.isEmpty())
    }

    // ---- 多系统 -------------------------------------------------------------

    @Test
    fun `fingerings in two different systems detected`() {
        val h2 = 380
        val img2 = BinaryImage.blank(width, h2)
        // 系统 0：线 y=30..110（间距 20）
        val sys0Lines = listOf(30, 50, 70, 90, 110)
        // 系统 1：线 y=250..330（间距 20），与系统 0 充分间隔避免搜索区域重叠
        val sys1Lines = listOf(250, 270, 290, 310, 330)

        // 符头
        drawNotehead(img2, 100, 80)   // 系统 0
        drawNotehead(img2, 100, 300)  // 系统 1

        // 系统 0 上方指法：y=[2, 22]
        renderDigit(img2, 2, x0 = 92, y0 = 2, scale = 3)
        // 系统 1 上方指法：y=[222, 242]
        renderDigit(img2, 1, x0 = 92, y0 = 222, scale = 3)

        val nhs = listOf(makeNh(100, 80), makeNh(100, 300))
        val sysIdx = listOf(0, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val result = FingeringDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(2, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(2, result[0].finger)
        assertEquals(1, result[1].noteheadIdx)
        assertEquals(1, result[1].finger)
    }

    @Test
    fun `fingering in one system does not match notehead in another`() {
        val h2 = 380
        val img2 = BinaryImage.blank(width, h2)
        val sys0Lines = listOf(30, 50, 70, 90, 110)
        val sys1Lines = listOf(250, 270, 290, 310, 330)

        drawNotehead(img2, 100, 80)   // 系统 0，无指法
        drawNotehead(img2, 100, 300)  // 系统 1，有指法
        // 系统 1 上方指法：y=[222, 242]，中心 232
        // 系统 0 下方搜索区域 y=[116, 176]，不会匹配到 y=232 的数字
        renderDigit(img2, 3, x0 = 92, y0 = 222, scale = 3)

        val nhs = listOf(makeNh(100, 80), makeNh(100, 300))
        val sysIdx = listOf(0, 1)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val allBlobs = ConnectedComponents.label(img2, minPixels = 4)
        val result = FingeringDetector.detect(img2, allBlobs, nhs, sysIdx, systems, s)

        assertEquals(1, result.size)
        assertEquals(
            "指法应匹配系统 1 的符头（索引 1），而非系统 0",
            1, result[0].noteheadIdx
        )
    }

    // ---- 边界情况 -----------------------------------------------------------

    @Test
    fun `empty noteheads returns empty`() {
        val img = blank()
        val systems = listOf(makeSystem())
        val result = FingeringDetector.detect(img, blobs(img), emptyList(), emptyList(), systems, s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        renderDigit(img, 3, x0 = 192, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, 0)
        assertTrue("零间距应返回空列表", result.isEmpty())
    }

    @Test
    fun `invalid system index skips notehead`() {
        val img = blank()
        drawNotehead(img, 200, 80)
        renderDigit(img, 3, x0 = 192, y0 = 2, scale = 3)

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(5)  // 无效：只有 1 个系统
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)
        assertTrue("无效系统索引应跳过符头", result.isEmpty())
    }

    @Test
    fun `above takes priority over below when both present`() {
        // 如果符头上方和下方都有数字，上方优先（上方检测在前）
        val img = blank()
        drawNotehead(img, 200, 80)
        renderDigit(img, 2, x0 = 192, y0 = 2, scale = 3)   // 上方
        renderDigit(img, 4, x0 = 192, y0 = 130, scale = 3)  // 下方

        val nhs = listOf(makeNh(200, 80))
        val sysIdx = listOf(0)
        val systems = listOf(makeSystem())

        val result = FingeringDetector.detect(img, blobs(img), nhs, sysIdx, systems, s)

        assertEquals(1, result.size)
        assertEquals("上方优先，应返回 2", 2, result[0].finger)
        assertTrue("应为上方标记", result[0].above)
    }
}
