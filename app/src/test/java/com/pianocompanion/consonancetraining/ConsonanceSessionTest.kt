package com.pianocompanion.consonancetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 协和度辨识训练会话状态机单元测试。
 */
class ConsonanceSessionTest {

    private lateinit var engine: ConsonanceEngine
    private lateinit var session: ConsonanceSession

    @Before
    fun setUp() {
        engine = ConsonanceEngine.withSeed(42)
        session = ConsonanceSession(engine, ConsonanceDifficulty.INTERMEDIATE)
    }

    @Test
    fun `not started initially`() {
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `start generates first question`() {
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `submit correct answer increments counters`() {
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        val record = session.submit(correct)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer resets streak`() {
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        // First correct
        session.submit(correct)
        assertEquals(1, session.currentStreak)
        session.next()
        // Then wrong (pick any non-correct answer)
        val wrongAnswer = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        session.start()
        // 3 correct in a row
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // 1 wrong
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.bestStreak)
        // 2 more correct
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `submit before start returns null`() {
        assertNull(session.submit("anything"))
    }

    @Test
    fun `double submit returns null`() {
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        session.submit(correct)
        assertNull(session.submit(correct))
    }

    @Test
    fun `next before start returns null`() {
        assertNull(session.next())
    }

    @Test
    fun `next generates new question and clears answered state`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `accuracy calculation`() {
        session.start()
        // 2 correct, 1 wrong
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.answeredCount)
        assertEquals(2, session.correctCount)
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero before answering`() {
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `reset clears all state`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `history records all answers`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `last answer is updated`() {
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `difficulty returns configured difficulty`() {
        assertEquals(ConsonanceDifficulty.INTERMEDIATE, session.difficulty())
    }

    @Test
    fun `beginner session works end to end`() {
        val s = ConsonanceSession(ConsonanceEngine.withSeed(1), ConsonanceDifficulty.BEGINNER)
        s.start()
        assertNotNull(s.currentQuestion)
        assertEquals(2, s.currentQuestion!!.answerChoices.size)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.correctCount)
    }

    @Test
    fun `advanced session works end to end`() {
        val s = ConsonanceSession(ConsonanceEngine.withSeed(1), ConsonanceDifficulty.ADVANCED)
        s.start()
        assertNotNull(s.currentQuestion)
        assertEquals(3, s.currentQuestion!!.answerChoices.size)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.correctCount)
    }

    @Test
    fun `answer record correct answer is null when correct`() {
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(record)
        assertNull(record!!.correctAnswer)
    }

    @Test
    fun `answer record correct answer is set when wrong`() {
        session.start()
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrong)
        assertNotNull(record)
        assertEquals(session.currentQuestion!!.correctAnswer, record!!.correctAnswer)
    }
}
