package com.pianocompanion.articulationtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 演奏法辨识训练会话状态机单元测试。
 */
class ArticulationSessionTest {

    private fun createSession(difficulty: ArticulationTrainingDifficulty): ArticulationTrainingSession {
        val engine = ArticulationTrainingEngine.withSeed(42)
        return ArticulationTrainingSession(engine, difficulty)
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `未开始的会话没有当前题目`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        assertNull(session.currentQuestion)
        assertFalse(session.isStarted)
    }

    @Test
    fun `start后生成第一题`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        assertNotNull(session.currentQuestion)
        assertTrue(session.isStarted)
    }

    @Test
    fun `start后统计清零`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
    }

    @Test
    fun `start后isAnswered为false`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        assertFalse(session.isAnswered)
    }

    // ── 答题 ──────────────────────────────────────────

    @Test
    fun `提交正确答案返回isCorrect=true`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        val correctAnswer = session.currentQuestion!!.correctAnswer
        val record = session.submit(correctAnswer)
        assertNotNull(record)
        assertTrue(record!!.isCorrect)
    }

    @Test
    fun `提交错误答案返回isCorrect=false`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        val wrongAnswer = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrongAnswer)
        assertNotNull(record)
        assertFalse(record!!.isCorrect)
    }

    @Test
    fun `提交正确答案后correctCount递增`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertEquals(1, session.correctCount)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `提交错误答案后correctCount不变`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.correctCount)
        assertEquals(1, session.answeredCount)
    }

    @Test
    fun `已作答后再次提交返回null`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        val record = session.submit(session.currentQuestion!!.correctAnswer)
        assertNull(record)
    }

    @Test
    fun `未开始时submit返回null`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        assertNull(session.submit("anything"))
    }

    @Test
    fun `提交后isAnswered变为true`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        assertFalse(session.isAnswered)
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
    }

    // ── 连击 ──────────────────────────────────────────

    @Test
    fun `连续答对增加连击`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        for (i in 1..5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            assertEquals(i, session.currentStreak)
            session.next()
        }
        assertEquals(5, session.bestStreak)
    }

    @Test
    fun `答错归零连击`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        // 先答对几道
        for (i in 1..3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.currentStreak)
        // 答错
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak)
    }

    @Test
    fun `bestStreak记录最长连击`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        // 连答 3
        repeat(3) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        // 答错
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        session.next()
        // 连答 2
        repeat(2) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(3, session.bestStreak)
    }

    // ── next() ──────────────────────────────────────────

    @Test
    fun `next后生成新题目`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        val firstQuestion = session.currentQuestion
        session.next()
        assertNotNull(session.currentQuestion)
        // 新题目应该是新生成的（虽然不保证内容不同，但对象不同）
        assertNotSame(firstQuestion, session.currentQuestion)
    }

    @Test
    fun `next后isAnswered重置为false`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    @Test
    fun `未开始时next返回null`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        assertNull(session.next())
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `未答题时准确率为0`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        assertEquals(0.0, session.accuracy, 0.001)
    }

    @Test
    fun `全对准确率为1`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
            session.next()
        }
        assertEquals(1.0, session.accuracy, 0.001)
    }

    @Test
    fun `半对半错准确率为0点5`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        // 对
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        // 错
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(0.5, session.accuracy, 0.001)
    }

    // ── history ──────────────────────────────────────────

    @Test
    fun `history记录所有答题`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        session.submit(wrong)
        assertEquals(2, session.history.size)
    }

    @Test
    fun `history首条记录是第一次答题`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        val answer = session.currentQuestion!!.correctAnswer
        session.submit(answer)
        assertEquals(answer, session.history[0].userAnswer)
    }

    // ── lastAnswer ──────────────────────────────────────────

    @Test
    fun `lastAnswer记录最近一次答题`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        session.submit(session.currentQuestion!!.correctAnswer)
        session.next()
        val wrong = session.currentQuestion!!.answerChoices
            .first { it != session.currentQuestion!!.correctAnswer }
        val record = session.submit(wrong)
        assertEquals(record, session.lastAnswer)
    }

    @Test
    fun `start后lastAnswer为null`() {
        val session = createSession(ArticulationTrainingDifficulty.BEGINNER)
        session.start()
        assertNull(session.lastAnswer)
    }

    // ── reset ──────────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
        val session = createSession(ArticulationTrainingDifficulty.ADVANCED)
        session.start()
        repeat(5) {
            session.submit(session.currentQuestion!!.correctAnswer)
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

    // ── difficulty和noteCount ─────────────────────────────────

    @Test
    fun `difficulty返回正确难度`() {
        val session = ArticulationTrainingSession(
            ArticulationTrainingEngine(),
            ArticulationTrainingDifficulty.INTERMEDIATE,
            noteCount = 7
        )
        assertEquals(ArticulationTrainingDifficulty.INTERMEDIATE, session.difficulty())
    }

    @Test
    fun `noteCount返回正确值`() {
        val session = ArticulationTrainingSession(
            ArticulationTrainingEngine(),
            ArticulationTrainingDifficulty.ADVANCED,
            noteCount = 7
        )
        assertEquals(7, session.noteCount())
    }
}
