package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OctavaDetector] 单元测试：使用合成二值图（synthetic BinaryImage）端到端验证
 * 八度记号(8va / 8vb / 15ma / 15mb)检测的完整链路——数字识别 → 虚线确认 → 移位计算。
 *
 * 测试模式参考 [TupletDetectorTest] 和 [SignatureDetectorTest]：
 * - 用 `renderDigit` 将 5×7 数字模板按倍率画入合成图（scale=2）。
 * - 用 `drawDashedLine` 在数字右侧绘制虚线（4px dash + 2px gap）。
 * - 用 [ConnectedComponents.label] 提取 blobs。
 *
 * 几何约束（关键）：
 * - 谱线间距 s=20，五条谱线 y=40,60,80,100,120。
 * - 上方搜索区域：searchGap=0.5×20=10，aboveBottom=40-10=30，aboveTop=max(0,30-80)=0。
 *   → 上方区域 y∈[0,30]，数字渲染于 y0=5（blob centerY≈11）。
 * - 下方搜索区域：belowTop=120+10=130，belowBottom=min(130+80, height-1)。
 *   → 下方区域 y∈[130,209]（height=250），数字渲染于 y0=150（blob centerY≈156）。
 * - 数字 scale=2：宽 10px（"8"等全宽字形），高 14px。
 * - maxBlobW=1.5×20=30、maxBlobH=30、minBlobW=6、minBlobH=8。
 */
class OctavaDetectorTest {

