package com.pianocompanion.chordinversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练会话状态机单元测试。
 */
class ChordInversionSessionTest {

    private fun createSession(difficulty: ChordInversionDifficulty = ChordInversionDifficulty.INTERMEDIATE): ChordInversionSession {
        return ChordInversionSession(ChordInversionEngine.withSeed(42), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────

    @Test
    fun `session starts with first question`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start resets all counters`() {
        val session = createSession()
        session.start()
        // answer a few questions
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.answeredCount)
        // restart
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    // ── 答题 ──────────────────────────────────

    @Test
    fun `submit correct answer increments correct count and streak`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `submit wrong answer resets streak`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        // Find a wrong answer
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        val session = createSession()
        session.start()
        // 3 correct in a row
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // Wrong answer
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.bestStreak) // best stays at 3
        assertEquals(0, session.currentStreak)
        // 1 correct
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak) // still 3
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `accuracy calculation is correct`() {
        val session = createSession()
        session.start()
        // 2 correct
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 1 wrong
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        session.next()
        // 1 more correct
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    // ── 双击防护 ──────────────────────────────────

    @Test
    fun `double submit returns null`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        session.submit(question.correctAnswer)
        val second = session.submit(question.correctAnswer)
        assertNull(second)
        assertEquals(1, session.answeredCount) // only counted once
    }

    @Test
    fun `submit before start returns null`() {
        val session = createSession()
        val result = session.submit("anything")
        assertNull(result)
    }

    // ── 防御性副本 ──────────────────────────────────

    @Test
    fun `history is a defensive copy`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history1 = session.history
        assertEquals(1, history1.size)
        // Mutate the returned list (cast to MutableList to simulate mutation)
        @Suppress("UNCHECKED_CAST")
        (history1 as MutableList<Any>).clear()
        // Original should be unaffected
        assertEquals(1, session.history.size)
    }

    // ── 下一题 ──────────────────────────────────

    @Test
    fun `next generates a new question`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion
        session.submit(q1!!.correctAnswer)
        session.next()
        assertNotNull(session.currentQuestion)
        // After next, isAnswered should be false
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next before start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── 重置 ──────────────────────────────────

    @Test
    fun `reset clears everything`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
        assertFalse(session.isStarted)
    }

    // ── 答题历史保序 ──────────────────────────────────

    @Test
    fun `history preserves order`() {
        val session = createSession()
        session.start()
        val answers = mutableListOf<Boolean>()
        repeat(5) { i ->
            val q = session.currentQuestion!!
            // Alternate correct/wrong: even = correct, odd = wrong
            if (i % 2 == 0) {
                session.submit(q.correctAnswer)
                answers.add(true)
            } else {
                val wrong = q.answerChoices.first { it != q.correctAnswer }
                session.submit(wrong)
                answers.add(false)
            }
            session.next()
        }
        val history = session.history
        assertEquals(5, history.size)
        history.forEachIndexed { index, record ->
            assertEquals(answers[index], record.isCorrect)
        }
    }

    // ── lastAnswer ──────────────────────────────────

    @Test
    fun `lastAnswer is set after submit and cleared after next`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertNull(session.lastAnswer)
        assertFalse(session.isAnswered)
    }

    // ── 难度 ──────────────────────────────────

    @Test
    fun `difficulty returns correct value`() {
        val session = createSession(ChordInversionDifficulty.ADVANCED)
        assertEquals(ChordInversionDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `answer record contains correct data`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)!!
        assertEquals(question, record.question)
        assertEquals(wrongAnswer, record.userAnswer)
        assertFalse(record.isCorrect)
        assertEquals(question.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `answer record for correct answer has null correctAnswer`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)!!
        assertTrue(record.isCorrect)
        assertNull(record.correctAnswer)
    }
}
