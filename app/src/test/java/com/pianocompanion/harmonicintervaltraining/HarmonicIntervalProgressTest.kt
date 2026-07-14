package com.pianocompanion.harmonicintervaltraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 和声音程辨识训练进度跟踪模型单元测试。
 *
 * 验证累计统计、难度隔离、JSON 往返、容错解析。
 */
class HarmonicIntervalProgressTest {

    // ── 基本统计 ──────────────────────────────────────────

    @Test
    fun `初始进度为空`() {
        val p = HarmonicIntervalProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession 累加统计`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 8, 10, 5)

        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `多次recordSession累加`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 7, 10, 4)

        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
        assertEquals(4, p.overallBestStreak)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `不同难度统计独立`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 10, 10, 5)
        p.recordSession(HarmonicIntervalDifficulty.ADVANCED, 2, 10, 3)

        val beginner = p.getProgress(HarmonicIntervalDifficulty.BEGINNER)
        val advanced = p.getProgress(HarmonicIntervalDifficulty.ADVANCED)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(10, beginner.totalCorrect)
        assertEquals(5, beginner.bestStreak)

        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
        assertEquals(3, advanced.bestStreak)
    }

    @Test
    fun `未记录难度返回空进度`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 3)

        val advanced = p.getProgress(HarmonicIntervalDifficulty.ADVANCED)
        assertEquals(0, advanced.totalAnswered)
        assertEquals(0, advanced.totalCorrect)
    }

    // ── bestStreak 取最大 ──────────────────────────────────────────

    @Test
    fun `bestStreak保持历史最大值`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 7)
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 3, 10, 4)

        assertEquals(7, p.getProgress(HarmonicIntervalDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `bestStreak可被打破`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 8)
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 6)

        assertEquals(8, p.getProgress(HarmonicIntervalDifficulty.BEGINNER).bestStreak)
    }

    // ── bestAccuracy 取最大 ──────────────────────────────────────────

    @Test
    fun `bestAccuracy保持历史最大值`() {
        val p = HarmonicIntervalProgress()
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 9, 10, 5)
        p.recordSession(HarmonicIntervalDifficulty.BEGINNER, 5, 10, 3)

        assertEquals(0.9, p.getProgress(HarmonicIntervalDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `JSON往返保持数据一致`() {
        val original = HarmonicIntervalProgress()
        original.recordSession(HarmonicIntervalDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(HarmonicIntervalDifficulty.ADVANCED, 6, 15, 3)

        val json = original.toJson()
        val restored = HarmonicIntervalProgress.fromJson(json)

        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON往返保持各难度独立`() {
        val original = HarmonicIntervalProgress()
        original.recordSession(HarmonicIntervalDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(HarmonicIntervalDifficulty.INTERMEDIATE, 6, 12, 4)
        original.recordSession(HarmonicIntervalDifficulty.ADVANCED, 3, 15, 2)

        val json = original.toJson()
        val restored = HarmonicIntervalProgress.fromJson(json)

        for (d in HarmonicIntervalDifficulty.ALL) {
            val o = original.getProgress(d)
            val r = restored.getProgress(d)
            assertEquals("$d totalAnswered", o.totalAnswered, r.totalAnswered)
            assertEquals("$d totalCorrect", o.totalCorrect, r.totalCorrect)
            assertEquals("$d bestStreak", o.bestStreak, r.bestStreak)
            assertEquals("$d bestAccuracy", o.bestAccuracy, r.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `空进度的JSON往返`() {
        val empty = HarmonicIntervalProgress()
        val json = empty.toJson()
        val restored = HarmonicIntervalProgress.fromJson(json)

        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalCorrect)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = HarmonicIntervalProgress.fromJson("not valid json {{{")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalCorrect)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = HarmonicIntervalProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val restored = HarmonicIntervalProgress.fromJson("{\"other\":123}")
        assertEquals(0, restored.totalAnswered)
    }

    // ── ProgressEntry ──────────────────────────────────────────

    @Test
    fun `ProgressEntry JSON往返`() {
        val entry = HarmonicIntervalProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = HarmonicIntervalProgressEntry.fromJson(json)

        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(35, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.85, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry cumulativeAccuracy`() {
        val entry = HarmonicIntervalProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 零答题时cumulativeAccuracy为零`() {
        val entry = HarmonicIntervalProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 损坏JSON返回null`() {
        val restored = HarmonicIntervalProgressEntry.fromJson("broken")
        assertNull(restored)
    }
}
