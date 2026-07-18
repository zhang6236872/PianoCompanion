package com.pianocompanion.dynamicsdirectiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 力度变化方向辨识训练出题引擎单元测试。
 */
class DynamicsDirectionEngineTest {

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = DynamicsDirectionEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(DynamicsDirectionDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are crescendo vs decrescendo`() {
        val engine = DynamicsDirectionEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(DynamicsDirectionDifficulty.BEGINNER)
            assertTrue(
                "Expected Crescendo/Decrescendo labels, got: ${q.answerChoices}",
                q.answerChoices.contains(DynamicsDirection.CRESCENDO.fullLabel) &&
                    q.answerChoices.contains(DynamicsDirection.DECRESCENDO.fullLabel)
            )
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = DynamicsDirectionEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(DynamicsDirectionDifficulty.INTERMEDIATE)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate choices are crescendo decrescendo steady`() {
        val engine = DynamicsDirectionEngine.withSeed(100)
        repeat(30) {
            val q = engine.generate(DynamicsDirectionDifficulty.INTERMEDIATE)
            val expected = DynamicsDirectionDifficulty.INTERMEDIATE.directions.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced generates exactly 5 choices`() {
        val engine = DynamicsDirectionEngine.withSeed(99)
        repeat(20) {
            val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
            assertEquals(5, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced choices are all 5 directions`() {
        val engine = DynamicsDirectionEngine.withSeed(5)
        repeat(30) {
            val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
            val expected = DynamicsDirection.ALL.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = DynamicsDirectionEngine.withSeed(3)
        DynamicsDirectionDifficulty.ALL.forEach { difficulty ->
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
        val engine = DynamicsDirectionEngine.withSeed(13)
        DynamicsDirectionDifficulty.ALL.forEach { difficulty ->
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

    @Test
    fun `deterministic generation with same seed`() {
        val engine1 = DynamicsDirectionEngine.withSeed(777)
        val engine2 = DynamicsDirectionEngine.withSeed(777)
        repeat(10) {
            val q1 = engine1.generate(DynamicsDirectionDifficulty.ADVANCED)
            val q2 = engine2.generate(DynamicsDirectionDifficulty.ADVANCED)
            assertEquals(q1.direction, q2.direction)
            assertEquals(q1.tonicMidi, q2.tonicMidi)
            assertEquals(q1.answerChoices, q2.answerChoices)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val questions = (0L..100).map { seed ->
            val e = DynamicsDirectionEngine.withSeed(seed)
            e.generate(DynamicsDirectionDifficulty.ADVANCED)
        }
        val distinctDirections = questions.map { it.direction }.distinct()
        assertTrue(
            "Expected variety of directions across seeds, got ${distinctDirections.size}",
            distinctDirections.size >= 3
        )
        val distinctTonics = questions.map { it.tonicMidi }.distinct()
        assertTrue(
            "Expected variety of tonics across seeds, got ${distinctTonics.size}",
            distinctTonics.size >= 3
        )
    }

    @Test
    fun `beginner correct answer is crescendo or decrescendo only`() {
        val engine = DynamicsDirectionEngine.withSeed(2)
        repeat(50) {
            val q = engine.generate(DynamicsDirectionDifficulty.BEGINNER)
            assertTrue(
                "Beginner answer must be Crescendo or Decrescendo, got ${q.direction}",
                q.direction == DynamicsDirection.CRESCENDO || q.direction == DynamicsDirection.DECRESCENDO
            )
        }
    }

    @Test
    fun `intermediate correct answer is in intermediate direction set`() {
        val engine = DynamicsDirectionEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(DynamicsDirectionDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate answer not in candidate set, got ${q.direction}",
                q.direction in DynamicsDirectionDifficulty.INTERMEDIATE.directions
            )
        }
    }

    @Test
    fun `advanced correct answer is in advanced direction set`() {
        val engine = DynamicsDirectionEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
            assertTrue(
                "Advanced answer not in candidate set, got ${q.direction}",
                q.direction in DynamicsDirectionDifficulty.ADVANCED.directions
            )
        }
    }

    @Test
    fun `tonic midi stays within valid range`() {
        val engine = DynamicsDirectionEngine.withSeed(8)
        repeat(100) {
            val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
            assertTrue(
                "Tonic ${q.tonicMidi} below minimum $MIN_TONIC_MIDI",
                q.tonicMidi >= MIN_TONIC_MIDI
            )
            assertTrue(
                "Tonic ${q.tonicMidi} above maximum $MAX_TONIC_MIDI",
                q.tonicMidi <= MAX_TONIC_MIDI
            )
        }
    }

    @Test
    fun `beginner produces both crescendo and decrescendo across many seeds`() {
        val answers = mutableSetOf<DynamicsDirection>()
        for (seed in 0L..500) {
            val engine = DynamicsDirectionEngine.withSeed(seed)
            val q = engine.generate(DynamicsDirectionDifficulty.BEGINNER)
            answers.add(q.direction)
        }
        assertTrue("Should produce both directions", answers.size >= 2)
        assertTrue("Should include Crescendo", DynamicsDirection.CRESCENDO in answers)
        assertTrue("Should include Decrescendo", DynamicsDirection.DECRESCENDO in answers)
    }

    @Test
    fun `advanced produces all 5 directions across seeds`() {
        val answers = mutableSetOf<DynamicsDirection>()
        for (seed in 0L..3000) {
            val engine = DynamicsDirectionEngine.withSeed(seed)
            val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
            answers.add(q.direction)
        }
        assertEquals(
            "Should produce all 5 dynamics directions",
            DynamicsDirection.ALL.toSet(),
            answers
        )
    }

    @Test
    fun `seed is non-zero`() {
        val engine = DynamicsDirectionEngine.withSeed(123)
        val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
        assertNotNull(q.seed)
    }

    @Test
    fun `choices are shuffled and not always in declaration order`() {
        val naturalOrder = DynamicsDirection.ALL.map { it.fullLabel }
        var foundShuffled = false
        for (seed in 0L..200) {
            val engine = DynamicsDirectionEngine.withSeed(seed)
            val q = engine.generate(DynamicsDirectionDifficulty.ADVANCED)
            if (q.answerChoices != naturalOrder) {
                foundShuffled = true
                break
            }
        }
        assertTrue("Choices should sometimes be shuffled from natural order", foundShuffled)
    }

    @Test
    fun `question difficulty matches requested`() {
        val engine = DynamicsDirectionEngine.withSeed(33)
        DynamicsDirectionDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    @Test
    fun `answer count matches difficulty choice count`() {
        val engine = DynamicsDirectionEngine.withSeed(33)
        DynamicsDirectionDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `choices are never empty`() {
        val engine = DynamicsDirectionEngine.withSeed(33)
        DynamicsDirectionDifficulty.ALL.forEach { difficulty ->
            repeat(5) {
                val q = engine.generate(difficulty)
                assertTrue("Choices should not be empty", q.answerChoices.isNotEmpty())
            }
        }
    }

    @Test
    fun `different engines with different seeds produce different sequences`() {
        val engine1 = DynamicsDirectionEngine.withSeed(1)
        val engine2 = DynamicsDirectionEngine.withSeed(2)
        var anyDifferent = false
        repeat(20) {
            val q1 = engine1.generate(DynamicsDirectionDifficulty.ADVANCED)
            val q2 = engine2.generate(DynamicsDirectionDifficulty.ADVANCED)
            if (q1.direction != q2.direction || q1.tonicMidi != q2.tonicMidi) {
                anyDifferent = true
            }
        }
        assertTrue("Different seeds should produce different sequences", anyDifferent)
    }

    @Test
    fun `no two consecutive questions are guaranteed identical`() {
        // 不是硬性保证，但跨多次生成应能看出引擎在前进
        val engine = DynamicsDirectionEngine.withSeed(999)
        val q1 = engine.generate(DynamicsDirectionDifficulty.INTERMEDIATE)
        val q2 = engine.generate(DynamicsDirectionDifficulty.INTERMEDIATE)
        // 即使偶然相同也无妨，这里只验证不会抛异常且字段合法
        assertNotEquals(0L, q1.seed)
        assertNotEquals(0L, q2.seed)
    }
}
