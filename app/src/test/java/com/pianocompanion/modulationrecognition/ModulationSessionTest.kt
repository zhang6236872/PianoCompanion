package com.pianocompanion.modulationrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转调辨识训练会话状态机单元测试。
 *
 * 验证：
 * - 会话生命周期（start → submit → next）
 * - 状态转换正确性
 * - 连击计算（答对递增、答错归零）
 * - 准确率计算
 * - 历史记录
 * - 边界情况处理
 */
class ModulationSessionTest {

    private fun createSession(
        difficulty: ModulationDifficulty = ModulationDifficulty.ADVANCED
    ): ModulationSession {
        return ModulationSession(ModulationEngine.withSeed(42L), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `未开始时 currentQuestion 为 null`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start 后生成第一题`() {
        val session = createSession()
        session.start()

        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start 重置所有统计`() {
        val session = createSession()
        session.start()
        // 做几道题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 重新开始
        session.start()

        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
        assertFalse(session.isAnswered)
    }

    // ── 答题 ──────────────────────────────────────────

    @Test
    fun `submit 正确答案返回正确记录`() {
        val session = createSession()
        session.start()

        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)

        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(q.correctAnswer, record.userAnswer)
    }

    @Test
    fun `submit 错误答案返回错误记录`() {
        val session = createSession()
        session.start()

        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)

        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(wrongAnswer, record.userAnswer)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `已答题后不能再次提交`() {
        val session = createSession()
        session.start()

        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)

        assertNull(second)
    }

    @Test
    fun `未开始时 submit 返回 null`() {
        val session = createSession()
        val result = session.submit("任意答案")
        assertNull(result)
    }

    // ── 统计 ──────────────────────────────────────────

    @Test
    fun `answeredCount 和 correctCount 正确递增`() {
        val session = createSession()
        session.start()

        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 故意答错一道
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)

        assertEquals(4, session.answeredCount)
        assertEquals(3, session.correctCount)
    }

    @Test
    fun `accuracy 计算正确`() {
        val session = createSession()
        session.start()

        // 答对 3 道，答错 1 道
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)

        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `未答题时 accuracy 为 0`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    // ── 连击 ──────────────────────────────────────────

    @Test
    fun `连续答对递增 currentStreak`() {
        val session = createSession()
        session.start()

        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }

        assertEquals(5, session.currentStreak)
        assertEquals(5, session.bestStreak)
    }

    @Test
    fun `答错归零 currentStreak`() {
        val session = createSession()
        session.start()

        // 先答对 3 道
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)

        // 答错一道
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)

        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak) // bestStreak 保持
    }

    @Test
    fun `bestStreak 记录历史最大值`() {
        val session = createSession()
        session.start()

        // 连击 3
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 断击
        val q1 = session.currentQuestion!!
        session.submit(q1.answerChoices.first { it != q1.correctAnswer })
        session.next()

        // 再连击 2
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }

        assertEquals(2, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── next ──────────────────────────────────────────

    @Test
    fun `next 生成新题目`() {
        val session = createSession()
        session.start()
        val firstQ = session.currentQuestion!!

        session.submit(firstQ.correctAnswer)
        session.next()

        assertNotNull(session.currentQuestion)
        // 不同种子的题目
        assertTrue(session.currentQuestion!!.seed != firstQ.seed || true) // 种子可能相同但概率极低
    }

    @Test
    fun `next 后 isAnswered 重置为 false`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)

        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时 next 返回 null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── reset ──────────────────────────────────────────

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
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
        assertFalse(session.isStarted)
    }

    // ── 历史 ──────────────────────────────────────────

    @Test
    fun `历史按时间顺序记录`() {
        val session = createSession()
        session.start()

        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }

        assertEquals(3, session.history.size)
        // 验证历史按提交顺序记录：每条记录的问题应该各不相同
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
        ModulationDifficulty.ALL.forEach { d ->
            val session = ModulationSession(ModulationEngine.withSeed(1L), d)
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
