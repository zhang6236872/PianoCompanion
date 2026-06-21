package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：用像素级手绘的合成休止符验证 [RestDetector]。
 *
 * 测试覆盖全/二分/四分/八分休止符的识别、全 vs 二分位置判定、与符头/签名区/
 * 小节线的区分，以及边界情况。不依赖真实照片或 Android 设备。
 */
class RestDetectorTest {

    private val s = 10          // 谱线间距
    private val w = 300
    private val h = 160
    // 五线谱线 Y 坐标（自上而下）：lines[0]=30 … lines[4]=70
    private val lineYs = listOf(30, 40, 50, 60, 70)

    private fun blank() = BinaryImage.blank(w, h)

    /** 标记 blobs 与 label blobs 的快捷方式。 */
    private fun blobs(img: BinaryImage) = ConnectedComponents.label(img, minPixels = 4)

    // ---- 绘图工具 ---------------------------------------------------------- //

    /** 实心椭圆符头（用于测试排除逻辑）。 */
    private fun filledEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ndx = (x - cx).toDouble() / rx
            val ndy = (y - cy).toDouble() / ry
            if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
        }
    }

    /**
     * 全休止符：挂在 line2Y（lines[1]）下方的实心矩形。
     * top 边在 line2Y，向下延伸约半个谱线间距。
     */
    private fun wholeRest(img: BinaryImage, cx: Int, line2Y: Int) {
        val rw = s          // 宽 = 1 个谱线间距
        val rh = s / 2      // 高 = 半个谱线间距
        val left = cx - rw / 2
        val top = line2Y    // 挂在线下方
        for (y in top until top + rh) for (x in left until left + rw) {
            if (x in 0 until w && y in 0 until h) img.set(x, y, true)
        }
    }

    /**
     * 二分休止符：坐在 line3Y（lines[2]）上方的实心矩形。
     * bottom 边在 line3Y，向上延伸约半个谱线间距。
     */
    private fun halfRest(img: BinaryImage, cx: Int, line3Y: Int) {
        val rw = s
        val rh = s / 2
        val left = cx - rw / 2
        val bottom = line3Y  // 坐在线上方
        for (y in bottom - rh + 1..bottom) for (x in left until left + rw) {
            if (x in 0 until w && y in 0 until h) img.set(x, y, true)
        }
    }

    /**
     * 四分休止符：高而窄的锯齿形/闪电形墨迹。
     * 由 3 段交替方向的粗对角线组成，高约 2.5 个谱线间距、宽约 0.7 个谱线间距。
     */
    private fun quarterRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = (2.5 * s).toInt()  // 25
        val halfW = (0.35 * s).toInt()  // 3.5
        val topY = cy - totalH / 2
        val segH = totalH / 3           // ~8
        val thick = 2

        // 3 段交替方向的粗对角线
        for (seg in 0 until 3) {
            val y0 = topY + seg * segH
            val y1 = y0 + segH
            val x0 = cx + if (seg % 2 == 0) halfW else -halfW
            val x1 = cx + if (seg % 2 == 0) -halfW else halfW
            drawThickLine(img, x0, y0, x1, y1, thick)
        }
    }

    /**
     * 八分休止符：旗形符号——一根对角斜线 + 顶端一个小卷曲旗钩。
     * 总高度控制在 ~1.0s（10px），确保低于四分休止符的最低高度阈值（1.5s=15px），
     * 从而只匹配八分休止符分类。
     */
    private fun eighthRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = (1.0 * s).toInt()  // 10
        val halfW = (0.4 * s).toInt()   // 4
        val topY = cy - totalH / 2
        val thick = 2
        // 主斜线：右上到左下
        drawThickLine(img, cx + halfW, topY, cx - halfW, topY + totalH, thick)
        // 旗钩：顶端一个小弧（几个额外像素）
        for (dy in 0 until 3) for (dx in 0 until 3) {
            val px = cx + halfW - dx
            val py = topY - 1 + dy
            if (px in 0 until w && py in 0 until h) img.set(px, py, true)
        }
    }

    /**
     * 十六分休止符：旗形符号——一根竖线符干 + 2 个旗钩。
     * 总高度 ~1.2 个谱线间距，2 个旗钩分别位于上部和下部，中间有 >=2 行纯符干间隔，
     * 使 countFlags 能正确统计出 2 个独立旗钩组。
     */
    private fun sixteenthRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = (1.2 * s).toInt()  // 12
        val topY = cy - totalH / 2
        val stemW = 2
        val flagW = 4
        // 竖线符干
        for (y in topY until topY + totalH) {
            for (x in cx until cx + stemW) {
                if (x in 0 until w && y in 0 until h) img.set(x, y, true)
            }
        }
        // 2 个旗钩（各 1 行，间隔 >= 2 行纯符干）
        for (flagY in listOf(topY + 1, topY + 6)) {
            if (flagY in 0 until h) {
                for (x in cx + stemW until cx + stemW + flagW) {
                    if (x in 0 until w) img.set(x, flagY, true)
                }
            }
        }
    }

    /**
     * 三十二分休止符：旗形符号——一根竖线符干 + 3 个旗钩。
     * 总高度 ~1.4 个谱线间距，3 个旗钩均匀分布，间隔 >= 2 行纯符干。
     */
    private fun thirtySecondRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = (1.4 * s).toInt()  // 14
        val topY = cy - totalH / 2
        val stemW = 2
        val flagW = 4
        // 竖线符干
        for (y in topY until topY + totalH) {
            for (x in cx until cx + stemW) {
                if (x in 0 until w && y in 0 until h) img.set(x, y, true)
            }
        }
        // 3 个旗钩
        for (flagY in listOf(topY + 1, topY + 5, topY + 9)) {
            if (flagY in 0 until h) {
                for (x in cx + stemW until cx + stemW + flagW) {
                    if (x in 0 until w) img.set(x, flagY, true)
                }
            }
        }
    }

    /**
     * **高大的**三十二分休止符：总高度 ~1.8 个谱线间距（超过四分休止符下限 1.5 间距），
     * 3 个旗钩间隔更大（>= 5 行纯符干），用于验证 [RestDetector] 能正确区分高大的
     * 三十二分休止符与四分休止符（此前会被四分休止符的"高锯齿形"启发式误判）。
     */
    private fun tallThirtySecondRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = (1.8 * s).toInt()  // 18
        val topY = cy - totalH / 2
        val stemW = 2
        val flagW = 4
        // 竖线符干
        for (y in topY until topY + totalH) {
            for (x in cx until cx + stemW) {
                if (x in 0 until w && y in 0 until h) img.set(x, y, true)
            }
        }
        // 3 个旗钩（间隔 >= 5 行纯符干，强对比）
        for (flagY in listOf(topY + 1, topY + 7, topY + 13)) {
            if (flagY in 0 until h) {
                for (x in cx + stemW until cx + stemW + flagW) {
                    if (x in 0 until w) img.set(x, flagY, true)
                }
            }
        }
    }

    /**
     * **高大的**十六分休止符：总高度 ~1.7 个谱线间距（超过四分休止符下限 1.5 间距），
     * 2 个旗钩间隔大（>= 7 行纯符干）。
     */
    private fun tallSixteenthRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = (1.7 * s).toInt()  // 17
        val topY = cy - totalH / 2
        val stemW = 2
        val flagW = 4
        // 竖线符干
        for (y in topY until topY + totalH) {
            for (x in cx until cx + stemW) {
                if (x in 0 until w && y in 0 until h) img.set(x, y, true)
            }
        }
        // 2 个旗钩（间隔 >= 7 行纯符干，强对比）
        for (flagY in listOf(topY + 1, topY + 9)) {
            if (flagY in 0 until h) {
                for (x in cx + stemW until cx + stemW + flagW) {
                    if (x in 0 until w) img.set(x, flagY, true)
                }
            }
        }
    }

    /** 厚对角线：在 (x0,y0)-(x1,y1) 之间画 thick×thick 的粗线。 */
    private fun drawThickLine(img: BinaryImage, x0: Int, y0: Int, x1: Int, y1: Int, thick: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)).coerceAtLeast(1)
        for (i in 0..steps) {
            val x = x0 + dx * i / steps
            val y = y0 + dy * i / steps
            for (ty in 0 until thick) for (tx in 0 until thick) {
                val px = x + tx
                val py = y + ty
                if (px in 0 until w && py in 0 until h) img.set(px, py, true)
            }
        }
    }

    /** 细长竖线（模拟小节线 bar line），高 height。 */
    private fun vLine(img: BinaryImage, x: Int, cy: Int, height: Int = 50) {
        val top = cy - height / 2
        for (y in top until top + height) if (y in 0 until h) img.set(x, y, true)
    }

    // ---- 全休止符测试 ------------------------------------------------------- //

    @Test
    fun `whole rest hanging below line 2 is detected as WHOLE`() {
        val img = blank()
        // lines[1] = 40；全休止符挂在 y=40 下方
        wholeRest(img, cx = 100, line2Y = lineYs[1])
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("应为全休止符", NoteDuration.WHOLE, rest[0].duration)
    }

    @Test
    fun `whole rest center is in upper part of center space`() {
        val img = blank()
        wholeRest(img, cx = 100, line2Y = lineYs[1])
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(1, rest.size)
        // 中心应在 lines[1]=40 与 midSpace=45 之间
        assertTrue("全休止符中心 ${rest[0].centerY} 应 < midSpace 45", rest[0].centerY < 45)
    }

    // ---- 二分休止符测试 ----------------------------------------------------- //

    @Test
    fun `half rest sitting above middle line is detected as HALF`() {
        val img = blank()
        // lines[2] = 50（中线）；二分休止符坐在 y=50 上方
        halfRest(img, cx = 100, line3Y = lineYs[2])
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("应为二分休止符", NoteDuration.HALF, rest[0].duration)
    }

    @Test
    fun `half rest center is in lower part of center space`() {
        val img = blank()
        halfRest(img, cx = 100, line3Y = lineYs[2])
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(1, rest.size)
        // 中心应在 midSpace=45 与 lines[2]=50 之间
        assertTrue("二分休止符中心 ${rest[0].centerY} 应 >= midSpace 45", rest[0].centerY >= 45)
    }

    // ---- 全 vs 二分区分测试 ------------------------------------------------- //

    @Test
    fun `whole and half rests at same x but different y are distinguished`() {
        val img1 = blank()
        val img2 = blank()
        wholeRest(img1, cx = 100, line2Y = lineYs[1]) // 全休止符
        halfRest(img2, cx = 100, line3Y = lineYs[2])  // 二分休止符
        val r1 = RestDetector.detect(blobs(img1), emptyList(), s, lineYs)
        val r2 = RestDetector.detect(blobs(img2), emptyList(), s, lineYs)
        assertEquals(NoteDuration.WHOLE, r1[0].duration)
        assertEquals(NoteDuration.HALF, r2[0].duration)
    }

    // ---- 四分休止符测试 ----------------------------------------------------- //

    @Test
    fun `quarter rest zigzag is detected as QUARTER`() {
        val img = blank()
        quarterRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("应为四分休止符", NoteDuration.QUARTER, rest[0].duration)
    }

    @Test
    fun `quarter rest is taller than wide`() {
        val img = blank()
        quarterRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(1, rest.size)
        assertTrue("四分休止符高 ${rest[0].height} 应 > 宽 ${rest[0].width}",
            rest[0].height > rest[0].width)
    }

    // ---- 八分休止符测试 ----------------------------------------------------- //

    @Test
    fun `eighth rest flag shape is detected as EIGHTH`() {
        val img = blank()
        eighthRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("应为八分休止符", NoteDuration.EIGHTH, rest[0].duration)
    }

    @Test
    fun `eighth rest with image counts exactly 1 flag`() {
        val img = blank()
        eighthRest(img, cx = 100, cy = 50)
        // 传入二值图后，旗钩层数计数应得到 1 → EIGHTH
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals(1, rest.size)
        assertEquals(NoteDuration.EIGHTH, rest[0].duration)
    }

    // ---- 十六分休止符测试 --------------------------------------------------- //

    @Test
    fun `sixteenth rest with 2 flags is detected as SIXTEENTH`() {
        val img = blank()
        sixteenthRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("应为十六分休止符", NoteDuration.SIXTEENTH, rest[0].duration)
    }

    @Test
    fun `sixteenth rest without image falls back to EIGHTH`() {
        val img = blank()
        sixteenthRest(img, cx = 100, cy = 50)
        // 不传 image → 无法计数旗钩 → 向后兼容默认 EIGHTH
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(1, rest.size)
        assertEquals("无图像时十六分休止符回退为八分", NoteDuration.EIGHTH, rest[0].duration)
    }

    // ---- 三十二分休止符测试 ------------------------------------------------- //

    @Test
    fun `thirty-second rest with 3 flags is detected as THIRTY_SECOND`() {
        val img = blank()
        thirtySecondRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("应为三十二分休止符", NoteDuration.THIRTY_SECOND, rest[0].duration)
    }

    @Test
    fun `thirty-second rest without image falls back to EIGHTH`() {
        val img = blank()
        thirtySecondRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(1, rest.size)
        assertEquals("无图像时三十二分休止符回退为八分", NoteDuration.EIGHTH, rest[0].duration)
    }

    // ---- 旗钩层数区分测试 --------------------------------------------------- //

    @Test
    fun `eighth sixteenth and thirty-second rests are distinguished by flag count`() {
        val img8 = blank()
        val img16 = blank()
        val img32 = blank()
        eighthRest(img8, cx = 100, cy = 50)
        sixteenthRest(img16, cx = 100, cy = 50)
        thirtySecondRest(img32, cx = 100, cy = 50)
        val r8 = RestDetector.detect(blobs(img8), emptyList(), s, lineYs, image = img8)
        val r16 = RestDetector.detect(blobs(img16), emptyList(), s, lineYs, image = img16)
        val r32 = RestDetector.detect(blobs(img32), emptyList(), s, lineYs, image = img32)
        assertEquals(NoteDuration.EIGHTH, r8[0].duration)
        assertEquals(NoteDuration.SIXTEENTH, r16[0].duration)
        assertEquals(NoteDuration.THIRTY_SECOND, r32[0].duration)
    }

    @Test
    fun `quarter rest is not misclassified as flagged rest`() {
        val img = blank()
        quarterRest(img, cx = 100, cy = 50)
        // 四分休止符（高锯齿形）不应被旗形休止符分类匹配（高度 > 1.5s）
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals(1, rest.size)
        assertEquals("四分休止符不应被误判为十六/三十二分", NoteDuration.QUARTER, rest[0].duration)
    }

    // ---- 高位旗形休止符测试（高大的十六/三十二分休止符 vs 四分休止符）----------- //

    @Test
    fun `tall thirty-second rest above 1_5 spacing is detected as THIRTY_SECOND`() {
        val img = blank()
        tallThirtySecondRest(img, cx = 100, cy = 50)
        // 高度 ~1.8 间距（> 1.5），此前会被四分休止符抢先匹配而误判为 QUARTER；
        // 现由 tallFlaggedRest 在四分休止符之前拦截，通过强对比旗钩计数正确分类。
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("高大的三十二分休止符应为 THIRTY_SECOND（而非 QUARTER）",
            NoteDuration.THIRTY_SECOND, rest[0].duration)
    }

    @Test
    fun `tall sixteenth rest above 1_5 spacing is detected as SIXTEENTH`() {
        val img = blank()
        tallSixteenthRest(img, cx = 100, cy = 50)
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals("应检测到 1 个休止符", 1, rest.size)
        assertEquals("高大的十六分休止符应为 SIXTEENTH（而非 QUARTER）",
            NoteDuration.SIXTEENTH, rest[0].duration)
    }

    @Test
    fun `tall thirty-second rest without image falls back to quarter rest`() {
        val img = blank()
        tallThirtySecondRest(img, cx = 100, cy = 50)
        // 不传 image → 无法做旗钩分析 → tallFlaggedRest 跳过 → 高度 1.8 间距落入
        // 四分休止符区间，按向后兼容行为判为 QUARTER（无图时无法区分）。
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(1, rest.size)
        assertEquals("无图像时无法计数旗钩，高大的旗形休止符回退为四分",
            NoteDuration.QUARTER, rest[0].duration)
    }

    @Test
    fun `quarter rest remains QUARTER even with image and tall flagged-rest check active`() {
        val img = blank()
        quarterRest(img, cx = 100, cy = 50)
        // 强对比旗钩计数对锯齿形四分休止符应返回 <2（各行密度均匀、无强对比旗钩带），
        // 不被 tallFlaggedRest 误判。
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals(1, rest.size)
        assertEquals("四分休止符不应被高位旗形休止符判定误判",
            NoteDuration.QUARTER, rest[0].duration)
    }

    @Test
    fun `tall thirty-second rest and quarter rest are distinguished side by side`() {
        val img = blank()
        tallThirtySecondRest(img, cx = 60, cy = 50)   // 高大的三十二分休止符
        quarterRest(img, cx = 160, cy = 50)            // 四分休止符（锯齿）
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals("应检测到 2 个休止符", 2, rests.size)
        // 按 x 排序：三十二分在左，四分在右
        assertEquals(60.0, rests[0].centerX.toDouble(), 3.0)
        assertEquals(160.0, rests[1].centerX.toDouble(), 3.0)
        assertEquals("左侧应为三十二分休止符", NoteDuration.THIRTY_SECOND, rests[0].duration)
        assertEquals("右侧应为四分休止符", NoteDuration.QUARTER, rests[1].duration)
    }

    @Test
    fun `tall thirty-second rest advances cursor less than quarter rest`() {
        // 时值验证：三十二分（0.25 拍）短于四分（1 拍），确保正确分类带来的时序正确性。
        val img32 = blank()
        val imgQ = blank()
        tallThirtySecondRest(img32, cx = 100, cy = 50)
        quarterRest(imgQ, cx = 100, cy = 50)
        val r32 = RestDetector.detect(blobs(img32), emptyList(), s, lineYs, image = img32)[0]
        val rq = RestDetector.detect(blobs(imgQ), emptyList(), s, lineYs, image = imgQ)[0]
        assertTrue("三十二分休止符时长应短于四分休止符",
            r32.duration.quarterValue < rq.duration.quarterValue)
    }

    // ---- 排除逻辑测试 ------------------------------------------------------- //

    @Test
    fun `filled notehead is not detected as a rest`() {
        val img = blank()
        filledEllipse(img, 100, 50) // 实心符头在谱表中间
        val nh = Notehead(100, 50, 9, 7, 60)
        val rest = RestDetector.detect(blobs(img), listOf(nh), s, lineYs)
        // 符头被排除后，不应有任何休止符
        assertTrue("已检测的符头不应被误判为休止符", rest.isEmpty())
    }

    @Test
    fun `notehead and rest coexist at different x positions`() {
        val img = blank()
        filledEllipse(img, 50, 50)  // 符头在 x=50
        quarterRest(img, cx = 150, cy = 50) // 休止符在 x=150
        val nh = Notehead(50, 50, 9, 7, 60)
        val rest = RestDetector.detect(blobs(img), listOf(nh), s, lineYs)
        assertEquals("应检测到 1 个休止符（符头被排除）", 1, rest.size)
        assertEquals(NoteDuration.QUARTER, rest[0].duration)
        assertTrue("休止符应在符头右侧", rest[0].centerX > 50)
    }

    @Test
    fun `blob in signature region is not detected as rest`() {
        val img = blank()
        // 全休止符在 x=50，但签名区右边界在 x=80
        wholeRest(img, cx = 50, line2Y = lineYs[1])
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs, signatureEndX = 80)
        assertTrue("签名区内的连通块不应被检测为休止符", rest.isEmpty())
    }

    @Test
    fun `thin vertical bar line is not detected as quarter rest`() {
        val img = blank()
        vLine(img, x = 100, cy = 50, height = 50) // 细竖线（小节线）
        val rest = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertTrue("小节线不应被误判为休止符", rest.isEmpty())
    }

    // ---- 多休止符测试 ------------------------------------------------------- //

    @Test
    fun `multiple rests are detected and sorted by x`() {
        val img = blank()
        quarterRest(img, cx = 50, cy = 50)
        wholeRest(img, cx = 150, line2Y = lineYs[1])
        halfRest(img, cx = 250, line3Y = lineYs[2])
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals("应检测到 3 个休止符", 3, rests.size)
        // 按 x 排序（容差 ±2 像素，因 Blob 中心计算可能有取整偏差）
        assertEquals(50.0, rests[0].centerX.toDouble(), 2.0)
        assertEquals(150.0, rests[1].centerX.toDouble(), 2.0)
        assertEquals(250.0, rests[2].centerX.toDouble(), 2.0)
        // 类型正确
        assertEquals(NoteDuration.QUARTER, rests[0].duration)
        assertEquals(NoteDuration.WHOLE, rests[1].duration)
        assertEquals(NoteDuration.HALF, rests[2].duration)
    }

    @Test
    fun `quarter rest followed by whole rest`() {
        val img = blank()
        quarterRest(img, cx = 60, cy = 50)
        wholeRest(img, cx = 180, line2Y = lineYs[1])
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertEquals(2, rests.size)
        assertEquals(NoteDuration.QUARTER, rests[0].duration)
        assertEquals(NoteDuration.WHOLE, rests[1].duration)
    }

    @Test
    fun `flagged rests of different durations are sorted and identified`() {
        val img = blank()
        sixteenthRest(img, cx = 50, cy = 50)
        thirtySecondRest(img, cx = 150, cy = 50)
        eighthRest(img, cx = 250, cy = 50)
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals("应检测到 3 个休止符", 3, rests.size)
        // 按 x 排序
        assertEquals(50.0, rests[0].centerX.toDouble(), 2.0)
        assertEquals(150.0, rests[1].centerX.toDouble(), 2.0)
        assertEquals(250.0, rests[2].centerX.toDouble(), 2.0)
        // 类型正确
        assertEquals(NoteDuration.SIXTEENTH, rests[0].duration)
        assertEquals(NoteDuration.THIRTY_SECOND, rests[1].duration)
        assertEquals(NoteDuration.EIGHTH, rests[2].duration)
    }

    @Test
    fun `sixteenth rest coexists with quarter rest at different x`() {
        val img = blank()
        sixteenthRest(img, cx = 50, cy = 50)
        quarterRest(img, cx = 150, cy = 50)
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs, image = img)
        assertEquals(2, rests.size)
        assertEquals(NoteDuration.SIXTEENTH, rests[0].duration)
        assertEquals(NoteDuration.QUARTER, rests[1].duration)
    }

    // ---- 边界情况 ----------------------------------------------------------- //

    @Test
    fun `empty blobs returns empty list`() {
        val img = blank()
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertTrue(rests.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        quarterRest(img, cx = 100, cy = 50)
        val rests = RestDetector.detect(blobs(img), emptyList(), 0, lineYs)
        assertTrue(rests.isEmpty())
    }

    @Test
    fun `block rest outside center space is not detected`() {
        val img = blank()
        // 在谱表顶部附近画一个小实心矩形（不在中央间内）
        val rw = s
        val rh = s / 2
        for (y in 20 until 20 + rh) for (x in 90 until 90 + rw) {
            if (x in 0 until w && y in 0 until h) img.set(x, y, true)
        }
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs)
        assertTrue("中央间外的实心矩形不应被误判为休止符", rests.isEmpty())
    }

    @Test
    fun `insufficient staff lines skips block rest classification`() {
        val img = blank()
        wholeRest(img, cx = 100, line2Y = lineYs[1])
        // 只给 2 根线，不足以区分全/二分
        val rests = RestDetector.detect(blobs(img), emptyList(), s, lineYs.subList(0, 2))
        assertTrue("谱线不足时不应检测全/二分休止符", rests.isEmpty())
    }
}
