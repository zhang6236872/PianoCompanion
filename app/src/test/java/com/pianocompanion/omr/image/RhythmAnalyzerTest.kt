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
}
