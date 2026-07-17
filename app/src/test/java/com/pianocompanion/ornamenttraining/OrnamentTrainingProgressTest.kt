package com.pianocompanion.ornamenttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装饰音辨识训练进度跟踪单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：累计统计、难度隔离、JSON 往返一致性、容错解析、全局聚合。
 */
class OrnamentTrainingProgressTest {

    // ── 累计统计 ──────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = OrnamentTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `record session accumulates totals`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(OrnamentDifficulty.BEGINNER, 6, 10, 3)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.0001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `best streak tracks maximum across sessions`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.ADVANCED, 5, 5, 7)
        p.recordSession(OrnamentDifficulty.ADVANCED, 5, 5, 3)
        p.recordSession(OrnamentDifficulty.ADVANCED, 5, 5, 12)
        assertEquals(12, p.overallBestStreak)
    }

    @Test
    fun `best accuracy tracks maximum session accuracy`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.INTERMEDIATE, 7, 10, 3) // 0.7
        p.recordSession(OrnamentDifficulty.INTERMEDIATE, 9, 10, 4) // 0.9
        val entry = p.getProgress(OrnamentDifficulty.INTERMEDIATE)
        assertEquals(0.9, entry.bestAccuracy, 0.0001)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `stats are isolated by difficulty`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(OrnamentDifficulty.ADVANCED, 3, 5, 2)
        val beginner = p.getProgress(OrnamentDifficulty.BEGINNER)
        val advanced = p.getProgress(OrnamentDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(5, advanced.totalAnswered)
        assertEquals(3, advanced.totalCorrect)
    }

    @Test
    fun `global totals aggregate across difficulties`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(OrnamentDifficulty.INTERMEDIATE, 6, 10, 4)
        p.recordSession(OrnamentDifficulty.ADVANCED, 3, 5, 2)
        assertEquals(3, p.totalSessions)
        assertEquals(25, p.totalAnswered)
        assertEquals(17, p.totalCorrect)
    }

    @Test
    fun `getProgress for unknown difficulty returns empty entry`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        val advanced = p.getProgress(OrnamentDifficulty.ADVANCED)
        assertEquals(0, advanced.totalAnswered)
        assertEquals(0.0, advanced.cumulativeAccuracy, 0.0001)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `json round trip preserves stats`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(OrnamentDifficulty.ADVANCED, 3, 5, 2)
        val json = p.toJson()
        val restored = OrnamentTrainingProgress.fromJson(json)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `json round trip preserves per-difficulty entries`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(OrnamentDifficulty.INTERMEDIATE, 7, 10, 4)
        val restored = OrnamentTrainingProgress.fromJson(p.toJson())
        val beginner = restored.getProgress(OrnamentDifficulty.BEGINNER)
        val intermediate = restored.getProgress(OrnamentDifficulty.INTERMEDIATE)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(5, beginner.bestStreak)
        assertEquals(0.8, beginner.bestAccuracy, 0.0001)
        assertEquals(10, intermediate.totalAnswered)
        assertEquals(7, intermediate.totalCorrect)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `fromJson with corrupted json returns empty progress`() {
        val p = OrnamentTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val p = OrnamentTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with missing fields defaults to zero`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10}}}"
        val p = OrnamentTrainingProgress.fromJson(json)
        val entry = p.getProgress(OrnamentDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
    }

    @Test
    fun `fromJson ignores unknown difficulty keys gracefully`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":8,\"sessionCount\":1,\"bestStreak\":3,\"bestAccuracy\":0.8},\"UNKNOWN_DIFF\":{\"totalAnswered\":5}}}"
        val p = OrnamentTrainingProgress.fromJson(json)
        assertEquals(10, p.getProgress(OrnamentDifficulty.BEGINNER).totalAnswered)
    }

    // ── 空会话 ──────────────────────────────────────────

    @Test
    fun `record session with zero total does not update best accuracy`() {
        val p = OrnamentTrainingProgress()
        p.recordSession(OrnamentDifficulty.BEGINNER, 0, 0, 0)
        val entry = p.getProgress(OrnamentDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.bestAccuracy, 0.0001)
        assertEquals(1, entry.sessionCount)
    }

    @Test
    fun `cumulative accuracy computes correctly for entry`() {
        val entry = OrnamentTrainingProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `entry with zero answered has zero accuracy`() {
        val entry = OrnamentTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    // ── 所有难度 ──────────────────────────────────────────

    @Test
    fun `all difficulties can be recorded independently`() {
        val p = OrnamentTrainingProgress()
        OrnamentDifficulty.ALL.forEach { d ->
            p.recordSession(d, 5, 10, 3)
        }
        assertEquals(OrnamentDifficulty.ALL.size, p.totalSessions)
        assertEquals(OrnamentDifficulty.ALL.size * 10, p.totalAnswered)
        OrnamentDifficulty.ALL.forEach { d ->
            assertEquals(10, p.getProgress(d).totalAnswered)
        }
        // JSON 往返
        val restored = OrnamentTrainingProgress.fromJson(p.toJson())
        OrnamentDifficulty.ALL.forEach { d ->
            assertEquals(10, restored.getProgress(d).totalAnswered)
        }
    }
}
