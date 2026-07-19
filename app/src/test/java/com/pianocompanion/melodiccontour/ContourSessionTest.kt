package com.pianocompanion.melodiccontour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContourSession] 单元测试。
 *
 * 验证会话状态机生命周期、得分计算、连击追踪、准确率、防御性历史副本等。
 */
class ContourSessionTest {

    private fun newSession(difficulty: ContourDifficulty = ContourDifficulty.BEGINNER): ContourSession {
        return ContourSession(ContourEngine.withSeed(42L), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `start generates first question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
    }

    @Test
    fun `next returns null before start`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `submit returns null before start`() {
        val s = newSession()
        assertNull(s.submit("上行"))
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
    fun `next generates new question after answering`() {
        val s = newSession()
        s.start()
        val q1 = s.currentQuestion!!
        s.submit(q1.correctAnswer)
        s.next()
        assertNotNull(s.currentQuestion)
        // 新题目可能相同或不同（随机），但 isAnswered 应为 false
        assertFalse(s.isAnswered)
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.history.size)
    }

    // ── 得分 ──────────────────────────────────────────────

    @Test
    fun `correct answer increments correct count and streak`() {
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
    }

    @Test
    fun `wrong answer does not increment correct count`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `streak resets on wrong answer`() {
        val s = newSession()
        s.start()
        // 连续答对 3 题
        repeat(3) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        // 答错一题
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak) // bestStreak 不因答错而降低
    }

    @Test
    fun `best streak tracks maximum`() {
        val s = newSession()
        s.start()
        repeat(5) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        assertEquals(5, s.bestStreak)
        // 答错
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        // 再答对 2 题
        repeat(2) {
            s.next()
            val q2 = s.currentQuestion!!
            s.submit(q2.correctAnswer)
        }
        assertEquals(5, s.bestStreak) // 仍保持最高记录
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `accuracy is zero before any answer`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.0001)
    }

    @Test
    fun `accuracy computes correctly`() {
        val s = newSession()
        s.start()
        // 答对 1
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        // 答对 2
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(3, s.answeredCount)
        assertEquals(2, s.correctCount)
        assertEquals(2.0 / 3.0, s.accuracy, 0.0001)
    }

    // ── 历史 ──────────────────────────────────────────────

    @Test
    fun `history records answers in order`() {
        val s = newSession()
        s.start()
        repeat(3) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            if (it < 2) s.next()
        }
        assertEquals(3, s.history.size)
        s.history.forEach { assertTrue(it.isCorrect) }
    }

    @Test
    fun `history returns defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)

        val h1 = s.history
        val h2 = s.history
        // 返回的副本应不同实例
        assertNotSameList(h1, h2)
        // 修改副本不影响内部状态
        try {
            (h2 as MutableList).clear()
        } catch (_: Exception) {
            // toMutableList 返回可变副本，clear 应成功
        }
        // 内部历史不受影响
        assertEquals(1, s.history.size)
    }

    private fun <T> assertNotSameList(a: List<T>, b: List<T>) {
        assertTrue(a !== b)
    }

    // ── lastAnswer ────────────────────────────────────────

    @Test
    fun `lastAnswer is set after submit and cleared after next`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(s.lastAnswer)
        s.next()
        assertNull(s.lastAnswer)
    }

    // ── 难度 ──────────────────────────────────────────────

    @Test
    fun `difficulty returns correct value`() {
        val s = ContourSession(ContourEngine.withSeed(1L), ContourDifficulty.ADVANCED)
        assertEquals(ContourDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `session works across all difficulties`() {
        ContourDifficulty.ALL.forEach { d ->
            val s = ContourSession(ContourEngine.withSeed(1L), d)
            s.start()
            assertNotNull(s.currentQuestion)
            assertEquals(d.noteCount, s.currentQuestion!!.noteCount)
            s.submit(s.currentQuestion!!.correctAnswer)
            assertEquals(1, s.correctCount)
        }
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
    fun `start resets previous session stats`() {
        val s = newSession()
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.answeredCount)
        // 再次 start 应重置
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.history.size)
    }
}
