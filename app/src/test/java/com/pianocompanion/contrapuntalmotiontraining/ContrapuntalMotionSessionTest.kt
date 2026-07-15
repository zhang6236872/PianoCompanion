package com.pianocompanion.contrapuntalmotiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 声部运动辨识训练会话状态机单元测试。
 *
 * 验证会话生命周期、状态转换、连击/准确率计算。
 */
class ContrapuntalMotionSessionTest {

    private fun createSession(difficulty: ContrapuntalMotionDifficulty = ContrapuntalMotionDifficulty.ADVANCED): ContrapuntalMotionSession {
        return ContrapuntalMotionSession(ContrapuntalMotionEngine.withSeed(42L), difficulty)
    }

    // ── 会话开始 ──────────────────────────────────────────

    @Test
    fun `start 后有当前题目`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)

        session.start()

        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `start 重置统计`() {
        val session = createSession()
        session.start()
        // 答几题
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!
        session.submit(q2.correctAnswer)

        assertEquals(2, session.answeredCount)

        // 重新开始
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertTrue(session.history.isEmpty())
    }

    // ── 答题 ──────────────────────────────────────────

    @Test
    fun `submit 正确答案返回 isCorrect`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!

        val record = session.submit(q.correctAnswer)

        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(q.correctAnswer, record.userAnswer)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit 错误答案返回 not isCorrect`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.filter { it != q.correctAnswer }.first()

        val record = session.submit(wrongAnswer)

        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(wrongAnswer, record.userAnswer)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `submit 后不能重复提交`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!

        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)

        assertNull("已答题后不应再次提交", second)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `未开始时 submit 返回 null`() {
        val session = createSession()
        val record = session.submit("任意")
        assertNull(record)
    }

    // ── 连击 ──────────────────────────────────────────

    @Test
    fun `连续答对更新连击和最佳连击`() {
        val session = createSession()
        session.start()

        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        assertEquals(3, session.correctCount)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `答错归零连击`() {
        val session = createSession()
        session.start()

        // 答对 2 题
        repeat(2) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(2, session.currentStreak)

        // 第 3 题答错
        val q3 = session.currentQuestion!!
        val wrong = q3.answerChoices.filter { it != q3.correctAnswer }.first()
        session.submit(wrong)

        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak) // 最佳仍为 2
    }

    @Test
    fun `连击中断后恢复更新最佳连击`() {
        val session = createSession()
        session.start()

        // 答对 2
        repeat(2) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        // 答错 1
        val q3 = session.currentQuestion!!
        session.submit(q3.answerChoices.first { it != q3.correctAnswer })
        session.next()

        // 再答对 3
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `准确率计算正确`() {
        val session = createSession()
        session.start()

        // 答对 3
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        // 答错 1
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })

        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时准确率为 0`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 下一题 ──────────────────────────────────────────

    @Test
    fun `next 生成新题目`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion!!

        session.next()
        val q2 = session.currentQuestion!!

        assertNotNull(q2)
        assertFalse(session.isAnswered)
        assertNotSame(q1, q2)
    }

    @Test
    fun `未开始时 next 返回 null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── 重置 ──────────────────────────────────────────

    @Test
    fun `reset 清空所有状态`() {
        val session = createSession()
        session.start()
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
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

    // ── 历史 ──────────────────────────────────────────

    @Test
    fun `历史按时间顺序记录`() {
        val session = createSession()
        session.start()

        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }

        assertEquals(3, session.history.size)
        // 验证历史按提交顺序记录：每条记录的问题应该各不相同（引擎每次生成不同种子）
        val seeds = session.history.map { it.question.seed }
        assertEquals(3, seeds.toSet().size)
        // 验证每条记录都正确标记了答题结果
        session.history.forEach { record ->
            assertTrue(record.isCorrect)
        }
    }

    // ── 难度 ──────────────────────────────────────────

    @Test
    fun `difficulty 返回正确难度`() {
        ContrapuntalMotionDifficulty.ALL.forEach { d ->
            val session = ContrapuntalMotionSession(ContrapuntalMotionEngine.withSeed(1L), d)
            assertEquals(d, session.difficulty())
        }
    }

    // ── lastAnswer ──────────────────────────────────────

    @Test
    fun `lastAnswer 更新正确`() {
        val session = createSession()
        session.start()

        assertNull(session.lastAnswer)

        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)

        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }
}
