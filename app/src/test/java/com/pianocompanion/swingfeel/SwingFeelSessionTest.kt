package com.pianocompanion.swingfeel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SwingFeelSession] 单元测试。
 *
 * 验证会话状态机生命周期、得分、连击、准确率、防御性历史副本等。
 */
class SwingFeelSessionTest {

    private fun newSession(difficulty: SwingDifficulty = SwingDifficulty.ADVANCED): SwingFeelSession =
        SwingFeelSession(SwingFeelEngine.withSeed(42L), difficulty)

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `not started before start`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
    }

    @Test
    fun `start generates first question`() {
        val s = newSession()
        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
        assertFalse(s.isAnswered)
    }

    @Test
    fun `reset clears state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.bestStreak)
    }

    // ── 提交答案 ──────────────────────────────────────────

    @Test
    fun `submit correct answer increments correct and streak`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.bestStreak)
        assertTrue(s.isAnswered)
    }

    @Test
    fun `submit wrong answer breaks streak`() {
        val s = newSession()
        s.start()
        // 先答对一题建立连击
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        // 再答错
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(1, s.bestStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        val s = newSession()
        s.start()
        // 连续答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak)
        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(3, s.bestStreak) // bestStreak 保持
        assertEquals(0, s.currentStreak)
        // 再答对 2 题
        s.next()
        repeat(2) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak) // 仍未超过 3
    }

    @Test
    fun `submit when not started returns null`() {
        val s = newSession()
        assertNull(s.submit("等分"))
    }

    @Test
    fun `double submit returns null second time`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNull(s.submit(s.currentQuestion!!.correctAnswer))
        assertEquals(1, s.answeredCount) // 只计一次
    }

    // ── 下一题 ────────────────────────────────────────────

    @Test
    fun `next advances to new question and clears answered state`() {
        val s = newSession()
        s.start()
        val first = s.currentQuestion
        s.submit(first!!.correctAnswer)
        s.next()
        assertNotNull(s.currentQuestion)
        assertFalse(s.isAnswered)
        assertNull(s.lastAnswer)
    }

    @Test
    fun `next when not started returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `accuracy is zero before any answer`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 1e-9)
    }

    @Test
    fun `accuracy computes correctly`() {
        val s = newSession()
        s.start()
        // 答对
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(0.5, s.accuracy, 1e-9)
    }

    // ── 难度 ──────────────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        val s = newSession(SwingDifficulty.BEGINNER)
        assertEquals(SwingDifficulty.BEGINNER, s.difficulty())
    }

    // ── 历史与防御性副本 ──────────────────────────────────

    @Test
    fun `history records all answers in order`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `history is a defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val snapshot = s.history
        // 尝试修改返回的列表，不应影响内部状态
        try {
            (snapshot as MutableList).clear()
        } catch (_: UnsupportedOperationException) {
            // toMutableList 返回的也可能不可变 —— 两种情况都说明对外隔离
        }
        assertEquals(1, s.history.size)
    }

    @Test
    fun `answer record correct answer is null when correct`() {
        val s = newSession()
        s.start()
        val rec = s.submit(s.currentQuestion!!.correctAnswer)!!
        assertNull(rec.correctAnswer)
    }

    @Test
    fun `answer record correct answer is set when wrong`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val rec = s.submit(q.answerChoices.first { it != q.correctAnswer })!!
        assertEquals(q.correctAnswer, rec.correctAnswer)
    }

    // ── 端到端（确定性种子，连续多题）────────────────────

    @Test
    fun `full session with seeded engine is deterministic`() {
        val s1 = newSession()
        val s2 = newSession()
        s1.start()
        s2.start()
        repeat(10) {
            assertEquals(s1.currentQuestion!!.ratio, s2.currentQuestion!!.ratio)
            // s1 答对，s2 答错（不影响下题）
            s1.submit(s1.currentQuestion!!.correctAnswer)
            val q2 = s2.currentQuestion!!
            s2.submit(q2.answerChoices.first { it != q2.correctAnswer })
            s1.next()
            s2.next()
        }
        assertEquals(10, s1.answeredCount)
        assertEquals(10, s1.correctCount)
        assertEquals(0, s2.correctCount)
    }
}
