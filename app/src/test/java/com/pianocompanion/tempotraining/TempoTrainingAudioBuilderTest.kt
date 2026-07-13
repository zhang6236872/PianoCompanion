package com.pianocompanion.tempotraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 速度辨识训练音频构建器单元测试。
 */
class TempoTrainingAudioBuilderTest {

    private val builder = TempoTrainingAudioBuilder()

    // ── 基础渲染 ────────────────────────────────────────

    @Test
    fun `渲染非空`() {
        val q = TempoTrainingQuestion(
            tempo = TempoCategory.MODERATO,
            difficulty = TempoTrainingDifficulty.BEGINNER,
            clickCount = 8,
            answerChoices = listOf(),
            correctAnswer = ""
        )
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `renderTempo返回非空`() {
        val audio = builder.renderTempo(TempoCategory.ALLEGRO, clickCount = 8)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `零clickCount返回空数组`() {
        val audio = builder.renderTempo(TempoCategory.MODERATO, clickCount = 0)
        assertTrue(audio.isEmpty())
    }

    @Test
    fun `所有采样在-1到1之间`() {
        val audio = builder.renderTempo(TempoCategory.PRESTO, clickCount = 16)
        for (sample in audio) {
            assertTrue("采样 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `慢速和快速都满足范围`() {
        for (tempo in TempoCategory.ALL) {
            val audio = builder.renderTempo(tempo, clickCount = 8)
            for (sample in audio) {
                assertTrue(sample in -1.0f..1.0f)
            }
        }
    }

    // ── 采样率与长度 ────────────────────────────────────

    @Test
    fun `采样率正确`() {
        val builder2 = TempoTrainingAudioBuilder(sampleRate = 22050)
        val audio = builder2.renderTempo(TempoCategory.MODERATO, clickCount = 4)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `PCM长度合理`() {
        val audio = builder.renderTempo(TempoCategory.MODERATO, clickCount = 8)
        // MODERATO 120 BPM → 500ms 间距, 8 clicks
        // 最后 onset = 400 + 7*500 = 3900ms
        // 预期长度 ≈ (3900 + 90 + 400)ms * 44100/1000 ≈ ~193000 samples
        assertTrue("PCM 长度 ${audio.size} 过短", audio.size > 100000)
    }

    @Test
    fun `慢速PCM长于快速`() {
        val slowAudio = builder.renderTempo(TempoCategory.LARGO, clickCount = 8)
        val fastAudio = builder.renderTempo(TempoCategory.PRESTO, clickCount = 8)
        assertTrue(
            "广板 PCM(${slowAudio.size}) 应长于 急板 PCM(${fastAudio.size})",
            slowAudio.size > fastAudio.size
        )
    }

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同参数渲染结果相同`() {
        val audio1 = builder.renderTempo(TempoCategory.ANDANTE, clickCount = 8)
        val audio2 = builder.renderTempo(TempoCategory.ANDANTE, clickCount = 8)
        assertTrue(audio1.contentEquals(audio2))
    }

    @Test
    fun `不同速度渲染结果不同`() {
        val audio1 = builder.renderTempo(TempoCategory.LARGO, clickCount = 8)
        val audio2 = builder.renderTempo(TempoCategory.ALLEGRO, clickCount = 8)
        assertFalse(audio1.contentEquals(audio2))
    }

    // ── onset 计算 ─────────────────────────────────────

    @Test
    fun `computeOnsetTimes首拍在LEAD_SILENCE`() {
        val onsets = builder.computeOnsetTimes(TempoCategory.MODERATO, clickCount = 8)
        assertEquals(TempoTrainingAudioBuilder.LEAD_SILENCE_MS, onsets[0], 0.01)
    }

    @Test
    fun `computeOnsetTimes数量等于clickCount`() {
        for (count in listOf(1, 4, 8, 16)) {
            val onsets = builder.computeOnsetTimes(TempoCategory.MODERATO, clickCount = count)
            assertEquals(count, onsets.size)
        }
    }

    @Test
    fun `computeOnsetTimes间距正确`() {
        val onsets = builder.computeOnsetTimes(TempoCategory.PRESTO, clickCount = 8)
        val expectedInterval = TempoCategory.PRESTO.intervalMs
        for (i in 1 until onsets.size) {
            assertEquals(expectedInterval, onsets[i] - onsets[i - 1], 0.01)
        }
    }

    @Test
    fun `computeOnsetTimes与Engine一致`() {
        val engine = TempoTrainingEngine()
        val builderOnsets = builder.computeOnsetTimes(TempoCategory.ALLEGRO, clickCount = 8)
        val engineOnsets = engine.computeOnsetTimes(TempoCategory.ALLEGRO, clickCount = 8)
        assertEquals(engineOnsets.size, builderOnsets.size)
        for (i in builderOnsets.indices) {
            assertEquals(engineOnsets[i], builderOnsets[i], 0.01)
        }
    }

    // ── 时长预估 ────────────────────────────────────────

    @Test
    fun `estimateDurationMs合理`() {
        val q = TempoTrainingQuestion(
            tempo = TempoCategory.MODERATO,
            difficulty = TempoTrainingDifficulty.BEGINNER,
            clickCount = 8,
            answerChoices = listOf(),
            correctAnswer = ""
        )
        val duration = builder.estimateDurationMs(q)
        // 最后 onset = 400 + 7*500 = 3900ms, + 90 + 400 = 4390ms
        assertTrue("预估时长 ${duration}ms 过短", duration > 3000)
        assertTrue("预估时长 ${duration}ms 过长", duration < 6000)
    }

    @Test
    fun `estimateDurationMs空题目返回0`() {
        val q = TempoTrainingQuestion(
            tempo = TempoCategory.MODERATO,
            difficulty = TempoTrainingDifficulty.BEGINNER,
            clickCount = 0,
            answerChoices = listOf(),
            correctAnswer = ""
        )
        assertEquals(0L, builder.estimateDurationMs(q))
    }

    // ── click 波形质量 ─────────────────────────────────

    @Test
    fun `存在非零采样（有click声音）`() {
        val audio = builder.renderTempo(TempoCategory.MODERATO, clickCount = 8)
        val nonZeroCount = audio.count { it != 0.0f }
        assertTrue("非零采样数 $nonZeroCount 过少", nonZeroCount > 1000)
    }

    @Test
    fun `前导静音区域接近零`() {
        val audio = builder.renderTempo(TempoCategory.MODERATO, clickCount = 8)
        val leadSamples = (TempoTrainingAudioBuilder.LEAD_SILENCE_MS * TempoTrainingAudioBuilder.DEFAULT_SAMPLE_RATE / 1000.0).toInt()
        // 前 LEAD_SILENCE 区域应全为 0
        for (i in 0 until leadSamples) {
            assertEquals("前导静音区域采样 $i 不为零: ${audio[i]}", 0.0f, audio[i], 0.001f)
        }
    }

    @Test
    fun `每个click位置有能量`() {
        val audio = builder.renderTempo(TempoCategory.MODERATO, clickCount = 8)
        val onsets = builder.computeOnsetTimes(TempoCategory.MODERATO, clickCount = 8)
        val sr = TempoTrainingAudioBuilder.DEFAULT_SAMPLE_RATE
        for (onset in onsets) {
            val sampleIdx = (onset * sr / 1000.0).toInt()
            // onset 附近的采样应有非零值
            var foundNonZero = false
            for (offset in 0 until 50) {
                val idx = sampleIdx + offset
                if (idx in audio.indices && audio[idx] != 0.0f) {
                    foundNonZero = true
                    break
                }
            }
            assertTrue("onset ${onset}ms 附近无能量", foundNonZero)
        }
    }

    @Test
    fun `不同clickCount产生不同长度`() {
        val a4 = builder.renderTempo(TempoCategory.ALLEGRO, clickCount = 4)
        val a8 = builder.renderTempo(TempoCategory.ALLEGRO, clickCount = 8)
        assertTrue(a8.size > a4.size)
    }

    // ── 所有速度渲染不崩溃 ────────────────────────────────

    @Test
    fun `所有速度类型渲染成功`() {
        for (tempo in TempoCategory.ALL) {
            val audio = builder.renderTempo(tempo, clickCount = 8)
            assertTrue("${tempo.italianName} 渲染为空", audio.isNotEmpty())
        }
    }

    @Test
    fun `单个click渲染成功`() {
        for (tempo in TempoCategory.ALL) {
            val audio = builder.renderTempo(tempo, clickCount = 1)
            assertTrue(audio.isNotEmpty())
        }
    }
}
