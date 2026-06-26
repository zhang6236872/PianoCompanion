package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NavigationInstructionDetector] 的单元测试——使用合成二值图像。
 *
 * 导航指令是乐谱中的文本标记：D.C. (Da Capo)、D.S. (Dal Segno)、al Coda、
 * al Fine、Fine。它们与 [NavigationSymbolDetector] 识别的 Segno/Coda 视觉符号
 * 配合使用，定义非线性播放顺序。
 *
 * 这些测试通过像素级渲染字母模板，验证检测器能够正确识别各种导航指令文本，
 * 同时正确拒绝噪声和无关文本。
 */
class NavigationInstructionDetectorTest {

    private val width = 500
    private val height = 200
    private val s = 10  // 谱线间距

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** 谱线位于 y = 60, 70, 80, 90, 100 → 间距 = 10 */
    private val lineYs = listOf(60, 70, 80, 90, 100)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /** 绘制谱线 */
    private fun drawStaff(img: BinaryImage, lys: List<Int> = lineYs) {
        for (y in lys) {
            for (x in 0 until width) {
                img.set(x, y, true)
            }
        }
    }

    /** 从图像构建连通块列表 */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    /** 渲染单个字母（委托给检测器的 renderLetter 方法） */
    private fun renderLetter(img: BinaryImage, char: Char, x: Int, y: Int, scale: Int = 2) {
        NavigationInstructionDetector.renderLetter(img, char, x, y, scale)
    }

    /** 绘制一个小的句点（用于 D.C./D.S. 中的句点） */
    private fun renderPeriod(img: BinaryImage, x: Int, y: Int, size: Int = 3) {
        for (dy in 0 until size) {
            for (dx in 0 until size) {
                if (x + dx in 0 until img.width && y + dy in 0 until img.height) {
                    img.set(x + dx, y + dy, true)
                }
            }
        }
    }

    // ===== D.C. (Da Capo) 检测 ================================================

    /**
     * 渲染 "D.C." 文本：字母 D, C + 句点。
     * @return 最后一个绘制元素的右边界 x 坐标
     */
    private fun renderDC(img: BinaryImage, x: Int, y: Int, scale: Int = 2): Int {
        renderLetter(img, 'D', x, y, scale)
        val gap = scale * 2
        renderLetter(img, 'C', x + 5 * scale + gap, y, scale)
        val periodX = x + 10 * scale + 2 * gap + scale
        renderPeriod(img, periodX, y + 6 * scale)
        return periodX + 4
    }

