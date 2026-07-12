package com.pianocompanion.nonscaletonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调外音听辨训练会话状态机单元测试。
 *
 * 覆盖：启动/答题/切换状态流转、连击统计、历史记录、边界条件。
 */
class NonScaleToneTrainingSessionTest {

    private fun newEngine(): NonScaleToneTrainingEngine = NonScaleToneTrainingEngine.withSeed(42L)

    private fun newSession(difficulty: NonScaleToneDifficulty = NonScaleToneDifficulty.INTERMEDIATE): NonScaleToneTrainingSession {
        return NonScaleToneTrainingSession(newEngine(), difficulty)
    }

    // ── 初始状态 ──────────────────────────────────────────────

    @Test
    fun `new session is not started`() {
        val session = newSession()
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertNull(session.currentQuestion)
        assertNull(session.lastAnswer)
        assertFalse(session.isAnswered)
    }

    // ── 启动会话 ──────────────────────────────────────────────

    @Test
    fun `start sets first question and activates session`() {
        val session = newSession()
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start resets previous stats`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 正确
        session.start() // 重新开始
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `start always produces a valid question`() {
        for (difficulty in NonScaleToneDifficulty.ALL) {
            val session = NonScaleToneTrainingSession(newEngine(), difficulty)
            session.start()
            val q = session.currentQuestion
            assertNotNull("${difficulty.displayName} 应生成题目", q)
            assertEquals(NonScaleToneDifficulty.typesForDifficulty(difficulty).size, q!!.answerChoices.size)
            assertTrue(q.correctAnswer in q.answerChoices)
        }
    }

    // ── 正确答题 ──────────────────────────────────────────────

    @Test
    fun `correct answer increments counts`() {
        val session = newSession()
        session.start()
        val q = session.currentQuestion!!
        val result = session.submit(q.correctAnswer)
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `correct answer sets isAnswered`() {
        val session = newSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `correct answer creates result with question`() {
        val session = newSession()
        session.start()
        val q = session.currentQuestion!!
        val result = session.submit(q.correctAnswer)
        assertEquals(q, result!!.question)
        assertEquals(q.correctAnswer, result.userAnswer)
        assertTrue(result.isCorrect)
    }

    // ── 错误答题 ──────────────────────────────────────────────

    @Test
    fun `incorrect answer does not increment correct count`() {
        val session = newSession()
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val result = session.submit(wrongAnswer)
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `incorrect answer resets streak`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 正确 → streak=1
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong) // 错误 → streak=0
        assertEquals(0, session.currentStreak)
    }

    // ── 连击统计 ──────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct answers`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 1
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 2
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 3
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `streak resets on wrong answer then restarts on correct`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 1
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer }) // 0
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 1
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `best streak persists even after reset`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 1
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 2
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 3
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer }) // 0
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── 切换下一题 ────────────────────────────────────────────

    @Test
    fun `next sets new question and clears isAnswered`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `next before start returns null`() {
        val session = newSession()
        val result = session.next()
        assertNull(result)
    }

    // ── 重复答题返回 null ────────────────────────────────────

    @Test
    fun `submitting twice returns null second time`() {
        val session = newSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull("第二次答题应返回 null", second)
    }

    @Test
    fun `submit before start returns null`() {
        val session = newSession()
        val result = session.submit("调内（自然大调）")
        assertNull(result)
    }

    // ── 结束会话 ──────────────────────────────────────────────

    @Test
    fun `reset clears state`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertNull(session.currentQuestion)
        assertNull(session.lastAnswer)
        assertFalse(session.isStarted)
    }

    // ── 历史记录 ──────────────────────────────────────────────

    @Test
    fun `history accumulates answers`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.history.size)
    }

    @Test
    fun `history records correct and incorrect`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 正确
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer }) // 错误
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `reset clears history`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertEquals(0, session.history.size)
    }

    // ── 准确率计算 ────────────────────────────────────────────

    @Test
    fun `accuracy is correct fraction`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 1/1
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 2/2
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer }) // 2/3
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 0 when no answers`() {
        val session = newSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 难度记忆 ──────────────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        for (difficulty in NonScaleToneDifficulty.ALL) {
            val session = NonScaleToneTrainingSession(newEngine(), difficulty)
            assertEquals(difficulty, session.difficulty())
        }
    }

    // ── 多轮答题 ──────────────────────────────────────────────

    @Test
    fun `50 rounds all correct`() {
        val session = NonScaleToneTrainingSession(
            NonScaleToneTrainingEngine.withSeed(123L),
            NonScaleToneDifficulty.ADVANCED
        )
        session.start()
        var correctCount = 0
        for (i in 0 until 50) {
            val q = session.currentQuestion!!
            val result = session.submit(q.correctAnswer)
            if (result!!.isCorrect) correctCount++
            session.next()
        }
        assertEquals(50, session.answeredCount)
        assertEquals(50, correctCount)
        assertEquals(50, session.correctCount)
    }

    @Test
    fun `beginner session only generates first 3 types`() {
        val session = NonScaleToneTrainingSession(
            NonScaleToneTrainingEngine.withSeed(999L),
            NonScaleToneDifficulty.BEGINNER
        )
        val allowed = NonScaleToneDifficulty.typesForDifficulty(NonScaleToneDifficulty.BEGINNER).toSet()
        session.start()
        for (i in 0 until 30) {
            val q = session.currentQuestion!!
            assertTrue(
                "初级只应出前 3 种类型 (实际 ${q.type})",
                q.type in allowed
            )
            session.submit(q.correctAnswer)
            session.next()
        }
    }
}
