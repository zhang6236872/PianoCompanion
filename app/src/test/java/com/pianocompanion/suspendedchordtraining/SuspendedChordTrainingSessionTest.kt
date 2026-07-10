package com.pianocompanion.suspendedchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挂留和弦品质听辨训练会话状态机单元测试。
 *
 * 验证状态机生命周期、连击追踪、准确率计算、答题历史、边界安全。
 */
class SuspendedChordTrainingSessionTest {

    private fun createSession(difficulty: SuspendedChordDifficulty = SuspendedChordDifficulty.BEGINNER): SuspendedChordTrainingSession {
        return SuspendedChordTrainingSession(
            SuspendedChordTrainingEngine.withSeed(42L),
            difficulty
        )
    }

    // ── 状态机生命周期 ────────────────────────────────────────

    @Test
    fun `session starts with null question`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    private fun assertNotNull(value: Any?) {
        assertTrue("应为非 null", value != null)
    }

    @Test
    fun `submit before start returns null`() {
        val session = createSession()
        assertNull(session.submit("大三和弦"))
    }

    @Test
    fun `next before start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `submit correct answer records correctly`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)!!
        assertTrue(record.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer records incorrectly`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        // Find a wrong answer
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)!!
        assertFalse(record.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `double submit returns null`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNull(session.submit(session.currentQuestion!!.correctAnswer))
    }

    // ── 连击追踪 ──────────────────────────────────────────────

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
    fun `streak resets on wrong answer`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        // Wrong answer
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val session = createSession()
        session.start()
        // Build streak to 3
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // Wrong answer
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── 准确率 ────────────────────────────────────────────────

    @Test
    fun `accuracy is zero before any answers`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 1_0 after all correct`() {
        val session = createSession()
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(1.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 0_0 after all wrong`() {
        val session = createSession()
        session.start()
        repeat(3) {
            val q = session.currentQuestion!!
            val wrong = q.answerChoices.first { it != q.correctAnswer }
            session.submit(wrong)
            session.next()
        }
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 0_5 after half correct`() {
        val session = createSession()
        session.start()
        // Correct
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // Wrong
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    // ── 答题历史 ──────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!
        val wrong = q2.answerChoices.first { it != q2.correctAnswer }
        session.submit(wrong)

        assertEquals(2, session.history.size)
        assertEquals(q1.correctAnswer, session.history[0].userAnswer)
        assertEquals(wrong, session.history[1].userAnswer)
    }

    @Test
    fun `last answer is available`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    // ── 边界安全 ──────────────────────────────────────────────

    @Test
    fun `next without answer still generates new question`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion!!
        session.next() // skip without answering
        assertNotNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
    }

    @Test
    fun `difficulty returns correct value`() {
        val session = createSession(SuspendedChordDifficulty.ADVANCED)
        assertEquals(SuspendedChordDifficulty.ADVANCED, session.difficulty())
    }

    // ── reset ────────────────────────────────────────────────

    @Test
    fun `reset clears all stats`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.answeredCount)

        session.reset()

        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
        assertFalse(session.isStarted)
        assertFalse(session.isAnswered)
        assertNull(session.lastAnswer)
    }
}
