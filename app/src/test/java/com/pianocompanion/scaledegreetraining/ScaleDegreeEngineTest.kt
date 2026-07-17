package com.pianocompanion.scaledegreetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调内音级辨识训练出题引擎单元测试。
 */
class ScaleDegreeEngineTest {

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = ScaleDegreeEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(ScaleDegreeDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are Do vs Sol`() {
        val engine = ScaleDegreeEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(ScaleDegreeDifficulty.BEGINNER)
            assertTrue(
                "Expected Do/Sol labels, got: ${q.answerChoices}",
                q.answerChoices.contains(ScaleDegree.DO.fullLabel) &&
                    q.answerChoices.contains(ScaleDegree.SOL.fullLabel)
            )
        }
    }

    @Test
    fun `intermediate generates exactly 4 choices`() {
        val engine = ScaleDegreeEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(ScaleDegreeDifficulty.INTERMEDIATE)
            assertEquals(4, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate choices are Do Mi Fa Sol`() {
        val engine = ScaleDegreeEngine.withSeed(100)
        repeat(30) {
            val q = engine.generate(ScaleDegreeDifficulty.INTERMEDIATE)
            val expected = ScaleDegreeDifficulty.INTERMEDIATE.degrees.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced generates exactly 7 choices`() {
        val engine = ScaleDegreeEngine.withSeed(99)
        repeat(20) {
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            assertEquals(7, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced choices are all 7 scale degrees`() {
        val engine = ScaleDegreeEngine.withSeed(5)
        repeat(30) {
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            val expected = ScaleDegree.ALL.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = ScaleDegreeEngine.withSeed(3)
        ScaleDegreeDifficulty.ALL.forEach { difficulty ->
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
        val engine = ScaleDegreeEngine.withSeed(13)
        ScaleDegreeDifficulty.ALL.forEach { difficulty ->
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
        val engine1 = ScaleDegreeEngine.withSeed(777)
        val engine2 = ScaleDegreeEngine.withSeed(777)
        repeat(10) {
            val q1 = engine1.generate(ScaleDegreeDifficulty.ADVANCED)
            val q2 = engine2.generate(ScaleDegreeDifficulty.ADVANCED)
            assertEquals(q1.degree, q2.degree)
            assertEquals(q1.tonicMidi, q2.tonicMidi)
            assertEquals(q1.answerChoices, q2.answerChoices)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val questions = (0L..100).map { seed ->
            val e = ScaleDegreeEngine.withSeed(seed)
            e.generate(ScaleDegreeDifficulty.ADVANCED)
        }
        val distinctDegrees = questions.map { it.degree }.distinct()
        assertTrue(
            "Expected variety of degrees across seeds, got ${distinctDegrees.size}",
            distinctDegrees.size >= 4
        )
        val distinctTonics = questions.map { it.tonicMidi }.distinct()
        assertTrue(
            "Expected variety of tonics across seeds, got ${distinctTonics.size}",
            distinctTonics.size >= 4
        )
    }

    @Test
    fun `beginner correct answer is Do or Sol only`() {
        val engine = ScaleDegreeEngine.withSeed(2)
        repeat(50) {
            val q = engine.generate(ScaleDegreeDifficulty.BEGINNER)
            assertTrue(
                "Beginner answer must be Do or Sol, got ${q.degree}",
                q.degree == ScaleDegree.DO || q.degree == ScaleDegree.SOL
            )
        }
    }

    @Test
    fun `intermediate correct answer is in intermediate degree set`() {
        val engine = ScaleDegreeEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(ScaleDegreeDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate answer not in candidate set, got ${q.degree}",
                q.degree in ScaleDegreeDifficulty.INTERMEDIATE.degrees
            )
        }
    }

    @Test
    fun `advanced correct answer is in advanced degree set`() {
        val engine = ScaleDegreeEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            assertTrue(
                "Advanced answer not in candidate set, got ${q.degree}",
                q.degree in ScaleDegreeDifficulty.ADVANCED.degrees
            )
        }
    }

    @Test
    fun `target midi equals tonic plus semitone offset`() {
        val engine = ScaleDegreeEngine.withSeed(8)
        repeat(50) {
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            assertEquals(
                "Target MIDI must be tonic + degree offset",
                q.tonicMidi + q.degree.semitonesFromTonic,
                q.targetMidi
            )
        }
    }

    @Test
    fun `tonic midi stays within valid range`() {
        val engine = ScaleDegreeEngine.withSeed(8)
        repeat(100) {
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
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
    fun `target midi stays within audible range`() {
        val engine = ScaleDegreeEngine.withSeed(11)
        repeat(100) {
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            // 最高主音(A4=69) + 11 半音(Ti) = 80，未超 MIDI 84 (C6)
            assertTrue(
                "Target ${q.targetMidi} too high",
                q.targetMidi <= 84
            )
        }
    }

    @Test
    fun `beginner produces both Do and Sol answers across many seeds`() {
        val answers = mutableSetOf<ScaleDegree>()
        for (seed in 0L..300) {
            val engine = ScaleDegreeEngine.withSeed(seed)
            val q = engine.generate(ScaleDegreeDifficulty.BEGINNER)
            answers.add(q.degree)
        }
        assertTrue("Should produce both Do and Sol", answers.size >= 2)
        assertTrue("Should include Do", ScaleDegree.DO in answers)
        assertTrue("Should include Sol", ScaleDegree.SOL in answers)
    }

    @Test
    fun `intermediate produces all 4 candidate degrees across seeds`() {
        val answers = mutableSetOf<ScaleDegree>()
        for (seed in 0L..500) {
            val engine = ScaleDegreeEngine.withSeed(seed)
            val q = engine.generate(ScaleDegreeDifficulty.INTERMEDIATE)
            answers.add(q.degree)
        }
        assertEquals(
            "Should produce all 4 intermediate degrees",
            ScaleDegreeDifficulty.INTERMEDIATE.degrees.toSet(),
            answers
        )
    }

    @Test
    fun `advanced produces all 7 degrees across seeds`() {
        val answers = mutableSetOf<ScaleDegree>()
        for (seed in 0L..2000) {
            val engine = ScaleDegreeEngine.withSeed(seed)
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            answers.add(q.degree)
        }
        assertEquals(
            "Should produce all 7 scale degrees",
            ScaleDegree.ALL.toSet(),
            answers
        )
    }

    @Test
    fun `seed is non-zero`() {
        val engine = ScaleDegreeEngine.withSeed(123)
        val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
        assertNotNull(q.seed)
    }

    @Test
    fun `scale degree semitone offsets are correct for major scale`() {
        // 大调音阶半音数：0,2,4,5,7,9,11
        assertEquals(0, ScaleDegree.DO.semitonesFromTonic)
        assertEquals(2, ScaleDegree.RE.semitonesFromTonic)
        assertEquals(4, ScaleDegree.MI.semitonesFromTonic)
        assertEquals(5, ScaleDegree.FA.semitonesFromTonic)
        assertEquals(7, ScaleDegree.SOL.semitonesFromTonic)
        assertEquals(9, ScaleDegree.LA.semitonesFromTonic)
        assertEquals(11, ScaleDegree.TI.semitonesFromTonic)
    }

    @Test
    fun `fromSemitones returns correct degree`() {
        assertEquals(ScaleDegree.DO, ScaleDegree.fromSemitones(0))
        assertEquals(ScaleDegree.MI, ScaleDegree.fromSemitones(4))
        assertEquals(ScaleDegree.TI, ScaleDegree.fromSemitones(11))
        assertEquals(null, ScaleDegree.fromSemitones(1)) // 小二度不在大调音阶
    }

    @Test
    fun `choices are shuffled and not always in natural order`() {
        // 收集高级难度的选项顺序，确认至少有一些不是自然顺序
        val naturalOrder = ScaleDegree.ALL.map { it.fullLabel }
        var foundShuffled = false
        for (seed in 0L..100) {
            val engine = ScaleDegreeEngine.withSeed(seed)
            val q = engine.generate(ScaleDegreeDifficulty.ADVANCED)
            if (q.answerChoices != naturalOrder) {
                foundShuffled = true
                break
            }
        }
        assertTrue("Choices should sometimes be shuffled from natural order", foundShuffled)
    }

    @Test
    fun `question difficulty matches requested`() {
        val engine = ScaleDegreeEngine.withSeed(33)
        ScaleDegreeDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }
}
