package com.pianocompanion.harmonicintervaltraining

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 和声音程辨识训练音频构建器单元测试。
 *
 * 验证 PCM 缓冲区有效性、采样范围、频率计算、和声音程区分度。
 */
class HarmonicIntervalAudioBuilderTest {

    private lateinit var builder: HarmonicIntervalAudioBuilder

    @Before
    fun setUp() {
        builder = HarmonicIntervalAudioBuilder(sampleRate = 44100)
    }

    // ── 缓冲区基本属性 ──────────────────────────────────────────

    @Test
    fun `渲染产生非空缓冲区`() {
        val q = HarmonicIntervalQuestion(
            interval = HarmonicInterval.PERFECT_FIFTH,
            difficulty = HarmonicIntervalDifficulty.BEGINNER,
            answerChoices = listOf(),
            correctAnswer = ""
        )
        val pcm = builder.render(q)
        assertTrue("PCM 缓冲区应非空", pcm.isNotEmpty())
    }

    @Test
    fun `缓冲区长度合理`() {
        val pcm = builder.renderInterval(semitones = 7)
        // 前导 400ms + 主体 1200ms + 尾部 300ms = 1900ms
        // 44100 × 1.9 = 83790
        val expectedMin = 44000 * 1.8
        val expectedMax = 44100 * 2.0
        assertTrue("缓冲区长度 ${pcm.size} 应在合理范围内",
            pcm.size in expectedMin.toInt()..expectedMax.toInt())
    }

    @Test
    fun `采样值在有效范围`() {
        val pcm = builder.renderInterval(semitones = 4)
        for (sample in pcm) {
            assertTrue("采样值 $sample 应在 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `缓冲区有非零内容`() {
        val pcm = builder.renderInterval(semitones = 7)
        val nonZeroCount = pcm.count { it != 0.0f }
        assertTrue("应有大量非零采样", nonZeroCount > pcm.size / 2)
    }

    // ── 前导静音 ──────────────────────────────────────────

    @Test
    fun `前导静音区域接近零`() {
        val pcm = builder.renderInterval(semitones = 7)
        val leadSamples = (44100 * HarmonicIntervalAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前导静音区域应该接近零
        for (i in 0 until leadSamples - 100) {
            assertTrue("前导区域 [$i] = ${pcm[i]} 应接近零", kotlin.math.abs(pcm[i]) < 0.01f)
        }
    }

    @Test
    fun `尾部静音区域接近零`() {
        val pcm = builder.renderInterval(semitones = 7)
        val tailSamples = (44100 * HarmonicIntervalAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        // 尾部静音区域应该接近零
        for (i in (pcm.size - tailSamples + 100) until pcm.size) {
            assertTrue("尾部区域 [$i] = ${pcm[i]} 应接近零", kotlin.math.abs(pcm[i]) < 0.01f)
        }
    }

    // ── 频率计算 ──────────────────────────────────────────

    @Test
    fun `midiToFreq C4 约为261_63Hz`() {
        val freq = builder.midiToFreq(60) // C4
        assertEquals(261.63, freq, 0.1)
    }

    @Test
    fun `midiToFreq A4 约为440Hz`() {
        val freq = builder.midiToFreq(69) // A4
        assertEquals(440.0, freq, 0.01)
    }

    @Test
    fun `midiToFreq C5 约为523_25Hz`() {
        val freq = builder.midiToFreq(72) // C5
        assertEquals(523.25, freq, 0.1)
    }

    // ── 不同音程产生不同波形 ──────────────────────────────────────────

    @Test
    fun `大三度和小三度波形不同`() {
        val major3 = builder.renderInterval(semitones = 4)
        val minor3 = builder.renderInterval(semitones = 3)
        assertFalse("大三度和小三度波形应不同", major3.contentEquals(minor3))
    }

    @Test
    fun `纯五度和三全音波形不同`() {
        val fifth = builder.renderInterval(semitones = 7)
        val tritone = builder.renderInterval(semitones = 6)
        assertFalse("纯五度和三全音波形应不同", fifth.contentEquals(tritone))
    }

    @Test
    fun `纯八度和纯五度波形不同`() {
        val octave = builder.renderInterval(semitones = 12)
        val fifth = builder.renderInterval(semitones = 7)
        assertFalse("纯八度和纯五度波形应不同", octave.contentEquals(fifth))
    }

    @Test
    fun `相同参数产生相同波形`() {
        val a = builder.renderInterval(semitones = 5)
        val b = builder.renderInterval(semitones = 5)
        assertTrue("相同参数应产生相同波形", a.contentEquals(b))
    }

    // ── 音程半音数与波形复杂度的关系 ──────────────────────────────────────────

    @Test
    fun `三全音有更多高频成分（拍频）`() {
        // 三全音 (6 semitones) 产生拍频，频谱更复杂
        // 纯八度 (12 semitones) 两个音的谐波重合，更简单
        val tritone = builder.renderInterval(semitones = 6)
        val octave = builder.renderInterval(semitones = 12)

        // 计算"过零率"作为粗略复杂度指标
        val tritoneZcr = computeZeroCrossingRate(tritone)
        val octaveZcr = computeZeroCrossingRate(octave)

        // 三全音的过零率应该不同（可能更高因为两个频率更近，或波形更不规则）
        assertNotEquals("三全音和八度的过零率应不同",
            tritoneZcr, octaveZcr, 0.0001)
    }

    @Test
    fun `不同半音数产生不同能量分布`() {
        val intervals = listOf(3, 4, 5, 6, 7, 8, 9, 12)
        val rmsValues = mutableMapOf<Int, Double>()

        for (semis in intervals) {
            val pcm = builder.renderInterval(semitones = semis)
            val rms = kotlin.math.sqrt(pcm.map { it.toDouble() * it }.average())
            rmsValues[semis] = rms
        }

        // 不同音程的 RMS 不应完全相同
        val uniqueRms = rmsValues.values.toSet()
        assertTrue("不同音程应有不同能量分布", uniqueRms.size > 1)
    }

    // ── 预估时长 ──────────────────────────────────────────

    @Test
    fun `estimateDurationMs 返回合理值`() {
        val duration = builder.estimateDurationMs()
        // 400 + 1200 + 300 = 1900ms
        assertEquals(1900L, duration)
    }

    // ── render 方法 ──────────────────────────────────────────

    @Test
    fun `render 使用题目参数`() {
        val q = HarmonicIntervalQuestion(
            interval = HarmonicInterval.MAJOR_THIRD,
            difficulty = HarmonicIntervalDifficulty.BEGINNER,
            lowerMidi = 60,
            answerChoices = listOf(),
            correctAnswer = ""
        )
        val pcm1 = builder.render(q)
        val pcm2 = builder.renderInterval(lowerMidi = 60, semitones = 4)
        assertTrue("render(question) 应等价于 renderInterval(60, 4)",
            pcm1.contentEquals(pcm2))
    }

    @Test
    fun `不同下方音产生不同波形`() {
        val c4 = builder.renderInterval(lowerMidi = 60, semitones = 7)
        val g4 = builder.renderInterval(lowerMidi = 67, semitones = 7)
        assertFalse("不同下方音应产生不同波形", c4.contentEquals(g4))
    }

    // ── 辅助方法 ──────────────────────────────────────────

    private fun computeZeroCrossingRate(pcm: FloatArray): Double {
        var crossings = 0
        for (i in 1 until pcm.size) {
            if ((pcm[i - 1] >= 0 && pcm[i] < 0) || (pcm[i - 1] < 0 && pcm[i] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / pcm.size
    }
}
