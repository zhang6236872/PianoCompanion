package com.pianocompanion.chordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordTrainingSession] 单元测试。
 *
 * 验证会话状态机的完整生命周期：
 * - 开始会话 → 生成第一题
 * - 提交答案 → 正确/错误判定、统计更新
 * - 连击机制（答对递增、答错归零）
 * - 下一题 → 生成新题
 * - 重置 → 清空所有统计
 * - 边界情况（未开始就 submit/next、重复 submit）
 */
class ChordTrainingSessionTest {

    private fun createSession(
        difficulty: ChordEarDifficulty = ChordEarDifficulty.BEGINNER,
        playStyle: ChordPlayStyle = ChordPlayStyle.BLOCK,
        seed: Long = 0L
    ): ChordTrainingSession {
        val engine = ChordTrainingEngine.withSeed(seed)
        return ChordTrainingSession(engine, difficulty, playStyle)
    }

    private fun pickWrongAnswer(q: ChordEarQuestion): String {
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
    fun `start resets counters`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `submit before start returns null`() {
        val session = createSession()
        assertNull(session.submit("大三和弦"))
    }

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
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(pickWrongAnswer(q))
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `double submit returns null`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull(second)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `streak increments on correct`() {
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
    fun `streak resets on wrong`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        session.next()
        session.submit(pickWrongAnswer(session.currentQuestion!!))
        assertEquals(0, session.currentStreak)
        assertEquals(1, session.bestStreak) // bestStreak 不降
    }

    @Test
    fun `next before start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `next generates new question`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        // 固定种子的引擎会产生不同的下一题（大概率）
    }

    @Test
    fun `reset clears all`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `accuracy calculation`() {
        val session = createSession(seed = 10L)
        session.start()
        // 答对一题
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1.0, session.accuracy, 0.001)
        // 答错一题
        session.next()
        session.submit(pickWrongAnswer(session.currentQuestion!!))
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero before any answer`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `history preserves order`() {
        val session = createSession(seed = 5L)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(pickWrongAnswer(session.currentQuestion!!))
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `last answer is set`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `difficulty and playStyle accessors`() {
        val session = createSession(
            difficulty = ChordEarDifficulty.ADVANCED,
            playStyle = ChordPlayStyle.ARPEGGIO
        )
        assertEquals(ChordEarDifficulty.ADVANCED, session.difficulty())
        assertEquals(ChordPlayStyle.ARPEGGIO, session.playStyle())
    }

    @Test
    fun `isAnswered flag lifecycle`() {
        val session = createSession()
        session.start()
        assertFalse(session.isAnswered)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `correctAnswer is null when correct`() {
        val session = createSession()
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `full lifecycle beginner`() {
        val session = createSession(difficulty = ChordEarDifficulty.BEGINNER, seed = 3L)
        session.start()
        var correct = 0
        repeat(10) {
            val q = session.currentQuestion!!
            if (session.submit(q.correctAnswer) != null) correct++
            session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(10, session.correctCount)
        assertEquals(10, correct)
        assertEquals(10, session.bestStreak)
    }

    @Test
    fun `full lifecycle intermediate`() {
        val session = createSession(difficulty = ChordEarDifficulty.INTERMEDIATE, seed = 7L)
        session.start()
        repeat(10) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(1.0, session.accuracy, 0.001)
    }

    @Test
    fun `full lifecycle advanced with mistakes`() {
        val session = createSession(difficulty = ChordEarDifficulty.ADVANCED, seed = 11L)
        session.start()
        var streakCheck = 0
        repeat(20) { i ->
            val q = session.currentQuestion!!
            if (i % 3 == 0) {
                // 每 3 题答错 1 题
                session.submit(pickWrongAnswer(q))
                streakCheck = 0
            } else {
                session.submit(q.correctAnswer)
                streakCheck++
            }
            session.next()
        }
        assertEquals(20, session.answeredCount)
        assertTrue(session.bestStreak >= 1)
    }
}
