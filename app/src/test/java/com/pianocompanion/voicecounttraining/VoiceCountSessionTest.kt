package com.pianocompanion.voicecounttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceCountSession] 单元测试。
 */
class VoiceCountSessionTest {

    private fun newSession(difficulty: VoiceCountDifficulty = VoiceCountDifficulty.INTERMEDIATE): VoiceCountSession =
        VoiceCountSession(VoiceCountEngine.withSeed(1L), difficulty)

    @Test
    fun `未开始时 currentQuestion 为 null`() {
        val s = newSession()
        assertNull(s.currentQuestion)
        assertFalse(s.isStarted)
    }

    @Test
    fun `start 后生成第一题`() {
        val s = newSession()
        s.start()
        assertNotNull(s.currentQuestion)
        assertTrue(s.isStarted)
    }

    @Test
    fun `start 重置统计`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.answeredCount)
        // 再次 start 应清零
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.history.size)
    }

    @Test
    fun `submit 正确答案`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.bestStreak)
    }

    @Test
    fun `submit 错误答案`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `连击 - 连续答对递增，答错归零`() {
        val s = newSession()
        s.start()
        // 连续 3 次答对
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        // 答错一次
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)  // 最长连击保留
    }

    @Test
    fun `bestStreak 记录最长连击`() {
        val s = newSession()
        s.start()
        // 连击 2
        repeat(2) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        // 答错
        val q1 = s.currentQuestion!!
        s.submit(q1.answerChoices.first { it != q1.correctAnswer })
        // 连击 1（不超过 best）
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.bestStreak)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `submit 后 isAnswered 为 true`() {
        val s = newSession()
        s.start()
        assertFalse(s.isAnswered)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
    }

    @Test
    fun `已作答时再次 submit 返回 null`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val again = s.submit("1 个音（单音）")
        assertNull(again)
    }

    @Test
    fun `未开始时 submit 返回 null`() {
        val s = newSession()
        assertNull(s.submit("1 个音（单音）"))
    }

    @Test
    fun `next 生成新题并清除 isAnswered`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertNotNull(s.currentQuestion)
        assertFalse(s.isAnswered)
    }

    @Test
    fun `未开始时 next 返回 null`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `reset 清空所有状态`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.reset()
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.history.size)
        assertFalse(s.isStarted)
    }

    @Test
    fun `history 按时间顺序记录答题`() {
        val s = newSession()
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.history.size)
        assertTrue(s.history.all { it.isCorrect })
    }

    @Test
    fun `history getter 返回防御性副本`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val h1 = s.history
        // 修改返回的列表不影响内部状态（getter 返回独立副本）
        (h1 as MutableList).clear()
        assertEquals(1, s.history.size)
    }

    @Test
    fun `accuracy 未答题时为 0`() {
        val s = newSession()
        assertEquals(0.0, s.accuracy, 0.0001)
        s.start()
        assertEquals(0.0, s.accuracy, 0.0001)
    }

    @Test
    fun `accuracy 计算正确`() {
        val s = newSession()
        s.start()
        // 答对 2 答错 1 = 2/3
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val q = s.currentQuestion!!
        s.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(3, s.answeredCount)
        assertEquals(2, s.correctCount)
        assertEquals(2.0 / 3.0, s.accuracy, 0.0001)
    }

    @Test
    fun `difficulty 返回构造时的难度`() {
        val s = VoiceCountSession(VoiceCountEngine.withSeed(1L), VoiceCountDifficulty.ADVANCED)
        assertEquals(VoiceCountDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `lastAnswer 在 submit 后更新`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        assertNotNull(s.lastAnswer)
        assertTrue(s.lastAnswer!!.isCorrect)
    }

    @Test
    fun `AnswerRecord correctAnswer 答错时返回正确答案`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrong)!!
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `AnswerRecord correctAnswer 答对时为 null`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)!!
        assertNull(record.correctAnswer)
    }
}
