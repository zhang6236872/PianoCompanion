package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单元测试 [BinaryDenoiser] —— 二值图像降噪模块。
 *
 * 用手绘合成像素图验证两个核心操作的独立行为与组合行为，重点确认：
 *  - 椒噪声（孤立黑斑）被擦除，而细笔画（1px 谱线、符头椭圆）完整保留；
 *  - 盐噪声（笔画内部白孔）被填充，而谱线间隙/背景不被误填；
 *  - 保守默认对干净图像是恒等操作（无回归）。
 */
class BinaryDenoiserTest {

    // ── removePepper ────────────────────────────────────────────────────────

    @Test
    fun `removePepper erases a single isolated black pixel`() {
        val img = BinaryImage.blank(20, 20)
        img.set(10, 10, true) // isolated speck
        val (out, removed) = BinaryDenoiser.removePepper(img)
        assertEquals("isolated speck should be removed", 1, removed)
        assertFalse("speck pixel should be white after denoise", out.isBlack(10, 10))
    }

    @Test
    fun `removePepper erases a small speck below min area`() {
        val img = BinaryImage.blank(30, 30)
        // 2x2 block = 4 pixels; with default minArea=4 it is kept (>=4).
        img.set(10, 10, true); img.set(11, 10, true)
        img.set(10, 11, true); img.set(11, 11, true)
        val (out, removed) = BinaryDenoiser.removePepper(img, pepperMinArea = 5)
        assertEquals("2x2 block (area 4) removed when minArea=5", 4, removed)
        assertFalse(out.isBlack(10, 10))
    }

    @Test
    fun `removePepper keeps a 1px horizontal staff line intact`() {
        // 一条贯穿全图的 1px 谱线是一个大组件，必须保留。
        val img = BinaryImage.blank(200, 40)
        for (x in 0 until 200) img.set(x, 20, true)
        val (out, removed) = BinaryDenoiser.removePepper(img)
        assertEquals("no pixels should be removed from a full-width line", 0, removed)
        for (x in 0 until 200) {
            assertTrue("line pixel $x should survive", out.isBlack(x, 20))
        }
    }

    @Test
    fun `removePepper keeps a solid notehead ellipse`() {
        val img = BinaryImage.blank(60, 60)
        drawEllipse(img, 30, 30, rx = 5, ry = 4) // ~60px ellipse
        val original = img.totalBlack()
        val (out, removed) = BinaryDenoiser.removePepper(img)
        assertEquals("ellipse should be untouched", 0, removed)
        assertEquals("ink count unchanged", original, out.totalBlack())
    }

    @Test
    fun `removePepper removes multiple scattered specks but keeps glyphs`() {
        val img = BinaryImage.blank(100, 60)
        // A real stroke (horizontal line).
        for (x in 10 until 90) img.set(x, 30, true)
        // Three isolated specks.
        img.set(5, 5, true)
        img.set(95, 5, true)
        img.set(50, 55, true)
        val (out, removed) = BinaryDenoiser.removePepper(img)
        assertEquals("3 specks removed", 3, removed)
        // Stroke preserved.
        for (x in 10 until 90) assertTrue("stroke pixel $x kept", out.isBlack(x, 30))
        assertFalse("speck removed", out.isBlack(5, 5))
        assertFalse("speck removed", out.isBlack(95, 5))
        assertFalse("speck removed", out.isBlack(50, 55))
    }

    // ── fillSalt ────────────────────────────────────────────────────────────

    @Test
    fun `fillSalt fills a single white hole inside a solid block`() {
        val img = BinaryImage.blank(10, 10)
        // 8x8 solid black block with one white pixel in the centre.
        for (y in 1..8) for (x in 1..8) img.set(x, y, true)
        img.set(4, 4, false) // punch a hole
        val (out, filled) = BinaryDenoiser.fillSalt(img)
        assertEquals("the hole should be filled", 1, filled)
        assertTrue("centre pixel now black", out.isBlack(4, 4))
    }

    @Test
    fun `fillSalt does not fill white gaps between staff lines`() {
        // 谱线间距 10px，间隙中的白像素黑邻居很少（≤3），不应被填。
        val img = BinaryImage.blank(200, 60)
        for (y in listOf(20, 30, 40)) for (x in 0 until 200) img.set(x, y, true)
        val (out, filled) = BinaryDenoiser.fillSalt(img)
        assertEquals("no gaps should be filled between widely-spaced lines", 0, filled)
        // Lines remain intact.
        for (y in listOf(20, 30, 40)) for (x in 0 until 200) assertTrue(out.isBlack(x, y))
    }

    @Test
    fun `fillSalt does not touch the background`() {
        val img = BinaryImage.blank(40, 40)
        img.set(20, 20, true) // single black pixel, lots of white around
        val (out, filled) = BinaryDenoiser.fillSalt(img)
        assertEquals("background must not be filled", 0, filled)
        assertEquals(img.totalBlack(), out.totalBlack())
    }

    @Test
    fun `fillSalt fills multiple holes but leaves nearby background alone`() {
        val img = BinaryImage.blank(20, 20)
        // Solid block 5..14 with two holes.
        for (y in 5..14) for (x in 5..14) img.set(x, y, true)
        img.set(8, 8, false)
        img.set(10, 10, false)
        val (out, filled) = BinaryDenoiser.fillSalt(img)
        assertTrue("at least the two interior holes filled (got $filled)", filled >= 2)
        assertTrue(out.isBlack(8, 8))
        assertTrue(out.isBlack(10, 10))
    }

