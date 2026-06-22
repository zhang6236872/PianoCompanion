package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DynamicMarkingDetector] using synthetic binary images.
 *
 * These tests render letter templates ('p', 'm', 'f') below a mock staff system
 * and verify that the detector correctly identifies dynamic markings.
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

    @Test
    fun `letter templates are distinct`() {
        // 验证三个字母模板两两之间的汉明距离足够大（>10/35）
        val p = DynamicMarkingDetector.LETTER_TEMPLATES['p']!!
        val m = DynamicMarkingDetector.LETTER_TEMPLATES['m']!!
        val f = DynamicMarkingDetector.LETTER_TEMPLATES['f']!!

        fun hamming(a: BooleanArray, b: BooleanArray): Int {
            var d = 0
            for (i in a.indices) if (a[i] != b[i]) d++
            return d
        }

        assertTrue("p vs m 距离应 > 10", hamming(p, m) > 10)
        assertTrue("p vs f 距离应 > 10", hamming(p, f) > 10)
        assertTrue("m vs f 距离应 > 10", hamming(m, f) > 10)
    }
}
