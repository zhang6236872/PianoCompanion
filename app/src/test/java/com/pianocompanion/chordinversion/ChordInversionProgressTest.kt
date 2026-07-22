package com.pianocompanion.chordinversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练进度跟踪单元测试。
 */
class ChordInversionProgressTest {

    // ── 累计统计 ──────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val progress = ChordInversionProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates stats`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 7, 10, 4)
        val entry = progress.getProgress(ChordInversionDifficulty.BEGINNER)
        assertEquals(20, entry.totalAnswered)
        assertEquals(15, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `difficulty isolation`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ChordInversionDifficulty.ADVANCED, 9, 10, 7)
        val beginner = progress.getProgress(ChordInversionDifficulty.BEGINNER)
        val advanced = progress.getProgress(ChordInversionDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(3, beginner.bestStreak)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(9, advanced.totalCorrect)
        assertEquals(7, advanced.bestStreak)
    }

    @Test
    fun `overall stats aggregate across difficulties`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ChordInversionDifficulty.INTERMEDIATE, 7, 10, 6)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(6, progress.overallBestStreak)
    }

    // ── bestAccuracy ──────────────────────────────────

    @Test
    fun `bestAccuracy only increases`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 9, 10, 5) // 90%
        var entry = progress.getProgress(ChordInversionDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 5, 10, 3) // 50%
        entry = progress.getProgress(ChordInversionDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001) // still 90%
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 10, 10, 8) // 100%
        entry = progress.getProgress(ChordInversionDifficulty.BEGINNER)
        assertEquals(1.0, entry.bestAccuracy, 0.001) // now 100%
    }

    @Test
    fun `cumulativeAccuracy is running average`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.INTERMEDIATE, 8, 10, 4)
        var entry = progress.getProgress(ChordInversionDifficulty.INTERMEDIATE)
        assertEquals(0.8, entry.cumulativeAccuracy, 0.001)
        progress.recordSession(ChordInversionDifficulty.INTERMEDIATE, 6, 10, 3)
        entry = progress.getProgress(ChordInversionDifficulty.INTERMEDIATE)
        assertEquals(0.7, entry.cumulativeAccuracy, 0.001) // 14/20
    }

    // ── JSON 往返 ──────────────────────────────────

    @Test
    fun `json roundtrip preserves all data`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(ChordInversionDifficulty.INTERMEDIATE, 7, 10, 4)
        progress.recordSession(ChordInversionDifficulty.ADVANCED, 9, 10, 7)

        val json = progress.toJson()
        val restored = ChordInversionProgress.fromJson(json)

        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)

        ChordInversionDifficulty.ALL.forEach { difficulty ->
            val orig = progress.getProgress(difficulty)
            val rstr = restored.getProgress(difficulty)
            assertEquals(orig.totalAnswered, rstr.totalAnswered)
            assertEquals(orig.totalCorrect, rstr.totalCorrect)
            assertEquals(orig.sessionCount, rstr.sessionCount)
            assertEquals(orig.bestStreak, rstr.bestStreak)
            assertEquals(orig.bestAccuracy, rstr.bestAccuracy, 0.0001)
        }
    }

    @Test
    fun `json roundtrip for single entry`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.ADVANCED, 15, 20, 10)
        val json = progress.toJson()
        val restored = ChordInversionProgress.fromJson(json)
        val entry = restored.getProgress(ChordInversionDifficulty.ADVANCED)
        assertEquals(20, entry.totalAnswered)
        assertEquals(15, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(10, entry.bestStreak)
        assertEquals(0.75, entry.bestAccuracy, 0.001)
    }

    // ── 容错解析 ──────────────────────────────────

    @Test
    fun `fromJson handles corrupt json gracefully`() {
        val restored = ChordInversionProgress.fromJson("not valid json")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles empty string`() {
        val restored = ChordInversionProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles null-like content`() {
        val restored = ChordInversionProgress.fromJson("{}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson with missing stats key returns empty`() {
        val restored = ChordInversionProgress.fromJson("{\"otherKey\":42}")
        assertEquals(0, restored.totalAnswered)
    }

    // ── 严格 5 字段校验 ──────────────────────────────────

    @Test
    fun `entry with all 5 fields parses correctly`() {
        val json = """{"totalAnswered":50,"totalCorrect":40,"sessionCount":5,"bestStreak":12,"bestAccuracy":0.8500}"""
        val entry = ChordInversionProgressEntry.fromJson(json)
        assertNotNull(entry)
        assertEquals(50, entry!!.totalAnswered)
        assertEquals(40, entry.totalCorrect)
        assertEquals(5, entry.sessionCount)
        assertEquals(12, entry.bestStreak)
        assertEquals(0.85, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `entry missing any field returns null`() {
        val required = listOf("totalAnswered", "totalCorrect", "sessionCount", "bestStreak", "bestAccuracy")
        for (missing in required) {
            val fields = required.filter { it != missing }.map { "\"$it\":1" }
            val json = "{" + fields.joinToString(",") + "}"
            val entry = ChordInversionProgressEntry.fromJson(json)
            assertNull("Should return null when '$missing' is missing", entry)
        }
    }

    @Test
    fun `entry with negative numbers coerces to zero`() {
        val json = """{"totalAnswered":-5,"totalCorrect":-3,"sessionCount":-1,"bestStreak":-2,"bestAccuracy":-0.5}"""
        val entry = ChordInversionProgressEntry.fromJson(json)
        assertNotNull(entry)
        assertEquals(0, entry!!.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── 多难度序列化 ──────────────────────────────────

    @Test
    fun `multiple difficulties serialize and restore`() {
        val progress = ChordInversionProgress()
        progress.recordSession(ChordInversionDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(ChordInversionDifficulty.INTERMEDIATE, 4, 5, 3)
        progress.recordSession(ChordInversionDifficulty.ADVANCED, 5, 5, 4)
        val json = progress.toJson()
        val restored = ChordInversionProgress.fromJson(json)
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            val orig = progress.getProgress(difficulty)
            val rstr = restored.getProgress(difficulty)
            assertEquals(orig.totalAnswered, rstr.totalAnswered)
            assertEquals(orig.totalCorrect, rstr.totalCorrect)
        }
    }

    @Test
    fun `empty entry toJson is valid`() {
        val entry = ChordInversionProgressEntry()
        val json = entry.toJson()
        val restored = ChordInversionProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(0, restored!!.totalAnswered)
    }

    @Test
    fun `getProgress for unknown difficulty returns empty entry`() {
        val progress = ChordInversionProgress()
        val entry = progress.getProgress(ChordInversionDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.sessionCount)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }
}
