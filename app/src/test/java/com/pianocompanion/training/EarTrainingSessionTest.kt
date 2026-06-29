package com.pianocompanion.training

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * [EarTrainingSession] 会话状态机单元测试。
 *
 * 覆盖：启动/答题/下一题/重置全生命周期，统计正确性（得分/连击/准确率/历史）。
 */
class EarTrainingSessionTest {

    private fun createSession(): EarTrainingSession {
        return EarTrainingSession(EarTrainingEngine(Random(42)), ExerciseType.INTERVAL, Difficulty.BEGINNER)
    }

    // ── 启动 ──────────────────────────────────────────────

    @Test
    fun `start 后有当前题目`() {
        val session = createSession()
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start 后统计归零`() {
        val session = createSession()
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start 清空历史`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 答对一题
        session.start() // 重新开始
        assertTrue(session.history.isEmpty())
    }

    // ── 答题 ──────────────────────────────────────────────

    @Test
    fun `答对题增加得分和连击`() {
        val session = createSession()
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        val result = session.submit(correct)
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `答错题不增加得分且重置连击`() {
        val session = createSession()
        session.start()
        // 先答对一题建立连击
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // 答错
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val result = session.submit(wrong)
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `连击记录最大值`() {
        val session = createSession()
        session.start()
        // 连续答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错一题
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(3, session.bestStreak)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `未启动时 submit 返回 null`() {
        val session = createSession()
        assertNull(session.submit("test"))
    }

    @Test
    fun `已答题后再次 submit 返回 null`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        // 再次提交
        assertNull(session.submit(session.currentQuestion!!.correctAnswer))
    }

    // ── 下一题 ────────────────────────────────────────────

    @Test
    fun `next 生成新题目`() {
        val session = createSession()
        session.start()
        val first = session.currentQuestion!!
        session.submit(first.correctAnswer)
        session.next()
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未启动时 next 返回 null`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `连续多题不崩溃`() {
        val session = createSession()
        session.start()
        repeat(10) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(10, session.correctCount)
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        val session = createSession()
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错 1 题
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时准确率为 0`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 历史 ──────────────────────────────────────────────

    @Test
    fun `历史记录答题过程`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)

        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `history 返回不可变副本`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history = session.history
        assertEquals(1, history.size)
    }

    // ── 重置 ──────────────────────────────────────────────

    @Test
    fun `reset 清空所有状态`() {
        val session = createSession()
        session.start()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertFalse(session.isStarted)
        assertTrue(session.history.isEmpty())
    }

    // ── 配置查询 ──────────────────────────────────────────

    @Test
    fun `exerciseType 和 difficulty 查询正确`() {
        val session = EarTrainingSession(
            EarTrainingEngine(), ExerciseType.CHORD, Difficulty.ADVANCED
        )
        assertEquals(ExerciseType.CHORD, session.exerciseType())
        assertEquals(Difficulty.ADVANCED, session.difficulty())
    }

    // ── lastAnswer ────────────────────────────────────────

    @Test
    fun `lastAnswer 记录最后一次答题`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `AnswerRecord correctAnswer 答对时为 null`() {
        val session = createSession()
        session.start()
        val r = session.submit(session.currentQuestion!!.correctAnswer)!!
        assertTrue(r.isCorrect)
        assertNull(r.correctAnswer)
    }

    @Test
    fun `AnswerRecord correctAnswer 答错时返回正确答案`() {
        val session = createSession()
        session.start()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val r = session.submit(wrong)!!
        assertFalse(r.isCorrect)
        assertEquals(session.currentQuestion!!.correctAnswer, r.correctAnswer)
    }
}
