package com.pianocompanion.harmonicseries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泛音列辨识训练会话状态机单元测试。
 */
class HarmonicSeriesSessionTest {

    private fun createSession(difficulty: HarmonicDifficulty = HarmonicDifficulty.BEGINNER): HarmonicSeriesSession {
        return HarmonicSeriesSession(HarmonicSeriesEngine.withSeed(42), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────

    @Test
    fun `session starts with no question`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start resets all counters`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
        // 再次 start 应该重置
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    // ── 答题与连击 ──────────────────────────────────

    @Test
    fun `correct answer increments correct count and streak`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)

        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `wrong answer increments answered count but not correct`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
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
    fun `streak resets on wrong answer`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        // 答错 1 题
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak) // bestStreak 保持
    }

    @Test
    fun `best streak tracks maximum`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(5, session.bestStreak)
        // 答错
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(5, session.bestStreak)
    }

    // ── 双击防护 ──────────────────────────────────

    @Test
    fun `submitting twice on same question returns null`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit("第2泛音")
        assertNull(second)
        assertEquals(1, session.answeredCount) // 不重复计数
    }

    @Test
    fun `submit before start returns null`() {
        val session = createSession()
        val record = session.submit("第2泛音")
        assertNull(record)
    }

    // ── next 机制 ──────────────────────────────────

    @Test
    fun `next generates new question`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        val q1 = session.currentQuestion
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        // 新题目应清除 isAnswered 状态
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next before start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `next clears last answer`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        session.next()
        assertNull(session.lastAnswer)
        assertFalse(session.isAnswered)
    }

    // ── 防御性副本 ──────────────────────────────────

    @Test
    fun `history returns defensive copy`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history1 = session.history
        assertEquals(1, history1.size)
        // 修改返回的列表不影响内部状态
        (history1 as MutableList).clear()
        assertEquals(1, session.history.size)
    }

    @Test
    fun `history preserves order`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        val history = session.history
        assertEquals(5, history.size)
        // 历史按答题顺序排列，验证每条记录都有有效的题目和答案
        for (i in history.indices) {
            assertNotNull("Record $i should have a question", history[i].question.targetHarmonic)
            assertTrue("Record $i should be correct", history[i].isCorrect)
            assertEquals("Record $i correct answer should match", history[i].question.targetHarmonic.displayName, history[i].userAnswer)
        }
    }

    // ── 准确率 ──────────────────────────────────

    @Test
    fun `accuracy is zero before answering`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy reflects correct ratio`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错 1 题
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0.75, session.accuracy, 0.001) // 3/4
    }

    // ── reset ──────────────────────────────────

    @Test
    fun `reset clears all state`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
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
        assertEquals(0, session.history.size)
    }

    // ── 答题记录正确性 ──────────────────────────────────

    @Test
    fun `answer record stores correct information`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)!!
        assertEquals(q, record.question)
        assertEquals(q.correctAnswer, record.userAnswer)
        assertTrue(record.isCorrect)
        assertNull(record.correctAnswer) // 答对了，correctAnswer 为 null
    }

    @Test
    fun `wrong answer record stores correct answer`() {
        val session = createSession(HarmonicDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)!!
        assertFalse(record.isCorrect)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }
}
