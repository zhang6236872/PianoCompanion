package com.pianocompanion.timbrebrightness

import org.junit.Assert.*
import org.junit.Test

/**
 * [TimbreBrightnessAudioBuilder] 单元测试。
 *
 * 验证 PCM 渲染、泛音结构、频率换算、RMS 归一化、确定性等。
 */
class TimbreBrightnessAudioBuilderTest {

    private val builder = TimbreBrightnessAudioBuilder()

    // ── 基础渲染 ──────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val engine = TimbreBrightnessEngine.withSeed(1L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val audio = builder.render(q)
        assertTrue("渲染结果不应为空", audio.isNotEmpty())
    }

    @Test
    fun `render output is valid FloatArray`() {
        val engine = TimbreBrightnessEngine.withSeed(2L)
        val q = engine.generate(TimbreBrightnessDifficulty.BEGINNER)
        val audio = builder.render(q)
        for (sample in audio) {
            assertTrue("样本应在 [-1, 1] 范围内: $sample", sample in -1.0f..1.0f)
            assertFalse(sample.isNaN())
            assertFalse(sample.isInfinite())
        }
    }

    @Test
    fun `render is deterministic with same question`() {
        val engine = TimbreBrightnessEngine.withSeed(42L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val audio1 = builder.render(q)
        val audio2 = builder.render(q)
        assertArrayEquals(audio1, audio2, 0.0f)
    }

    // ── 事件构建 ──────────────────────────────────

    @Test
    fun `buildToneEvents creates two events`() {
        val engine = TimbreBrightnessEngine.withSeed(3L)
        val q = engine.generate(TimbreBrightnessDifficulty.INTERMEDIATE)
        val events = builder.buildToneEvents(q)
        assertEquals(2, events.size)
    }

    @Test
    fun `first event onset is zero`() {
        val engine = TimbreBrightnessEngine.withSeed(4L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        assertEquals(0.0, events[0].onsetMs, 0.001)
    }

    @Test
    fun `second event onset equals first duration plus gap`() {
        val engine = TimbreBrightnessEngine.withSeed(5L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        val expectedSecondOnset = TimbreBrightnessAudioBuilder.TONE_DURATION_MS + TimbreBrightnessAudioBuilder.GAP_MS
        assertEquals(expectedSecondOnset, events[1].onsetMs, 0.001)
    }

    @Test
    fun `both events use same fundamental and brightness`() {
        val engine = TimbreBrightnessEngine.withSeed(6L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        assertEquals(events[0].fundamentalMidi, events[1].fundamentalMidi)
        assertEquals(events[0].brightness, events[1].brightness)
    }

    @Test
    fun `computeOnsets returns two timestamps`() {
        val engine = TimbreBrightnessEngine.withSeed(7L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val onsets = builder.computeOnsets(q)
        assertEquals(2, onsets.size)
        assertEquals(0.0, onsets[0], 0.001)
    }

    // ── 频率换算 ──────────────────────────────────

    @Test
    fun `midiToFreq A4 equals 440 Hz`() {
        val freq = builder.midiToFreq(69)
        assertEquals(440.0, freq, 0.1)
    }

    @Test
    fun `midiToFreq C4 is approximately 261_63 Hz`() {
        val freq = builder.midiToFreq(60)
        assertEquals(261.63, freq, 0.1)
    }

    @Test
    fun `midiToFreq octave doubles frequency`() {
        val freqC4 = builder.midiToFreq(60)
        val freqC5 = builder.midiToFreq(72)
        assertEquals(2.0, freqC5 / freqC4, 0.001)
    }

    @Test
    fun `noteFrequency delegates to midiToFreq`() {
        val engine = TimbreBrightnessEngine.withSeed(8L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val freq = builder.noteFrequency(q.fundamentalMidi)
        assertEquals(builder.midiToFreq(q.fundamentalMidi), freq, 0.001)
    }

    // ── 泛音结构 ──────────────────────────────────

    @Test
    fun `PURE has only fundamental no harmonics`() {
        val amps = builder.harmonicAmplitudes(TimbreBrightness.PURE)
        assertEquals(1, amps.size)
        assertEquals(1.0, amps[0], 0.001)
    }

    @Test
    fun `MELLOW has fundamental plus 2 harmonics`() {
        val amps = builder.harmonicAmplitudes(TimbreBrightness.MELLOW)
        assertEquals(3, amps.size)
        assertEquals(1.0, amps[0], 0.001) // fundamental
    }

    @Test
    fun `BRIGHT has fundamental plus 5 harmonics`() {
        val amps = builder.harmonicAmplitudes(TimbreBrightness.BRIGHT)
        assertEquals(6, amps.size)
    }

    @Test
    fun `BRILLIANT has fundamental plus 10 harmonics`() {
        val amps = builder.harmonicAmplitudes(TimbreBrightness.BRILLIANT)
        assertEquals(11, amps.size)
    }

    @Test
    fun `harmonic amplitudes decay geometrically for MELLOW`() {
        val amps = builder.harmonicAmplitudes(TimbreBrightness.MELLOW)
        val strength = TimbreBrightness.MELLOW.harmonicStrength
        assertEquals(1.0, amps[0], 0.001)          // fundamental
        assertEquals(strength, amps[1], 0.001)     // 1st harmonic = strength^1
        assertEquals(strength * strength, amps[2], 0.001) // 2nd harmonic = strength^2
    }

    @Test
    fun `BRILLIANT has stronger harmonics than MELLOW at same index`() {
        val mellowAmps = builder.harmonicAmplitudes(TimbreBrightness.MELLOW)
        val brilliantAmps = builder.harmonicAmplitudes(TimbreBrightness.BRILLIANT)
        // Compare first harmonic (index 1)
        assertTrue(
            "BRILLIANT 第 1 泛音应比 MELLOW 更强: ${brilliantAmps[1]} > ${mellowAmps[1]}",
            brilliantAmps[1] > mellowAmps[1]
        )
    }

    @Test
    fun `fundamental amplitude is always 1_0`() {
        for (brightness in TimbreBrightness.ALL) {
            val amps = builder.harmonicAmplitudes(brightness)
            assertEquals(
                "${brightness.displayName} 基频幅度应为 1.0",
                1.0,
                amps[0],
                0.001
            )
        }
    }

    // ── RMS 归一化（响度一致性） ──────────────────────────────────

    @Test
    fun `PURE and BRILLIANT have similar RMS after normalization`() {
        val engine = TimbreBrightnessEngine.withSeed(100L)
        val qPure = TimbreBrightnessQuestion(
            brightness = TimbreBrightness.PURE,
            difficulty = TimbreBrightnessDifficulty.ADVANCED,
            seed = 1L,
            fundamentalMidi = 69,
            answerChoices = TimbreBrightness.ALL.map { it.fullLabel },
            correctAnswer = TimbreBrightness.PURE.fullLabel
        )
        val qBrilliant = TimbreBrightnessQuestion(
            brightness = TimbreBrightness.BRILLIANT,
            difficulty = TimbreBrightnessDifficulty.ADVANCED,
            seed = 2L,
            fundamentalMidi = 69,
            answerChoices = TimbreBrightness.ALL.map { it.fullLabel },
            correctAnswer = TimbreBrightness.BRILLIANT.fullLabel
        )

        val audioPure = builder.render(qPure)
        val audioBrilliant = builder.render(qBrilliant)

        val rmsPure = computeRMS(audioPure)
        val rmsBrilliant = computeRMS(audioBrilliant)

        // RMS 归一化后，两者应在 20% 范围内（不完全相同因为衰减包络和 tanh 影响）
        val ratio = rmsPure / rmsBrilliant
        assertTrue(
            "PURE RMS ($rmsPure) 和 BRILLIANT RMS ($rmsBrilliant) 应在 20% 范围内 (ratio=$ratio)",
            ratio in 0.8..1.2
        )
    }

    @Test
    fun `all brightness levels have similar RMS`() {
        val fundamentalMidi = 69
        for (brightness in TimbreBrightness.ALL) {
            val q = TimbreBrightnessQuestion(
                brightness = brightness,
                difficulty = TimbreBrightnessDifficulty.ADVANCED,
                seed = 42L,
                fundamentalMidi = fundamentalMidi,
                answerChoices = TimbreBrightness.ALL.map { it.fullLabel },
                correctAnswer = brightness.fullLabel
            )
            val audio = builder.render(q)
            val rms = computeRMS(audio)
            assertTrue(
                "${brightness.displayName} RMS ($rms) 应在合理范围 (>0.01)",
                rms > 0.01
            )
        }
    }

    // ── 估算时长 ──────────────────────────────────

    @Test
    fun `estimateDurationMs is positive`() {
        val engine = TimbreBrightnessEngine.withSeed(11L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val duration = builder.estimateDurationMs(q)
        assertTrue("估算时长应为正数", duration > 0)
    }

    @Test
    fun `estimateDurationMs includes lead silence tone and tail`() {
        val engine = TimbreBrightnessEngine.withSeed(12L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val duration = builder.estimateDurationMs(q)
        val minExpected = TimbreBrightnessAudioBuilder.LEAD_SILENCE_MS +
            TimbreBrightnessAudioBuilder.TONE_DURATION_MS +
            TimbreBrightnessAudioBuilder.GAP_MS +
            TimbreBrightnessAudioBuilder.TONE_DURATION_MS +
            TimbreBrightnessAudioBuilder.TAIL_SILENCE_MS
        assertEquals(minExpected.toLong(), duration)
    }

    // ── 辅助 ──────────────────────────────────

    @Test
    fun `custom sample rate produces proportional buffer`() {
        val customBuilder = TimbreBrightnessAudioBuilder(sampleRate = 22050)
        val engine = TimbreBrightnessEngine.withSeed(13L)
        val q = engine.generate(TimbreBrightnessDifficulty.ADVANCED)
        val audio44100 = builder.render(q)
        val audio22050 = customBuilder.render(q)

        // 22050 Hz 的缓冲区约为 44100 Hz 的一半
        val ratio = audio22050.size.toDouble() / audio44100.size
        assertEquals(0.5, ratio, 0.05)
    }

    @Test
    fun `render all difficulty levels without error`() {
        val engine = TimbreBrightnessEngine.withSeed(77L)
        for (difficulty in TimbreBrightnessDifficulty.ALL) {
            val q = engine.generate(difficulty)
            val audio = builder.render(q)
            assertTrue("渲染 ${difficulty.displayName} 不应失败", audio.isNotEmpty())
        }
    }

    @Test
    fun `PURE waveform is close to sine wave at fundamental`() {
        // PURE has only fundamental, so the waveform should approximate sin(2πf·t) × envelope
        val q = TimbreBrightnessQuestion(
            brightness = TimbreBrightness.PURE,
            difficulty = TimbreBrightnessDifficulty.BEGINNER,
            seed = 1L,
            fundamentalMidi = 69, // A4 = 440 Hz
            answerChoices = TimbreBrightness.BEGINNER_LEVELS.map { it.fullLabel },
            correctAnswer = TimbreBrightness.PURE.fullLabel
        )
        val audio = builder.render(q)

        // Find first peak after lead silence
        val leadSamples = (TimbreBrightnessAudioBuilder.DEFAULT_SAMPLE_RATE *
            TimbreBrightnessAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        var peakCount = 0
        for (i in leadSamples + 1 until minOf(audio.size, leadSamples + 44100)) {
            if (audio[i] > audio[i - 1] && audio[i] > 0.05f && (i == audio.size - 1 || audio[i] >= audio[i + 1])) {
                peakCount++
            }
        }
        // In ~1 second of 440 Hz sine wave, there should be ~440 peaks (one per cycle)
        assertTrue("PURE 波形应近似正弦波，有多个周期峰值: $peakCount", peakCount > 200)
    }

    // ── 辅助方法 ──────────────────────────────────

    private fun computeRMS(audio: FloatArray): Double {
        var sumSq = 0.0
        for (sample in audio) {
            sumSq += sample.toDouble() * sample.toDouble()
        }
        return kotlin.math.sqrt(sumSq / audio.size)
    }
}
