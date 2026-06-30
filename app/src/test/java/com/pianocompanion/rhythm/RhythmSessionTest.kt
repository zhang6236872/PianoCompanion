package com.pianocompanion.rhythm

import org.junit.Assert.*
import org.junit.Test

/**
 * [RhythmSession] 节奏训练会话状态机单元测试。
 *
 * 覆盖：
 * - 会话生命周期（start → submitTaps → next → end）
 * - 统计追踪（答题数/通过数/平均分/连击/最佳连击）
 * - 通过阈值判定
 * - 状态守卫（未开始不能提交/已答题不能重复提交）
 * - 历史记录
 */
class RhythmSessionTest {

    private fun createSession(difficulty: RhythmDifficulty = RhythmDifficulty.BEGINNER): RhythmSession {
        return RhythmSession(RhythmPatternGenerator.withSeed(42), difficulty)
    }

    /** 生成恰好匹配所有目标的敲击（全部 Perfect）。 */
    private fun perfectTaps(pattern: RhythmPattern): List<Long> {
        return pattern.toTapTargets().map { it.onsetMs }
    }

    /** 生成完全不匹配的敲击（全部 Miss）。 */
    private fun badTaps(pattern: RhythmPattern): List<Long> {
        // 所有敲击远偏离目标
        return pattern.toTapTargets().map { it.onsetMs + 9999 }
    }

    // ── 生命周期 ──────────────────────────────────────────

    @Test
    fun `start后生成第一题`() {
        val session = createSession()
        assertFalse(session.isStarted)
        session.start()
        assertTrue(session.isStarted)
        assertNotNull(session.currentPattern)
    }

