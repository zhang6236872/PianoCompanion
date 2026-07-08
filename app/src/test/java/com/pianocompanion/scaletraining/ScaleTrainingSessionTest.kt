package com.pianocompanion.scaletraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音阶听辨训练会话状态机单元测试。
 */
class ScaleTrainingSessionTest {

    private fun createSession(difficulty: ScaleDifficulty = ScaleDifficulty.BEGINNER): ScaleTrainingSession {
        return ScaleTrainingSession(ScaleTrainingEngine.withSeed(42L), difficulty)
    }

    // ── 初始状态 ──────────────────────────────────────────

    @Test
    fun `initial state is not started`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `initial accuracy is zero`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `initial history is empty`() {
        val session = createSession()
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `initial isAnswered is false`() {
        val session = createSession()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `initial lastAnswer is null`() {
        val session = createSession()
        assertNull(session.lastAnswer)
    }

    // ── start() ───────────────────────────────────────────

    @Test
    fun `start generates first question`() {
        val session = createSession()
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start resets counters`() {
        val session = createSession()
        // Simulate some state
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer) // Correct answer
        assertEquals(1, session.answeredCount)

        // Start again should reset
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
        assertNull(session.lastAnswer)
    }

    // ── submit() ──────────────────────────────────────────

    @Test
    fun `submit correct answer`() {
        val session = createSession()
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
    fun `submit wrong answer`() {
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
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit returns null when not started`() {
        val session = createSession()
        val record = session.submit("大调音阶")
        assertNull(record)
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val record2 = session.submit(q.correctAnswer)
        assertNull(record2)
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertEquals(q.correctAnswer, session.lastAnswer!!.userAnswer)
    }

    // ── 连击追踪 ──────────────────────────────────────────

    @Test
    fun `correct answer increments streak`() {
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
    fun `wrong answer resets streak`() {
        val session = createSession()
        session.start()
        // Get a few correct
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)

        // Wrong answer
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak) // Best streak preserved
    }

    @Test
    fun `bestStreak does not decrease`() {
        val session = createSession()
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        val peak = session.bestStreak

        // Wrong answer
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(peak, session.bestStreak)
    }

    // ── next() ────────────────────────────────────────────

    @Test
    fun `next generates new question`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)

        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next returns null when not started`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `next clears isAnswered`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)

        session.next()
        assertFalse(session.isAnswered)
    }

    // ── history ───────────────────────────────────────────

    @Test
    fun `history accumulates answers`() {
        val session = createSession()
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
    fun `history preserves order`() {
        val session = createSession()
        session.start()
        val answers = mutableListOf<Boolean>()
        repeat(5) {
            val q = session.currentQuestion!!
            val isCorrect = it % 2 == 0
            val answer = if (isCorrect) q.correctAnswer else q.answerChoices.first { a -> a != q.correctAnswer }
            session.submit(answer)
            answers.add(isCorrect)
            session.next()
        }
        assertEquals(answers, session.history.map { it.isCorrect })
    }

    // ── accuracy ──────────────────────────────────────────

    @Test
    fun `accuracy calculates correctly after mixed answers`() {
        val session = createSession()
        session.start()

        // 3 correct, 2 wrong = 0.6
        session.submit(session.currentQuestion!!.correctAnswer); session.next() // 1 correct
        session.submit(session.currentQuestion!!.correctAnswer); session.next() // 2 correct
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer }); session.next() // 1 wrong
        session.submit(session.currentQuestion!!.correctAnswer); session.next() // 3 correct
        val q2 = session.currentQuestion!!
        session.submit(q2.answerChoices.first { it != q2.correctAnswer }) // 2 wrong

        assertEquals(5, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.6, session.accuracy, 0.001)
    }

    // ── reset() ───────────────────────────────────────────

    @Test
    fun `reset clears everything`() {
        val session = createSession()
        session.start()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }

        session.reset()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isAnswered)
        assertNull(session.lastAnswer)
    }

    // ── difficulty() ──────────────────────────────────────

    @Test
    fun `difficulty returns the configured difficulty`() {
        for (d in ScaleDifficulty.ALL) {
            val session = ScaleTrainingSession(ScaleTrainingEngine.withSeed(1L), d)
            assertEquals(d, session.difficulty())
        }
    }

    // ── 完整生命周期 ──────────────────────────────────────

    @Test
    fun `full lifecycle for all difficulties`() {
        for (difficulty in ScaleDifficulty.ALL) {
            val session = ScaleTrainingSession(ScaleTrainingEngine.withSeed(7L), difficulty)
            session.start()
            assertNotNull(session.currentQuestion)

            repeat(10) {
                val q = session.currentQuestion!!
                session.submit(q.correctAnswer)
                assertTrue(session.isAnswered)
                session.next()
                assertFalse(session.isAnswered)
            }

            assertEquals(10, session.answeredCount)
            assertEquals(10, session.correctCount)
            assertEquals(1.0, session.accuracy, 0.001)
        }
    }
}
