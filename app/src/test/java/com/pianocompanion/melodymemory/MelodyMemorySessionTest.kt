package com.pianocompanion.melodymemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旋律记忆训练会话状态机单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class MelodyMemorySessionTest {

    private fun newSession(
        difficulty: MelodyDifficulty = MelodyDifficulty.INTERMEDIATE,
        tempo: MelodyTempo = MelodyTempo.SLOW
    ): MelodyMemorySession {
        return MelodyMemorySession(MelodyMemoryEngine.withSeed(42L), difficulty, tempo)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `session starts with no question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
    }

    @Test
    fun `start generates first question`() {
        val s = newSession()
        s.start()
        assertTrue(s.isStarted)
        assertNotNull(s.currentQuestion)
        assertFalse(s.isAnswered)
    }

    @Test
    fun `start resets counters`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.answeredCount)
        // 再次 start 应重置
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
    }

    // ── 答题判定 ──────────────────────────────────────────

    @Test
    fun `submit correct answer returns correct record`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val record = s.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `submit wrong answer returns incorrect record`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = s.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    @Test
    fun `submit increments answered count`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.answeredCount)
        s.next()
        s.submit(s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer })
        assertEquals(2, s.answeredCount)
    }

    // ── 连击 ──────────────────────────────────────────────

    @Test
    fun `correct answer increments streak`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.currentStreak)
    }

    @Test
    fun `wrong answer resets streak to zero`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        s.submit(s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer })
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val s = newSession()
        s.start()
        // 连答 3 题正确
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak)
        // 答错一题
        s.submit(s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer })
        assertEquals(3, s.bestStreak)
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `accuracy is zero before any answer`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy calculation`() {
        val s = newSession()
        s.start()
        // 3 正确
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        // 1 错误
        s.submit(s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer })
        assertEquals(0.75, s.accuracy, 0.001)
        assertEquals(4, s.answeredCount)
        assertEquals(3, s.correctCount)
    }

    // ── 边界安全 ──────────────────────────────────────────

    @Test
    fun `submit without start returns null`() {
        val s = newSession()
        assertNull(s.submit("↑ ↑"))
    }

    @Test
    fun `submit after already answered returns null`() {
        val s = newSession()
        s.start()
        val q = s.currentQuestion!!
        s.submit(q.correctAnswer)
        // 重复提交
        assertNull(s.submit(q.correctAnswer))
    }

    @Test
    fun `next without start returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `next generates new question and clears answered flag`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
        assertNotNull(s.currentQuestion)
    }

    // ── 历史 ──────────────────────────────────────────────

    @Test
    fun `history records answers in order`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer })
        assertEquals(2, s.history.size)
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.history.size)
    }

    // ── 访问器 ────────────────────────────────────────────

    @Test
    fun `difficulty accessor returns configured difficulty`() {
        val s = newSession(MelodyDifficulty.ADVANCED, MelodyTempo.NORMAL)
        assertEquals(MelodyDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `tempo accessor returns configured tempo`() {
        val s = newSession(MelodyDifficulty.BEGINNER, MelodyTempo.NORMAL)
        assertEquals(MelodyTempo.NORMAL, s.tempo())
    }

    @Test
    fun `last answer is null before any submission`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
    }

    @Test
    fun `last answer is set after submission`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(s.lastAnswer)
        assertTrue(s.lastAnswer!!.isCorrect)
    }

    // ── 全生命周期 ────────────────────────────────────────

    @Test
    fun `full lifecycle beginner`() {
        val s = MelodyMemorySession(
            MelodyMemoryEngine.withSeed(777L),
            MelodyDifficulty.BEGINNER,
            MelodyTempo.SLOW
        )
        s.start()
        var correct = 0
        repeat(10) {
            val q = s.currentQuestion!!
            val result = s.submit(q.correctAnswer)
            assertNotNull(result)
            if (result!!.isCorrect) correct++
            if (it < 9) s.next()
        }
        assertEquals(10, s.answeredCount)
        assertEquals(10, correct)
        assertEquals(10, s.bestStreak)
    }

    @Test
    fun `full lifecycle advanced with mixed results`() {
        val s = MelodyMemorySession(
            MelodyMemoryEngine.withSeed(888L),
            MelodyDifficulty.ADVANCED,
            MelodyTempo.NORMAL
        )
        s.start()
        repeat(20) { i ->
            val q = s.currentQuestion!!
            // 偶数题答对，奇数题答错
            if (i % 2 == 0) {
                s.submit(q.correctAnswer)
            } else {
                s.submit(q.answerChoices.first { it != q.correctAnswer })
            }
            if (i < 19) s.next()
        }
        assertEquals(20, s.answeredCount)
        assertEquals(10, s.correctCount)
        assertEquals(0.5, s.accuracy, 0.001)
    }
}
