package com.pianocompanion.chordreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordReadingAudioBuilder] 单元测试。
 *
 * 验证柱式和弦音频渲染：
 * - 空输入返回空数组
 * - 输出长度包含前导/尾部静音
 * - 输出值在 [-1.0, 1.0] 范围内（软限幅）
 * - 多音符叠加后非全零
 */
class ChordReadingAudioBuilderTest {

    private val builder = ChordReadingAudioBuilder()

    @Test
    fun `empty midi list returns empty array`() {
        val out = builder.renderChord(emptyList())
        assertEquals(0, out.size)
    }

    @Test
    fun `output length includes lead and tail silence`() {
        val out = builder.renderChord(listOf(60, 64, 67))
        // 至少应包含前导静音 + 音符 + 尾部静音
        val minLen = ChordReadingAudioBuilder.SILENCE_LEAD_SAMPLES +
            ChordReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        assertTrue("输出长度 ${out.size} 应大于最小静音长度 $minLen", out.size > minLen)
    }

    @Test
    fun `output values are within negative one to one`() {
        val out = builder.renderChord(listOf(60, 64, 67, 71))
        for ((i, v) in out.withIndex()) {
            assertTrue(
                "采样[$i]=$v 超出 [-1.0, 1.0] 范围",
                v >= -1.0f && v <= 1.0f
            )
        }
    }

    @Test
    fun `lead silence region is all zeros`() {
        val out = builder.renderChord(listOf(60, 64, 67))
        val leadEnd = ChordReadingAudioBuilder.SILENCE_LEAD_SAMPLES
        for (i in 0 until leadEnd) {
            assertEquals("前导静音区[$i] 应为 0", 0.0f, out[i], 1e-6f)
        }
    }

    @Test
    fun `tail silence region is all zeros`() {
        val out = builder.renderChord(listOf(60, 64, 67))
        val tailStart = out.size - ChordReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        for (i in tailStart until out.size) {
            assertEquals("尾部静音区[$i] 应为 0", 0.0f, out[i], 1e-6f)
        }
    }

    @Test
    fun `chord region has non-zero samples`() {
        val out = builder.renderChord(listOf(60, 64, 67))
        val chordStart = ChordReadingAudioBuilder.SILENCE_LEAD_SAMPLES
        val chordEnd = out.size - ChordReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        var nonZero = 0
        for (i in chordStart until chordEnd) {
            if (out[i] != 0.0f) nonZero++
        }
        assertTrue("和弦区应有非零采样，实际非零数=$nonZero", nonZero > 100)
    }

    @Test
    fun `sample rate matches synthesizer default`() {
        assertEquals(44100, ChordReadingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `estimate duration is positive`() {
        val ms = builder.estimateDurationMs()
        assertTrue("预估时长应大于 0，实际 $ms", ms > 0)
    }

    @Test
    fun `render from question produces valid audio`() {
        val engine = ChordReadingEngine.withSeed(7L)
        val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.INTERMEDIATE)
        val out = builder.render(q)
        assertTrue(out.size > 1000)
        // 验证范围
        for (v in out) {
            assertTrue(v >= -1.0f && v <= 1.0f)
        }
    }

    @Test
    fun `seventh chord renders without clipping`() {
        val engine = ChordReadingEngine.withSeed(3L)
        val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.ADVANCED)
        val out = builder.render(q)
        assertEquals(4, q.noteMidis.size)
        for (v in out) {
            assertTrue("七和弦渲染后值 $v 超范围", v >= -1.0f && v <= 1.0f)
        }
    }
}