    private val width = 300
    private val height = 250
    private val s = 20 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** 五条谱线 y=40,60,80,100,120 → 间距=20。 */
    private val lineYs = listOf(40, 60, 80, 100, 120)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /**
     * 把某个数字的 5×7 模板按 [scale] 倍放大画入图像。
     * 8 连通保证（含对角）合成字形为一个完整 blob。
     */
    private fun renderDigit(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("no template for $digit")
        for (r in 0 until SignatureDetector.GRID_H) {
            for (c in 0 until SignatureDetector.GRID_W) {
                if (!tmpl[r * SignatureDetector.GRID_W + c]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val px = x0 + c * scale + dx
                        val py = y0 + r * scale + dy
                        if (px in 0 until width && py in 0 until height) {
                            img.set(px, py, true)
                        }
                    }
                }
            }
        }
    }

    /**
     * 从 [x] 开始向右绘制水平虚线。
     * 每个 dash 长 [dashLen] 像素，gap 间隔 [gapLen] 像素，共 [numDashes] 个 dash。
     * dash 厚度为 [thickness] 像素（默认 1）。
     */
    private fun drawDashedLine(
        img: BinaryImage, x: Int, y: Int, dashLen: Int, gapLen: Int, numDashes: Int,
        thickness: Int = 1
    ) {
        for (i in 0 until numDashes) {
            val dashStart = x + i * (dashLen + gapLen)
            for (dx in 0 until dashLen) {
                for (dy in 0 until thickness) {
                    val px = dashStart + dx
                    val py = y + dy
                    if (px in 0 until width && py in 0 until height) {
                        img.set(px, py, true)
                    }
                }
            }
        }
    }

    /** 从图像提取连通块。 */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ---- 8va 检测（谱表上方，数字 "8" + 虚线 → +12 半音）---------------------

    @Test
    fun `ottava 8va above staff with dashed line is detected`() {
        val img = blank()
        // 数字 "8" 在谱表上方（y0=5，centerY≈11）
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        // 虚线在数字右侧（gap 后开始），dashLen=4, gapLen=2, 10 dashes
        // 扫描起点 markerRightX+1=60，虚线从 x=65 开始
        drawDashedLine(img, x = 65, y = 11, dashLen = 4, gapLen = 2, numDashes = 10)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个八度记号", 1, shifts.size)
        val shift = shifts[0]
        assertEquals(12, shift.semitones)
        assertEquals(OctavaDetector.OctavaDirection.ABOVE, shift.direction)
        assertEquals(1, shift.octaves)
        assertEquals(0, shift.systemIdx)
    }

    // ---- 8vb 检测（谱表下方，数字 "8" + 虚线 → -12 半音）---------------------

    @Test
    fun `ottava bassa 8vb below staff with dashed line is detected`() {
        val img = blank()
        // 数字 "8" 在谱表下方（y0=150，centerY≈156）
        renderDigit(img, 8, x0 = 50, y0 = 150, scale = 2)
        // 虚线在数字右侧
        drawDashedLine(img, x = 65, y = 156, dashLen = 4, gapLen = 2, numDashes = 10)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个八度记号", 1, shifts.size)
        val shift = shifts[0]
        assertEquals(-12, shift.semitones)
        assertEquals(OctavaDetector.OctavaDirection.BELOW, shift.direction)
        assertEquals(1, shift.octaves)
    }

    // ---- 15ma 检测（谱表上方，数字 "1"+"5" + 虚线 → +24 半音）----------------

    @Test
    fun `quindicesima 15ma above staff with dashed line is detected`() {
        val img = blank()
        // 数字 "1" 在左侧（x0=45），"5" 在右侧（x0=55）
        renderDigit(img, 1, x0 = 45, y0 = 5, scale = 2)
        renderDigit(img, 5, x0 = 55, y0 = 5, scale = 2)
        // 虚线在 "5" 的右侧（markerRightX≈64，扫描从 65 开始）
        drawDashedLine(img, x = 68, y = 11, dashLen = 4, gapLen = 2, numDashes = 10)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个双八度记号", 1, shifts.size)
        val shift = shifts[0]
        assertEquals(24, shift.semitones)
        assertEquals(OctavaDetector.OctavaDirection.ABOVE, shift.direction)
        assertEquals(2, shift.octaves)
    }

    // ---- 15mb 检测（谱表下方，数字 "1"+"5" + 虚线 → -24 半音）----------------

    @Test
    fun `quindicesima bassa 15mb below staff with dashed line is detected`() {
        val img = blank()
        renderDigit(img, 1, x0 = 45, y0 = 150, scale = 2)
        renderDigit(img, 5, x0 = 55, y0 = 150, scale = 2)
        drawDashedLine(img, x = 68, y = 156, dashLen = 4, gapLen = 2, numDashes = 10)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个双八度记号", 1, shifts.size)
        val shift = shifts[0]
        assertEquals(-24, shift.semitones)
        assertEquals(OctavaDetector.OctavaDirection.BELOW, shift.direction)
        assertEquals(2, shift.octaves)
    }

    // ---- 无虚线不检测（区分指法数字与八度记号）-------------------------------

    @Test
    fun `digit 8 above staff without dashed line is not detected`() {
        val img = blank()
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        // 不画虚线——这可能是普通指法数字而非八度记号

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertTrue("没有虚线时不应检测为八度记号", shifts.isEmpty())
    }

    @Test
    fun `digit 5 without adjacent 1 is not detected`() {
        val img = blank()
        // 单独的 "5" 在上方，没有紧邻的 "1"，且没有虚线
        renderDigit(img, 5, x0 = 50, y0 = 5, scale = 2)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertTrue("单独的 5 没有 1 紧邻不应检测", shifts.isEmpty())
    }

    // ---- semitoneShiftForNote 测试 ------------------------------------------

    @Test
    fun `semitoneShiftForNote returns shift when note is in range`() {
        val shifts = listOf(
            OctavaDetector.OctavaShift(
                systemIdx = 0, startX = 50, endX = 200,
                semitones = 12, direction = OctavaDetector.OctavaDirection.ABOVE, octaves = 1
            )
        )
        assertEquals(12, OctavaDetector.semitoneShiftForNote(shifts, 0, 100))
        assertEquals(12, OctavaDetector.semitoneShiftForNote(shifts, 0, 50))
        assertEquals(12, OctavaDetector.semitoneShiftForNote(shifts, 0, 200))
    }

    @Test
    fun `semitoneShiftForNote returns zero when note is out of range`() {
        val shifts = listOf(
            OctavaDetector.OctavaShift(
                systemIdx = 0, startX = 50, endX = 200,
                semitones = 12, direction = OctavaDetector.OctavaDirection.ABOVE, octaves = 1
            )
        )
        assertEquals(0, OctavaDetector.semitoneShiftForNote(shifts, 0, 49))
        assertEquals(0, OctavaDetector.semitoneShiftForNote(shifts, 0, 201))
    }

    @Test
    fun `semitoneShiftForNote returns zero for different system`() {
        val shifts = listOf(
            OctavaDetector.OctavaShift(
                systemIdx = 0, startX = 50, endX = 200,
                semitones = 12, direction = OctavaDetector.OctavaDirection.ABOVE, octaves = 1
            )
        )
        assertEquals(0, OctavaDetector.semitoneShiftForNote(shifts, 1, 100))
    }

    @Test
    fun `semitoneShiftForNote sums multiple overlapping shifts`() {
        val shifts = listOf(
            OctavaDetector.OctavaShift(
                systemIdx = 0, startX = 50, endX = 200,
                semitones = 12, direction = OctavaDetector.OctavaDirection.ABOVE, octaves = 1
            ),
            OctavaDetector.OctavaShift(
                systemIdx = 0, startX = 100, endX = 300,
                semitones = -12, direction = OctavaDetector.OctavaDirection.BELOW, octaves = 1
            )
        )
        // X=150 在两个移位范围内 → 12 + (-12) = 0
        assertEquals(0, OctavaDetector.semitoneShiftForNote(shifts, 0, 150))
        // X=75 只在第一个范围内 → 12
        assertEquals(12, OctavaDetector.semitoneShiftForNote(shifts, 0, 75))
        // X=250 只在第二个范围内 → -12
        assertEquals(-12, OctavaDetector.semitoneShiftForNote(shifts, 0, 250))
    }

    @Test
    fun `semitoneShiftForNote with empty shifts returns zero`() {
        assertEquals(0, OctavaDetector.semitoneShiftForNote(emptyList(), 0, 100))
    }

    // ---- 多系统测试 ----------------------------------------------------------

    @Test
    fun `8va in system 0 does not affect system 1`() {
        val img = blank()
        // 系统 0 上方有 "8" + 虚线
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        drawDashedLine(img, x = 65, y = 11, dashLen = 4, gapLen = 2, numDashes = 10)

        val sys0Lines = listOf(40, 60, 80, 100, 120)
        val sys1Lines = listOf(160, 180, 200, 220, 240)
        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))

        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个八度记号", 1, shifts.size)
        assertEquals(0, shifts[0].systemIdx)
    }

    // ---- 边界情况 ------------------------------------------------------------

    @Test
    fun `empty image returns empty`() {
        val img = blank()
        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)
        assertTrue(shifts.isEmpty())
    }

    @Test
    fun `empty systems returns empty`() {
        val img = blank()
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        drawDashedLine(img, x = 65, y = 11, dashLen = 4, gapLen = 2, numDashes = 10)

        val shifts = OctavaDetector.detect(img, blobs(img), emptyList(), s)
        assertTrue("空系统列表应返回空", shifts.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        drawDashedLine(img, x = 65, y = 11, dashLen = 4, gapLen = 2, numDashes = 10)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, 0)
        assertTrue("零谱线间距应返回空", shifts.isEmpty())
    }

    // ---- 8va 虚线范围正确性 --------------------------------------------------

    @Test
    fun `8va dashed line extent defines endX correctly`() {
        val img = blank()
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        // 虚线从 x=65 开始，10 dashes × (4+2) = 60px span，最后一个 dash 结束于 x=65+58=123
        drawDashedLine(img, x = 65, y = 11, dashLen = 4, gapLen = 2, numDashes = 10)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertEquals(1, shifts.size)
        val shift = shifts[0]
        // endX 应在虚线范围内（最后一个有墨列的 X）
        assertTrue("endX (${-1}) 应 >= startX (54)", shift.endX >= shift.startX)
        assertTrue("endX (${-1}) 应在虚线范围内 [65, 125]", shift.endX in 65..130)
    }

    // ---- 实心线不误检（覆盖率过高） ------------------------------------------

    @Test
    fun `solid line instead of dashed is not detected`() {
        val img = blank()
        renderDigit(img, 8, x0 = 50, y0 = 5, scale = 2)
        // 画一条实心水平线（覆盖率 > 85%，应被拒绝）
        drawDashedLine(img, x = 65, y = 11, dashLen = 60, gapLen = 0, numDashes = 1)

        val systems = listOf(makeSystem())
        val shifts = OctavaDetector.detect(img, blobs(img), systems, s)

        assertTrue("实心线（覆盖率过高）不应检测为八度记号虚线", shifts.isEmpty())
    }
}
