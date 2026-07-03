package com.pianocompanion.chordreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordReadingSession] 单元测试。
 *
 * 验证会话状态机的完整生命周期：
 * - start → 生成第一题
 * - submit → 正确/错误判定、计数更新、连击逻辑
 * - next → 生成下一题、重置作答状态
 * - reset → 清空所有统计
 */
class ChordReadingSessionTest {

    private fun newSession(
        clef: ChordReadingClef = ChordReadingClef.TREBLE,
        difficulty: ChordReadingDifficulty = ChordReadingDifficulty.INTERMEDIATE
    ): ChordReadingSession = ChordReadingSession(ChordReadingEngine(), clef, difficulty)

    @Test
    fun `start generates first question`() {
        val session = newSession()
        assertFalse("会话未开始时 isStarted 应为 false", session.isStarted)
        session.start()
        assertNotNull("开始后应有当前题目", session.currentQuestion)
        assertTrue("开始后 isStarted 应为 true", session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `submit correct answer increments counts`() {
        val session = newSession()
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        val result = session.submit(correct)
        assertNotNull(result)
        assertTrue("正确答案应判定为 isCorrect", result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue("提交后 isAnswered 应为 true", session.isAnswered)
    }

    @Test
    fun `submit wrong answer resets streak but keeps answered count`() {
        val session = newSession()
        session.start()
        // 提交一个肯定错误的答案
        val result = session.submit("__错误选项__")
        assertNotNull(result)
        assertFalse("错误答案应判定为非正确", result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `streak resets on wrong answer after correct`() {
        val session = newSession()
        session.start()
        // 连续答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.correctCount)
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
        // 答错一题
        session.submit("__错误选项__")
        assertEquals(3, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `best streak tracks maximum across session`() {
        val session = newSession()
        session.start()
        // 连击 2
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 断连
        session.submit("__错误选项__")
        // 连击 3
        repeat(3) {
            session.next()
            session.submit(session.currentQuestion!!.correctAnswer)
        }
        assertEquals(5, session.correctCount)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `submit returns null when no current question`() {
        val session = newSession()
        assertNull("未开始时提交应返回 null", session.submit("大三和弦"))
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        // 再次提交
        assertNull("已作答后再次提交应返回 null", session.submit("大三和弦"))
    }

    @Test
    fun `next clears answered state and generates new question`() {
        val session = newSession()
        session.start()
        val firstQuestion = session.currentQuestion!!
        session.submit(firstQuestion.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse("next 后 isAnswered 应为 false", session.isAnswered)
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `next returns null when not started`() {
        val session = newSession()
        assertNull("未开始时 next 应返回 null", session.next())
    }

    @Test
    fun `reset clears all stats and question`() {
        val session = newSession()
        session.start()
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
        assertFalse(session.isStarted)
    }

    @Test
    fun `history records all answers in order`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit("__错误选项__")
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `last answer tracks most recent submission`() {
        val session = newSession()
        session.start()
        assertNull("开始后首次提交前 lastAnswer 应为 null", session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `accuracy is zero before any answer`() {
        val session = newSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy computes correctly`() {
        val session = newSession()
        session.start()
        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // 答错
        session.submit("__错误选项__")
        session.next()
        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        // 3 题中 2 对 → 0.667
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    @Test
    fun `session uses configured clef and difficulty`() {
        val session = ChordReadingSession(
            ChordReadingEngine(),
            ChordReadingClef.BASS,
            ChordReadingDifficulty.ADVANCED
        )
        session.start()
        assertEquals(ChordReadingClef.BASS, session.clef())
        assertEquals(ChordReadingDifficulty.ADVANCED, session.difficulty())
        // 高级难度应为七和弦
        assertTrue(session.currentQuestion!!.isSeventh)
    }

    @Test
    fun `full lifecycle with multiple questions`() {
        val session = newSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        session.start()
        repeat(10) {
            assertTrue(session.isStarted)
            // 交替答对/答错
            if (it % 2 == 0) {
                session.submit(session.currentQuestion!!.correctAnswer)
            } else {
                session.submit("__错误选项__")
            }
            if (it < 9) session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(5, session.correctCount)
    }
}
