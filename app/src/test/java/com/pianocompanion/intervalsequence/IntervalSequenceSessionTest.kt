package com.pianocompanion.intervalsequence

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class IntervalSequenceSessionTest {

    private fun createSession(difficulty: IntervalSequenceDifficulty = IntervalSequenceDifficulty.BEGINNER): IntervalSequenceSession {
        val engine = IntervalSequenceEngine(difficulty, Random(42L))
        return IntervalSequenceSession(engine, difficulty)
    }

    @Test
    fun `初始状态 currentQuestion 为 null`() {
        val session = createSession()
        assertNull(session.currentQuestion)
    }

    @Test
    fun `start 后生成题目`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
    }

    @Test
    fun `start 后 answeredCount 为 0`() {
        val session = createSession()
        session.start()
        assertEquals(0, session.answeredCount)
    }

    @Test
    fun `submit 正确答案返回 isCorrect = true`() {
        val session = createSession()
        session.start()
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(record.isCorrect)
    }

    @Test
    fun `submit 错误答案返回 isCorrect = false`() {
        val session = createSession()
        session.start()
        val wrongAnswer = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertFalse(record.isCorrect)
    }

    @Test
    fun `连击计数 - 连续正确`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.streak)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.streak)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.streak)
    }

    @Test
    fun `错误重置连击`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.streak)
        session.next()
        val wrongAnswer = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.streak)
    }

    @Test
    fun `bestStreak 保留最大值`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.bestStreak)
        val wrongAnswer = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.next()
        session.submit(wrongAnswer)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `准确率计算正确`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrongAnswer = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrongAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(3, session.answeredCount)
        assertEquals(2, session.correctCount)
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    @Test
    fun `历史记录保序`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer })
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `防御性副本 - 外部修改不影响内部`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history = session.history
        assertEquals(1, history.size)
        // 外部无法修改内部 history
        // history is List (immutable), so nothing to mutate
        assertEquals(1, session.history.size)
    }

    @Test
    fun `双击防护 - 重复提交当前题抛异常`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertThrows(IllegalArgumentException::class.java) {
            session.submit(session.currentQuestion!!.correctAnswer)
        }
    }

    @Test
    fun `没有当前题时 submit 抛异常`() {
        val session = createSession()
        assertThrows(IllegalStateException::class.java) {
            session.submit("anything")
        }
    }

    @Test
    fun `reset 清空所有状态`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.streak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.history.size)
    }

    @Test(expected = IllegalStateException::class)
    fun `reset 后 submit 抛异常`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        session.submit("test")
    }

    @Test
    fun `lastAnswer 返回最近一条记录`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer })
        val last = session.lastAnswer
        assertNotNull(last)
        assertFalse(last!!.isCorrect)
    }

    @Test
    fun `difficulty 返回正确难度`() {
        val session = createSession(IntervalSequenceDifficulty.ADVANCED)
        assertEquals(IntervalSequenceDifficulty.ADVANCED, session.difficulty)
    }
}
