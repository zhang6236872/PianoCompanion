package com.pianocompanion.rhythmreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmReadingSession] 单元测试。
 *
 * 验证：
 * - 会话生命周期（start → submit → next）
 * - 答题判定正确性
 * - 连击机制（答对递增、答错归零、最佳连击）
 * - 准确率计算
 * - 答题历史记录
 * - 重复提交 / 未开始答题的边界情况
 * - 重置
 */
class RhythmReadingSessionTest {

    private fun newSession(difficulty: RhythmReadingDifficulty = RhythmReadingDifficulty.BEGINNER): RhythmReadingSession {
        return RhythmReadingSession(RhythmReadingEngine(), difficulty)
    }

    @Test
    fun `session not started has no question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
    }

    @Test
    fun `start generates first question`() {
        val s = newSession()
        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
    }

    @Test
    fun `submit correct answer returns correct record`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `submit wrong answer returns incorrect record`() {
        val s = newSession()
        s.start()
        // 提交一个不存在的指纹 → 必定错误
        val record = s.submit("__WRONG__")
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
    }

    @Test
    fun `submit before start returns null`() {
        val s = newSession()
        val record = s.submit("anything")
        assertNull(record)
    }

    @Test
    fun `double submit returns null on second`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        val second = s.submit(q.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `streak increments on correct`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.currentStreak)
    }

    @Test
    fun `streak resets to zero on wrong`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        s.submit("__WRONG__")
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        val s = newSession()
        s.start()
        // 连续答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak)
        // 答错一题
        s.submit("__WRONG__")
        assertEquals(3, s.bestStreak) // best 不降
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `answered and correct counts tracked`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer) // correct
        s.next()
        s.submit("__WRONG__") // wrong
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
    }

    @Test
    fun `accuracy calculation`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit("__WRONG__")
        // 2/3
        assertEquals(2.0 / 3.0, s.accuracy, 1e-9)
    }

    @Test
    fun `accuracy is zero before any answer`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 1e-9)
    }

    @Test
    fun `history records all answers`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit("__WRONG__")
        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `next before start returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `next generates new question`() {
        val s = newSession()
        s.start()
        val first = s.currentQuestion!!
        s.submit(first.correctAnswer)
        s.next()
        assertNotNull(s.currentQuestion)
    }

    @Test
    fun `isAnswered flag lifecycle`() {
        val s = newSession()
        s.start()
        assertFalse(s.isAnswered)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit("__WRONG__")
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.history.size)
    }

    @Test
    fun `difficulty accessor returns configured difficulty`() {
        val s = newSession(RhythmReadingDifficulty.ADVANCED)
        assertEquals(RhythmReadingDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(s.lastAnswer)
        assertTrue(s.lastAnswer!!.isCorrect)
    }

    @Test
    fun `correctAnswer in record is null when correct`() {
        val s = newSession()
        s.start()
        val record = s.submit(s.currentQuestion!!.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `correctAnswer in record is set when wrong`() {
        val s = newSession()
        s.start()
        val record = s.submit("__WRONG__")!!
        assertNotNull(record.correctAnswer)
    }
}
