package com.pianocompanion.thirteenthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十三和弦色彩听辨训练进度跟踪单元测试。
 *
 * 验证分难度累计、全局汇总、bestAccuracy/bestStreak 不降级、JSON 往返、容错解析等。
 */
class ThirteenthChordTrainingProgressTest {

    // ── 分难度累计 ──────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = ThirteenthChordTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession adds answered count`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(10, p.totalAnswered)
    }

    @Test
    fun `recordSession adds correct count`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(5, p.totalCorrect)
    }

    @Test
    fun `recordSession increments session count`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 8, 2)
        assertEquals(2, p.totalSessions)
    }

    @Test
    fun `recordSession accumulates across sessions`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 8, 2)
        assertEquals(18, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
    }

    @Test
    fun `different difficulties tracked separately`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ThirteenthChordDifficulty.ADVANCED, 2, 5, 1)
        val beginner = p.getProgress(ThirteenthChordDifficulty.BEGINNER)
        val advanced = p.getProgress(ThirteenthChordDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, advanced.totalAnswered)
    }

    // ── 全局汇总 ──────────────────────────────────────────────

    @Test
    fun `overallAccuracy computes correctly`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 10, 0)
        assertEquals(0.3, p.overallAccuracy, 0.001)
    }

    @Test
    fun `overallAccuracy combines across difficulties`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 10, 0)
        p.recordSession(ThirteenthChordDifficulty.INTERMEDIATE, 7, 10, 0)
        assertEquals(10, p.totalCorrect)
        assertEquals(20, p.totalAnswered)
        assertEquals(0.5, p.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak finds max across difficulties`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 10, 5)
        p.recordSession(ThirteenthChordDifficulty.ADVANCED, 2, 5, 8)
        assertEquals(8, p.overallBestStreak)
    }

    // ── bestAccuracy 不降级 ──────────────────────────────────

    @Test
    fun `bestAccuracy does not decrease`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 9, 10, 5)
        assertEquals(0.9, p.getProgress(ThirteenthChordDifficulty.BEGINNER).bestAccuracy, 0.001)
        // 下一次会话准确率更低
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 1, 10, 1)
        assertEquals(
            "bestAccuracy 不应下降",
            0.9,
            p.getProgress(ThirteenthChordDifficulty.BEGINNER).bestAccuracy,
            0.001
        )
    }

    @Test
    fun `bestAccuracy updates when higher`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 2)
        assertEquals(0.5, p.getProgress(ThirteenthChordDifficulty.BEGINNER).bestAccuracy, 0.001)
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 10, 10, 5)
        assertEquals(
            "bestAccuracy 应更新为 1.0",
            1.0,
            p.getProgress(ThirteenthChordDifficulty.BEGINNER).bestAccuracy,
            0.001
        )
    }

    // ── bestStreak 不降级 ──────────────────────────────────────

    @Test
    fun `bestStreak does not decrease`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.ADVANCED, 5, 10, 8)
        assertEquals(8, p.getProgress(ThirteenthChordDifficulty.ADVANCED).bestStreak)
        p.recordSession(ThirteenthChordDifficulty.ADVANCED, 1, 5, 2)
        assertEquals(
            "bestStreak 不应下降",
            8,
            p.getProgress(ThirteenthChordDifficulty.ADVANCED).bestStreak
        )
    }

    @Test
    fun `bestStreak updates when higher`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 10, 3)
        assertEquals(3, p.getProgress(ThirteenthChordDifficulty.BEGINNER).bestStreak)
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 7)
        assertEquals(
            "bestStreak 应更新为 7",
            7,
            p.getProgress(ThirteenthChordDifficulty.BEGINNER).bestStreak
        )
    }

    // ── 累计准确率 ──────────────────────────────────────────

    @Test
    fun `cumulativeAccuracy computes from totals`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 3, 10, 2)
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 4)
        val entry = p.getProgress(ThirteenthChordDifficulty.BEGINNER)
        assertEquals(8.0 / 20.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy is 0 when no answers`() {
        val p = ThirteenthChordTrainingProgress()
        assertEquals(0.0, p.getProgress(ThirteenthChordDifficulty.BEGINNER).cumulativeAccuracy, 0.001)
    }

    // ── 零答题安全 ──────────────────────────────────────────

    @Test
    fun `recordSession with zero total is safe`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0, p.totalAnswered)
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `getProgress for non-existent difficulty returns empty entry`() {
        val p = ThirteenthChordTrainingProgress()
        val entry = p.getProgress(ThirteenthChordDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    // ── JSON 往返 ────────────────────────────────────────────

    @Test
    fun `json roundtrip preserves data`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ThirteenthChordDifficulty.INTERMEDIATE, 7, 10, 5)
        p.recordSession(ThirteenthChordDifficulty.ADVANCED, 3, 8, 2)

        val json = p.toJson()
        val restored = ThirteenthChordTrainingProgress.fromJson(json)

        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        for (difficulty in ThirteenthChordDifficulty.ALL) {
            val orig = p.getProgress(difficulty)
            val rest = restored.getProgress(difficulty)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.sessionCount, rest.sessionCount)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `json roundtrip with single difficulty`() {
        val p = ThirteenthChordTrainingProgress()
        p.recordSession(ThirteenthChordDifficulty.BEGINNER, 8, 10, 4)
        val json = p.toJson()
        val restored = ThirteenthChordTrainingProgress.fromJson(json)
        assertEquals(10, restored.getProgress(ThirteenthChordDifficulty.BEGINNER).totalAnswered)
        assertEquals(8, restored.getProgress(ThirteenthChordDifficulty.BEGINNER).totalCorrect)
    }

    // ── 容错解析 ──────────────────────────────────────────────

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val p = ThirteenthChordTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with garbage string returns empty progress`() {
        val p = ThirteenthChordTrainingProgress.fromJson("not json at all")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with null-like string returns empty progress`() {
        val p = ThirteenthChordTrainingProgress.fromJson("{}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with missing stats key returns empty progress`() {
        val p = ThirteenthChordTrainingProgress.fromJson("{\"foo\":123}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with partially corrupted entry skips bad entry`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":5,"sessionCount":1,"bestStreak":3,"bestAccuracy":0.5000},"INTERMEDIATE":garbage}}"""
        val p = ThirteenthChordTrainingProgress.fromJson(json)
        // BEGINNER should parse successfully
        assertEquals(10, p.getProgress(ThirteenthChordDifficulty.BEGINNER).totalAnswered)
        // INTERMEDIATE should be skipped (garbage)
        assertEquals(0, p.getProgress(ThirteenthChordDifficulty.INTERMEDIATE).totalAnswered)
    }

    @Test
    fun `fromJson with missing fields in entry uses defaults`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"sessionCount":1}}}"""
        val p = ThirteenthChordTrainingProgress.fromJson(json)
        val entry = p.getProgress(ThirteenthChordDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── ProgressEntry JSON ────────────────────────────────────

    @Test
    fun `progressEntry json roundtrip`() {
        val entry = ThirteenthChordTrainingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 28,
            sessionCount = 5,
            bestStreak = 8,
            bestAccuracy = 0.8750
        )
        val json = entry.toJson()
        val restored = ThirteenthChordTrainingProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `progressEntry fromJson with non-object returns null`() {
        val result = ThirteenthChordTrainingProgressEntry.fromJson("not an object")
        assertEquals(null, result)
    }

    @Test
    fun `progressEntry fromJson with empty object returns defaults`() {
        val result = ThirteenthChordTrainingProgressEntry.fromJson("{}")
        assertEquals(0, result!!.totalAnswered)
        assertEquals(0, result.totalCorrect)
        assertEquals(0, result.sessionCount)
    }

    @Test
    fun `progressEntry cumulativeAccuracy correct`() {
        val entry = ThirteenthChordTrainingProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    // ── key mapping ────────────────────────────────────────────

    @Test
    fun `key mapping uses enum name`() {
        assertEquals("BEGINNER", ThirteenthChordTrainingProgress.key(ThirteenthChordDifficulty.BEGINNER))
        assertEquals("INTERMEDIATE", ThirteenthChordTrainingProgress.key(ThirteenthChordDifficulty.INTERMEDIATE))
        assertEquals("ADVANCED", ThirteenthChordTrainingProgress.key(ThirteenthChordDifficulty.ADVANCED))
    }
}
