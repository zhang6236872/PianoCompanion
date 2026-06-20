package com.pianocompanion.omr

import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.Deskewer
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

    // ---- 连梁组（beamed group）合成图辅助 -----------------------------------

    /**
     * 绘制一组共享横梁的实心符头（符干向上），全部融合成一个连通块。
     *
     * @param centers 各符头中心的 x 坐标（从左到右）。
     * @param cy      符头中心的 y 坐标（统一高度）。
     * @param beamY   横梁顶端 y（符干向上延伸至此）。
     * @param beamLayers 横梁层数（1=单横梁八分, 2=双横梁十六分）。
     */
    private fun drawBeamedGroup(
        img: BinaryImage,
        centers: List<Int>,
        cy: Int,
        beamY: Int,
        beamLayers: Int = 1
    ) {
        for (cx in centers) drawEllipse(img, cx, cy)
        // 符干：每个符头右边缘向上到横梁
        for (cx in centers) {
            val x = cx + 4
            for (y in (cy - 3) downTo beamY) if (y in 0 until height) img.set(x, y, true)
        }
        // 横梁：逐层连接所有符干顶端（层间距 4px：2px 横梁 + 2px 间隙，
        // 保证节奏分析器能区分多层横梁）。
        val xLeft = centers.min() + 4
        val xRight = centers.max() + 4
        for (layer in 0 until beamLayers) {
            for (y in (beamY + layer * 4)..(beamY + layer * 4 + 1)) {
                if (y !in 0 until height) continue
                for (x in xLeft..xRight) if (x in 0 until width) img.set(x, y, true)
            }
        }
    }

    /**
     * 绘制一组共享横梁的实心符头（**符干向下**），融合成一个连通块。
     *
     * @param beamY 横梁底端 y（符干向下延伸至此）。
     */
    private fun drawBeamedGroupDown(
        img: BinaryImage,
        centers: List<Int>,
        cy: Int,
        beamY: Int,
        beamLayers: Int = 1
    ) {
        for (cx in centers) drawEllipse(img, cx, cy)
        for (cx in centers) {
            val x = cx + 4
            for (y in (cy + 3)..beamY) if (y in 0 until height) img.set(x, y, true)
        }
        val xLeft = centers.min() + 4
        val xRight = centers.max() + 4
        for (layer in 0 until beamLayers) {
            for (y in (beamY - layer * 4 - 1)..(beamY - layer * 4)) {
                if (y !in 0 until height) continue
                for (x in xLeft..xRight) if (x in 0 until width) img.set(x, y, true)
            }
        }
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

    // ---- 连梁组切分集成测试 --------------------------------------------------

    @Test
    fun `pipeline splits a beamed pair into two noteheads`() {
        val img = blankScore()
        drawStaff(img)
        // 两个同音符头 + 向上符干 + 共享单横梁（连梁组），融合成一个宽连通块。
        // beamY=33 落在第 1、2 条谱线(30/40)之间，避免与谱线重叠被擦除。
        drawBeamedGroup(img, listOf(150, 210), cy = 65, beamY = 33, beamLayers = 1)

        val result = OmrPipeline.recognize(img, tempo = 120)

        // 之前整个连梁组因"过宽"被丢弃；现在应切分出 2 个符头。
        assertEquals("连梁组应切分为 2 个符头", 2, result.diagnostics.noteheadCount)
        assertEquals(2, result.score.notes.size)
        // 共享单横梁 → 八分音符 = 250ms（120 BPM）
        result.score.notes.forEach {
            assertEquals("连梁音符应为八分音符", 250L, it.duration)
        }
        // 时序：第二个音在第一个音之后 250ms 开始
        val sorted = result.score.notes.sortedBy { it.startTime }
        assertEquals(0L, sorted[0].startTime)
        assertEquals(250L, sorted[1].startTime)
    }

    @Test
    fun `pipeline splits a beamed pair with double beam into sixteenths`() {
        val img = blankScore()
        drawStaff(img)
        // 双横梁 → 十六分音符（beamY=33 落在谱线之间，避免与谱线重叠）
        drawBeamedGroup(img, listOf(150, 210), cy = 65, beamY = 33, beamLayers = 2)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(2, result.diagnostics.noteheadCount)
        assertEquals(2, result.score.notes.size)
        // 十六分音符 = 125ms（120 BPM）
        result.score.notes.forEach {
            assertEquals("双横梁应为十六分音符", 125L, it.duration)
        }
        val sorted = result.score.notes.sortedBy { it.startTime }
        assertEquals(0L, sorted[0].startTime)
        assertEquals(125L, sorted[1].startTime)
    }

    @Test
    fun `pipeline splits a stems-down beamed pair`() {
        val img = blankScore()
        drawStaff(img)
        // 符干向下：横梁在符头下方
        drawBeamedGroupDown(img, listOf(150, 210), cy = 45, beamY = 78, beamLayers = 1)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("向下连梁组也应切分为 2 个符头", 2, result.diagnostics.noteheadCount)
        assertEquals(2, result.score.notes.size)
        result.score.notes.forEach { assertEquals(250L, it.duration) }
    }

    @Test
    fun `pipeline splits a three-note beamed group`() {
        val img = blankScore()
        drawStaff(img)
        // 三个连梁八分音符
        drawBeamedGroup(img, listOf(120, 180, 240), cy = 65, beamY = 33, beamLayers = 1)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("三连梁组应切分为 3 个符头", 3, result.diagnostics.noteheadCount)
        assertEquals(3, result.score.notes.size)
        // 三个音依次间隔 250ms
        val sorted = result.score.notes.sortedBy { it.startTime }
        assertEquals(listOf(0L, 250L, 500L), sorted.map { it.startTime })
    }

    // ---- 休止符集成测试 -------------------------------------------------------

    @Test
    fun `beamed group detection leaves a nearby standalone quarter note intact`() {
        val img = blankScore()
        drawStaff(img)
        // 连梁组（2 个八分）+ 后面一个独立四分音符（带符干）
        drawBeamedGroup(img, listOf(110, 170), cy = 65, beamY = 33, beamLayers = 1)
        drawStemmedFilled(img, 320, 65)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("应识别出 3 个符头", 3, result.diagnostics.noteheadCount)
        assertEquals(3, result.score.notes.size)
        val sorted = result.score.notes.sortedBy { it.startTime }
        // 两个八分 (250ms) 后接一个四分 (500ms)
        assertEquals(250L, sorted[0].duration)
        assertEquals(250L, sorted[1].duration)
        assertEquals(500L, sorted[2].duration)
        assertEquals(0L, sorted[0].startTime)
        assertEquals(250L, sorted[1].startTime)
        assertEquals(500L, sorted[2].startTime) // 两个八分结束后
    }

    /**
     * 四分休止符：高而窄的锯齿形/闪电形墨迹（3 段交替方向的粗对角线）。
     * 高约 2.5 个谱线间距（25px），宽约 0.7 个谱线间距（7px）。
     */
    private fun drawQuarterRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = 25
        val halfW = 3
        val topY = cy - totalH / 2
        val segH = totalH / 3
        val thick = 2
        for (seg in 0 until 3) {
            val y0 = topY + seg * segH
            val y1 = y0 + segH
            val x0 = cx + if (seg % 2 == 0) halfW else -halfW
            val x1 = cx + if (seg % 2 == 0) -halfW else halfW
            val dx = x1 - x0
            val dy = y1 - y0
            val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)).coerceAtLeast(1)
            for (i in 0..steps) {
                val x = x0 + dx * i / steps
                val y = y0 + dy * i / steps
                for (ty in 0 until thick) for (tx in 0 until thick) {
                    val px = x + tx
                    val py = y + ty
                    if (px in 0 until width && py in 0 until height) img.set(px, py, true)
                }
            }
        }
    }

    @Test
    fun `pipeline advances cursor past a quarter rest between two notes`() {
        val img = blankScore()
        drawStaff(img)
        // 四分音符 → 四分休止符 → 四分音符
        drawEllipse(img, 100, 60)                 // 第一个四分音符
        drawQuarterRest(img, 220, 50)             // 四分休止符
        drawEllipse(img, 340, 60)                 // 第二个四分音符

        val result = OmrPipeline.recognize(img, tempo = 120)

        // 应产生 2 个音符（休止符不产生音符，只推进时间轴）
        assertEquals("休止符不应产生音符", 2, result.score.notes.size)
        val sorted = result.score.notes.sortedBy { it.startTime }
        // 第一个音：start 0ms，四分音符 500ms
        assertEquals(0L, sorted[0].startTime)
        assertEquals(500L, sorted[0].duration)
        // 休止符推进 500ms → 第二个音 start = 500 + 500 = 1000ms
        assertEquals("休止符后第二个音应在 1000ms 开始", 1000L, sorted[1].startTime)
        assertEquals(500L, sorted[1].duration)
    }

    @Test
    fun `pipeline reports rest detection warning`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 100, 60)
        drawQuarterRest(img, 220, 50)
        drawEllipse(img, 340, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertTrue(
            "应提示检测到休止符",
            result.warnings.any { it.contains("休止符") }
        )
    }

    @Test
    fun `pipeline handles two consecutive quarter rests`() {
        val img = blankScore()
        drawStaff(img)
        // 四分音符 → 四分休止符 → 四分休止符 → 四分音符
        drawEllipse(img, 80, 60)
        drawQuarterRest(img, 160, 50)
        drawQuarterRest(img, 240, 50)
        drawEllipse(img, 320, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(2, result.score.notes.size)
        val sorted = result.score.notes.sortedBy { it.startTime }
        assertEquals(0L, sorted[0].startTime)
        // 500ms（第一个音）+ 500ms（第一个休止）+ 500ms（第二个休止）= 1500ms
        assertEquals("两个四分休止符应推进 1000ms", 1500L, sorted[1].startTime)
    }

    // ---- 十六分 / 三十二分休止符端到端检测 -------------------------------------

    /**
     * 十六分休止符：竖线符干（1px）+ 2 个旗钩（各 3px 宽）。
     * 总高度 9px（~0.9 个谱线间距），blob 宽度仅 4px（< 符头最小宽度 5px），
     * 确保不会被 NoteheadDetector 误判为符头。
     * 位于五线谱中央间（y=41–49，不与任何谱线交叉），去谱线后不碎裂。
     * 2 个旗钩间隔 3 行纯符干，使 countFlags 统计出 2 组。
     */
    private fun drawSixteenthRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = 9
        val topY = cy - totalH / 2
        val stemW = 1
        val flagW = 3
        // 竖线符干
        for (y in topY until topY + totalH) {
            if (cx in 0 until width && y in 0 until height) img.set(cx, y, true)
        }
        // 2 个旗钩（间隔 >= 2 行纯符干）
        for (flagY in listOf(topY + 1, topY + 5)) {
            if (flagY in 0 until height) {
                for (x in cx + stemW until cx + stemW + flagW) {
                    if (x in 0 until width) img.set(x, flagY, true)
                }
            }
        }
    }

    /**
     * 三十二分休止符：竖线符干（1px）+ 3 个旗钩（各 3px 宽）。
     * blob 宽度仅 4px，避免被误判为符头。3 个旗钩间隔各 2 行纯符干。
     */
    private fun drawThirtySecondRest(img: BinaryImage, cx: Int, cy: Int) {
        val totalH = 9
        val topY = cy - totalH / 2
        val stemW = 1
        val flagW = 3
        // 竖线符干
        for (y in topY until topY + totalH) {
            if (cx in 0 until width && y in 0 until height) img.set(cx, y, true)
        }
        // 3 个旗钩（间隔各 >= 2 行纯符干）
        for (flagY in listOf(topY + 1, topY + 4, topY + 7)) {
            if (flagY in 0 until height) {
                for (x in cx + stemW until cx + stemW + flagW) {
                    if (x in 0 until width) img.set(x, flagY, true)
                }
            }
        }
    }

    @Test
    fun `pipeline advances cursor past a sixteenth rest between two notes`() {
        val img = blankScore()
        drawStaff(img)
        // 四分音符 → 十六分休止符 → 四分音符
        drawEllipse(img, 100, 60)
        drawSixteenthRest(img, 220, 45)   // 位于中央间，不与谱线交叉
        drawEllipse(img, 340, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        // 休止符不产生音符，只推进时间轴
        assertEquals("十六分休止符不应产生音符", 2, result.score.notes.size)
        val sorted = result.score.notes.sortedBy { it.startTime }
        // 第一个音：start 0ms，四分音符 500ms
        assertEquals(0L, sorted[0].startTime)
        assertEquals(500L, sorted[0].duration)
        // 500ms（第一个音）+ 125ms（十六分休止符）= 625ms
        assertEquals("十六分休止符后第二个音应在 625ms 开始", 625L, sorted[1].startTime)
        assertEquals(500L, sorted[1].duration)
        // 应提示检测到休止符
        assertTrue(
            "应提示检测到休止符",
            result.warnings.any { it.contains("休止符") }
        )
    }

    @Test
    fun `pipeline advances cursor past a thirty-second rest between two notes`() {
        val img = blankScore()
        drawStaff(img)
        // 四分音符 → 三十二分休止符 → 四分音符
        drawEllipse(img, 100, 60)
        drawThirtySecondRest(img, 220, 45)
        drawEllipse(img, 340, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("三十二分休止符不应产生音符", 2, result.score.notes.size)
        val sorted = result.score.notes.sortedBy { it.startTime }
        assertEquals(0L, sorted[0].startTime)
        assertEquals(500L, sorted[0].duration)
        // 500ms（第一个音）+ 62ms（三十二分休止符，0.125×500=62.5→62）= 562ms
        assertEquals("三十二分休止符后第二个音应在 562ms 开始", 562L, sorted[1].startTime)
        assertEquals(500L, sorted[1].duration)
    }

    // ── Deskew integration ────────────────────────────────────────────────────

    /** Larger canvas with 2px-thick staff lines for deskew robustness tests. */
    private val dw = 600
    private val dh = 200
    private val dLineYs = listOf(70, 90, 110, 130, 150) // spacing = 20

    private fun drawThickStaff(img: BinaryImage) {
        for (y in dLineYs) {
            for (x in 0 until img.width) {
                img.set(x, y, true)
                if (y + 1 < img.height) img.set(x, y + 1, true) // 2px thick
            }
        }
    }

    private fun drawBigEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 7, ry: Int = 5) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until img.width || y !in 0 until img.height) continue
                val ndx = (x - cx).toDouble() / rx
                val ndy = (y - cy).toDouble() / ry
                if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
            }
        }
    }

    @Test
    fun `pipeline auto-deskews a tilted score and detects notes`() {
        // 1) Draw a horizontal score: thick staff + 3 noteheads.
        val img = BinaryImage.blank(dw, dh)
        drawThickStaff(img)
        drawBigEllipse(img, 120, 130)  // on the 4th line → lower pitch
        drawBigEllipse(img, 260, 110)  // on the 3rd line (center)
        drawBigEllipse(img, 400, 90)   // on the 2nd line → higher pitch

        // 2) Tilt by 3° to simulate a handheld photo.
        val tilted = Deskewer.rotate(img, 3.0)

        // 3) Without deskew the tilt would break staff detection; the pipeline
        //    auto-deskews internally, so recognition should still succeed.
        val result = OmrPipeline.recognize(tilted, tempo = 120)

        assertTrue(
            "tilted score should still be recognised after auto-deskew (got ${result.score.notes.size} notes, warnings: ${result.warnings})",
            result.score.notes.isNotEmpty()
        )
    }

    @Test
    fun `pipeline reports deskew correction in warnings when tilt is significant`() {
        val img = BinaryImage.blank(dw, dh)
        drawThickStaff(img)
        drawBigEllipse(img, 300, 110)

        val tilted = Deskewer.rotate(img, 5.0)

        val result = OmrPipeline.recognize(tilted, tempo = 120)

        assertTrue(
            "should warn about deskew correction, warnings: ${result.warnings}",
            result.warnings.any { it.contains("倾斜") && it.contains("校正") }
        )
    }

    @Test
    fun `pipeline does not report deskew for an already-horizontal score`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 200, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertFalse(
            "horizontal score should not trigger deskew warning, warnings: ${result.warnings}",
            result.warnings.any { it.contains("倾斜") }
        )
    }

    // ── Denoise integration ────────────────────────────────────────────────────

    /** Scatter isolated black specks (pepper noise) across the image. */
    private fun addPepperNoise(img: BinaryImage, count: Int, seed: Long = 42L) {
        var s = seed
        var placed = 0
        while (placed < count) {
            // simple LCG pseudo-random
            s = (s * 6364136223846793005L + 1442695040888963407L)
            val x = ((s ushr 16).toInt() and 0x7fffffff) % img.width
            s = (s * 6364136223846793005L + 1442695040888963407L)
            val y = ((s ushr 16).toInt() and 0x7fffffff) % img.height
            if (!img.isBlack(x, y)) {
                img.set(x, y, true)
                placed++
            }
        }
    }

    @Test
    fun `pipeline recognises a score corrupted by pepper noise`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 100, 60) // G4-ish
        drawEllipse(img, 250, 60)
        drawEllipse(img, 380, 60)
        // Add many isolated specks that would otherwise become spurious blobs.
        addPepperNoise(img, count = 200)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertTrue(
            "noisy score should still be recognised (got ${result.score.notes.size} notes, warnings: ${result.warnings})",
            result.score.notes.isNotEmpty()
        )
    }

    @Test
    fun `pipeline reports a denoise warning when noise is cleaned`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 200, 60)
        addPepperNoise(img, count = 150)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertTrue(
            "should warn that noise was cleaned, warnings: ${result.warnings}",
            result.warnings.any { it.contains("噪点") && it.contains("降噪") }
        )
    }

    @Test
    fun `pipeline does not report denoise warning on a clean score`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 200, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertFalse(
            "clean score should not trigger denoise warning, warnings: ${result.warnings}",
            result.warnings.any { it.contains("噪点") }
        )
    }

    // ── Keystone (perspective) integration ────────────────────────────────────

    /**
     * Draw the 5 staff lines warped by a yaw-perspective convergence model:
     * at column x each original line y is remapped to
     *   y' = yc + (y - yc) * (1 + k * (x/w - 0.5))
     * k > 0 ⇒ the system is taller on the right (lines fan apart toward the
     * right). This is an *independent* synthesis of the distortion (not the
     * corrector's own warp), used to verify detection + correction end-to-end.
     */
    private fun drawWarpedStaff(img: BinaryImage, k: Double, yc: Double) {
        val w = img.width
        for (baseY in lineYs) {
            for (x in 0 until w) {
                val scale = 1.0 + k * (x.toDouble() / w - 0.5)
                val y = (yc + (baseY - yc) * scale).toInt()
                if (y in 0 until img.height) img.set(x, y, true)
            }
        }
    }

    /** Draw a filled ellipse warped by the same convergence model as the staff. */
    private fun drawWarpedEllipse(img: BinaryImage, cx: Int, cy: Int, rx: Int, ry: Int, k: Double, yc: Double) {
        val w = img.width
        for (dy in -ry..ry) {
            for (dx in -rx..rx) {
                if ((dx.toDouble() / rx).let { it * it } +
                    (dy.toDouble() / ry).let { it * it } > 1.01
                ) continue
                val sx = cx + dx
                if (sx !in 0 until w) continue
                val sy = cy + dy
                val scale = 1.0 + k * (sx.toDouble() / w - 0.5)
                val wy = (yc + (sy - yc) * scale).toInt()
                if (wy in 0 until img.height) img.set(sx, wy, true)
            }
        }
    }

    @Test
    fun `pipeline recognises a perspective-warped score after keystone correction`() {
        // k = 0.20 ⇒ ~20% system-height change across the width (moderate yaw).
        val k = 0.20
        val yc = 50.0
        val img = blankScore()
        drawWarpedStaff(img, k, yc)
        // Noteheads sit on the staff positions, warped the same way.
        drawWarpedEllipse(img, 90, 60, 4, 3, k, yc)   // 4th line region
        drawWarpedEllipse(img, 210, 55, 4, 3, k, yc)  // upper-space region
        drawWarpedEllipse(img, 330, 50, 4, 3, k, yc)  // middle line

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertTrue(
            "warped score should still be recognised after keystone correction " +
                "(got ${result.score.notes.size} notes, warnings: ${result.warnings})",
            result.score.notes.isNotEmpty()
        )
    }

    @Test
    fun `pipeline reports a keystone warning when perspective is corrected`() {
        val k = 0.25
        val yc = 50.0
        val img = blankScore()
        drawWarpedStaff(img, k, yc)
        drawWarpedEllipse(img, 200, 60, 4, 3, k, yc)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertTrue(
            "should warn that perspective was corrected, warnings: ${result.warnings}",
            result.warnings.any { it.contains("透视") && it.contains("校正") }
        )
    }

    @Test
    fun `pipeline does not report keystone warning on a fronto-parallel score`() {
        val img = blankScore()
        drawStaff(img)
        drawEllipse(img, 200, 60)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertFalse(
            "fronto-parallel score should not trigger keystone warning, warnings: ${result.warnings}",
            result.warnings.any { it.contains("透视") }
        )
    }

    // ---- 多系统页面时间轴排序（multi-system timeline ordering）----------------
    // 真实乐谱一页通常有多个谱表系统（staff systems）上下排列；音乐从上到下、
    // 每个系统内从左到右流动。此前时间轴仅按 x 排序，会把下方系统最左侧的音符
    // （小 x）插到上方系统最右侧音符（大 x）之前，完全打乱顺序。

    /** 在指定图像的 y 坐标列表处绘制一组 5 条谱线（使用图像自身宽高）。 */
    private fun drawStaffAt(img: BinaryImage, ys: List<Int>) {
        for (y in ys) for (x in 0 until img.width) img.set(x, y, true)
    }

    /** 在指定图像上绘制实心椭圆符头（使用图像自身宽高做边界检查）。 */
    private fun drawEllipseOn(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until img.width || y !in 0 until img.height) continue
                val ndx = (x - cx).toDouble() / rx
                val ndy = (y - cy).toDouble() / ry
                if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
            }
        }
    }

    @Test
    fun `multi-system page sequences notes top-to-bottom not interleaved by x`() {
        // 两个系统上下排列。系统 1（上方，y=20..60），系统 2（下方，y=100..140）。
        val w = 420
        val h = 180
        val img = BinaryImage.blank(w, h)
        drawStaffAt(img, listOf(20, 30, 40, 50, 60))   // 系统 1
        drawStaffAt(img, listOf(100, 110, 120, 130, 140)) // 系统 2

        // 系统 1：在最右侧放一个符头（大 x=360），底线 y=60。
        // 无谱号 + 多系统 → 上方系统回退高音谱表，底线 E4 = MIDI 64。
        drawEllipseOn(img, 360, 60)
        // 系统 2：在最左侧放一个符头（小 x=60），底线 y=140。
        // 无谱号 + 多系统 → 下方系统回退低音谱表，底线 G2 = MIDI 43。
        drawEllipseOn(img, 60, 140)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("should detect 2 systems", 2, result.diagnostics.systemCount)
        assertEquals("should find 2 notes", 2, result.score.notes.size)
        // 关键断言：系统 1 的音符（x=360）必须先于系统 2 的音符（x=60）。
        // 正确顺序：[64@0ms, 43@500ms]。
        // BUG 行为（仅按 x 排序）：[43@0ms, 64@500ms]（x=60 排到 x=360 之前）。
        assertEquals(
            "system 1 note (x=360) must precede system 2 note (x=60)",
            listOf(64, 43),
            result.score.notes.map { it.midiNumber }
        )
        assertEquals(listOf(0L, 500L), result.score.notes.map { it.startTime })
    }

    @Test
    fun `multi-system page preserves full sequence across systems with overlapping x`() {
        // 系统 1 有 3 个音符（x=60/200/350），系统 2 有 2 个音符（x=100/300）。
        // 两系统的 x 范围重叠，按 x 排序会完全交错，必须按 (system, x) 排序。
        val w = 420
        val h = 180
        val img = BinaryImage.blank(w, h)
        drawStaffAt(img, listOf(20, 30, 40, 50, 60))
        drawStaffAt(img, listOf(100, 110, 120, 130, 140))

        // 系统 1（高音谱表回退）：底线 E4=64, 自下而上 E4,F4,G4...
        // 放在底线 y=60 → E4=64；间 y=55 → F4=65；第二线 y=50 → G4=67
        drawEllipseOn(img, 60, 60)   // MIDI 64
        drawEllipseOn(img, 200, 55)  // MIDI 65
        drawEllipseOn(img, 350, 50)  // MIDI 67
        // 系统 2（低音谱表回退）：底线 G2=43, 间 A2=45, 第二线 B2=47
        drawEllipseOn(img, 100, 140) // MIDI 43
        drawEllipseOn(img, 300, 135) // MIDI 45

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(2, result.diagnostics.systemCount)
        assertEquals(5, result.score.notes.size)
        // 正确顺序：sys1[64,65,67] 然后 sys2[43,45]。
        // BUG 顺序（按 x）：64(x60), 43(x100), 65(x200), 45(x300), 67(x350)。
        assertEquals(
            "notes must follow system order then x order",
            listOf(64, 65, 67, 43, 45),
            result.score.notes.map { it.midiNumber }
        )
        // 5 个连续四分音符 @120BPM = 500ms each。
        assertEquals(
            listOf(0L, 500L, 1000L, 1500L, 2000L),
            result.score.notes.map { it.startTime }
        )
    }

    @Test
    fun `multi-system page carries time cursor from system 1 into system 2`() {
        // 验证时间游标从系统 1 连续传递到系统 2：系统 2 第一个音符的 startTime
        // 必须紧跟系统 1 最后一个音符结束之后（而非从 0 重新开始）。
        val w = 420
        val h = 180
        val img = BinaryImage.blank(w, h)
        drawStaffAt(img, listOf(20, 30, 40, 50, 60))
        drawStaffAt(img, listOf(100, 110, 120, 130, 140))

        // 系统 1：两个四分音符（高音回退）
        drawEllipseOn(img, 100, 60)  // E4=64 @0ms
        drawEllipseOn(img, 250, 55)  // F4=65 @500ms
        // 系统 2：一个四分音符（低音回退），必须 @1000ms（前两个结束时）
        drawEllipseOn(img, 80, 140)  // G2=43 @1000ms

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals(3, result.score.notes.size)
        val sys2Note = result.score.notes.first { it.midiNumber == 43 }
        assertEquals(
            "system 2 first note must start at 1000ms (after system 1's two notes), " +
                "not at 0ms or 500ms",
            1000L,
            sys2Note.startTime
        )
    }

    @Test
    fun `multi-system page orders three systems top-to-bottom`() {
        // 验证排序修复对 3 个系统同样生效（真实乐谱一页常有 3-6 行谱表）。
        val w = 420
        val h = 260
        val img = BinaryImage.blank(w, h)
        drawStaffAt(img, listOf(20, 30, 40, 50, 60))    // 系统 1
        drawStaffAt(img, listOf(100, 110, 120, 130, 140)) // 系统 2
        drawStaffAt(img, listOf(180, 190, 200, 210, 220)) // 系统 3

        // 每个系统各放一个音符，x 全部相同（100），确保排序依据是系统索引而非 x。
        // 系统 1 底线 y=60 → 高音回退 E4=64
        drawEllipseOn(img, 100, 60)
        // 系统 2 底线 y=140 → 低音回退 G2=43
        drawEllipseOn(img, 100, 140)
        // 系统 3 底线 y=220 → 低音回退 G2=43
        drawEllipseOn(img, 100, 220)

        val result = OmrPipeline.recognize(img, tempo = 120)

        assertEquals("should detect 3 systems", 3, result.diagnostics.systemCount)
        assertEquals(3, result.score.notes.size)
        // 正确顺序：系统 1 → 系统 2 → 系统 3，各间隔 500ms（四分音符）。
        assertEquals(
            "three notes at same x must be ordered by system, not interleaved",
            listOf(0L, 500L, 1000L),
            result.score.notes.map { it.startTime }
        )
    }
}
