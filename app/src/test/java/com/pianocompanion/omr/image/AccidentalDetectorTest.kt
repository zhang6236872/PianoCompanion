package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Accidental
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AccidentalDetector] 的单元测试，使用合成二值图像。
 *
 * 临时记号（accidental）是写在音符左侧的小型符号：
 * - 升号(♯)：两根垂直笔画 + 两条对角交叉线（内部区域墨迹行占比 > 35%）
 * - 还原号(♮)：两根垂直笔画 + 两条水平交叉线（内部区域墨迹行占比 ≤ 35%）
 * - 降号(♭)：一根垂直笔画 + 右下方圆弧凸起（仅 1 根垂直笔画）
 *
 * 本测试通过在合成 [BinaryImage] 上精确绘制这些像素图形，验证检测器能正确分类。
 */
class AccidentalDetectorTest {

    private val width = 300
    private val height = 120
    private val s = 10 // 谱线间距

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /**
     * 绘制符头（填充椭圆），模拟真实乐谱中的音符头。
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

    /**
     * 绘制升号(♯)：两根垂直笔画（间距 6px）+ 两条对角交叉线。
     * 笔画高度 10px，对角线从左上到右下、左下到右上，在内部区域跨越多行。
     */
    private fun drawSharp(img: BinaryImage, cx: Int, cy: Int) {
        // 两根垂直笔画
        for (y in cy - 5..cy + 4) {
            img.set(cx - 3, y, true)
            img.set(cx + 3, y, true)
        }
        // 上对角线：(cx-3, cy-3) → (cx+3, cy+2)
        for (t in 0..6) {
            val x = cx - 3 + t
            val y = cy - 3 + t * 5 / 6
            if (x in 0 until width && y in 0 until height) img.set(x, y, true)
        }
        // 下对角线：(cx-3, cy+2) → (cx+3, cy-3)
        for (t in 0..6) {
            val x = cx - 3 + t
            val y = cy + 2 - t * 5 / 6
            if (x in 0 until width && y in 0 until height) img.set(x, y, true)
        }
    }

    /**
     * 绘制还原号(♮)：两根垂直笔画 + 两条水平交叉线。
     * 水平线仅在 2 行出现，内部墨迹行占比低（≤ 35%）。
     */
    private fun drawNatural(img: BinaryImage, cx: Int, cy: Int) {
        // 两根垂直笔画
        for (y in cy - 5..cy + 4) {
            img.set(cx - 3, y, true)
            img.set(cx + 3, y, true)
        }
        // 两条水平交叉线
        for (x in cx - 3..cx + 3) {
            img.set(x, cy - 2, true)
            img.set(x, cy + 2, true)
        }
    }

    /**
     * 绘制降号(♭)：一根垂直笔画 + 右下方圆弧凸起（4px 高 × 2px 宽）。
     * 只有一根垂直笔画 → strokeCount = 1 → FLAT。
     */
    private fun drawFlat(img: BinaryImage, cx: Int, cy: Int) {
        // 垂直笔画（10px 高）
        for (y in cy - 5..cy + 4) {
            img.set(cx - 1, y, true)
        }
        // 右下方凸起（4px 高 × 2px 宽）
        for (y in cy - 1..cy + 2) {
            img.set(cx, y, true)
            img.set(cx + 1, y, true)
        }
    }

    /**
     * 辅助：对图像运行连通块标记 + 临时记号检测。
     */
    private fun runDetect(
        img: BinaryImage,
        nhs: List<Notehead>,
        sigEndXs: List<Int> = List(nhs.size) { 0 }
    ): Map<Int, Accidental> {
        val blobs = ConnectedComponents.label(img, minPixels = 4)
        return AccidentalDetector.detect(img, blobs, nhs, List(nhs.size) { 0 }, sigEndXs, s).byNotehead
    }

    // ---- 基本检测 -----------------------------------------------------------

