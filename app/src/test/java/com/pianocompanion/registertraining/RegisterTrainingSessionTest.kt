package com.pianocompanion.registertraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音区辨识训练会话状态机单元测试。
 */
class RegisterTrainingSessionTest {

    private fun createSession(difficulty: RegisterTrainingDifficulty = RegisterTrainingDifficulty.BEGINNER): RegisterTrainingSession {
        return RegisterTrainingSession(RegisterTrainingEngine.withSeed(42), difficulty)
    }

    // ── 初始状态 ──────────────────────────────────────

    @Test
    fun `初始状态未开始`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isAnswered)
        assertNull(session.lastAnswer)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `初始准确率为0`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── start ──────────────────────────────────────────

    @Test
    fun `start后生成第一题`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后计数器归零`() {
        val session = createSession()
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start后isAnswered为false`() {
        val session = createSession()
        session.start()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `多次start重置状态`() {
        val session = createSession()
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        session.submit(correctAnswer)
        assertEquals(1, session.answeredCount)

        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertNull(session.lastAnswer)
    }

    // ── submit ─────────────────────────────────────────

    @Test
    fun `submit正确答案返回正确记录`() {
        val session = createSession()
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val record = session.submit(correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(correctAnswer, record.userAnswer)
    }

    @Test
    fun `submit错误答案返回错误记录`() {
        val session = createSession(RegisterTrainingDifficulty.ADVANCED)
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val wrongAnswer = session.currentQuestion!!.answerChoices.filter { it != correctAnswer }.first()
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(wrongAnswer, record.userAnswer)
    }

    @Test
    fun `submit后answeredCount增加`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `submit正确后correctCount和Streak增加`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `submit错误后Streak归零`() {
        val session = createSession(RegisterTrainingDifficulty.ADVANCED)
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        session.submit(correctAnswer)
        assertEquals(1, session.currentStreak)

        session.next()
        val wrongAnswer = session.currentQuestion!!.answerChoices.filter { it != session.currentQuestion!!.correctAnswer }.first()
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `submit后isAnswered为true`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `已答题后再submit返回null`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val result = session.submit(session.currentQuestion!!.correctAnswer)
        assertNull(result)
    }

    @Test
    fun `未开始时submit返回null`() {
        val session = createSession()
        val result = session.submit("任意答案")
        assertNull(result)
    }

    // ── next ───────────────────────────────────────────

    @Test
    fun `next后生成新题目`() {
        val session = createSession()
        session.start()
        val firstQuestion = session.currentQuestion
        session.next()
        assertNotNull(session.currentQuestion)
        // 新题目是不同实例
        assertNotSame(firstQuestion, session.currentQuestion)
    }

    @Test
    fun `next后isAnswered为false`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时next返回null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── streak 追踪 ────────────────────────────────────

    @Test
    fun `连击正确后bestStreak更新`() {
        val session = createSession(RegisterTrainingDifficulty.ADVANCED)
        session.start()

        // 答对3题
        for (i in 1..3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        assertEquals(3, session.correctCount)
    }

    @Test
    fun `连击中断后bestStreak保持`() {
        val session = createSession(RegisterTrainingDifficulty.ADVANCED)
        session.start()

        // 答对2题
        for (i in 1..2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错一题
        val wrongAnswer = session.currentQuestion!!.answerChoices.filter { it != session.currentQuestion!!.correctAnswer }.first()
        session.submit(wrongAnswer)
        session.next()
        assertEquals(2, session.bestStreak)
        assertEquals(0, session.currentStreak)
    }

    // ── accuracy ───────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        val session = createSession(RegisterTrainingDifficulty.ADVANCED)
        session.start()

        // 答对3题，答错1题
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.filter { it != session.currentQuestion!!.correctAnswer }.first()
        session.submit(wrong)

        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    // ── history ────────────────────────────────────────

    @Test
    fun `history按顺序记录`() {
        val session = createSession(RegisterTrainingDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.filter { it != session.currentQuestion!!.correctAnswer }.first()
        session.submit(wrong)

        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `start清空history`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.history.size)

        session.start()
        assertTrue(session.history.isEmpty())
    }

    // ── reset ──────────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()

        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
        assertFalse(session.isStarted)
    }

    // ── difficulty ─────────────────────────────────────

    @Test
    fun `difficulty返回正确难度`() {
        for (d in RegisterTrainingDifficulty.ALL) {
            val session = createSession(d)
            assertEquals(d, session.difficulty())
        }
    }
}
