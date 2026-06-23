package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装饰音(grace note)检测器单元测试。
 *
 * 使用合成二值图像验证：
 * - 基本装饰音检测（小符头 + 大符头）
 * - 尺寸判别（相似大小 → 非装饰音）
 * - 邻近性约束（太远/太近/左侧 → 非装饰音）
 * - 斜线检测（短前倚音 vs 长前倚音）
 * - 多装饰音、多系统、边界情况
 */
class GraceNoteDetectorTest {

    private val width = 300
    private val height = 120
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /**
     * 绘制实心椭圆符头。
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

    /**
     * 绘制竖直符干。
     */
    private fun drawStem(img: BinaryImage, x: Int, yTop: Int, yBot: Int) {
        for (y in yTop..yBot) {
            if (y in 0 until height) img.set(x, y, true)
        }
    }

    /**
     * 绘制穿过符干的对角斜杠（短前倚音标志）。
     * 从 (x0, y0) 到 (x1, y1) 画一条对角线。
     */
    private fun drawSlash(img: BinaryImage, x0: Int, y0: Int, x1: Int, y1: Int) {
        val steps = maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
        for (i in 0..steps) {
            val t = if (steps == 0) 0.0 else i.toDouble() / steps
            val x = (x0 + t * (x1 - x0)).toInt()
            val y = (y0 + t * (y1 - y0)).toInt()
            if (x in 0 until width && y in 0 until height) img.set(x, y, true)
        }
    }

    /** 普通符头（9×7，面积 63）。 */
    private fun regularNh(cx: Int, cy: Int): Notehead = Notehead(cx, cy, 9, 7, 63)

    /** 装饰音小符头（5×4，面积 20）。 */
    private fun graceNh(cx: Int, cy: Int): Notehead = Notehead(cx, cy, 5, 4, 20)

    // ---- 基本检测 ------------------------------------------------------------

