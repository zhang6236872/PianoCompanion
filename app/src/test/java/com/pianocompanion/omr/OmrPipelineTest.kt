package com.pianocompanion.omr

import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.OtsuThresholder
import com.pianocompanion.omr.image.StaffLineDetector
import com.pianocompanion.omr.image.StaffLineRemover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests for the pure-Kotlin OMR pipeline using **synthetic** score
 * images drawn pixel-by-pixel. This verifies the whole recognition chain
 * (staff detection → staff removal → notehead localization → pitch mapping →
 * sequencing) without needing a real photograph or an Android device.
 */
class OmrPipelineTest {

    /** Image geometry used across tests. */
    private val width = 420
    private val height = 120
    // Staff lines at y = 30,40,50,60,70  → spacing = 10, bottom line (y=70) = E4.
    private val lineYs = listOf(30, 40, 50, 60, 70)

    private fun blankScore(): BinaryImage = BinaryImage.blank(width, height)

    private fun drawStaff(img: BinaryImage) {
        for (y in lineYs) for (x in 0 until width) img.set(x, y, true)
    }

    private fun drawEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val ndx = (x - cx).toDouble() / rx
                val ndy = (y - cy).toDouble() / ry
                if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
            }
        }
    }

    @Test
    fun `otsu threshold separates ink from paper`() {
        val ink = (0 until 80).map { 20 + (it % 40) }        // dark cluster 20..59
        val paper = (80 until 200).map { 180 + (it % 60) }   // light cluster 180..239
        val gray = (ink + paper).toIntArray()
        val t = OtsuThresholder.threshold(gray)
        val binary = BinaryImage.fromGrayscale(gray.size, 1, gray, t)
        // Contract: every dark pixel must binarize to black, every light one to white.
        ink.indices.forEach { i -> assertTrue("ink pixel $i should be black", binary.pixels[i]) }
        paper.indices.forEach { j ->
            assertTrue("paper pixel should be white", !binary.pixels[j + ink.size])
        }
    }

    @Test
    fun `staff detector finds five lines and correct spacing`() {
        val img = blankScore()
        drawStaff(img)
        val systems = StaffLineDetector.detect(img)
        assertEquals(1, systems.size)
        assertEquals(5, systems[0].lines.size)
        assertEquals(10, systems[0].lineSpacing)
        // bottom line center should be y=70
        assertEquals(70, systems[0].bottomLine.center)
    }

    @Test
    fun `staff remover preserves a notehead sitting on a line`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 200, 60) // centered exactly on the y=60 staff line
        val cleaned = StaffLineRemover.remove(img, minLineRun = 210, maxLineThickness = 3)
        val blobs = ConnectedComponents.label(cleaned, minPixels = 4)
        // The notehead must survive as a single blob; the staff lines must be gone.
        assertEquals("expected exactly the notehead blob", 1, blobs.size)
        assertTrue("notehead should be reasonably sized", blobs[0].area in 20..60)
    }

    @Test
    fun `pipeline extracts ascending C-major scale from synthetic treble staff`() {
        val img = blankScore()
        drawStaff(img)
        // Noteheads on successive staff positions: E4 F4 G4 A4 B4 C5 D5 E5
        val positions = listOf(
            60 to 70, 110 to 65, 160 to 60, 210 to 55,
            260 to 50, 310 to 45, 360 to 40, 400 to 35
        )
        positions.forEach { (x, y) -> drawEllipse(img, x, y) }

        val result = OmrPipeline.recognize(img, title = "测试音阶", tempo = 120)

        assertEquals(1, result.diagnostics.systemCount)
        assertEquals(8, result.diagnostics.noteheadCount)
        val midis = result.score.notes.map { it.midiNumber }
        assertEquals(listOf(64, 65, 67, 69, 71, 72, 74, 76), midis)
        // Timing: 8 sequential quarter notes at 120 BPM = 500ms each.
        val starts = result.score.notes.map { it.startTime }
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L, 3000L, 3500L), starts)
        assertEquals(500L, result.score.notes.first().duration)
    }

    @Test
    fun `pipeline assigns chord notes the same start time`() {
        val img = blankScore()
        drawStaff(img)
        // Two noteheads in the same horizontal column (a third): C5 + E5
        drawEllipse(img, 100, 45) // C5
        drawEllipse(img, 100, 35) // E5
        // A later single notehead
        drawEllipse(img, 200, 60) // G4

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(3, result.score.notes.size)
        val grouped = result.score.notes.groupBy { it.startTime }
        // Two distinct time slots: the chord and the following note.
        assertEquals(2, grouped.size)
        val chord = grouped[0L] ?: error("missing chord")
        assertEquals(setOf(72, 76), chord.map { it.midiNumber }.toSet())
    }

    @Test
    fun `pipeline returns empty result and warning when no staff is present`() {
        val img = blankScore() // pure white — no staff lines
        val result = OmrPipeline.recognize(img)
        assertTrue(result.isEmpty)
        assertTrue(result.warnings.any { it.contains("五线谱") })
        assertEquals(0, result.diagnostics.systemCount)
    }

    @Test
    fun `recognized score is tagged as OMR source`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 100, 60) // G4
        val result = OmrPipeline.recognize(img)
        assertFalse(result.isEmpty)
        assertEquals(com.pianocompanion.data.model.ScoreSource.OMR, result.score.source)
    }

    // ---- 节奏分析集成测试 ----------------------------------------------------

    /** 实心椭圆 + 向上符干（与符头融合成一个连通块）。 */
    private fun drawStemmedFilled(img: BinaryImage, cx: Int, cy: Int, stemLen: Int = 24) {
        drawEllipse(img, cx, cy)
        val x = cx + 4
        val topY = cy - 3
        for (y in topY downTo topY - stemLen) if (y in 0 until height) img.set(x, y, true)
    }

    /** 空心(环状)椭圆。 */
    private fun drawHollow(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (x !in 0 until width || y !in 0 until height) continue
            val ndx = (x - cx).toDouble() / rx
            val ndy = (y - cy).toDouble() / ry
            val d = ndx * ndx + ndy * ndy
            if (d <= 1.01 && d >= 0.45) img.set(x, y, true)
        }
    }

    /** 空心椭圆 + 向上符干（二分音符）。 */
    private fun drawStemmedHollow(img: BinaryImage, cx: Int, cy: Int, stemLen: Int = 24) {
        drawHollow(img, cx, cy)
        val x = cx + 4
        val topY = cy - 3
        for (y in topY downTo topY - stemLen) if (y in 0 until height) img.set(x, y, true)
    }

    @Test
    fun `pipeline detects a quarter note with stem via secondary pass`() {
        val img = blankScore()
        drawStaff(img)
        drawStemmedFilled(img, 200, 60) // A4，带符干
        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("应通过二次扫描识别出带干符头", 1, result.diagnostics.noteheadCount)
        assertEquals(1, result.score.notes.size)
        // 实心 + 符干 + 无尾 = 四分音符 = 500ms
        assertEquals(500L, result.score.notes[0].duration)
    }

    @Test
    fun `pipeline assigns half-note duration to hollow stemmed notehead`() {
        val img = blankScore()
        drawStaff(img)
        drawStemmedHollow(img, 200, 60)
        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(1, result.diagnostics.noteheadCount)
        // 空心 + 符干 = 二分音符 = 2 × 500 = 1000ms
        assertEquals(1000L, result.score.notes[0].duration)
    }

    @Test
    fun `pipeline sequences mixed durations with correct timing`() {
        val img = blankScore()
        drawStaff(img)
        // 二分音符 (1000ms) 后接四分音符 (500ms)
        drawStemmedHollow(img, 120, 60)
        drawStemmedFilled(img, 260, 60)
        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(2, result.score.notes.size)
        val sorted = result.score.notes.sortedBy { it.startTime }
        assertEquals(0L, sorted[0].startTime)
        assertEquals(1000L, sorted[1].startTime) // 第二个音在二分音符结束后才开始
        assertEquals(500L, sorted[1].duration)
    }

    @Test
    fun `pipeline rhythm warning reflects detected durations`() {
        val img = blankScore()
        drawStaff(img)
        drawStemmedHollow(img, 200, 60) // 二分音符
        val result = OmrPipeline.recognize(img, tempo = 120)

        // 检测到符干/非四分时值时，提示文案应体现已做节奏分析
        assertTrue(
            result.warnings.any { it.contains("节奏已通过符干") }
        )
    }
}
