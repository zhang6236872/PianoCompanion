package com.pianocompanion.timbrebrightness

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * [TimbreBrightnessProgress] 单元测试。
 *
 * 验证跨会话进度跟踪、难度隔离、JSON 往返、容错解析、严格字段校验、负数回退等。
 */
class TimbreBrightnessProgressTest {

    private lateinit var progress: TimbreBrightnessProgress

    @Before
    fun setUp() {
        progress = TimbreBrightnessProgress()
    }

    // ── 基础记录 ──────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession updates totals`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
        assertEquals(5, progress.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates across sessions`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 7, 10, 6)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
        assertEquals(6, progress.overallBestStreak)
    }

    // ── 难度隔离 ──────────────────────────────────

    @Test
    fun `different difficulties are isolated`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(TimbreBrightnessDifficulty.ADVANCED, 4, 10, 7)

        val beginnerProgress = progress.getProgress(TimbreBrightnessDifficulty.BEGINNER)
        val advancedProgress = progress.getProgress(TimbreBrightnessDifficulty.ADVANCED)

        assertEquals(10, beginnerProgress.totalAnswered)
        assertEquals(8, beginnerProgress.totalCorrect)
        assertEquals(3, beginnerProgress.bestStreak)

        assertEquals(10, advancedProgress.totalAnswered)
        assertEquals(4, advancedProgress.totalCorrect)
        assertEquals(7, advancedProgress.bestStreak)
    }

    @Test
    fun `bestStreak takes maximum across sessions`() {
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 5, 10, 4)
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 6, 10, 2)
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 7, 10, 9)

        assertEquals(9, progress.getProgress(TimbreBrightnessDifficulty.INTERMEDIATE).bestStreak)
    }

    @Test
    fun `bestAccuracy takes maximum session accuracy`() {
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 5, 10, 0) // 0.5
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 9, 10, 0) // 0.9
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 3, 10, 0) // 0.3

        assertEquals(0.9, progress.getProgress(TimbreBrightnessDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
    }

    @Test
    fun `getProgress returns empty entry for unseen difficulty`() {
        val entry = progress.getProgress(TimbreBrightnessDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────

    @Test
    fun `JSON round trip preserves data`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(TimbreBrightnessDifficulty.ADVANCED, 3, 7, 2)

        val json = progress.toJson()
        val restored = TimbreBrightnessProgress.fromJson(json)

        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)

        val origBeginner = progress.getProgress(TimbreBrightnessDifficulty.BEGINNER)
        val restBeginner = restored.getProgress(TimbreBrightnessDifficulty.BEGINNER)
        assertEquals(origBeginner.totalAnswered, restBeginner.totalAnswered)
        assertEquals(origBeginner.totalCorrect, restBeginner.totalCorrect)
        assertEquals(origBeginner.bestStreak, restBeginner.bestStreak)
        assertEquals(origBeginner.bestAccuracy, restBeginner.bestAccuracy, 0.001)
    }

    @Test
    fun `empty progress JSON round trip`() {
        val json = progress.toJson()
        val restored = TimbreBrightnessProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    // ── 容错解析 ──────────────────────────────────

    @Test
    fun `invalid JSON returns empty progress`() {
        val restored = TimbreBrightnessProgress.fromJson("not valid json")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `empty string returns empty progress`() {
        val restored = TimbreBrightnessProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `malformed JSON with missing stats returns empty`() {
        val restored = TimbreBrightnessProgress.fromJson("{\"foo\":\"bar\"}")
        assertEquals(0, restored.totalAnswered)
    }

    // ── 严格字段校验 ──────────────────────────────────

    @Test
    fun `entry with missing field is rejected`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5}}}"""
        val restored = TimbreBrightnessProgress.fromJson(json)
        // 缺少 bestAccuracy 字段 → 该 entry 被拒绝
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `entry with all 5 fields is accepted`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8000}}}"""
        val restored = TimbreBrightnessProgress.fromJson(json)
        assertEquals(10, restored.totalAnswered)
        assertEquals(8, restored.totalCorrect)
    }

    @Test
    fun `entry missing totalCorrect is rejected`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8}}}"""
        val restored = TimbreBrightnessProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 负数回退 ──────────────────────────────────

    @Test
    fun `negative values fall back to zero`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":-3,"sessionCount":-1,"bestStreak":-2,"bestAccuracy":-0.5}}}"""
        val restored = TimbreBrightnessProgress.fromJson(json)
        val entry = restored.getProgress(TimbreBrightnessDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── 全局聚合 ──────────────────────────────────

    @Test
    fun `overallBestStreak across difficulties`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(TimbreBrightnessDifficulty.INTERMEDIATE, 6, 10, 8)
        progress.recordSession(TimbreBrightnessDifficulty.ADVANCED, 4, 10, 2)
        assertEquals(8, progress.overallBestStreak)
    }

    @Test
    fun `totalSessions sums across difficulties`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(TimbreBrightnessDifficulty.ADVANCED, 5, 10, 3)
        assertEquals(3, progress.totalSessions)
    }

    @Test
    fun `cumulativeAccuracy for entry`() {
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 3, 10, 0)
        progress.recordSession(TimbreBrightnessDifficulty.BEGINNER, 5, 10, 0)
        val entry = progress.getProgress(TimbreBrightnessDifficulty.BEGINNER)
        assertEquals(0.4, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `unknown keys in JSON are ignored`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8000,"extraField":"ignored"}}}"""
        val restored = TimbreBrightnessProgress.fromJson(json)
        assertEquals(10, restored.totalAnswered)
    }

    @Test
    fun `multiple difficulties in JSON are preserved`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8000},"ADVANCED":{"totalAnswered":7,"totalCorrect":3,"sessionCount":1,"bestStreak":2,"bestAccuracy":0.4286}}}"""
        val restored = TimbreBrightnessProgress.fromJson(json)
        assertEquals(17, restored.totalAnswered)
        assertEquals(11, restored.totalCorrect)
        assertEquals(2, restored.totalSessions)
    }
}
