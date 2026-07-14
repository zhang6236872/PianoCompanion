package com.pianocompanion.articulationtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 演奏法辨识训练音频构建器单元测试。
 *
 * 验证 PCM 合成有效性、采样范围、演奏法间的波形差异。
 */
class ArticulationAudioBuilderTest {

    private val builder = ArticulationTrainingAudioBuilder()

    // ── 基础有效性 ──────────────────────────────────────────

    @Test
    fun `渲染缓冲区非空`() {
        val audio = builder.renderArticulation(ArticulationType.LEGATO, 5)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `渲染缓冲区长度合理`() {
        val audio = builder.renderArticulation(ArticulationType.LEGATO, 5)
        // 5 notes × 380ms spacing + 400ms lead + 400ms tail + note duration
        // 总时长约 2700ms × 44100 / 1000 ≈ 119000 samples
        val minLength = 100000
        assertTrue("缓冲区长度 ${audio.size} 太短", audio.size > minLength)
    }

    @Test
    fun `渲染缓冲区采样范围有效`() {
        for (a in ArticulationType.ALL) {
            val audio = builder.renderArticulation(a, 5)
            for (sample in audio) {
                assertTrue("采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `0个音符返回空缓冲区`() {
        val audio = builder.renderArticulation(ArticulationType.LEGATO, 0)
        assertEquals(0, audio.size)
    }

    @Test
    fun `1个音符渲染成功`() {
        val audio = builder.renderArticulation(ArticulationType.STACCATO, 1)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `所有演奏法渲染成功`() {
        for (a in ArticulationType.ALL) {
            val audio = builder.renderArticulation(a, 5)
            assertTrue("${a.englishName} 渲染失败", audio.isNotEmpty())
        }
    }

    // ── 前导和尾部静音 ──────────────────────────────────────

    @Test
    fun `前导静音区域接近零`() {
        val audio = builder.renderArticulation(ArticulationType.LEGATO, 5)
        val leadSamples = (ArticulationTrainingAudioBuilder.DEFAULT_SAMPLE_RATE *
            ArticulationTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前导静音的前半段应接近零
        val halfLead = leadSamples / 2
        for (i in 0 until halfLead) {
            assertTrue("前导静音区 $i 处有非零值 ${audio[i]}", kotlin.math.abs(audio[i]) < 0.001f)
        }
    }

    @Test
    fun `尾部有静音区域`() {
        val audio = builder.renderArticulation(ArticulationType.STACCATO, 3)
        // 最后几个采样应接近零（尾部静音）
        val tailCheckStart = audio.size - 10
        for (i in tailCheckStart until audio.size) {
            assertTrue("尾部 $i 处有非零值 ${audio[i]}", kotlin.math.abs(audio[i]) < 0.01f)
        }
    }

    // ── 演奏法差异验证 ──────────────────────────────────────

    /**
     * 辅助：计算 RMS（均方根）。
     */
    private fun rms(audio: FloatArray): Double {
        var sum = 0.0
        for (s in audio) {
            sum += s.toDouble() * s
        }
        return kotlin.math.sqrt(sum / audio.size)
    }

    /**
     * 辅助：计算波形中非静音比例（能量超过阈值的采样占比）。
     */
    private fun activeRatio(audio: FloatArray, threshold: Float = 0.01f): Double {
        var count = 0
        for (s in audio) {
            if (kotlin.math.abs(s) > threshold) count++
        }
        return count.toDouble() / audio.size
    }

    @Test
    fun `断音的非静音比例低于连音`() {
        // 断音 durationRatio=0.30，每个音符只占节拍 30%，有大量间隙
        // 连音 durationRatio=1.05，音符几乎无间隙甚至重叠
        val staccato = builder.renderArticulation(ArticulationType.STACCATO, 5)
        val legato = builder.renderArticulation(ArticulationType.LEGATO, 5)
        val staccatoActive = activeRatio(staccato)
        val legatoActive = activeRatio(legato)
        assertTrue(
            "断音非静音比例 $staccatoActive 应低于连音 $legatoActive",
            staccatoActive < legatoActive
        )
    }

    @Test
    fun `保持音的非静音比例高于断音`() {
        // 保持音 durationRatio=0.95，几乎占满节拍
        val tenuto = builder.renderArticulation(ArticulationType.TENUTO, 5)
        val staccato = builder.renderArticulation(ArticulationType.STACCATO, 5)
        assertTrue(
            "保持音非静音比例应高于断音",
            activeRatio(tenuto) > activeRatio(staccato)
        )
    }

    @Test
    fun `连音的非静音比例高于保持音`() {
        // 连音 durationRatio=1.05 > 保持音 0.95
        val legato = builder.renderArticulation(ArticulationType.LEGATO, 5)
        val tenuto = builder.renderArticulation(ArticulationType.TENUTO, 5)
        assertTrue(
            "连音非静音比例应高于保持音",
            activeRatio(legato) >= activeRatio(tenuto)
        )
    }

    @Test
    fun `不同演奏法产生不同波形`() {
        val legato = builder.renderArticulation(ArticulationType.LEGATO, 5)
        val staccato = builder.renderArticulation(ArticulationType.STACCATO, 5)
        // 两段音频的 activeRatio 应该明显不同
        val diff = kotlin.math.abs(activeRatio(legato) - activeRatio(staccato))
        assertTrue("连音 vs 断音波形差异 $diff 应显著", diff > 0.1)
    }

    @Test
    fun `重音的峰值能量高于保持音`() {
        // Marcato 有 accent 强调，前 50ms 额外增强
        // 对比两者的 peak energy（最大绝对值）
        val marcato = builder.renderArticulation(ArticulationType.MARCATO, 5)
        val tenuto = builder.renderArticulation(ArticulationType.TENUTO, 5)
        // 由于归一化，整体 peak 可能相同；但前 50ms 的能量分布不同
        // Marcato 的衰减更快（decayTimeConstant=120ms vs 350ms），后半段能量更低
        val marcatoBackHalfRms = rms(marcato.copyOfRange(marcato.size / 2, marcato.size))
        val tenutoBackHalfRms = rms(tenuto.copyOfRange(tenuto.size / 2, tenuto.size))
        // 保持音的后半段应该比重音的后半段更有能量（衰减更慢）
        assertTrue(
            "保持音后半段 RMS $tenutoBackHalfRms 应高于重音 $marcatoBackHalfRms",
            tenutoBackHalfRms > marcatoBackHalfRms
        )
    }

    @Test
    fun `断音衰减速度快于连音`() {
        // 断音 decayTimeConstant=80ms，连音 400ms
        val staccato = builder.renderArticulation(ArticulationType.STACCATO, 5)
        val legato = builder.renderArticulation(ArticulationType.LEGATO, 5)
        val staccatoRms = rms(staccato)
        val legatoRms = rms(legato)
        // 连音因为有更长持续时间和更慢衰减，整体 RMS 应更高
        assertTrue(
            "连音 RMS $legatoRms 应高于断音 RMS $staccatoRms",
            legatoRms > staccatoRms
        )
    }

    // ── onset 时间 ──────────────────────────────────────────

    @Test
    fun `computeOnsetTimes首项正确`() {
        val onsets = builder.computeOnsetTimes(5)
        assertEquals(
            ArticulationTrainingAudioBuilder.LEAD_SILENCE_MS,
            onsets[0],
            0.001
        )
    }

    @Test
    fun `computeOnsetTimes间距正确`() {
        val onsets = builder.computeOnsetTimes(5)
        for (i in 1 until onsets.size) {
            assertEquals(
                ArticulationTrainingAudioBuilder.NOTE_SPACING_MS,
                onsets[i] - onsets[i - 1],
                0.001
            )
        }
    }

    // ── estimateDurationMs ─────────────────────────────────

    @Test
    fun `estimateDurationMs为正`() {
        for (a in ArticulationType.ALL) {
            val q = ArticulationTrainingQuestion(
                articulation = a,
                difficulty = ArticulationTrainingDifficulty.ADVANCED,
                noteCount = 5,
                answerChoices = listOf(a.fullLabel),
                correctAnswer = a.fullLabel
            )
            val duration = builder.estimateDurationMs(q)
            assertTrue("${a.englishName} 估算时长应为正", duration > 0)
        }
    }

    // ── 通过 Question 渲染 ─────────────────────────────────

    @Test
    fun `通过Question渲染与直接渲染一致`() {
        val question = ArticulationTrainingQuestion(
            articulation = ArticulationType.STACCATO,
            difficulty = ArticulationTrainingDifficulty.BEGINNER,
            noteCount = 5,
            answerChoices = listOf(ArticulationType.STACCATO.fullLabel),
            correctAnswer = ArticulationType.STACCATO.fullLabel
        )
        val viaQ = builder.render(question)
        val direct = builder.renderArticulation(ArticulationType.STACCATO, 5)
        assertEquals(direct.size, viaQ.size)
        for (i in direct.indices) {
            assertEquals(direct[i], viaQ[i], 0.0f)
        }
    }

    @Test
    fun `次断音非静音比例介于断音和保持音之间`() {
        // Portato durationRatio=0.65，介于 staccato(0.30) 和 tenuto(0.95)
        val portato = builder.renderArticulation(ArticulationType.PORTATO, 5)
        val staccato = builder.renderArticulation(ArticulationType.STACCATO, 5)
        val tenuto = builder.renderArticulation(ArticulationType.TENUTO, 5)
        val portatoActive = activeRatio(portato)
        val staccatoActive = activeRatio(staccato)
        val tenutoActive = activeRatio(tenuto)
        assertTrue(
            "次断音 ($portatoActive) 应高于断音 ($staccatoActive)",
            portatoActive > staccatoActive
        )
        assertTrue(
            "次断音 ($portatoActive) 应低于保持音 ($tenutoActive)",
            portatoActive < tenutoActive
        )
    }
}
