package com.pianocompanion.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KeyDetector 单元测试 — Krumhansl-Schmuckler 调性判定算法。
 *
 * 使用已知调性的 MIDI 旋律（音阶、音程序列）验证检测准确性。
 */
class KeyDetectorTest {

    // === 音级类常量 ===
    private val C = 0; private val Cs = 1; private val D = 2; private val Ds = 3
    private val E = 4; private val F = 5; private val Fs = 6; private val G = 7
    private val Gs = 8; private val A = 9; private val As = 10; private val B = 11

    /** C 大调音阶（一个八度，从 C4=60 开始）。 */
    private val cMajorScale = listOf(60, 62, 64, 65, 67, 69, 71, 72)

    /** G 大调音阶（G4=67 开始，含 F#5=78）。 */
    private val gMajorScale = listOf(67, 69, 71, 72, 74, 66, 67)

    /** F 大调音阶（F4=65 开始，含 Bb4=70）。 */
    private val fMajorScale = listOf(65, 67, 69, 70, 72, 74, 76, 77)

    /** D 大调音阶（D4=62 开始，含 F#=66, C#=73）。 */
    private val dMajorScale = listOf(62, 64, 66, 67, 69, 71, 73, 74)

    /** a 小调音阶（A3=57 开始）。 */
    private val aMinorScale = listOf(57, 59, 60, 62, 64, 65, 67, 69)

    // =========================
    // 基础检测
    // =========================

    @Test
    fun `空音符列表返回 C 大调 置信度0`() {
        val result = KeyDetector.detectFromMidiNumbers(emptyList())
        assertEquals(0, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `全无效音符回退 C 大调`() {
        val result = KeyDetector.detectFromMidiNumbers(listOf(-1, -5, 200, 300))
        assertEquals(0, result.tonic)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `C 大调音阶检测为 C 大调`() {
        val result = KeyDetector.detectFromMidiNumbers(cMajorScale)
        assertEquals(C, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `G 大调音阶检测为 G 大调`() {
        val result = KeyDetector.detectFromMidiNumbers(gMajorScale)
        assertEquals(G, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `F 大调音阶检测为 F 大调`() {
        val result = KeyDetector.detectFromMidiNumbers(fMajorScale)
        assertEquals(F, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `D 大调音阶检测为 D 大调`() {
        val result = KeyDetector.detectFromMidiNumbers(dMajorScale)
        assertEquals(D, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `a 小调音阶检测为 a 小调`() {
        val result = KeyDetector.detectFromMidiNumbers(aMinorScale)
        assertEquals(A, result.tonic)
        assertEquals(KeyMode.MINOR, result.mode)
    }

    // =========================
    // 多八度 / 鲁棒性
    // =========================

    @Test
    fun `跨八度 C 大调音阶仍正确检测`() {
        val notes = listOf(48, 50, 52, 53, 55, 57, 59, 60, 62, 64, 65, 67, 69, 71, 72)
        val result = KeyDetector.detectFromMidiNumbers(notes)
        assertEquals(C, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `主音重复加权不改变检测结果`() {
        // C 大调音阶 + 强化主音 C，应该仍为 C 大调
        val notes = cMajorScale + listOf(60, 60)
        val result = KeyDetector.detectFromMidiNumbers(notes)
        assertEquals(C, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `大三和弦检测为大调`() {
        // C-E-G 三和弦 → C 大调
        val result = KeyDetector.detectFromMidiNumbers(listOf(60, 64, 67, 60, 64, 67))
        assertEquals(C, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `小三和弦检测为小调`() {
        // A-C-E 三和弦 → a 小调
        val result = KeyDetector.detectFromMidiNumbers(listOf(57, 60, 64, 57, 60, 64))
        assertEquals(A, result.tonic)
        assertEquals(KeyMode.MINOR, result.mode)
    }

    // =========================
    // 各调测试
    // =========================

    @Test
    fun `E 大调音阶检测为 E 大调`() {
        val eMajor = listOf(64, 66, 68, 69, 71, 73, 75, 76) // E F# G# A B C# D# E
        val result = KeyDetector.detectFromMidiNumbers(eMajor)
        assertEquals(E, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `Bb 大调音阶检测为 Bb 大调`() {
        // Bb C D Eb F G A Bb
        val bbMajor = listOf(70, 72, 74, 75, 77, 79, 81, 82)
        val result = KeyDetector.detectFromMidiNumbers(bbMajor)
        assertEquals(As, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `e 小调音阶检测为 e 小调`() {
        // E F# G A B C D E
        val eMinor = listOf(64, 66, 67, 69, 71, 72, 74, 76)
        val result = KeyDetector.detectFromMidiNumbers(eMinor)
        assertEquals(E, result.tonic)
        assertEquals(KeyMode.MINOR, result.mode)
    }

    // =========================
    // 置信度
    // =========================

    @Test
    fun `明确调性置信度较高`() {
        val result = KeyDetector.detectFromMidiNumbers(cMajorScale)
        assertTrue("C 大调置信度应 > 0.5, 实际 ${result.confidence}", result.confidence > 0.5f)
    }

    @Test
    fun `单音符置信度较低`() {
        // 只有 C，调性模糊
        val result = KeyDetector.detectFromMidiNumbers(listOf(60))
        assertTrue("单音符置信度应较低, 实际 ${result.confidence}", result.confidence < 0.7f)
    }

    // =========================
    // 直方图接口
    // =========================

    @Test(expected = IllegalArgumentException::class)
    fun `直方图维度错误抛异常`() {
        KeyDetector.detectFromHistogram(DoubleArray(11))
    }

    @Test
    fun `从直方图检测 C 大调`() {
        // C 大调轮廓：C D E F G A B 各 1 次，其他 0
        val hist = DoubleArray(12)
        hist[0] = 1.0; hist[2] = 1.0; hist[4] = 1.0
        hist[5] = 1.0; hist[7] = 1.0; hist[9] = 1.0; hist[11] = 1.0
        val result = KeyDetector.detectFromHistogram(hist)
        assertEquals(C, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    @Test
    fun `全零直方图返回 C 大调`() {
        val result = KeyDetector.detectFromHistogram(DoubleArray(12))
        // 皮尔逊相关在零向量上为 0，bestScore 保持 -∞ 初始化后的首个候选 C 大调
        assertEquals(C, result.tonic)
        assertEquals(KeyMode.MAJOR, result.mode)
    }

    // =========================
    // KeyInfo displayName
    // =========================

    @Test
    fun `displayName 大调格式正确`() {
        val key = KeyInfo(0, KeyMode.MAJOR, 1f)
        assertEquals("C大调", key.displayName)
    }

    @Test
    fun `displayName 小调用小写字母`() {
        val key = KeyInfo(9, KeyMode.MINOR, 1f)
        assertEquals("a小调", key.displayName)
    }

    @Test
    fun `displayName 升号调正确`() {
        val key = KeyInfo(6, KeyMode.MAJOR, 1f)
        assertEquals("F#大调", key.displayName)
    }
}
