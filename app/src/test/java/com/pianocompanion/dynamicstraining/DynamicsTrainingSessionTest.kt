package com.pianocompanion.dynamicstraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 力度辨识会话状态机单元测试。
 */
class DynamicsTrainingSessionTest {

    private fun createSession(
        difficulty: DynamicsTrainingDifficulty = DynamicsTrainingDifficulty.BEGINNER,
        seed: Long = 42
    ): DynamicsTrainingSession {
        val engine = DynamicsTrainingEngine.withSeed(seed)
        return DynamicsTrainingSession(engine, difficulty)
    }

    // ── 生命周期 ──────────────────────────────────────

    @Test
    fun `新建会话未开始`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start后生成第一题`() {
        val session = createSession()
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start重置统计`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 答对
        assertEquals(1, session.correctCount)

        session.start() // 重新开始
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    // ── submit ──────────────────────────────────────

    @Test
    fun `submit正确答案返回isCorrect为true`() {
        val session = createSession()
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `submit错误答案返回isCorrect为false`() {
        val session = createSession()
        session.start()
        val wrongAnswer = DynamicLevel.ALL
            .filter { it.fullLabel != session.currentQuestion!!.correctAnswer }
            .first().fullLabel
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
    }

    @Test
    fun `submit后isAnswered为true`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `重复submit返回null`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val result = session.submit("wrong")
        assertNull(result)
    }

    @Test
    fun `未开始时submit返回null`() {
        val session = createSession()
        val result = session.submit("anything")
        assertNull(result)
    }

    // ── 统计跟踪 ──────────────────────────────────────

    @Test
    fun `答对增加correctCount和currentStreak`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `答错不增加correctCount且currentStreak归零`() {
        val session = createSession()
        session.start()
        // 先答对一题
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // 再答错一题
        val wrongAnswer = DynamicLevel.ALL
            .filter { it.fullLabel != session.currentQuestion!!.correctAnswer }
            .first().fullLabel
        session.submit(wrongAnswer)
        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `连击记录bestStreak`() {
        val session = createSession()
        session.start()
        for (i in 1..5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(5, session.bestStreak)
        assertEquals(5, session.currentStreak)
    }

    @Test
    fun `连击中断后bestStreak不降`() {
        val session = createSession()
        session.start()
        // 3 连击
        for (i in 1..3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错
        val wrongAnswer = DynamicLevel.ALL
            .filter { it.fullLabel != session.currentQuestion!!.correctAnswer }
            .first().fullLabel
        session.submit(wrongAnswer)
        assertEquals(3, session.bestStreak)
        assertEquals(0, session.currentStreak)
        // 再答对
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak)
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `accuracy计算正确`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrongAnswer = DynamicLevel.ALL
            .filter { it.fullLabel != session.currentQuestion!!.correctAnswer }
            .first().fullLabel
        session.submit(wrongAnswer)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时accuracy为0`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── next ──────────────────────────────────────

    @Test
    fun `next生成新题目`() {
        val session = createSession()
        session.start()
        val firstQuestion = session.currentQuestion
        session.next()
        assertNotNull(session.currentQuestion)
        assertNotSame(firstQuestion, session.currentQuestion)
    }

    @Test
    fun `next重置isAnswered`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时next返回null`() {
        val session = createSession()
        val result = session.next()
        assertNull(result)
    }

    // ── reset ──────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isAnswered)
    }

    // ── history ──────────────────────────────────────

    @Test
    fun `history按时间顺序记录`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrongAnswer = DynamicLevel.ALL
            .filter { it.fullLabel != session.currentQuestion!!.correctAnswer }
            .first().fullLabel
        session.submit(wrongAnswer)
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `lastAnswer记录最近一次答题`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    // ── difficulty 和 noteCount ──────────────────────

    @Test
    fun `difficulty返回正确难度`() {
        val session = createSession(DynamicsTrainingDifficulty.ADVANCED)
        assertEquals(DynamicsTrainingDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `noteCount返回正确值`() {
        val engine = DynamicsTrainingEngine()
        val session = DynamicsTrainingSession(engine, DynamicsTrainingDifficulty.BEGINNER, noteCount = 6)
        assertEquals(6, session.noteCount())
    }

    @Test
    fun `题目难度与session一致`() {
        val session = createSession(DynamicsTrainingDifficulty.INTERMEDIATE)
        session.start()
        assertEquals(DynamicsTrainingDifficulty.INTERMEDIATE, session.currentQuestion!!.difficulty)
    }
}
