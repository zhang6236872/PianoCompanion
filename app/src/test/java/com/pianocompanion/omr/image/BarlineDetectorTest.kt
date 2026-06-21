package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：用像素级手绘的合成小节线验证 [BarlineDetector]。
 *
 * 测试覆盖单/双/终止小节线的识别、符干排除、签名区排除、多小节线排序、
 * 以及边界情况。不依赖真实照片或 Android 设备。
 */
class BarlineDetectorTest {

    private val s = 10          // 谱线间距
    private val w = 300
    private val h = 120
    // 五线谱线 Y 坐标（自上而下）：30, 40, 50, 60, 70
    private val lineYs = listOf(30, 40, 50, 60, 70)

    private fun blank() = BinaryImage.blank(w, h)

    private fun system(): StaffSystem =
        StaffSystem(lineYs.map { StaffLine(it, it, 0.5) })

    // ---- 绘图工具 ---------------------------------------------------------- //

    /** 画五线谱（5 根满宽水平线）。 */
    private fun drawStaff(img: BinaryImage) {
        for (y in lineYs) for (x in 0 until w) img.set(x, y, true)
    }

    /**
     * 小节线：宽度 [lineWidth]px 的竖线，从 topY-margin 到 botY+margin。
     * 默认 2px 宽、延伸 ±2px 超出谱表，模拟标准印刷小节线。
     */
    private fun barline(img: BinaryImage, cx: Int, lineWidth: Int = 2, margin: Int = 2) {
        val x0 = cx - lineWidth / 2
        val x1 = x0 + lineWidth - 1
        for (x in x0..x1) {
            for (y in (lineYs.first() - margin)..(lineYs.last() + margin)) {
                if (x in 0 until w && y in 0 until h) img.set(x, y, true)
            }
        }
    }

    /** 1px 宽符干，从 fromY 到 toY。 */
    private fun stem(img: BinaryImage, x: Int, fromY: Int, toY: Int) {
        for (y in minOf(fromY, toY)..maxOf(fromY, toY)) {
            if (y in 0 until h) img.set(x, y, true)
        }
    }

