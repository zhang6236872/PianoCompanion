package com.pianocompanion.melodicdirectiontraining

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 旋律方向辨识训练会话状态机单元测试。
 */
class MelodicDirectionSessionTest {

    private lateinit var session: MelodicDirectionSession

    @Before
    fun setUp() {
        session = MelodicDirectionSession(
            MelodicDirectionEngine.withSeed(42),
            MelodicDirectionDifficulty.INTERMEDIATE
        )
    }

    // ── 生命周期 ────────────────────────────────────────

    @Test
    fun `未开始会话时currentQuestion为null`() {
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start后currentQuestion非null`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后计数器归零`() {
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start后isAnswered为false`() {
        session.start()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start后lastAnswer为null`() {
        session.start()
        assertNull(session.lastAnswer)
    }

    // ── 答题 ────────────────────────────────────────────

    @Test
    fun `答对后correctCount增加`() {
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val result = session.submit(correctAnswer)
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
    }

    @Test
    fun `答错后correctCount不变`() {
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val wrongAnswer = session.currentQuestion!!.answerChoices
            .filter { it != correctAnswer }
            .firstOrNull() ?: return
        val result = session.submit(wrongAnswer)
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `未开始时submit返回null`() {
        val result = session.submit("随便答")
        assertNull(result)
    }

    @Test
    fun `已作答后重复submit返回null`() {
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        session.submit(correctAnswer)
        val result2 = session.submit(correctAnswer)
        assertNull(result2)
    }

    // ── 连击 ────────────────────────────────────────────

    @Test
    fun `连续答对增加连击`() {
        session.start()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `答错重置连击`() {
        session.start()
        // 先答对 2 题
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(2, session.currentStreak)
        // 答错一题
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val wrongAnswer = session.currentQuestion!!.answerChoices.first { it != correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak) // bestStreak 不降
    }

    @Test
    fun `bestStreak记录历史最长`() {
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val wrongAnswer = session.currentQuestion!!.answerChoices.first { it != correctAnswer }
        session.submit(wrongAnswer)
        // 再答对 1 题
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── 下一题 ──────────────────────────────────────────

    @Test
    fun `next后isAnswered重置`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next后生成新题目`() {
        session.start()
        val q1 = session.currentQuestion
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q1)
        assertNotNull(q2)
    }

    @Test
    fun `未开始时next返回null`() {
        val result = session.next()
        assertNull(result)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `未答题准确率为0`() {
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `全对准确率为1`() {
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(1.0, session.accuracy, 0.001)
    }

    @Test
    fun `半对半错准确率为0_5`() {
        session.start()
        for (i in 0 until 4) {
            if (i % 2 == 0) {
                session.submit(session.currentQuestion!!.correctAnswer)
            } else {
                val correct = session.currentQuestion!!.correctAnswer
                val wrong = session.currentQuestion!!.answerChoices.first { it != correct }
                session.submit(wrong)
            }
            session.next()
        }
        assertEquals(0.5, session.accuracy, 0.001)
    }

    // ── 历史 ────────────────────────────────────────────

    @Test
    fun `历史记录按顺序保存`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val correct = session.currentQuestion!!.correctAnswer
        val wrong = session.currentQuestion!!.answerChoices.first { it != correct }
        session.submit(wrong)
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    // ── 重置 ────────────────────────────────────────────

    @Test
    fun `reset后所有状态清空`() {
        session.start()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isStarted)
    }

    // ── difficulty ──────────────────────────────────────

    @Test
    fun `difficulty返回正确难度`() {
        val s = MelodicDirectionSession(
            MelodicDirectionEngine(),
            MelodicDirectionDifficulty.ADVANCED
        )
        assertEquals(MelodicDirectionDifficulty.ADVANCED, s.difficulty())
    }
}
