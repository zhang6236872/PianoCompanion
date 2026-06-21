package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：用像素级手绘的合成跳房子括号验证 [VoltaDetector]。
 *
 * 测试覆盖跳房子括号（水平线 + 左右竖钩）识别、序号(1./2.)识别、开口跳房子、
 * 假阳性排除（无钩水平线、连音线、谱线）、多跳房子、签名区排除、多系统上界、
 * 边界情况。不依赖真实照片或 Android 设备。
 */
class VoltaDetectorTest {

    private val s = 10          // 谱线间距
    private val w = 320
    private val h = 120
    // 五线谱线 Y 坐标（自上而下）：30, 40, 50, 60, 70
    private val lineYs = listOf(30, 40, 50, 60, 70)
    private val topLineY = lineYs.first()   // 30

    private fun blank() = BinaryImage.blank(w, h)

    private fun system(): StaffSystem =
        StaffSystem(lineYs.map { StaffLine(it, it, 0.5) })

    // ---- 绘图工具 ---------------------------------------------------------- //

    /** 画五线谱（5 根满宽水平线）。 */
    private fun drawStaff(img: BinaryImage) {
        for (y in lineYs) for (x in 0 until w) img.set(x, y, true)
    }

    /**
     * 跳房子括号：在 [y] 行画一条从 [startX] 到 [endX] 的水平线（1~2px 粗），
     * 两端各画一条向下延伸 [hookLen] 的竖钩。
     *
     * @param leftHook  是否画左竖钩（默认 true）
     * @param rightHook 是否画右竖钩（默认 true）
     */
    private fun voltaBracket(
        img: BinaryImage,
        startX: Int,
        endX: Int,
        y: Int,
        hookLen: Int = 6,
        leftHook: Boolean = true,
        rightHook: Boolean = true
    ) {
        // 水平线
        for (x in startX..endX) img.set(x, y, true)
        // 左竖钩：向下
        if (leftHook) {
            for (dy in 1..hookLen) {
                val yy = y + dy
                if (yy in 0 until h) img.set(startX, yy, true)
            }
        }
        // 右竖钩：向下
        if (rightHook) {
            for (dy in 1..hookLen) {
                val yy = y + dy
                if (yy in 0 until h) img.set(endX, yy, true)
            }
        }
    }

