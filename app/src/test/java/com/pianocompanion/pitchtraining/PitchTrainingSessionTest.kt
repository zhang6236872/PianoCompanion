package com.pianocompanion.pitchtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绝对音高训练会话状态机单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class PitchTrainingSessionTest {

    private fun newSession(difficulty: PitchTrainingDifficulty = PitchTrainingDifficulty.BEGINNER): PitchTrainingSession {
        return PitchTrainingSession(PitchTrainingEngine.withSeed(42L), difficulty)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `session not started has no question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
    }

    @Test
    fun `start generates first question`() {
        val s = newSession()
        s.start()
        assertTrue(s.isStarted)
        assertTrue(s.currentQuestion != null)
        assertEquals(0, s.answeredCount)
        assertFalse(s.isAnswered)
    }

    @Test
    fun `start resets counters`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.answeredCount)
        // restart
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    // ── 答题判定 ──────────────────────────────────────────

    @Test
    fun `submit correct answer increments correct count and streak`() {
        val s = newSession()
        s.start()
        val correct = s.currentQuestion!!.correctAnswer
        val record = s.submit(correct)
        assertTrue(record != null)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
        assertTrue(s.isAnswered)
    }

    @Test
    fun `submit wrong answer does not increment correct count and resets streak`() {
        val s = newSession()
        s.start()
        // Build a wrong answer
        val correct = s.currentQuestion!!.correctAnswer
        val wrong = PitchClass.ALL.first { it != correct }
        val record = s.submit(wrong)
        assertTrue(record != null)
        assertFalse(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    // ── 连击 ──────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct answers`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.BEGINNER)
        s.start()
        repeat(5) {
            s.submit(s.currentQuestion!!.correctAnswer)
            assertEquals(it + 1, s.currentStreak)
            s.next()
        }
        assertEquals(5, s.bestStreak)
    }

    @Test
    fun `streak resets on wrong answer`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.BEGINNER)
        s.start()
        // 3 correct
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        // wrong
        val correct = s.currentQuestion!!.correctAnswer
        val wrong = PitchClass.ALL.first { it != correct }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.BEGINNER)
        s.start()
        repeat(4) { s.submit(s.currentQuestion!!.correctAnswer); s.next() }
        val bestAfter4 = s.bestStreak
        val correct = s.currentQuestion!!.correctAnswer
        val wrong = PitchClass.ALL.first { it != correct }
        s.submit(wrong)
        assertEquals(bestAfter4, s.bestStreak)
        s.next()
        repeat(2) { s.submit(s.currentQuestion!!.correctAnswer); s.next() }
        assertEquals(bestAfter4, s.bestStreak)
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `accuracy is correct ratio`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.BEGINNER)
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer) // correct
        s.next()
        val correct2 = s.currentQuestion!!.correctAnswer
        val wrong = PitchClass.ALL.first { it != correct2 }
        s.submit(wrong) // wrong
        assertEquals(2, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(0.5, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero when not answered`() {
        val s = newSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.001)
    }

    // ── 历史 ──────────────────────────────────────────────

    @Test
    fun `history records answers in order`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.BEGINNER)
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val correct2 = s.currentQuestion!!.correctAnswer
        val wrong = PitchClass.ALL.first { it != correct2 }
        s.submit(wrong)
        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `history is empty before any answer`() {
        val s = newSession()
        s.start()
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `last answer is set after submit`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.lastAnswer != null)
        assertTrue(s.lastAnswer!!.isCorrect)
    }

    // ── 边界安全 ──────────────────────────────────────────

    @Test
    fun `submit when not started returns null`() {
        val s = newSession()
        assertNull(s.submit(PitchClass.C))
    }

    @Test
    fun `submit twice returns null on second`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNull(s.submit(PitchClass.C))
    }

    @Test
    fun `next when not started returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    // ── next 生成新题 ─────────────────────────────────────

    @Test
    fun `next generates a new question`() {
        val s = newSession()
        s.start()
        val q1 = s.currentQuestion
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        assertTrue(s.currentQuestion != null)
        // After next, isAnswered should be false
        assertFalse(s.isAnswered)
    }

    // ── reset ────────────────────────────────────────────

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
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
    }

    // ── 难度访问 ──────────────────────────────────────────

    @Test
    fun `difficulty accessor returns correct difficulty`() {
        PitchTrainingDifficulty.ALL.forEach { d ->
            val s = PitchTrainingSession(PitchTrainingEngine(), d)
            assertEquals(d, s.difficulty())
        }
    }

    // ── 全生命周期（多难度多题）────────────────────────────

    @Test
    fun `full lifecycle beginner 10 questions`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.BEGINNER)
        s.start()
        var correct = 0
        repeat(10) {
            val q = s.currentQuestion!!
            val record = s.submit(q.correctAnswer)
            if (record!!.isCorrect) correct++
            s.next()
        }
        assertEquals(10, s.answeredCount)
        assertEquals(10, correct)
        assertEquals(10, s.bestStreak)
    }

    @Test
    fun `full lifecycle intermediate 15 questions`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.INTERMEDIATE)
        s.start()
        repeat(15) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        assertEquals(15, s.answeredCount)
        assertEquals(15, s.correctCount)
    }

    @Test
    fun `full lifecycle advanced 20 questions`() {
        val s = PitchTrainingSession(PitchTrainingEngine(), PitchTrainingDifficulty.ADVANCED)
        s.start()
        repeat(20) {
            val q = s.currentQuestion!!
            s.submit(q.correctAnswer)
            s.next()
        }
        assertEquals(20, s.answeredCount)
        assertEquals(20, s.correctCount)
        assertEquals(20, s.bestStreak)
    }
}
