package com.pianocompanion.omr.image

import com.pianocompanion.omr.image.SignatureDetector.ClefType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SignatureDetector 单元测试：用像素级合成的"签名区"（谱号 + 调号 + 拍号）端到端
 * 验证识别链路，无需真实照片或 Android 设备。
 *
 * 几何：宽 520 × 高 180；五条谱线 y=50,60,70,80,90（间距 s=10）。
 */
class SignatureDetectorTest {

    private val width = 520
    private val height = 180
    private val lineYs = listOf(50, 60, 70, 80, 90)
    private val s = 10
    private val noteX = 420 // 第一个音符 x，签名区在其左侧

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

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

    // ---- 签名区字形绘制 -----------------------------------------------------

    /** 高音谱号：自顶线上方 2.5 个间距延伸至底线，4px 宽实心列（明显向上超出）。 */
    private fun drawTrebleClef(img: BinaryImage, x: Int) {
        val top = lineYs.first() - (s * 5 / 2) // 50 - 25 = 25
        val bottom = lineYs.last()             // 90
        for (y in top..bottom) for (dx in 0..3) img.set(x + dx, y, true)
    }

    /** 低音谱号：谱表内的紧凑曲线 + 右侧两点（F 线上下）。 */
    private fun drawBassClef(img: BinaryImage, x: Int) {
        val top = lineYs.first() + s / 2       // 55
        val bottom = lineYs.last() - s / 2     // 85
        for (y in top..bottom) for (dx in 0..4) img.set(x + dx, y, true)
        val fLineY = lineYs[1]                 // 第 2 条线 = 60
        fillSquare(img, x + 7, fLineY - s / 2, 3) // 上点
        fillSquare(img, x + 7, fLineY + s / 2, 3) // 下点
    }

    private fun fillSquare(img: BinaryImage, x0: Int, y0: Int, size: Int) {
        for (y in y0 until y0 + size) for (x in x0 until x0 + size) {
            if (x in 0 until width && y in 0 until height) img.set(x, y, true)
        }
    }

    /** 升号：两根竖直笔画 + 两根横杠（融合成一个连通块），高度约 2 个间距。 */
    private fun drawSharp(img: BinaryImage, x: Int) {
        val yTop = lineYs.first() + s  // 60
        val yBot = lineYs.last() - s   // 80
        for (y in yTop..yBot) { img.set(x, y, true); img.set(x + 6, y, true) }
        for (y in listOf(yTop + s / 2, yBot - s / 2)) {
            for (dx in 0..7) img.set(x + dx, y, true)
        }
    }

    /** 降号：一根竖直笔画 + 底部圆腹（一个连通块，仅 1 根长竖笔），高度约 2 个间距。 */
    private fun drawFlat(img: BinaryImage, x: Int) {
        val yTop = lineYs.first() + s  // 60
        val yBot = lineYs.last() - s   // 80
        for (y in yTop..yBot) img.set(x, y, true)
        fillSquare(img, x + 1, yBot - s / 2, 5) // 圆腹
    }

    /** 把某个数字的 5×7 模板按 [scale] 倍放大画入图像。 */
    private fun renderDigit(img: BinaryImage, digit: Int, x0: Int, y0: Int, scale: Int) {
        val tmpl = SignatureDetector.DIGIT_TEMPLATES[digit] ?: error("no template for $digit")
        for (r in 0 until SignatureDetector.GRID_H) {
            for (c in 0 until SignatureDetector.GRID_W) {
                if (!tmpl[r * SignatureDetector.GRID_W + c]) continue
                for (dy in 0 until scale) for (dx in 0 until scale) {
                    val x = x0 + c * scale + dx
                    val y = y0 + r * scale + dy
                    if (x in 0 until width && y in 0 until height) img.set(x, y, true)
                }
            }
        }
    }

    // ---- 运行检测的辅助 -----------------------------------------------------

