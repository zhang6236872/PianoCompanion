package com.pianocompanion.progressiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦进行听辨训练会话状态机单元测试。
 *
 * 验证生命周期、连击追踪、准确率计算、答题历史、边界安全。
 */
class ProgressionTrainingSessionTest {

    private fun createSession(difficulty: ProgressionDifficulty = ProgressionDifficulty.BEGINNER): ProgressionTrainingSession {
        return ProgressionTrainingSession(
            ProgressionTrainingEngine.withSeed(42L),
            difficulty
        )
    }

    // ── 生命周期 ────────────────────────────────────────────

    @Test
    fun `start generates first question`() {
        val session = createSession()
        assertFalse(session.isStarted)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start resets stats`() {
        val session = createSession()
        session.start()
        // answer one correctly
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.answeredCount > 0)
        // restart
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `next generates new question after answer`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion
        session.submit(q1!!.correctAnswer)
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
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

    // ── 连击追踪 ────────────────────────────────────────────

    @Test
    fun `correct answer increments streak`() {
        val session = createSession()
        session.start()
        assertEquals(0, session.currentStreak)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `wrong answer resets streak`() {
        val session = createSession()
        session.start()
        // First correct
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        session.next()
        // Then wrong
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val session = createSession()
        session.start()
        // Build streak of 3
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // Wrong answer
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.bestStreak) // unchanged
    }

    // ── 准确率 ──────────────────────────────────────────────

    @Test
    fun `accuracy is correct after mixed answers`() {
        val session = createSession(ProgressionDifficulty.ADVANCED)
        session.start()
        // 2 correct, 1 wrong = 0.667
        session.submit(session.currentQuestion!!.correctAnswer) // correct
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // correct
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong) // wrong
        assertEquals(3, session.answeredCount)
        assertEquals(2, session.correctCount)
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 0 before any answers`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 答题历史 ────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        val session = createSession(ProgressionDifficulty.ADVANCED)
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            if (it < 4) session.next()
        }
        assertEquals(5, session.history.size)
        assertTrue(session.history.all { it.isCorrect })
    }

    @Test
    fun `history stores wrong answers correctly`() {
        val session = createSession(ProgressionDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrong)
        assertNotNull(record)
        assertEquals(1, session.history.size)
        assertFalse(session.history[0].isCorrect)
    }

    @Test
    fun `lastAnswer is updated after submit`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(record, session.lastAnswer)
    }

    // ── 边界安全 ────────────────────────────────────────────

    @Test
    fun `submit without start returns null`() {
        val session = createSession()
        val result = session.submit("anything")
        assertNull(result)
    }

    @Test
    fun `double submit returns null on second`() {
        val session = createSession()
        session.start()
        val first = session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun `next without start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── 重置 ────────────────────────────────────────────────

    @Test
    fun `reset clears everything`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
        assertNull(session.lastAnswer)
    }

    // ── 全难度生命周期 ──────────────────────────────────────

    @Test
    fun `full lifecycle for all difficulties`() {
        for (difficulty in ProgressionDifficulty.ALL) {
            val session = ProgressionTrainingSession(
                ProgressionTrainingEngine.withSeed(99L),
                difficulty
            )
            session.start()
            assertNotNull(session.currentQuestion)
            repeat(3) {
                session.submit(session.currentQuestion!!.correctAnswer)
                assertTrue(session.isAnswered)
                session.next()
            }
            assertEquals(3, session.answeredCount)
            assertEquals(3, session.correctCount)
            assertEquals(1.0, session.accuracy, 0.001)
        }
    }

    // ── difficulty accessor ─────────────────────────────────

    @Test
    fun `difficulty accessor returns correct value`() {
        val session = createSession(ProgressionDifficulty.ADVANCED)
        assertEquals(ProgressionDifficulty.ADVANCED, session.difficulty())
    }
}
