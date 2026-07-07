package com.pianocompanion.rhythmpattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmPatternProgress] 单元测试。
 *
 * 验证跨会话进度跟踪：
 * - 空进度
 * - 单次/多次会话累计
 * - bestStreak/bestAccuracy 追踪
 * - 不同难度/速度分开统计
 * - 全局汇总统计
 * - JSON 往返一致性
 * - 容错解析
 */
class RhythmPatternProgressTest {

    @Test
    fun `empty progress`() {
        val p = RhythmPatternProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `single session recording`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 8, 10, 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 8, 10, 5)
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 6, 10, 3)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `bestStreak does not decrease`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 5, 10, 7)
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 5, 10, 3)
        val entry = p.getProgress(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW)
        assertEquals(7, entry.bestStreak)
    }

    @Test
    fun `bestAccuracy tracks session accuracy`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 7, 10, 5) // 70%
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 9, 10, 6) // 90%
        val entry = p.getProgress(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy differs from bestAccuracy`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.ADVANCED, RhythmTempo.FAST, 8, 10, 5) // 80%
        p.recordSession(RhythmDifficulty.ADVANCED, RhythmTempo.FAST, 2, 10, 1) // 20%
        val entry = p.getProgress(RhythmDifficulty.ADVANCED, RhythmTempo.FAST)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.001) // (8+2)/(10+10)
        assertEquals(0.8, entry.bestAccuracy, 0.001) // best session
    }

    @Test
    fun `different difficulties tracked separately`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 5, 10, 3)
        p.recordSession(RhythmDifficulty.ADVANCED, RhythmTempo.SLOW, 2, 10, 1)
        val beginner = p.getProgress(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW)
        val advanced = p.getProgress(RhythmDifficulty.ADVANCED, RhythmTempo.SLOW)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
    }

    @Test
    fun `different tempos tracked separately`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 5, 10, 3)
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.FAST, 3, 10, 2)
        val slow = p.getProgress(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW)
        val fast = p.getProgress(RhythmDifficulty.BEGINNER, RhythmTempo.FAST)
        assertEquals(5, slow.totalCorrect)
        assertEquals(3, fast.totalCorrect)
    }

    @Test
    fun `global summary across all combos`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 5, 10, 3)
        p.recordSession(RhythmDifficulty.INTERMEDIATE, RhythmTempo.FAST, 7, 10, 6)
        p.recordSession(RhythmDifficulty.ADVANCED, RhythmTempo.SLOW, 3, 10, 2)
        assertEquals(3, p.totalSessions)
        assertEquals(30, p.totalAnswered)
        assertEquals(15, p.totalCorrect)
        assertEquals(0.5, p.overallAccuracy, 0.001)
        assertEquals(6, p.overallBestStreak)
    }

    @Test
    fun `JSON round trip`() {
        val p = RhythmPatternProgress()
        p.recordSession(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW, 5, 10, 3)
        p.recordSession(RhythmDifficulty.ADVANCED, RhythmTempo.FAST, 7, 10, 6)
        val json = p.toJson()
        val restored = RhythmPatternProgress.fromJson(json)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        val entry1 = restored.getProgress(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW)
        assertEquals(10, entry1.totalAnswered)
        assertEquals(5, entry1.totalCorrect)
        assertEquals(3, entry1.bestStreak)

        val entry2 = restored.getProgress(RhythmDifficulty.ADVANCED, RhythmTempo.FAST)
        assertEquals(10, entry2.totalAnswered)
        assertEquals(7, entry2.totalCorrect)
        assertEquals(6, entry2.bestStreak)
    }

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val p = RhythmPatternProgress.fromJson("")
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with corrupt string returns empty progress`() {
        val p = RhythmPatternProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson with missing stats returns empty progress`() {
        val p = RhythmPatternProgress.fromJson("{\"other\":123}")
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson with empty stats returns empty progress`() {
        val p = RhythmPatternProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `key format is DIFFICULTY_TEMPO`() {
        assertEquals("BEGINNER_SLOW", RhythmPatternProgress.key(RhythmDifficulty.BEGINNER, RhythmTempo.SLOW))
        assertEquals("ADVANCED_FAST", RhythmPatternProgress.key(RhythmDifficulty.ADVANCED, RhythmTempo.FAST))
    }

    @Test
    fun `six key combination round trip`() {
        val p = RhythmPatternProgress()
        for (d in RhythmDifficulty.ALL) {
            for (t in RhythmTempo.ALL) {
                p.recordSession(d, t, 1, 2, 1)
            }
        }
        val json = p.toJson()
        val restored = RhythmPatternProgress.fromJson(json)
        assertEquals(6, restored.totalSessions)
        for (d in RhythmDifficulty.ALL) {
            for (t in RhythmTempo.ALL) {
                val entry = restored.getProgress(d, t)
                assertEquals(2, entry.totalAnswered)
                assertEquals(1, entry.totalCorrect)
            }
        }
    }

    @Test
    fun `progress entry JSON round trip`() {
        val entry = RhythmPatternProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.875
        )
        val json = entry.toJson()
        val restored = RhythmPatternProgressEntry.fromJson(json)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(30, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.875, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `progress entry cumulative accuracy`() {
        val entry = RhythmPatternProgressEntry(totalAnswered = 0, totalCorrect = 0)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)

        entry.totalAnswered = 20
        entry.totalCorrect = 15
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }
}
