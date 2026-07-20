package com.pianocompanion.polyphonicmotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复调运动辨识训练音频合成器单元测试。
 *
 * 覆盖 [MotionAudioBuilder] 的事件构建、方向序列、频率换算与渲染输出。
 */
class PolyphonicMotionAudioBuilderTest {

    private val builder = MotionAudioBuilder()
    private val engine = MotionEngine.withSeed(42)

    private fun makeQuestion(difficulty: MotionDifficulty = MotionDifficulty.ADVANCED): MotionQuestion =
        engine.generate(difficulty)

    // ── 基本输出 ──────────────────────────────────

    @Test
    fun `render returns non-empty float array`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        assertTrue("PCM must be non-empty", pcm.isNotEmpty())
    }

    @Test
    fun `render output is FloatArray`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        assertTrue("PCM must be FloatArray", pcm is FloatArray)
    }

    @Test
    fun `render is deterministic for same question`() {
        val q = makeQuestion()
        val pcm1 = builder.render(q)
        val pcm2 = builder.render(q)
        assertEquals(pcm1.size, pcm2.size)
        pcm1.indices.forEach { i ->
            assertEquals("Sample $i differs", pcm1[i], pcm2[i], 0.0f)
        }
    }

    @Test
    fun `render values are within normalized range`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        // tanh 限幅后应在 [-1, 1]
        assertTrue(pcm.all { it in -1.0f..1.0f })
    }

    // ── 音符事件构建 ──────────────────────────────────

    @Test
    fun `buildNoteEvents produces two events per beat`() {
        val q = makeQuestion() // noteCount 个节拍点
        val events = builder.buildNoteEvents(q)
        // 每个节拍点：高声部 1 个 + 低声部 1 个 = 2 个事件
        assertEquals(q.noteCount * 2, events.size)
    }

    @Test
    fun `note events have matching onset per beat`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        // 成对：第 0/1 事件同起始、第 2/3 事件同起始 …
        for (i in 0 until events.size step 2) {
            assertEquals(
                "Beat ${i / 2} upper and lower onset must match",
                events[i].onsetMs,
                events[i + 1].onsetMs,
                0.001
            )
        }
    }

    @Test
    fun `note events use correct midi pitches`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        // 偶数索引 = 高声部、奇数索引 = 低声部
        for (i in q.upperVoice.indices) {
            assertEquals(q.upperVoice[i], events[i * 2].midi)
            assertEquals(q.lowerVoice[i], events[i * 2 + 1].midi)
        }
    }

    @Test
    fun `note events duration matches difficulty`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        assertTrue(events.all { it.durationMs == q.difficulty.noteDurationMs.toDouble() })
    }

    // ── 起始时间 ──────────────────────────────────

    @Test
    fun `computeOnsetTimes returns one per note`() {
        val q = makeQuestion()
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(q.noteCount, onsets.size)
    }

    @Test
    fun `first onset is zero`() {
        val q = makeQuestion()
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(0.0, onsets[0], 0.001)
    }

    @Test
    fun `onsets are monotonically increasing`() {
        val q = makeQuestion()
        val onsets = builder.computeOnsetTimes(q)
        for (i in 1 until onsets.size) {
            assertTrue("Onset $i must be after onset ${i - 1}", onsets[i] > onsets[i - 1])
        }
    }

    @Test
    fun `onset spacing equals noteDuration plus gap`() {
        val q = makeQuestion()
        val onsets = builder.computeOnsetTimes(q)
        val expected = q.difficulty.noteDurationMs.toDouble() + MotionAudioBuilder.GAP_MS
        for (i in 1 until onsets.size) {
            assertEquals(expected, onsets[i] - onsets[i - 1], 0.001)
        }
    }

    // ── 方向序列 ──────────────────────────────────

    @Test
    fun `upperDirections length is noteCount minus one`() {
        val q = makeQuestion()
        val dirs = builder.upperDirections(q)
        assertEquals(q.noteCount - 1, dirs.size)
    }

    @Test
    fun `lowerDirections length is noteCount minus one`() {
        val q = makeQuestion()
        val dirs = builder.lowerDirections(q)
        assertEquals(q.noteCount - 1, dirs.size)
    }

    @Test
    fun `directions contain only valid values`() {
        val q = makeQuestion()
        val upper = builder.upperDirections(q)
        val lower = builder.lowerDirections(q)
        assertTrue(upper.all { it in listOf(-1, 0, 1) })
        assertTrue(lower.all { it in listOf(-1, 0, 1) })
    }

    @Test
    fun `parallel question has matching direction signs`() {
        // 找一道同向运动题
        val q = findQuestion(MotionType.PARALLEL)
        val upper = builder.upperDirections(q)
        val lower = builder.lowerDirections(q)
        // 同向：两声部方向一致且均非零
        upper.indices.forEach { i ->
            assertTrue("Parallel: upper[${upper[i]}] and lower[${lower[i]}] must be same sign", upper[i] == lower[i] && upper[i] != 0)
        }
    }

    @Test
    fun `contrary question has opposite direction signs`() {
        val q = findQuestion(MotionType.CONTRARY)
        val upper = builder.upperDirections(q)
        val lower = builder.lowerDirections(q)
        upper.indices.forEach { i ->
            assertTrue("Contrary: upper[${upper[i]}] and lower[${lower[i]}] must be opposite sign", upper[i] == -lower[i] && upper[i] != 0)
        }
    }

    @Test
    fun `oblique question has one voice holding`() {
        val q = findQuestion(MotionType.OBLIQUE)
        val upper = builder.upperDirections(q)
        val lower = builder.lowerDirections(q)
        upper.indices.forEach { i ->
            // 斜向：恰有一个声部为 0
            assertTrue(
                "Oblique: exactly one voice must hold at step $i (u=${upper[i]}, l=${lower[i]})",
                (upper[i] == 0) xor (lower[i] == 0)
            )
        }
    }

    @Test
    fun `oblique holding voice is consistent across steps`() {
        val q = findQuestion(MotionType.OBLIQUE)
        val upper = builder.upperDirections(q)
        val lower = builder.lowerDirections(q)
        val upperHoldsEverywhere = upper.all { it == 0 }
        val lowerHoldsEverywhere = lower.all { it == 0 }
        assertTrue(
            "Oblique: the holding voice must be the same across all steps",
            upperHoldsEverywhere || lowerHoldsEverywhere
        )
    }

    // ── 频率换算 ──────────────────────────────────

    @Test
    fun `midiToFreq A4 equals 440`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.01)
    }

    @Test
    fun `midiToFreq A5 is double A4`() {
        assertEquals(880.0, builder.midiToFreq(81), 0.01)
    }

    @Test
    fun `midiToFreq A3 is half A4`() {
        assertEquals(220.0, builder.midiToFreq(57), 0.01)
    }

    @Test
    fun `noteFrequency matches midiToFreq`() {
        for (midi in 36..96) {
            assertEquals(builder.midiToFreq(midi), builder.noteFrequency(midi), 0.001)
        }
    }

    @Test
    fun `midiToFreq is monotonically increasing`() {
        var prev = builder.midiToFreq(36)
        for (midi in 37..96) {
            val freq = builder.midiToFreq(midi)
            assertTrue("Freq should increase for midi $midi", freq > prev)
            prev = freq
        }
    }

    // ── 时长估算 ──────────────────────────────────

    @Test
    fun `estimateDurationMs is positive`() {
        val q = makeQuestion()
        assertTrue(builder.estimateDurationMs(q) > 0)
    }

    @Test
    fun `estimateDurationMs includes lead and tail silence`() {
        val q = makeQuestion()
        val estimated = builder.estimateDurationMs(q)
        // 至少包含前导 + 尾部静音
        assertTrue(
            "Estimated ${estimated}ms should exceed silence padding",
            estimated > (MotionAudioBuilder.LEAD_SILENCE_MS + MotionAudioBuilder.TAIL_SILENCE_MS).toLong()
        )
    }

    @Test
    fun `estimateDurationMs scales with note count`() {
        // beginner 4 音符 vs advanced 4 音符——音数相同但 noteDurationMs 不同
        val beginner = findQuestion(MotionType.PARALLEL, MotionDifficulty.BEGINNER)
        val advanced = findQuestion(MotionType.PARALLEL, MotionDifficulty.ADVANCED)
        val bDur = builder.estimateDurationMs(beginner)
        val aDur = builder.estimateDurationMs(advanced)
        // beginner 更慢（noteDurationMs 更大）→ 时长更长
        assertTrue("Slower difficulty should estimate longer duration (b=$bDur, a=$aDur)", bDur > aDur)
    }

    // ── 渲染输出特性 ──────────────────────────────────

    @Test
    fun `render produces dynamic range`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        val max = pcm.maxOrNull() ?: 0f
        val min = pcm.minOrNull() ?: 0f
        assertTrue("Should have dynamic range", max - min > 0.1f)
    }

    @Test
    fun `render has silence at start and end`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        val leadSamples = (MotionAudioBuilder.DEFAULT_SAMPLE_RATE * MotionAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前导静音区域应接近 0
        assertTrue("Lead silence region should be near-zero", pcm[0] < 0.01f)
        assertTrue("Lead silence region should be near-zero", pcm[leadSamples / 2] < 0.01f)
    }

    @Test
    fun `render different questions produce different output`() {
        val q1 = engine.generate(MotionDifficulty.ADVANCED)
        val q2 = engine.generate(MotionDifficulty.ADVANCED)
        val pcm1 = builder.render(q1)
        val pcm2 = builder.render(q2)
        // 不同题（种子推进）应产生不同波形
        var anyDiff = false
        val minLen = minOf(pcm1.size, pcm2.size)
        for (i in 0 until minLen) {
            if (pcm1[i] != pcm2[i]) {
                anyDiff = true
                break
            }
        }
        assertTrue("Different questions should produce different PCM", anyDiff || pcm1.size != pcm2.size)
    }

    @Test
    fun `renderEvents of empty list returns empty array`() {
        val pcm = builder.renderEvents(emptyList())
        assertEquals(0, pcm.size)
    }

    @Test
    fun `all motion types render without error`() {
        MotionType.ALL.forEach { mt ->
            val q = findQuestion(mt, MotionDifficulty.ADVANCED)
            val pcm = builder.render(q)
            assertTrue("PCM for $mt must be non-empty", pcm.isNotEmpty())
        }
    }

    @Test
    fun `render respects custom sample rate`() {
        val q = makeQuestion()
        val lowRate = MotionAudioBuilder(sampleRate = 22050)
        val pcm = lowRate.render(q)
        // 低采样率应产生更少采样点
        val highRate = MotionAudioBuilder(sampleRate = 44100)
        val pcmHigh = highRate.render(q)
        assertTrue(
            "Lower sample rate should produce fewer samples (${pcm.size} vs ${pcmHigh.size})",
            pcm.size < pcmHigh.size
        )
    }

    // ── 辅助 ──────────────────────────────────

    /** 生成指定运动类型与难度的题目（多次尝试直到命中）。 */
    private fun findQuestion(
        type: MotionType,
        difficulty: MotionDifficulty = MotionDifficulty.ADVANCED
    ): MotionQuestion {
        require(type in difficulty.motions) { "$type not available in $difficulty" }
        val localEngine = MotionEngine.withSeed(System.nanoTime())
        repeat(500) {
            val q = localEngine.generate(difficulty)
            if (q.motionType == type) return q
        }
        // 确定性兜底：直接构造符合 verifyMotion 的声部
        return constructManualQuestion(type, difficulty)
    }

    private fun constructManualQuestion(type: MotionType, difficulty: MotionDifficulty): MotionQuestion {
        val (upper, lower) = when (type) {
            MotionType.PARALLEL -> listOf(72, 74, 76, 78) to listOf(48, 50, 52, 54)
            MotionType.CONTRARY -> listOf(72, 74, 76, 78) to listOf(54, 52, 50, 48)
            MotionType.OBLIQUE -> listOf(72, 72, 72, 72) to listOf(48, 50, 52, 54)
        }
        return MotionQuestion(
            motionType = type,
            difficulty = difficulty,
            seed = 0L,
            upperVoice = upper,
            lowerVoice = lower,
            answerChoices = difficulty.motions.map { it.fullLabel }.shuffled(),
            correctAnswer = type.fullLabel
        )
    }
}
