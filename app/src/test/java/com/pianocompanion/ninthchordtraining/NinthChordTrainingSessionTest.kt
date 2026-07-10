package com.pianocompanion.ninthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 九和弦色彩听辨训练会话状态机单元测试。
 */
class NinthChordTrainingSessionTest {

    private lateinit var session: NinthChordTrainingSession

    @Before
    fun setUp() {
        session = NinthChordTrainingSession(
            NinthChordTrainingEngine.withSeed(42L),
            NinthChordDifficulty.INTERMEDIATE
        )
    }

    // ── 基本生命周期 ──────────────────────────────────────────

    @Test
    fun `session starts with no question`() {
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start generates first question`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start can be called multiple times`() {
        session.start()
        assertNotNull(session.currentQuestion)
        session.start()
        // Second start resets stats but still generates a valid question
        assertNotNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertTrue(session.isStarted)
    }

    // ── 答题 ──────────────────────────────────────────────

    @Test
    fun `submit correct answer`() {
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)!!

        assertTrue(record.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)!!

        assertFalse(record.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit returns null when no question`() {
        assertNull(session.submit("anything"))
    }

    @Test
    fun `submit returns null when already answered`() {
        session.start()
        val question = session.currentQuestion!!
        session.submit(question.correctAnswer)
        assertNull(session.submit(question.correctAnswer))
    }

    // ── 连击追踪 ──────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct answers`() {
        session.start()

        // Answer 3 correct in a row
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `streak resets on wrong answer`() {
        session.start()

        // 2 correct
        repeat(2) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(2, session.currentStreak)

        // 1 wrong
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak) // best streak preserved
    }

    @Test
    fun `best streak does not decrease`() {
        session.start()

        // 3 correct → best=3
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)

        // 1 wrong → streak=0, best stays 3
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)

        // 1 correct → streak=1, best still 3
        session.next()
        val q2 = session.currentQuestion!!
        session.submit(q2.correctAnswer)
        assertEquals(1, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────────

    @Test
    fun `accuracy is correct after mixed answers`() {
        session.start()

        // 2 correct
        repeat(2) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        // 1 wrong
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)

        assertEquals(3, session.answeredCount)
        assertEquals(2, session.correctCount)
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 0 before answering`() {
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── next ──────────────────────────────────────────────

    @Test
    fun `next generates new question`() {
        session.start()
        val q1 = session.currentQuestion
        session.next()
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next returns null when not started`() {
        assertNull(session.next())
    }

    // ── history ──────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        session.start()

        val answers = mutableListOf<NinthChordAnswerRecord>()
        repeat(3) {
            val q = session.currentQuestion!!
            val answer = if (it % 2 == 0) q.correctAnswer else q.answerChoices.first { a -> a != q.correctAnswer }
            val record = session.submit(answer)!!
            answers.add(record)
            session.next()
        }

        assertEquals(3, session.history.size)
        for (i in answers.indices) {
            assertEquals(answers[i].isCorrect, session.history[i].isCorrect)
        }
    }

    @Test
    fun `history is empty before answering`() {
        session.start()
        assertTrue(session.history.isEmpty())
    }

    // ── reset ──────────────────────────────────────────────

    @Test
    fun `reset clears everything`() {
        session.start()
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        session.reset()

        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
    }

    @Test
    fun `lastAnswer is set after submit`() {
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertNotNull(session.lastAnswer)
    }

    @Test
    fun `difficulty returns configured difficulty`() {
        assertEquals(NinthChordDifficulty.INTERMEDIATE, session.difficulty())
    }

    private fun assertNotNull(value: Any?) {
        org.junit.Assert.assertNotNull(value)
    }
}
