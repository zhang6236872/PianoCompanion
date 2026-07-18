package com.pianocompanion.accentrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AccentSession] 单元测试。
 *
 * 验证会话生命周期（start/submit/next/reset）、得分统计、连击、准确率、历史记录。
 */
class AccentRecognitionSessionTest {

    private fun newSession(difficulty: AccentDifficulty = AccentDifficulty.BEGINNER): AccentSession {
        // 固定种子引擎，便于断言
        return AccentSession(AccentEngine.withSeed(42L), difficulty)
    }

    @Test
    fun `session starts with first question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)

        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
        assertFalse(s.isAnswered)
    }

    @Test
    fun `start resets counters`() {
        val s = newSession()
        s.start()
        // 模拟一些答题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.answeredCount)
        // 再次 start 应重置
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `submit correct answer increments correct and streak`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!

        val record = s.submit(q.correctAnswer)

        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(q.correctAnswer, record.userAnswer)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.bestStreak)
        assertTrue(s.isAnswered)
    }

    @Test
    fun `submit wrong answer breaks streak`() {
        val s = newSession()
        s.start()
        // 先答对一题
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        // 故意答错（选一个错误选项）
        val q = s.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrongAnswer)

        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(0, s.currentStreak)
        // bestStreak 保留历史最大值
        assertEquals(1, s.bestStreak)
    }

    @Test
    fun `best streak tracks maximum across session`() {
        val s = newSession()
        s.start()
        // 连续答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        // 答错一题
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)
        // 再连对 2 题
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun `submit returns null when no question`() {
        val s = newSession()
        assertNull(s.submit("第 1 拍"))
    }

    @Test
    fun `submit returns null when already answered`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        // 再次提交应返回 null
        assertNull(s.submit(q.correctAnswer))
        assertEquals(1, s.answeredCount)
    }

    @Test
    fun `next generates new question and clears answered state`() {
        val s = newSession()
        s.start()
        val q1 = s.currentQuestion
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)

        val q2 = s.next()
        assertNotNull(q2)
        assertFalse(s.isAnswered)
        assertNull(s.lastAnswer)
        // 新题目拍数应在该难度候选集合内
        assertTrue(q2!!.beatsPerMeasure in AccentDifficulty.BEGINNER.beatsPerMeasureOptions)
    }

    @Test
    fun `next returns null when not started`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `reset clears everything`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()

        s.reset()

        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
        assertFalse(s.isAnswered)
        assertFalse(s.isStarted)
    }

    @Test
    fun `accuracy computed correctly`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.0001)

        // 答对
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1.0, s.accuracy, 0.0001)
        s.next()
        // 答错
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(0.5, s.accuracy, 0.0001)
    }

    @Test
    fun `history records all answers in order`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })

        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `history is a defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val snapshot = s.history
        // 修改快照不应影响内部状态
        @Suppress("UNCHECKED_CAST")
        val mutable = snapshot as MutableList<AccentAnswerRecord>
        mutable.clear()
        assertEquals(1, s.history.size)
    }

    @Test
    fun `lastAnswer updated on submit and cleared on next`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(s.lastAnswer)
        s.next()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `difficulty returns configured difficulty`() {
        val s = AccentSession(AccentEngine.withSeed(1L), AccentDifficulty.ADVANCED)
        assertEquals(AccentDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `correctAnswer helper on record is null when correct`() {
        val s = newSession()
        s.start()
        val record = s.submit(s.currentQuestion!!.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `correctAnswer helper on record shows answer when wrong`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)!!
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `multiple rounds maintain consistent state`() {
        val s = newSession()
        s.start()
        // 10 轮：交替对错
        for (i in 0 until 10) {
            val q = s.currentQuestion!!
            if (i % 2 == 0) {
                s.submit(q.correctAnswer)
            } else {
                s.submit(q.answerChoices.first { it != q.correctAnswer })
            }
            if (i < 9) s.next()
        }
        assertEquals(10, s.answeredCount)
        assertEquals(5, s.correctCount)
        assertEquals(10, s.history.size)
    }
}