    /** 实心椭圆符头。 */
    private fun ellipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ndx = (x - cx).toDouble() / rx
            val ndy = (y - cy).toDouble() / ry
            if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
        }
    }

    // ---- 基础检测测试 ------------------------------------------------------- //

    @Test
    fun `single barline is detected as SINGLE`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 150)
        val bars = BarlineDetector.detect(img, system())
        assertEquals("应检测到 1 条小节线", 1, bars.size)
        assertEquals("应为单竖线", BarlineType.SINGLE, bars[0].type)
        assertEquals(150.0, bars[0].centerX.toDouble(), 1.0)
    }

    @Test
    fun `barline center x is accurate within tolerance`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 200)
        val bars = BarlineDetector.detect(img, system())
        assertEquals(1, bars.size)
        assertEquals(200.0, bars[0].centerX.toDouble(), 1.0)
    }

    @Test
    fun `multiple barlines are detected and sorted by x`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 100)
        barline(img, cx = 200)
        barline(img, cx = 250)
        val bars = BarlineDetector.detect(img, system())
        assertEquals("应检测到 3 条小节线", 3, bars.size)
        assertEquals(100.0, bars[0].centerX.toDouble(), 1.0)
        assertEquals(200.0, bars[1].centerX.toDouble(), 1.0)
        assertEquals(250.0, bars[2].centerX.toDouble(), 1.0)
        // 全部为单竖线
        bars.forEach { assertEquals(BarlineType.SINGLE, it.type) }
    }

    // ---- 空谱表/无线测试 --------------------------------------------------- //

    @Test
    fun `empty staff returns no barlines`() {
        val img = blank()
        drawStaff(img)
        // 仅谱线，无小节线
        val bars = BarlineDetector.detect(img, system())
        assertTrue("空谱表不应检测到小节线", bars.isEmpty())
    }

    @Test
    fun `blank image returns no barlines`() {
        val img = blank()
        val bars = BarlineDetector.detect(img, system())
        assertTrue(bars.isEmpty())
    }

    // ---- 符干排除测试 ------------------------------------------------------- //

    @Test
    fun `1px note stem is not detected as barline by width filter`() {
        val img = blank()
        drawStaff(img)
        // 从底谱线(70)向上的 1px 符干，跨度覆盖大部分谱高但不全
        stem(img, x = 150, fromY = 30, toY = 60)
        val bars = BarlineDetector.detect(img, system())
        assertTrue("1px 符干不应被检测为小节线", bars.isEmpty())
    }

    @Test
    fun `note stem excluded by notehead overlap filter`() {
        val img = blank()
        drawStaff(img)
        // 符头在 x=150，符干在 x=154（符头右边缘 +4）
        ellipse(img, 150, 60)
        stem(img, x = 154, fromY = 33, toY = 57)
        // 即使符干碰巧达到高填充率，传入符头 X 后应被排除
        val bars = BarlineDetector.detect(img, system(), noteheadXs = listOf(150))
        assertTrue("与符头重叠的竖线不应被检测为小节线", bars.isEmpty())
    }

    @Test
    fun `stem from bottom to top of staff still rejected by width`() {
        val img = blank()
        drawStaff(img)
        // 超长 1px 符干从 y=25 到 y=75（跨越全谱高），模拟极端情况
        stem(img, x = 150, fromY = 25, toY = 75)
        val bars = BarlineDetector.detect(img, system())
        // 1px 宽度 < minWidth(2px)，应被排除
        assertTrue("1px 宽超长符干仍应被宽度过滤器排除", bars.isEmpty())
    }

    @Test
    fun `notehead alone is not detected as barline`() {
        val img = blank()
        drawStaff(img)
        ellipse(img, 150, 50) // 符头在谱表中间
        val bars = BarlineDetector.detect(img, system())
        assertTrue("单个符头不应被检测为小节线", bars.isEmpty())
    }

    // ---- 签名区排除测试 ----------------------------------------------------- //

    @Test
    fun `barline in signature region is excluded`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 50)  // 在签名区内
        val bars = BarlineDetector.detect(img, system(), signatureEndX = 80)
        assertTrue("签名区内的小节线不应被检测", bars.isEmpty())
    }

    @Test
    fun `barline after signature region is detected`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 50)   // 签名区内
        barline(img, cx = 150)  // 签名区外
        val bars = BarlineDetector.detect(img, system(), signatureEndX = 80)
        assertEquals("只应检测到签名区外的 1 条小节线", 1, bars.size)
        assertEquals(150.0, bars[0].centerX.toDouble(), 1.0)
    }

    // ---- 双竖线 / 终止线分类测试 --------------------------------------------- //

    @Test
    fun `double barline is classified as DOUBLE`() {
        val img = blank()
        drawStaff(img)
        // 两条相邻细竖线（间距 3px）
        barline(img, cx = 148, lineWidth = 2)
        barline(img, cx = 155, lineWidth = 2)
        val bars = BarlineDetector.detect(img, system())
        assertEquals("应检测到 1 条小节线（双竖线合并）", 1, bars.size)
        assertEquals("应为双竖线", BarlineType.DOUBLE, bars[0].type)
    }

    @Test
    fun `final barline thin plus thick is classified as FINAL`() {
        val img = blank()
        drawStaff(img)
        // 细线 + 紧邻粗线
        barline(img, cx = 145, lineWidth = 2)   // 细
        barline(img, cx = 156, lineWidth = 6)   // 粗（>0.5*spacing，确保被分类为 FINAL）
        val bars = BarlineDetector.detect(img, system())
        assertEquals("应检测到 1 条小节线（终止线合并）", 1, bars.size)
        assertEquals("应为终止线", BarlineType.FINAL, bars[0].type)
    }

    @Test
    fun `two distant barlines are classified as SINGLE not DOUBLE`() {
        val img = blank()
        drawStaff(img)
        // 两条相距很远的竖线（间距 > 1.5*spacing = 15px），不应合并为双竖线
        barline(img, cx = 100)
        barline(img, cx = 200)
        val bars = BarlineDetector.detect(img, system())
        assertEquals(2, bars.size)
        bars.forEach { assertEquals("远距竖线应为单竖线", BarlineType.SINGLE, it.type) }
    }

    // ---- 边界情况 ----------------------------------------------------------- //

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 150)
        // 构造一个 lineSpacing=0 的系统
        val badSystem = StaffSystem(listOf(StaffLine(50, 50, 0.5)))
        val bars = BarlineDetector.detect(img, badSystem)
        assertTrue("lineSpacing=0 时应返回空", bars.isEmpty())
    }

    @Test
    fun `barline at image right edge is detected`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = w - 5)
        val bars = BarlineDetector.detect(img, system())
        assertEquals(1, bars.size)
        assertTrue(
            "右边缘小节线中心应在 ${w - 5} 附近",
            bars[0].centerX in (w - 7)..(w - 3)
        )
    }

    @Test
    fun `barline coexists with noteheads at different x`() {
        val img = blank()
        drawStaff(img)
        ellipse(img, 80, 50)   // 符头
        barline(img, cx = 150)  // 小节线
        ellipse(img, 220, 50)  // 符头
        val bars = BarlineDetector.detect(img, system(), noteheadXs = listOf(80, 220))
        assertEquals("应检测到 1 条小节线（符头被排除）", 1, bars.size)
        assertEquals(BarlineType.SINGLE, bars[0].type)
        assertEquals(150.0, bars[0].centerX.toDouble(), 1.0)
    }

    @Test
    fun `barline width is reported correctly`() {
        val img = blank()
        drawStaff(img)
        barline(img, cx = 150, lineWidth = 3)
        val bars = BarlineDetector.detect(img, system())
        assertEquals(1, bars.size)
        assertTrue(
            "3px 小节线的宽度应报为 3 (实际 ${bars[0].width})",
            bars[0].width in 3..4  // 允许 1px 的分组边界误差
        )
    }
}
