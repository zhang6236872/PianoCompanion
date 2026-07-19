package com.pianocompanion.swingfeel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SwingProgress] / [SwingProgressEntry] 单元测试。
 *
 * 验证累计统计、难度隔离、JSON 往返、容错解析、严格 5 字段校验、负数回退 0 等。
 */
class SwingProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = SwingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 1e-9)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates counts`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(5, p.totalCorrect)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.BEGINNER, 3, 5, 2)
        p.recordSession(SwingDifficulty.BEGINNER, 4, 5, 4)
        assertEquals(2, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
        assertEquals(4, p.overallBestStreak)
    }

    @Test
    fun `difficulty isolation`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.BEGINNER, 1, 2, 1)
        p.recordSession(SwingDifficulty.ADVANCED, 2, 2, 2)
        val beg = p.getProgress(SwingDifficulty.BEGINNER)
        val adv = p.getProgress(SwingDifficulty.ADVANCED)
        assertEquals(2, beg.totalAnswered)
        assertEquals(2, adv.totalAnswered)
        assertEquals(1, beg.totalCorrect)
        assertEquals(2, adv.totalCorrect)
        assertEquals(1, beg.bestStreak)
        assertEquals(2, adv.bestStreak)
        // 未记录的难度返回空 entry
        val mid = p.getProgress(SwingDifficulty.INTERMEDIATE)
        assertEquals(0, mid.totalAnswered)
    }

    @Test
    fun `overall accuracy aggregates across difficulties`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.BEGINNER, 4, 5, 1)
        p.recordSession(SwingDifficulty.ADVANCED, 1, 5, 1)
        assertEquals(10, p.totalAnswered)
        assertEquals(5, p.totalCorrect)
        assertEquals(0.5, p.overallAccuracy, 1e-9)
    }

    @Test
    fun `best accuracy tracks per-session max`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.BEGINNER, 1, 4, 1) // 25%
        p.recordSession(SwingDifficulty.BEGINNER, 3, 4, 2) // 75%
        p.recordSession(SwingDifficulty.BEGINNER, 2, 4, 1) // 50%
        assertEquals(0.75, p.getProgress(SwingDifficulty.BEGINNER).bestAccuracy, 1e-9)
    }

    @Test
    fun `best streak only increases`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.ADVANCED, 1, 1, 5)
        p.recordSession(SwingDifficulty.ADVANCED, 1, 1, 3)
        assertEquals(5, p.getProgress(SwingDifficulty.ADVANCED).bestStreak)
    }

    // ── JSON 往返 ────────────────────────────────────────

    @Test
    fun `json round trip preserves stats`() {
        val p = SwingProgress()
        p.recordSession(SwingDifficulty.BEGINNER, 3, 5, 2)
        p.recordSession(SwingDifficulty.ADVANCED, 4, 5, 4)
        val json = p.toJson()
        val restored = SwingProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.getProgress(SwingDifficulty.BEGINNER).bestAccuracy,
            restored.getProgress(SwingDifficulty.BEGINNER).bestAccuracy, 1e-9)
    }

    @Test
    fun `json round trip with all difficulties`() {
        val p = SwingProgress()
        SwingDifficulty.ALL.forEach { d ->
            p.recordSession(d, 2, 4, 1)
        }
        val restored = SwingProgress.fromJson(p.toJson())
        SwingDifficulty.ALL.forEach { d ->
            assertEquals(4, restored.getProgress(d).totalAnswered)
            assertEquals(2, restored.getProgress(d).totalCorrect)
        }
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `fromJson handles empty json`() {
        val p = SwingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles malformed json`() {
        val p = SwingProgress.fromJson("{this is not valid")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats key`() {
        val p = SwingProgress.fromJson("{\"other\":123}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson skips entries missing required fields`() {
        // entry 缺少 bestStreak 字段 → 应被拒绝
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5,"totalCorrect":3,"sessionCount":1,"bestAccuracy":0.6}}}"""
        val p = SwingProgress.fromJson(json)
        // 缺字段的 entry 不计入
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson rejects partial entry missing bestAccuracy`() {
        val json = """{"stats":{"ADVANCED":{"totalAnswered":5,"totalCorrect":3,"sessionCount":1,"bestStreak":2}}}"""
        val p = SwingProgress.fromJson(json)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson accepts complete entry`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestStreak":3,"bestAccuracy":0.7}}}"""
        val p = SwingProgress.fromJson(json)
        val entry = p.getProgress(SwingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
        assertEquals(0.7, entry.bestAccuracy, 1e-9)
    }

    @Test
    fun `fromJson negative values fall back to zero`() {
        // 键存在但值为负 → 回退 0（而非计入负数）
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":-2,"sessionCount":1,"bestStreak":-1,"bestAccuracy":-0.5}}}"""
        val p = SwingProgress.fromJson(json)
        val entry = p.getProgress(SwingDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 1e-9)
    }

    @Test
    fun `fromJson accepts multiple difficulties`() {
        val json = """{"stats":{
            "BEGINNER":{"totalAnswered":4,"totalCorrect":2,"sessionCount":1,"bestStreak":1,"bestAccuracy":0.5},
            "ADVANCED":{"totalAnswered":6,"totalCorrect":5,"sessionCount":2,"bestStreak":4,"bestAccuracy":0.8333}
        }}"""
        val p = SwingProgress.fromJson(json)
        assertEquals(10, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
        assertEquals(4, p.overallBestStreak)
    }

    // ── entry 单元 ────────────────────────────────────────

    @Test
    fun `entry cumulative accuracy`() {
        val e = SwingProgressEntry(totalAnswered = 10, totalCorrect = 3)
        assertEquals(0.3, e.cumulativeAccuracy, 1e-9)
    }

    @Test
    fun `entry cumulative accuracy zero when no answers`() {
        val e = SwingProgressEntry()
        assertEquals(0.0, e.cumulativeAccuracy, 1e-9)
    }

    @Test
    fun `entry fromJson returns null for non-object`() {
        assertNull(SwingProgressEntry.fromJson("123"))
        assertNull(SwingProgressEntry.fromJson("\"string\""))
    }

    @Test
    fun `entry fromJson returns null for empty braces missing fields`() {
        assertNull(SwingProgressEntry.fromJson("{}"))
    }
}
