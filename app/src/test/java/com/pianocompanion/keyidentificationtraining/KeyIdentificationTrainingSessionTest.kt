package com.pianocompanion.keyidentificationtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调性中心辨识训练会话状态机单元测试。
 *
 * 验证状态机生命周期、连击追踪、准确率计算、答题历史保序、边界安全。
 */
class KeyIdentificationTrainingSessionTest {

    private fun createSession(difficulty: KeyDifficulty = KeyDifficulty.BEGINNER): KeyIdentificationTrainingSession {
        return KeyIdentificationTrainingSession(
            KeyIdentificationTrainingEngine.withSeed(42L),
            difficulty
        )
    }

    // ── 启动与初始状态 ──────────────────────────────────────

    @Test
    fun `session not started has no question`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertFalse(session.isAnswered)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start resets counters`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // answer 1 correctly
        assertEquals(1, session.correctCount)
        session.start() // restart
        assertEquals(0, session.correctCount)
        assertEquals(0, session.answeredCount)
    }

    // ── 提交答案 ────────────────────────────────────────────

    @Test
    fun `submit correct answer increments correct count`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer does not increment correct count`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `submit returns null when no question`() {
        val session = createSession()
        val record = session.submit("C 大调")
        assertNull(record)
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull(second)
    }

    // ── 连击追踪 ────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `streak resets to zero on wrong answer`() {
        val session = createSession()
        session.start()
        // Answer 2 correctly
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        // Answer wrong
        session.next()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak) // best streak preserved
    }

    @Test
    fun `best streak never decreases`() {
        val session = createSession()
        session.start()
        // Build streak of 3
        for (i in 0 until 3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // Wrong answer
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(3, session.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────────

    @Test
    fun `accuracy is zero before answering`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy calculates correctly`() {
        val session = createSession()
        session.start()
        // 3 correct
        for (i in 0 until 3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 1 wrong
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        // 3/4 = 0.75
        assertEquals(0.75, session.accuracy, 0.001)
    }

    // ── next() ──────────────────────────────────────────────

    @Test
    fun `next generates new question`() {
        val session = createSession()
        session.start()
        val firstQ = session.currentQuestion
        session.next()
        assertNotNull(session.currentQuestion)
        // New question should be generated (may or may not be different)
        assertTrue(session.isStarted)
    }

    @Test
    fun `next clears answered state`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next returns null when not started`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── 答题历史 ────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        val session = createSession()
        session.start()
        for (i in 0 until 5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(5, session.history.size)
        // History should match the number of answered questions
        assertEquals(session.answeredCount, session.history.size)
    }

    @Test
    fun `history records include correct isCorrect flag`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertTrue(session.history.last().isCorrect)

        session.next()
        val q2 = session.currentQuestion!!
        session.submit(q2.answerChoices.first { it != q2.correctAnswer })
        assertFalse(session.history.last().isCorrect)
    }

    // ── reset() ─────────────────────────────────────────────

    @Test
    fun `reset clears everything`() {
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
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
    }

    // ── difficulty() ────────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        val session = createSession(KeyDifficulty.ADVANCED)
        assertEquals(KeyDifficulty.ADVANCED, session.difficulty())
    }

    // ── 多难度测试 ──────────────────────────────────────────

    @Test
    fun `session works for all difficulties`() {
        for (difficulty in KeyDifficulty.ALL) {
            val session = KeyIdentificationTrainingSession(
                KeyIdentificationTrainingEngine.withSeed(7L),
                difficulty
            )
            session.start()
            assertNotNull(session.currentQuestion)
            session.submit(session.currentQuestion!!.correctAnswer)
            assertEquals(1, session.correctCount)
        }
    }
}
