package com.pianocompanion.dynamicstraining

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 力度辨识音频构建器单元测试。
 */
class DynamicsTrainingAudioBuilderTest {

    private val builder = DynamicsTrainingAudioBuilder()

    // ── 基本 PCM 缓冲区 ────────────────────────────────

    @Test
    fun `render返回非空PCM缓冲区`() {
        val question = DynamicsTrainingQuestion(
            dynamic = DynamicLevel.MEZZO_FORTE,
            difficulty = DynamicsTrainingDifficulty.BEGINNER,
            noteCount = 4,
            answerChoices = listOf("mf  中强"),
            correctAnswer = "mf  中强"
        )
        val pcm = builder.render(question)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `render采样在-1到1范围内`() {
        val pcm = builder.renderDynamic(DynamicLevel.FORTE, noteCount = 4)
        for (sample in pcm) {
            assertTrue("采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `render采样率正确`() {
        // 缓冲区长度应约等于 (LEAD + noteCount*NOTE_DUR + TAIL) * sampleRate / 1000
        val pcm = builder.renderDynamic(DynamicLevel.PIANO, noteCount = 4)
        val expectedMs = DynamicsTrainingAudioBuilder.LEAD_SILENCE_MS +
            4 * DynamicsTrainingAudioBuilder.NOTE_DURATION_MS +
            DynamicsTrainingAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (expectedMs * DynamicsTrainingAudioBuilder.DEFAULT_SAMPLE_RATE / 1000.0).toInt()
        // 允许 ±2 个采样误差
        assertTrue("实际 ${pcm.size} 预期约 $expectedSamples", abs(pcm.size - expectedSamples) <= 5)
    }

    // ── 不同 noteCount ────────────────────────────────

    @Test
    fun `noteCount越多缓冲区越长`() {
        val pcm2 = builder.renderDynamic(DynamicLevel.MEZZO_PIANO, noteCount = 2)
        val pcm4 = builder.renderDynamic(DynamicLevel.MEZZO_PIANO, noteCount = 4)
        assertTrue("4音应长于2音: ${pcm4.size} > ${pcm2.size}", pcm4.size > pcm2.size)
    }

    @Test
    fun `所有力度级别渲染成功`() {
        for (dynamic in DynamicLevel.ALL) {
            val pcm = builder.renderDynamic(dynamic, noteCount = 4)
            assertTrue("${dynamic.italianName} 渲染为空", pcm.isNotEmpty())
        }
    }

    // ── 力度差异验证（核心测试） ──────────────────────

    @Test
    fun `极强比极弱RMS更大`() {
        val pcmPp = builder.renderDynamic(DynamicLevel.PIANISSIMO, noteCount = 4)
        val pcmFf = builder.renderDynamic(DynamicLevel.FORTISSIMO, noteCount = 4)
        val rmsPp = computeRms(pcmPp)
        val rmsFf = computeRms(pcmFf)
        assertTrue("ff RMS($rmsFf) 应大于 pp RMS($rmsPp)", rmsFf > rmsPp)
    }

    @Test
    fun `强比中弱RMS更大`() {
        val pcmMp = builder.renderDynamic(DynamicLevel.MEZZO_PIANO, noteCount = 4)
        val pcmF = builder.renderDynamic(DynamicLevel.FORTE, noteCount = 4)
        val rmsMp = computeRms(pcmMp)
        val rmsF = computeRms(pcmF)
        assertTrue("f RMS($rmsF) 应大于 mp RMS($rmsMp)", rmsF > rmsMp)
    }

    @Test
    fun `RMS从弱到强单调递增`() {
        val rmsValues = DynamicLevel.ALL.map { computeRms(builder.renderDynamic(it, noteCount = 4)) }
        for (i in 1 until rmsValues.size) {
            assertTrue(
                "RMS应单调递增: ${DynamicLevel.ALL[i - 1]}=$rmsValues[${i - 1}] vs ${DynamicLevel.ALL[i]}=$rmsValues[$i]",
                rmsValues[i] > rmsValues[i - 1]
            )
        }
    }

    @Test
    fun `峰值振幅与amplitude成正比`() {
        val peakPp = computePeak(builder.renderDynamic(DynamicLevel.PIANISSIMO, noteCount = 4))
        val peakFf = computePeak(builder.renderDynamic(DynamicLevel.FORTISSIMO, noteCount = 4))
        assertTrue("ff峰值($peakFf)应远大于pp峰值($peakPp)", peakFf > peakPp * 3.0f)
    }

    @Test
    fun `峰值振幅近似等于amplitude`() {
        for (dynamic in DynamicLevel.ALL) {
            val peak = computePeak(builder.renderDynamic(dynamic, noteCount = 4))
            // 峰值应在 amplitude 的 ±20% 范围内（加法合成可能有轻微偏差）
            assertTrue(
                "${dynamic.italianName} peak=$peak 不接近 amplitude=${dynamic.amplitude}",
                peak >= dynamic.amplitude * 0.5f
            )
        }
    }

    // ── onset 时间 ────────────────────────────────────

    @Test
    fun `computeOnsetTimes首音在LEAD_SILENCE`() {
        val onsets = builder.computeOnsetTimes(noteCount = 4)
        assertEquals(DynamicsTrainingAudioBuilder.LEAD_SILENCE_MS, onsets[0], 0.01)
    }

    @Test
    fun `computeOnsetTimes数量等于noteCount`() {
        for (count in listOf(1, 2, 4, 6)) {
            val onsets = builder.computeOnsetTimes(noteCount = count)
            assertEquals(count, onsets.size)
        }
    }

    @Test
    fun `computeOnsetTimes间距等于NOTE_DURATION`() {
        val onsets = builder.computeOnsetTimes(noteCount = 4)
        for (i in 1 until onsets.size) {
            assertEquals(
                DynamicsTrainingAudioBuilder.NOTE_DURATION_MS,
                onsets[i] - onsets[i - 1],
                0.01
            )
        }
    }

    // ── 时长预估 ──────────────────────────────────────

    @Test
    fun `estimateDurationMs为正数`() {
        val question = DynamicsTrainingQuestion(
            dynamic = DynamicLevel.FORTE,
            difficulty = DynamicsTrainingDifficulty.ADVANCED,
            noteCount = 4,
            answerChoices = listOf("f  强"),
            correctAnswer = "f  强"
        )
        val duration = builder.estimateDurationMs(question)
        assertTrue(duration > 0)
    }

    @Test
    fun `estimateDurationMs随noteCount增加`() {
        val q2 = DynamicsTrainingQuestion(
            dynamic = DynamicLevel.PIANO, difficulty = DynamicsTrainingDifficulty.BEGINNER,
            noteCount = 2, answerChoices = emptyList(), correctAnswer = ""
        )
        val q6 = DynamicsTrainingQuestion(
            dynamic = DynamicLevel.PIANO, difficulty = DynamicsTrainingDifficulty.BEGINNER,
            noteCount = 6, answerChoices = emptyList(), correctAnswer = ""
        )
        assertTrue(builder.estimateDurationMs(q6) > builder.estimateDurationMs(q2))
    }

    // ── 边界情况 ──────────────────────────────────────

    @Test
    fun `noteCount为零返回空数组`() {
        val pcm = builder.renderDynamic(DynamicLevel.FORTE, noteCount = 0)
        assertEquals(0, pcm.size)
    }

    @Test
    fun `所有力度级别采样在有效范围`() {
        for (dynamic in DynamicLevel.ALL) {
            val pcm = builder.renderDynamic(dynamic, noteCount = 4)
            for (sample in pcm) {
                assertTrue(sample in -1.0f..1.0f)
            }
        }
    }

    // ── 辅助函数 ──────────────────────────────────────

    private fun computeRms(pcm: FloatArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sumSq = 0.0
        for (s in pcm) {
            sumSq += s.toDouble() * s
        }
        return sqrt(sumSq / pcm.size)
    }

    private fun computePeak(pcm: FloatArray): Float {
        var peak = 0.0f
        for (s in pcm) {
            val absVal = abs(s)
            if (absVal > peak) peak = absVal
        }
        return peak
    }
}
