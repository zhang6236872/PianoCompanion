package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：用像素级手绘的合成符头/符干/横梁验证 [RhythmAnalyzer]。
 * 不依赖真实照片或 Android 设备。
 */
class RhythmAnalyzerTest {

    private val s = 10          // 谱线间距
    private val w = 300
    private val h = 160

    private fun blank() = BinaryImage.blank(w, h)

    /** 实心椭圆符头。 */
    private fun filledEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ndx = (x - cx).toDouble() / rx
            val ndy = (y - cy).toDouble() / ry
            if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
        }
    }

    /** 空心(环状)椭圆符头。 */
    private fun hollowEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ndx = (x - cx).toDouble() / rx
            val ndy = (y - cy).toDouble() / ry
            val d = ndx * ndx + ndy * ndy
            if (d <= 1.01 && d >= 0.45) img.set(x, y, true)
        }
    }

    /** 符干在符头右侧向上，长度 len（默认 2.4 个谱线间距）。 */
    private fun stemUp(img: BinaryImage, cx: Int, cy: Int, len: Int = 24) {
        val x = cx + 4
        val topY = cy - 3
        for (y in topY downTo topY - len) if (y in 0 until h) img.set(x, y, true)
    }

    /** 符干在符头左侧向下。 */
    private fun stemDown(img: BinaryImage, cx: Int, cy: Int, len: Int = 24) {
        val x = cx - 4
        val botY = cy + 3
        for (y in botY..botY + len) if (y in 0 until h) img.set(x, y, true)
    }

    /** 厚度 thick 的水平横梁，从 x1 到 x2，中心在 y。 */
    private fun hBeam(img: BinaryImage, x1: Int, x2: Int, y: Int, thick: Int = 3) {
        val half = thick / 2
        for (yy in y - half..y + half) for (x in minOf(x1, x2)..maxOf(x1, x2)) {
            if (x in 0 until w && yy in 0 until h) img.set(x, yy, true)
        }
    }

    /** 小实心圆点（附点），半径 r（默认 2）。 */
    private fun dot(img: BinaryImage, cx: Int, cy: Int, r: Int = 2) {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ndx = (x - cx).toDouble() / r
            val ndy = (y - cy).toDouble() / r
            if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
        }
    }

    /** 细长竖线（模拟符干），高 height。 */
    private fun vLine(img: BinaryImage, x: Int, cy: Int, height: Int = 20) {
        val top = cy - height / 2
        for (y in top until top + height) if (y in 0 until h) img.set(x, y, true)
    }

    /**
     * 符尾(flag)：附着在符干末端的水平卷曲墨迹。从 [stemX] 起、向 [dir] 方向延伸
     * [width] 像素、垂直 [thick] 像素的一条水平带，中心行在 [rowY]。
     * 多个符尾可通过不同 [rowY] 堆叠（沿符干方向逐层下移/上移）。
     *
     * @param dir +1 = 向右卷曲，-1 = 向左卷曲。
     */
    private fun flag(img: BinaryImage, stemX: Int, rowY: Int, width: Int = 8, thick: Int = 2, dir: Int = +1) {
        val half = thick / 2
        val xRange = if (dir >= 0) (stemX until stemX + width) else (stemX downTo stemX - width + 1)
        for (yy in rowY - half..rowY + half) for (x in xRange) {
            if (x in 0 until w && yy in 0 until h) img.set(x, yy, true)
        }
    }

    private fun nh(cx: Int, cy: Int) = Notehead(cx, cy, 9, 7, 60)

    // ---- 纯分类逻辑 ----------------------------------------------------------

    @Test
    fun `classify maps feature combinations to durations`() {
        assertEquals(NoteDuration.WHOLE, RhythmAnalyzer.classify(filled = false, hasStem = false, tailCount = 0))
        assertEquals(NoteDuration.HALF, RhythmAnalyzer.classify(filled = false, hasStem = true, tailCount = 0))
        assertEquals(NoteDuration.QUARTER, RhythmAnalyzer.classify(filled = true, hasStem = true, tailCount = 0))
        assertEquals(NoteDuration.EIGHTH, RhythmAnalyzer.classify(filled = true, hasStem = true, tailCount = 1))
        assertEquals(NoteDuration.SIXTEENTH, RhythmAnalyzer.classify(filled = true, hasStem = true, tailCount = 2))
        assertEquals(NoteDuration.THIRTY_SECOND, RhythmAnalyzer.classify(filled = true, hasStem = true, tailCount = 3))
        // 实心无干：保守按四分音符（符干漏检时不会给出过长的时值）
        assertEquals(NoteDuration.QUARTER, RhythmAnalyzer.classify(filled = true, hasStem = false, tailCount = 0))
    }

    // ---- 填充判定 ------------------------------------------------------------

    @Test
    fun `filled notehead is detected as filled`() {
        val img = blank()
        filledEllipse(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertTrue("实心符头应判定为 filled", r[0].filled)
    }

    @Test
    fun `hollow notehead is detected as not filled`() {
        val img = blank()
        hollowEllipse(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertFalse("空心符头应判定为非 filled", r[0].filled)
    }

    // ---- 符干判定 ------------------------------------------------------------

    @Test
    fun `notehead with upward stem detects a stem`() {
        val img = blank()
        filledEllipse(img, 60, 60)
        stemUp(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertTrue("应检测到符干", r[0].hasStem)
        assertTrue("符干方向应为向上", r[0].stemUp)
    }

    @Test
    fun `notehead with downward stem detects a stem`() {
        val img = blank()
        filledEllipse(img, 60, 90)
        stemDown(img, 60, 90)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 90)), s)
        assertTrue("应检测到符干", r[0].hasStem)
        assertFalse("符干方向应为向下", r[0].stemUp)
    }

    @Test
    fun `notehead without stem has no stem`() {
        val img = blank()
        filledEllipse(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertFalse("无符干时不应误判", r[0].hasStem)
    }

    // ---- 完整时值判定 --------------------------------------------------------

    @Test
    fun `hollow notehead without stem is a whole note`() {
        val img = blank()
        hollowEllipse(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(NoteDuration.WHOLE, r[0].duration)
    }

    @Test
    fun `hollow notehead with stem is a half note`() {
        val img = blank()
        hollowEllipse(img, 60, 60)
        stemUp(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(NoteDuration.HALF, r[0].duration)
    }

    @Test
    fun `filled notehead with stem and no tail is a quarter note`() {
        val img = blank()
        filledEllipse(img, 60, 60)
        stemUp(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(NoteDuration.QUARTER, r[0].duration)
    }

    @Test
    fun `beamed eighth-note pair`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        filledEllipse(img, 130, 60); stemUp(img, 130, 60)
        // 两根符干顶部约在 y = 60 - 3 - 24 = 33
        hBeam(img, 64, 134, 33, thick = 3)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60), nh(130, 60)), s)
        assertEquals(NoteDuration.EIGHTH, r[0].duration)
        assertEquals(NoteDuration.EIGHTH, r[1].duration)
        assertEquals("应检测到 1 层横梁", 1, r[0].beamCount)
    }

    @Test
    fun `beamed sixteenth-note pair has two beam layers`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        filledEllipse(img, 130, 60); stemUp(img, 130, 60)
        hBeam(img, 64, 134, 33, thick = 3) // 第一根横梁
        hBeam(img, 64, 134, 38, thick = 3) // 第二根横梁
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60), nh(130, 60)), s)
        assertEquals(NoteDuration.SIXTEENTH, r[0].duration)
        assertEquals(NoteDuration.SIXTEENTH, r[1].duration)
        assertEquals("应检测到 2 层横梁", 2, r[0].beamCount)
    }

    @Test
    fun `quarter and half notes keep different durations`() {
        val img = blank()
        // 第一个：二分音符（空心+干）
        hollowEllipse(img, 50, 60); stemUp(img, 50, 60)
        // 第二个：四分音符（实心+干）
        filledEllipse(img, 140, 60); stemUp(img, 140, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(50, 60), nh(140, 60)), s)
        assertEquals(NoteDuration.HALF, r[0].duration)
        assertEquals(NoteDuration.QUARTER, r[1].duration)
    }

    @Test
    fun `empty notehead list returns empty result`() {
        val img = blank()
        assertTrue(RhythmAnalyzer.analyze(img, emptyList(), s).isEmpty())
    }

    @Test
    fun `result is aligned one-to-one with input noteheads`() {
        val img = blank()
        hollowEllipse(img, 40, 60)               // whole
        filledEllipse(img, 120, 60); stemUp(img, 120, 60) // quarter
        val nhs = listOf(nh(40, 60), nh(120, 60))
        val r = RhythmAnalyzer.analyze(img, nhs, s)
        assertEquals(2, r.size)
        assertEquals(NoteDuration.WHOLE, r[0].duration)
        assertEquals(NoteDuration.QUARTER, r[1].duration)
    }

    // ---- 附点判定 (augmentation dots) ----------------------------------------

    @Test
    fun `NoteDuration toMillis applies dot multipliers`() {
        val quarterMs = 500L
        // 无附点
        assertEquals(500L, NoteDuration.QUARTER.toMillis(quarterMs, 0))
        // 单附点 ×1.5
        assertEquals(750L, NoteDuration.QUARTER.toMillis(quarterMs, 1))
        // 双附点 ×1.75
        assertEquals(875L, NoteDuration.QUARTER.toMillis(quarterMs, 2))
        // 附点二分 = 3 个四分 = 1500ms
        assertEquals(1500L, NoteDuration.HALF.toMillis(quarterMs, 1))
        // 默认参数 = 无附点
        assertEquals(500L, NoteDuration.QUARTER.toMillis(quarterMs))
    }

    @Test
    fun `filled notehead with stem and a right-side dot is dotted`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        dot(img, 75, 60) // 符头右侧约 1 个谱线间距处的小点
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("基础时值仍为四分音符", NoteDuration.QUARTER, r[0].duration)
        assertEquals("应检测到 1 个附点", 1, r[0].dotCount)
        assertTrue("dotted 应为 true", r[0].dotted)
    }

    @Test
    fun `hollow notehead with stem and a dot is a dotted half`() {
        val img = blank()
        hollowEllipse(img, 60, 60); stemUp(img, 60, 60)
        dot(img, 75, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(NoteDuration.HALF, r[0].duration)
        assertEquals(1, r[0].dotCount)
        // 附点二分 = 2.0 × 1.5 = 3.0 个四分音符
        assertEquals(3.0, r[0].duration.quarterValue * 1.5, 0.001)
    }

    @Test
    fun `whole note can be dotted`() {
        val img = blank()
        hollowEllipse(img, 60, 60) // 无符干 = 全音符
        dot(img, 75, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(NoteDuration.WHOLE, r[0].duration)
        assertEquals(1, r[0].dotCount)
    }

    @Test
    fun `notehead without dot is not dotted`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(0, r[0].dotCount)
        assertFalse("无附点时 dotted 应为 false", r[0].dotted)
    }

    @Test
    fun `double dot is detected as two dots`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        dot(img, 73, 60) // 第一个附点
        dot(img, 79, 60) // 第二个附点
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("应检测到 2 个附点", 2, r[0].dotCount)
        // 双附点四分 = 0.25 + ... → 1.0 × 1.75 = 1.75 个四分音符
        val quarterMs = 500L
        assertEquals(875L, r[0].effectiveMillis(quarterMs))
    }

    @Test
    fun `effectiveMillis honours the dot`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        dot(img, 75, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        val quarterMs = 500L
        // 附点四分 = 1.5 × 500 = 750ms
        assertEquals(750L, r[0].effectiveMillis(quarterMs))
    }

    @Test
    fun `a wide blob to the right (next notehead) is not counted as a dot`() {
        val img = blank()
        // 两个紧邻的实心符头（右侧符头落在附点扫描窗内，但宽度像符头而非附点）
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        filledEllipse(img, 78, 60); stemUp(img, 78, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60), nh(78, 60)), s)
        assertEquals("右侧符头不应被误判为附点", 0, r[0].dotCount)
        assertEquals(0, r[1].dotCount)
    }

    @Test
    fun `a tall thin stroke to the right is not counted as a dot`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        // 符头右侧窗内一根高而窄的竖线（如下一个音符的向下符干残影）
        vLine(img, 75, 60, height = 22)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("高而窄的竖线不应被误判为附点", 0, r[0].dotCount)
    }

    @Test
    fun `dot above the notehead (line-note convention) is detected`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        // 线上音符的附点常画在上方的间内（约高半个谱线间距）
        dot(img, 75, 55)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals(1, r[0].dotCount)
    }

    // ---- 符尾判定 (flags / 非连梁单音符) -------------------------------------

    @Test
    fun `filled notehead with stem and a single flag is an eighth note`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        // 符干顶端在 y = 60 - 3 - 24 = 33，符干右侧 x = 64
        flag(img, stemX = 64, rowY = 33)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("应检测到 1 个符尾", 1, r[0].flagCount)
        assertEquals(NoteDuration.EIGHTH, r[0].duration)
    }

    @Test
    fun `filled notehead with two stacked flags is a sixteenth note`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        flag(img, stemX = 64, rowY = 33)
        flag(img, stemX = 64, rowY = 39) // 第二层符尾，与第一层间隔约 0.6 个谱线间距
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("应检测到 2 个符尾", 2, r[0].flagCount)
        assertEquals(NoteDuration.SIXTEENTH, r[0].duration)
    }

    @Test
    fun `filled notehead with three stacked flags is a thirty-second note`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        flag(img, stemX = 64, rowY = 33)
        flag(img, stemX = 64, rowY = 39)
        flag(img, stemX = 64, rowY = 45)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("应检测到 3 个符尾", 3, r[0].flagCount)
        assertEquals(NoteDuration.THIRTY_SECOND, r[0].duration)
    }

    @Test
    fun `downward stem with a flag is also detected`() {
        val img = blank()
        filledEllipse(img, 60, 90); stemDown(img, 60, 90)
        // 向下符干底端在 y = 90 + 3 + 24 = 117，符干左侧 x = 56；符尾向上卷曲
        flag(img, stemX = 56, rowY = 115, dir = +1)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 90)), s)
        assertEquals("向下符干的符尾也应被检测", 1, r[0].flagCount)
        assertEquals(NoteDuration.EIGHTH, r[0].duration)
    }

    @Test
    fun `flag curling to the left is detected`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        flag(img, stemX = 64, rowY = 33, dir = -1) // 向左卷曲（罕见但需兼容）
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("向左卷曲的符尾也应被检测", 1, r[0].flagCount)
        assertEquals(NoteDuration.EIGHTH, r[0].duration)
    }

    @Test
    fun `bare stem (quarter note) does not produce a flag`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertEquals("裸符干不应误判为符尾", 0, r[0].flagCount)
        assertEquals(NoteDuration.QUARTER, r[0].duration)
    }

    @Test
    fun `flagged notes mixed with quarter notes keep correct counts`() {
        val img = blank()
        // 第一个：八分音符（实心+干+1 符尾）
        filledEllipse(img, 50, 60); stemUp(img, 50, 60)
        flag(img, stemX = 54, rowY = 33)
        // 第二个：四分音符（实心+干，无符尾）
        filledEllipse(img, 140, 60); stemUp(img, 140, 60)
        val r = RhythmAnalyzer.analyze(img, listOf(nh(50, 60), nh(140, 60)), s)
        assertEquals(1, r[0].flagCount)
        assertEquals(NoteDuration.EIGHTH, r[0].duration)
        assertEquals(0, r[1].flagCount)
        assertEquals(NoteDuration.QUARTER, r[1].duration)
    }

    @Test
    fun `flag does not bleed into the notehead region`() {
        val img = blank()
        filledEllipse(img, 60, 60); stemUp(img, 60, 60)
        flag(img, stemX = 64, rowY = 33)
        // 即使有符尾，符头不应被误判为额外的符尾层数（扫描被符头中心截断）
        val r = RhythmAnalyzer.analyze(img, listOf(nh(60, 60)), s)
        assertTrue("符尾层数不应超过 3", r[0].flagCount <= 3)
        assertEquals(1, r[0].flagCount)
    }
}
