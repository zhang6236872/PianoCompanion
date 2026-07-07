package com.pianocompanion.rhythmpattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmPatternEngine] 单元测试。
 *
 * 验证出题引擎：
 * - 正确答案在选项中
 * - 选项数与难度对应
 * - 选项无重复且含正确答案
 * - 难度对应的节奏型集合正确
 * - 速度正确传递
 * - 确定性出题（固定种子复现）
 * - onset 时间计算正确性
 */
class RhythmPatternEngineTest {

    @Test
    fun `correct answer is in choices`() {
        val engine = RhythmPatternEngine.withSeed(42L)
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
    }

    @Test
    fun `beginner difficulty has 4 choices`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.BEGINNER)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `intermediate difficulty has 6 choices`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.INTERMEDIATE)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `advanced difficulty has 8 choices`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        assertEquals(8, q.answerChoices.size)
    }

    @Test
    fun `choices are unique`() {
        val engine = RhythmPatternEngine.withSeed(7L)
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
    }

    @Test
    fun `choices contain only difficulty-appropriate types`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.BEGINNER)
        val allowed = RhythmPatternType.forDifficulty(RhythmDifficulty.BEGINNER).map { it.displayName }.toSet()
        q.answerChoices.forEach { choice ->
            assertTrue("选项 $choice 不属于初级范围", choice in allowed)
        }
    }

    @Test
    fun `forDifficulty beginner has 4 patterns`() {
        val types = RhythmPatternType.forDifficulty(RhythmDifficulty.BEGINNER)
        assertEquals(4, types.size)
        assertTrue(types.contains(RhythmPatternType.QUARTERS))
        assertTrue(types.contains(RhythmPatternType.EIGHTHS))
        assertTrue(types.contains(RhythmPatternType.HALVES))
        assertTrue(types.contains(RhythmPatternType.DOTTED))
    }

    @Test
    fun `forDifficulty intermediate has 6 patterns`() {
        val types = RhythmPatternType.forDifficulty(RhythmDifficulty.INTERMEDIATE)
        assertEquals(6, types.size)
        assertTrue(types.contains(RhythmPatternType.SCOTCH_SNAP))
        assertTrue(types.contains(RhythmPatternType.SYNCOPATION))
    }

    @Test
    fun `forDifficulty advanced has 8 patterns`() {
        val types = RhythmPatternType.forDifficulty(RhythmDifficulty.ADVANCED)
        assertEquals(8, types.size)
        assertTrue(types.contains(RhythmPatternType.TRIPLETS))
        assertTrue(types.contains(RhythmPatternType.MIXED_EIGHTHS))
    }

    @Test
    fun `deterministic generation with same seed`() {
        val e1 = RhythmPatternEngine.withSeed(123L)
        val e2 = RhythmPatternEngine.withSeed(123L)
        val q1 = e1.generate(RhythmDifficulty.ADVANCED)
        val q2 = e2.generate(RhythmDifficulty.ADVANCED)
        assertEquals(q1.type, q2.type)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `tempo is propagated to question`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.INTERMEDIATE, RhythmTempo.FAST)
        assertEquals(RhythmTempo.FAST, q.tempo)
    }

    @Test
    fun `difficulty is propagated to question`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        assertEquals(RhythmDifficulty.ADVANCED, q.difficulty)
    }

    @Test
    fun `repeatCount is propagated to question`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.BEGINNER, repeatCount = 3)
        assertEquals(3, q.repeatCount)
    }

    @Test
    fun `default repeatCount is 2`() {
        val engine = RhythmPatternEngine()
        val q = engine.generate(RhythmDifficulty.BEGINNER)
        assertEquals(2, q.repeatCount)
    }

    // ── onset 时间计算 ──────────────────────────────────

    @Test
    fun `computeOnsetTimes quarters single repeat`() {
        val engine = RhythmPatternEngine()
        // 四分音符 [1,1,1,1]，慢速 80 BPM → beatMs = 750ms
        val onsets = engine.computeOnsetTimes(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        assertEquals(4, onsets.size)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS, onsets[0], 0.01)
        // 各 onset 间距 = 1 beat * 750ms = 750ms
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 750.0, onsets[1], 0.01)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 1500.0, onsets[2], 0.01)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 2250.0, onsets[3], 0.01)
    }

    @Test
    fun `computeOnsetTimes eighths single repeat`() {
        val engine = RhythmPatternEngine()
        // 八分音符 [0.5 ×8]，快速 140 BPM → beatMs = 600000/140 ≈ 428.57ms
        val onsets = engine.computeOnsetTimes(RhythmPatternType.EIGHTHS, RhythmTempo.FAST, repeatCount = 1)
        assertEquals(8, onsets.size)
        val halfBeatMs = RhythmTempo.FAST.beatMs * 0.5
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + halfBeatMs, onsets[1], 0.1)
    }

    @Test
    fun `computeOnsetTimes halves single repeat`() {
        val engine = RhythmPatternEngine()
        // 二分音符 [2,2]，慢速 → 各 onset 间距 = 2 beats
        val onsets = engine.computeOnsetTimes(RhythmPatternType.HALVES, RhythmTempo.SLOW, repeatCount = 1)
        assertEquals(2, onsets.size)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS, onsets[0], 0.01)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 2 * RhythmTempo.SLOW.beatMs, onsets[1], 0.01)
    }

    @Test
    fun `computeOnsetTimes dotted rhythm uneven spacing`() {
        val engine = RhythmPatternEngine()
        // 附点 [1.5, 0.5, 1.5, 0.5] → onset 间距不均匀
        val onsets = engine.computeOnsetTimes(RhythmPatternType.DOTTED, RhythmTempo.SLOW, repeatCount = 1)
        assertEquals(4, onsets.size)
        val beatMs = RhythmTempo.SLOW.beatMs
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 1.5 * beatMs, onsets[1], 0.1)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 2.0 * beatMs, onsets[2], 0.1)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 3.5 * beatMs, onsets[3], 0.1)
    }

    @Test
    fun `computeOnsetTimes scotch snap opposite spacing`() {
        val engine = RhythmPatternEngine()
        // 后附点 [0.5, 1.5, 0.5, 1.5] → 首间距短（0.5）
        val onsets = engine.computeOnsetTimes(RhythmPatternType.SCOTCH_SNAP, RhythmTempo.SLOW, repeatCount = 1)
        assertEquals(4, onsets.size)
        val beatMs = RhythmTempo.SLOW.beatMs
        // 第一间距 = 0.5 beat（短），第二间距 = 1.5 beat（长）
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 0.5 * beatMs, onsets[1], 0.1)
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 2.0 * beatMs, onsets[2], 0.1)
    }

    @Test
    fun `computeOnsetTimes repeat 2 doubles onsets`() {
        val engine = RhythmPatternEngine()
        val onsets1 = engine.computeOnsetTimes(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val onsets2 = engine.computeOnsetTimes(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 2)
        assertEquals(4, onsets1.size)
        assertEquals(8, onsets2.size)
        // 第二遍的第一个 onset = LEAD_SILENCE + measureMs
        val measureMs = 4.0 * RhythmTempo.SLOW.beatMs
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + measureMs, onsets2[4], 0.1)
    }

    @Test
    fun `computeOnsetTimes triplets sum to measure`() {
        val engine = RhythmPatternEngine()
        // 三连音 [2/3 ×6] = 4 beats
        val onsets = engine.computeOnsetTimes(RhythmPatternType.TRIPLETS, RhythmTempo.SLOW, repeatCount = 1)
        assertEquals(6, onsets.size)
        val beatMs = RhythmTempo.SLOW.beatMs
        // 每个间距 = 2/3 beat
        assertEquals(RhythmPatternEngine.LEAD_SILENCE_MS + 2.0 / 3.0 * beatMs, onsets[1], 0.5)
    }

    @Test
    fun `computeOnsetTimes last onset within measure`() {
        val engine = RhythmPatternEngine()
        RhythmPatternType.ALL.forEach { pattern ->
            val onsets = engine.computeOnsetTimes(pattern, RhythmTempo.SLOW, repeatCount = 1)
            val measureMs = 4.0 * RhythmTempo.SLOW.beatMs
            // 最后一个 onset 应在 LEAD_SILENCE + (measureMs - last_duration * beatMs) 附近
            assertTrue("$pattern 最后 onset 应小于一小节末尾", onsets.last() < RhythmPatternEngine.LEAD_SILENCE_MS + measureMs)
        }
    }

    // ── 节奏型不变量 ──────────────────────────────────

    @Test
    fun `all pattern types sum to 4 beats`() {
        RhythmPatternType.ALL.forEach { pattern ->
            assertEquals("${pattern.displayName} 时值之和应 = 4.0", 4.0, pattern.totalBeats, 0.01)
        }
    }

    @Test
    fun `noteCount matches durations size`() {
        RhythmPatternType.ALL.forEach { pattern ->
            assertEquals(pattern.durations.size, pattern.noteCount)
        }
    }

    @Test
    fun `all 8 pattern types exist`() {
        assertEquals(8, RhythmPatternType.ALL.size)
    }

    @Test
    fun `all difficulties has 3 entries`() {
        assertEquals(3, RhythmDifficulty.ALL.size)
    }

    @Test
    fun `all tempos has 2 entries`() {
        assertEquals(2, RhythmTempo.ALL.size)
    }

    @Test
    fun `tempo beatMs is correct`() {
        // 80 BPM → 750ms/beat
        assertEquals(750.0, RhythmTempo.SLOW.beatMs, 0.01)
        // 140 BPM → 600000/140 ≈ 428.57ms/beat
        assertEquals(428.571, RhythmTempo.FAST.beatMs, 0.1)
    }

    @Test
    fun `fullName contains tempo and type`() {
        val engine = RhythmPatternEngine.withSeed(99L)
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        assertTrue(q.fullName.contains(q.type.displayName))
        assertTrue(q.fullName.contains(q.tempo.displayName))
    }

    @Test
    fun `dotted and scotch snap have reversed first gap`() {
        // 附点 [1.5, 0.5, ...] 首间距长；后附点 [0.5, 1.5, ...] 首间距短
        assertEquals(1.5, RhythmPatternType.DOTTED.durations[0], 0.001)
        assertEquals(0.5, RhythmPatternType.DOTTED.durations[1], 0.001)
        assertEquals(0.5, RhythmPatternType.SCOTCH_SNAP.durations[0], 0.001)
        assertEquals(1.5, RhythmPatternType.SCOTCH_SNAP.durations[1], 0.001)
    }

    @Test
    fun `quarters and eighths have even spacing`() {
        // 四分音符全为 1.0，八分音符全为 0.5
        RhythmPatternType.QUARTERS.durations.forEach { assertEquals(1.0, it, 0.001) }
        RhythmPatternType.EIGHTHS.durations.forEach { assertEquals(0.5, it, 0.001) }
    }

    @Test
    fun `syncopation has 6 notes`() {
        assertEquals(6, RhythmPatternType.SYNCOPATION.noteCount)
    }

    @Test
    fun `triplets has 6 notes`() {
        assertEquals(6, RhythmPatternType.TRIPLETS.noteCount)
    }

    @Test
    fun `different seeds produce valid questions`() {
        val e1 = RhythmPatternEngine.withSeed(1L)
        val e2 = RhythmPatternEngine.withSeed(2L)
        val q1 = e1.generate(RhythmDifficulty.ADVANCED)
        val q2 = e2.generate(RhythmDifficulty.ADVANCED)
        assertTrue(q1.correctAnswer in q1.answerChoices)
        assertTrue(q2.correctAnswer in q2.answerChoices)
    }
}