    @Test
    fun `small notehead before larger notehead is detected as grace note`() {
        // 装饰音 (cx=50) 在主音符 (cx=70) 左侧，面积显著更小。
        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertEquals(1, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(1, result[0].mainNoteheadIdx)
    }

    @Test
    fun `two regular-sized noteheads are not grace notes`() {
        val nhs = listOf(regularNh(50, 60), regularNh(80, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertTrue("Equal-sized noteheads should not be grace notes", result.isEmpty())
    }

    // ---- 邻近性约束 ----------------------------------------------------------

    @Test
    fun `grace note too far right is not detected`() {
        // 间距 = 250 - 50 = 200px = 20s > MAX_GAP(2.0s = 20px)
        val nhs = listOf(graceNh(50, 60), regularNh(250, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertTrue("Grace note too far away should not be detected", result.isEmpty())
    }

    @Test
    fun `grace note to the left of main note is not detected when main is on left`() {
        // 小符头在大符头右侧 → 不是装饰音（装饰音在主音符左侧）。
        val nhs = listOf(regularNh(50, 60), graceNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertTrue("Small note to the right of main should not be detected", result.isEmpty())
    }

    @Test
    fun `grace note too far vertically is not detected`() {
        // Y 差异 = |60 - 95| = 35px = 3.5s > MAX_Y_DIFF(2.5s = 25px)
        val nhs = listOf(graceNh(50, 60), regularNh(70, 95))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertTrue("Grace note too far vertically should not be detected", result.isEmpty())
    }

    @Test
    fun `grace note within vertical tolerance is detected`() {
        // Y 差异 = |60 - 75| = 15px = 1.5s < MAX_Y_DIFF(2.5s)
        val nhs = listOf(graceNh(50, 60), regularNh(70, 75))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertEquals(1, result.size)
    }

    // ---- 斜线检测 ------------------------------------------------------------

    @Test
    fun `grace note with slash through stem is acciaccatura`() {
        val img = blank()
        // 装饰音小符头在 (50, 60)，符干向上 (x=52, y=40..56)
        drawNotehead(img, 50, 60, rx = 2, ry = 2)
        drawStem(img, 52, 40, 58)
        // 斜杠从 (48, 48) 到 (56, 52) —— 穿过符干的对角线
        drawSlash(img, 48, 48, 56, 52)

        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, img, s)

        assertEquals(1, result.size)
        assertTrue("Grace note with slash should be acciaccatura", result[0].hasSlash)
    }

    @Test
    fun `grace note without slash is appoggiatura`() {
        val img = blank()
        // 装饰音小符头 + 符干，但无斜杠
        drawNotehead(img, 50, 60, rx = 2, ry = 2)
        drawStem(img, 52, 40, 58)

        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, img, s)

        assertEquals(1, result.size)
        assertFalse("Grace note without slash should be appoggiatura", result[0].hasSlash)
    }

    @Test
    fun `grace note with slash below stem-down note is acciaccatura`() {
        val img = blank()
        // 装饰音小符头在 (50, 60)，符干向下 (x=52, y=62..80)
        drawNotehead(img, 50, 60, rx = 2, ry = 2)
        drawStem(img, 52, 62, 80)
        // 斜杠从 (48, 68) 到 (56, 72)
        drawSlash(img, 48, 68, 56, 72)

        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, img, s)

        assertEquals(1, result.size)
        assertTrue("Grace note with slash below should be acciaccatura", result[0].hasSlash)
    }

    @Test
    fun `bare stem without slash is not acciaccatura`() {
        val img = blank()
        // 粗符干 (3px 宽) 但无对角斜杠
        drawNotehead(img, 50, 60, rx = 2, ry = 2)
        for (x in 51..53) drawStem(img, x, 40, 58)

        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, img, s)

        assertEquals(1, result.size)
        assertFalse("Thick stem without diagonal should not be acciaccatura", result[0].hasSlash)
    }

    // ---- 多装饰音 ------------------------------------------------------------

    @Test
    fun `multiple grace notes before different main notes are detected`() {
        val nhs = listOf(
            graceNh(30, 60), regularNh(50, 60),  // 第一组
            graceNh(80, 60), regularNh(100, 60)  // 第二组
        )
        val systems = listOf(0, 0, 0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertEquals(2, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(1, result[0].mainNoteheadIdx)
        assertEquals(2, result[1].noteheadIdx)
        assertEquals(3, result[1].mainNoteheadIdx)
    }

    @Test
    fun `grace note attaches to nearest main note`() {
        // 装饰音在 (40,60)，两个普通音符在 (60,60) 和 (80,60)
        // 装饰音应附着到最近的 (60,60)
        val nhs = listOf(graceNh(40, 60), regularNh(60, 60), regularNh(80, 60))
        val systems = listOf(0, 0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertEquals(1, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(1, result[0].mainNoteheadIdx)
    }

    // ---- 多系统 --------------------------------------------------------------

    @Test
    fun `grace notes in different systems are detected independently`() {
        val nhs = listOf(
            graceNh(40, 30), regularNh(60, 30),  // 系统 0
            graceNh(40, 80), regularNh(60, 80)   // 系统 1
        )
        val systems = listOf(0, 0, 1, 1)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertEquals(2, result.size)
        assertEquals(0, result[0].noteheadIdx)
        assertEquals(2, result[1].noteheadIdx)
    }

    @Test
    fun `grace note does not match main note in different system`() {
        // 装饰音在系统 0，但唯一的大符头在系统 1
        val nhs = listOf(graceNh(40, 30), regularNh(60, 80))
        val systems = listOf(0, 1)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)

        assertTrue("Grace note should not match main note in different system", result.isEmpty())
    }

    // ---- 边界情况 ------------------------------------------------------------

    @Test
    fun `empty noteheads returns empty`() {
        val result = GraceNoteDetector.detect(emptyList(), emptyList(), blank(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single notehead returns empty`() {
        val nhs = listOf(regularNh(50, 60))
        val systems = listOf(0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `system indices shorter than noteheads uses default system`() {
        // systemIndices 为空 → 所有符头默认系统 0
        val nhs = listOf(graceNh(50, 60), regularNh(70, 60))
        val result = GraceNoteDetector.detect(nhs, emptyList(), blank(), s)
        assertEquals(1, result.size)
    }

    @Test
    fun `grace note with no main note to the right is not detected`() {
        // 只有一个小符头和一个大符头，但大符头在左侧
        val nhs = listOf(regularNh(50, 60), graceNh(70, 60))
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `two grace notes and one regular note - both grace notes detected`() {
        // 测试最大值法：两个小符头(20) + 一个大符头(63)
        // max=63, threshold=0.55*63=34.65, 小符头 20<34.65 ✓
        // 多个装饰音紧邻排列：dx(40→60)=20=MAX_GAP(2.0s), dx(50→60)=10 ✓
        val nhs = listOf(
            graceNh(40, 60), graceNh(50, 60), regularNh(60, 60)
        )
        val systems = listOf(0, 0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)
        assertEquals(2, result.size)
    }

    // ---- 回归保护 ------------------------------------------------------------

    @Test
    fun `chord members at same X are not grace notes`() {
        // 两个符头在同一 X（和弦），大小不同
        val nhs = listOf(
            Notehead(50, 55, 5, 4, 20),
            Notehead(50, 65, 9, 7, 63)
        )
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)
        // dx=0, 不满足 dx > minGap
        assertTrue("Chord member at same X should not be grace note", result.isEmpty())
    }

    @Test
    fun `medium-sized notehead is not grace note`() {
        // 中等大小符头（面积 45 = 0.71 × 63），不满足 < 0.55 阈值
        val nhs = listOf(
            Notehead(50, 60, 7, 6, 42),
            Notehead(70, 60, 9, 7, 63)
        )
        val systems = listOf(0, 0)
        val result = GraceNoteDetector.detect(nhs, systems, blank(), s)
        assertTrue("Medium-sized notehead should not be grace note", result.isEmpty())
    }
}
