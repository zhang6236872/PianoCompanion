package com.pianocompanion.mixedpractice

import org.junit.Assert.*
import org.junit.Test

/**
 * 综合练习会话单元测试。
 */
class MixedPracticeSessionTest {

    private fun createSession(): MixedPracticeSession {
        val engine = MixedPracticeEngine.withSeed(42L)
        return MixedPracticeSession(engine, MixedDifficulty.BEGINNER)
    }

    @Test
    fun `start 后 currentQuestion 非空`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `初始状态统计全为零`() {
        val session = createSession()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `submit 正确答案后统计增加`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val result = session.submit(question.correctAnswer)

        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit 错误答案后连击归零`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        // 先答对一题
        session.submit(question.correctAnswer)
        assertEquals(1, session.currentStreak)

        // 下一题答错
        session.next()
        val q2 = session.currentQuestion!!
        val wrongAnswer = q2.choices.first { it != q2.correctAnswer }
        session.submit(wrongAnswer)

        assertEquals(0, session.currentStreak)
        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
        // bestStreak 保留之前最高记录
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `连击记录跟踪正确`() {
        val session = createSession()
        session.start()
        // 序列：对对错 对对错 对对错 对 → 连击最高为 2
        val answers = listOf(true, true, false, true, true, false, true, true, false, true)
        for (i in answers.indices) {
            val q = session.currentQuestion!!
            if (answers[i]) {
                session.submit(q.correctAnswer)
            } else {
                val wrong = q.choices.first { it != q.correctAnswer }
                session.submit(wrong)
            }
            if (i < answers.lastIndex) session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(7, session.correctCount)
        assertEquals(1, session.currentStreak) // 第 10 题正确（第 9 题错误）→ 连击 1
        assertEquals(2, session.bestStreak) // 最高连续答对 2 题
    }

    @Test
    fun `submit 未开始时返回 null`() {
        val session = createSession()
        val result = session.submit("C")
        assertNull(result)
    }

    @Test
    fun `重复 submit 同一题返回 null`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        session.submit(question.correctAnswer)
        val result2 = session.submit(question.correctAnswer)
        assertNull(result2)
    }

    @Test
    fun `next 未开始时返回 null`() {
        val session = createSession()
        val result = session.next()
        assertNull(result)
    }

    @Test
    fun `next 后生成新题目`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val oldQuestion = session.currentQuestion
        session.next()
        assertNotNull(session.currentQuestion)
        // 可能恰好相同，但 isAnswered 应重置
        assertFalse(session.isAnswered)
    }

    @Test
    fun `reset 后恢复初始状态`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `typeAttempts 和 typeCorrect 正确跟踪`() {
        val session = createSession()
        session.start()
        // 答完至少 10 题，检查统计
        for (i in 1..10) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            if (i < 10) session.next()
        }
        // 总尝试数应为 10
        val totalAttempts = session.typeAttempts.values.sum()
        assertEquals(10, totalAttempts)
        // 正确数也应是 10
        val totalCorrect = session.typeCorrect.values.sum()
        assertEquals(10, totalCorrect)
    }

    @Test
    fun `accuracy 计算正确`() {
        val session = createPracticeSessionWithResults()
        val expectedAccuracy = session.correctCount.toDouble() / session.answeredCount
        assertEquals(expectedAccuracy, session.accuracy, 0.001)
    }

    @Test
    fun `history 记录所有答题历史`() {
        val session = createSession()
        session.start()
        for (i in 1..5) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            if (i < 5) session.next()
        }
        assertEquals(5, session.history.size)
        assertTrue(session.history.all { it.isCorrect })
    }

    @Test
    fun `lastAnswer 返回最后一次答题记录`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        session.next()
        val wrong = session.currentQuestion!!.choices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertFalse(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `difficulty 返回正确难度`() {
        MixedDifficulty.ALL.forEach { d ->
            val session = MixedPracticeSession(MixedPracticeEngine.withSeed(42L), d)
            assertEquals(d, session.difficulty())
        }
    }

    private fun createPracticeSessionWithResults(): MixedPracticeSession {
        val session = createSession()
        session.start()
        for (i in 1..10) {
            val q = session.currentQuestion!!
            if (i % 4 == 0) {
                val wrong = q.choices.first { it != q.correctAnswer }
                session.submit(wrong)
            } else {
                session.submit(q.correctAnswer)
            }
            if (i < 10) session.next()
        }
        return session
    }
}
