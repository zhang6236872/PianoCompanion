package com.pianocompanion.tempochangetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 速度变化方向辨识训练会话状态机单元测试。
 */
class TempoChangeSessionTest {

    private fun makeSession(difficulty: TempoChangeDifficulty): TempoChangeSession {
        return TempoChangeSession(TempoChangeEngine.withSeed(42), difficulty)
    }

    // ── 生命周期 ───────────────────────────────────────────

    @Test
    fun `fresh session is not started`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        assertTrue("New session should not be started", !session.isStarted)
        assertEquals(null, session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `start sets current question`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        assertTrue("After start, session should be started", session.isStarted)
        assertTrue("Current question should be set", session.currentQuestion != null)
        assertEquals(0, session.answeredCount)
    }

    @Test
    fun `start resets stats`() {
        val session = makeSession(TempoChangeDifficulty.INTERMEDIATE)
        session.start()
        // 提交一题
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.answeredCount)
        // 再次 start，应重置
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    // ── 提交答案 ───────────────────────────────────────────

    @Test
    fun `submit correct answer increments counts`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)
        assertEquals(true, record?.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer increments answered but not correct`() {
        val session = makeSession(TempoChangeDifficulty.ADVANCED)
        session.start()
        val q = session.currentQuestion!!
        // 找一个错误答案
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertEquals(false, record?.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `submit returns null when no question`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        val record = session.submit("anything")
        assertEquals(null, record)
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        // 再次提交应被拒绝
        val record2 = session.submit(q.correctAnswer)
        assertEquals(null, record2)
    }

    // ── 连击 ─────────────────────────────────────────────

    @Test
    fun `streak resets on wrong answer`() {
        val session = makeSession(TempoChangeDifficulty.ADVANCED)
        session.start()
        // 连续答对
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
        // 答错
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `best streak tracks maximum`() {
        val session = makeSession(TempoChangeDifficulty.ADVANCED)
        session.start()
        // 答对 2
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(2, session.bestStreak)
        // 答错
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(2, session.bestStreak)
        // 再答对 1
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.bestStreak) // 仍然是 2，没超过
    }

    // ── next ─────────────────────────────────────────────

    @Test
    fun `next generates new question and clears answered flag`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertTrue(!session.isAnswered)
        assertTrue(session.currentQuestion != null)
    }

    @Test
    fun `next returns null when not started`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        assertEquals(null, session.next())
    }

    // ── reset ───────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        val session = makeSession(TempoChangeDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.reset()
        assertTrue(!session.isStarted)
        assertEquals(null, session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
    }

    // ── accuracy ────────────────────────────────────────

    @Test
    fun `accuracy is zero when no answers`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        assertEquals(0.0, session.accuracy, 0.0001)
    }

    @Test
    fun `accuracy is half when one correct one wrong`() {
        val session = makeSession(TempoChangeDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(0.5, session.accuracy, 0.0001)
    }

    // ── history ─────────────────────────────────────────

    @Test
    fun `history accumulates records`() {
        val session = makeSession(TempoChangeDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        assertEquals(2, session.history.size)
        assertEquals(true, session.history[0].isCorrect)
        assertEquals(false, session.history[1].isCorrect)
    }

    @Test
    fun `history returns defensive copy`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val h1 = session.history
        val h2 = session.history
        // 不是同一引用
        assertTrue(h1 !== h2)
        // 修改副本不影响内部
        (h1 as MutableList).clear()
        assertEquals(1, session.history.size)
    }

    // ── difficulty ──────────────────────────────────────

    @Test
    fun `difficulty returns configured difficulty`() {
        TempoChangeDifficulty.ALL.forEach { d ->
            val session = TempoChangeSession(TempoChangeEngine.withSeed(1), d)
            assertEquals(d, session.difficulty())
        }
    }

    // ── lastAnswer ──────────────────────────────────────

    @Test
    fun `lastAnswer updates on submit`() {
        val session = makeSession(TempoChangeDifficulty.BEGINNER)
        session.start()
        assertEquals(null, session.lastAnswer)
        val rec = session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(rec, session.lastAnswer)
    }
}
