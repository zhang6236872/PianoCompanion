package com.pianocompanion.rhythmreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmReadingProgress] 单元测试。
 *
 * 验证：
 * - 累计统计更新（答题数/正确数/会话数/最佳连击/最佳准确率）
 * - 各难度独立统计隔离
 * - JSON 序列化往返（toJson → fromJson 数据完整）
 * - 损坏 JSON 容错
 * - 全局汇总（totalSessions / overallAccuracy）
 */
class RhythmReadingProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = RhythmReadingProgress()
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0, p.totalSessions)
        assertEquals(0.0, p.overallAccuracy, 1e-9)
    }

    @Test
    fun `recordSession updates counts`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 5)
        val entry = p.getProgress(RhythmReadingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `cumulative accuracy calculation`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 7, 10, 3)
        assertEquals(0.7, p.getProgress(RhythmReadingDifficulty.BEGINNER).cumulativeAccuracy, 1e-9)
    }

    @Test
    fun `best streak is maximum across sessions`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.INTERMEDIATE, 5, 5, 4)
        p.recordSession(RhythmReadingDifficulty.INTERMEDIATE, 3, 5, 7)
        p.recordSession(RhythmReadingDifficulty.INTERMEDIATE, 4, 5, 2)
        assertEquals(7, p.getProgress(RhythmReadingDifficulty.INTERMEDIATE).bestStreak)
    }

    @Test
    fun `best accuracy tracks maximum session accuracy`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.ADVANCED, 8, 10, 3) // 0.8
        p.recordSession(RhythmReadingDifficulty.ADVANCED, 10, 10, 5) // 1.0
        p.recordSession(RhythmReadingDifficulty.ADVANCED, 5, 10, 2) // 0.5
        assertEquals(1.0, p.getProgress(RhythmReadingDifficulty.ADVANCED).bestAccuracy, 1e-9)
    }

    @Test
    fun `different difficulties tracked independently`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 10, 10, 5)
        p.recordSession(RhythmReadingDifficulty.ADVANCED, 2, 10, 1)
        assertEquals(10, p.getProgress(RhythmReadingDifficulty.BEGINNER).totalAnswered)
        assertEquals(10, p.getProgress(RhythmReadingDifficulty.ADVANCED).totalAnswered)
        assertEquals(10, p.getProgress(RhythmReadingDifficulty.BEGINNER).totalCorrect)
        assertEquals(2, p.getProgress(RhythmReadingDifficulty.ADVANCED).totalCorrect)
        assertEquals(0, p.getProgress(RhythmReadingDifficulty.INTERMEDIATE).totalAnswered)
    }

    @Test
    fun `total sessions sums across difficulties`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 1, 1, 1)
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 1, 1, 1)
        p.recordSession(RhythmReadingDifficulty.INTERMEDIATE, 1, 1, 1)
        assertEquals(3, p.totalSessions)
    }

    @Test
    fun `overall accuracy combines all`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(RhythmReadingDifficulty.ADVANCED, 4, 10, 2)
        // 12/20 = 0.6
        assertEquals(0.6, p.overallAccuracy, 1e-9)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `json round trip preserves data`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(RhythmReadingDifficulty.ADVANCED, 3, 7, 2)
        val json = p.toJson()
        val restored = RhythmReadingProgress.fromJson(json)
        assertEquals(10, restored.getProgress(RhythmReadingDifficulty.BEGINNER).totalAnswered)
        assertEquals(8, restored.getProgress(RhythmReadingDifficulty.BEGINNER).totalCorrect)
        assertEquals(5, restored.getProgress(RhythmReadingDifficulty.BEGINNER).bestStreak)
        assertEquals(7, restored.getProgress(RhythmReadingDifficulty.ADVANCED).totalAnswered)
        assertEquals(3, restored.getProgress(RhythmReadingDifficulty.ADVANCED).totalCorrect)
    }

    @Test
    fun `json round trip preserves best accuracy`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.INTERMEDIATE, 9, 10, 4)
        val json = p.toJson()
        val restored = RhythmReadingProgress.fromJson(json)
        assertEquals(0.9, restored.getProgress(RhythmReadingDifficulty.INTERMEDIATE).bestAccuracy, 1e-9)
    }

    @Test
    fun `fromJson handles empty json`() {
        val p = RhythmReadingProgress.fromJson("{}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles malformed json`() {
        val p = RhythmReadingProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles empty stats object`() {
        val p = RhythmReadingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles null input gracefully`() {
        val p = RhythmReadingProgress.fromJson("")
        assertNotNull(p)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `multiple sessions accumulate counts`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 5, 5, 3)
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 3, 5, 2)
        val entry = p.getProgress(RhythmReadingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
    }

    @Test
    fun `getProgress for unrecorded difficulty returns empty entry`() {
        val p = RhythmReadingProgress()
        val entry = p.getProgress(RhythmReadingDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 1e-9)
    }

    @Test
    fun `zero total session does not crash`() {
        val p = RhythmReadingProgress()
        p.recordSession(RhythmReadingDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0, p.totalAnswered)
        assertEquals(0.0, p.overallAccuracy, 1e-9)
    }
}
