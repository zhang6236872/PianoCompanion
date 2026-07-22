package com.pianocompanion.harmonicseries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泛音列辨识训练进度跟踪单元测试。
 */
class HarmonicSeriesProgressTest {

    // ── 累计统计 ──────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val progress = HarmonicSeriesProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates counts`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(HarmonicDifficulty.BEGINNER, 7, 10, 4)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
    }

    @Test
    fun `overall accuracy reflects all difficulties`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(HarmonicDifficulty.ADVANCED, 4, 10, 3)
        assertEquals(0.6, progress.overallAccuracy, 0.001) // 12/20
    }

    // ── 难度隔离 ──────────────────────────────────

    @Test
    fun `stats are isolated by difficulty`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(HarmonicDifficulty.ADVANCED, 3, 10, 7)

        val beginner = progress.getProgress(HarmonicDifficulty.BEGINNER)
        val advanced = progress.getProgress(HarmonicDifficulty.ADVANCED)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(3, beginner.bestStreak)

        assertEquals(10, advanced.totalAnswered)
        assertEquals(3, advanced.totalCorrect)
        assertEquals(7, advanced.bestStreak)
    }

    @Test
    fun `overall best streak is maximum across difficulties`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(HarmonicDifficulty.ADVANCED, 3, 10, 8)
        progress.recordSession(HarmonicDifficulty.INTERMEDIATE, 4, 10, 5)
        assertEquals(8, progress.overallBestStreak)
    }

    // ── JSON 往返 ──────────────────────────────────

    @Test
    fun `toJson and fromJson roundtrip preserves data`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(HarmonicDifficulty.ADVANCED, 7, 20, 8)

        val json = progress.toJson()
        val restored = HarmonicSeriesProgress.fromJson(json)

        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)

        val origBeginner = progress.getProgress(HarmonicDifficulty.BEGINNER)
        val restBeginner = restored.getProgress(HarmonicDifficulty.BEGINNER)
        assertEquals(origBeginner.totalAnswered, restBeginner.totalAnswered)
        assertEquals(origBeginner.totalCorrect, restBeginner.totalCorrect)
        assertEquals(origBeginner.bestStreak, restBeginner.bestStreak)
    }

    @Test
    fun `fromJson with empty json returns empty progress`() {
        val progress = HarmonicSeriesProgress.fromJson("{}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with garbage returns empty progress`() {
        val progress = HarmonicSeriesProgress.fromJson("not valid json at all")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with corrupted json returns empty progress`() {
        val progress = HarmonicSeriesProgress.fromJson("{\"stats\":{\"BEGINNER\":{\"totalAnswered\": BROKEN}}}")
        assertEquals(0, progress.totalAnswered)
    }

    // ── 严格字段校验 ──────────────────────────────────

    @Test
    fun `missing field rejects entry`() {
        // 缺少 bestAccuracy 字段
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":5,"sessionCount":1,"bestStreak":3}}}"""
        val progress = HarmonicSeriesProgress.fromJson(json)
        // 缺字段 → 不计入
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `negative numbers are clamped to zero`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":-3,"sessionCount":-1,"bestStreak":-2,"bestAccuracy":-0.5}}}"""
        val progress = HarmonicSeriesProgress.fromJson(json)
        val entry = progress.getProgress(HarmonicDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── bestAccuracy ──────────────────────────────────

    @Test
    fun `bestAccuracy only increases`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 9, 10, 3) // 90%
        assertEquals(0.9, progress.getProgress(HarmonicDifficulty.BEGINNER).bestAccuracy, 0.001)
        progress.recordSession(HarmonicDifficulty.BEGINNER, 5, 10, 2) // 50%
        // bestAccuracy 不应该下降
        assertEquals(0.9, progress.getProgress(HarmonicDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy reflects all sessions`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 9, 10, 3) // 90%
        progress.recordSession(HarmonicDifficulty.BEGINNER, 5, 10, 2) // 50%
        val entry = progress.getProgress(HarmonicDifficulty.BEGINNER)
        assertEquals(0.7, entry.cumulativeAccuracy, 0.001) // 14/20
    }

    // ── 多难度序列化 ──────────────────────────────────

    @Test
    fun `multiple difficulties serialize and restore correctly`() {
        val progress = HarmonicSeriesProgress()
        progress.recordSession(HarmonicDifficulty.BEGINNER, 1, 2, 1)
        progress.recordSession(HarmonicDifficulty.INTERMEDIATE, 2, 4, 2)
        progress.recordSession(HarmonicDifficulty.ADVANCED, 3, 6, 3)

        val json = progress.toJson()
        val restored = HarmonicSeriesProgress.fromJson(json)

        HarmonicDifficulty.ALL.forEach { difficulty ->
            val orig = progress.getProgress(difficulty)
            val rest = restored.getProgress(difficulty)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.bestStreak, rest.bestStreak)
        }
    }

    // ── 未知键容错 ──────────────────────────────────

    @Test
    fun `unknown difficulty key is ignored`() {
        val json = """{"stats":{"UNKNOWN_DIFF":{"totalAnswered":99,"totalCorrect":50,"sessionCount":5,"bestStreak":10,"bestAccuracy":0.5}}}"""
        val progress = HarmonicSeriesProgress.fromJson(json)
        // 未知键的条目也会被解析存储（键名字符串匹配），但不会与任何 HarmonicDifficulty 对应
        // 它会被存储在 stats map 中但 getProgress 不会返回它
        assertEquals(0, progress.getProgress(HarmonicDifficulty.BEGINNER).totalAnswered)
    }
}
