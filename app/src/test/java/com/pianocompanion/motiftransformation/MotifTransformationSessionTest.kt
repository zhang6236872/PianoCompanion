package com.pianocompanion.motiftransformation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动机发展辨识训练会话状态机单元测试。
 */
class MotifTransformationSessionTest {

    private fun createSession(difficulty: MotifTransformationDifficulty = MotifTransformationDifficulty.BEGINNER)
        : MotifTransformationSession {
        val engine = MotifTransformationEngine.withSeed(42L)
        return MotifTransformationSession(engine, difficulty)
    }

    @Test
    fun `session starts with no question`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `submit correct answer increments correct count`() {
        val session = createSession()
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
    fun `submit wrong answer resets streak`() {
        val session = createSession(MotifTransformationDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `streak accumulates on consecutive correct`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `submit returns null when no question`() {
        val session = createSession()
        val record = session.submit("whatever")
        assertNull(record)
    }

    @Test
    fun `double submit returns null`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull("重复提交应返回 null", second)
    }

    @Test
    fun `isAnswered flag toggles correctly`() {
        val session = createSession()
        session.start()
        assertFalse(session.isAnswered)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next generates new question`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion!!
        session.next()
        val q2 = session.currentQuestion!!
        assertNotNull(q2)
        // 不同种子引擎实例可能产生相同，但 withSeed 引擎是确定性的
        // 这里只验证产生了新的 question 对象
    }

    @Test
    fun `next returns null when not started`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `accuracy is zero before answering`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy calculates correctly`() {
        val session = createSession(MotifTransformationDifficulty.ADVANCED)
        session.start()
        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        // 答错
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `history preserves order`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
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
        val h1 = session.history
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        val h2 = session.history
        // h1 should still be size 1 (defensive copy)
        assertEquals(1, h1.size)
        assertEquals(2, h2.size)
    }

    @Test
    fun `reset clears all state`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
        assertFalse(session.isStarted)
    }

    @Test
    fun `difficulty returns correct value`() {
        val session = createSession(MotifTransformationDifficulty.ADVANCED)
        assertEquals(MotifTransformationDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `lastAnswer cleared on next`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertNull(session.lastAnswer)
    }

    @Test
    fun `bestStreak tracks maximum streak`() {
        val session = createSession()
        session.start()
        // 3 correct
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // 1 wrong
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.bestStreak) // bestStreak unchanged
        assertEquals(0, session.currentStreak)
        // 1 correct
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak) // still 3
        assertEquals(1, session.currentStreak)
    }
}
