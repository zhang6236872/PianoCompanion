package com.pianocompanion.dynamicsdirectiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 力度变化方向辨识训练会话状态机单元测试。
 */
class DynamicsDirectionSessionTest {

    private fun newSession(difficulty: DynamicsDirectionDifficulty = DynamicsDirectionDifficulty.ADVANCED): DynamicsDirectionSession {
        val engine = DynamicsDirectionEngine.withSeed(42)
        val session = DynamicsDirectionSession(engine, difficulty)
        session.start()
        return session
    }

    @Test
    fun `start generates a current question`() {
        val session = newSession()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `fresh session has zero counts`() {
        val session = newSession()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `submit correct answer increments correct and streak`() {
        val session = newSession()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val record = session.submit(correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer does not increment correct but increments answered`() {
        val session = newSession()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(wrong, record.userAnswer)
        assertEquals(session.currentQuestion!!.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `streak resets on wrong answer`() {
        val session = newSession()
        // 连续答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.correctCount)
        assertEquals(3, session.currentStreak)
        // 答错一题
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak) // bestStreak 保留
    }

    @Test
    fun `best streak tracks maximum`() {
        val session = newSession()
        // 答对 2 题
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错
        session.submit(session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer })
        // 再答对 1 题
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `submit twice on same question returns null second time`() {
        val session = newSession()
        val correct = session.currentQuestion!!.correctAnswer
        session.submit(correct)
        val second = session.submit(correct)
        assertNull(second)
    }

    @Test
    fun `submit before start returns null`() {
        val engine = DynamicsDirectionEngine.withSeed(1)
        val session = DynamicsDirectionSession(engine, DynamicsDirectionDifficulty.BEGINNER)
        assertNull(session.submit("anything"))
    }

    @Test
    fun `next before start returns null`() {
        val engine = DynamicsDirectionEngine.withSeed(1)
        val session = DynamicsDirectionSession(engine, DynamicsDirectionDifficulty.BEGINNER)
        assertNull(session.next())
    }

    @Test
    fun `next generates a new question and clears answered state`() {
        val session = newSession()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
        assertNotNull(session.currentQuestion)
        assertNull(session.lastAnswer)
    }

    @Test
    fun `accuracy computes correctly`() {
        val session = newSession()
        // 答对 3，答错 1
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer })
        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.75, session.accuracy, 0.0001)
    }

    @Test
    fun `accuracy is zero when no answers`() {
        val session = newSession()
        assertEquals(0.0, session.accuracy, 0.0001)
    }

    @Test
    fun `history records all answers`() {
        val session = newSession()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.history.size)
        session.history.forEach { assertTrue(it.isCorrect) }
    }

    @Test
    fun `reset clears all state but keeps difficulty`() {
        val session = newSession()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertEquals(DynamicsDirectionDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `reset then start works`() {
        val session = newSession()
        session.reset()
        assertFalse(session.isStarted)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `history is a defensive copy`() {
        val session = newSession()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history = session.history
        // 修改返回的列表不应影响内部状态
        try {
            (history as MutableList).clear()
        } catch (_: Exception) {
            // toList() 已是不可变副本，转换可能失败也无妨
        }
        assertEquals(1, session.history.size)
    }

    @Test
    fun `last answer reflects most recent submission`() {
        val session = newSession()
        val correct = session.currentQuestion!!.correctAnswer
        session.submit(correct)
        assertEquals(correct, session.lastAnswer?.userAnswer)
        assertTrue(session.lastAnswer?.isCorrect ?: false)
    }

    @Test
    fun `session retains configured difficulty`() {
        DynamicsDirectionDifficulty.ALL.forEach { difficulty ->
            val engine = DynamicsDirectionEngine.withSeed(7)
            val session = DynamicsDirectionSession(engine, difficulty)
            session.start()
            assertEquals(difficulty, session.difficulty())
            assertEquals(difficulty, session.currentQuestion!!.difficulty)
        }
    }

    @Test
    fun `beginner session questions only have beginner directions`() {
        val engine = DynamicsDirectionEngine.withSeed(11)
        val session = DynamicsDirectionSession(engine, DynamicsDirectionDifficulty.BEGINNER)
        session.start()
        repeat(20) {
            assertTrue(
                session.currentQuestion!!.direction in DynamicsDirectionDifficulty.BEGINNER.directions
            )
            session.next()
        }
    }
}
