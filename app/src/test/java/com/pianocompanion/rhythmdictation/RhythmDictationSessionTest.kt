package com.pianocompanion.rhythmdictation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 节奏听写会话状态机单元测试。
 */
class RhythmDictationSessionTest {

    private lateinit var session: RhythmDictationSession

    @Before
    fun setUp() {
        val engine = RhythmDictationEngine.withSeed(42)
        session = RhythmDictationSession(
            engine = engine,
            difficulty = RhythmDictationDifficulty.BEGINNER,
            tempo = RhythmDictationTempo.SLOW
        )
    }

    // ── 初始状态 ────────────────────────────────────────

    @Test
    fun `初始状态未开始`() {
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0.0, session.accuracy, 0.001)
        assertTrue(session.history.isEmpty())
    }

    // ── 启动 ────────────────────────────────────────────

    @Test
    fun `start 后有当前题目`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start 后统计清零`() {
        session.start()
        session.submit("wrong answer") // 答错
        session.start() // 重新开始
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    // ── 答题 ────────────────────────────────────────────

    @Test
    fun `submit 正确答案返回 isCorrect true`() {
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        val record = session.submit(correct)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `submit 错误答案返回 isCorrect false`() {
        session.start()
        val record = session.submit("错误答案")
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
    }

    @Test
    fun `submit 正确后统计递增`() {
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        session.submit(correct)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `submit 错误后连击归零`() {
        session.start()
        // 先答对
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        // 下一题答错
        session.next()
        session.submit("错误答案")
        assertEquals(0, session.currentStreak)
        assertEquals(1, session.correctCount)
        assertEquals(2, session.answeredCount)
    }

    @Test
    fun `submit 后 isAnswered 变 true`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `重复 submit 返回 null`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit(session.currentQuestion!!.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `未 start 时 submit 返回 null`() {
        val record = session.submit("anything")
        assertNull(record)
    }

    // ── 连击 ────────────────────────────────────────────

    @Test
    fun `连续答对连击递增`() {
        session.start()
        for (i in 1..5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            assertEquals(i, session.currentStreak)
            session.next()
        }
    }

    @Test
    fun `bestStreak 不降级`() {
        session.start()
        // 连续答对 3 题
        for (i in 1..3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // 答错一题
        session.submit("错误答案")
        assertEquals(3, session.bestStreak)
        assertEquals(0, session.currentStreak)
        // 再答对 2 题
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak) // bestStreak 仍为 3，直到超过
    }

    // ── next ────────────────────────────────────────────

    @Test
    fun `next 生成新题目`() {
        session.start()
        val q1 = session.currentQuestion
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        // 不保证不同（概率上可能相同），但不是同一个引用
        assertNotSame(q1, q2)
    }

    @Test
    fun `next 后 isAnswered 变 false`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未 start 时 next 返回 null`() {
        assertNull(session.next())
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        session.start()
        // 答对 3 题
        for (i in 1..3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错 1 题
        session.submit("错误答案")
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `零答题时准确率为0`() {
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 历史 ────────────────────────────────────────────

    @Test
    fun `历史记录保序`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit("错误答案")
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    // ── reset ───────────────────────────────────────────

    @Test
    fun `reset 清空所有状态`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit("错误答案")
        session.reset()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
    }

    // ── 难度/速度/重复次数 ──────────────────────────────

    @Test
    fun `difficulty 返回正确值`() {
        val s = RhythmDictationSession(
            RhythmDictationEngine.withSeed(1),
            RhythmDictationDifficulty.ADVANCED,
            RhythmDictationTempo.FAST,
            repeatCount = 3
        )
        assertEquals(RhythmDictationDifficulty.ADVANCED, s.difficulty())
        assertEquals(RhythmDictationTempo.FAST, s.tempo())
        assertEquals(3, s.repeatCount())
    }

    // ── 多轮答题 ────────────────────────────────────────

    @Test
    fun `多轮答题统计累积`() {
        session.start()
        for (i in 1..10) {
            session.submit(session.currentQuestion!!.correctAnswer)
            if (i < 10) session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(10, session.correctCount)
        assertEquals(10, session.currentStreak)
        assertEquals(10, session.bestStreak)
        assertEquals(1.0, session.accuracy, 0.001)
    }

    @Test
    fun `AnswerRecord correctAnswer 答对时为 null`() {
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `AnswerRecord correctAnswer 答错时为正确答案`() {
        session.start()
        val record = session.submit("错误")!!
        assertEquals(session.currentQuestion!!.correctAnswer, record.correctAnswer)
    }
}
