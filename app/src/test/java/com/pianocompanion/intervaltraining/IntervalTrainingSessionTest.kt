package com.pianocompanion.intervaltraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音程听辨训练会话状态机单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class IntervalTrainingSessionTest {

    private fun newSession(difficulty: IntervalDifficulty = IntervalDifficulty.INTERMEDIATE): IntervalTrainingSession {
        return IntervalTrainingSession(
            IntervalTrainingEngine.withSeed(42L),
            difficulty,
            PlayDirection.ASCENDING
        )
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `fresh session is not started`() {
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
        assertEquals(0, s.answeredCount)
    }

    @Test
    fun `start can be called multiple times resetting state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
    }

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.options.first { it != s.currentQuestion!!.correctAnswer })
        s.reset()
        assertFalse(s.isStarted)
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.history.size)
    }

    // ── 答题判定 ──────────────────────────────────────────

    @Test
    fun `submit correct answer returns correct record`() {
        val s = newSession()
        s.start()
        val record = s.submit(s.currentQuestion!!.correctAnswer)!!
        assertTrue(record.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `submit wrong answer returns incorrect record`() {
        val s = newSession()
        s.start()
        val wrong = s.currentQuestion!!.options.first { it != s.currentQuestion!!.correctAnswer }
        val record = s.submit(wrong)!!
        assertFalse(record.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    // ── 边界安全 ──────────────────────────────────────────

    @Test
    fun `submit before start returns null`() {
        val s = newSession()
        assertNull(s.submit(IntervalType.MAJOR_THIRD))
    }

    @Test
    fun `submit after already answered returns null`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertNull(s.submit(s.currentQuestion!!.correctAnswer))
    }

    @Test
    fun `next before start returns null`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `isAnswered is false before submit and true after`() {
        val s = newSession()
        s.start()
        assertFalse(s.isAnswered)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
    }

    @Test
    fun `next resets isAnswered to false`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
    }

    // ── 连击 ──────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.currentStreak)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun `streak resets to zero on wrong answer`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(2, s.currentStreak)
        s.next()
        val wrong = s.currentQuestion!!.options.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(2, s.bestStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val s = newSession()
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak)
        val wrong = s.currentQuestion!!.options.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(3, s.bestStreak)
        s.next()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(3, s.bestStreak)
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `accuracy is zero before any answers`() {
        val s = newSession()
        assertEquals(0.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy calculation`() {
        val s = newSession()
        s.start()
        // 2 correct, 1 wrong = 0.667
        s.submit(s.currentQuestion!!.correctAnswer); s.next()
        s.submit(s.currentQuestion!!.correctAnswer); s.next()
        val wrong = s.currentQuestion!!.options.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(3, s.answeredCount)
        assertEquals(2, s.correctCount)
        assertEquals(2.0 / 3.0, s.accuracy, 0.001)
    }

    // ── 历史记录 ──────────────────────────────────────────

    @Test
    fun `history records are in order`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        val wrong = s.currentQuestion!!.options.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(2, s.history.size)
        assertTrue(s.history[0].isCorrect)
        assertFalse(s.history[1].isCorrect)
    }

    @Test
    fun `last answer is updated after each submit`() {
        val s = newSession()
        s.start()
        val r1 = s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(r1, s.lastAnswer)
        s.next()
        val r2 = s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(r2, s.lastAnswer)
        assertNotEqualsRecords(r1!!, r2!!)
    }

    private fun assertNotEqualsRecords(a: IntervalAnswerRecord, b: IntervalAnswerRecord) {
        // records should be different objects (different questions)
        assertTrue(a !== b || a.question !== b.question)
    }

    // ── 难度/方向访问器 ───────────────────────────────────

    @Test
    fun `difficulty accessor returns configured difficulty`() {
        IntervalDifficulty.ALL.forEach { d ->
            val s = IntervalTrainingSession(IntervalTrainingEngine(), d)
            assertEquals(d, s.difficulty())
        }
    }

    @Test
    fun `playDirection accessor returns configured direction`() {
        PlayDirection.ALL.forEach { dir ->
            val s = IntervalTrainingSession(IntervalTrainingEngine(), IntervalDifficulty.BEGINNER, dir)
            assertEquals(dir, s.playDirection())
        }
    }

    // ── 全生命周期 ────────────────────────────────────────

    @Test
    fun `full lifecycle beginner`() {
        val s = IntervalTrainingSession(IntervalTrainingEngine.withSeed(1L), IntervalDifficulty.BEGINNER)
        s.start()
        repeat(10) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(10, s.answeredCount)
        assertEquals(10, s.correctCount)
        assertEquals(1.0, s.accuracy, 0.001)
        assertEquals(10, s.bestStreak)
    }

    @Test
    fun `full lifecycle advanced`() {
        val s = IntervalTrainingSession(IntervalTrainingEngine.withSeed(1L), IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC)
        s.start()
        repeat(20) {
            val q = s.currentQuestion!!
            // 答对奇数题，答错偶数题
            if (it % 2 == 0) s.submit(q.correctAnswer)
            else s.submit(q.options.first { o -> o != q.correctAnswer })
            s.next()
        }
        assertEquals(20, s.answeredCount)
        assertEquals(10, s.correctCount)
        assertEquals(0.5, s.accuracy, 0.001)
    }
}
