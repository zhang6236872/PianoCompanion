package com.pianocompanion.thirteenthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十三和弦色彩听辨训练会话状态机单元测试。
 *
 * 验证生命周期、连击追踪、准确率计算、答题历史、边界安全、reset 等。
 */
class ThirteenthChordTrainingSessionTest {

    private fun createSession(difficulty: ThirteenthChordDifficulty = ThirteenthChordDifficulty.BEGINNER): ThirteenthChordTrainingSession {
        return ThirteenthChordTrainingSession(
            ThirteenthChordTrainingEngine.withSeed(42L),
            difficulty
        )
    }

    // ── 生命周期 ──────────────────────────────────────────────

    @Test
    fun `session starts with no question`() {
        val s = createSession()
        assertNull("start() 之前 currentQuestion 应为 null", s.currentQuestion)
        assertFalse("start() 之前 isStarted 应为 false", s.isStarted)
    }

    @Test
    fun `start generates first question`() {
        val s = createSession()
        s.start()
        assertNotNull("start() 后 currentQuestion 不应为 null", s.currentQuestion)
        assertTrue("start() 后 isStarted 应为 true", s.isStarted)
    }

    @Test
    fun `start resets counters`() {
        val s = createSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        s.start() // restart
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.history.size)
    }

    // ── 答题 ──────────────────────────────────────────────────

    @Test
    fun `submit correct answer`() {
        val s = createSession()
        s.start()
        val record = s.submit(s.currentQuestion!!.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `submit wrong answer`() {
        val s = createSession()
        s.start()
        val correct = s.currentQuestion!!.correctAnswer
        val wrongAnswer = s.currentQuestion!!.answerChoices.first { it != correct }
        val record = s.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `submit returns null when no question`() {
        val s = createSession()
        val record = s.submit("大十三和弦")
        assertNull(record)
    }

    @Test
    fun `submit returns null when already answered`() {
        val s = createSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        val record2 = s.submit(s.currentQuestion!!.correctAnswer)
        assertNull("已答题后 submit 应返回 null", record2)
    }

    @Test
    fun `submit sets isAnswered`() {
        val s = createSession()
        s.start()
        assertFalse(s.isAnswered)
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
    }

    // ── 连击追踪 ──────────────────────────────────────────────

    @Test
    fun `correct streak increments`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        for (i in 1..5) {
            s.submit(s.currentQuestion!!.correctAnswer)
            assertEquals("第 $i 题连击应为 $i", i, s.currentStreak)
            s.next()
        }
    }

    @Test
    fun `wrong answer resets streak`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        // 答对 3 题
        repeat(3) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(3, s.currentStreak)
        // 答错第 4 题
        val correct = s.currentQuestion!!.correctAnswer
        val wrongAnswer = s.currentQuestion!!.answerChoices.first { it != correct }
        s.submit(wrongAnswer)
        assertEquals(0, s.currentStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        // 连对 5 题
        repeat(5) {
            s.submit(s.currentQuestion!!.correctAnswer)
            s.next()
        }
        assertEquals(5, s.bestStreak)
        // 答错
        val correct = s.currentQuestion!!.correctAnswer
        val wrongAnswer = s.currentQuestion!!.answerChoices.first { it != correct }
        s.submit(wrongAnswer)
        assertEquals("bestStreak 不应下降", 5, s.bestStreak)
    }

    @Test
    fun `best streak equals longest consecutive correct`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        // 连对 3 题
        repeat(3) { s.submit(s.currentQuestion!!.correctAnswer); s.next() }
        // 连对 2 题（中断后重新连对）
        val correct = s.currentQuestion!!.correctAnswer
        val wrongAnswer = s.currentQuestion!!.answerChoices.first { it != correct }
        s.submit(wrongAnswer); s.next()
        repeat(2) { s.submit(s.currentQuestion!!.correctAnswer); s.next() }
        assertEquals(3, s.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────────────

    @Test
    fun `accuracy is 0 before answering`() {
        val s = createSession()
        s.start()
        assertEquals(0.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy is 1 after all correct`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        repeat(3) { s.submit(s.currentQuestion!!.correctAnswer); s.next() }
        assertEquals(1.0, s.accuracy, 0.001)
    }

    @Test
    fun `accuracy is correct after mixed answers`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        // 2 correct
        s.submit(s.currentQuestion!!.correctAnswer); s.next()
        s.submit(s.currentQuestion!!.correctAnswer); s.next()
        // 1 wrong
        val correct = s.currentQuestion!!.correctAnswer
        val wrongAnswer = s.currentQuestion!!.answerChoices.first { it != correct }
        s.submit(wrongAnswer)
        assertEquals(3, s.answeredCount)
        assertEquals(2, s.correctCount)
        assertEquals(2.0 / 3.0, s.accuracy, 0.001)
    }

    // ── 答题历史 ──────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        val s = createSession(ThirteenthChordDifficulty.ADVANCED)
        s.start()
        val answers = mutableListOf<ThirteenthChordAnswerRecord>()
        repeat(3) {
            val record = s.submit(s.currentQuestion!!.correctAnswer)!!
            answers.add(record)
            s.next()
        }
        assertEquals(3, s.history.size)
        for (i in answers.indices) {
            assertEquals(answers[i], s.history[i])
        }
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val s = createSession()
        s.start()
        assertNull(s.lastAnswer)
        val record = s.submit(s.currentQuestion!!.correctAnswer)!!
        assertEquals(record, s.lastAnswer)
    }

    // ── next ──────────────────────────────────────────────────

    @Test
    fun `next generates new question`() {
        val s = createSession()
        s.start()
        val q1 = s.currentQuestion
        s.next()
        val q2 = s.currentQuestion
        assertNotNull(q2)
        // 新题目（可能品质/根音相同，但对象不同）
        assertNotNull(q1)
    }

    @Test
    fun `next clears isAnswered`() {
        val s = createSession()
        s.start()
        s.submit(s.currentQuestion!!.correctAnswer)
        assertTrue(s.isAnswered)
        s.next()
        assertFalse(s.isAnswered)
    }

    @Test
    fun `next returns null when not started`() {
        val s = createSession()
        assertNull(s.next())
    }

    // ── reset ──────────────────────────────────────────────────

    @Test
    fun `reset clears everything`() {
        val s = createSession()
        s.start()
        repeat(3) { s.submit(s.currentQuestion!!.correctAnswer); s.next() }
        s.reset()
        assertNull(s.currentQuestion)
        assertEquals(0, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.history.size)
        assertFalse(s.isAnswered)
        assertNull(s.lastAnswer)
    }

    // ── difficulty ──────────────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        val s = ThirteenthChordTrainingSession(
            ThirteenthChordTrainingEngine.withSeed(1L),
            ThirteenthChordDifficulty.ADVANCED
        )
        assertEquals(ThirteenthChordDifficulty.ADVANCED, s.difficulty())
    }

    @Test
    fun `beginner questions have beginner difficulty`() {
        val s = ThirteenthChordTrainingSession(
            ThirteenthChordTrainingEngine.withSeed(1L),
            ThirteenthChordDifficulty.BEGINNER
        )
        s.start()
        assertEquals(ThirteenthChordDifficulty.BEGINNER, s.currentQuestion!!.difficulty)
    }

    // ── answer record ──────────────────────────────────────────

    @Test
    fun `answer record correct has null correctAnswer`() {
        val s = createSession()
        s.start()
        val record = s.submit(s.currentQuestion!!.correctAnswer)!!
        assertNull(record.correctAnswer)
    }

    @Test
    fun `answer record wrong has correct answer`() {
        val s = createSession()
        s.start()
        val correct = s.currentQuestion!!.correctAnswer
        val wrongAnswer = s.currentQuestion!!.answerChoices.first { it != correct }
        val record = s.submit(wrongAnswer)!!
        assertEquals(correct, record.correctAnswer)
    }
}
