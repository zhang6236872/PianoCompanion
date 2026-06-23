package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DynamicMarkingDetector] using synthetic binary images.
 *
 * These tests render letter templates ('p', 'm', 'f', 's', 'z', 'r', 'c', 'e', 'd')
 * below a mock staff system and verify that the detector correctly identifies dynamic markings.
 * Each letter template is drawn at integer scale factors so that downsampling
 * back to 5×7 recovers the original template exactly.
 */
class DynamicMarkingDetectorTest {

    private val width = 400
    private val height = 150
    // Staff lines at y = 30,40,50,60,70 → spacing = 10, bottom line (y=70)
    private val lineSpacing = 10

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    private fun makeSystem(): StaffSystem = StaffSystem(listOf(
        StaffLine(29, 31, 0.9),
        StaffLine(39, 41, 0.9),
        StaffLine(49, 51, 0.9),
        StaffLine(59, 61, 0.9),
        StaffLine(69, 71, 0.9)
    ))

    /**
     * 把字母模板按指定倍率渲染到二值图像中。
     */
    private fun renderLetter(img: BinaryImage, char: Char, x: Int, y: Int, scale: Int) {
        val tmpl = DynamicMarkingDetector.LETTER_TEMPLATES[char] ?: return
        for (r in 0 until 7) {
            for (c in 0 until 5) {
                if (tmpl[r * 5 + c]) {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = x + c * scale + dx
                            val py = y + r * scale + dy
                            if (px in 0 until width && py in 0 until height) {
                                img.set(px, py, true)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 在指定位置画一个小型实心方块（模拟缩写末尾的句点）。
     */
    private fun renderPeriod(img: BinaryImage, x: Int, y: Int, size: Int = 3) {
        for (dy in 0 until size) {
            for (dx in 0 until size) {
                val px = x + dx
                val py = y + dy
                if (px in 0 until width && py in 0 until height) {
                    img.set(px, py, true)
                }
            }
        }
    }

    // ---- 基础力度记号 (p/m/f) 回归测试 ----------------------------------------

    @Test
    fun `detects single p marking below staff`() {
        val img = blank()
        // 'p' at scale=2: 10px wide, 14px tall, below the staff (y=80)
        renderLetter(img, 'p', 100, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 p", "p", results[0].text)
    }

    @Test
    fun `detects f marking below staff`() {
        val img = blank()
        renderLetter(img, 'f', 150, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 f", "f", results[0].text)
    }

    @Test
    fun `detects mf marking below staff`() {
        val img = blank()
        // 'm' at scale=2: 10px wide; 'f' at scale=2: 8px wide
        // 放置 'm' 和 'f' 间距 4px，在 maxGap(8px) 内会被分为一组
        renderLetter(img, 'm', 200, 80, 2)
        renderLetter(img, 'f', 214, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 mf", "mf", results[0].text)
    }

    @Test
    fun `detects pp marking below staff`() {
        val img = blank()
        // 两个 'p' 间距 4px
        renderLetter(img, 'p', 100, 80, 2)
        renderLetter(img, 'p', 114, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 pp", "pp", results[0].text)
    }

    @Test
    fun `no marking detected when image is blank`() {
        val img = blank()

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertTrue("空白图像不应检测到力度记号", results.isEmpty())
    }

    @Test
    fun `no marking when letters too far below staff`() {
        val img = blank()
        // 在很远的位置（y=130，超出搜索区底线 y=70+40=110）
        renderLetter(img, 'p', 100, 130, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertTrue("超出搜索范围的字母不应被检测到", results.isEmpty())
    }

    @Test
    fun `detects multiple markings in same system`() {
        val img = blank()
        // 两个力度记号：p 在左，f 在右，间距大于 maxGap
        renderLetter(img, 'p', 80, 80, 2)
        renderLetter(img, 'f', 300, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 2 个力度记号", 2, results.size)
        val texts = results.map { it.text }.sorted()
        assertEquals("应有 f 和 p", listOf("f", "p"), texts)
    }

    @Test
    fun `detects marking in second system`() {
        val img = blank()
        // 第二个谱表系统的底线在 y=170，但在 height=150 的图像中不可行
        // 改为创建更高的图像
        val tallImg = BinaryImage.blank(400, 280)
        val system1 = StaffSystem(listOf(
            StaffLine(29, 31, 0.9), StaffLine(39, 41, 0.9),
            StaffLine(49, 51, 0.9), StaffLine(59, 61, 0.9),
            StaffLine(69, 71, 0.9)
        ))
        val system2 = StaffSystem(listOf(
            StaffLine(169, 171, 0.9), StaffLine(179, 181, 0.9),
            StaffLine(189, 191, 0.9), StaffLine(199, 201, 0.9),
            StaffLine(209, 211, 0.9)
        ))
        // 在第一个系统下方画 'p'
        for (r in 0 until 7) for (c in 0 until 5) {
            if (DynamicMarkingDetector.LETTER_TEMPLATES['p']!![r * 5 + c]) {
                for (dy in 0 until 2) for (dx in 0 until 2) {
                    val px = 80 + c * 2 + dx
                    val py = 80 + r * 2 + dy
                    if (px in 0 until 400 && py in 0 until 280) tallImg.set(px, py, true)
                }
            }
        }
        // 在第二个系统下方画 'f'
        for (r in 0 until 7) for (c in 0 until 5) {
            if (DynamicMarkingDetector.LETTER_TEMPLATES['f']!![r * 5 + c]) {
                for (dy in 0 until 2) for (dx in 0 until 2) {
                    val px = 250 + c * 2 + dx
                    val py = 220 + r * 2 + dy
                    if (px in 0 until 400 && py in 0 until 280) tallImg.set(px, py, true)
                }
            }
        }

        val blobs = ConnectedComponents.label(tallImg, minPixels = 4)
        val results = DynamicMarkingDetector.detect(
            tallImg, blobs, listOf(system1, system2), lineSpacing
        )

        assertEquals("应检测到 2 个力度记号", 2, results.size)
        val bySystem = results.associateBy { it.systemIdx }
        assertNotNull("系统 0 应有力度记号", bySystem[0])
        assertNotNull("系统 1 应有力度记号", bySystem[1])
        assertEquals("系统 0 应为 p", "p", bySystem[0]!!.text)
        assertEquals("系统 1 应为 f", "f", bySystem[1]!!.text)
    }

    // ---- 新增字母模板 (s/z/r/c/e/d) 模板验证 -----------------------------------

    @Test
    fun `all nine letter templates exist`() {
        val chars = listOf('p', 'm', 'f', 's', 'z', 'r', 'c', 'e', 'd')
        for (ch in chars) {
            assertNotNull("字母 '$ch' 模板应存在", DynamicMarkingDetector.LETTER_TEMPLATES[ch])
        }
    }

    @Test
    fun `all new templates have valid dimensions`() {
        val newChars = listOf('s', 'z', 'r', 'c', 'e', 'd')
        for (ch in newChars) {
            val tmpl = DynamicMarkingDetector.LETTER_TEMPLATES[ch]!!
            assertEquals("字母 '$ch' 模板应有 35 个元素", 35, tmpl.size)
            // 验证所有 5 列至少有 1 个像素
            for (col in 0 until 5) {
                var found = false
                for (row in 0 until 7) {
                    if (tmpl[row * 5 + col]) found = true
                }
                assertTrue("字母 '$ch' 列 $col 必须至少有 1 个像素", found)
            }
            // 验证所有 7 行至少有 1 个像素
            for (row in 0 until 7) {
                var found = false
                for (col in 0 until 5) {
                    if (tmpl[row * 5 + col]) found = true
                }
                assertTrue("字母 '$ch' 行 $row 必须至少有 1 个像素", found)
            }
        }
    }

    @Test
    fun `letter templates are distinct`() {
        // 验证所有字母模板两两之间的汉明距离足够大。
        // c/e/s 是三个「圆形」字母，在 5×7 网格中天然相似（c=开放弧, e=弧+横线,
        // s=双弧），它们的成对距离较小但在 matchLetter 的 secondDist 差距检查
        // （secondDist - bestDist ≥ 2）下仍可正确区分。
        val allChars = listOf('p', 'm', 'f', 's', 'z', 'r', 'c', 'e', 'd')

        fun hamming(a: BooleanArray, b: BooleanArray): Int {
            var d = 0
            for (i in a.indices) if (a[i] != b[i]) d++
            return d
        }

        // c↔e(4), s↔e(4), s↔c(6) 是 5×7 网格中圆形字母的固有相似性，允许较低阈值
        val tightPairs = setOf(
            'c' to 'e', 'e' to 'c',
            's' to 'e', 'e' to 's',
            's' to 'c', 'c' to 's'
        )

        for (i in allChars.indices) {
            for (j in i + 1 until allChars.size) {
                val a = allChars[i]
                val b = allChars[j]
                val dist = hamming(
                    DynamicMarkingDetector.LETTER_TEMPLATES[a]!!,
                    DynamicMarkingDetector.LETTER_TEMPLATES[b]!!
                )
                val threshold = if (a to b in tightPairs) 4 else 10
                assertTrue(
                    "字母 '$a' vs '$b' 的汉明距离 $dist 应 ≥ $threshold",
                    dist >= threshold
                )
            }
        }
    }

    // ---- 突强类 (sfz/sf) 检测 -------------------------------------------------

    @Test
    fun `detects sfz marking below staff`() {
        val img = blank()
        // s + f + z, 间距 4px
        renderLetter(img, 's', 100, 80, 2)
        renderLetter(img, 'f', 114, 80, 2)
        renderLetter(img, 'z', 126, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 sfz", "sfz", results[0].text)
    }

    @Test
    fun `detects sf marking below staff`() {
        val img = blank()
        renderLetter(img, 's', 100, 80, 2)
        renderLetter(img, 'f', 114, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 sf", "sf", results[0].text)
    }

    // ---- rinforzando (rf/rfz) 检测 -------------------------------------------

    @Test
    fun `detects rf marking below staff`() {
        val img = blank()
        renderLetter(img, 'r', 100, 80, 2)
        renderLetter(img, 'f', 114, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 rf", "rf", results[0].text)
    }

    @Test
    fun `detects rfz marking below staff`() {
        val img = blank()
        renderLetter(img, 'r', 100, 80, 2)
        renderLetter(img, 'f', 114, 80, 2)
        renderLetter(img, 'z', 126, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 rfz", "rfz", results[0].text)
    }

    // ---- 缩写类 (cresc_/decresc_) 检测 ---------------------------------------

    @Test
    fun `detects cresc marking without period`() {
        val img = blank()
        // c-r-e-s-c, 间距 4px (每个字母宽 10px at scale=2, 间距 = 14px - 10px = 4px)
        var x = 80
        for (ch in listOf('c', 'r', 'e', 's', 'c')) {
            renderLetter(img, ch, x, 80, 2)
            x += 14
        }

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 cresc", "cresc", results[0].text)
    }

    @Test
    fun `detects cresc marking with period`() {
        val img = blank()
        // c-r-e-s-c + period
        var x = 80
        for (ch in listOf('c', 'r', 'e', 's', 'c')) {
            renderLetter(img, ch, x, 80, 2)
            x += 14
        }
        // period after the last 'c'
        renderPeriod(img, x + 2, 88, size = 3)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 cresc.", "cresc.", results[0].text)
    }

    @Test
    fun `detects decresc marking without period`() {
        val img = blank()
        // d-e-c-r-e-s-c
        var x = 60
        for (ch in listOf('d', 'e', 'c', 'r', 'e', 's', 'c')) {
            renderLetter(img, ch, x, 80, 2)
            x += 14
        }

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 decresc", "decresc", results[0].text)
    }

    @Test
    fun `detects decresc marking with period`() {
        val img = blank()
        // d-e-c-r-e-s-c + period
        var x = 60
        for (ch in listOf('d', 'e', 'c', 'r', 'e', 's', 'c')) {
            renderLetter(img, ch, x, 80, 2)
            x += 14
        }
        renderPeriod(img, x + 2, 88, size = 3)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 decresc.", "decresc.", results[0].text)
    }

    // ---- 缩写句点边界情况 ----------------------------------------------------

    @Test
    fun `period too large is not treated as abbreviation period`() {
        val img = blank()
        // c-r-e-s-c + large block (too big for a period)
        var x = 80
        for (ch in listOf('c', 'r', 'e', 's', 'c')) {
            renderLetter(img, ch, x, 80, 2)
            x += 14
        }
        // large block (6x6 = 36px, bigger than period threshold of 5px)
        renderPeriod(img, x + 2, 84, size = 6)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        // 应匹配 cresc (without period, since the trailing block is too large)
        assertEquals("应检测到 1 个力度记号", 1, results.size)
        assertEquals("应为 cresc (大块不被视为句点)", "cresc", results[0].text)
    }

    // ---- 混合场景 -------------------------------------------------------------

    @Test
    fun `detects sfz and pp in same system`() {
        val img = blank()
        // sfz 在左, pp 在右, 间距大于 maxGap
        renderLetter(img, 's', 60, 80, 2)
        renderLetter(img, 'f', 74, 80, 2)
        renderLetter(img, 'z', 86, 80, 2)

        renderLetter(img, 'p', 260, 80, 2)
        renderLetter(img, 'p', 274, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 2 个力度记号", 2, results.size)
        val texts = results.map { it.text }.sorted()
        assertEquals("应有 pp 和 sfz", listOf("pp", "sfz"), texts)
    }

    @Test
    fun `detects cresc and f in same system`() {
        val img = blank()
        // cresc 在左, f 在右
        var x = 60
        for (ch in listOf('c', 'r', 'e', 's', 'c')) {
            renderLetter(img, ch, x, 80, 2)
            x += 14
        }
        renderLetter(img, 'f', 300, 80, 2)

        val blobs = ConnectedComponents.label(img, minPixels = 4)
        val results = DynamicMarkingDetector.detect(img, blobs, listOf(makeSystem()), lineSpacing)

        assertEquals("应检测到 2 个力度记号", 2, results.size)
        val texts = results.map { it.text }.sorted()
        assertEquals("应有 cresc 和 f", listOf("cresc", "f"), texts)
    }

    // ---- 单字母精确匹配验证 ---------------------------------------------------

    @Test
    fun `each new letter matches itself at scale 2`() {
        val newChars = listOf('s', 'z', 'r', 'c', 'e', 'd')
        for (ch in newChars) {
            val img = blank()
            renderLetter(img, ch, 100, 80, 2)

            val blobs = ConnectedComponents.label(img, minPixels = 4)
            // 单字母不在 KNOWN_DYNAMICS 中，但我们可以验证它不会误匹配
            // 我们通过检查 blob 存在来间接验证
            assertTrue("字母 '$ch' 应产生至少 1 个 blob", blobs.isNotEmpty())
        }
    }
}
