package com.pianocompanion.compoundmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复合节拍听辨训练会话状态机单元测试。
 */
class CompoundMeterSessionTest {

    private fun createSession(difficulty: CompoundMeterDifficulty = CompoundMeterDifficulty.BEGINNER): CompoundMeterSession {
        return CompoundMeterSession(CompoundMeterEngine.withSeed(42), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────

    @Test
    fun `session starts with first question`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start clears previous stats`() {
        val session = createSession()
        session.start()
        // Answer some questions
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertEquals(1, session.answeredCount)
        // Restart
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `reset clears all state`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    // ── 答题判定 ──────────────────────────────────

    @Test
    fun `correct answer increments correct count and streak`() {
        val session = createSession(CompoundMeterDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `incorrect answer resets streak but keeps count`() {
        val session = createSession(CompoundMeterDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        // Find a wrong answer
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        val session = createSession(CompoundMeterDifficulty.ADVANCED)
        session.start()
        // 3 correct in a row
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.correctCount)
        assertEquals(3, session.bestStreak)
        // 1 wrong
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(3, session.bestStreak)
        assertEquals(0, session.currentStreak)
        // 1 more correct
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak)
        assertEquals(1, session.currentStreak)
    }

    // ── 答题状态守卫 ──────────────────────────────────

    @Test
    fun `submit returns null when no current question`() {
        val session = createSession()
        val record = session.submit("test")
        assertNull(record)
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `next returns null when session not started`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `next generates new question after answering`() {
        val session = createSession(CompoundMeterDifficulty.ADVANCED)
        session.start()
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
        assertNotNull(session.currentQuestion)
    }

    // ── 统计 ──────────────────────────────────

    @Test
    fun `accuracy is correct`() {
        val session = createSession(CompoundMeterDifficulty.ADVANCED)
        session.start()
        // 2 correct, 1 wrong
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0.6667, session.accuracy, 0.01)
    }

    @Test
    fun `accuracy is zero when no questions answered`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 历史 ──────────────────────────────────

    @Test
    fun `history records all answers in order`() {
        val session = createSession(CompoundMeterDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!
        val wrong = q2.answerChoices.first { it != q2.correctAnswer }
        session.submit(wrong)
        val history = session.history
        assertEquals(2, history.size)
        assertTrue(history[0].isCorrect)
        assertFalse(history[1].isCorrect)
    }

    @Test
    fun `history is defensive copy`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history1 = session.history
        val history2 = session.history
        assertEquals(history1, history2)
        // They should be different instances
        assertTrue(history1 !== history2)
    }

    // ── lastAnswer ──────────────────────────────────

    @Test
    fun `lastAnswer is set after submit`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)
        assertEquals(record, session.lastAnswer)
    }

    @Test
    fun `lastAnswer is cleared after next`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        session.next()
        assertNull(session.lastAnswer)
    }

    // ── 难度 ──────────────────────────────────

    @Test
    fun `difficulty returns the configured difficulty`() {
        val session = createSession(CompoundMeterDifficulty.INTERMEDIATE)
        assertEquals(CompoundMeterDifficulty.INTERMEDIATE, session.difficulty())
    }
}
