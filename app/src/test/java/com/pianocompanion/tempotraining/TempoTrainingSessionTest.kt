package com.pianocompanion.tempotraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 速度辨识训练会话状态机单元测试。
 */
class TempoTrainingSessionTest {

    private fun newSession(
        difficulty: TempoTrainingDifficulty = TempoTrainingDifficulty.ADVANCED,
        clickCount: Int = 8
    ): TempoTrainingSession {
        return TempoTrainingSession(
            TempoTrainingEngine.withSeed(42),
            difficulty,
            clickCount
        )
    }

    // ── 初始状态 ──────────────────────────────────────

    @Test
    fun `未开始时currentQuestion为null`() {
        val session = newSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `未开始时answeredCount为0`() {
        val session = newSession()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    // ── start ──────────────────────────────────────────

    @Test
    fun `start后生成第一题`() {
        val session = newSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后isAnswered为false`() {
        val session = newSession()
        session.start()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start重置统计`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
        // 再次 start 应重置
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    // ── submit ─────────────────────────────────────────

    @Test
    fun `答对增加correctCount和streak`() {
        val session = newSession()
        session.start()
        val result = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `答错不增加correctCount且streak归零`() {
        val session = newSession()
        session.start()
        val wrongAnswer = session.currentQuestion!!.answerChoices.first {
            it != session.currentQuestion!!.correctAnswer
        }
        val result = session.submit(wrongAnswer)
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `连击正确后答错归零`() {
        val session = newSession(TempoTrainingDifficulty.ADVANCED)
        session.start()
        // 答对 3 次
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.correctCount)
        assertEquals(3, session.currentStreak)
        // 答错
        val wrongAnswer = session.currentQuestion!!.answerChoices.first {
            it != session.currentQuestion!!.correctAnswer
        }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.correctCount) // correctCount 不降
    }

    @Test
    fun `重复submit同一题返回null`() {
        val session = newSession()
        session.start()
        val result1 = session.submit(session.currentQuestion!!.correctAnswer)
        val result2 = session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(result1)
        assertNull(result2)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `未开始时submit返回null`() {
        val session = newSession()
        val result = session.submit("Largo  广板")
        assertNull(result)
    }

    // ── next ───────────────────────────────────────────

    @Test
    fun `next生成新题目`() {
        val session = newSession()
        session.start()
        val q1 = session.currentQuestion
        session.submit(q1!!.correctAnswer)
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        assertNotSame(q1, q2)
    }

    @Test
    fun `next重置isAnswered`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时next返回null`() {
        val session = newSession()
        val result = session.next()
        assertNull(result)
    }

    // ── bestStreak ─────────────────────────────────────

    @Test
    fun `bestStreak跟踪最大连击`() {
        val session = newSession(TempoTrainingDifficulty.ADVANCED)
        session.start()
        // 连对 5 次
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(5, session.bestStreak)
        assertEquals(5, session.currentStreak)
        // 答错
        val wrongAnswer = session.currentQuestion!!.answerChoices.first {
            it != session.currentQuestion!!.correctAnswer
        }
        session.submit(wrongAnswer)
        assertEquals(5, session.bestStreak) // bestStreak 不降
        assertEquals(0, session.currentStreak)
        // 再连对 2 次
        repeat(2) {
            session.next()
            session.submit(session.currentQuestion!!.correctAnswer)
        }
        assertEquals(5, session.bestStreak) // 仍未超过 5
        assertEquals(2, session.currentStreak)
    }

    // ── accuracy ───────────────────────────────────────

    @Test
    fun `accuracy计算正确`() {
        val session = newSession(TempoTrainingDifficulty.ADVANCED)
        session.start()
        // 答对 3、答错 2
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong); session.next()
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        session.submit(wrong); session.next()
        assertEquals(5, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.6, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时accuracy为0`() {
        val session = newSession()
        assertEquals(0.0, session.accuracy, 0.001)
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── history ────────────────────────────────────────

    @Test
    fun `history按顺序记录`() {
        val session = newSession(TempoTrainingDifficulty.ADVANCED)
        session.start()
        val answers = mutableListOf<TempoTrainingAnswerRecord>()
        repeat(4) {
            val q = session.currentQuestion!!
            val answer = q.answerChoices.random(kotlin.random.Random(it))
            val record = session.submit(answer)!!
            answers.add(record)
            session.next()
        }
        assertEquals(4, session.history.size)
        assertEquals(answers, session.history)
    }

    @Test
    fun `history是不可变副本`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history1 = session.history
        assertEquals(1, history1.size)
    }

    // ── reset ──────────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.reset()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `reset后可重新start`() {
        val session = newSession()
        session.start()
        session.reset()
        session.start()
        assertNotNull(session.currentQuestion)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `不同难度会话独立`() {
        val s1 = newSession(TempoTrainingDifficulty.BEGINNER)
        val s2 = newSession(TempoTrainingDifficulty.ADVANCED)
        s1.start()
        s2.start()
        assertTrue(s1.currentQuestion!!.tempo in TempoCategory.BEGINNER_TEMPOS)
        assertTrue(s2.currentQuestion!!.tempo in TempoCategory.ALL)
    }

    @Test
    fun `难度和clickCount可读取`() {
        val session = newSession(TempoTrainingDifficulty.INTERMEDIATE, clickCount = 10)
        assertEquals(TempoTrainingDifficulty.INTERMEDIATE, session.difficulty())
        assertEquals(10, session.clickCount())
    }

    // ── lastAnswer ─────────────────────────────────────

    @Test
    fun `lastAnswer记录最近一次答题`() {
        val session = newSession()
        session.start()
        assertNull(session.lastAnswer)
        val result = session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(result, session.lastAnswer)
    }

    // ── 边界安全 ────────────────────────────────────────

    @Test
    fun `连续大量答题不崩溃`() {
        val session = newSession(TempoTrainingDifficulty.ADVANCED)
        session.start()
        for (i in 1..100) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(100, session.answeredCount)
        assertEquals(100, session.correctCount)
        assertEquals(100, session.bestStreak)
    }
}
