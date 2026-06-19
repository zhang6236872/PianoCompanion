package com.pianocompanion.omr

import com.pianocompanion.omr.image.BinaryImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全管线集成测试：验证 OmrPipeline 真正消费 SignatureDetector 识别到的
 * 谱号 / 调号 / 拍号——谱号改变音高、调号升降半音、拍号写入 Score。
 *
 * 几何：宽 520 × 高 180；五条谱线 y=50,60,70,80,90（间距 s=10）。
 */
class OmrSignatureIntegrationTest {

    private val width = 520
    private val height = 180
    private val lineYs = listOf(50, 60, 70, 80, 90)
    private val s = 10

    private fun blank() = BinaryImage.blank(width, height)

    private fun drawStaff(img: BinaryImage) {
        for (y in lineYs) for (x in 0 until width) img.set(x, y, true)
    }

    private fun ellipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (x !in 0 until width || y !in 0 until height) continue
            val ndx = (x - cx).toDouble() / rx
            val ndy = (y - cy).toDouble() / ry
            if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
        }
    }

    private fun fillSquare(img: BinaryImage, x0: Int, y0: Int, size: Int) {
        for (y in y0 until y0 + size) for (x in x0 until x0 + size)
            if (x in 0 until width && y in 0 until height) img.set(x, y, true)
    }

    private fun drawBassClef(img: BinaryImage, x: Int) {
        for (y in lineYs.first() + s / 2..lineYs.last() - s / 2) for (dx in 0..4) img.set(x + dx, y, true)
        val fLineY = lineYs[1]
        fillSquare(img, x + 7, fLineY - s / 2, 3)
        fillSquare(img, x + 7, fLineY + s / 2, 3)
    }

    private fun drawTrebleClef(img: BinaryImage, x: Int) {
        val top = lineYs.first() - s * 5 / 2
        val bottom = lineYs.last()
        for (y in top..bottom) for (dx in 0..3) img.set(x + dx, y, true)
    }

    private fun drawSharp(img: BinaryImage, x: Int) {
        val yTop = lineYs.first() + s
        val yBot = lineYs.last() - s
        for (y in yTop..yBot) { img.set(x, y, true); img.set(x + 6, y, true) }
        for (y in listOf(yTop + s / 2, yBot - s / 2)) for (dx in 0..7) img.set(x + dx, y, true)
    }

    private fun renderDigit(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int) {
        val tmpl = com.pianocompanion.omr.image.SignatureDetector.DIGIT_TEMPLATES[digit]!!
        val gw = com.pianocompanion.omr.image.SignatureDetector.GRID_W
        val gh = com.pianocompanion.omr.image.SignatureDetector.GRID_H
        for (r in 0 until gh) for (c in 0 until gw) {
            if (!tmpl[r * gw + c]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                val x = x0 + c * scale + dx; val y = y0 + r * scale + dy
                if (x in 0 until width && y in 0 until height) img.set(x, y, true)
            }
        }
    }

    @Test
    fun `bass clef shifts bottom-line note to G2 instead of E4`() {
        val img = blank(); drawStaff(img); drawBassClef(img, 15)
        ellipse(img, 400, 90) // 底线
        val result = OmrPipeline.recognize(img, tempo = 120)
        assertEquals(1, result.score.notes.size)
        // 低音谱表底线 = G2 = MIDI 43（若误用高音谱表则为 E4 = 64）
        assertEquals(43, result.score.notes[0].midiNumber)
        assertTrue(result.warnings.any { it.contains("低音谱号") })
    }

    @Test
    fun `single staff without clef keeps default treble pitches`() {
        // 回归保护：无谱号时回退到高音谱表，底线音 = E4 = 64。
        val img = blank(); drawStaff(img); ellipse(img, 400, 90)
        val result = OmrPipeline.recognize(img, tempo = 120)
        assertEquals(64, result.score.notes[0].midiNumber)
    }

    @Test
    fun `one sharp key signature raises F4 to F-sharp4`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        drawSharp(img, 55) // 1 个升号 = G 大调
        ellipse(img, 400, 85) // F4 的位置（底线上方一个间）
        val result = OmrPipeline.recognize(img, tempo = 120)
        // C 大调下 F4=65；G 大调下升 F → F#4 = 66
        assertEquals(66, result.score.notes[0].midiNumber)
        assertTrue(result.warnings.any { it.contains("G大调") })
    }

    @Test
    fun `detected 3_over_4 time signature is written into the score`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        renderDigit(img, 3, x0 = 80, y0 = 36, scale = 5)
        renderDigit(img, 4, x0 = 80, y0 = 78, scale = 5)
        ellipse(img, 400, 70)
        val result = OmrPipeline.recognize(img, tempo = 120)
        assertEquals("3/4", result.score.timeSignature)
        assertTrue(result.warnings.any { it.contains("拍号识别：3/4") })
        // 3/4 → quartersPerMeasure=3 → measureMs = 500*3 = 1500ms；measureIndex 仍为 0。
        assertEquals(0, result.score.notes[0].measureIndex)
    }

    // ---- C 谱号 (中音 / 次中音) 全管线集成 ----------------------------------

    /**
     * C 谱号：竖直对称的紧凑字形，框住指定谱线 [centerLineY]。竖直对称保证
     * 黑像质心落在该线，使 SignatureDetector 能区分中音/次中音。
     */
    private fun drawCClef(img: BinaryImage, x: Int, centerLineY: Int) {
        val half = s * 3 / 2
        val top = centerLineY - half
        val bot = centerLineY + half
        for (y in top..bot) for (dx in 0..3) img.set(x + dx, y, true)
        for (dx in 0..9) {
            img.set(x + dx, centerLineY - 1, true)
            img.set(x + dx, centerLineY + 1, true)
        }
    }

    @Test
    fun `alto clef maps bottom-line note to F3 instead of E4`() {
        val img = blank(); drawStaff(img); drawCClef(img, 15, lineYs[2]) // 中音谱号(中央线)
        ellipse(img, 400, 90) // 底线
        val result = OmrPipeline.recognize(img, tempo = 120)
        assertEquals(1, result.score.notes.size)
        // 中音谱表底线 = F3 = MIDI 53（若误用高音谱表则为 E4 = 64）
        assertEquals(53, result.score.notes[0].midiNumber)
        assertTrue("应提示中音谱号，实际: ${result.warnings}",
            result.warnings.any { it.contains("中音谱号") })
    }

    @Test
    fun `alto clef middle-line note maps to middle C`() {
        val img = blank(); drawStaff(img); drawCClef(img, 15, lineYs[2])
        ellipse(img, 400, 70) // 中央线
        val result = OmrPipeline.recognize(img, tempo = 120)
        // 中音谱表中央线 = C4 = 60
        assertEquals(60, result.score.notes[0].midiNumber)
    }

    @Test
    fun `tenor clef maps bottom-line note to D3`() {
        val img = blank(); drawStaff(img); drawCClef(img, 15, lineYs[1]) // 次中音谱号(第2线)
        ellipse(img, 400, 90) // 底线
        val result = OmrPipeline.recognize(img, tempo = 120)
        assertEquals(1, result.score.notes.size)
        // 次中音谱表底线 = D3 = MIDI 50
        assertEquals(50, result.score.notes[0].midiNumber)
        assertTrue("应提示次中音谱号，实际: ${result.warnings}",
            result.warnings.any { it.contains("次中音谱号") })
    }
}