    @Test
    fun `升号在符头左侧被检测到`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawSharp(img, 82, 50)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        assertEquals(mapOf(0 to Accidental.SHARP), result)
    }

    @Test
    fun `降号在符头左侧被检测到`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawFlat(img, 84, 50)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        assertEquals(mapOf(0 to Accidental.FLAT), result)
    }

    @Test
    fun `还原号在符头左侧被检测到`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawNatural(img, 82, 50)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        assertEquals(mapOf(0 to Accidental.NATURAL), result)
    }

    @Test
    fun `无临时记号时返回空映射`() {
        val img = blank()
        drawNotehead(img, 100, 50)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        assertTrue("无临时记号应返回空映射", result.isEmpty())
    }

    // ---- 多符头 -------------------------------------------------------------

    @Test
    fun `多个符头各自检测到不同的临时记号`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawNotehead(img, 200, 50)
        drawNotehead(img, 280, 50)
        drawSharp(img, 82, 50)
        drawFlat(img, 184, 50)
        drawNatural(img, 262, 50)

        val nhs = listOf(makeNh(100, 50), makeNh(200, 50), makeNh(280, 50))
        val result = runDetect(img, nhs)

        assertEquals(
            mapOf(0 to Accidental.SHARP, 1 to Accidental.FLAT, 2 to Accidental.NATURAL),
            result
        )
    }

    @Test
    fun `仅部分符头有临时记号`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawNotehead(img, 200, 50)
        drawNotehead(img, 280, 50)
        // 只有第一个和第三个有临时记号
        drawSharp(img, 82, 50)
        drawNatural(img, 262, 50)

        val nhs = listOf(makeNh(100, 50), makeNh(200, 50), makeNh(280, 50))
        val result = runDetect(img, nhs)

        assertEquals(mapOf(0 to Accidental.SHARP, 2 to Accidental.NATURAL), result)
    }

    // ---- 调号区域排除 --------------------------------------------------------

    @Test
    fun `调号区域内的升降号不被误判为临时记号`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // 升号放在 sigEndX=90 左侧（属于调号区域）
        drawSharp(img, 82, 50)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs, sigEndXs = listOf(90))

        assertTrue("调号区域内的符号不应被检测为临时记号", result.isEmpty())
    }

    // ---- 边界情况 ------------------------------------------------------------

    @Test
    fun `空符头列表返回空映射`() {
        val img = blank()
        val result = runDetect(img, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `零谱线间距返回空映射`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        drawSharp(img, 82, 50)

        val nhs = listOf(makeNh(100, 50))
        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val result = AccidentalDetector.detect(img, blobs, nhs, listOf(0), listOf(0), 0).byNotehead

        assertTrue(result.isEmpty())
    }

    @Test
    fun `临时记号离符头太远不被检测`() {
        val img = blank()
        drawNotehead(img, 200, 50)
        // 升号放在距符头 3+ 个间距处（超出搜索窗口 2.5 个间距）
        drawSharp(img, 82, 50)

        val nhs = listOf(makeNh(200, 50))
        val result = runDetect(img, nhs)

        assertTrue("太远的符号不应被检测", result.isEmpty())
    }

    @Test
    fun `符头竖直方向偏移过大的临时记号不被检测`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // 升号在 Y 方向偏离符头 2+ 个间距（超出 ±1.2 间距搜索范围）
        drawSharp(img, 82, 80)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        assertTrue("竖直偏离过大的符号不应被检测", result.isEmpty())
    }

    @Test
    fun `过小连通块不被检测为临时记号`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // 在符头左侧放置一个小噪声块（面积 < minPixels=4）
        img.set(82, 50, true)
        img.set(83, 50, true)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        assertTrue("过小的块不应被检测", result.isEmpty())
    }

    @Test
    fun `纯垂直线（符干残余）不被误判`() {
        val img = blank()
        drawNotehead(img, 100, 50)
        // 在符头左侧放置一根竖直线（高度足够，但只有 1 列 → strokeCount=1 → FLAT）
        // 但宽度只有 1px < MIN_WIDTH(3) → 应被尺寸过滤
        for (y in 45..55) img.set(82, y, true)

        val nhs = listOf(makeNh(100, 50))
        val result = runDetect(img, nhs)

        // 单列竖线宽度=1 < minWidth(3)，应被过滤
        assertTrue("单列竖线不应被误判为降号", result.isEmpty())
    }
}
