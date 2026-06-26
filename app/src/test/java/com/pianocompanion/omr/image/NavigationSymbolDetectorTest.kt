package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NavigationSymbolDetector] 的单元测试——使用合成二值图像验证 Segno (𝄋) 和
 * Coda (𝄐) 导航符号的检测。
 *
 * 测试策略：
 * - 逐像素绘制合成符号（X 形 Segno、圆环+十字 Coda）
 * - 验证正确检测、正确分类、多系统、边界条件、误判拒绝
 */
class NavigationSymbolDetectorTest {

    private val width = 400
    private val height = 150
    private val s = 10 // 谱线间距

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** 谱表线在 y = 80,90,100,110,120 → 间距 = 10，顶线 y=80。 */
    private val lineYs = listOf(80, 90, 100, 110, 120)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /** 从图像构建连通块列表。 */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    // ====================================================================
    // 绘制辅助方法
    // ====================================================================

    /** 用 Bresenham 算法画一条带厚度的对角线。 */
    private fun drawThickLine(img: BinaryImage, x0: Int, y0: Int, x1: Int, y1: Int) {
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var x = x0
        var y = y0
        while (true) {
            setPixelWithThickness(img, x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
        }
    }

    /** 设置像素及其右、下邻居（形成 2px 厚度）。 */
    private fun setPixelWithThickness(img: BinaryImage, x: Int, y: Int) {
        for (dy in 0..1) {
            for (dx in 0..1) {
                val px = x + dx
                val py = y + dy
                if (px in 0 until img.width && py in 0 until img.height) {
                    img.set(px, py, true)
                }
            }
        }
    }

    /** 画一个填充矩形。 */
    private fun fillRect(img: BinaryImage, x0: Int, y0: Int, w: Int, h: Int) {
        for (y in y0 until y0 + h) {
            for (x in x0 until x0 + w) {
                if (x in 0 until img.width && y in 0 until img.height) {
                    img.set(x, y, true)
                }
            }
        }
    }

    /**
     * 绘制合成 Segno (𝄋)：两条交叉对角线形成 X 形 + 两个圆点。
     * X 形提供双侧墨迹和多行段分布；交叉点确保连通性。
     */
    private fun drawSegno(img: BinaryImage, cx: Int, cy: Int, halfSize: Int = 10) {
        // 两条对角线交叉
        drawThickLine(img, cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize)
        drawThickLine(img, cx + halfSize, cy - halfSize, cx - halfSize, cy + halfSize)
        // 两个圆点（紧贴 X 端点确保连通）
        fillRect(img, cx - halfSize - 3, cy - halfSize - 3, 3, 3)
        fillRect(img, cx + halfSize + 1, cy + halfSize + 1, 3, 3)
    }

    /**
     * 绘制合成 Coda (𝄐)：圆环轮廓 + 中心十字。
     * 圆环和十字在四个交点（上下左右）连通，形成单一连通块。
     * 四个角落区域形成封闭空洞。
     */
    private fun drawCoda(img: BinaryImage, cx: Int, cy: Int, radius: Int = 7) {
        // 圆环（厚度 2）
        for (y in (cy - radius - 1)..(cy + radius + 1)) {
            for (x in (cx - radius - 1)..(cx + radius + 1)) {
                if (x !in 0 until img.width || y !in 0 until img.height) continue
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                if (dist in (radius - 1.5)..(radius + 1.5)) {
                    img.set(x, y, true)
                }
            }
        }
        // 水平十字笔画
        for (x in (cx - radius - 1)..(cx + radius + 1)) {
            if (x in 0 until img.width && cy in 0 until img.height) {
                img.set(x, cy, true)
            }
        }
        // 垂直十字笔画
        for (y in (cy - radius - 1)..(cy + radius + 1)) {
            if (cx in 0 until img.width && y in 0 until img.height) {
                img.set(cx, y, true)
            }
        }
    }

    /**
     * 绘制合成 Fermata (⌒)：穹顶弧线。用于验证导航符号检测器不会误判 fermata。
     */
    private fun drawFermataArc(
        img: BinaryImage, cx: Int, baseY: Int,
        halfWidth: Int = 7, peakHeight: Int = 6, thickness: Int = 2
    ) {
        for (x in (cx - halfWidth)..(cx + halfWidth)) {
            if (x !in 0 until width) continue
            val t = (x - (cx - halfWidth)).toDouble() / (2 * halfWidth)
            val offset = (peakHeight * kotlin.math.sin(Math.PI * t)).toInt()
            val arcY = baseY - offset
            for (dy in 0 until thickness) {
                val y = arcY + dy
                if (y in 0 until height) img.set(x, y, true)
            }
        }
    }

    // ====================================================================
    // Segno 检测测试
    // ====================================================================

    @Test
    fun `segno above staff is detected`() {
        val img = blank()
        drawSegno(img, 200, 50)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个导航符号", 1, results.size)
        assertEquals(
            "应为 SEGNO 类型",
            NavigationSymbolDetector.NavigationSymbolType.SEGNO,
            results[0].type
        )
        assertEquals(0, results[0].systemIdx)
    }

    @Test
    fun `segno correctly classified as SEGNO not CODA`() {
        val img = blank()
        drawSegno(img, 200, 50)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertEquals(1, results.size)
        assertTrue(
            "Segno 不应被误判为 Coda",
            results[0].type == NavigationSymbolDetector.NavigationSymbolType.SEGNO
        )
    }

    // ====================================================================
    // Coda 检测测试
    // ====================================================================

    @Test
    fun `coda above staff is detected`() {
        val img = blank()
        drawCoda(img, 200, 50)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 1 个导航符号", 1, results.size)
        assertEquals(
            "应为 CODA 类型",
            NavigationSymbolDetector.NavigationSymbolType.CODA,
            results[0].type
        )
        assertEquals(0, results[0].systemIdx)
    }

    @Test
    fun `coda correctly classified as CODA not SEGNO`() {
        val img = blank()
        drawCoda(img, 200, 50)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertEquals(1, results.size)
        assertTrue(
            "Coda 不应被误判为 Segno",
            results[0].type == NavigationSymbolDetector.NavigationSymbolType.CODA
        )
    }

    // ====================================================================
    // 多符号检测
    // ====================================================================

    @Test
    fun `both segno and coda detected`() {
        val img = blank()
        drawSegno(img, 120, 50)
        drawCoda(img, 280, 50)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertEquals("应检测到 2 个导航符号", 2, results.size)
        val types = results.map { it.type }.toSet()
        assertTrue("应同时包含 SEGNO 和 CODA", types.size == 2)
    }

    @Test
    fun `no navigation symbols returns empty`() {
        val img = blank()
        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)
        assertTrue("空白图像不应检测到导航符号", results.isEmpty())
    }

