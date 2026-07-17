package com.pianocompanion.scaledegreetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调内音级辨识训练会话状态机单元测试。
 */
class ScaleDegreeSessionTest {

    private fun newSession(difficulty: ScaleDegreeDifficulty = ScaleDegreeDifficulty.BEGINNER): ScaleDegreeSession {
        return ScaleDegreeSession(ScaleDegreeEngine.withSeed(42), difficulty)
    }

    @Test
    fun `session starts inactive`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0.0, s.accuracy, 0.0001)
        assertTrue(s.history.isEmpty())
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
    fun `submit correct answer increments correct count and streak`() {
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
        assertEquals(1.0, s.accuracy, 0.0001)
    }

    @Test
    fun `submit wrong answer resets streak but keeps answered count`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrongAnswer)

        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0.0, s.accuracy, 0.0001)
    }

    @Test
    fun `submit returns null when no current question`() {
        val s = newSession()
        assertNull(s.submit("anything"))
    }

    @Test
    fun `submit returns null when already answered`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        val second = s.submit(q.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `streak accumulates on consecutive correct then resets on wrong`() {
        val s = newSession(ScaleDegreeDifficulty.ADVANCED)
        s.start()

        // 连续答对 3 题
        repeat(3) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        assertEquals(3, s.correctCount)
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)

        // 答错一题
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(4, s.answeredCount)
        assertEquals(3, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun `best streak tracks maximum across session`() {
        val s = newSession(ScaleDegreeDifficulty.ADVANCED)
        s.start()

        // 连击 2
        repeat(2) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(2, s.bestStreak)

        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(2, s.bestStreak)

        // 连击 1
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.bestStreak) // 仍是历史最高 2
    }

    @Test
    fun `next generates a new question and clears answered state`() {
        val s = newSession()
        s.start()
        val first = s.currentQuestion
        s.submit(first!!.correctAnswer)
        assertTrue(s.isAnswered)

        s.next()
        assertFalse(s.isAnswered)
        assertNotNull(s.currentQuestion)
    }

    @Test
    fun `next returns null when session not started`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)

        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
        assertFalse(s.isAnswered)
    }

    @Test
    fun `history records all answers in order`() {
        val s = newSession(ScaleDegreeDifficulty.ADVANCED)
        s.start()

        val records = mutableListOf<ScaleDegreeAnswerRecord>()
        repeat(5) {
            val q = s.currentQuestion!!
            val answer = if (it % 2 == 0) q.correctAnswer else q.answerChoices.first { a -> a != q.correctAnswer }
            val r = s.submit(answer)!!
            records.add(r)
            s.next()
        }

        assertEquals(5, s.history.size)
        assertEquals(records, s.history)
    }

    @Test
    fun `history is a defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)

        val snapshot = s.history
        // 返回的列表应该是不可变副本
        assertEquals(1, snapshot.size)
    }

    @Test
    fun `last answer is tracked`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)

        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertEquals(record, s.lastAnswer)

        s.next()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `difficulty returns the configured difficulty`() {
        val s = newSession(ScaleDegreeDifficulty.INTERMEDIATE)
        assertEquals(ScaleDegreeDifficulty.INTERMEDIATE, s.difficulty())
    }

    @Test
    fun `accuracy reflects correct ratio`() {
        val s = newSession(ScaleDegreeDifficulty.ADVANCED)
        s.start()

        // 3 对 2 错
        for (i in 0 until 5) {
            val q = s.currentQuestion!!
            if (i < 3) {
                s.submit(q.correctAnswer)
            } else {
                s.submit(q.answerChoices.first { it != q.correctAnswer })
            }
            s.next()
        }
        assertEquals(5, s.answeredCount)
        assertEquals(3, s.correctCount)
        assertEquals(0.6, s.accuracy, 0.0001)
    }

    @Test
    fun `answer record correct answer is null when correct`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `answer record correct answer is populated when wrong`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)!!
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `can start again after reset`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
    }
}
