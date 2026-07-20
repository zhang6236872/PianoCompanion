package com.pianocompanion.harmonycolor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和声色彩听辨训练会话状态机单元测试。
 */
class HarmonyColorSessionTest {

    private fun newSession(difficulty: HarmonyColorDifficulty = HarmonyColorDifficulty.BEGINNER): HarmonyColorSession {
        return HarmonyColorSession(HarmonyColorEngine.withSeed(42), difficulty)
    }

    @Test
    fun `session starts not started`() {
        val session = newSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
    }

    @Test
    fun `start sets current question`() {
        val session = newSession()
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start resets all stats`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `submit correct answer increments correct and streak`() {
        val session = newSession()
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `submit wrong answer resets streak`() {
        val session = newSession(HarmonyColorDifficulty.ADVANCED)
        session.start()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `submit returns null when no current question`() {
        val session = newSession()
        val record = session.submit("anything")
        assertNull(record)
    }

    @Test
    fun `double submit returns null on second`() {
        val session = newSession()
        session.start()
        val first = session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(first)
        assertNull(second)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `next generates new question after answering`() {
        val session = newSession()
        session.start()
        val firstQuestion = session.currentQuestion
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertNotNull(session.currentQuestion)
        // 新题目可能相同（随机），但 isAnswered 应为 false
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next returns null when session not started`() {
        val session = newSession()
        assertNull(session.next())
    }

    @Test
    fun `streak accumulates across correct answers`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.correctCount)
        assertEquals(2, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `best streak preserved after wrong answer`() {
        val session = newSession(HarmonyColorDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // Now streak = 2, bestStreak = 2
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `accuracy calculation`() {
        val session = newSession(HarmonyColorDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        // 1 correct / 2 answered = 0.5
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero before answering`() {
        val session = newSession()
        assertEquals(0.0, session.accuracy, 0.001)
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `reset clears everything`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isStarted)
    }

    @Test
    fun `history records all answers`() {
        val session = newSession(HarmonyColorDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(2, session.history.size)
    }

    @Test
    fun `history is defensive copy`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history1 = session.history
        val history2 = session.history
        // 不是同一引用
        assertTrue(history1 !== history2)
        // 修改副本不影响内部
        (history1 as MutableList).clear()
        assertEquals(1, session.history.size)
        assertEquals(1, history2.size)
    }

    @Test
    fun `last answer is set after submit`() {
        val session = newSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
    }

    @Test
    fun `last answer cleared after next`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertNull(session.lastAnswer)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `difficulty returns correct difficulty`() {
        HarmonyColorDifficulty.ALL.forEach { d ->
            val session = HarmonyColorSession(HarmonyColorEngine.withSeed(1), d)
            assertEquals(d, session.difficulty())
        }
    }

    @Test
    fun `isAnswered is true after submit and false after next`() {
        val session = newSession()
        session.start()
        assertFalse(session.isAnswered)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }
}