    private fun recognizeSignatures(img: BinaryImage): List<SignatureDetector.SystemSignatures> {
        val systems = StaffLineDetector.detect(img)
        val minLineRun = (width * 0.5).toInt().coerceAtLeast(s * 4)
        val cleaned = StaffLineRemover.remove(img, minLineRun, maxLineThickness = 3)
        val blobs = ConnectedComponents.label(cleaned, minPixels = 4)
        val noteheadsBySystem = systems.map { sys ->
            listOf(Notehead(noteX, sys.centerY, 8, 8, 64))
        }
        return SignatureDetector.detect(cleaned, systems, blobs, noteheadsBySystem).perSystem
    }

    // ---- 谱号识别测试 -------------------------------------------------------

    @Test
    fun `treble clef is recognized via upward reach`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.TREBLE, result[0].clef)
    }

    @Test
    fun `bass clef is recognized via two dots`() {
        val img = blank(); drawStaff(img); drawBassClef(img, 15); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.BASS, result[0].clef)
    }

    @Test
    fun `absence of clef yields UNKNOWN`() {
        // 只有谱表 + 音符，没有谱号。
        val img = blank(); drawStaff(img); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.UNKNOWN, result[0].clef)
    }

    // ---- 调号识别测试 -------------------------------------------------------

    @Test
    fun `two sharps yield D major key signature`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        drawSharp(img, 55); drawSharp(img, 70); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(KeySignature.D_MAJOR, result[0].keySignature)
    }

    @Test
    fun `single sharp yields G major`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        drawSharp(img, 55); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(KeySignature.G_MAJOR_E_MINOR, result[0].keySignature)
    }

    @Test
    fun `two flats yield bB major key signature`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        drawFlat(img, 55); drawFlat(img, 70); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(KeySignature.B_FLAT_MAJOR, result[0].keySignature)
    }

    @Test
    fun `no accidentals yields C major`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(KeySignature.C_MAJOR_A_MINOR, result[0].keySignature)
    }

    // ---- 拍号识别测试 -------------------------------------------------------

    @Test
    fun `stacked 4 over 4 recognized as four-four`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        renderDigit(img, 4, x0 = 80, y0 = 36, scale = 5) // 上 4 (y36..70, 中心53)
        renderDigit(img, 4, x0 = 80, y0 = 78, scale = 5) // 下 4 (y78..112, 中心95)
        ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(TimeSignature(4, 4), result[0].timeSignature)
    }

    @Test
    fun `stacked 3 over 4 recognized as three-four`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        renderDigit(img, 3, x0 = 80, y0 = 36, scale = 5)
        renderDigit(img, 4, x0 = 80, y0 = 78, scale = 5)
        ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(TimeSignature(3, 4), result[0].timeSignature)
    }

    @Test
    fun `stacked 6 over 8 recognized as six-eight`() {
        val img = blank(); drawStaff(img); drawTrebleClef(img, 15)
        renderDigit(img, 6, x0 = 80, y0 = 36, scale = 5)
        renderDigit(img, 8, x0 = 80, y0 = 78, scale = 5)
        ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(TimeSignature(6, 8), result[0].timeSignature)
    }

    // ---- 数字识别器直接测试（无谱表，验证模板往返）--------------------------

    @Test
    fun `every digit template round-trips through the classifier`() {
        for (d in 0..9) {
            val img = BinaryImage.blank(40, 50)
            renderDigit(img, d, x0 = 5, y0 = 5, scale = 5)
            val blob = ConnectedComponents.label(img, minPixels = 4).first()
            assertEquals("digit $d 应被识别", d, SignatureDetector.classifyDigit(img, blob))
        }
    }

    @Test
    fun `non-digit blob returns null`() {
        // 一个实心方块不像任何数字模板的轮廓特征。
        val img = BinaryImage.blank(40, 50)
        fillSquare(img, 5, 5, 20)
        val blob = ConnectedComponents.label(img, minPixels = 4).first()
        assertNull(SignatureDetector.classifyDigit(img, blob))
    }

    // ---- 竖直笔画计数（升/降判定核心）测试 ----------------------------------

    @Test
    fun `sharp glyph has two vertical strokes, flat has one`() {
        val bigSharp = BinaryImage.blank(40, 100)
        drawStaffStyleAgnosticSharp(bigSharp, 10)
        val sharpBlob = ConnectedComponents.label(bigSharp, minPixels = 4).first()
        assertEquals(2, SignatureDetector.countVerticalStrokes(bigSharp, sharpBlob))

        val bigFlat = BinaryImage.blank(40, 100)
        drawStaffStyleAgnosticFlat(bigFlat, 10)
        val flatBlob = ConnectedComponents.label(bigFlat, minPixels = 4).first()
        assertEquals(1, SignatureDetector.countVerticalStrokes(bigFlat, flatBlob))
    }

    private fun drawStaffStyleAgnosticSharp(img: BinaryImage, x: Int) {
        for (y in 20..80) { img.set(x, y, true); img.set(x + 6, y, true) }
        for (y in listOf(35, 65)) for (dx in 0..7) img.set(x + dx, y, true)
    }

    private fun drawStaffStyleAgnosticFlat(img: BinaryImage, x: Int) {
        for (y in 20..80) img.set(x, y, true)
        fillSquare(img, x + 1, 65, 8) // 圆腹
    }

    // ---- C 谱号 (中音 / 次中音) 识别测试 ------------------------------------

    /**
     * C 谱号：竖直对称的紧凑字形，框住指定谱线 [centerLineY]——
     * 一条贯穿的主竖直柱体 + 中心线两侧的横向托记号。竖直对称保证黑像质心
     * 恰好落在 [centerLineY]，从而被 classifyCClef 正确归类。
     */
    private fun drawCClef(img: BinaryImage, x: Int, centerLineY: Int) {
        val half = s * 3 / 2
        val top = centerLineY - half
        val bot = centerLineY + half
        // 主竖直柱体（关于 centerLineY 对称）
        for (y in top..bot) for (dx in 0..3) img.set(x + dx, y, true)
        // 中心线处的横向托记号（关于 centerLineY 对称，成对出现在 ±1）
        for (dx in 0..9) {
            img.set(x + dx, centerLineY - 1, true)
            img.set(x + dx, centerLineY + 1, true)
        }
    }

    /** 中音谱号：C 谱号框住中央线（自上而下第 3 条线）。 */
    private fun drawAltoClef(img: BinaryImage, x: Int) = drawCClef(img, x, lineYs[2])

    /** 次中音谱号：C 谱号框住自上而下第 2 条线。 */
    private fun drawTenorClef(img: BinaryImage, x: Int) = drawCClef(img, x, lineYs[1])

    @Test
    fun `alto clef is recognized via center-line bracket`() {
        val img = blank(); drawStaff(img); drawAltoClef(img, 15); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.ALTO, result[0].clef)
    }

    @Test
    fun `tenor clef is recognized via second-line bracket`() {
        val img = blank(); drawStaff(img); drawTenorClef(img, 15); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.TENOR, result[0].clef)
    }

    @Test
    fun `alto clef does not interfere with bass clef detection`() {
        // 回归保护：带双点的低音谱号仍应被识别为 BASS，不会被误判为 C 谱号。
        val img = blank(); drawStaff(img); drawBassClef(img, 15); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.BASS, result[0].clef)
    }

    @Test
    fun `alto clef with key signature still detects clef and key`() {
        val img = blank(); drawStaff(img); drawAltoClef(img, 15)
        drawSharp(img, 55); drawSharp(img, 70); ellipse(img, noteX, 70)
        val result = recognizeSignatures(img)
        assertEquals(ClefType.ALTO, result[0].clef)
        assertEquals(KeySignature.D_MAJOR, result[0].keySignature)
    }

    @Test
    fun `alto and tenor clefs produce distinct results`() {
        val altoImg = blank(); drawStaff(altoImg); drawAltoClef(altoImg, 15); ellipse(altoImg, noteX, 70)
        val tenorImg = blank(); drawStaff(tenorImg); drawTenorClef(tenorImg, 15); ellipse(tenorImg, noteX, 70)
        assertEquals(ClefType.ALTO, recognizeSignatures(altoImg)[0].clef)
        assertEquals(ClefType.TENOR, recognizeSignatures(tenorImg)[0].clef)
    }
}
