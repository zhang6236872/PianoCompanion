package com.pianocompanion.pitchtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绝对音高训练音频构建器单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 验证渲染长度、采样率、不削波、不同音符差异、前导/尾部静音。
 */
class PitchTrainingAudioBuilderTest {

    private val builder = PitchTrainingAudioBuilder()

    // ── 基本渲染 ──────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer for a note`() {
        val q = PitchQuestion(
            pitchClass = PitchClass.C,
            midiNote = 60,
            difficulty = PitchTrainingDifficulty.BEGINNER,
            options = PitchClass.WHITE_KEYS
        )
        val buffer = builder.render(q)
        assertTrue("单音渲染应非空", buffer.isNotEmpty())
    }

    @Test
    fun `renderNote produces non-empty buffer`() {
        val buffer = builder.renderNote(60)
        assertTrue("单音渲染应非空", buffer.isNotEmpty())
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, PitchTrainingAudioBuilder.SAMPLE_RATE)
    }

    // ── 长度 ──────────────────────────────────────────────

    @Test
    fun `buffer length matches expected duration`() {
        val buffer = builder.renderNote(60)
        val expectedMs = PitchTrainingAudioBuilder.LEAD_SILENCE_MS +
            PitchTrainingAudioBuilder.NOTE_DURATION_MS +
            PitchTrainingAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (PitchTrainingAudioBuilder.SAMPLE_RATE * expectedMs / 1000.0).toInt()
        // 合成器有自身包络/释放行为，实际长度可能略短；允许 ±15% 容差
        val tolerance = (expectedSamples * 0.15).toInt()
        assertTrue(
            "缓冲长度 ${buffer.size} 应接近预期 $expectedSamples (容差 ±$tolerance)",
            buffer.size in (expectedSamples - tolerance)..(expectedSamples + tolerance)
        )
    }

    @Test
    fun `same note produces same length buffer`() {
        val b1 = builder.renderNote(60)
        val b2 = builder.renderNote(60)
        assertEquals(b1.size, b2.size)
    }

    @Test
    fun `different notes produce same length buffer`() {
        val low = builder.renderNote(48) // C3
        val high = builder.renderNote(84) // C6
        assertEquals(low.size, high.size)
    }

    // ── 不削波 ────────────────────────────────────────────

    @Test
    fun `render samples are within negative one to one`() {
        val buffer = builder.renderNote(60)
        buffer.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `render high velocity samples within range`() {
        val buffer = builder.renderNote(76, velocity = 120)
        buffer.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    // ── 信号质量 ──────────────────────────────────────────

    @Test
    fun `render produces non-zero audio signal`() {
        val buffer = builder.renderNote(60)
        val nonZeroCount = buffer.count { it != 0.0f }
        assertTrue("音频应有非零信号 (非零=${nonZeroCount})", nonZeroCount > buffer.size / 4)
    }

    @Test
    fun `different notes produce different buffers`() {
        val low = builder.renderNote(60) // C4
        val high = builder.renderNote(72) // C5
        assertEquals(low.size, high.size)
        var diffCount = 0
        for (i in low.indices) {
            if (low[i] != high[i]) diffCount++
        }
        assertTrue("不同音符应产生不同音频 (差异采样=${diffCount})", diffCount > 0)
    }

    @Test
    fun `adjacent semitones produce different buffers`() {
        val c = builder.renderNote(60) // C4
        val cSharp = builder.renderNote(61) // C#4
        assertEquals(c.size, cSharp.size)
        var diffCount = 0
        for (i in c.indices) {
            if (c[i] != cSharp[i]) diffCount++
        }
        assertTrue("相邻半音应产生不同音频", diffCount > 0)
    }

    // ── 静音区域 ──────────────────────────────────────────

    @Test
    fun `render includes lead silence`() {
        val buffer = builder.renderNote(60)
        val leadSamples = (PitchTrainingAudioBuilder.SAMPLE_RATE *
            PitchTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val leadEnd = minOf(leadSamples, buffer.size / 4)
        var silentCount = 0
        for (i in 0 until leadEnd) {
            if (buffer[i] == 0.0f) silentCount++
        }
        assertTrue("前导应有静音区域 (静音=${silentCount})", silentCount > 0)
    }

    @Test
    fun `render includes tail silence`() {
        val buffer = builder.renderNote(60)
        val tailSamples = (PitchTrainingAudioBuilder.SAMPLE_RATE *
            PitchTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val tailStart = buffer.size - tailSamples
        var silentCount = 0
        for (i in tailStart until buffer.size) {
            if (buffer[i] == 0.0f) silentCount++
        }
        assertTrue("尾部应有静音区域 (静音=${silentCount})", silentCount > 0)
    }

    // ── 时长预估 ──────────────────────────────────────────

    @Test
    fun `estimate duration matches formula`() {
        val q = PitchQuestion(
            pitchClass = PitchClass.A,
            midiNote = 69,
            difficulty = PitchTrainingDifficulty.INTERMEDIATE,
            options = PitchClass.ALL
        )
        val estimated = builder.estimateDurationMs(q)
        val expected = PitchTrainingAudioBuilder.LEAD_SILENCE_MS +
            PitchTrainingAudioBuilder.NOTE_DURATION_MS +
            PitchTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    @Test
    fun `estimate duration is constant across difficulties`() {
        val expected = PitchTrainingAudioBuilder.LEAD_SILENCE_MS +
            PitchTrainingAudioBuilder.NOTE_DURATION_MS +
            PitchTrainingAudioBuilder.TAIL_SILENCE_MS
        PitchTrainingDifficulty.ALL.forEach { d ->
            val q = PitchTrainingEngine.withSeed(1L).generate(d)
            assertEquals(expected, builder.estimateDurationMs(q))
        }
    }

    // ── 全难度渲染 ────────────────────────────────────────

    @Test
    fun `render works for all difficulties`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val engine = PitchTrainingEngine.withSeed(1L)
            val q = engine.generate(difficulty)
            val buffer = builder.render(q)
            assertTrue("${difficulty.displayName} 渲染应非空", buffer.isNotEmpty())
        }
    }

    @Test
    fun `render works for extreme piano range`() {
        val lowBuffer = builder.renderNote(21)  // A0
        val highBuffer = builder.renderNote(108) // C8
        assertTrue("最低音渲染应非空", lowBuffer.isNotEmpty())
        assertTrue("最高音渲染应非空", highBuffer.isNotEmpty())
    }

    // ── 渲染题目 ──────────────────────────────────────────

    @Test
    fun `render question matches render note`() {
        val q = PitchQuestion(
            pitchClass = PitchClass.G,
            midiNote = 67,
            difficulty = PitchTrainingDifficulty.BEGINNER,
            options = PitchClass.WHITE_KEYS
        )
        val fromQuestion = builder.render(q)
        val fromNote = builder.renderNote(67)
        assertEquals(fromNote.size, fromQuestion.size)
    }
}
