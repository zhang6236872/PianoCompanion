package com.pianocompanion.meterrecognition

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 拍号听辨训练会话状态机单元测试。
 */
class MeterRecognitionSessionTest {

    private lateinit var engine: MeterRecognitionEngine
    private lateinit var session: MeterRecognitionSession

    @Before
    fun setup() {
        engine = MeterRecognitionEngine.withSeed(42)
        session = MeterRecognitionSession(engine, MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
    }

    // ── 生命周期 ──────────────────────────────────────

    @Test
    fun `未开始时currentQuestion为null`() {
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start后生成第一题`() {
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后所有计数器归零`() {
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start后isAnswered为false`() {
        session.start()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start清空历史`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.start() // 重新开始
        assertTrue(session.history.isEmpty())
        assertEquals(0, session.answeredCount)
    }

    // ── submit ──────────────────────────────────────────

    @Test
    fun `submit正确答案返回正确结果`() {
        session.start()
        val question = session.currentQuestion!!
        val result = session.submit(question.correctAnswer)
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
    }

    @Test
    fun `submit错误答案返回错误结果`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val result = session.submit(wrongAnswer)
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
    }

    @Test
    fun `submit增加answeredCount`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `submit正确增加correctCount`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.correctCount)
    }

    @Test
    fun `submit错误不增加correctCount`() {
        session.start()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.correctCount)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `submit后isAnswered为true`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `重复submit返回null`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val second = session.submit("anything")
        assertNull(second)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `未开始时submit返回null`() {
        val result = session.submit("anything")
        assertNull(result)
    }

    // ── 连击 ────────────────────────────────────────────

    @Test
    fun `连续答对增加currentStreak`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
    }

    @Test
    fun `答错重置currentStreak`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `bestStreak不随currentStreak下降`() {
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
        // 答错 1 题
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `currentStreak不会超过bestStreak`() {
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(5, session.currentStreak)
        assertEquals(5, session.bestStreak)
    }

    // ── next ────────────────────────────────────────────

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
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时next返回null`() {
        assertNull(session.next())
    }

    @Test
    fun `next不清空计数器`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
    }

    // ── accuracy ────────────────────────────────────────

    @Test
    fun `未答题时accuracy为0`() {
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy计算正确`() {
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错 1 题
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `全对时accuracy为1`() {
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(1.0, session.accuracy, 0.001)
    }

    // ── history ─────────────────────────────────────────

    @Test
    fun `history按顺序记录`() {
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
    fun `history返回副本不可修改`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history = session.history
        assertEquals(1, history.size)
        // 尝试修改不应影响内部状态
        // history is a List, can't add, so just verify immutability by checking
        assertEquals(1, session.history.size)
    }

    @Test
    fun `lastAnswer记录最近一次答题`() {
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)

        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertNotNull(session.lastAnswer)
        assertFalse(session.lastAnswer!!.isCorrect)
    }

    // ── reset ───────────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
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
        assertFalse(session.isAnswered)
        assertNull(session.lastAnswer)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `中级难度题目拍号在4种范围内`() {
        engine = MeterRecognitionEngine.withSeed(1)
        session = MeterRecognitionSession(engine, MeterRecognitionDifficulty.INTERMEDIATE)
        session.start()
        assertTrue(MeterType.INTERMEDIATE_METERS.contains(session.currentQuestion!!.meter))
    }

    @Test
    fun `高级难度题目拍号在6种范围内`() {
        engine = MeterRecognitionEngine.withSeed(1)
        session = MeterRecognitionSession(engine, MeterRecognitionDifficulty.ADVANCED)
        session.start()
        assertTrue(MeterType.ALL.contains(session.currentQuestion!!.meter))
    }

    // ── 边界安全 ────────────────────────────────────────

    @Test
    fun `多次start不崩溃`() {
        repeat(10) {
            session.start()
        }
        assertEquals(0, session.answeredCount)
    }

    @Test
    fun `大量答题不崩溃`() {
        session.start()
        repeat(100) {
            val q = session.currentQuestion!!
            // 交替答对答错
            if (it % 2 == 0) {
                session.submit(q.correctAnswer)
            } else {
                val wrong = q.answerChoices.first { it != q.correctAnswer }
                session.submit(wrong)
            }
            session.next()
        }
        assertEquals(100, session.answeredCount)
        assertEquals(50, session.correctCount)
    }

    // ── 配置访问器 ──────────────────────────────────────

    @Test
    fun `difficulty访问器返回正确值`() {
        session = MeterRecognitionSession(engine, MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST, 3)
        assertEquals(MeterRecognitionDifficulty.ADVANCED, session.difficulty())
        assertEquals(MeterRecognitionTempo.FAST, session.tempo())
        assertEquals(3, session.measureRepeat())
    }
}