    @Test
    fun `fillSalt with strict threshold leaves a partially-surrounded pixel white`() {
        // A white pixel with only 4 black neighbours (< default 6) stays white.
        val img = BinaryImage.blank(10, 10)
        img.set(4, 5, true); img.set(6, 5, true)
        img.set(5, 4, true); img.set(5, 6, true)
        // centre (5,5) is white with 4 black neighbours.
        val (out, filled) = BinaryDenoiser.fillSalt(img, saltMinNeighbors = 6)
        assertEquals("pixel with 4 neighbours should not be filled", 0, filled)
        assertFalse(out.isBlack(5, 5))
    }

    // ── denoise (combined) ──────────────────────────────────────────────────

    @Test
    fun `denoise is a no-op on a clean image`() {
        val img = BinaryImage.blank(120, 80)
        for (y in listOf(20, 30, 40, 50, 60)) for (x in 0 until 120) img.set(x, y, true)
        drawEllipse(img, 60, 40, rx = 4, ry = 3)
        val originalBlack = img.totalBlack()
        val (out, stats) = BinaryDenoiser.denoise(img)
        assertEquals("no pepper removed on clean image", 0, stats.pepperRemoved)
        assertEquals("no salt filled on clean image", 0, stats.saltFilled)
        assertEquals("total ink unchanged", originalBlack, out.totalBlack())
    }

    @Test
    fun `denoise removes specks and fills holes in one pass`() {
        val img = BinaryImage.blank(40, 40)
        // A solid block with a hole + an isolated speck nearby.
        for (y in 10..20) for (x in 10..20) img.set(x, y, true)
        img.set(15, 15, false) // hole
        img.set(35, 35, true)  // isolated speck
        val (out, stats) = BinaryDenoiser.denoise(img)
        assertTrue("speck should be removed (removed=${stats.pepperRemoved})", stats.pepperRemoved >= 1)
        assertTrue("hole should be filled (filled=${stats.saltFilled})", stats.saltFilled >= 1)
        assertFalse("speck gone", out.isBlack(35, 35))
        assertTrue("hole filled", out.isBlack(15, 15))
    }

    @Test
    fun `denoise stats totalChanged sums both operations`() {
        val img = BinaryImage.blank(30, 30)
        // 3 isolated specks.
        img.set(2, 2, true); img.set(28, 2, true); img.set(2, 28, true)
        // Solid block with 1 hole.
        for (y in 12..20) for (x in 12..20) img.set(x, y, true)
        img.set(16, 16, false)
        val (_, stats) = BinaryDenoiser.denoise(img)
        assertEquals(stats.pepperRemoved + stats.saltFilled, stats.totalChanged)
        assertTrue("should have removed 3 specks", stats.pepperRemoved >= 3)
        assertTrue("should have filled >=1 hole", stats.saltFilled >= 1)
    }

    // ── pipeline-relevant invariants ────────────────────────────────────────

    @Test
    fun `denoise preserves a filled notehead fill ratio for rhythm analysis`() {
        // 实心符头布满椒盐噪声后，填充率会下降；降噪后应恢复为接近实心。
        val img = BinaryImage.blank(40, 30)
        drawEllipse(img, 20, 15, rx = 6, ry = 5) // solid filled notehead
        val cleanFill = fillRatio(img, 20, 15, 6, 5)
        // Punch several salt holes inside the notehead.
        img.set(20, 15, false)
        img.set(18, 14, false)
        img.set(22, 16, false)
        val noisyFill = fillRatio(img, 20, 15, 6, 5)
        assertTrue("noise should reduce fill ratio ($noisyFill < $cleanFill)", noisyFill < cleanFill)
        val (denoised, _) = BinaryDenoiser.denoise(img)
        val restoredFill = fillRatio(denoised, 20, 15, 6, 5)
        assertTrue(
            "denoise should restore fill ratio towards solid (restored=$restoredFill, noisy=$noisyFill)",
            restoredFill > noisyFill
        )
    }

    @Test
    fun `denoise does not merge two horizontally separated staff lines`() {
        // 两条平行的 1px 谱线（间距 4px），降噪后不应被盐填充连接。
        val img = BinaryImage.blank(100, 20)
        for (x in 0 until 100) {
            img.set(x, 8, true)
            img.set(x, 12, true)
        }
        val (out, stats) = BinaryDenoiser.denoise(img)
        assertEquals("no salt filling between lines 4px apart", 0, stats.saltFilled)
        // The gap row (y=10) should remain entirely white.
        for (x in 0 until 100) assertFalse("gap should stay white at ($x,10)", out.isBlack(x, 10))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun drawEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int, ry: Int) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x < 0 || y < 0 || x >= img.width || y >= img.height) continue
                val ndx = (x - cx).toDouble() / rx
                val ndy = (y - cy).toDouble() / ry
                if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
            }
        }
    }

    /** Black-pixel ratio inside the bounding box of an ellipse (mimics RhythmAnalyzer fill test). */
    private fun fillRatio(img: BinaryImage, cx: Int, cy: Int, rx: Int, ry: Int): Double {
        var black = 0
        var total = 0
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                total++
                if (img.isBlack(x, y)) black++
            }
        }
        return black.toDouble() / total.coerceAtLeast(1)
    }
}
