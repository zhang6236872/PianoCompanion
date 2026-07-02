package com.pianocompanion.cadence

import com.pianocompanion.chord.ChordRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * CadenceAudioBuilder 单元测试（JUnit 4）。
 *
 * 覆盖范围：
 * - PCM 缓冲区非空
 * - 缓冲区长度精确计算
 * - 前导静音区为 0
 * - 尾部静音区为 0
 * - 有声音区非零
 * - 采样值在 [-1.0, 1.0] 范围内（不削波）
 * - 时长估算
 * - 确定性（相同参数 → 相同输出）
 * - 不同终止式产生不同输出
 * - 不同调性产生不同输出
 * - 空终止式处理
 */
class CadenceAudioBuilderTest {

    private val builder = CadenceAudioBuilder()

    private fun makeInstance(
        type: CadenceType = CadenceType.PERFECT_AUTHENTIC,
        key: ChordRoot = ChordRoot.C,
        mode: CadenceMode = CadenceMode.MAJOR
    ): CadenceInstance {
        return CadenceEngine.instantiate(key, type, mode)
    }

    @Test
    fun renderProducesNonEmptyBuffer() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun renderBufferLengthMatchesExpectedCalculation() {
        val inst = makeInstance() // 2 chords
        val audio = builder.render(inst)

        val expectedMs = CadenceAudioBuilder.LEAD_SILENCE_MS +
            2 * CadenceAudioBuilder.CHORD_DURATION_MS +
            1 * CadenceAudioBuilder.CHORD_GAP_MS +
            CadenceAudioBuilder.TAIL_SILENCE_MS

        val expectedSamples = (expectedMs * CadenceAudioBuilder.SAMPLE_RATE / 1000).toInt()
        assertEquals(expectedSamples, audio.size)
    }

