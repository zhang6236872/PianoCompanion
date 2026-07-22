package com.pianocompanion.modescale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调式音阶色彩对比训练出题引擎单元测试。
 */
class ModeScaleEngineTest {

    // ── 选项正确性 ──────────────────────────────────

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = ModeScaleEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(ModeScaleDifficulty.BEGINNER)
            assertEquals(ModeScaleDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 4 choices`() {
        val engine = ModeScaleEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(ModeScaleDifficulty.INTERMEDIATE)
            assertEquals(ModeScaleDifficulty.INTERMEDIATE.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 7 choices`() {
        val engine = ModeScaleEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(ModeScaleDifficulty.ADVANCED)
            assertEquals(ModeScaleDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = ModeScaleEngine.withSeed(3)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Correct answer not in choices: ${q.correctAnswer}",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `choices contain no duplicates`() {
        val engine = ModeScaleEngine.withSeed(13)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "Duplicate choices found: ${q.answerChoices}",
                    q.answerChoices.size,
                    q.answerChoices.distinct().size
                )
            }
        }
    }

    // ── 确定性种子 ──────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val engine1 = ModeScaleEngine.withSeed(100)
        val engine2 = ModeScaleEngine.withSeed(100)
        val q1 = engine1.generate(ModeScaleDifficulty.ADVANCED)
        val q2 = engine2.generate(ModeScaleDifficulty.ADVANCED)
        assertEquals(q1.targetMode, q2.targetMode)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds produce different sequences eventually`() {
        val engine1 = ModeScaleEngine.withSeed(1)
        val engine2 = ModeScaleEngine.withSeed(999)
        val targets1 = (1..50).map { engine1.generate(ModeScaleDifficulty.ADVANCED).targetMode }
        val targets2 = (1..50).map { engine2.generate(ModeScaleDifficulty.ADVANCED).targetMode }
        assertNotEquals(targets1, targets2)
    }

    // ── 难度缩放 ──────────────────────────────────

    @Test
    fun `beginner only uses ionian and aeolian`() {
        val engine = ModeScaleEngine.withSeed(55)
        repeat(30) {
            val q = engine.generate(ModeScaleDifficulty.BEGINNER)
            assertTrue(
                "Beginner should only use Ionian/Aeolian, got ${q.targetMode}",
                q.targetMode in ModeScaleDifficulty.BEGINNER.modes
            )
            // 所有选项也应该在该难度的调式集合中
            q.answerChoices.forEach { choice ->
                val matchingMode = ModeType.entries.find { it.displayName == choice }
                assertTrue(
                    "Choice '$choice' not in beginner modes",
                    matchingMode != null && matchingMode in ModeScaleDifficulty.BEGINNER.modes
                )
            }
        }
    }

    @Test
    fun `intermediate uses 4 modes`() {
        val engine = ModeScaleEngine.withSeed(88)
        repeat(30) {
            val q = engine.generate(ModeScaleDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate should only use Ionian/Aeolian/Dorian/Mixolydian, got ${q.targetMode}",
                q.targetMode in ModeScaleDifficulty.INTERMEDIATE.modes
            )
        }
    }

    @Test
    fun `advanced uses all 7 modes`() {
        val engine = ModeScaleEngine.withSeed(77)
        repeat(50) {
            val q = engine.generate(ModeScaleDifficulty.ADVANCED)
            assertTrue(
                "Advanced should only use all 7 modes, got ${q.targetMode}",
                q.targetMode in ModeScaleDifficulty.ADVANCED.modes
            )
        }
    }

    // ── 目标调式覆盖 ──────────────────────────────────

    @Test
    fun `advanced covers all 7 mode types over 500 seeds`() {
        val seen = mutableSetOf<ModeType>()
        for (seed in 0L..499L) {
            val engine = ModeScaleEngine.withSeed(seed)
            val q = engine.generate(ModeScaleDifficulty.ADVANCED)
            seen.add(q.targetMode)
        }
        assertEquals(
            "Advanced should cover all 7 mode types",
            ModeType.entries.toSet(),
            seen
        )
    }

    @Test
    fun `beginner covers both ionian and aeolian over 200 seeds`() {
        val seen = mutableSetOf<ModeType>()
        for (seed in 0L..199L) {
            val engine = ModeScaleEngine.withSeed(seed)
            val q = engine.generate(ModeScaleDifficulty.BEGINNER)
            seen.add(q.targetMode)
        }
        assertEquals(
            "Beginner should cover both Ionian and Aeolian",
            ModeType.BEGINNER_MODES.toSet(),
            seen
        )
    }

    // ── 选项内容验证 ──────────────────────────────────

    @Test
    fun `all choices are valid mode display names`() {
        val engine = ModeScaleEngine.withSeed(123)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue(
                        "Choice '$choice' is not a valid mode display name",
                        ModeType.entries.any { it.displayName == choice }
                    )
                }
            }
        }
    }

    @Test
    fun `target mode display name equals correct answer`() {
        val engine = ModeScaleEngine.withSeed(456)
        repeat(20) {
            val q = engine.generate(ModeScaleDifficulty.ADVANCED)
            assertEquals(q.targetMode.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `question tonic midi matches difficulty`() {
        val engine = ModeScaleEngine.withSeed(789)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.tonicMidi, q.tonicMidi)
        }
    }
}
