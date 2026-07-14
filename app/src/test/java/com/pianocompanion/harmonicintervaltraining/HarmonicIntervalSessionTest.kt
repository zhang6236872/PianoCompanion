package com.pianocompanion.harmonicintervaltraining

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 和声音程辨识训练会话状态机单元测试。
 *
 * 验证会话生命周期、状态转换、连击/准确率计算。
 */
class HarmonicIntervalSessionTest {

    private lateinit var engine: HarmonicIntervalEngine
    private lateinit var session: HarmonicIntervalSession

    @Before
    fun setUp() {
        engine = HarmonicIntervalEngine.withSeed(42L)
        session = HarmonicIntervalSession(engine, HarmonicIntervalDifficulty.BEGINNER)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `初始状态未开始`() {
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `start后生成第一题`() {
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start清空历史统计`() {
        // 先做几题
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!
        session.submit("错误答案")

        // 重新 start
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
    }

    // ── 答题 ──────────────────────────────────────────

    @Test
    fun `答对增加correctCount和streak`() {
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)

        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `答错不增加correctCount且streak归零`() {
        session.start()
        session.submit("错误答案")

        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `已答题后再次submit返回null`() {
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `未开始时submit返回null`() {
        val record = session.submit("anything")
        assertNull(record)
    }

    // ── 连击 ──────────────────────────────────────────

    @Test
    fun `连续答对更新bestStreak`() {
        session.start()

        for (i in 1..5) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            assertEquals(i, session.currentStreak)
            assertEquals(i, session.bestStreak)
            session.next()
        }
    }

    @Test
    fun `答错后streak归零`() {
        session.start()
        // 连续答对 3 次
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)

        // 答错一次
        session.submit("错误答案")
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `bestStreak保持历史最大值`() {
        session.start()
        // 连对 4
        repeat(4) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        // 答错
        session.submit("错误答案")
        // 连对 2
        repeat(2) {
            session.next()
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
        }

        assertEquals(4, session.bestStreak)
        assertEquals(2, session.currentStreak)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        session.start()
        // 用固定种子的引擎可能不总是可控，这里直接测试比例
        // 答对 3，答错 1
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        session.submit("错误答案")

        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时准确率为0`() {
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── next ──────────────────────────────────────────

    @Test
    fun `next生成新题目`() {
        session.start()
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!

        assertNotNull(q2)
        assertNotSame(q1, q2)
    }

    @Test
    fun `next后isAnswered为false`() {
        session.start()
        session.submit("错误答案")
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时next返回null`() {
        val q = session.next()
        assertNull(q)
    }

    // ── reset ──────────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        session.next()

        session.reset()
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

    // ── difficulty ──────────────────────────────────────────

    @Test
    fun `difficulty返回正确难度`() {
        val s = HarmonicIntervalSession(
            HarmonicIntervalEngine(),
            HarmonicIntervalDifficulty.ADVANCED
        )
        assertEquals(HarmonicIntervalDifficulty.ADVANCED, s.difficulty())
    }

    // ── 历史 ──────────────────────────────────────────

    @Test
    fun `历史记录按时间顺序`() {
        session.start()

        val records = mutableListOf<HarmonicIntervalAnswerRecord>()
        repeat(3) {
            val q = session.currentQuestion!!
            val r = session.submit(q.correctAnswer)
            if (r != null) records.add(r)
            session.next()
        }

        assertEquals(3, session.history.size)
        assertEquals(records, session.history)
    }

    @Test
    fun `lastAnswer保存最近一次答题`() {
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)

        assertEquals(record, session.lastAnswer)
    }

    // ── 答题记录属性 ──────────────────────────────────────────

    @Test
    fun `答对时correctAnswer为null`() {
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)

        assertTrue(record!!.isCorrect)
        assertNull(record.correctAnswer)
    }

    @Test
    fun `答错时correctAnswer返回正确答案`() {
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit("错误答案")

        assertFalse(record!!.isCorrect)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }
}
