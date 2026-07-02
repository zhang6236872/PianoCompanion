package com.pianocompanion.interval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IntervalSession] 单元测试。
 *
 * 验证会话状态机的完整生命周期：
 * - 开始会话 → 生成第一题
 * - 提交答案 → 正确/错误判定、统计更新
 * - 连击机制（答对递增、答错归零）
 * - 下一题 → 生成新题
 * - 重置 → 清空所有统计
 * - 边界情况（未开始就 submit/next、重复 submit）
 */
class IntervalSessionTest {

    private fun createSession(
        clef: IntervalClef = IntervalClef.TREBLE,
        difficulty: IntervalDifficulty = IntervalDifficulty.BEGINNER,
        seed: Long = 0L
    ): IntervalSession {
        val engine = IntervalEngine.withSeed(seed)
        return IntervalSession(engine, clef, difficulty)
    }

    private fun pickWrongAnswer(q: IntervalQuestion): String {
        return q.answerChoices.first { it != q.correctAnswer }
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        assertFalse(session.isStarted)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start resets all stats`() {
        val session = createSession()
        session.start()
        session.submit("错误答案") // wrong answer to populate stats
        session.start() // restart
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `submit correct answer increments correct count`() {
        val session = createSession()
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val record = session.submit(correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
    }

    @Test
    fun `submit wrong answer does not increment correct count`() {
        val session = createSession()
        session.start()
        val wrongAnswer = pickWrongAnswer(session.currentQuestion!!)
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `streak increments on consecutive correct answers`() {
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
        session.submit(session.currentQuestion!!.correctAnswer) // correct
        assertEquals(1, session.currentStreak)
        session.next()
        val wrong = pickWrongAnswer(session.currentQuestion!!)
        session.submit(wrong) // wrong
        assertEquals(0, session.currentStreak)
        assertEquals(1, session.bestStreak) // best streak preserved
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
        // 1 wrong
        session.submit(pickWrongAnswer(session.currentQuestion!!))
        assertEquals(3, session.bestStreak)
        // 1 correct
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak) // still 3
    }

    @Test
    fun `submit returns null when no current question`() {
        val session = createSession()
        assertNull(session.submit("三度"))
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        // Already answered, second submit should return null
        assertNull(session.submit("三度"))
    }

    @Test
    fun `isAnswered flag works correctly`() {
        val session = createSession()
        session.start()
        assertFalse(session.isAnswered)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next generates new question`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
    }

    @Test
    fun `next returns null when not started`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `accuracy calculation`() {
        val session = createSession(seed = 1L)
        session.start()
        // Answer 4 questions: 3 correct, 1 wrong
        for (i in 0 until 4) {
            if (i == 2) {
                session.submit(pickWrongAnswer(session.currentQuestion!!))
            } else {
                session.submit(session.currentQuestion!!.correctAnswer)
            }
            if (i < 3) session.next()
        }
        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero when no questions answered`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `history records all answers`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(pickWrongAnswer(session.currentQuestion!!))
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

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
        assertTrue(session.history.isEmpty())
        assertFalse(session.isStarted)
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `clef and difficulty accessors`() {
        val session = IntervalSession(
            IntervalEngine(),
            IntervalClef.BASS,
            IntervalDifficulty.ADVANCED
        )
        assertEquals(IntervalClef.BASS, session.clef())
        assertEquals(IntervalDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `answer record correctAnswer is null when correct`() {
        val session = createSession()
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `answer record correctAnswer is set when wrong`() {
        val session = createSession()
        session.start()
        val wrong = pickWrongAnswer(session.currentQuestion!!)
        val record = session.submit(wrong)!!
        assertNotNull(record.correctAnswer)
        assertEquals(session.currentQuestion!!.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `full session lifecycle`() {
        val session = createSession(seed = 100L)
        session.start()
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)

        var correct = 0
        for (i in 0 until 10) {
            if (i % 3 == 0) {
                val wrong = pickWrongAnswer(session.currentQuestion!!)
                session.submit(wrong)
            } else {
                session.submit(session.currentQuestion!!.correctAnswer)
                correct++
            }
            if (i < 9) session.next()
        }

        assertEquals(10, session.answeredCount)
        assertEquals(correct, session.correctCount)
        assertTrue(session.bestStreak >= 1)
        assertEquals(10, session.history.size)
    }
}
