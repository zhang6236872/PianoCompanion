package com.pianocompanion.sequencetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模进辨识训练音频构建器单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：PCM 采样有效性、采样值范围 [-1,1]、旋律渲染、序列时长估算、
 * 空输入处理、音域钳制、不同 MIDI 渲染。
 */
class SequenceTrainingAudioBuilderTest {

    private val builder = SequenceTrainingAudioBuilder()

    // ── 基础有效性 ──────────────────────────────────────────

    @Test
    fun `render produces non-empty pcm buffer`() {
        val q = SequenceTrainingEngine.withSeed(1L).generate(SequenceDifficulty.ADVANCED)
        val pcm = builder.render(q)
        assertTrue("PCM 缓冲区不应为空", pcm.isNotEmpty())
    }

    @Test
    fun `render samples are within valid range`() {
        SequenceDifficulty.ALL.forEach { difficulty ->
            val q = SequenceTrainingEngine.withSeed(2L).generate(difficulty)
            val pcm = builder.render(q)
            pcm.forEach { sample ->
                assertTrue(
                    "采样值 $sample 超出 [-1,1] 范围",
                    sample in -1.0f..1.0f
                )
            }
        }
    }

    @Test
    fun `render buffer length matches expected duration`() {
        val q = SequenceTrainingEngine.withSeed(3L).generate(SequenceDifficulty.ADVANCED)
        val pcm = builder.render(q)
        val expectedMs = SequenceTrainingAudioBuilder.LEAD_SILENCE_MS +
            q.sequenceDurationMs + SequenceTrainingAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (SequenceTrainingAudioBuilder.SAMPLE_RATE * expectedMs / 1000.0).toInt()
        // 允许小误差（合成器时长计算）
        val diff = kotlin.math.abs(pcm.size - expectedSamples)
        assertTrue(
            "缓冲区长度 ${pcm.size} 应接近预期 $expectedSamples（误差 $diff）",
            diff < expectedSamples * 0.15
        )
    }

    // ── 各构造类型渲染 ────────────────────────────────────────

    @Test
    fun `render each sequence type produces non-silent audio`() {
        SequenceType.ALL.forEach { type ->
            // 用对应难度生成题目以触发各类型
            val difficulties = when (type) {
                SequenceType.ASCENDING, SequenceType.DESCENDING -> SequenceDifficulty.ALL
                SequenceType.FREE -> listOf(SequenceDifficulty.INTERMEDIATE, SequenceDifficulty.ADVANCED)
                SequenceType.REPETITION -> listOf(SequenceDifficulty.ADVANCED)
            }
            var rendered = false
            for (d in difficulties) {
                for (seed in 1L..40L) {
                    val q = SequenceTrainingEngine.withSeed(seed).generate(d)
                    if (q.type == type) {
                        val pcm = builder.render(q)
                        assertTrue("${type.displayName} 的 PCM 不应为空", pcm.isNotEmpty())
                        val nonZero = pcm.count { kotlin.math.abs(it) > 0.001f }
                        assertTrue(
                            "${type.displayName} 应有非零音频采样，实际 $nonZero 个",
                            nonZero > pcm.size / 10
                        )
                        rendered = true
                        break
                    }
                }
                if (rendered) break
            }
            assertTrue("${type.displayName} 至少应被渲染一次", rendered)
        }
    }

    @Test
    fun `longer melody produces longer buffer`() {
        val shortMelody = listOf(60, 62, 64)
        val longMelody = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74)
        val shortPcm = builder.renderMelody(shortMelody, 200)
        val longPcm = builder.renderMelody(longMelody, 200)
        assertTrue(
            "更长旋律应产生更长缓冲区: ${longPcm.size} > ${shortPcm.size}",
            longPcm.size > shortPcm.size
        )
    }

    // ── 空输入 ──────────────────────────────────────────────

    @Test
    fun `render empty melody returns empty buffer`() {
        val pcm = builder.renderMelody(emptyList(), 200)
        assertEquals(0, pcm.size)
    }

    // ── 时长估算 ──────────────────────────────────────────

    @Test
    fun `estimate duration includes lead silence and tail`() {
        val q = SequenceTrainingEngine.withSeed(4L).generate(SequenceDifficulty.ADVANCED)
        val estimate = builder.estimateDurationMs(q)
        val expected = SequenceTrainingAudioBuilder.LEAD_SILENCE_MS +
            q.sequenceDurationMs + SequenceTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimate)
    }

    // ── 音域钳制 ──────────────────────────────────────────

    @Test
    fun `extreme midi values clamped without crashing`() {
        // 极端 MIDI 应被钳制到钢琴范围而不崩溃
        val pcm = builder.renderMelody(listOf(0, 21, 108, 127), 100)
        assertTrue(pcm.isNotEmpty())
        pcm.forEach { assertTrue(it in -1.0f..1.0f) }
    }

    @Test
    fun `render with different start notes produces valid audio`() {
        listOf(60, 62, 64, 65, 67).forEach { start ->
            val melody = listOf(start, start + 2, start + 4, start + 5, start + 7)
            val pcm = builder.renderMelody(melody, 200)
            assertTrue("起始音 $start 的旋律应能正常渲染", pcm.isNotEmpty())
            pcm.forEach { assertTrue(it in -1.0f..1.0f) }
        }
    }

    // ── 力度 ─────────────────────────────────────────────

    @Test
    fun `higher velocity produces louder or equal peak`() {
        val melody = listOf(60, 64, 67)
        val softPcm = builder.renderMelody(melody, 200, velocity = 40)
        val loudPcm = builder.renderMelody(melody, 200, velocity = 110)
        val softPeak = softPcm.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val loudPeak = loudPcm.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        assertTrue("强力度峰值($loudPeak)应不低于弱力度($softPeak)", loudPeak >= softPeak * 0.9f)
        assertTrue(loudPeak > 0f)
    }

    @Test
    fun `single note melody renders valid audio`() {
        val pcm = builder.renderMelody(listOf(60), 300)
        assertTrue("单音旋律应能渲染", pcm.isNotEmpty())
        val nonZero = pcm.count { kotlin.math.abs(it) > 0.001f }
        assertTrue("单音旋律应有非零采样", nonZero > 0)
    }
}