    // ====================================================================
    // 误判拒绝测试
    // ====================================================================

    @Test
    fun `fermata dome not mistaken for navigation symbol`() {
        val img = blank()
        // 画一个 fermata 穹顶弧（在搜索区域内）
        drawFermataArc(img, 200, baseY = 60)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertTrue(
            "Fermata 穹顶不应被检测为导航符号",
            results.isEmpty()
        )
    }

    @Test
    fun `small noise blob rejected`() {
        val img = blank()
        // 画一个很小的噪点块（远小于最小尺寸约束）
        fillRect(img, 195, 48, 4, 4)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertTrue("小型噪点块不应被检测为导航符号", results.isEmpty())
    }

    @Test
    fun `horizontal bar rejected`() {
        val img = blank()
        // 画一条水平细条（宽高比极端，不像导航符号）
        for (x in 180..220) {
            for (y in 49..51) {
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertTrue(
            "水平细条不应被检测为导航符号",
            results.isEmpty()
        )
    }

    // ====================================================================
    // 多系统测试
    // ====================================================================

    @Test
    fun `navigation symbols in different systems detected`() {
        val h2 = 280
        val img2 = BinaryImage.blank(width, h2)
        // 系统 0：线在 y=80..120
        val sys0Lines = listOf(80, 90, 100, 110, 120)
        // 系统 1：线在 y=180..220
        val sys1Lines = listOf(180, 190, 200, 210, 220)

        // 系统 0 上方画 Segno
        drawSegno(img2, 120, 50)
        // 系统 1 上方画 Coda
        drawCoda(img2, 280, 150)

        val systems = listOf(makeSystem(sys0Lines), makeSystem(sys1Lines))
        val results = NavigationSymbolDetector.detect(img2, blobs(img2), systems, s)

        assertEquals("应检测到 2 个导航符号", 2, results.size)
        // 验证分别属于不同系统
        val sysIndices = results.map { it.systemIdx }.toSet()
        assertTrue("两个符号应属于不同系统", sysIndices.size == 2)
    }

    // ====================================================================
    // 边界条件
    // ====================================================================

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawSegno(img, 200, 50)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, 0)

        assertTrue("谱线间距为 0 时应返回空列表", results.isEmpty())
    }

    @Test
    fun `empty systems returns empty`() {
        val img = blank()
        drawSegno(img, 200, 50)

        val results = NavigationSymbolDetector.detect(img, blobs(img), emptyList(), s)

        assertTrue("无谱表系统时应返回空列表", results.isEmpty())
    }

    @Test
    fun `symbol below search region not detected`() {
        val img = blank()
        // 在谱表区域内画一个 Segno（不应该被检测）
        drawSegno(img, 200, 100)

        val systems = listOf(makeSystem())
        val results = NavigationSymbolDetector.detect(img, blobs(img), systems, s)

        assertTrue(
            "搜索区域之外的符号不应被检测",
            results.isEmpty()
        )
    }
}
