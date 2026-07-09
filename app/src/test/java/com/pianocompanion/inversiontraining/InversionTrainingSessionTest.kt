package com.pianocompanion.inversiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 和弦转位听辨训练会话状态机单元测试。
 */
class InversionTrainingSessionTest {

    private lateinit var engine: InversionTrainingEngine
    private lateinit var session: InversionTrainingSession

    @Before
    fun setUp() {
        engine = InversionTrainingEngine.withSeed(42L)
        session = InversionTrainingSession(engine, InversionDifficulty.INTERMEDIATE)
    }

    // ── 初始状态 ────────────────────────────────────────────

    @Test
    fun `initial state is not started`() {
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    // ── start() ────────────────────────────────────────────

    @Test
    fun `start generates first question`() {
        session.start()
        assertTrue(session.isStarted)
        assertTrue(session.currentQuestion != null)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start resets counters`() {
        // 先答几题
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!
        session.submit(q2.correctAnswer)
        assertEquals(2, session.answeredCount)

        // 重新开始
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
    }

    // ── submit() ───────────────────────────────────────────

    @Test
    fun `correct answer increments correct count and streak`() {
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)

        assertTrue(record != null)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `wrong answer resets streak`() {
        session.start()
        // 先答对一题建立连击
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        assertEquals(1, session.currentStreak)

        session.next()
        // 故意答错
        val q2 = session.currentQuestion!!
        val wrongAnswer = q2.answerChoices.first { it != q2.correctAnswer }
        session.submit(wrongAnswer)

        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(1, session.bestStreak) // bestStreak 不降级
    }

    @Test
    fun `submit returns null when not started`() {
        assertNull(session.submit("原位"))
    }

    @Test
    fun `submit returns null when already answered`() {
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertNull(session.submit(q.correctAnswer))
    }

    @Test
    fun `last answer is recorded`() {
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertEquals(q.correctAnswer, session.lastAnswer?.userAnswer)
    }

    // ── 连击追踪 ────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct`() {
        session.start()
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            if (it < 2) session.next()
        }
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `streak does not decrement below zero`() {
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)

        session.next()
        val q2 = session.currentQuestion!!
        val wrongAnswer2 = q2.answerChoices.first { it != q2.correctAnswer }
        session.submit(wrongAnswer2)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `best streak is preserved after a wrong answer`() {
        session.start()
        // 建立连击 3
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            if (it < 2) session.next()
        }
        assertEquals(3, session.bestStreak)

        // 答错
        session.next()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(3, session.bestStreak)
    }

    // ── next() ─────────────────────────────────────────────

    @Test
    fun `next generates new question and resets answered flag`() {
        session.start()
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        assertTrue(session.isAnswered)

        val q2 = session.next()
        assertTrue(q2 != null)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next returns null when not started`() {
        assertNull(session.next())
    }

    // ── accuracy ───────────────────────────────────────────

    @Test
    fun `accuracy is zero when not answered`() {
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is correct ratio`() {
        session.start()
        // 答对 2 错 1
        repeat(3) { i ->
            val q = session.currentQuestion!!
            if (i == 1) {
                val wrong = q.answerChoices.first { it != q.correctAnswer }
                session.submit(wrong)
            } else {
                session.submit(q.correctAnswer)
            }
            if (i < 2) session.next()
        }
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    // ── history ────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        session.start()
        val answers = mutableListOf<String>()
        repeat(5) { i ->
            val q = session.currentQuestion!!
            val answer = if (i % 2 == 0) q.correctAnswer else q.answerChoices.first { it != q.correctAnswer }
            session.submit(answer)
            answers.add(answer)
            if (i < 4) session.next()
        }

        assertEquals(5, session.history.size)
        session.history.forEachIndexed { i, record ->
            assertEquals(answers[i], record.userAnswer)
        }
    }

    // ── reset() ────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        session.start()
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            if (it < 2) session.next()
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
    }

    // ── 难度 ────────────────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        val s = InversionTrainingSession(InversionTrainingEngine(), InversionDifficulty.ADVANCED)
        assertEquals(InversionDifficulty.ADVANCED, s.difficulty())
    }

    // ── 全难度完整生命周期 ─────────────────────────────────

    @Test
    fun `full lifecycle for all difficulties`() {
        InversionDifficulty.ALL.forEach { d ->
            val eng = InversionTrainingEngine.withSeed(5L)
            val sess = InversionTrainingSession(eng, d)
            sess.start()
            repeat(5) {
                val q = sess.currentQuestion!!
                assertTrue(q.difficulty == d)
                sess.submit(q.correctAnswer)
                assertTrue(sess.isAnswered)
                if (it < 4) {
                    sess.next()
                    assertFalse(sess.isAnswered)
                }
            }
            assertEquals(5, sess.answeredCount)
            assertEquals(5, sess.correctCount)
            assertEquals(1.0, sess.accuracy, 0.001)
        }
    }

    @Test
    fun `correct answer record has null correctAnswer field`() {
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)
        assertTrue(record!!.isCorrect)
        assertNull(record.correctAnswer)
    }

    @Test
    fun `wrong answer record has non-null correctAnswer field`() {
        session.start()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrong)
        assertFalse(record!!.isCorrect)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }
}
