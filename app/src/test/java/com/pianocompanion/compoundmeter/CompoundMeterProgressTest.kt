package com.pianocompanion.compoundmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复合节拍听辨训练进度跟踪单元测试。
 */
class CompoundMeterProgressTest {

    // ── 基础记录 ──────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val progress = CompoundMeterProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates stats`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
        assertEquals(5, progress.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 7, 10, 6)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
        assertEquals(6, progress.overallBestStreak)
    }

    // ── 难度隔离 ──────────────────────────────────

    @Test
    fun `different difficulties are isolated`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(CompoundMeterDifficulty.ADVANCED, 9, 10, 7)

        val beginnerProgress = progress.getProgress(CompoundMeterDifficulty.BEGINNER)
        val advancedProgress = progress.getProgress(CompoundMeterDifficulty.ADVANCED)

        assertEquals(10, beginnerProgress.totalAnswered)
        assertEquals(5, beginnerProgress.totalCorrect)
        assertEquals(3, beginnerProgress.bestStreak)

        assertEquals(10, advancedProgress.totalAnswered)
        assertEquals(9, advancedProgress.totalCorrect)
        assertEquals(7, advancedProgress.bestStreak)
    }

    @Test
    fun `getProgress for unplayed difficulty returns empty entry`() {
        val progress = CompoundMeterProgress()
        val entry = progress.getProgress(CompoundMeterDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak tracks across difficulties`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(CompoundMeterDifficulty.ADVANCED, 9, 10, 8)
        progress.recordSession(CompoundMeterDifficulty.INTERMEDIATE, 7, 10, 5)
        assertEquals(8, progress.overallBestStreak)
    }

    // ── bestAccuracy ──────────────────────────────────

    @Test
    fun `bestAccuracy only increases`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 9, 10, 5)
        assertEquals(0.9, progress.getProgress(CompoundMeterDifficulty.BEGINNER).bestAccuracy, 0.001)
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(0.9, progress.getProgress(CompoundMeterDifficulty.BEGINNER).bestAccuracy, 0.001)
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 10, 10, 10)
        assertEquals(1.0, progress.getProgress(CompoundMeterDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy reflects all sessions`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 2, 10, 1)
        val entry = progress.getProgress(CompoundMeterDifficulty.BEGINNER)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.001)
    }

    // ── JSON 序列化 ──────────────────────────────────

    @Test
    fun `JSON round trip preserves data`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(CompoundMeterDifficulty.ADVANCED, 9, 10, 7)

        val json = progress.toJson()
        val restored = CompoundMeterProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)

        val originalBeginner = progress.getProgress(CompoundMeterDifficulty.BEGINNER)
        val restoredBeginner = restored.getProgress(CompoundMeterDifficulty.BEGINNER)
        assertEquals(originalBeginner.totalAnswered, restoredBeginner.totalAnswered)
        assertEquals(originalBeginner.totalCorrect, restoredBeginner.totalCorrect)
        assertEquals(originalBeginner.bestStreak, restoredBeginner.bestStreak)
        assertEquals(originalBeginner.bestAccuracy, restoredBeginner.bestAccuracy, 0.001)
    }

    @Test
    fun `JSON round trip with all difficulties`() {
        val progress = CompoundMeterProgress()
        CompoundMeterDifficulty.ALL.forEach { d ->
            progress.recordSession(d, 7, 10, 4)
        }
        val json = progress.toJson()
        val restored = CompoundMeterProgress.fromJson(json)
        CompoundMeterDifficulty.ALL.forEach { d ->
            val orig = progress.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.sessionCount, rest.sessionCount)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }

    // ── 容错解析 ──────────────────────────────────

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val progress = CompoundMeterProgress.fromJson("")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with invalid JSON returns empty progress`() {
        val progress = CompoundMeterProgress.fromJson("not valid json")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with corrupted JSON returns empty progress`() {
        val progress = CompoundMeterProgress.fromJson("{\"stats\":{BROKEN")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with partial data still parses valid entries`() {
        // Construct JSON with one valid and one invalid entry
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8000},"BROKEN":{}}}"""
        val progress = CompoundMeterProgress.fromJson(json)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
    }

    // ── 严格字段校验 ──────────────────────────────────

    @Test
    fun `fromJson rejects entry missing required fields`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5}}}"""
        val progress = CompoundMeterProgress.fromJson(json)
        // Missing bestAccuracy → entry rejected
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with negative values coerces to zero`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":-3,"sessionCount":1,"bestStreak":-1,"bestAccuracy":-0.5}}}"""
        val progress = CompoundMeterProgress.fromJson(json)
        val entry = progress.getProgress(CompoundMeterDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson with valid negative session count coerces to zero`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":-1,"bestStreak":5,"bestAccuracy":0.8}}}"""
        val progress = CompoundMeterProgress.fromJson(json)
        val entry = progress.getProgress(CompoundMeterDifficulty.BEGINNER)
        assertEquals(0, entry.sessionCount)
    }

    // ── 多难度序列化 ──────────────────────────────────

    @Test
    fun `toJson produces valid JSON structure`() {
        val progress = CompoundMeterProgress()
        progress.recordSession(CompoundMeterDifficulty.BEGINNER, 5, 10, 3)
        val json = progress.toJson()
        assertTrue("JSON should contain 'stats' key", json.contains("\"stats\""))
        assertTrue("JSON should contain 'BEGINNER' key", json.contains("\"BEGINNER\""))
        assertTrue("JSON should contain 'totalAnswered'", json.contains("\"totalAnswered\""))
        assertTrue("JSON should contain 'bestAccuracy'", json.contains("\"bestAccuracy\""))
    }
}
