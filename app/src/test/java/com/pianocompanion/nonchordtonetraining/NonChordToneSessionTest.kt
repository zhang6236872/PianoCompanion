package com.pianocompanion.nonchordtonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦外音辨识训练会话状态机单元测试。
 *
 * 覆盖：生命周期、状态转换、连击/准确率计算、答题历史。
 */
class NonChordToneSessionTest {

    private fun newSession(difficulty: NonChordToneDifficulty = NonChordToneDifficulty.ADVANCED): NonChordToneSession {
        val engine = NonChordToneEngine.withSeed(123)
        return NonChordToneSession(engine, difficulty)
    }

    @Test
    fun `session is not started before start`() {
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
    fun `submit before start returns null`() {
        val s = newSession()
        assertNull(s.submit(NonChordToneType.PASSING_TONE.fullLabel))
    }

    @Test
    fun `submit correct answer records correct`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
        assertTrue(s.isAnswered)
    }

    @Test
    fun `submit wrong answer records wrong and resets streak`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        val q = s.currentQuestion!!
        // 找一个错误答案
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `double submit returns null`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        assertNull(s.submit(q.correctAnswer))
        assertEquals(1, s.answeredCount)
    }

    @Test
    fun `streak accumulates on consecutive correct`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
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
    }

    @Test
    fun `streak resets on wrong then can rebuild`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        // 先答对 2 题
        repeat(2) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        // 第 3 题答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(0, s.currentStreak)
        assertEquals(2, s.bestStreak)
        // 再答对
        s.next()
        val q2 = s.currentQuestion!!
        s.submit(q2.correctAnswer)
        assertEquals(1, s.currentStreak)
        assertEquals(2, s.bestStreak)
    }

    @Test
    fun `best streak is the maximum`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        // 连对 2
        repeat(2) {
            s.submit(s.currentQuestion!!.correctAnswer); s.next()
        }
        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        // 连对 1
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        assertEquals(2, s.bestStreak)
    }

    @Test
    fun `next before start returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `next after answer clears answered flag and generates new question`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        val newQ = s.next()
        assertNotNull(newQ)
        assertFalse(s.isAnswered)
        assertNull(s.lastAnswer)
    }

    @Test
    fun `history records all answers`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        repeat(3) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        assertEquals(3, s.history.size)
        assertTrue(s.history.all { it.isCorrect })
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `accuracy is correct`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        // 答对
        s.submit(s.currentQuestion!!.correctAnswer); s.next()
        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(0.5, s.accuracy, 0.0001)
    }

    @Test
    fun `accuracy is 0 before any answer`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.0001)
    }

    @Test
    fun `difficulty returns the configured difficulty`() {
        val s = newSession(NonChordToneDifficulty.INTERMEDIATE)
        assertEquals(NonChordToneDifficulty.INTERMEDIATE, s.difficulty())
    }

    @Test
    fun `last answer record is stored`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        assertNotNull(s.lastAnswer)
        assertEquals(q.correctAnswer, s.lastAnswer!!.userAnswer)
        assertEquals(q.correctAnswer, s.lastAnswer!!.question.correctAnswer)
    }

    @Test
    fun `wrong answer exposes correct answer in record`() {
        val s = newSession(NonChordToneDifficulty.ADVANCED)
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)!!
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `correct answer record exposes null correct answer`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `full session with all difficulties works`() {
        NonChordToneDifficulty.ALL.forEach { difficulty ->
            val s = NonChordToneSession(NonChordToneEngine.withSeed(999), difficulty)
            s.start()
            repeat(5) {
                val q = s.currentQuestion!!
                s.submit(q.correctAnswer)
                s.next()
            }
            assertEquals(5, s.answeredCount)
            assertEquals(5, s.correctCount)
            assertEquals(5, s.currentStreak)
        }
    }
}
