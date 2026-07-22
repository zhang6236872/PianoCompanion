package com.pianocompanion.harmonicseries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泛音列辨识训练音频构建器单元测试。
 */
class HarmonicSeriesAudioBuilderTest {

    private val builder = HarmonicSeriesAudioBuilder()

    private fun createQuestion(
        harmonic: HarmonicNumber = HarmonicNumber.THIRD,
        difficulty: HarmonicDifficulty = HarmonicDifficulty.ADVANCED
    ): HarmonicSeriesQuestion {
        // 确保正确答案在选项中：目标泛音 + 其余泛音（打乱后取 choiceCount 个）
        val otherHarmonics = difficulty.harmonics.filter { it != harmonic }
        val selected = (listOf(harmonic) + otherHarmonics.take(difficulty.choiceCount - 1))
        val choices = selected.map { it.displayName }.shuffled()
        return HarmonicSeriesQuestion(
            difficulty = difficulty,
            seed = 42L,
            targetHarmonic = harmonic,
            answerChoices = choices,
            correctAnswer = harmonic.displayName
        )
    }

    // ── 渲染基础 ──────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = createQuestion()
        val audio = builder.render(q)
        assertTrue("Audio buffer should not be empty", audio.isNotEmpty())
    }

    @Test
    fun `render returns FloatArray`() {
        val q = createQuestion()
        val audio = builder.render(q)
        assertTrue(audio is FloatArray)
    }

    @Test
    fun `rendered values are in valid range`() {
        val q = createQuestion()
        val audio = builder.render(q)
        audio.forEach { sample ->
            assertTrue("Sample $sample out of range [-1, 1]", sample in -1.0f..1.0f)
        }
    }

    // ── 确定性 ──────────────────────────────────

    @Test
    fun `same question produces identical audio`() {
        val q = createQuestion()
        val audio1 = builder.render(q)
        val audio2 = builder.render(q)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `different harmonics produce different audio`() {
        val q2 = createQuestion(harmonic = HarmonicNumber.SECOND)
        val q3 = createQuestion(harmonic = HarmonicNumber.THIRD)
        val audio2 = builder.render(q2)
        val audio3 = builder.render(q3)
        // 不同泛音频率不同，音频应不同
        var diff = 0
        val minLen = minOf(audio2.size, audio3.size)
        for (i in 0 until minLen) {
            if (Math.abs(audio2[i] - audio3[i]) > 0.001f) diff++
        }
        assertTrue("Different harmonics should produce different audio", diff > 100)
    }

    // ── 频率换算 ──────────────────────────────────

    @Test
    fun `midiToFreq A4 equals 440 Hz`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.1) // A4 = MIDI 69
    }

    @Test
    fun `midiToFreq A5 equals 880 Hz`() {
        assertEquals(880.0, builder.midiToFreq(81), 0.1) // A5 = MIDI 81
    }

    @Test
    fun `midiToFreq octave doubles frequency`() {
        val freq1 = builder.midiToFreq(48) // C3
        val freq2 = builder.midiToFreq(60) // C4
        assertEquals(2.0, freq2 / freq1, 0.001)
    }

    @Test
    fun `target harmonic frequency is integer multiple of fundamental`() {
        val q = createQuestion(harmonic = HarmonicNumber.THIRD)
        val fundamentalFreq = builder.fundamentalFrequency(q)
        val harmonicFreq = builder.targetHarmonicFrequency(q)
        assertEquals(3.0, harmonicFreq / fundamentalFreq, 0.001) // 第3泛音 = 3倍
    }

    @Test
    fun `second harmonic frequency is exactly 2x fundamental`() {
        val q = createQuestion(harmonic = HarmonicNumber.SECOND)
        val fundamentalFreq = builder.fundamentalFrequency(q)
        val harmonicFreq = builder.targetHarmonicFrequency(q)
        assertEquals(2.0, harmonicFreq / fundamentalFreq, 0.001)
    }

    @Test
    fun `eighth harmonic frequency is exactly 8x fundamental`() {
        val q = createQuestion(harmonic = HarmonicNumber.EIGHTH)
        val fundamentalFreq = builder.fundamentalFrequency(q)
        val harmonicFreq = builder.targetHarmonicFrequency(q)
        assertEquals(8.0, harmonicFreq / fundamentalFreq, 0.001)
    }

    // ── 事件构建 ──────────────────────────────────

    @Test
    fun `buildToneEvents produces exactly 2 events`() {
        val q = createQuestion()
        val events = builder.buildToneEvents(q)
        assertEquals(2, events.size)
    }

    @Test
    fun `first event is fundamental`() {
        val q = createQuestion()
        val events = builder.buildToneEvents(q)
        assertTrue(events[0].isFundamental)
        assertEquals(1, events[0].harmonicNumber)
    }

    @Test
    fun `second event is target harmonic`() {
        val q = createQuestion(harmonic = HarmonicNumber.FIFTH)
        val events = builder.buildToneEvents(q)
        assertFalse(events[1].isFundamental)
        assertEquals(5, events[1].harmonicNumber)
    }

    @Test
    fun `fundamental event starts at time zero`() {
        val q = createQuestion()
        val events = builder.buildToneEvents(q)
        assertEquals(0.0, events[0].onsetMs, 0.1)
    }

    @Test
    fun `harmonic event starts after fundamental plus gap`() {
        val q = createQuestion()
        val d = q.difficulty
        val events = builder.buildToneEvents(q)
        val expectedOnset = d.fundamentalDurationMs + d.gapMs
        assertEquals(expectedOnset.toDouble(), events[1].onsetMs, 0.1)
    }

    @Test
    fun `fundamental frequency matches MIDI to freq`() {
        val q = createQuestion()
        val events = builder.buildToneEvents(q)
        val expectedFreq = builder.midiToFreq(q.difficulty.fundamentalMidi)
        assertEquals(expectedFreq, events[0].frequencyHz, 0.1)
    }

    @Test
    fun `harmonic frequency matches fundamental times ratio`() {
        val q = createQuestion(harmonic = HarmonicNumber.SEVENTH)
        val events = builder.buildToneEvents(q)
        val fundamentalFreq = events[0].frequencyHz
        val expectedHarmonicFreq = fundamentalFreq * 7.0
        assertEquals(expectedHarmonicFreq, events[1].frequencyHz, 0.1)
    }

    // ── 时长验证 ──────────────────────────────────

    @Test
    fun `musicDurationMs includes both segments`() {
        val q = createQuestion()
        val d = q.difficulty
        val musicDur = builder.musicDurationMs(q)
        // 基频 + 间隔 + 泛音
        val expected = d.fundamentalDurationMs + d.gapMs + d.harmonicDurationMs
        assertEquals(expected.toDouble(), musicDur, 1.0)
    }

    @Test
    fun `estimateDurationMs includes lead and tail silence`() {
        val q = createQuestion()
        val estimated = builder.estimateDurationMs(q)
        val musicMs = builder.musicDurationMs(q)
        val expected = HarmonicSeriesAudioBuilder.LEAD_SILENCE_MS +
            musicMs + HarmonicSeriesAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected.toLong(), estimated)
    }

    @Test
    fun `audio buffer length matches estimated duration`() {
        val q = createQuestion()
        val audio = builder.render(q)
        val estimatedMs = builder.estimateDurationMs(q)
        val estimatedSamples = (44100 * estimatedMs / 1000.0).toInt()
        assertEquals(estimatedSamples, audio.size)
    }

    // ── 自定义采样率 ──────────────────────────────────

    @Test
    fun `custom sample rate produces proportionally sized buffer`() {
        val builder22k = HarmonicSeriesAudioBuilder(22050)
        val builder44k = HarmonicSeriesAudioBuilder(44100)
        val q = createQuestion()
        val audio22k = builder22k.render(q)
        val audio44k = builder44k.render(q)
        // 44100 采样率的缓冲区应该约为 22050 的 2 倍
        assertEquals(2.0, audio44k.size.toDouble() / audio22k.size, 0.05)
    }

    // ── 全难度渲染 ──────────────────────────────────

    @Test
    fun `all difficulties render without error`() {
        HarmonicDifficulty.ALL.forEach { difficulty ->
            val q = createQuestion(difficulty = difficulty)
            val audio = builder.render(q)
            assertTrue("Difficulty ${difficulty.name} should produce audio", audio.isNotEmpty())
        }
    }

    @Test
    fun `all harmonic numbers render without error`() {
        HarmonicNumber.entries.forEach { harmonic ->
            val q = createQuestion(harmonic = harmonic, difficulty = HarmonicDifficulty.ADVANCED)
            val audio = builder.render(q)
            assertTrue("Harmonic ${harmonic.number} should produce audio", audio.isNotEmpty())
        }
    }
}