    @Test
    fun `D_C text above staff is detected as DA_CAPO`() {
        val img = blank()
        drawStaff(img)
        renderDC(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        val dcCount = results.count { it.type == NavigationInstructionDetector.NavigationInstructionType.DA_CAPO }
        assertEquals("应检测到 1 个 D.C. (Da Capo)", 1, dcCount)
    }

    @Test
    fun `D_C detection assigns correct system index`() {
        val img = blank()
        drawStaff(img)
        renderDC(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("D.C. 应属于系统 0", results.any { it.systemIdx == 0 })
    }

    // ===== D.S. (Dal Segno) 检测 ==============================================

    private fun renderDS(img: BinaryImage, x: Int, y: Int, scale: Int = 2): Int {
        renderLetter(img, 'D', x, y, scale)
        val gap = scale * 2
        renderLetter(img, 'S', x + 5 * scale + gap, y, scale)
        val periodX = x + 10 * scale + 2 * gap + scale
        renderPeriod(img, periodX, y + 6 * scale)
        return periodX + 4
    }

    @Test
    fun `D_S text above staff is detected as DAL_SEGNO`() {
        val img = blank()
        drawStaff(img)
        renderDS(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        val dsCount = results.count { it.type == NavigationInstructionDetector.NavigationInstructionType.DAL_SEGNO }
        assertEquals("应检测到 1 个 D.S. (Dal Segno)", 1, dsCount)
    }

    // ===== Fine 检测 ==========================================================

    /**
     * 渲染 "Fine" 文本：F + i + n + e。
     * 注意：'i' 不在字母模板中（点会被过滤），用 'l' 代替作为窄竖线。
     */
    private fun renderFine(img: BinaryImage, x: Int, y: Int, scale: Int = 2): Int {
        val gap = scale  // 词内间距较小
        renderLetter(img, 'F', x, y, scale)
        var cx = x + 5 * scale + gap
        renderLetter(img, 'l', cx, y, scale)  // 'i' 用竖线近似
        cx += 5 * scale + gap
        renderLetter(img, 'n', cx, y, scale)
        cx += 5 * scale + gap
        renderLetter(img, 'e', cx, y, scale)
        return cx + 5 * scale
    }

    @Test
    fun `Fine text above staff is detected as FINE`() {
        val img = blank()
        drawStaff(img)
        renderFine(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        val fineCount = results.count { it.type == NavigationInstructionDetector.NavigationInstructionType.FINE }
        assertEquals("应检测到 1 个 Fine", 1, fineCount)
    }

    // ===== al Coda 检测 =======================================================

    /**
     * 渲染 "al Coda" 文本：a + l（词1）+ 空格 + C + o + d + a（词2）。
     */
    private fun renderAlCoda(img: BinaryImage, x: Int, y: Int, scale: Int = 2): Int {
        val smallGap = scale   // 词内间距
        val bigGap = scale * 6 // 词间间距（需 > 1.0 * lineSpacing 才能正确分词）

        renderLetter(img, 'a', x, y, scale)
        var cx = x + 5 * scale + smallGap
        renderLetter(img, 'l', cx, y, scale)
        cx += 5 * scale + bigGap

        renderLetter(img, 'C', cx, y, scale)
        cx += 5 * scale + smallGap
        renderLetter(img, 'o', cx, y, scale)
        cx += 5 * scale + smallGap
        renderLetter(img, 'd', cx, y, scale)
        cx += 5 * scale + smallGap
        renderLetter(img, 'a', cx, y, scale)
        return cx + 5 * scale
    }

    @Test
    fun `al Coda text above staff is detected as AL_CODA`() {
        val img = blank()
        drawStaff(img)
        renderAlCoda(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        val alCodaCount = results.count { it.type == NavigationInstructionDetector.NavigationInstructionType.AL_CODA }
        assertEquals("应检测到 1 个 al Coda", 1, alCodaCount)
    }

    // ===== al Fine 检测 =======================================================

    private fun renderAlFine(img: BinaryImage, x: Int, y: Int, scale: Int = 2): Int {
        val smallGap = scale
        val bigGap = scale * 6 // 词间间距（需 > 1.0 * lineSpacing 才能正确分词）

        renderLetter(img, 'a', x, y, scale)
        var cx = x + 5 * scale + smallGap
        renderLetter(img, 'l', cx, y, scale)
        cx += 5 * scale + bigGap

        renderLetter(img, 'F', cx, y, scale)
        cx += 5 * scale + smallGap
        renderLetter(img, 'l', cx, y, scale)
        cx += 5 * scale + smallGap
        renderLetter(img, 'n', cx, y, scale)
        cx += 5 * scale + smallGap
        renderLetter(img, 'e', cx, y, scale)
        return cx + 5 * scale
    }

    @Test
    fun `al Fine text above staff is detected as AL_FINE`() {
        val img = blank()
        drawStaff(img)
        renderAlFine(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        val alFineCount = results.count { it.type == NavigationInstructionDetector.NavigationInstructionType.AL_FINE }
        assertEquals("应检测到 1 个 al Fine", 1, alFineCount)
    }

    // ===== 组合指令检测 =======================================================

    @Test
    fun `D_C al Coda combination is fully detected`() {
        val img = blank()
        drawStaff(img)
        val gap = 4
        var x = 100
        x = renderDC(img, x, 20, scale = 2)
        x += gap * 6
        renderAlCoda(img, x, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("应检测到 DA_CAPO", results.any { it.type == NavigationInstructionDetector.NavigationInstructionType.DA_CAPO })
        assertTrue("应检测到 AL_CODA", results.any { it.type == NavigationInstructionDetector.NavigationInstructionType.AL_CODA })
    }

    @Test
    fun `D_S al Fine combination is fully detected`() {
        val img = blank()
        drawStaff(img)
        val gap = 4
        var x = 100
        x = renderDS(img, x, 20, scale = 2)
        x += gap * 6
        renderAlFine(img, x, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("应检测到 DAL_SEGNO", results.any { it.type == NavigationInstructionDetector.NavigationInstructionType.DAL_SEGNO })
        assertTrue("应检测到 AL_FINE", results.any { it.type == NavigationInstructionDetector.NavigationInstructionType.AL_FINE })
    }

    // ===== 多系统检测 =========================================================

    @Test
    fun `navigation instruction in second system is detected`() {
        val img = BinaryImage.blank(width, 400)
        // 第一个系统的谱线
        for (y in listOf(60, 70, 80, 90, 100)) {
            for (x in 0 until width) img.set(x, y, true)
        }
        // 第二个系统的谱线
        for (y in listOf(260, 270, 280, 290, 300)) {
            for (x in 0 until width) img.set(x, y, true)
        }
        val sys1 = StaffSystem(listOf(60, 70, 80, 90, 100).map { StaffLine(it - 1, it + 1, 1.0) })
        val sys2 = StaffSystem(listOf(260, 270, 280, 290, 300).map { StaffLine(it - 1, it + 1, 1.0) })

        // 在第二个系统上方渲染 Fine
        renderFine(img, 200, 210, scale = 2)
        val bl = ConnectedComponents.label(img, minPixels = 4)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(sys1, sys2), s)
        assertTrue("Fine 应在系统 1 中检测到", results.any {
            it.type == NavigationInstructionDetector.NavigationInstructionType.FINE && it.systemIdx == 1
        })
    }

    // ===== 误判拒绝 ===========================================================

    @Test
    fun `random noise above staff is not detected`() {
        val img = blank()
        drawStaff(img)
        // 随机点噪声
        for (i in 0 until 30) {
            val x = 80 + (i * 7) % 100
            val y = 20 + (i * 5) % 30
            img.set(x, y, true)
        }
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("随机噪声不应产生导航指令", results.isEmpty())
    }

    @Test
    fun `single letter above staff is not detected`() {
        val img = blank()
        drawStaff(img)
        // 单个字母 D
        renderLetter(img, 'D', 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("单个字母不应产生导航指令", results.isEmpty())
    }

    @Test
    fun `text below staff is not detected as navigation instruction`() {
        val img = blank()
        drawStaff(img)
        // 在谱线下方渲染 D.C.（不应该被检测到，搜索区域是上方）
        renderDC(img, 100, 120, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("谱表下方的文本不应被检测为导航指令", results.isEmpty())
    }

    // ===== 空输入处理 =========================================================

    @Test
    fun `empty blobs returns empty list`() {
        val img = blank()
        drawStaff(img)
        val results = NavigationInstructionDetector.detect(img, emptyList(), listOf(makeSystem()), s)
        assertTrue("空连通块应返回空列表", results.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty list`() {
        val img = blank()
        drawStaff(img)
        renderDC(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, listOf(makeSystem()), 0)
        assertTrue("谱线间距为 0 应返回空列表", results.isEmpty())
    }

    @Test
    fun `no systems returns empty list`() {
        val img = blank()
        drawStaff(img)
        renderDC(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = NavigationInstructionDetector.detect(img, bl, emptyList(), s)
        assertTrue("无谱表系统应返回空列表", results.isEmpty())
    }
}
