package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：用像素级手绘的合成 "×N" 标注验证 [RepeatCountDetector]。
 *
 * 测试覆盖 ×N / N× 两种标注形式、多位数字（×12）、乘号与数字分离判定、
 * 跳房子序号误判防护（纯数字无乘号）、非反复小节线忽略、多谱表系统、
 * 以及与 [BarlineDetector] 的端到端集成。不依赖真实照片或 Android 设备。
 *
 * 几何与 [BarlineDetectorTest] 一致：宽 300 × 高 120；五线谱 y=30,40,50,60,70（间距 s=10）。
 */
class RepeatCountDetectorTest {

    private val s = 10
    private val w = 300
    private val h = 120
    private val lineYs = listOf(30, 40, 50, 60, 70)

    private fun blank() = BinaryImage.blank(w, h)

    private fun system() = StaffSystem(lineYs.map { StaffLine(it, it, 0.5) })

    // ---- 绘图工具 ---------------------------------------------------------- //

    /** 把 5×7 布尔模板按 [scale] 倍放大画入图像。 */
    private fun renderTemplate(
        img: BinaryImage, template: BooleanArray, cols: Int, rows: Int,
        x0: Int, y0: Int, scale: Int
    ) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!template[r * cols + c]) continue
                for (dy in 0 until scale) for (dx in 0 until scale) {
                    val x = x0 + c * scale + dx
                    val y = y0 + r * scale + dy
                    if (x in 0 until w && y in 0 until h) img.set(x, y, true)
                }
            }
        }
    }

    /** 画乘号 ×（使用 [RepeatCountDetector.MULTIPLIER_TEMPLATE]）。 */
    private fun renderMultiplier(img: BinaryImage, x0: Int, y0: Int, scale: Int = 2) {
        renderTemplate(
            img, RepeatCountDetector.MULTIPLIER_TEMPLATE,
            SignatureDetector.GRID_W, SignatureDetector.GRID_H,
            x0, y0, scale
        )
    }

    /** 画数字（使用 [SignatureDetector.DIGIT_TEMPLATES]）。 */
    private fun renderDigit(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int = 2) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("无模板: $digit")
        renderTemplate(img, tmpl, SignatureDetector.GRID_W, SignatureDetector.GRID_H, x0, y0, scale)
    }

    /** 标注带 Y：顶线正上方，scale=2 时字形高 14px。y0=15 → y 15..28。 */
    private val annotY = 15

    /** 反复结束小节线。 */
    private fun repeatEndBarline(cx: Int) = Barline(cx, BarlineType.REPEAT_END, 8)

    /** 对图像做连通块标记，返回墨块列表。 */
    private fun label(img: BinaryImage) = ConnectedComponents.label(img, 4)

    /** 调用检测器（单系统便捷封装）。 */
    private fun detect(
        img: BinaryImage,
        barlines: List<Barline>,
        systems: List<StaffSystem> = listOf(system())
    ): List<RepeatCountDetector.RepeatCount> {
        val blobs = label(img)
        val barlinesBySystem = systems.mapIndexed { idx, _ ->
            if (idx < barlines.size) listOf(barlines[idx]) else emptyList()
        }
        return RepeatCountDetector.detect(img, blobs, barlinesBySystem, systems, s)
    }

    // ---- ×N 形式（乘号在左，数字在右）--------------------------------------- //

    @Test
    fun `×3 above repeat-end barline detected as count 3`() {
        val img = blank()
        // ×3 标注在 x=200 反复结束小节线上方
        renderMultiplier(img, x0 = 190, y0 = annotY)
        renderDigit(img, digit = 3, x0 = 202, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertEquals("应检测到 1 个反复次数标注", 1, results.size)
        assertEquals(3, results[0].count)
        assertEquals(0, results[0].systemIdx)
    }

    @Test
    fun `×2 detected as count 2`() {
        val img = blank()
        renderMultiplier(img, x0 = 190, y0 = annotY)
        renderDigit(img, digit = 2, x0 = 202, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertEquals(1, results.size)
        assertEquals(2, results[0].count)
    }

    @Test
    fun `×4 detected as count 4`() {
        val img = blank()
        renderMultiplier(img, x0 = 190, y0 = annotY)
        renderDigit(img, digit = 4, x0 = 202, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertEquals(1, results.size)
        assertEquals(4, results[0].count)
    }

    // ---- N× 形式（数字在左，乘号在右）--------------------------------------- //

    @Test
    fun `3× form (digit then multiplier) detected as count 3`() {
        val img = blank()
        // 3× 标注，数字在 × 左侧
        renderDigit(img, digit = 3, x0 = 180, y0 = annotY)
        renderMultiplier(img, x0 = 192, y0 = annotY)
        // 小节线居中于标注
        val results = detect(img, listOf(repeatEndBarline(196)))
        assertEquals("N× 形式也应识别", 1, results.size)
        assertEquals(3, results[0].count)
    }

    // ---- 多位数字 ---------------------------------------------------------- //

    @Test
    fun `×12 multi-digit count detected`() {
        val img = blank()
        // ×12：三个字形紧挨排列
        renderMultiplier(img, x0 = 176, y0 = annotY)
        renderDigit(img, digit = 1, x0 = 188, y0 = annotY)
        renderDigit(img, digit = 2, x0 = 200, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(196)))
        assertEquals("应检测到 ×12", 1, results.size)
        assertEquals(12, results[0].count)
    }

    // ---- 误判防护 ---------------------------------------------------------- //

    @Test
    fun `digit only without multiplier is not detected`() {
        val img = blank()
        // 仅有数字 3（模拟跳房子序号），无乘号
        renderDigit(img, digit = 3, x0 = 196, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertTrue("纯数字（无乘号）不应被识别为反复次数", results.isEmpty())
    }

    @Test
    fun `multiplier only without digit returns empty`() {
        val img = blank()
        // 仅有 ×，无数字
        renderMultiplier(img, x0 = 196, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertTrue("仅乘号无数字不应产生反复次数", results.isEmpty())
    }

    @Test
    fun `empty image returns no repeat counts`() {
        val img = blank()
        val results = detect(img, listOf(repeatEndBarline(200)))
        assertTrue("空白图像不应有反复次数", results.isEmpty())
    }

    @Test
    fun `annotation above non-repeat barline is ignored`() {
        val img = blank()
        renderMultiplier(img, x0 = 190, y0 = annotY)
        renderDigit(img, digit = 3, x0 = 202, y0 = annotY)
        // 单竖线（非反复结束）——不应检测反复次数
        val results = detect(img, listOf(Barline(200, BarlineType.SINGLE, 2)))
        assertTrue("非反复结束小节线上方的标注应被忽略", results.isEmpty())
    }

    @Test
    fun `×1 is rejected because count must be at least 2`() {
        val img = blank()
        renderMultiplier(img, x0 = 190, y0 = annotY)
        renderDigit(img, digit = 1, x0 = 202, y0 = annotY)

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertTrue("×1 不合理（反复至少 2 遍），应被拒绝", results.isEmpty())
    }

    @Test
    fun `multiplier and digit too far apart are not grouped`() {
        val img = blank()
        // × 和 3 都在小节线水平窗内，但彼此间距过大
        renderMultiplier(img, x0 = 186, y0 = annotY)   // x 186..195
        renderDigit(img, digit = 3, x0 = 206, y0 = annotY) // x 206..215

        val results = detect(img, listOf(repeatEndBarline(200)))
        assertTrue("乘号与数字间距过大不应组合", results.isEmpty())
    }

    @Test
    fun `no barlines returns empty`() {
        val img = blank()
        renderMultiplier(img, x0 = 190, y0 = annotY)
        renderDigit(img, digit = 3, x0 = 202, y0 = annotY)

        val blobs = label(img)
        val results = RepeatCountDetector.detect(
            img, blobs, emptyList(), listOf(system()), s
        )
        assertTrue("无小节线时不应检测到反复次数", results.isEmpty())
    }

    // ---- 多谱表系统 -------------------------------------------------------- //

    @Test
    fun `repeat count in second system detected`() {
        val img = blank()
        val sys0LineYs = lineYs                       // 30,40,50,60,70
        val sys1LineYs = listOf(100, 110, 120, 130, 140)

        val systems = listOf(
            StaffSystem(sys0LineYs.map { StaffLine(it, it, 0.5) }),
            StaffSystem(sys1LineYs.map { StaffLine(it, it, 0.5) })
        )

        // 第二系统（topLineY=100）上方画 ×3
        // bandTop = max(100-25, 70+10) = max(75, 80) = 80; bandBot = 99
        // y0=85 → y 85..98，在 [80, 99] 内
        renderMultiplier(img, x0 = 140, y0 = 85)
        renderDigit(img, digit = 3, x0 = 152, y0 = 85)

        val blobs = label(img)
        val barlinesBySystem = listOf(emptyList<Barline>(), listOf(repeatEndBarline(150)))
        val results = RepeatCountDetector.detect(img, blobs, barlinesBySystem, systems, s)

        assertEquals("应在第二系统检测到反复次数", 1, results.size)
        assertEquals(3, results[0].count)
        assertEquals(1, results[0].systemIdx)
    }

    // ---- MULTIPLIER_TEMPLATE 基本属性 -------------------------------------- //

    @Test
    fun `MULTIPLIER_TEMPLATE has 5x7 grid`() {
        val tmpl = RepeatCountDetector.MULTIPLIER_TEMPLATE
        assertEquals(
            "乘号模板应为 ${SignatureDetector.GRID_W}×${SignatureDetector.GRID_H} = ${
                SignatureDetector.GRID_W * SignatureDetector.GRID_H
            } 格",
            SignatureDetector.GRID_W * SignatureDetector.GRID_H,
            tmpl.size
        )
        assertTrue("模板应至少有 1 个填充像素", tmpl.any { it })
    }

    // ---- 端到端集成：BarlineDetector + RepeatCountDetector ------------------- //

    @Test
    fun `full pipeline detects ×3 above real repeat-end barline`() {
        val img = blank()

        // 画五线谱
        for (y in lineYs) for (x in 0 until w) img.set(x, y, true)

        // 画反复结束小节线（:‖）——左侧两点 + 细线 + 粗线
        // 细线 cx=150, 粗线 cx=158（合并后中心约 154）
        repeatEndBarlinePixels(img, thinCx = 150, thickCx = 158)
        repeatDotsLeft(img, cx = 150, offsetX = 10)

        // 在小节线上方画 ×3 标注（y0=12 确保 y 12..25，不触碰 y=28 的小节线起点）
        renderMultiplier(img, x0 = 144, y0 = 12)
        renderDigit(img, digit = 3, x0 = 156, y0 = 12)

        // 第一步：用 BarlineDetector 检测小节线
        val barlines = BarlineDetector.detect(img, system())
        assertTrue("应检测到至少 1 条反复结束小节线", barlines.isNotEmpty())
        val repeatEnd = barlines.first { it.type == BarlineType.REPEAT_END }

        // 第二步：用 RepeatCountDetector 检测反复次数
        val blobs = ConnectedComponents.label(img, 4)
        val results = RepeatCountDetector.detect(
            img, blobs, listOf(barlines), listOf(system()), s
        )

        assertEquals("端到端应检测到 1 个反复次数", 1, results.size)
        assertEquals(3, results[0].count)
        assertTrue(
            "反复次数应关联到反复结束小节线附近 (cx=${repeatEnd.centerX})",
            kotlin.math.abs(results[0].centerX - repeatEnd.centerX) <= s * 2
        )
    }

    // ---- 端到端集成辅助方法 ------------------------------------------------ //

    /** 画反复结束小节线的竖线部分（细线 + 粗线）。 */
    private fun repeatEndBarlinePixels(
        img: BinaryImage, thinCx: Int, thickCx: Int,
        thinW: Int = 2, thickW: Int = 6
    ) {
        val yTop = lineYs.first() - 2   // 28
        val yBot = lineYs.last() + 2    // 72
        for (dx in 0 until thinW) img.set(thinCx + dx, yTop, true)
        for (dx in 0 until thickW) img.set(thickCx + dx, yTop, true)
        // 竖线从 yTop 到 yBot
        for (y in yTop..yBot) {
            for (dx in 0 until thinW) img.set(thinCx + dx, y, true)
            for (dx in 0 until thickW) img.set(thickCx + dx, y, true)
        }
    }

    /** 在竖线左侧画一对反复记号圆点。 */
    private fun repeatDotsLeft(img: BinaryImage, cx: Int, offsetX: Int = 10) {
        val dx = cx - offsetX
        dotPixel(img, dx, 45)
        dotPixel(img, dx, 55)
    }

    /** 小型实心圆点。 */
    private fun dotPixel(img: BinaryImage, cx: Int, cy: Int, r: Int = 2) {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ndx = (x - cx).toDouble() / r
            val ndy = (y - cy).toDouble() / r
            if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
        }
    }
}
