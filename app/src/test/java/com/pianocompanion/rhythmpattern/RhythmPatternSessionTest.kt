package com.pianocompanion.rhythmpattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmPatternSession] 单元测试。
 *
 * 验证会话状态机：
 * - start/submit/next/reset 完整生命周期
 * - 正确/错误判定
 * - 连击递增/归零
 * - 最长连击追踪
 * - 边界安全（未开始提交/重复提交返回 null）
 * - accuracy 计算
 * - history 记录顺序
 */
class RhythmPatternSessionTest {

    private fun createSession(
        difficulty: RhythmDifficulty = RhythmDifficulty.BEGINNER,
        tempo: RhythmTempo = RhythmTempo.SLOW
    ): RhythmPatternSession {
        return RhythmPatternSession(
            RhythmPatternEngine.withSeed(42L),
            difficulty,
            tempo
        )
    }

    @Test
    fun `start generates first question`() {
        val session = createSession()
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `submit correct answer returns record`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
        assertEquals(question.correctAnswer, record.userAnswer)
    }

    @Test
    fun `submit wrong answer returns record`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        // 找一个错误答案
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
        assertEquals(wrongAnswer, record.userAnswer)
    }

    @Test
    fun `submit before start returns null`() {
        val session = createSession()
        val record = session.submit("四分音符")
        assertNull(record)
    }

    @Test
    fun `double submit returns null`() {
        val session = createSession()
        session.start()
        val question = session.currentQuestion!!
        session.submit(question.correctAnswer)
        val second = session.submit(question.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `streak increments on correct`() {
        val session = createSession()
        session.start()
        assertEquals(0, session.currentStreak)

        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)

        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.currentStreak)
    }

    @Test
    fun `streak resets on wrong`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)

        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `bestStreak tracks maximum`() {
        val session = createSession()
        session.start()
        // 答对 3 题
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)

        // 答错
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        // bestStreak 不降
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `answeredCount and correctCount`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer) // 1 correct
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong) // 1 wrong

        assertEquals(2, session.answeredCount)
        assertEquals(1, session.correctCount)
    }

    @Test
    fun `accuracy calculation`() {
        val session = createSession()
        session.start()
        // 答对 3，答错 1 → 0.75
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)

        assertEquals(0.75, session.accuracy, 0.001)
    }

    @Test
    fun `accuracy is zero before any answer`() {
        val session = createSession()
        session.start()
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `history is in order`() {
        val session = createSession()
        session.start()
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            if (it < 2) session.next()
        }
        val history = session.history
        assertEquals(3, history.size)
        // 每条记录都正确
        history.forEach { assertTrue(it.isCorrect) }
    }

    @Test
    fun `isAnswered flag`() {
        val session = createSession()
        session.start()
        assertFalse(session.isAnswered)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `next before start returns null`() {
        val session = createSession()
        assertNull(session.next())
    }

    @Test
    fun `reset clears everything`() {
        val session = createSession()
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)

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
    fun `difficulty and tempo accessors`() {
        val session = RhythmPatternSession(
            RhythmPatternEngine(),
            RhythmDifficulty.INTERMEDIATE,
            RhythmTempo.FAST
        )
        assertEquals(RhythmDifficulty.INTERMEDIATE, session.difficulty())
        assertEquals(RhythmTempo.FAST, session.tempo())
    }

    @Test
    fun `repeatCount accessor`() {
        val session = RhythmPatternSession(
            RhythmPatternEngine(),
            RhythmDifficulty.BEGINNER,
            RhythmTempo.SLOW,
            repeatCount = 3
        )
        assertEquals(3, session.repeatCount())
    }

    @Test
    fun `full lifecycle beginner`() {
        val session = createSession(RhythmDifficulty.BEGINNER)
        session.start()
        repeat(10) {
            assertNotNull(session.currentQuestion)
            session.submit(session.currentQuestion!!.correctAnswer)
            assertTrue(session.isAnswered)
            if (it < 9) {
                session.next()
                assertFalse(session.isAnswered)
            }
        }
        assertEquals(10, session.answeredCount)
        assertEquals(10, session.correctCount)
        assertEquals(10, session.bestStreak)
    }

    @Test
    fun `full lifecycle intermediate`() {
        val session = createSession(RhythmDifficulty.INTERMEDIATE)
        session.start()
        repeat(10) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(10, session.answeredCount)
    }

    @Test
    fun `full lifecycle advanced with errors`() {
        val session = createSession(RhythmDifficulty.ADVANCED)
        session.start()
        var correct = 0
        repeat(20) {
            val q = session.currentQuestion!!
            // 随机答对或答错
            if (it % 3 == 0) {
                val wrong = q.answerChoices.first { it != q.correctAnswer }
                session.submit(wrong)
            } else {
                session.submit(q.correctAnswer)
                correct++
            }
            session.next()
        }
        assertEquals(20, session.answeredCount)
        assertEquals(correct, session.correctCount)
    }

    @Test
    fun `lastAnswer is set after submit`() {
        val session = createSession()
        session.start()
        assertNull(session.lastAnswer)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }
}