    /**
     * 在左钩上方区域绘制跳房子序号：用 [SignatureDetector] 的 5×7 模板按 [scale] 倍放大，
     * 并在数字右下角加一个句点「.」（标准记谱 1. / 2.）。
     */
    private fun voltaNumber(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int = 2) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("no template for $digit")
        for (r in 0 until SignatureDetector.GRID_H) {
            for (c in 0 until SignatureDetector.GRID_W) {
                if (!tmpl[r * SignatureDetector.GRID_W + c]) continue
                for (dy in 0 until scale) for (dx in 0 until scale) {
                    val x = x0 + c * scale + dx
                    val yy = y0 + r * scale + dy
                    if (x in 0 until w && yy in 0 until h) img.set(x, yy, true)
                }
            }
        }
        // 句点：数字右下方
        val dotX = x0 + SignatureDetector.GRID_W * scale + 1
        val dotY = y0 + (SignatureDetector.GRID_H - 1) * scale
        for (dx in 0..1) for (dy in 0..1) {
            if (dotX + dx in 0 until w && dotY + dy in 0 until h) img.set(dotX + dx, dotY + dy, true)
        }
    }

    // ---- 基础检测测试 ------------------------------------------------------- //

    @Test
    fun `volta bracket with number 1 is detected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s  // 顶线上方 1 个间距
        voltaBracket(img, startX = 100, endX = 180, y = bracketY)
        voltaNumber(img, digit = 1, x0 = 104, y0 = bracketY - 16)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals("应检测到 1 个跳房子", 1, voltas.size)
        assertEquals(1, voltas[0].number)
        assertEquals(100.0, voltas[0].startX.toDouble(), 1.0)
        assertEquals(180.0, voltas[0].endX.toDouble(), 1.0)
        assertEquals(bracketY, voltas[0].y)
    }

    @Test
    fun `volta bracket with number 2 is detected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        voltaBracket(img, startX = 110, endX = 200, y = bracketY)
        voltaNumber(img, digit = 2, x0 = 114, y0 = bracketY - 16)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals(1, voltas.size)
        assertEquals("序号应为 2", 2, voltas[0].number)
    }

    @Test
    fun `volta without number defaults to 1`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 仅括号，不画序号
        voltaBracket(img, startX = 100, endX = 180, y = bracketY)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals(1, voltas.size)
        assertEquals("无序号时默认 1", 1, voltas[0].number)
    }

    // ---- 竖钩验证测试 ------------------------------------------------------- //

    @Test
    fun `open-ended volta with only left hook and long span is detected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 开口跳房子：仅左钩，但跨度 ≥ 3 间距（100..140 = 40px > 3*10=30）
        voltaBracket(img, startX = 100, endX = 145, y = bracketY, rightHook = false)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals("长跨度开口跳房子应被检测", 1, voltas.size)
    }

    @Test
    fun `open-ended volta without right hook and short span is rejected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 短跨度（100..125 = 25px < 30）且无右钩 → 不应检测（缺第二个佐证）
        voltaBracket(img, startX = 100, endX = 125, y = bracketY, rightHook = false)
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("短跨度且无右钩的开口跳房子不应被检测", voltas.isEmpty())
    }

    @Test
    fun `horizontal line without any hook is rejected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 仅水平线，两端无竖钩
        for (x in 100..200) img.set(x, bracketY, true)
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("无竖钩的水平线不应被检测为跳房子", voltas.isEmpty())
    }

    @Test
    fun `horizontal line with only right hook is rejected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 仅右钩（无左钩）→ 必须有左钩
        for (x in 100..200) img.set(x, bracketY, true)
        for (dy in 1..6) img.set(200, bracketY + dy, true)
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("无左钩的括号不应被检测", voltas.isEmpty())
    }

    // ---- 多跳房子测试 ------------------------------------------------------- //

    @Test
    fun `two consecutive voltas ending 1 and ending 2 are both detected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 第1结尾（左）
        voltaBracket(img, startX = 100, endX = 150, y = bracketY)
        voltaNumber(img, digit = 1, x0 = 104, y0 = bracketY - 16)
        // 第2结尾（右）
        voltaBracket(img, startX = 160, endX = 230, y = bracketY)
        voltaNumber(img, digit = 2, x0 = 164, y0 = bracketY - 16)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals("应检测到 2 个跳房子", 2, voltas.size)
        assertEquals(1, voltas[0].number)
        assertEquals(2, voltas[1].number)
        assertTrue("第1结尾在左", voltas[0].startX < voltas[1].startX)
    }

    // ---- 假阳性排除测试 ----------------------------------------------------- //

    @Test
    fun `empty staff returns no voltas`() {
        val img = blank()
        drawStaff(img)
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("空谱表不应检测到跳房子", voltas.isEmpty())
    }

    @Test
    fun `blank image returns no voltas`() {
        val img = blank()
        val voltas = VoltaDetector.detect(img, system())
        assertTrue(voltas.isEmpty())
    }

    @Test
    fun `staff lines below top line are not detected as voltas`() {
        val img = blank()
        drawStaff(img)
        // 谱线在搜索带之下（搜索带在 topLineY 上方），不应被误判
        val voltas = VoltaDetector.detect(img, system())
        assertTrue(voltas.isEmpty())
    }

    @Test
    fun `notehead above staff is not detected as volta`() {
        val img = blank()
        drawStaff(img)
        // 谱表上方的孤立符头（圆点），不是水平长线，不应误判
        val cy = topLineY - s
        for (y in cy - 3..cy + 3) for (x in 100..106) {
            val ndx = (x - 103).toDouble() / 3
            val ndy = (y - cy).toDouble() / 3
            if (ndx * ndx + ndy * ndy <= 1.01 && x in 0 until w && y in 0 until h) img.set(x, y, true)
        }
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("孤立符头不应被检测为跳房子", voltas.isEmpty())
    }

    @Test
    fun `short horizontal segment above staff is rejected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 短水平线段（< 2 间距）+ 左钩，但因太短不构成括号候选
        for (x in 100..112) img.set(x, bracketY, true)
        for (dy in 1..6) img.set(100, bracketY + dy, true)
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("过短水平段不应被检测为跳房子", voltas.isEmpty())
    }

    // ---- 签名区排除测试 ----------------------------------------------------- //

    @Test
    fun `volta starting in signature region is excluded`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 括号起点在签名区内（startX=50 < signatureEndX=90）
        voltaBracket(img, startX = 50, endX = 150, y = bracketY)
        val voltas = VoltaDetector.detect(img, system(), signatureEndX = 90)
        assertTrue("签名区内的跳房子不应被检测", voltas.isEmpty())
    }

    @Test
    fun `volta after signature region is detected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        voltaBracket(img, startX = 50, endX = 150, y = bracketY)  // 部分在签名区
        voltaBracket(img, startX = 170, endX = 250, y = bracketY) // 签名区外
        val voltas = VoltaDetector.detect(img, system(), signatureEndX = 90)
        assertEquals("只应检测到签名区外的跳房子", 1, voltas.size)
        assertEquals(170.0, voltas[0].startX.toDouble(), 1.0)
    }

    // ---- 多系统上界测试 ----------------------------------------------------- //

    @Test
    fun `upperLimit bounds the search band for multi-system pages`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s  // = 20
        voltaBracket(img, startX = 100, endX = 200, y = bracketY)
        // upperLimit=25 > bracketY(20)：搜索带 [25, 29] 不含括号 → 无结果
        val voltas = VoltaDetector.detect(img, system(), upperLimit = 25)
        assertTrue("upperLimit 过高时应搜不到括号", voltas.isEmpty())
    }

    @Test
    fun `upperLimit below bracket allows detection`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s  // = 20
        voltaBracket(img, startX = 100, endX = 200, y = bracketY)
        // upperLimit=0 < bracketY(20)：搜索带 [0, 29] 含括号 → 检测到
        val voltas = VoltaDetector.detect(img, system(), upperLimit = 0)
        assertEquals(1, voltas.size)
    }

    // ---- 边界情况 ----------------------------------------------------------- //

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawStaff(img)
        val badSystem = StaffSystem(listOf(StaffLine(50, 50, 0.5)))
        val voltas = VoltaDetector.detect(img, badSystem)
        assertTrue("lineSpacing=0 时应返回空", voltas.isEmpty())
    }

    @Test
    fun `volta width is reported correctly`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        voltaBracket(img, startX = 100, endX = 175, y = bracketY)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals(1, voltas.size)
        assertTrue(
            "跳房子宽度应约为 76 (实际 ${voltas[0].width})",
            voltas[0].width in 75..77
        )
    }

    @Test
    fun `thick horizontal structure above staff is rejected`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        // 很厚的水平条（>0.6 间距 = 6px），不像细括号线
        for (y in bracketY..bracketY + 8) for (x in 100..200) {
            if (x in 0 until w && y in 0 until h) img.set(x, y, true)
        }
        // 加左钩
        for (dy in 1..6) img.set(100, bracketY + 9 + dy, true)
        val voltas = VoltaDetector.detect(img, system())
        assertTrue("过厚水平结构不应被检测为跳房子", voltas.isEmpty())
    }

    @Test
    fun `number 3 volta is recognized`() {
        val img = blank()
        drawStaff(img)
        val bracketY = topLineY - s
        voltaBracket(img, startX = 100, endX = 180, y = bracketY)
        voltaNumber(img, digit = 3, x0 = 104, y0 = bracketY - 16)
        val voltas = VoltaDetector.detect(img, system())
        assertEquals(1, voltas.size)
        assertEquals("序号应为 3", 3, voltas[0].number)
    }
}
