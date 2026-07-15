package com.pianocompanion.polyrhythmtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 复合节奏辨识训练会话状态机单元测试。
 */
class PolyrhythmSessionTest {

    private fun createSession(difficulty: PolyrhythmDifficulty = PolyrhythmDifficulty.ADVANCED): PolyrhythmTrainingSession {
        val engine = PolyrhythmTrainingEngine.withSeed(42)
        return PolyrhythmTrainingSession(engine, difficulty)
    }

    // ── 生命周期 ────────────────────────────────────────

    @Test
    fun `未开始时currentQuestion为null`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start后currentQuestion不为null`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后计数器归零`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit("wrong")
        // 再次 start 应归零
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    // ── 答题流程 ────────────────────────────────────────

    @Test
    fun `submit正确答案返回isCorrect=true`() {
        val session = createSession()
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `submit错误答案返回isCorrect=false`() {
        val session = createSession()
        session.start()
        // 找一个错误答案
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
    }

    @Test
    fun `重复submit返回null`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit(session.currentQuestion!!.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `未start时submit返回null`() {
        val session = createSession()
        val record = session.submit("anything")
        assertNull(record)
    }

    // ── 连击追踪 ────────────────────────────────────────

    @Test
    fun `连续答对3题连击为3`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
        assertEquals(3, session.correctCount)
    }

    @Test
    fun `答错后连击归零`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `答错后bestStreak保留`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时准确率为0`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 下一题 ──────────────────────────────────────────

    @Test
    fun `next生成新题目`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q1)
        assertNotNull(q2)
        // 新题目应该有不同的实例
        assertNotSame(q1, q2)
    }

    @Test
    fun `next后isAnswered为false`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未start时next返回null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── 重置 ────────────────────────────────────────────

    @Test
    fun `reset后所有状态清空`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isStarted)
    }

    // ── 历史记录 ────────────────────────────────────────

    @Test
    fun `历史记录按时间顺序`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `lastAnswer指向最后一次答题`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `不同难度题目使用不同复合节奏集合`() {
        val engine = PolyrhythmTrainingEngine.withSeed(42)
        val beginnerSession = PolyrhythmTrainingSession(engine, PolyrhythmDifficulty.BEGINNER)
        val advancedSession = PolyrhythmTrainingSession(engine, PolyrhythmDifficulty.ADVANCED)
        beginnerSession.start()
        advancedSession.start()
        assertTrue(beginnerSession.currentQuestion!!.polyrhythm in PolyrhythmType.BEGINNER_POLYRHYTHMS)
        assertTrue(advancedSession.currentQuestion!!.polyrhythm in PolyrhythmType.ALL)
    }

    @Test
    fun `difficulty方法返回正确难度`() {
        val session = createSession(PolyrhythmDifficulty.INTERMEDIATE)
        assertEquals(PolyrhythmDifficulty.INTERMEDIATE, session.difficulty())
    }

    @Test
    fun `cycleCount方法返回正确周期数`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val session = PolyrhythmTrainingSession(engine, PolyrhythmDifficulty.ADVANCED, cycleCount = 3)
        assertEquals(3, session.cycleCount())
    }
}
