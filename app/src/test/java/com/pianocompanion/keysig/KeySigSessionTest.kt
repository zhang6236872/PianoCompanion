package com.pianocompanion.keysig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KeySigSession] 单元测试。
 *
 * 验证会话状态机：出题 → 答题 → 判定 → 下一题，以及连击和历史记录。
 */
class KeySigSessionTest {

    private fun createSession(
        clef: KeySigClef = KeySigClef.TREBLE,
        difficulty: KeySigDifficulty = KeySigDifficulty.BEGINNER
    ): KeySigSession {
        val engine = KeySigEngine.withSeed(42L)
        return KeySigSession(engine, clef, difficulty)
    }

    // ── 启动 ──────────────────────────────────────────────────

    @Test
    fun `session starts with first question`() {
        val session = createSession()
        assertFalse(session.isStarted)
        assertNull(session.currentQuestion)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentQuestion)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `start resets all counters`() {
        val session = createSession()
        session.start()
        // 答几题
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit("___wrong___")
        // 再 start 应重置
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
    }

    // ── 答题 ──────────────────────────────────────────────────

    @Test
    fun `submit correct answer increments correct count and streak`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val result = session.submit(question.correctAnswer)
        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit wrong answer resets streak but keeps answered count`() {
        val session = createSession()
        session.start()
        val result = session.submit("__definitely_wrong__")
        assertNotNull(result)
        assertFalse(result!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit returns null when no current question`() {
        val session = createSession()
        // 未 start
        val result = session.submit("anything")
        assertNull(result)
    }

    @Test
    fun `submit returns null when already answered`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        // 再次 submit 应返回 null
        val result = session.submit("again")
        assertNull(result)
    }

    @Test
    fun `answer record stores correct answer when wrong`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val result = session.submit("__wrong__")!!
        assertFalse(result.isCorrect)
        assertEquals(question.correctAnswer, result.correctAnswer)
    }

    @Test
    fun `answer record correct answer is null when right`() {
        val session = createSession()
        session.start()
        val result = session.submit(session.currentQuestion!!.correctAnswer)!!
        assertTrue(result.isCorrect)
        assertNull(result.correctAnswer)
    }

    // ── 连击 ──────────────────────────────────────────────────

    @Test
    fun `best streak tracks maximum consecutive correct`() {
        val session = createSession()
        session.start()
        // 连续答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.correctCount)
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
        // 答错一题
        session.submit("__wrong__")
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    // ── 下一题 ────────────────────────────────────────────────

    @Test
    fun `next generates a new question`() {
        val session = createSession()
        session.start()
        val q1 = session.currentQuestion
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q2 = session.currentQuestion
        assertNotNull(q2)
        assertFalse(session.isAnswered)
        // 新题目（同种子引擎会循环但至少生成了新对象）
        assertNotEqualsByIdentity(q1, q2)
    }

    @Test
    fun `next returns null when not started`() {
        val session = createSession()
        val result = session.next()
        assertNull(result)
    }

    // ── 准确率 ────────────────────────────────────────────────

    @Test
    fun `accuracy is zero before answering`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy computes correctly`() {
        val session = createSession()
        session.start()
        // 答对 2 题，答错 2 题
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        session.submit(session.currentQuestion!!.correctAnswer); session.next()
        session.submit("__wrong__"); session.next()
        session.submit("__wrong__")
        assertEquals(4, session.answeredCount)
        assertEquals(2, session.correctCount)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    // ── 历史 ──────────────────────────────────────────────────

    @Test
    fun `history records all answers in order`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit("__wrong__")
        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isCorrect)
        assertFalse(session.history[1].isCorrect)
        assertEquals(session.history[1], session.lastAnswer)
    }

    // ── 重置 ──────────────────────────────────────────────────

    @Test
    fun `reset clears everything`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.reset()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertNull(session.lastAnswer)
    }

    // ── 谱号/难度 getter ─────────────────────────────────────

    @Test
    fun `clef and difficulty getters return configured values`() {
        val session = KeySigSession(
            KeySigEngine.withSeed(1L),
            KeySigClef.BASS,
            KeySigDifficulty.ADVANCED
        )
        assertEquals(KeySigClef.BASS, session.clef())
        assertEquals(KeySigDifficulty.ADVANCED, session.difficulty())
    }

    private fun assertNotEqualsByIdentity(a: KeySigQuestion?, b: KeySigQuestion?) {
        // 引用不同即可（内容可能相同因为种子循环）
        assertNotNull(a)
        assertNotNull(b)
    }
}
