package com.pianocompanion.cadencetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终止式听辨训练会话状态机单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：start/submit/next/reset 生命周期、连击追踪、准确率计算、答题历史、边界安全。
 */
class CadenceTrainingSessionTest {

    private fun newSession(difficulty: CadenceDifficulty = CadenceDifficulty.BEGINNER): CadenceTrainingSession {
        return CadenceTrainingSession(CadenceTrainingEngine.withSeed(42L), difficulty)
    }

    // ── start ─────────────────────────────────────────────

    @Test
    fun `start sets current question`() {
        val s = newSession()
        assertFalse(s.isStarted)
        s.start()
        assertNotNull(s.currentQuestion)
        assertTrue(s.isStarted)
    }

    @Test
    fun `start resets counters`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.start()
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `start sets isAnswered false`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.start()
        assertFalse(s.isAnswered)
    }

    // ── submit ────────────────────────────────────────────

    @Test
    fun `submit correct answer increments correctCount and streak`() {
        val s = newSession()
        s.start()
        val record = s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `submit wrong answer does not increment correctCount and resets streak`() {
        val s = newSession(CadenceDifficulty.INTERMEDIATE)
        s.start()
        // 先答对一题建立连击
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.currentStreak)
        s.next()
        // 找一个错误答案
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        val record = s.submit(wrong)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(0, s.currentStreak)
        assertEquals(1, s.correctCount)
        assertEquals(2, s.answeredCount)
    }

    @Test
    fun `submit returns null when session not started`() {
        val s = newSession()
        assertNull(s.submit("完全正格终止"))
    }

    @Test
    fun `submit twice returns null second time`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val second = s.submit(s.currentQuestion!!.correctAnswer)
        assertNull(second)
        assertEquals(1, s.answeredCount)
    }

    @Test
    fun `submit records answer in history`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(1, s.history.size)
        assertEquals(s.currentQuestion!!.correctAnswer, s.history[0].userAnswer)
    }

    @Test
    fun `submit sets lastAnswer`() {
        val s = newSession()
        s.start()
        assertNull(s.lastAnswer)
        val record = s.submit(s.currentQuestion!!.correctAnswer)
        assertEquals(record, s.lastAnswer)
    }

    // ── 连击追踪 ──────────────────────────────────────────

    @Test
    fun `bestStreak tracks maximum consecutive correct`() {
        val s = newSession()
        s.start()
        // 答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    @Test
    fun `bestStreak is not reduced after a wrong answer`() {
        val s = newSession()
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.bestStreak)
        // 答错
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)
    }

    // ── next ──────────────────────────────────────────────

    @Test
    fun `next generates a new question`() {
        val s = newSession()
        s.start()
        val first = s.currentQuestion
        s.submit(first!!.correctAnswer)
        s.next()
        assertNotNull(s.currentQuestion)
        // 新题目生成（不保证不同，但状态正确）
        assertFalse(s.isAnswered)
    }

    @Test
    fun `next returns null when session not started`() {
        val s = newSession()
        assertNull(s.next())
    }

    @Test
    fun `next resets isAnswered`() {
        val s = newSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
    }

    // ── 准确率 ────────────────────────────────────────────

    @Test
    fun `accuracy is zero when no answers`() {
        val s = newSession()
        assertEquals(0.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 1 dot 0 when all correct`() {
        val s = newSession()
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(1.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy reflects mixed results`() {
        val s = newSession(CadenceDifficulty.INTERMEDIATE)
        s.start()
        // 答对
        s.submit(s.currentQuestion!!.correctAnswer)
        s.next()
        // 答错
        val wrong = s.currentQuestion!!.answerChoices.first { it != s.currentQuestion!!.correctAnswer }
        s.submit(wrong)
        assertEquals(0.5, s.accuracy, 0.001)
    }

    // ── reset ─────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        val s = newSession()
        s.start()
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        s.reset()
        assertNull(s.currentQuestion)
        assertFalse(s.isStarted)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertTrue(s.history.isEmpty())
        assertFalse(s.isAnswered)
        assertNull(s.lastAnswer)
    }

    // ── difficulty ────────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        CadenceDifficulty.ALL.forEach { d ->
            val s = newSession(d)
            assertEquals(d, s.difficulty())
        }
    }

    // ── 完整生命周期 ──────────────────────────────────────

    @Test
    fun `full lifecycle across all difficulties`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val s = newSession(difficulty)
            s.start()
            assertNotNull(s.currentQuestion)
            repeat(5) {
                assertTrue("会话应处于活跃状态", s.isStarted)
                s.submit(s.currentQuestion!!.correctAnswer)
                assertTrue(s.isAnswered)
                s.next()
                assertFalse(s.isAnswered)
            }
            assertEquals(5, s.answeredCount)
            assertEquals(5, s.correctCount)
            assertEquals(5, s.bestStreak)
        }
    }

    @Test
    fun `history preserves order`() {
        val s = newSession(CadenceDifficulty.INTERMEDIATE)
        s.start()
        val answers = mutableListOf<CadenceAnswerRecord>()
        repeat(3) {
            val correct = s.currentQuestion!!.correctAnswer
            val record = s.submit(correct)!!
            answers.add(record)
            s.next()
        }
        assertEquals(answers, s.history)
    }
}
