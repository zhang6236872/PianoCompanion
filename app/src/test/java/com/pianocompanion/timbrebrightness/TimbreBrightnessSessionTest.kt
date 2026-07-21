package com.pianocompanion.timbrebrightness

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * [TimbreBrightnessSession] 单元测试。
 *
 * 验证会话状态机生命周期、得分、连击、准确率、防御性历史副本等。
 */
class TimbreBrightnessSessionTest {

    private lateinit var session: TimbreBrightnessSession

    @Before
    fun setUp() {
        val engine = TimbreBrightnessEngine.withSeed(42L)
        session = TimbreBrightnessSession(engine, TimbreBrightnessDifficulty.INTERMEDIATE)
    }

    @Test
    fun `session starts with no question`() {
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start generates first question`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `submit before start returns null`() {
        val result = session.submit(TimbreBrightness.PURE.fullLabel)
        assertNull(result)
    }

    @Test
    fun `submit correct answer records success`() {
        session.start()
        val question = session.currentQuestion!!
        val result = session.submit(question.correctAnswer)

        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer records failure`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = TimbreBrightness.ALL.first { it.fullLabel != question.correctAnswer }.fullLabel
        val result = session.submit(wrongAnswer)

        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `double submit is rejected`() {
        session.start()
        val question = session.currentQuestion!!
        session.submit(question.correctAnswer)
        val second = session.submit(question.correctAnswer)
        assertNull("已作答后再次提交应返回 null", second)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `streak increments on consecutive correct answers`() {
        session.start()

        // 第一题答对
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)

        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `streak resets on wrong answer`() {
        session.start()

        // 连续答对两题
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)

        // 答错
        session.next()
        val question = session.currentQuestion!!
        val wrongAnswer = TimbreBrightness.ALL.first { it.fullLabel != question.correctAnswer }.fullLabel
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `accuracy calculates correctly`() {
        session.start()

        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // 答错
        val q2 = session.currentQuestion!!
        val wrong = TimbreBrightness.ALL.first { it.fullLabel != q2.correctAnswer }.fullLabel
        session.submit(wrong)

        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero before any answers`() {
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `next before start returns null`() {
        assertNull(session.next())
    }

    @Test
    fun `next generates new question`() {
        session.start()
        val firstQuestion = session.currentQuestion
        session.submit(firstQuestion!!.correctAnswer)
        session.next()
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `history records all answers in order`() {
        session.start()

        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // 答错
        val q2 = session.currentQuestion!!
        val wrong = TimbreBrightness.ALL.first { it.fullLabel != q2.correctAnswer }.fullLabel
        session.submit(wrong)

        val history = session.history
        assertEquals(2, history.size)
        assertTrue(history[0].isCorrect)
        assertFalse(history[1].isCorrect)
    }

    @Test
    fun `history is a defensive copy`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)

        val history1 = session.history.toMutableList()
        history1.clear() // 修改返回的副本

        val history2 = session.history
        assertEquals("内部历史不应被外部修改影响", 1, history2.size)
    }

    @Test
    fun `reset clears all state`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()

        session.reset()

        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
    }

    @Test
    fun `lastAnswer is set after submit`() {
        session.start()
        assertNull(session.lastAnswer)

        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `lastAnswer cleared on next`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)

        session.next()
        assertNull(session.lastAnswer)
    }

    @Test
    fun `difficulty returns correct difficulty`() {
        assertEquals(TimbreBrightnessDifficulty.INTERMEDIATE, session.difficulty())
    }
}