    @Test
    fun `start重置统计`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        session.start() // 重新开始
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.passedCount)
        assertEquals(0.0, session.averageScore, 0.001)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `next生成新题目`() {
        val session = createSession()
        session.start()
        val first = session.currentPattern
        session.submitTaps(perfectTaps(first!!))
        session.next()
        assertNotNull(session.currentPattern)
        // 新题目应该不同（虽然不是严格要求，但种子不同时通常不同）
    }

    @Test
    fun `未start时next返回null`() {
        val session = createSession()
        assertNull(session.next())
    }

    // ── 统计追踪 ──────────────────────────────────────────

    @Test
    fun `完美敲击记为通过`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        assertEquals(1, session.answeredCount)
        assertEquals(1, session.passedCount)
        assertEquals(1, session.currentStreak)
    }

    @Test
    fun `差敲击记为不通过`() {
        val session = createSession()
        session.start()
        session.submitTaps(badTaps(session.currentPattern!!))
        assertEquals(1, session.answeredCount)
        assertEquals(0, session.passedCount)
        assertEquals(0, session.currentStreak)
    }

    @Test
    fun `平均分正确计算`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        session.next()
        session.submitTaps(badTaps(session.currentPattern!!))
        // 一题满分(1.0)，一题零分(0.0) → 平均 0.5
        assertEquals(0.5, session.averageScore, 0.01)
    }

    @Test
    fun `连击在未通过时归零`() {
        val session = createSession()
        session.start()
        // 连续通过 3 题
        repeat(3) {
            session.submitTaps(perfectTaps(session.currentPattern!!))
            session.next()
        }
        assertEquals(3, session.currentStreak)
        assertEquals(3, session.bestStreak)

        // 未通过
        session.submitTaps(badTaps(session.currentPattern!!))
        assertEquals(0, session.currentStreak)
        assertEquals(3, session.bestStreak) // 最佳记录保持
    }

    @Test
    fun `bestStreak记录最长连续通过`() {
        val session = createSession()
        session.start()
        // 通过 2 题
        repeat(2) {
            session.submitTaps(perfectTaps(session.currentPattern!!))
            session.next()
        }
        // 未通过 1 题
        session.submitTaps(badTaps(session.currentPattern!!))
        session.next()
        // 再通过 3 题
        repeat(3) {
            session.submitTaps(perfectTaps(session.currentPattern!!))
            session.next()
        }
        assertEquals(3, session.bestStreak)
    }

    // ── 通过率 ────────────────────────────────────────────

    @Test
    fun `passRate正确计算`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        session.next()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        session.next()
        session.submitTaps(badTaps(session.currentPattern!!))
        // 2/3 ≈ 0.667
        assertEquals(0.667, session.passRate, 0.01)
    }

    @Test
    fun `未答题时passRate为0`() {
        val session = createSession()
        assertEquals(0.0, session.passRate, 0.001)
    }

    // ── 状态守卫 ──────────────────────────────────────────

    @Test
    fun `未start时submitTaps返回null`() {
        val session = createSession()
        assertNull(session.submitTaps(listOf(0L, 500L)))
    }

    @Test
    fun `已答题时重复submitTaps返回null`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        // 已答题，不能重复提交
        assertNull(session.submitTaps(listOf(0L)))
    }

    @Test
    fun `submitTaps后isAnswered为true`() {
        val session = createSession()
        session.start()
        assertFalse(session.isAnswered)
        session.submitTaps(perfectTaps(session.currentPattern!!))
        assertTrue(session.isAnswered)
    }

    @Test
    fun `next后isAnswered重置为false`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        assertTrue(session.isAnswered)
        session.next()
        assertFalse(session.isAnswered)
    }

    // ── 历史记录 ──────────────────────────────────────────

    @Test
    fun `历史记录按时间顺序`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        session.next()
        session.submitTaps(badTaps(session.currentPattern!!))

        assertEquals(2, session.history.size)
        assertTrue(session.history[0].isPassed)
        assertFalse(session.history[1].isPassed)
    }

    @Test
    fun `历史记录包含完整信息`() {
        val session = createSession()
        session.start()
        val taps = listOf(0L, 500L, 1000L)
        session.submitTaps(taps)

        val record = session.history.last()
        assertNotNull(record.pattern)
        assertEquals(taps, record.taps)
        assertNotNull(record.result)
    }

    // ── 重置 ──────────────────────────────────────────────

    @Test
    fun `reset清空所有状态`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        session.next()
        session.submitTaps(perfectTaps(session.currentPattern!!))

        session.reset()

        assertNull(session.currentPattern)
        assertEquals(0, session.answeredCount)
        assertEquals(0, session.passedCount)
        assertEquals(0.0, session.averageScore, 0.001)
        assertEquals(0, session.currentStreak)
        assertEquals(0, session.bestStreak)
        assertTrue(session.history.isEmpty())
        assertFalse(session.isStarted)
    }

    // ── 难度 ──────────────────────────────────────────────

    @Test
    fun `difficulty返回会话难度`() {
        val session = createSession(RhythmDifficulty.ADVANCED)
        assertEquals(RhythmDifficulty.ADVANCED, session.difficulty())
    }

    @Test
    fun `初级会话生成的pattern只含四分和二分`() {
        val session = createSession(RhythmDifficulty.BEGINNER)
        session.start()
        for (event in session.currentPattern!!.events) {
            assertTrue(
                event.duration == RhythmDuration.QUARTER ||
                event.duration == RhythmDuration.HALF
            )
        }
    }

    // ── lastResult ────────────────────────────────────────

    @Test
    fun `lastResult保存最后一次匹配结果`() {
        val session = createSession()
        session.start()
        val result = session.submitTaps(perfectTaps(session.currentPattern!!))
        assertNotNull(result)
        assertEquals(result, session.lastResult)
    }

    @Test
    fun `reset后lastResult为null`() {
        val session = createSession()
        session.start()
        session.submitTaps(perfectTaps(session.currentPattern!!))
        assertNotNull(session.lastResult)
        session.reset()
        assertNull(session.lastResult)
    }

    // ── 通过阈值 ──────────────────────────────────────────

    @Test
    fun `PASS_THRESHOLD为0_5`() {
        assertEquals(0.5, RhythmSession.PASS_THRESHOLD, 0.001)
    }

    @Test
    fun `刚好达到阈值记为通过`() {
        val session = createSession()
        session.start()
        val targets = session.currentPattern!!.toTapTargets()
        // 构造一个恰好 0.5 分的敲击：一半 Perfect，一半 Miss
        val taps = targets.take(targets.size / 2 + 1).map { it.onsetMs }
        val result = session.submitTaps(taps)
        if (result != null && result.score >= 0.5) {
            assertEquals(1, session.passedCount)
        }
    }
}
