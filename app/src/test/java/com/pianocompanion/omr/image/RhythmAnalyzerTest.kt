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
}
