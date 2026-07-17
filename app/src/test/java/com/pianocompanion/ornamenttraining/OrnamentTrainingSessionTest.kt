package com.pianocompanion.ornamenttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装饰音辨识训练会话状态机单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：会话生命周期、答题判定、连击/准确率、历史记录、重置。
 */
class OrnamentTrainingSessionTest {

    private fun newSession(difficulty: OrnamentDifficulty = OrnamentDifficulty.BEGINNER): OrnamentTrainingSession =
        OrnamentTrainingSession(OrnamentTrainingEngine.withSeed(42L), difficulty)

    // ── 生命周期 ────────────────────────────────────────────

    @Test
    fun `session starts not started`() {
        val s = newSession()
        assertFalse("新会话不应已开始", s.isStarted)
        assertNull(s.currentQuestion)
    }

    @Test
    fun `start generates first question`() {
        val s = newSession()
        s.start()
        assertTrue("start 后应已开始", s.isStarted)
        assertNotNull("应有当前题目", s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertFalse("首题不应已作答", s.isAnswered)
    }

    @Test
    fun `difficulty is accessible`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        assertEquals(OrnamentDifficulty.ADVANCED, s.difficulty())
    }

    // ── 答题判定 ──────────────────────────────────────────

    @Test
    fun `submit correct answer returns correct record`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue("正确答案应判定为正确", record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.bestStreak)
        assertTrue("提交后应标记为已作答", s.isAnswered)
    }

    @Test
    fun `submit wrong answer returns incorrect record`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        val q = s.currentQuestion!!
        // 找一个错误答案
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)
        assertNotNull(record)
        assertFalse("错误答案应判定为错误", record!!.isCorrect)
        assertEquals(q.correctAnswer, record.correctAnswer)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `submit when not started returns null`() {
        val s = newSession()
        val record = s.submit("颤音")
        assertNull("未开始时提交应返回 null", record)
    }

    @Test
    fun `submit twice on same question returns null second time`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        val second = s.submit(q.correctAnswer)
        assertNull("已作答的题目再次提交应返回 null", second)
        assertEquals("答题数不应重复增加", 1, s.answeredCount)
    }

    // ── 连击 ─────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct answers`() {
        val s = newSession(OrnamentDifficulty.BEGINNER)
        s.start()
        // BEGINNER 只有 2 个选项，连答 5 题
        repeat(5) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(5, s.correctCount)
        assertEquals(5, s.currentStreak)
        assertEquals(5, s.bestStreak)
    }

    @Test
    fun `streak resets on wrong answer`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        // 连答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        // 第 4 题答错
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun `best streak survives reset of current streak`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        repeat(4) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        s.next()
        repeat(2) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(2, s.currentStreak)
        assertEquals(4, s.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `accuracy is zero before any answer`() {
        val s = newSession()
        assertEquals(0.0, s.accuracy, 0.0001)
        s.start()
        assertEquals(0.0, s.accuracy, 0.0001)
    }

    @Test
    fun `accuracy computes correctly`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        // 答对 2 题
        repeat(2) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        // 答错 1 题
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(3, s.answeredCount)
        assertEquals(2, s.correctCount)
        assertEquals(2.0 / 3.0, s.accuracy, 0.0001)
    }

    // ── 历史 ─────────────────────────────────────────────

    @Test
    fun `history records all answers in order`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val q2 = s.currentQuestion!!
        s.submit(q2.answerChoices.first { it != q2.correctAnswer })
        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `history is a defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val history = s.history
        // 返回的列表应不可影响内部状态
        assertEquals(1, history.size)
    }

    // ── 下一题 ──────────────────────────────────────────

    @Test
    fun `next generates new question and clears answered state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        val first = s.currentQuestion
        s.next()
        assertFalse("next 后应清除已作答状态", s.isAnswered)
        assertNotNull(s.currentQuestion)
        // 题目对象应更新（新题目）
        // 注意：不比较内容相等，因为可能相同；但 lastAnswer 应清空
        assertNull("next 后 lastAnswer 应为 null", s.lastAnswer)
    }

    @Test
    fun `next when not started returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    // ── 重置 ─────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
        assertFalse(s.isAnswered)
        assertNull(s.lastAnswer)
    }

    @Test
    fun `start after reset works`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        s.start()
        assertNotNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
    }

    // ── lastAnswer ─────────────────────────────────────────

    @Test
    fun `lastAnswer is null before any submit`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `lastAnswer reflects most recent submission`() {
        val s = newSession(OrnamentDifficulty.ADVANCED)
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertNotNull(s.lastAnswer)
        assertEquals(wrong, s.lastAnswer!!.userAnswer)
    }
}
