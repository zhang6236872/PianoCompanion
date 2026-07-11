package com.pianocompanion.eleventhchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十一和弦色彩听辨训练会话状态机单元测试。
 *
 * 验证会话生命周期、连击追踪、准确率计算、答题历史等。
 */
class EleventhChordTrainingSessionTest {

    private fun createSession(difficulty: EleventhChordDifficulty = EleventhChordDifficulty.ADVANCED): EleventhChordTrainingSession {
        val engine = EleventhChordTrainingEngine.withSeed(42L)
        return EleventhChordTrainingSession(engine, difficulty)
    }

    // ── 会话生命周期 ──────────────────────────────────────────

    @Test
    fun `session starts with no question`() {
        val session = createSession()
        assertNull("未开始的会话不应有当前题目", session.currentQuestion)
        assertFalse("未开始的会话不应标记为已开始", session.isStarted)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        session.start()
        assertNotNull("开始后应有当前题目", session.currentQuestion)
        assertTrue(session.isStarted)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next without start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `submit without start returns null`() {
        val session = createSession()
        assertNull(session.submit("大十一和弦"))
    }

    @Test
    fun `double submit returns null on second`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val first = session.submit(q.correctAnswer)
        assertNotNull("首次答题应返回记录", first)
        val second = session.submit(q.correctAnswer)
        assertNull("重复答题应返回 null", second)
    }

    // ── 答题正确性 ──────────────────────────────────────────────

    @Test
    fun `correct answer increments correct count`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val record = session.submit(q.correctAnswer)
        assertNotNull(record)
        assertTrue("应答对", record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `wrong answer does not increment correct count`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse("应答错", record!!.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(q.correctAnswer, record.correctAnswer)
    }

    // ── 连击追踪 ──────────────────────────────────────────────

    @Test
    fun `streak increments on correct`() {
        val session = createSession()
        session.start()
        // Answer 3 correctly
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `streak resets on wrong answer`() {
        val session = createSession()
        session.start()
        // 2 correct
        repeat(2) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(2, session.currentStreak)
        // 1 wrong
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0, session.currentStreak)
        assertEquals(2, session.bestStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val session = createSession()
        session.start()
        // Build a streak of 5
        repeat(5) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(5, session.bestStreak)
        // Break the streak
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(5, session.bestStreak)
        // Build a shorter streak
        repeat(2) {
            val q2 = session.currentQuestion!!
            session.submit(q2.correctAnswer)
            session.next()
        }
        assertEquals(5, session.bestStreak)
    }

    // ── 准确率 ──────────────────────────────────────────────

    @Test
    fun `accuracy is zero when no answers`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy calculation is correct`() {
        val session = createSession()
        session.start()
        // 3 correct + 1 wrong = 75%
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        val q = session.currentQuestion!!
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrongAnswer)
        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `100 percent accuracy`() {
        val session = createSession()
        session.start()
        repeat(5) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(1.0, session.accuracy, 0.001)
    }

    // ── 答题历史 ──────────────────────────────────────────────

    @Test
    fun `history preserves order`() {
        val session = createSession()
        session.start()
        val answers = mutableListOf<String>()
        repeat(3) {
            val q = session.currentQuestion!!
            val answer = q.answerChoices.first { it != q.correctAnswer }
            answers.add(answer)
            session.submit(answer)
            session.next()
        }
        assertEquals(3, session.history.size)
        for (i in answers.indices) {
            assertEquals(answers[i], session.history[i].userAnswer)
        }
    }

    @Test
    fun `history records correct flag`() {
        val session = createSession()
        session.start()
        // 1 correct
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        assertTrue(session.history[0].isCorrect)
        session.next()
        // 1 wrong
        val q2 = session.currentQuestion!!
        val wrongAnswer = q2.answerChoices.first { it != q2.correctAnswer }
        session.submit(wrongAnswer)
        assertFalse(session.history[1].isCorrect)
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val session = createSession()
        session.start()
        assertNull("初始 lastAnswer 应为 null", session.lastAnswer)
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertNotNull("提交后 lastAnswer 不应为 null", session.lastAnswer)
    }

    @Test
    fun `lastAnswer remains set after next`() {
        val session = createSession()
        session.start()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        assertNotNull(session.lastAnswer)
        session.next()
        // Session preserves lastAnswer; ViewModel clears its UI state separately
        assertNotNull(session.lastAnswer)
    }

    // ── reset ──────────────────────────────────────────────

    @Test
    fun `reset clears all stats`() {
        val session = createSession()
        session.start()
        repeat(5) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertEquals(0, session.history.size)
        assertFalse(session.isStarted)
    }

    @Test
    fun `reset preserves engine configuration`() {
        val session = createSession(EleventhChordDifficulty.ADVANCED)
        session.reset()
        assertEquals(EleventhChordDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `difficulty returns configured difficulty`() {
        for (d in EleventhChordDifficulty.ALL) {
            val session = createSession(d)
            assertEquals(d, session.difficulty())
        }
    }

    // ── 边界安全 ──────────────────────────────────────────────

    @Test
    fun `many questions generated without error`() {
        val session = createSession()
        session.start()
        repeat(50) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(50, session.answeredCount)
        assertEquals(50, session.correctCount)
    }
}
