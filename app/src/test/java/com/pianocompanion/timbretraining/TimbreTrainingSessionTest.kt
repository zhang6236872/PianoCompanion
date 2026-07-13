package com.pianocompanion.timbretraining

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 音色辨识训练会话状态机单元测试。
 */
class TimbreTrainingSessionTest {

    private lateinit var session: TimbreTrainingSession

    @Before
    fun setup() {
        val engine = TimbreTrainingEngine.withSeed(42)
        session = TimbreTrainingSession(engine, TimbreTrainingDifficulty.INTERMEDIATE)
    }

    // ── 生命周期 ────────────────────────────────────────

    @Test
    fun `未开始时currentQuestion为null`() {
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start后currentQuestion非null`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后统计清零`() {
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start后history为空`() {
        session.start()
        assertTrue(session.history.isEmpty())
    }

    // ── submit ─────────────────────────────────────────

    @Test
    fun `submit返回正确的结果记录`() {
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(question.correctAnswer, record.userAnswer)
    }

    @Test
    fun `submit错误答案返回isCorrect=false`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(wrongAnswer, record.userAnswer)
    }

    @Test
    fun `submit后answeredCount递增`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `submit正确答案后correctCount和streak递增`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
    }

    @Test
    fun `submit错误答案后streak归零`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `未start时submit返回null`() {
        val record = session.submit("anything")
        assertNull(record)
    }

    @Test
    fun `已答题后再次submit返回null`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit("anything")
        assertNull(second)
    }

    // ── next ───────────────────────────────────────────

    @Test
    fun `next生成新题目`() {
        session.start()
        val firstQuestion = session.currentQuestion
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertNotNull(session.currentQuestion)
        assertNotSame(firstQuestion, session.currentQuestion)
    }

    @Test
    fun `next后isAnswered为false`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未start时next返回null`() {
        assertNull(session.next())
    }

    // ── 连击追踪 ────────────────────────────────────────

    @Test
    fun `连续答对3题后bestStreak为3`() {
        session.start()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        assertEquals(3, session.currentStreak)
    }

    @Test
    fun `答对2题后答错1题streak归零`() {
        session.start()
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(2, session.bestStreak)
        // 答错
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `连击中断后重新累积更新bestStreak`() {
        session.start()
        // 答对3题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错1题
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        session.next()
        // 再答对5题
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(5, session.bestStreak)
        assertEquals(5, session.currentStreak)
    }

    // ── history ────────────────────────────────────────

    @Test
    fun `history按时间顺序记录`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `history返回不可变副本`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val h1 = session.history
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        val h2 = session.history
        assertEquals(1, h1.size)
        assertEquals(2, h2.size)
    }

    // ── lastAnswer ─────────────────────────────────────

    @Test
    fun `lastAnswer记录最后一次答题`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrong)
        assertEquals(record, session.lastAnswer)
    }

    @Test
    fun `lastAnswer初始为null`() {
        session.start()
        assertNull(session.lastAnswer)
    }

    // ── reset ──────────────────────────────────────────

    @Test
    fun `reset清空所有统计`() {
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
        assertFalse(session.isAnswered)
    }

    // ── accuracy ───────────────────────────────────────

    @Test
    fun `accuracy未答题时为0`() {
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy计算正确`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 1/1
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer }) // 1/2
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer) // 2/3
        assertEquals(2.0 / 3.0, session.accuracy, 0.001)
    }

    // ── difficulty 和 noteDurationMs ───────────────────

    @Test
    fun `difficulty返回配置的难度`() {
        val s = TimbreTrainingSession(TimbreTrainingEngine(), TimbreTrainingDifficulty.ADVANCED)
        assertEquals(TimbreTrainingDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `noteDurationMs返回配置的值`() {
        val s = TimbreTrainingSession(TimbreTrainingEngine(), TimbreTrainingDifficulty.BEGINNER, noteDurationMs = 2000)
        assertEquals(2000L, s.noteDurationMs())
    }

    @Test
    fun `默认noteDurationMs与引擎默认一致`() {
        val s = TimbreTrainingSession(TimbreTrainingEngine(), TimbreTrainingDifficulty.BEGINNER)
        assertEquals(TimbreTrainingEngine.DEFAULT_NOTE_DURATION_MS, s.noteDurationMs())
    }

    // ── isAnswered 状态 ────────────────────────────────

    @Test
    fun `submit后isAnswered为true`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `next后isAnswered重置为false`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    // ── answerChoices 正确性 ───────────────────────────

    @Test
    fun `题目answerChoices包含correctAnswer`() {
        session.start()
        val q = session.currentQuestion!!
        assertTrue(q.correctAnswer in q.answerChoices)
    }

    @Test
    fun `题目answerChoices无重复`() {
        session.start()
        val q = session.currentQuestion!!
        assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
    }
}
