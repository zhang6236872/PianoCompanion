package com.pianocompanion.musicalterms

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

/**
 * 音乐术语训练会话（MusicalTermsSession）单元测试。
 *
 * 验证会话状态机的完整生命周期：start → submit → next → end。
 */
class MusicalTermsSessionTest {

    private fun createSession(
        difficulty: TermDifficulty = TermDifficulty.BEGINNER,
        category: TermCategory? = null,
        direction: QuizDirection? = null
    ): MusicalTermsSession {
        val engine = MusicalTermsEngine(Random(42L))
        val session = MusicalTermsSession(engine, difficulty, category, direction)
        session.start()
        return session
    }

    @Test
    fun `start 后生成第一题`() {
        val session = createSession()
        assertNotNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `submit 正确答案`() {
        val session = createSession()
        val question = session.currentQuestion!!
        val record = session.submit(question.correctAnswer)!!
        assertTrue(record.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.currentStreak)
        assertEquals(1, session.bestStreak)
        assertTrue(session.isAnswered)
    }

    @Test
    fun `submit 错误答案`() {
        val session = createSession()
        val question = session.currentQuestion!!
        val wrongAnswer = question.answerChoices.first { it != question.correctAnswer }
        val record = session.submit(wrongAnswer)!!
        assertFalse(record.isCorrect)
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `连续答对 streak 递增`() {
        val session = createSession()
        for (i in 1..5) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            assertEquals(i, session.currentStreak)
            session.next()
        }
        assertEquals(5, session.bestStreak)
    }

    @Test
    fun `答错后 streak 归零`() {
        val session = createSession()
        // 先答对 3 题
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        // 答错一题
        val q = session.currentQuestion!!
        val wrong = q.answerChoices.first { it != q.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `答对-答错-再答对 streak 正确`() {
        val session = createSession()
        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
        session.next()
        // 答错
        val q2 = session.currentQuestion!!
        session.submit(q2.answerChoices.first { it != q2.correctAnswer })
        assertEquals(0, session.currentStreak)
        session.next()
        // 再答对
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `next 生成新题目`() {
        val session = createSession()
        val q1 = session.currentQuestion!!
        session.submit(q1.correctAnswer)
        session.next()
        val q2 = session.currentQuestion!!
        assertNotNull(q2)
        assertFalse(session.isAnswered)
    }

    @Test
    fun `submit 已作答返回 null`() {
        val session = createSession()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        val second = session.submit(q.correctAnswer)
        assertNull(second)
    }

    @Test
    fun `next 未开始返回 null`() {
        val engine = MusicalTermsEngine(Random(42L))
        val session = MusicalTermsSession(engine, TermDifficulty.BEGINNER)
        assertNull(session.next())
    }

    @Test
    fun `submit 未开始返回 null`() {
        val engine = MusicalTermsEngine(Random(42L))
        val session = MusicalTermsSession(engine, TermDifficulty.BEGINNER)
        assertNull(session.submit("test"))
    }

    @Test
    fun `reset 清空所有统计`() {
        val session = createSession()
        val q = session.currentQuestion!!
        session.submit(q.correctAnswer)
        session.next()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(2, session.answeredCount)
        session.reset()
        assertNull(session.currentQuestion)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertFalse(session.isStarted)
    }

    @Test
    fun `accuracy 计算`() {
        val session = createSession()
        assertEquals(0.0, session.accuracy, 0.001)

        // 答对
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        assertEquals(1.0, session.accuracy, 0.001)

        // 答错
        val q = session.currentQuestion!!
        session.submit(q.answerChoices.first { it != q.correctAnswer })
        session.next()
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `history 按时间顺序`() {
        val session = createSession()
        repeat(3) {
            val q = session.currentQuestion!!
            session.submit(q.correctAnswer)
            session.next()
        }
        assertEquals(3, session.history.size)
        assertTrue(session.history.all { it.isCorrect })
    }

    @Test
    fun `lastAnswer 更新`() {
        val session = createSession()
        assertNull(session.lastAnswer) // 未答题前 null → 但 start 后 lastAnswer 已为 null
        session.submit(session.currentQuestion!!.correctAnswer)
        assertNotNull(session.lastAnswer)
        assertTrue(session.lastAnswer!!.isCorrect)
    }

    @Test
    fun `配置参数返回正确`() {
        val session = MusicalTermsSession(
            MusicalTermsEngine(Random(42L)),
            TermDifficulty.ADVANCED,
            TermCategory.DYNAMICS,
            QuizDirection.MEANING_TO_TERM
        )
        assertEquals(TermDifficulty.ADVANCED, session.difficulty())
        assertEquals(TermCategory.DYNAMICS, session.category())
        assertEquals(QuizDirection.MEANING_TO_TERM, session.direction())
    }

    @Test
    fun `默认配置参数`() {
        val session = MusicalTermsSession(
            MusicalTermsEngine(Random(42L)),
            TermDifficulty.BEGINNER
        )
        assertEquals(TermDifficulty.BEGINNER, session.difficulty())
        assertNull(session.category())
        assertNull(session.direction())
    }

    @Test
    fun `指定方向出题`() {
        val session = MusicalTermsSession(
            MusicalTermsEngine(Random(42L)),
            TermDifficulty.BEGINNER,
            null,
            QuizDirection.TERM_TO_MEANING
        )
        session.start()
        assertEquals(QuizDirection.TERM_TO_MEANING, session.currentQuestion!!.direction)
    }

    @Test
    fun `指定类别出题`() {
        val session = MusicalTermsSession(
            MusicalTermsEngine(Random(42L)),
            TermDifficulty.ADVANCED,
            TermCategory.EXPRESSION
        )
        session.start()
        assertEquals(TermCategory.EXPRESSION, session.currentQuestion!!.term.category)
    }

    @Test
    fun `10 题会话统计正确`() {
        val session = createSession(TermDifficulty.ADVANCED)
        var correct = 0
        for (i in 0 until 10) {
            val q = session.currentQuestion!!
            // 交替答对答错
            if (i % 2 == 0) {
                session.submit(q.correctAnswer)
                correct++
            } else {
                session.submit(q.answerChoices.first { it != q.correctAnswer })
            }
            session.next()
        }
        assertEquals(10, session.answeredCount)
        assertEquals(correct, session.correctCount)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    @Test
    fun `isStarted 状态`() {
        val engine = MusicalTermsEngine(Random(42L))
        val session = MusicalTermsSession(engine, TermDifficulty.BEGINNER)
        assertFalse(session.isStarted)
        session.start()
        assertTrue(session.isStarted)
    }

    @Test
    fun `correctAnswerText 答对时为 null`() {
        val record = TermAnswerRecord(
            question = MusicalTermsEngine(Random(42L)).generate(),
            userAnswer = "test",
            isCorrect = true
        )
        assertNull(record.correctAnswerText)
    }

    @Test
    fun `correctAnswerText 答错时返回正确答案`() {
        val q = MusicalTermsEngine(Random(42L)).generate()
        val record = TermAnswerRecord(question = q, userAnswer = "wrong", isCorrect = false)
        assertEquals(q.correctAnswer, record.correctAnswerText)
    }
}
