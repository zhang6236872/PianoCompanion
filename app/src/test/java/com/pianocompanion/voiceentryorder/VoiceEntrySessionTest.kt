package com.pianocompanion.voiceentryorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声部进入顺序辨识训练会话状态机单元测试。
 */
class VoiceEntrySessionTest {

    private fun newSession(difficulty: EntryDifficulty = EntryDifficulty.ADVANCED): VoiceEntrySession {
        return VoiceEntrySession(VoiceEntryEngine.withSeed(42), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────

    @Test
    fun `session starts with first question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
    }

    @Test
    fun `start resets counters`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.answeredCount)
        // 再次 start 应重置
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
    }

    @Test
    fun `next generates a new question when started`() {
        val s = newSession()
        s.start()
        val q1 = s.currentQuestion
        s.next()
        assertNotNull(s.currentQuestion)
        // 两题不同引用
        assertTrue(q1 !== s.currentQuestion)
    }

    @Test
    fun `next returns null when not started`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `reset clears everything`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `difficulty returns configured difficulty`() {
        val s = newSession(EntryDifficulty.BEGINNER)
        assertEquals(EntryDifficulty.BEGINNER, s.difficulty())
    }

    // ── 答题 / 判定 ──────────────────────────────────

    @Test
    fun `correct answer increments correct and streak`() {
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
    fun `wrong answer does not increment correct and resets streak`() {
        val s = newSession()
        s.start()
        // 先答对一题建立连击
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        // 再答错
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        val record = s.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        val s = newSession()
        s.start()
        // 连续答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.correctCount)
        assertEquals(3, s.bestStreak)
        // 答错一题
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(3, s.bestStreak) // bestStreak 不因答错降低
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `submit returns null when no question`() {
        val s = newSession()
        assertNull(s.submit("anything"))
    }

    @Test
    fun `double submit prevented`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val first = s.submit(q.correctAnswer)
        val second = s.submit(q.correctAnswer)
        assertNotNull(first)
        assertNull(second) // 第二次提交被拒绝
        assertEquals(1, s.answeredCount)
    }

    @Test
    fun `isAnswered flag toggles correctly`() {
        val s = newSession()
        s.start()
        assertFalse(s.isAnswered)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
    }

    @Test
    fun `lastAnswer updated after submit`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(s.lastAnswer)
        s.next()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `accuracy computed correctly`() {
        val s = newSession()
        s.start()
        // 答对、答错 → 1/2 = 0.5
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(0.5, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy zero when no answers`() {
        val s = newSession()
        assertEquals(0.0, s.accuracy, 0.001)
    }

    // ── 防御性历史副本 ──────────────────────────────────

    @Test
    fun `history is defensive copy`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val h1 = s.history
        assertEquals(1, h1.size)
        // 尝试修改返回的列表，不应影响内部状态
        try {
            (h1 as MutableList).clear()
        } catch (_: UnsupportedOperationException) {
            // toMutableList 返回可变副本，clear 成功——也不应影响内部
        }
        val h2 = s.history
        assertEquals(1, h2.size)
    }

    @Test
    fun `history preserves order`() {
        val s = newSession()
        s.start()
        val answers = mutableListOf<Boolean>()
        repeat(3) {
            val q = s.currentQuestion!!
            val correct = it != 1 // 第二题答错
            val ans = if (correct) q.correctAnswer else q.answerChoices.first { it != q.correctAnswer }
            s.submit(ans)
            answers.add(correct)
            s.next()
        }
        assertEquals(3, s.history.size)
        assertEquals(answers, s.history.map { it.isCorrect })
    }

    @Test
    fun `answer record stores question and answer`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        val record = s.lastAnswer!!
        assertEquals(q.correctAnswer, record.userAnswer)
        assertTrue(record.isCorrect)
        assertNull(record.correctAnswer) // 答对时正确答案为 null
    }

    @Test
    fun `wrong answer record exposes correct answer`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        s.submit(wrong)
        val record = s.lastAnswer!!
        assertFalse(record.isCorrect)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }
}
