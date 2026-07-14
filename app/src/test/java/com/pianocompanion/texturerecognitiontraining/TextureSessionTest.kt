package com.pianocompanion.texturerecognitiontraining

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 织体辨识训练会话状态机单元测试。
 *
 * 验证会话生命周期、状态转换、连击/准确率计算。
 */
class TextureSessionTest {

    private lateinit var engine: TextureEngine
    private lateinit var session: TextureSession

    @Before
    fun setUp() {
        engine = TextureEngine.withSeed(42L)
        session = TextureSession(engine, TextureDifficulty.INTERMEDIATE)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `初始状态未开始`() {
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertFalse(session.isAnswered)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start 生成第一题`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start 重置统计`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)

        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    // ── 答题 ──────────────────────────────────────────

    @Test
    fun `submit 正确答案返回 isCorrect=true`() {
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `submit 错误答案返回 isCorrect=false`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
    }

    @Test
    fun `submit 后 isAnswered=true`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `已答题后再次 submit 返回 null`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val result = session.submit(session.currentQuestion!!.correctAnswer)
        assertNull(result)
    }

    @Test
    fun `未开始时 submit 返回 null`() {
        val record = session.submit("任意答案")
        assertNull(record)
    }

    // ── 统计 ──────────────────────────────────────────

    @Test
    fun `答对增加 correctCount 和 currentStreak`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `答错不增加 correctCount，currentStreak 归零`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `连续答对更新 bestStreak`() {
        session.start()
        // 连续答对 3 题
        for (i in 0 until 3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `连击中断后 bestStreak 保持最大值`() {
        session.start()
        // 先连对 2 题
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertEquals(2, session.bestStreak)
        // 答错一题
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(2, session.bestStreak)
        assertEquals(0, session.currentStreak)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        session.start()
        // 答对 3 题，答错 1 题 = 75%
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        session.submit(wrongAnswer)

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
    fun `next 生成新题目`() {
        session.start()
        val q1 = session.currentQuestion
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        // 种子不同 → 可能是不同的题目
        if (q1 != null && q2 != null) {
            assertNotEquals(q1.seed, q2.seed)
        }
    }

    @Test
    fun `next 后 isAnswered=false`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时 next 返回 null`() {
        val result = session.next()
        assertNull(result)
    }

    // ── reset ──────────────────────────────────────────

    @Test
    fun `reset 清空所有统计`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertFalse(session.isStarted)
    }

    // ── 历史 ──────────────────────────────────────────

    @Test
    fun `history 记录所有答题`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)

        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `lastAnswer 记录最近一次答题`() {
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `不同难度会话独立`() {
        val s1 = TextureSession(TextureEngine.withSeed(1L), TextureDifficulty.BEGINNER)
        val s2 = TextureSession(TextureEngine.withSeed(1L), TextureDifficulty.ADVANCED)
        s1.start()
        s2.start()
        assertNotEquals(s1.difficulty(), s2.difficulty())
    }
}
