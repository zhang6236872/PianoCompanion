package com.pianocompanion.contrapuntalmotiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 声部运动辨识训练音频构建器单元测试。
 *
 * 验证 PCM 缓冲区有效性、采样范围、运动类型音频区分度、声部序列正确性。
 */
class ContrapuntalMotionAudioBuilderTest {

    private val builder = ContrapuntalMotionAudioBuilder()

    // ── 缓冲区有效性 ──────────────────────────────────────

    @Test
    fun `所有运动类型生成非空缓冲区`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            val audio = renderMotion(motion)
            assertTrue("$motion 缓冲区不应为空", audio.isNotEmpty())
        }
    }

    @Test
    fun `缓冲区长度合理（至少 4 个音符的时长）`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            val audio = renderMotion(motion)
            val minExpected = (44100 * (400 + 4 * 500 + 300) / 1000).toInt()
            assertTrue(
                "$motion 缓冲区长度 ${audio.size} 应 >= $minExpected",
                audio.size >= minExpected
            )
        }
    }

    @Test
    fun `采样值在有效范围内`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            val audio = renderMotion(motion)
            audio.forEach { sample ->
                assertTrue("$motion 采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `缓冲区前导静音段为 0`() {
        val audio = renderMotion(ContrapuntalMotionType.PARALLEL)
        val leadSamples = (44100 * 400 / 1000.0).toInt()
        for (i in 0 until leadSamples) {
            assertEquals("前导静音段第 $i 个采样应为 0", 0.0f, audio[i], 0.001f)
        }
    }

    @Test
    fun `缓冲区尾部静音段为 0`() {
        val audio = renderMotion(ContrapuntalMotionType.PARALLEL)
        val tailSamples = (44100 * 300 / 1000.0).toInt()
        for (i in audio.size - tailSamples until audio.size) {
            assertEquals("尾部静音段第 $i 个采样应为 0", 0.0f, audio[i], 0.001f)
        }
    }

    // ── MIDI 转频率 ──────────────────────────────────────

    @Test
    fun `midiToFreq A4 等于 440`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.1)
    }

    @Test
    fun `midiToFreq C4 约等于 261_63`() {
        assertEquals(261.63, builder.midiToFreq(60), 0.1)
    }

    @Test
    fun `midiToFreq 隔八度频率翻倍`() {
        val freq1 = builder.midiToFreq(60) // C4
        val freq2 = builder.midiToFreq(72) // C5
        assertEquals(2.0, freq2 / freq1, 0.001)
    }

    // ── 声部序列验证（核心区分度） ──────────────────────────

    @Test
    fun `平行运动上声部单调递增`() {
        val upper = builder.extractUpperVoice(ContrapuntalMotionType.PARALLEL)
        assertEquals(listOf(60, 64, 67, 72), upper)
        // 验证单调递增
        for (i in 1 until upper.size) {
            assertTrue("上声部应递增", upper[i] > upper[i - 1])
        }
    }

    @Test
    fun `平行运动下声部单调递增`() {
        val lower = builder.extractLowerVoice(ContrapuntalMotionType.PARALLEL)
        assertEquals(listOf(48, 52, 55, 60), lower)
        for (i in 1 until lower.size) {
            assertTrue("下声部应递增", lower[i] > lower[i - 1])
        }
    }

    @Test
    fun `平行运动音程间距恒定`() {
        val upper = builder.extractUpperVoice(ContrapuntalMotionType.PARALLEL)
        val lower = builder.extractLowerVoice(ContrapuntalMotionType.PARALLEL)
        val intervals = upper.indices.map { upper[it] - lower[it] }
        // 平行运动：所有间距应相同
        val firstInterval = intervals[0]
        intervals.forEach { interval ->
            assertEquals("平行运动间距应恒定", firstInterval, interval)
        }
        assertEquals(12, firstInterval) // 八度
    }

    @Test
    fun `同向运动上下声部均递增但间距变化`() {
        val upper = builder.extractUpperVoice(ContrapuntalMotionType.SIMILAR)
        val lower = builder.extractLowerVoice(ContrapuntalMotionType.SIMILAR)

        // 都递增
        for (i in 1 until upper.size) {
            assertTrue("同向上声部应递增", upper[i] > upper[i - 1])
            assertTrue("同向下声部应递增", lower[i] > lower[i - 1])
        }

        // 间距变化
        val intervals = upper.indices.map { upper[it] - lower[it] }
        val distinctIntervals = intervals.toSet()
        assertTrue("同向运动间距应有变化", distinctIntervals.size > 1)
    }

    @Test
    fun `反向运动上声部递增下声部递减`() {
        val upper = builder.extractUpperVoice(ContrapuntalMotionType.CONTRARY)
        val lower = builder.extractLowerVoice(ContrapuntalMotionType.CONTRARY)

        // 上声部递增
        for (i in 1 until upper.size) {
            assertTrue("反向上声部应递增", upper[i] > upper[i - 1])
        }
        // 下声部递减
        for (i in 1 until lower.size) {
            assertTrue("反向下声部应递减", lower[i] < lower[i - 1])
        }
    }

    @Test
    fun `斜向运动下声部保持不变`() {
        val lower = builder.extractLowerVoice(ContrapuntalMotionType.OBLIQUE)
        // 下声部全部相同
        val first = lower[0]
        lower.forEach { note ->
            assertEquals("斜向下声部应保持不变", first, note)
        }
    }

    @Test
    fun `斜向运动上声部递增`() {
        val upper = builder.extractUpperVoice(ContrapuntalMotionType.OBLIQUE)
        for (i in 1 until upper.size) {
            assertTrue("斜向上声部应递增", upper[i] > upper[i - 1])
        }
    }

    // ── 运动类型间波形区分度 ──────────────────────────────

    @Test
    fun `不同运动类型产生不同波形`() {
        val parallel = renderMotion(ContrapuntalMotionType.PARALLEL)
        val contrary = renderMotion(ContrapuntalMotionType.CONTRARY)
        val oblique = renderMotion(ContrapuntalMotionType.OBLIQUE)

        // 各自长度相同（都是 4 个音符）
        assertEquals(parallel.size, contrary.size)
        assertEquals(parallel.size, oblique.size)

        // 波形应有差异
        assertNotEquals("平行与反向波形应不同", parallel.toList(), contrary.toList())
        assertNotEquals("平行与斜向波形应不同", parallel.toList(), oblique.toList())
        assertNotEquals("反向与斜向波形应不同", contrary.toList(), oblique.toList())
    }

    @Test
    fun `平行与同向波形不同`() {
        val parallel = renderMotion(ContrapuntalMotionType.PARALLEL)
        val similar = renderMotion(ContrapuntalMotionType.SIMILAR)
        assertNotEquals("平行与同向波形应不同", parallel.toList(), similar.toList())
    }

    @Test
    fun `反向与同向波形不同`() {
        val contrary = renderMotion(ContrapuntalMotionType.CONTRARY)
        val similar = renderMotion(ContrapuntalMotionType.SIMILAR)
        assertNotEquals("反向与同向波形应不同", contrary.toList(), similar.toList())
    }

    // ── renderEvents 空输入 ──────────────────────────────

    @Test
    fun `空事件列表返回空缓冲区`() {
        val result = builder.renderEvents(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildEvents 返回正确事件数`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            val events = builder.buildEvents(motion)
            // 每种运动有 4 个上声部 + 4 个下声部 = 8 个事件
            assertEquals("$motion 应有 8 个音符事件", 8, events.size)
        }
    }

    @Test
    fun `每个音符事件 gain 在有效范围`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            val events = builder.buildEvents(motion)
            events.forEach { event ->
                assertTrue("$motion gain ${event.gain} 应在 (0,1]", event.gain > 0f && event.gain <= 1f)
            }
        }
    }

    // ── 时长预估 ──────────────────────────────────────────

    @Test
    fun `estimateDurationMs 合理`() {
        ContrapuntalMotionType.ALL.forEach { motion ->
            val duration = builder.estimateDurationMs(motion)
            // 4 音符 × 500ms + 400ms 前导 + 300ms 尾部 = 2700ms
            assertEquals("$motion 时长应为 2700ms", 2700L, duration)
        }
    }

    // ── 自定义采样率 ──────────────────────────────────────

    @Test
    fun `自定义采样率影响缓冲区长度`() {
        val sr = 22050
        val lowBuilder = ContrapuntalMotionAudioBuilder(sr)
        val audio = lowBuilder.render(
            ContrapuntalMotionQuestion(
                motion = ContrapuntalMotionType.PARALLEL,
                difficulty = ContrapuntalMotionDifficulty.BEGINNER,
                seed = 1L,
                answerChoices = listOf(),
                correctAnswer = ""
            )
        )
        // 22050 采样率应为 44100 的一半左右
        val fullRate = renderMotion(ContrapuntalMotionType.PARALLEL)
        assertTrue(
            "低采样率缓冲区应明显更短",
            audio.size < fullRate.size
        )
    }

    // ── 辅助方法 ──────────────────────────────────────────

    private fun renderMotion(motion: ContrapuntalMotionType): FloatArray {
        return builder.render(
            ContrapuntalMotionQuestion(
                motion = motion,
                difficulty = ContrapuntalMotionDifficulty.ADVANCED,
                seed = 1L,
                answerChoices = listOf(),
                correctAnswer = ""
            )
        )
    }
}
