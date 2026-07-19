package com.pianocompanion.subdivisionrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SubdivisionSession] 单元测试。
 *
 * 验证会话状态机生命周期、得分/连击/准确率统计、防御性历史副本等。
 */
class SubdivisionRecognitionSessionTest {

    private fun newSession(difficulty: SubdivisionDifficulty = SubdivisionDifficulty.INTERMEDIATE): SubdivisionSession {
        return SubdivisionSession(SubdivisionEngine.withSeed(42L), difficulty)
    }

    @Test
    fun `start generates first question`() {
        val s = newSession()
        assertNull(s.currentQuestion)
        assertFalse(s.isStarted)
        s.start()
        assertNotNull(s.currentQuestion)
        assertTrue(s.isStarted)
    }

    @Test
    fun `start resets all stats`() {
        val s = newSession()
        s.start()
        // 模拟一些答题
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit("wrong")
        // 重新开始
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
        assertFalse(s.isAnswered)
    }

    @Test
    fun `submit correct answer increments correct and streak`() {
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
    }

    @Test
    fun `submit wrong answer does not increment correct and resets streak`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        // 先答对一题建立连击
        s.submit(q.correctAnswer)
        s.next()
        val q2 = s.currentQuestion!!
        val wrong = q2.answerChoices.first { it != q2.correctAnswer }
        val record = s.submit(wrong)

        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(1, s.bestStreak) // 之前的连击保留为最佳
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
        assertEquals(3, s.correctCount)
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        // 答错一题
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak) // 最佳保留
    }

    @Test
    fun `submit returns null when no current question`() {
        val s = newSession()
        assertNull(s.submit("二分细分"))
    }

    @Test
    fun `submit returns null when already answered`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        // 重复提交
        assertNull(s.submit(q.correctAnswer))
    }

    @Test
    fun `next generates a new question`() {
        val s = newSession(SubdivisionDifficulty.ADVANCED)
        s.start()
        val first = s.currentQuestion!!
        s.submit(first.correctAnswer)
        s.next()
        val second = s.currentQuestion!!
        assertNotNull(second)
        // 状态重置
        assertFalse(s.isAnswered)
    }

    @Test
    fun `next returns null when session not started`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `accuracy is zero before answering`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.0001)
    }

    @Test
    fun `accuracy computes correctly`() {
        val s = newSession()
        s.start()
        // 答对
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        // 答错
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(0.5, s.accuracy, 0.0001)
    }

    @Test
    fun `history records all answers in order`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)

        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `history returns a defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val snapshot = s.history
        // 修改返回的副本不应影响内部状态
        try {
            (snapshot as MutableList).clear()
        } catch (_: Exception) {
            // toMutableList 返回可变副本，clear 不会抛异常
        }
        assertEquals(1, s.history.size)
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
        assertFalse(s.isStarted)
    }

    @Test
    fun `difficulty returns configured difficulty`() {
        val s = newSession(SubdivisionDifficulty.ADVANCED)
        assertEquals(SubdivisionDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `lastAnswer is updated after submit`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertEquals(record, s.lastAnswer)
    }

    @Test
    fun `lastAnswer cleared on next`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `answer record correctAnswer is null when correct`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `answer record correctAnswer populated when wrong`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)!!
        assertEquals(q.correctAnswer, record.correctAnswer)
    }
}
