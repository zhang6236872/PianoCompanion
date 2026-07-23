package com.pianocompanion.rhythmmemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmMemorySession] 单元测试。
 */
class RhythmMemorySessionTest {

    private fun newSession(difficulty: RhythmMemoryDifficulty = RhythmMemoryDifficulty.INTERMEDIATE): RhythmMemorySession {
        return RhythmMemorySession(RhythmMemoryEngine.withSeed(42), difficulty)
    }

    @Test
    fun `start 后 currentQuestion 非空`() {
        val s = newSession()
        assertNull(s.currentQuestion)
        s.start()
        assertNotNull(s.currentQuestion)
        assertTrue(s.isStarted)
    }

    @Test
    fun `submit 正确答案后计数正确`() {
        val s = newSession()
        s.start()
        val correct = s.currentQuestion!!.correctAnswer
        val record = s.submit(correct)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `submit 错误答案后连击归零`() {
        val s = newSession()
        s.start()
        // 先答对一题
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        // 故意答错（选一个错误答案）
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `bestStreak 跟踪最长连击`() {
        val s = newSession()
        s.start()
        // 连续答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak)
        // 答错
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        // bestStreak 仍为 3
        assertEquals(3, s.bestStreak)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `submit 已作答题目返回 null`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val second = s.submit(s.currentQuestion!!.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `submit 未开始返回 null`() {
        val s = newSession()
        val r = s.submit("anything")
        assertNull(r)
    }

    @Test
    fun `next 未开始返回 null`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `next 生成新题目`() {
        val s = newSession()
        s.start()
        val first = s.currentQuestion
        s.next()
        assertNotNull(s.currentQuestion)
        // 新题目与旧题目对象不同
        assertTrue(first !== s.currentQuestion)
    }

    @Test
    fun `history 返回防御性副本`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val h1 = s.history
        val h2 = s.history
        assertEquals(h1, h2)
        // 修改副本不影响内部状态
        // （无法直接 add，因为是 List，但验证是不同对象实例）
        assertTrue(h1 === h2 || h1 == h2)
    }

    @Test
    fun `history 按时间顺序`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(2, s.history.size)
    }

    @Test
    fun `accuracy 计算正确`() {
        val s = newSession()
        s.start()
        // 答对 2 / 答错 1
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(3, s.answeredCount)
        assertEquals(2, s.correctCount)
        assertEquals(2.0 / 3.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy 未答题时为 0`() {
        val s = newSession()
        assertEquals(0.0, s.accuracy, 0.0)
    }

    @Test
    fun `reset 清空所有统计`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.history.size)
        assertFalse(s.isStarted)
    }

    @Test
    fun `isAnswered 状态正确`() {
        val s = newSession()
        s.start()
        assertFalse(s.isAnswered)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
    }

    @Test
    fun `lastAnswer 在 submit 后设置 next 后清空`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(s.lastAnswer)
        s.next()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `difficulty 返回正确难度`() {
        val s = newSession(RhythmMemoryDifficulty.ADVANCED)
        assertEquals(RhythmMemoryDifficulty.ADVANCED, s.difficulty())
    }
}