    @Test
    fun renderBufferLengthMatchesEstimateDurationMs() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        val expectedMs = builder.estimateDurationMs(inst)
        val expectedSamples = (expectedMs * CadenceAudioBuilder.SAMPLE_RATE / 1000).toInt()
        assertEquals(expectedSamples, audio.size)
    }

    @Test
    fun leadSilenceRegionIsAllZeros() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        val leadSamples = (CadenceAudioBuilder.SAMPLE_RATE * CadenceAudioBuilder.LEAD_SILENCE_MS / 1000).toInt()
        for (i in 0 until leadSamples) {
            assertTrue("Sample $i in lead silence should be ~0, got ${audio[i]}", abs(audio[i]) < 0.0001f)
        }
    }

    @Test
    fun tailSilenceRegionIsAllZeros() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        val tailSamples = (CadenceAudioBuilder.SAMPLE_RATE * CadenceAudioBuilder.TAIL_SILENCE_MS / 1000).toInt()
        for (i in (audio.size - tailSamples) until audio.size) {
            assertTrue("Sample $i in tail silence should be ~0, got ${audio[i]}", abs(audio[i]) < 0.0001f)
        }
    }

    @Test
    fun soundRegionIsNonZero() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        val leadSamples = (CadenceAudioBuilder.SAMPLE_RATE * CadenceAudioBuilder.LEAD_SILENCE_MS / 1000).toInt()
        val tailSamples = (CadenceAudioBuilder.SAMPLE_RATE * CadenceAudioBuilder.TAIL_SILENCE_MS / 1000).toInt()

        var nonZeroCount = 0
        for (i in leadSamples until (audio.size - tailSamples)) {
            if (abs(audio[i]) > 0.0001f) nonZeroCount++
        }
        assertTrue("Sound region should have significant non-zero samples, got $nonZeroCount", nonZeroCount > 100)
    }

    @Test
    fun allSamplesWithinValidRangeNoClipping() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        for (i in audio.indices) {
            assertTrue("Sample $i should be >= -1.0, got ${audio[i]}", audio[i] >= -1.0f)
            assertTrue("Sample $i should be <= 1.0, got ${audio[i]}", audio[i] <= 1.0f)
        }
    }

    @Test
    fun estimateDurationMsIsCorrectFor2ChordCadence() {
        val inst = makeInstance()
        val ms = builder.estimateDurationMs(inst)
        val expected = CadenceAudioBuilder.LEAD_SILENCE_MS +
            2 * CadenceAudioBuilder.CHORD_DURATION_MS +
            1 * CadenceAudioBuilder.CHORD_GAP_MS +
            CadenceAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, ms)
    }

    @Test
    fun estimateDurationForDifferentCadenceTypes() {
        // All cadence types have 2 steps, so duration should be same
        for (type in CadenceType.entries) {
            val mode = CadenceEngine.supportedModes(type).first()
            val inst = makeInstance(type, mode = mode)
            val ms = builder.estimateDurationMs(inst)
            assertEquals(2800L, ms)
        }
    }

    @Test
    fun renderIsDeterministicForSameParameters() {
        val inst = makeInstance()
        val audio1 = builder.render(inst)
        val audio2 = builder.render(inst)
        assertTrue(audio1.contentEquals(audio2))
    }

    @Test
    fun differentCadenceTypesProduceDifferentAudio() {
        val pacInst = makeInstance(CadenceType.PERFECT_AUTHENTIC)
        val plagalInst = makeInstance(CadenceType.PLAGAL)
        val audio1 = builder.render(pacInst)
        val audio2 = builder.render(plagalInst)
        var differences = 0
        val minLen = minOf(audio1.size, audio2.size)
        for (i in 0 until minLen) {
            if (audio1[i] != audio2[i]) differences++
        }
        assertTrue("Different cadences should produce different audio", differences > 100)
    }

    @Test
    fun differentKeysProduceDifferentAudio() {
        val inst1 = makeInstance(key = ChordRoot.C)
        val inst2 = makeInstance(key = ChordRoot.D)
        val audio1 = builder.render(inst1)
        val audio2 = builder.render(inst2)
        var differences = 0
        val minLen = minOf(audio1.size, audio2.size)
        for (i in 0 until minLen) {
            if (audio1[i] != audio2[i]) differences++
        }
        assertTrue("Different keys should produce different audio", differences > 100)
    }

    @Test
    fun velocityAffectsAmplitude() {
        val inst = makeInstance()
        val audioSoft = builder.render(inst, velocity = 30)
        val audioLoud = builder.render(inst, velocity = 100)
        val peakSoft = audioSoft.maxOfOrNull { abs(it) } ?: 0f
        val peakLoud = audioLoud.maxOfOrNull { abs(it) } ?: 0f
        assertTrue("Louder velocity should produce higher amplitude ($peakLoud > $peakSoft)", peakLoud > peakSoft)
    }

    @Test
    fun minorModeCadenceRendersSuccessfully() {
        val inst = makeInstance(CadenceType.PHRYGIAN_HALF, key = ChordRoot.A, mode = CadenceMode.HARMONIC_MINOR)
        val audio = builder.render(inst)
        assertTrue(audio.isNotEmpty())
        for (i in audio.indices) {
            assertTrue(audio[i] >= -1.0f && audio[i] <= 1.0f)
        }
    }

    @Test
    fun all12KeysRenderWithoutError() {
        for (key in ChordRoot.entries) {
            val inst = makeInstance(key = key)
            val audio = builder.render(inst)
            assertTrue("Key $key should render non-empty audio", audio.isNotEmpty())
        }
    }

    @Test
    fun renderWithEmptyStepsReturnsEmptyBuffer() {
        val inst = CadenceInstance(
            type = CadenceType.PERFECT_AUTHENTIC,
            keyRoot = ChordRoot.C,
            mode = CadenceMode.MAJOR,
            steps = emptyList(),
            romanNumeralSummary = "",
            keyName = "C大调",
            preferFlats = false
        )
        val audio = builder.render(inst)
        assertTrue(audio.isEmpty())
    }

    @Test
    fun renderProducesAudioWithSignificantPeakInFirstChord() {
        val inst = makeInstance()
        val audio = builder.render(inst)
        val leadSamples = (CadenceAudioBuilder.SAMPLE_RATE * CadenceAudioBuilder.LEAD_SILENCE_MS / 1000).toInt()
        val chordSamples = (CadenceAudioBuilder.SAMPLE_RATE * CadenceAudioBuilder.CHORD_DURATION_MS / 1000).toInt()

        // Find peak amplitude in first chord region
        var firstChordPeak = 0f
        for (i in leadSamples until leadSamples + chordSamples) {
            firstChordPeak = maxOf(firstChordPeak, abs(audio[i]))
        }
        assertTrue("First chord should have significant amplitude", firstChordPeak > 0.01f)
    }
}
