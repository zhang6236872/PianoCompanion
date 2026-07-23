package com.pianocompanion.texturerecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureCategorySessionTest {

    private fun newSession(difficulty: MusicTextureDifficulty = MusicTextureDifficulty.BEGINNER): TextureCategorySession =
        TextureCategorySession(TextureCategoryEngine.withSeed(10L), difficulty)

    @Test
    fun `start 后生成第一题`() {
        val session = newSession()
        assertFalse(session.isStarted)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `submit 正确答案后计数增加且 currentStreak 递增`() {
        val session = newSession()
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        val record = session.submit(correct)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit 错误答案后 currentStreak 归零`() {
        val session = newSession()
        session.start()
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `连续答对后 bestStreak 正确更新`() {
        val session = newSession(MusicTextureDifficulty.INTERMEDIATE)
        session.start()
        // 连续答对 3 题
        repeat(3) {
            val correct = session.currentQuestion!!.correctAnswer
            session.submit(correct)
            session.next()
        }
        assertEquals(3, session.answeredCount)
        assertEquals(3, session.correctCount)
        assertEquals(3, session.bestStreak)
        assertEquals(3, session.currentStreak)
    }

    @Test
    fun `答对-答错-答对后 bestStreak 保留最大值`() {
        val session = newSession(MusicTextureDifficulty.INTERMEDIATE)
        session.start()
        // 答对 2
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        assertEquals(2, session.bestStreak)
        // 答错 1（打断连击）
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong); session.next()
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
        // 答对 1
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        assertEquals(1, session.currentStreak)
        assertEquals(2, session.bestStreak) // 仍为历史最大
    }

    @Test
    fun `已作答的题目再次 submit 返回 null`() {
        val session = newSession()
        session.start()
        val correct = session.currentQuestion!!.correctAnswer
        session.submit(correct)
        val second = session.submit(correct)
        assertNull(second)
        assertEquals(1, session.answeredCount) // 不重复计数
    }

    @Test
    fun `未 start 时 submit 返回 null`() {
        val session = newSession()
        assertNull(session.submit("任意"))
    }

    @Test
    fun `next 在未开始时返回 null`() {
        val session = newSession()
        assertNull(session.next())
    }

    @Test
    fun `reset 清空所有统计`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.bestStreak)
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `history 返回防御性副本（修改不影响内部状态）`() {
        val session = newSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val history = session.history
        assertEquals(1, history.size)
        // 修改副本（运行时为 MutableList，验证内部不受影响）
        (history as MutableList).clear()
        // 内部不受影响
        assertEquals(1, session.history.size)
    }

    @Test
    fun `accuracy 计算正确`() {
        val session = newSession(MusicTextureDifficulty.INTERMEDIATE)
        session.start()
        // 答对 1
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        // 答错 1
        val wrong = session.currentQuestion!!.answerChoices.first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong); session.next()
        assertEquals(0.5, session.accuracy, 0.0001)
    }

    @Test
    fun `未答题时 accuracy 为 0`() {
        val session = newSession()
        assertEquals(0.0, session.accuracy, 0.0001)
    }

    @Test
    fun `difficulty 返回配置的难度`() {
        val session = newSession(MusicTextureDifficulty.ADVANCED)
        assertEquals(MusicTextureDifficulty.ADVANCED, session.difficulty())
    }
}
