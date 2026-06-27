package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TempoChangeDetector] 的单元测试——使用合成二值图像。
 *
 * 速度变化文字是乐谱中的表现性指令：rit. (渐慢)、rall. (渐慢)、riten. (突慢)、
 * accel. (渐快)、a tempo (回原速)。它们与 [TempoMarkingDetector] 识别的绝对速度
 * （♩=120）互补：后者是固定节拍值，本检测器识别的是速度变化趋势，仅产生信息性提示。
 *
 * 这些测试通过像素级渲染字母模板（i 用 l 近似），验证检测器能够正确识别各种速度变化
 * 文本，同时正确拒绝噪声和无关文本。约定：小写 i 由点+竖线组成（不连通），测试中用 l
 * 模板渲染 i 的竖干。
 */
class TempoChangeDetectorTest {

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
        TempoChangeDetector.renderLetter(img, char, x, y, scale)
    }

    /** 绘制一个小的句点（用于 rit./accel. 等缩写末尾） */
    private fun renderPeriod(img: BinaryImage, x: Int, y: Int, size: Int = 3) {
        for (dy in 0 until size) {
            for (dx in 0 until size) {
                if (x + dx in 0 until img.width && y + dy in 0 until img.height) {
                    img.set(x + dx, y + dy, true)
                }
            }
        }
    }

    // ===== 渲染辅助 ===========================================================

    /**
     * 渲染一组小写字母组成的单词。词内间距 = scale，所有字母在同一单词。
     * @return 最后绘制的右边界
     */
    private fun renderWord(img: BinaryImage, word: String, x: Int, y: Int, scale: Int = 2): Int {
        val gap = scale
        var cx = x
        for ((idx, ch) in word.withIndex()) {
            if (idx > 0) cx += 5 * scale + gap
            // 'i' 不在模板表中，用 'l' 近似（竖干）
            val renderCh = if (ch == 'i') 'l' else ch
            renderLetter(img, renderCh, cx, y, scale)
        }
        return cx + 5 * scale
    }

    // ===== rit. (RITARDANDO) 检测 =============================================

    @Test
    fun `rit text above staff is detected as RITARDANDO`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "rit", 100, 20, scale = 2)
        renderPeriod(img, 168, 32)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        val count = results.count { it.type == TempoChangeDetector.TempoChangeType.RITARDANDO }
        assertEquals("应检测到 1 个 rit. (渐慢)", 1, count)
    }

    @Test
    fun `rit detection assigns correct system index`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "rit", 100, 20, scale = 2)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("rit. 应属于系统 0", results.any { it.systemIdx == 0 })
    }

    // ===== rall. (RALLENTANDO) 检测 ===========================================

    @Test
    fun `rall text above staff is detected as RALLENTANDO`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "rall", 100, 20, scale = 2)
        renderPeriod(img, 196, 32)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        val count = results.count { it.type == TempoChangeDetector.TempoChangeType.RALLENTANDO }
        assertEquals("应检测到 1 个 rall. (渐慢)", 1, count)
    }

    // ===== riten. (RITENUTO) 检测 =============================================

    @Test
    fun `riten text above staff is detected as RITENUTO`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "riten", 100, 20, scale = 2)
        renderPeriod(img, 224, 32)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        val count = results.count { it.type == TempoChangeDetector.TempoChangeType.RITENUTO }
        assertEquals("应检测到 1 个 riten. (突慢)", 1, count)
    }

    // ===== accel. (ACCELERANDO) 检测 ==========================================

    @Test
    fun `accel text above staff is detected as ACCELERANDO`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "accel", 100, 20, scale = 2)
        renderPeriod(img, 224, 32)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        val count = results.count { it.type == TempoChangeDetector.TempoChangeType.ACCELERANDO }
        assertEquals("应检测到 1 个 accel. (渐快)", 1, count)
    }

    // ===== a tempo (A_TEMPO) 检测 =============================================

    /**
     * 渲染 "a tempo" 文本：a（词1）+ 空格 + t,e,m,p,o（词2）。
     * 词间间距需 > 1.0 * lineSpacing (=10) 才能正确分词。
     */
    private fun renderATempo(img: BinaryImage, x: Int, y: Int, scale: Int = 2): Int {
        val smallGap = scale
        val bigGap = scale * 6  // 词间间距 = 12 > 10

        renderLetter(img, 'a', x, y, scale)
        var cx = x + 5 * scale + bigGap
        cx = renderWord(img, "tempo", cx, y, scale)
        return cx
    }

    @Test
    fun `a tempo text above staff is detected as A_TEMPO`() {
        val img = blank()
        drawStaff(img)
        renderATempo(img, 100, 20, scale = 2)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        val count = results.count { it.type == TempoChangeDetector.TempoChangeType.A_TEMPO }
        assertEquals("应检测到 1 个 a tempo (回原速)", 1, count)
    }

    // ===== 组合指令检测 =======================================================

    @Test
    fun `rit and accel combination in same line is fully detected`() {
        val img = blank()
        drawStaff(img)
        val scale = 2
        // rit. （词1）+ 词间间距 >10px（>1.0×谱线间距）+ accel.（词2），同一文本行
        val endRit = renderWord(img, "rit", 100, 20, scale)
        renderWord(img, "accel", endRit + 14, 20, scale)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("应检测到 RITARDANDO", results.any { it.type == TempoChangeDetector.TempoChangeType.RITARDANDO })
        assertTrue("应检测到 ACCELERANDO", results.any { it.type == TempoChangeDetector.TempoChangeType.ACCELERANDO })
    }

    // ===== 多系统检测 =========================================================

    @Test
    fun `tempo change in second system is detected`() {
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

        // 在第二个系统上方渲染 accel.
        renderWord(img, "accel", 200, 210, scale = 2)
        val bl = ConnectedComponents.label(img, minPixels = 4)
        val results = TempoChangeDetector.detect(img, bl, listOf(sys1, sys2), s)
        assertTrue("accel. 应在系统 1 中检测到", results.any {
            it.type == TempoChangeDetector.TempoChangeType.ACCELERANDO && it.systemIdx == 1
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
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("随机噪声不应产生速度变化指令", results.isEmpty())
    }

    @Test
    fun `single letter above staff is not detected`() {
        val img = blank()
        drawStaff(img)
        // 单个字母 r
        renderLetter(img, 'r', 100, 20, scale = 2)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("单个字母不应产生速度变化指令", results.isEmpty())
    }

    @Test
    fun `text below staff is not detected as tempo change`() {
        val img = blank()
        drawStaff(img)
        // 在谱线下方渲染 rit.（不应被检测到，搜索区域是上方）
        renderWord(img, "rit", 100, 120, scale = 2)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("谱表下方的文本不应被检测为速度变化指令", results.isEmpty())
    }

    // ===== 空输入处理 =========================================================

    @Test
    fun `empty blobs returns empty list`() {
        val img = blank()
        drawStaff(img)
        val results = TempoChangeDetector.detect(img, emptyList(), listOf(makeSystem()), s)
        assertTrue("空连通块应返回空列表", results.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty list`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "rit", 100, 20, scale = 2)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, listOf(makeSystem()), 0)
        assertTrue("谱线间距为 0 应返回空列表", results.isEmpty())
    }

    @Test
    fun `no systems returns empty list`() {
        val img = blank()
        drawStaff(img)
        renderWord(img, "rit", 100, 20, scale = 2)
        val bl = blobs(img)
        val results = TempoChangeDetector.detect(img, bl, emptyList(), s)
        assertTrue("无谱表系统应返回空列表", results.isEmpty())
    }
}
