package com.pianocompanion.voiceentryorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声部进入顺序辨识训练进度跟踪单元测试。
 *
 * 覆盖 [VoiceEntryProgress] / [VoiceEntryProgressEntry] 的记录、累加、JSON 往返与容错解析。
 */
class VoiceEntryProgressTest {

    // ── 空状态 ──────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = VoiceEntryProgress()
        EntryDifficulty.ALL.forEach { d ->
            val entry = p.getProgress(d)
            assertEquals(0, entry.bestStreak)
            assertEquals(0, entry.totalAnswered)
            assertEquals(0, entry.totalCorrect)
            assertEquals(0, entry.sessionCount)
            assertEquals(0.0, entry.bestAccuracy, 0.001)
            assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
        }
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `empty progress serializes and deserializes`() {
        val p = VoiceEntryProgress()
        val json = p.toJson()
        val restored = VoiceEntryProgress.fromJson(json)
        EntryDifficulty.ALL.forEach { d ->
            assertEquals(p.getProgress(d), restored.getProgress(d))
        }
    }

    // ── 记录成绩 ──────────────────────────────────

    @Test
    fun `recordSession updates total and correct counts`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.INTERMEDIATE, correct = 7, total = 10, bestStreak = 4)
        val entry = p.getProgress(EntryDifficulty.INTERMEDIATE)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
    }

    @Test
    fun `recordSession accumulates across multiple sessions`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        p.recordSession(EntryDifficulty.BEGINNER, correct = 4, total = 5, bestStreak = 2)
        val entry = p.getProgress(EntryDifficulty.BEGINNER)
        assertEquals(15, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
    }

    @Test
    fun `recordSession updates best streak only when higher`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.ADVANCED, correct = 9, total = 10, bestStreak = 5)
        assertEquals(5, p.getProgress(EntryDifficulty.ADVANCED).bestStreak)
        p.recordSession(EntryDifficulty.ADVANCED, correct = 8, total = 10, bestStreak = 3)
        assertEquals(5, p.getProgress(EntryDifficulty.ADVANCED).bestStreak) // 不降低
        p.recordSession(EntryDifficulty.ADVANCED, correct = 10, total = 10, bestStreak = 7)
        assertEquals(7, p.getProgress(EntryDifficulty.ADVANCED).bestStreak) // 提高
    }

    @Test
    fun `recordSession updates best accuracy only when higher`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.ADVANCED, correct = 8, total = 10, bestStreak = 0)
        assertEquals(0.8, p.getProgress(EntryDifficulty.ADVANCED).bestAccuracy, 0.001)
        p.recordSession(EntryDifficulty.ADVANCED, correct = 6, total = 10, bestStreak = 0)
        assertEquals(0.8, p.getProgress(EntryDifficulty.ADVANCED).bestAccuracy, 0.001)
        p.recordSession(EntryDifficulty.ADVANCED, correct = 9, total = 10, bestStreak = 0)
        assertEquals(0.9, p.getProgress(EntryDifficulty.ADVANCED).bestAccuracy, 0.001)
    }

    @Test
    fun `recordSession is isolated per difficulty`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 2)
        val advanced = p.getProgress(EntryDifficulty.ADVANCED)
        val intermediate = p.getProgress(EntryDifficulty.INTERMEDIATE)
        assertEquals(0, advanced.totalAnswered)
        assertEquals(0, intermediate.totalAnswered)
    }

    @Test
    fun `recordSession with zero total does not update bestAccuracy`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 0, total = 0, bestStreak = 0)
        assertEquals(0.0, p.getProgress(EntryDifficulty.BEGINNER).bestAccuracy, 0.001)
        // total=0 不应除零
    }

    // ── 累计 / 全局统计 ──────────────────────────────────

    @Test
    fun `cumulativeAccuracy is correct ratio`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 3, total = 4, bestStreak = 1)
        p.recordSession(EntryDifficulty.BEGINNER, correct = 2, total = 4, bestStreak = 1)
        assertEquals(0.625, p.getProgress(EntryDifficulty.BEGINNER).cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy zero when no answers`() {
        val p = VoiceEntryProgress()
        assertEquals(0.0, p.getProgress(EntryDifficulty.BEGINNER).cumulativeAccuracy, 0.001)
    }

    @Test
    fun `overallAccuracy aggregates across difficulties`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 4, total = 5, bestStreak = 0)
        p.recordSession(EntryDifficulty.ADVANCED, correct = 6, total = 10, bestStreak = 0)
        assertEquals(10.0 / 15.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak tracks maximum across difficulties`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(EntryDifficulty.ADVANCED, correct = 9, total = 10, bestStreak = 7)
        assertEquals(7, p.overallBestStreak)
    }

    @Test
    fun `totalSessions counts all sessions`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 1, total = 5, bestStreak = 1)
        p.recordSession(EntryDifficulty.INTERMEDIATE, correct = 1, total = 5, bestStreak = 1)
        p.recordSession(EntryDifficulty.BEGINNER, correct = 1, total = 5, bestStreak = 1)
        assertEquals(3, p.totalSessions)
    }

    // ── JSON 往返 ──────────────────────────────────

    @Test
    fun `toJson fromJson round trip preserves data`() {
        val p = VoiceEntryProgress()
        EntryDifficulty.ALL.forEachIndexed { idx, d ->
            p.recordSession(d, correct = 8 + idx, total = 10 + idx, bestStreak = 4 + idx)
        }
        val json = p.toJson()
        val restored = VoiceEntryProgress.fromJson(json)
        EntryDifficulty.ALL.forEach { d ->
            val orig = p.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
            assertEquals(orig.sessionCount, rest.sessionCount)
        }
    }

    @Test
    fun `toJson produces valid json structure`() {
        val p = VoiceEntryProgress()
        p.recordSession(EntryDifficulty.BEGINNER, correct = 1, total = 2, bestStreak = 1)
        val json = p.toJson()
        assertTrue("JSON should start with {", json.trim().startsWith("{"))
        assertTrue("JSON should contain stats key", json.contains("\"stats\""))
        assertTrue("JSON should contain BEGINNER key", json.contains("\"BEGINNER\""))
    }

    // ── 容错解析 ──────────────────────────────────

    @Test
    fun `fromJson returns empty progress on blank string`() {
        val p = VoiceEntryProgress.fromJson("")
        EntryDifficulty.ALL.forEach { d ->
            assertEquals(0, p.getProgress(d).totalAnswered)
        }
    }

    @Test
    fun `fromJson returns empty progress on malformed json`() {
        val malformed = "{ this is not valid json"
        val p = VoiceEntryProgress.fromJson(malformed)
        EntryDifficulty.ALL.forEach { d ->
            assertEquals(0, p.getProgress(d).totalAnswered)
        }
    }

    @Test
    fun `fromJson returns empty progress on null-ish string`() {
        val p = VoiceEntryProgress.fromJson("null")
        EntryDifficulty.ALL.forEach { d ->
            assertEquals(0, p.getProgress(d).totalAnswered)
        }
    }

    @Test
    fun `fromJson strictly requires all 5 entry fields`() {
        // 严格解析：缺少字段 → entry 被丢弃 → totalAnswered 为 0
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5}}}"""
        val p = VoiceEntryProgress.fromJson(json)
        assertEquals(0, p.getProgress(EntryDifficulty.BEGINNER).totalAnswered)
    }

    @Test
    fun `fromJson parses complete entry`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestStreak":3,"bestAccuracy":0.7000}}}"""
        val p = VoiceEntryProgress.fromJson(json)
        val entry = p.getProgress(EntryDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
        assertEquals(0.7, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson tolerates unknown difficulty keys`() {
        val json = """{"stats":{"UNKNOWN_DIFFICULTY":{"totalAnswered":99,"totalCorrect":99,"sessionCount":1,"bestStreak":9,"bestAccuracy":0.9}}}"""
        val p = VoiceEntryProgress.fromJson(json)
        // 未知难度键被读取但不影响已知难度的统计
        EntryDifficulty.ALL.forEach { d ->
            assertEquals(0, p.getProgress(d).totalAnswered)
        }
    }

    @Test
    fun `fromJson tolerates non-numeric values`() {
        // 含全部 5 个键名，但值为非数字 → 解析回退 0
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5,"totalCorrect":"abc","sessionCount":1,"bestStreak":1,"bestAccuracy":0.5}}}"""
        val p = VoiceEntryProgress.fromJson(json)
        assertEquals(5, p.getProgress(EntryDifficulty.BEGINNER).totalAnswered)
        assertEquals(0, p.getProgress(EntryDifficulty.BEGINNER).totalCorrect)
    }

    @Test
    fun `fromJson tolerates negative values via coerce`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":-2,"sessionCount":1,"bestStreak":3,"bestAccuracy":0.5}}}"""
        val p = VoiceEntryProgress.fromJson(json)
        assertEquals(0, p.getProgress(EntryDifficulty.BEGINNER).totalAnswered)
        assertEquals(0, p.getProgress(EntryDifficulty.BEGINNER).totalCorrect)
    }

    @Test
    fun `fromJson does not crash on truncated json`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":3,"totalCorrect":2,"ses"""
        val p = VoiceEntryProgress.fromJson(json)
        // 不应抛出异常
        assertNotNull(p)
    }

    // ── 数据类相等性 ──────────────────────────────────

    @Test
    fun `progressEntry equals and hashCode`() {
        val e1 = VoiceEntryProgressEntry(totalAnswered = 10, totalCorrect = 7, sessionCount = 1, bestStreak = 3, bestAccuracy = 0.7)
        val e2 = VoiceEntryProgressEntry(totalAnswered = 10, totalCorrect = 7, sessionCount = 1, bestStreak = 3, bestAccuracy = 0.7)
        val e3 = VoiceEntryProgressEntry(totalAnswered = 9, totalCorrect = 7, sessionCount = 1, bestStreak = 3, bestAccuracy = 0.7)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
        assertFalse(e1 == e3)
    }
}
