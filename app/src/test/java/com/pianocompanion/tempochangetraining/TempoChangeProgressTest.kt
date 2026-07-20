package com.pianocompanion.tempochangetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 速度变化方向辨识训练进度跟踪单元测试。
 *
 * 验证 [TempoChangeProgress] 的会话记录聚合、JSON 序列化往返与容错解析。
 */
class TempoChangeProgressTest {

    // ── 默认值 ───────────────────────────────────────────

    @Test
    fun `empty progress has zero totals`() {
        val p = TempoChangeProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 1e-9)
        assertEquals(0, p.overallBestStreak)
    }

    // ── recordSession ────────────────────────────────────

    @Test
    fun `recordSession accumulates totals`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 8, 10, 4)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 1e-9)
    }

    @Test
    fun `recordSession multiple sessions accumulate`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(TempoChangeDifficulty.BEGINNER, 7, 10, 5)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
    }

    @Test
    fun `recordSession tracks best streak across sessions`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.INTERMEDIATE, 5, 10, 3)
        assertEquals(3, p.overallBestStreak)
        p.recordSession(TempoChangeDifficulty.INTERMEDIATE, 5, 10, 6)
        assertEquals(6, p.overallBestStreak)
        // 较小的连击不应覆盖
        p.recordSession(TempoChangeDifficulty.INTERMEDIATE, 5, 10, 2)
        assertEquals(6, p.overallBestStreak)
    }

    @Test
    fun `recordSession tracks best session accuracy`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.ADVANCED, 5, 10, 3)
        val entry = p.getProgress(TempoChangeDifficulty.ADVANCED)
        assertEquals(0.5, entry.bestAccuracy, 1e-9)
        p.recordSession(TempoChangeDifficulty.ADVANCED, 9, 10, 4)
        val entry2 = p.getProgress(TempoChangeDifficulty.ADVANCED)
        assertEquals(0.9, entry2.bestAccuracy, 1e-9)
    }

    @Test
    fun `recordSession per difficulty is independent`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(TempoChangeDifficulty.ADVANCED, 2, 10, 1)
        assertEquals(10, p.getProgress(TempoChangeDifficulty.BEGINNER).totalAnswered)
        assertEquals(10, p.getProgress(TempoChangeDifficulty.ADVANCED).totalAnswered)
        assertEquals(5, p.getProgress(TempoChangeDifficulty.BEGINNER).totalCorrect)
        assertEquals(2, p.getProgress(TempoChangeDifficulty.ADVANCED).totalCorrect)
    }

    @Test
    fun `getProgress for unseen difficulty returns empty entry`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 5, 10, 3)
        val adv = p.getProgress(TempoChangeDifficulty.ADVANCED)
        assertEquals(0, adv.totalAnswered)
        assertEquals(0, adv.sessionCount)
    }

    // ── JSON 序列化往返 ─────────────────────────────────

    @Test
    fun `toJson fromJson round trip preserves data`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 8, 10, 4)
        p.recordSession(TempoChangeDifficulty.ADVANCED, 3, 5, 2)

        val json = p.toJson()
        val restored = TempoChangeProgress.fromJson(json)

        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 1e-9)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `toJson fromJson preserves per-difficulty stats`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.INTERMEDIATE, 7, 10, 5)
        val json = p.toJson()
        val restored = TempoChangeProgress.fromJson(json)
        val entry = restored.getProgress(TempoChangeDifficulty.INTERMEDIATE)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
        assertEquals(0.7, entry.bestAccuracy, 1e-9)
    }

    @Test
    fun `fromJson of empty json returns empty progress`() {
        val p = TempoChangeProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson of garbage json returns empty progress`() {
        val p = TempoChangeProgress.fromJson("this is not json {{{")
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson of malformed json does not throw`() {
        // 各种畸形输入都不应抛异常
        TempoChangeProgress.fromJson("{\"stats\":{")
        TempoChangeProgress.fromJson("{\"stats\":}")
        TempoChangeProgress.fromJson("{\"stats\":{\"BEGINNER\":{}}}")
        TempoChangeProgress.fromJson(null ?: "")
    }

    @Test
    fun `fromJson with missing fields returns empty progress`() {
        // 缺少字段的条目应被丢弃（严格解析）
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":5}}}"""
        val p = TempoChangeProgress.fromJson(json)
        // BEGINNER 因字段不全被丢弃
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with negative values coerced to zero`() {
        // 负数回退 0
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":3,"sessionCount":1,"bestStreak":2,"bestAccuracy":0.5}}}"""
        val p = TempoChangeProgress.fromJson(json)
        val entry = p.getProgress(TempoChangeDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(3, entry.totalCorrect)
    }

    // ── cumulativeAccuracy ───────────────────────────────

    @Test
    fun `entry cumulativeAccuracy is correct`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 3, 10, 2)
        p.recordSession(TempoChangeDifficulty.BEGINNER, 5, 10, 4)
        val entry = p.getProgress(TempoChangeDifficulty.BEGINNER)
        // 累计 8/20 = 0.4
        assertEquals(0.4, entry.cumulativeAccuracy, 1e-9)
    }

    @Test
    fun `entry cumulativeAccuracy is zero when no answers`() {
        val entry = TempoChangeProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 1e-9)
    }

    @Test
    fun `overallAccuracy is zero when no answers`() {
        val p = TempoChangeProgress()
        assertEquals(0.0, p.overallAccuracy, 1e-9)
    }

    // ── toJson 有效性 ────────────────────────────────────

    @Test
    fun `toJson produces non-empty string`() {
        val p = TempoChangeProgress()
        p.recordSession(TempoChangeDifficulty.BEGINNER, 1, 1, 1)
        val json = p.toJson()
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("stats"))
        assertTrue(json.contains("BEGINNER"))
    }

    @Test
    fun `multiple difficulties serialize and restore`() {
        val p = TempoChangeProgress()
        TempoChangeDifficulty.ALL.forEach { d ->
            p.recordSession(d, 1, 2, 1)
        }
        val json = p.toJson()
        val restored = TempoChangeProgress.fromJson(json)
        TempoChangeDifficulty.ALL.forEach { d ->
            val entry = restored.getProgress(d)
            assertNotNull(entry)
            assertEquals(2, entry.totalAnswered)
        }
    }
}
