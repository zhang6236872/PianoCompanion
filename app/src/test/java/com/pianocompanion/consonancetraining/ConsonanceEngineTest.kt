package com.pianocompanion.consonancetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协和度辨识训练出题引擎单元测试。
 */
class ConsonanceEngineTest {

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = ConsonanceEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(ConsonanceDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are consonant vs dissonant labels`() {
        val engine = ConsonanceEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(ConsonanceDifficulty.BEGINNER)
            assertTrue(
                "Expected beginner labels, got: ${q.answerChoices}",
                q.answerChoices.contains(ConsonanceEngine.BEGINNER_CONSONANT_LABEL) &&
                    q.answerChoices.contains(ConsonanceEngine.BEGINNER_DISSONANT_LABEL)
            )
        }
    }

    @Test
    fun `beginner correct answer matches interval category`() {
        val engine = ConsonanceEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(ConsonanceDifficulty.BEGINNER)
            val expected = if (q.interval.category == ConsonanceCategory.DISSONANCE) {
                ConsonanceEngine.BEGINNER_DISSONANT_LABEL
            } else {
                ConsonanceEngine.BEGINNER_CONSONANT_LABEL
            }
            assertEquals(expected, q.correctAnswer)
        }
    }

    @Test
    fun `beginner produces both consonant and dissonant answers across many seeds`() {
        val consonantAnswers = mutableSetOf<String>()
        for (seed in 0L..200) {
            val engine = ConsonanceEngine.withSeed(seed)
            val q = engine.generate(ConsonanceDifficulty.BEGINNER)
            consonantAnswers.add(q.correctAnswer)
        }
        assertTrue("Should produce both labels", consonantAnswers.size >= 2)
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = ConsonanceEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(ConsonanceDifficulty.INTERMEDIATE)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate choices are the three full category labels`() {
        val engine = ConsonanceEngine.withSeed(3)
        repeat(30) {
            val q = engine.generate(ConsonanceDifficulty.INTERMEDIATE)
            assertEquals(ConsonanceCategory.ALL.size, q.answerChoices.size)
            ConsonanceCategory.ALL.forEach { cat ->
                assertTrue("Missing ${cat.fullLabel}", q.answerChoices.contains(cat.fullLabel))
            }
        }
    }

    @Test
    fun `intermediate correct answer is the interval's category`() {
        val engine = ConsonanceEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(ConsonanceDifficulty.INTERMEDIATE)
            assertEquals(q.interval.category.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `advanced generates 3 choices`() {
        val engine = ConsonanceEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(ConsonanceDifficulty.ADVANCED)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced correct answer is the interval's category`() {
        val engine = ConsonanceEngine.withSeed(123)
        repeat(30) {
            val q = engine.generate(ConsonanceDifficulty.ADVANCED)
            assertEquals(q.interval.category.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `advanced can produce non-zero octave offset`() {
        var hasNonZeroOffset = false
        for (seed in 0L..500) {
            val engine = ConsonanceEngine.withSeed(seed)
            val q = engine.generate(ConsonanceDifficulty.ADVANCED)
            if (q.octaveOffset != 0) {
                hasNonZeroOffset = true
                break
            }
        }
        assertTrue("Advanced should sometimes produce non-zero octave offsets", hasNonZeroOffset)
    }

    @Test
    fun `beginner and intermediate always have zero octave offset`() {
        val engine = ConsonanceEngine.withSeed(42)
        repeat(20) {
            assertEquals(0, engine.generate(ConsonanceDifficulty.BEGINNER).octaveOffset)
            assertEquals(0, engine.generate(ConsonanceDifficulty.INTERMEDIATE).octaveOffset)
        }
    }

    @Test
    fun `octave offset is within range minus1 to plus1`() {
        val engine = ConsonanceEngine.withSeed(42)
        repeat(50) {
            val q = engine.generate(ConsonanceDifficulty.ADVANCED)
            assertTrue(q.octaveOffset in -1..1)
        }
    }

    @Test
    fun `deterministic - same seed produces same question`() {
        val engine1 = ConsonanceEngine.withSeed(777)
        val engine2 = ConsonanceEngine.withSeed(777)
        val q1 = engine1.generate(ConsonanceDifficulty.INTERMEDIATE)
        val q2 = engine2.generate(ConsonanceDifficulty.INTERMEDIATE)
        assertEquals(q1.interval, q2.interval)
        assertEquals(q1.category, q2.category)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `correct answer is always in answer choices`() {
        val engine = ConsonanceEngine.withSeed(55)
        ConsonanceDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Correct answer '${q.correctAnswer}' not in choices ${q.answerChoices}",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `melodic presentation for beginner, harmonic for intermediate and advanced`() {
        val engine = ConsonanceEngine.withSeed(42)
        repeat(10) {
            assertEquals(
                Presentation.MELODIC,
                engine.generate(ConsonanceDifficulty.BEGINNER).presentation
            )
            assertEquals(
                Presentation.HARMONIC,
                engine.generate(ConsonanceDifficulty.INTERMEDIATE).presentation
            )
            assertEquals(
                Presentation.HARMONIC,
                engine.generate(ConsonanceDifficulty.ADVANCED).presentation
            )
        }
    }

    @Test
    fun `interval belongs to its declared category`() {
        val engine = ConsonanceEngine.withSeed(42)
        ConsonanceDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                assertEquals(q.interval.category, q.category)
            }
        }
    }

    @Test
    fun `base midi is C4`() {
        val engine = ConsonanceEngine.withSeed(42)
        val q = engine.generate(ConsonanceDifficulty.INTERMEDIATE)
        assertEquals(60, q.baseMidi)
    }

    @Test
    fun `all three categories appear across many seeds at intermediate`() {
        val seen = mutableSetOf<ConsonanceCategory>()
        for (seed in 0L..500) {
            val engine = ConsonanceEngine.withSeed(seed)
            val q = engine.generate(ConsonanceDifficulty.INTERMEDIATE)
            seen.add(q.category)
        }
        assertEquals(3, seen.size)
    }

    @Test
    fun `higher midi is always above lower midi`() {
        val engine = ConsonanceEngine.withSeed(42)
        ConsonanceDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                assertTrue(q.higherMidi > q.lowerMidi)
            }
        }
    }
}
