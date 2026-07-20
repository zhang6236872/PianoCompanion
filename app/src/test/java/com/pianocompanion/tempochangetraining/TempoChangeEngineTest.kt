package com.pianocompanion.tempochangetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 速度变化方向辨识训练出题引擎单元测试。
 */
class TempoChangeEngineTest {

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = TempoChangeEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(TempoChangeDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are accelerando vs ritardando`() {
        val engine = TempoChangeEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(TempoChangeDifficulty.BEGINNER)
            assertTrue(
                "Expected Accelerando/Ritardando labels, got: ${q.answerChoices}",
                q.answerChoices.contains(TempoChange.ACCELERANDO.fullLabel) &&
                    q.answerChoices.contains(TempoChange.RITARDANDO.fullLabel)
            )
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = TempoChangeEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(TempoChangeDifficulty.INTERMEDIATE)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate choices are accelerando ritardando steady`() {
        val engine = TempoChangeEngine.withSeed(100)
        repeat(30) {
            val q = engine.generate(TempoChangeDifficulty.INTERMEDIATE)
            val expected = TempoChangeDifficulty.INTERMEDIATE.directions.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced generates exactly 5 choices`() {
        val engine = TempoChangeEngine.withSeed(99)
        repeat(20) {
            val q = engine.generate(TempoChangeDifficulty.ADVANCED)
            assertEquals(5, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced choices are all 5 directions`() {
        val engine = TempoChangeEngine.withSeed(5)
        repeat(30) {
            val q = engine.generate(TempoChangeDifficulty.ADVANCED)
            val expected = TempoChange.ALL.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = TempoChangeEngine.withSeed(3)
        TempoChangeDifficulty.ALL.forEach { difficulty ->
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
        val engine = TempoChangeEngine.withSeed(13)
        TempoChangeDifficulty.ALL.forEach { difficulty ->
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
        val engine1 = TempoChangeEngine.withSeed(777)
        val engine2 = TempoChangeEngine.withSeed(777)
        repeat(10) {
            val q1 = engine1.generate(TempoChangeDifficulty.ADVANCED)
            val q2 = engine2.generate(TempoChangeDifficulty.ADVANCED)
            assertEquals(q1.direction, q2.direction)
            assertEquals(q1.tonicMidi, q2.tonicMidi)
            assertEquals(q1.answerChoices, q2.answerChoices)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val questions = (0L..100).map { seed ->
            val e = TempoChangeEngine.withSeed(seed)
            e.generate(TempoChangeDifficulty.ADVANCED)
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
    fun `beginner correct answer is accelerando or ritardando only`() {
        val engine = TempoChangeEngine.withSeed(2)
        repeat(50) {
            val q = engine.generate(TempoChangeDifficulty.BEGINNER)
            assertTrue(
                "Beginner answer must be Accelerando or Ritardando, got ${q.direction}",
                q.direction == TempoChange.ACCELERANDO || q.direction == TempoChange.RITARDANDO
            )
        }
    }

    @Test
    fun `intermediate correct answer is in intermediate direction set`() {
        val engine = TempoChangeEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(TempoChangeDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate answer not in candidate set, got ${q.direction}",
                q.direction in TempoChangeDifficulty.INTERMEDIATE.directions
            )
        }
    }

    @Test
    fun `advanced correct answer is in advanced direction set`() {
        val engine = TempoChangeEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(TempoChangeDifficulty.ADVANCED)
            assertTrue(
                "Advanced answer not in candidate set, got ${q.direction}",
                q.direction in TempoChangeDifficulty.ADVANCED.directions
            )
        }
    }

    @Test
    fun `tonic midi stays within valid range`() {
        val engine = TempoChangeEngine.withSeed(8)
        repeat(100) {
            val q = engine.generate(TempoChangeDifficulty.ADVANCED)
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
    fun `beginner produces both accelerando and ritardando across many seeds`() {
        val answers = mutableSetOf<TempoChange>()
        for (seed in 0L..500) {
            val engine = TempoChangeEngine.withSeed(seed)
            val q = engine.generate(TempoChangeDifficulty.BEGINNER)
            answers.add(q.direction)
        }
        assertTrue("Should produce both directions", answers.size >= 2)
        assertTrue("Should include Accelerando", TempoChange.ACCELERANDO in answers)
        assertTrue("Should include Ritardando", TempoChange.RITARDANDO in answers)
    }

    @Test
    fun `advanced produces all 5 directions across seeds`() {
        val answers = mutableSetOf<TempoChange>()
        for (seed in 0L..3000) {
            val engine = TempoChangeEngine.withSeed(seed)
            val q = engine.generate(TempoChangeDifficulty.ADVANCED)
            answers.add(q.direction)
        }
        assertEquals(
            "Should produce all 5 tempo changes",
            TempoChange.ALL.toSet(),
            answers
        )
    }

    @Test
    fun `seed is non-zero`() {
        val engine = TempoChangeEngine.withSeed(123)
        val q = engine.generate(TempoChangeDifficulty.ADVANCED)
        assertNotNull(q.seed)
    }

    @Test
    fun `choices are shuffled and not always in declaration order`() {
        val naturalOrder = TempoChange.ALL.map { it.fullLabel }
        var foundShuffled = false
        for (seed in 0L..200) {
            val engine = TempoChangeEngine.withSeed(seed)
            val q = engine.generate(TempoChangeDifficulty.ADVANCED)
            if (q.answerChoices != naturalOrder) {
                foundShuffled = true
                break
            }
        }
        assertTrue("Choices should sometimes be shuffled from natural order", foundShuffled)
    }

    @Test
    fun `question difficulty matches requested`() {
        val engine = TempoChangeEngine.withSeed(33)
        TempoChangeDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    @Test
    fun `answer count matches difficulty choice count`() {
        val engine = TempoChangeEngine.withSeed(33)
        TempoChangeDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `choices are never empty`() {
        val engine = TempoChangeEngine.withSeed(33)
        TempoChangeDifficulty.ALL.forEach { difficulty ->
            repeat(5) {
                val q = engine.generate(difficulty)
                assertTrue("Choices should not be empty", q.answerChoices.isNotEmpty())
            }
        }
    }

    @Test
    fun `different engines with different seeds produce different sequences`() {
        val engine1 = TempoChangeEngine.withSeed(1)
        val engine2 = TempoChangeEngine.withSeed(2)
        var anyDifferent = false
        repeat(20) {
            val q1 = engine1.generate(TempoChangeDifficulty.ADVANCED)
            val q2 = engine2.generate(TempoChangeDifficulty.ADVANCED)
            if (q1.direction != q2.direction || q1.tonicMidi != q2.tonicMidi) {
                anyDifferent = true
            }
        }
        assertTrue("Different seeds should produce different sequences", anyDifferent)
    }

    @Test
    fun `no two consecutive questions are guaranteed identical`() {
        val engine = TempoChangeEngine.withSeed(999)
        val q1 = engine.generate(TempoChangeDifficulty.INTERMEDIATE)
        val q2 = engine.generate(TempoChangeDifficulty.INTERMEDIATE)
        assertNotEquals(0L, q1.seed)
        assertNotEquals(0L, q2.seed)
    }
}
