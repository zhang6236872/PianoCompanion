package com.pianocompanion.harmonicseries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泛音列辨识训练出题引擎单元测试。
 */
class HarmonicSeriesEngineTest {

    // ── 选项正确性 ──────────────────────────────────

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = HarmonicSeriesEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(HarmonicDifficulty.BEGINNER)
            assertEquals(HarmonicDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = HarmonicSeriesEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(HarmonicDifficulty.INTERMEDIATE)
            assertEquals(HarmonicDifficulty.INTERMEDIATE.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 4 choices`() {
        val engine = HarmonicSeriesEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(HarmonicDifficulty.ADVANCED)
            assertEquals(HarmonicDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = HarmonicSeriesEngine.withSeed(3)
        HarmonicDifficulty.ALL.forEach { difficulty ->
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
        val engine = HarmonicSeriesEngine.withSeed(13)
        HarmonicDifficulty.ALL.forEach { difficulty ->
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
        val engine1 = HarmonicSeriesEngine.withSeed(100)
        val engine2 = HarmonicSeriesEngine.withSeed(100)
        val q1 = engine1.generate(HarmonicDifficulty.ADVANCED)
        val q2 = engine2.generate(HarmonicDifficulty.ADVANCED)
        assertEquals(q1.targetHarmonic, q2.targetHarmonic)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds produce different sequences eventually`() {
        val engine1 = HarmonicSeriesEngine.withSeed(1)
        val engine2 = HarmonicSeriesEngine.withSeed(999)
        val targets1 = (1..50).map { engine1.generate(HarmonicDifficulty.ADVANCED).targetHarmonic }
        val targets2 = (1..50).map { engine2.generate(HarmonicDifficulty.ADVANCED).targetHarmonic }
        assertNotEquals(targets1, targets2)
    }

    // ── 难度缩放 ──────────────────────────────────

    @Test
    fun `beginner only uses harmonics 2 and 3`() {
        val engine = HarmonicSeriesEngine.withSeed(55)
        repeat(30) {
            val q = engine.generate(HarmonicDifficulty.BEGINNER)
            assertTrue(
                "Beginner should only use 2nd/3rd harmonics, got ${q.targetHarmonic}",
                q.targetHarmonic in HarmonicDifficulty.BEGINNER.harmonics
            )
            // 所有选项也应该在该难度的泛音集合中
            q.answerChoices.forEach { choice ->
                val matchingHarmonic = HarmonicNumber.entries.find { it.displayName == choice }
                assertTrue(
                    "Choice '$choice' not in beginner harmonics",
                    matchingHarmonic != null && matchingHarmonic in HarmonicDifficulty.BEGINNER.harmonics
                )
            }
        }
    }

    @Test
    fun `intermediate uses harmonics 2 to 5`() {
        val engine = HarmonicSeriesEngine.withSeed(88)
        repeat(30) {
            val q = engine.generate(HarmonicDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate should only use 2nd-5th harmonics, got ${q.targetHarmonic}",
                q.targetHarmonic in HarmonicDifficulty.INTERMEDIATE.harmonics
            )
        }
    }

    @Test
    fun `advanced uses all harmonics 2 to 8`() {
        val engine = HarmonicSeriesEngine.withSeed(77)
        repeat(50) {
            val q = engine.generate(HarmonicDifficulty.ADVANCED)
            assertTrue(
                "Advanced should only use 2nd-8th harmonics, got ${q.targetHarmonic}",
                q.targetHarmonic in HarmonicDifficulty.ADVANCED.harmonics
            )
        }
    }

    // ── 目标泛音覆盖 ──────────────────────────────────

    @Test
    fun `advanced covers all 7 harmonic numbers over 500 seeds`() {
        val seen = mutableSetOf<HarmonicNumber>()
        for (seed in 0L..499L) {
            val engine = HarmonicSeriesEngine.withSeed(seed)
            val q = engine.generate(HarmonicDifficulty.ADVANCED)
            seen.add(q.targetHarmonic)
        }
        assertEquals(
            "Advanced should cover all 7 harmonic numbers (2-8)",
            HarmonicNumber.entries.toSet(),
            seen
        )
    }

    @Test
    fun `beginner covers both harmonic 2 and 3 over 200 seeds`() {
        val seen = mutableSetOf<HarmonicNumber>()
        for (seed in 0L..199L) {
            val engine = HarmonicSeriesEngine.withSeed(seed)
            val q = engine.generate(HarmonicDifficulty.BEGINNER)
            seen.add(q.targetHarmonic)
        }
        assertEquals(
            "Beginner should cover both 2nd and 3rd harmonics",
            HarmonicNumber.BEGINNER_HARMONICS.toSet(),
            seen
        )
    }

    // ── 选项内容验证 ──────────────────────────────────

    @Test
    fun `all choices are valid harmonic display names`() {
        val engine = HarmonicSeriesEngine.withSeed(123)
        HarmonicDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue(
                        "Choice '$choice' is not a valid harmonic display name",
                        HarmonicNumber.entries.any { it.displayName == choice }
                    )
                }
            }
        }
    }

    @Test
    fun `target harmonic display name equals correct answer`() {
        val engine = HarmonicSeriesEngine.withSeed(456)
        repeat(20) {
            val q = engine.generate(HarmonicDifficulty.ADVANCED)
            assertEquals(q.targetHarmonic.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `question fundamental midi matches difficulty`() {
        val engine = HarmonicSeriesEngine.withSeed(789)
        HarmonicDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.fundamentalMidi, q.fundamentalMidi)
        }
    }
}
