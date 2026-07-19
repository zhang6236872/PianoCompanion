package com.pianocompanion.melodiccontour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContourProgress] 单元测试。
 *
 * 验证累计统计、难度隔离、JSON 往返、容错解析、严格字段校验、负数回退等。
 */
class ContourProgressTest {

    // ── 基础累计统计 ──────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = ContourProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates counts`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ContourDifficulty.BEGINNER, 7, 10, 6)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.0001)
        assertEquals(6, p.overallBestStreak)
    }

    @Test
    fun `best streak is maximum across sessions`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 3, 5, 4)
        p.recordSession(ContourDifficulty.BEGINNER, 2, 5, 2)
        assertEquals(4, p.overallBestStreak)
    }

    @Test
    fun `best accuracy is maximum across sessions`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 5, 10, 1) // 50%
        p.recordSession(ContourDifficulty.BEGINNER, 9, 10, 3) // 90%
        val entry = p.getProgress(ContourDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.0001)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `difficulties are isolated`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ContourDifficulty.ADVANCED, 8, 10, 7)

        val beginner = p.getProgress(ContourDifficulty.BEGINNER)
        val advanced = p.getProgress(ContourDifficulty.ADVANCED)
        val intermediate = p.getProgress(ContourDifficulty.INTERMEDIATE)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(8, advanced.totalCorrect)
        assertEquals(0, intermediate.totalAnswered)
    }

    @Test
    fun `overall stats span all difficulties`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ContourDifficulty.INTERMEDIATE, 6, 10, 4)
        p.recordSession(ContourDifficulty.ADVANCED, 7, 10, 5)
        assertEquals(3, p.totalSessions)
        assertEquals(30, p.totalAnswered)
        assertEquals(18, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.0001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `getProgress returns empty for unknown difficulty`() {
        val p = ContourProgress()
        val entry = p.getProgress(ContourDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.sessionCount)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    // ── JSON 往返 ────────────────────────────────────────

    @Test
    fun `toJson fromJson round trip preserves data`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ContourDifficulty.ADVANCED, 6, 10, 3)

        val json = p.toJson()
        val restored = ContourProgress.fromJson(json)

        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        val origBeg = p.getProgress(ContourDifficulty.BEGINNER)
        val restBeg = restored.getProgress(ContourDifficulty.BEGINNER)
        assertEquals(origBeg.totalAnswered, restBeg.totalAnswered)
        assertEquals(origBeg.bestAccuracy, restBeg.bestAccuracy, 0.0001)
    }

    @Test
    fun `toJson produces valid JSON with stats key`() {
        val p = ContourProgress()
        p.recordSession(ContourDifficulty.BEGINNER, 1, 1, 1)
        val json = p.toJson()
        assertTrue(json.startsWith("{\"stats\":{"))
        assertTrue(json.contains("\"BEGINNER\""))
        assertTrue(json.contains("\"totalAnswered\""))
    }

    @Test
    fun `empty progress toJson is valid`() {
        val p = ContourProgress()
        val json = p.toJson()
        assertEquals("{\"stats\":{}}", json)
        val restored = ContourProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `fromJson handles invalid JSON gracefully`() {
        val restored = ContourProgress.fromJson("not json {{{")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `fromJson handles empty string`() {
        val restored = ContourProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles null-like content`() {
        val restored = ContourProgress.fromJson("null")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats key`() {
        val restored = ContourProgress.fromJson("{\"other\":123}")
        assertEquals(0, restored.totalAnswered)
    }

    // ── 严格字段校验 ──────────────────────────────────────

    @Test
    fun `entry with all fields parses correctly`() {
        val json = """{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestStreak":5,"bestAccuracy":0.9000}"""
        val entry = ContourProgressEntry.fromJson(json)
        assertNotNull(entry)
        assertEquals(10, entry!!.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
        assertEquals(0.9, entry.bestAccuracy, 0.0001)
    }

    @Test
    fun `entry missing totalAnswered is rejected`() {
        val json = """{"totalCorrect":7,"sessionCount":2,"bestStreak":5,"bestAccuracy":0.9}"""
        assertNull(ContourProgressEntry.fromJson(json))
    }

    @Test
    fun `entry missing totalCorrect is rejected`() {
        val json = """{"totalAnswered":10,"sessionCount":2,"bestStreak":5,"bestAccuracy":0.9}"""
        assertNull(ContourProgressEntry.fromJson(json))
    }

    @Test
    fun `entry missing sessionCount is rejected`() {
        val json = """{"totalAnswered":10,"totalCorrect":7,"bestStreak":5,"bestAccuracy":0.9}"""
        assertNull(ContourProgressEntry.fromJson(json))
    }

    @Test
    fun `entry missing bestStreak is rejected`() {
        val json = """{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestAccuracy":0.9}"""
        assertNull(ContourProgressEntry.fromJson(json))
    }

    @Test
    fun `entry missing bestAccuracy is rejected`() {
        val json = """{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestStreak":5}"""
        assertNull(ContourProgressEntry.fromJson(json))
    }

    @Test
    fun `entry with partial fields in full progress is ignored`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestStreak":5,"bestAccuracy":0.9000},"ADVANCED":{"totalAnswered":5}}}"""
        val p = ContourProgress.fromJson(json)
        // BEGINNER 完整，应解析
        assertEquals(10, p.getProgress(ContourDifficulty.BEGINNER).totalAnswered)
        // ADVANCED 缺字段，应被忽略
        assertEquals(0, p.getProgress(ContourDifficulty.ADVANCED).totalAnswered)
    }

    // ── 负数容错 ──────────────────────────────────────────

    @Test
    fun `negative values fall back to zero`() {
        val json = """{"totalAnswered":-5,"totalCorrect":-3,"sessionCount":-1,"bestStreak":-2,"bestAccuracy":-0.5}"""
        val entry = ContourProgressEntry.fromJson(json)
        assertNotNull(entry)
        assertEquals(0, entry!!.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.0001)
    }

    // ── cumulativeAccuracy ───────────────────────────────

    @Test
    fun `cumulativeAccuracy computes correctly`() {
        val entry = ContourProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `cumulativeAccuracy is zero when no answers`() {
        val entry = ContourProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    // ── entry toJson ─────────────────────────────────────

    @Test
    fun `entry toJson round trip`() {
        val entry = ContourProgressEntry(
            totalAnswered = 15, totalCorrect = 10,
            sessionCount = 3, bestStreak = 7, bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = ContourProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `entry fromJson rejects non-object`() {
        assertNull(ContourProgressEntry.fromJson("123"))
        assertNull(ContourProgressEntry.fromJson("\"string\""))
    }
}
