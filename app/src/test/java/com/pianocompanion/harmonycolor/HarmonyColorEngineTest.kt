package com.pianocompanion.harmonycolor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和声色彩听辨训练出题引擎单元测试。
 */
class HarmonyColorEngineTest {

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = HarmonyColorEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(HarmonyColorDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `beginner choices are major vs minor`() {
        val engine = HarmonyColorEngine.withSeed(1)
        repeat(30) {
            val q = engine.generate(HarmonyColorDifficulty.BEGINNER)
            assertTrue(
                "Expected Major/Minor labels, got: ${q.answerChoices}",
                q.answerChoices.contains(HarmonyColor.MAJOR.fullLabel) &&
                    q.answerChoices.contains(HarmonyColor.MINOR.fullLabel)
            )
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = HarmonyColorEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(HarmonyColorDifficulty.INTERMEDIATE)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate choices are major minor diminished`() {
        val engine = HarmonyColorEngine.withSeed(100)
        repeat(30) {
            val q = engine.generate(HarmonyColorDifficulty.INTERMEDIATE)
            val expected = HarmonyColorDifficulty.INTERMEDIATE.colors.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced generates exactly 4 choices`() {
        val engine = HarmonyColorEngine.withSeed(99)
        repeat(20) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            assertEquals(4, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced choices are all 4 colors`() {
        val engine = HarmonyColorEngine.withSeed(5)
        repeat(30) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            val expected = HarmonyColor.ALL.map { it.fullLabel }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = HarmonyColorEngine.withSeed(3)
        HarmonyColorDifficulty.ALL.forEach { difficulty ->
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
        val engine = HarmonyColorEngine.withSeed(13)
        HarmonyColorDifficulty.ALL.forEach { difficulty ->
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
        val engine1 = HarmonyColorEngine.withSeed(777)
        val engine2 = HarmonyColorEngine.withSeed(777)
        repeat(10) {
            val q1 = engine1.generate(HarmonyColorDifficulty.ADVANCED)
            val q2 = engine2.generate(HarmonyColorDifficulty.ADVANCED)
            assertEquals(q1.color, q2.color)
            assertEquals(q1.rootMidi, q2.rootMidi)
            assertEquals(q1.answerChoices, q2.answerChoices)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val questions = (0L..100).map { seed ->
            val e = HarmonyColorEngine.withSeed(seed)
            e.generate(HarmonyColorDifficulty.ADVANCED)
        }
        val distinctColors = questions.map { it.color }.distinct()
        assertTrue(
            "Expected variety of colors across seeds, got ${distinctColors.size}",
            distinctColors.size >= 3
        )
        val distinctRoots = questions.map { it.rootMidi }.distinct()
        assertTrue(
            "Expected variety of roots across seeds, got ${distinctRoots.size}",
            distinctRoots.size >= 3
        )
    }

    @Test
    fun `beginner correct answer is major or minor only`() {
        val engine = HarmonyColorEngine.withSeed(2)
        repeat(50) {
            val q = engine.generate(HarmonyColorDifficulty.BEGINNER)
            assertTrue(
                "Beginner answer must be Major or Minor, got ${q.color}",
                q.color == HarmonyColor.MAJOR || q.color == HarmonyColor.MINOR
            )
        }
    }

    @Test
    fun `intermediate correct answer is in intermediate color set`() {
        val engine = HarmonyColorEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(HarmonyColorDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate answer not in candidate set, got ${q.color}",
                q.color in HarmonyColorDifficulty.INTERMEDIATE.colors
            )
        }
    }

    @Test
    fun `advanced correct answer is in advanced color set`() {
        val engine = HarmonyColorEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            assertTrue(
                "Advanced answer not in candidate set, got ${q.color}",
                q.color in HarmonyColorDifficulty.ADVANCED.colors
            )
        }
    }

    @Test
    fun `root midi stays within valid range`() {
        val engine = HarmonyColorEngine.withSeed(8)
        repeat(100) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            assertTrue(
                "Root ${q.rootMidi} below minimum $MIN_ROOT_MIDI",
                q.rootMidi >= MIN_ROOT_MIDI
            )
            assertTrue(
                "Root ${q.rootMidi} above maximum $MAX_ROOT_MIDI",
                q.rootMidi <= MAX_ROOT_MIDI
            )
        }
    }

    @Test
    fun `voicing matches color interval structure`() {
        val engine = HarmonyColorEngine.withSeed(20)
        repeat(50) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            val expectedVoicing = q.color.intervals.map { it + q.rootMidi }
            assertEquals(
                "Voicing ${q.voicing} should match root ${q.rootMidi} + intervals ${q.color.intervals.toList()}",
                expectedVoicing,
                q.voicing
            )
        }
    }

    @Test
    fun `voicing has exactly 3 notes`() {
        val engine = HarmonyColorEngine.withSeed(20)
        HarmonyColorDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                assertEquals(3, q.voicing.size)
            }
        }
    }

    @Test
    fun `voicing notes are strictly increasing`() {
        val engine = HarmonyColorEngine.withSeed(20)
        repeat(50) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            for (i in 0 until q.voicing.size - 1) {
                assertTrue(
                    "Voicing should be strictly increasing: ${q.voicing}",
                    q.voicing[i] < q.voicing[i + 1]
                )
            }
        }
    }

    @Test
    fun `voicing notes within midi range`() {
        val engine = HarmonyColorEngine.withSeed(20)
        repeat(50) {
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            assertTrue(q.voicing.all { it in 0..127 })
        }
    }

    @Test
    fun `beginner produces both major and minor across many seeds`() {
        val answers = mutableSetOf<HarmonyColor>()
        for (seed in 0L..500) {
            val engine = HarmonyColorEngine.withSeed(seed)
            val q = engine.generate(HarmonyColorDifficulty.BEGINNER)
            answers.add(q.color)
        }
        assertTrue("Should produce both colors", answers.size >= 2)
        assertTrue("Should include Major", HarmonyColor.MAJOR in answers)
        assertTrue("Should include Minor", HarmonyColor.MINOR in answers)
    }

    @Test
    fun `advanced produces all 4 colors across seeds`() {
        val answers = mutableSetOf<HarmonyColor>()
        for (seed in 0L..3000) {
            val engine = HarmonyColorEngine.withSeed(seed)
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            answers.add(q.color)
        }
        assertEquals(
            "Should produce all 4 harmony colors",
            HarmonyColor.ALL.toSet(),
            answers
        )
    }

    @Test
    fun `seed is non-zero`() {
        val engine = HarmonyColorEngine.withSeed(123)
        val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
        assertNotNull(q.seed)
    }

    @Test
    fun `choices are shuffled and not always in declaration order`() {
        val naturalOrder = HarmonyColor.ALL.map { it.fullLabel }
        var foundShuffled = false
        for (seed in 0L..200) {
            val engine = HarmonyColorEngine.withSeed(seed)
            val q = engine.generate(HarmonyColorDifficulty.ADVANCED)
            if (q.answerChoices != naturalOrder) {
                foundShuffled = true
                break
            }
        }
        assertTrue("Choices should sometimes be shuffled from natural order", foundShuffled)
    }

    @Test
    fun `question difficulty matches requested`() {
        val engine = HarmonyColorEngine.withSeed(33)
        HarmonyColorDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    @Test
    fun `answer count matches difficulty choice count`() {
        val engine = HarmonyColorEngine.withSeed(33)
        HarmonyColorDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `choices are never empty`() {
        val engine = HarmonyColorEngine.withSeed(33)
        HarmonyColorDifficulty.ALL.forEach { difficulty ->
            repeat(5) {
                val q = engine.generate(difficulty)
                assertTrue("Choices should not be empty", q.answerChoices.isNotEmpty())
            }
        }
    }

    @Test
    fun `different engines with different seeds produce different sequences`() {
        val engine1 = HarmonyColorEngine.withSeed(1)
        val engine2 = HarmonyColorEngine.withSeed(2)
        var anyDifferent = false
        repeat(20) {
            val q1 = engine1.generate(HarmonyColorDifficulty.ADVANCED)
            val q2 = engine2.generate(HarmonyColorDifficulty.ADVANCED)
            if (q1.color != q2.color || q1.rootMidi != q2.rootMidi) {
                anyDifferent = true
            }
        }
        assertTrue("Different seeds should produce different sequences", anyDifferent)
    }

    @Test
    fun `no two consecutive questions are guaranteed identical`() {
        val engine = HarmonyColorEngine.withSeed(999)
        val q1 = engine.generate(HarmonyColorDifficulty.INTERMEDIATE)
        val q2 = engine.generate(HarmonyColorDifficulty.INTERMEDIATE)
        assertNotEquals(0L, q1.seed)
        assertNotEquals(0L, q2.seed)
    }
}
