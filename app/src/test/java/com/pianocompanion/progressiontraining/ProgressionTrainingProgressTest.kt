package com.pianocompanion.progressiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦进行听辨训练进度跟踪单元测试。
 *
 * 验证分难度累计、全局汇总、JSON 往返、容错解析。
 */
class ProgressionTrainingProgressTest {

    // ── 分难度累计 ──────────────────────────────────────────

    @Test
    fun `recordSession accumulates per difficulty`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ProgressionDifficulty.BEGINNER, 7, 10, 6)
        val entry = p.getProgress(ProgressionDifficulty.BEGINNER)
        assertEquals(20, entry.totalAnswered)
        assertEquals(15, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(6, entry.bestStreak)
    }

    @Test
    fun `different difficulties tracked separately`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 5, 3)
        p.recordSession(ProgressionDifficulty.ADVANCED, 3, 5, 2)
        val beg = p.getProgress(ProgressionDifficulty.BEGINNER)
        val adv = p.getProgress(ProgressionDifficulty.ADVANCED)
        assertEquals(5, beg.totalAnswered)
        assertEquals(5, beg.totalCorrect)
        assertEquals(5, adv.totalAnswered)
        assertEquals(3, adv.totalCorrect)
    }

    // ── 全局汇总 ────────────────────────────────────────────

    @Test
    fun `totalAnswered sums across difficulties`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ProgressionDifficulty.INTERMEDIATE, 4, 8, 2)
        p.recordSession(ProgressionDifficulty.ADVANCED, 3, 5, 4)
        assertEquals(23, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
    }

    @Test
    fun `overallAccuracy computes correctly`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 8, 10, 0)
        p.recordSession(ProgressionDifficulty.ADVANCED, 2, 10, 0)
        assertEquals(0.5, p.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak takes maximum`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 10, 7)
        p.recordSession(ProgressionDifficulty.ADVANCED, 5, 10, 3)
        assertEquals(7, p.overallBestStreak)
    }

    // ── bestAccuracy / bestStreak 不降级 ────────────────────

    @Test
    fun `bestAccuracy does not decrease`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 9, 10, 0) // 0.9
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 10, 0)  // 0.5
        assertEquals(0.9, p.getProgress(ProgressionDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `bestStreak does not decrease`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 10, 8)
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(8, p.getProgress(ProgressionDifficulty.BEGINNER).bestStreak)
    }

    // ── 空进度 ──────────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = ProgressionTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `getProgress for unrecorded difficulty returns empty entry`() {
        val p = ProgressionTrainingProgress()
        val entry = p.getProgress(ProgressionDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.sessionCount)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `json roundtrip preserves data`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ProgressionDifficulty.ADVANCED, 3, 7, 4)
        val json = p.toJson()
        val restored = ProgressionTrainingProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `json roundtrip for all difficulties`() {
        val p = ProgressionTrainingProgress()
        for (d in ProgressionDifficulty.ALL) {
            p.recordSession(d, 5, 10, 3)
        }
        val json = p.toJson()
        val restored = ProgressionTrainingProgress.fromJson(json)
        for (d in ProgressionDifficulty.ALL) {
            val orig = p.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }

    // ── 容错解析 ────────────────────────────────────────────

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val p = ProgressionTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with corrupted json returns empty progress`() {
        val p = ProgressionTrainingProgress.fromJson("{broken json!!!")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with missing fields returns partial data`() {
        // Partial JSON: only one field present
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5}}}"""
        val p = ProgressionTrainingProgress.fromJson(json)
        // Should parse totalAnswered=5, others default to 0
        val entry = p.getProgress(ProgressionDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    @Test
    fun `fromJson with null json returns empty`() {
        val p = ProgressionTrainingProgress.fromJson("null")
        assertEquals(0, p.totalAnswered)
    }

    // ── ProgressEntry 独立序列化 ────────────────────────────

    @Test
    fun `progress entry json roundtrip`() {
        val entry = ProgressionTrainingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.875
        )
        val json = entry.toJson()
        val restored = ProgressionTrainingProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `progress entry fromJson with null returns null`() {
        assertEquals(null, ProgressionTrainingProgressEntry.fromJson("null"))
    }

    @Test
    fun `progress entry fromJson with empty returns null`() {
        assertEquals(null, ProgressionTrainingProgressEntry.fromJson(""))
    }

    @Test
    fun `cumulativeAccuracy computes correctly`() {
        val entry = ProgressionTrainingProgressEntry(
            totalAnswered = 20,
            totalCorrect = 15
        )
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy is 0 when no answers`() {
        val entry = ProgressionTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── 多次 roundtrip 稳定性 ───────────────────────────────

    @Test
    fun `multiple roundtrips are stable`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ProgressionDifficulty.INTERMEDIATE, 6, 10, 4)
        var current = p
        repeat(5) {
            val json = current.toJson()
            current = ProgressionTrainingProgress.fromJson(json)
        }
        assertEquals(p.totalAnswered, current.totalAnswered)
        assertEquals(p.totalCorrect, current.totalCorrect)
        assertEquals(p.overallBestStreak, current.overallBestStreak)
    }

    @Test
    fun `key consistency`() {
        assertEquals("BEGINNER", ProgressionTrainingProgress.key(ProgressionDifficulty.BEGINNER))
        assertEquals("INTERMEDIATE", ProgressionTrainingProgress.key(ProgressionDifficulty.INTERMEDIATE))
        assertEquals("ADVANCED", ProgressionTrainingProgress.key(ProgressionDifficulty.ADVANCED))
    }

    // ── totalSessions ───────────────────────────────────────

    @Test
    fun `totalSessions counts all sessions`() {
        val p = ProgressionTrainingProgress()
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 5, 1)
        p.recordSession(ProgressionDifficulty.BEGINNER, 5, 5, 1)
        p.recordSession(ProgressionDifficulty.ADVANCED, 5, 5, 1)
        assertEquals(3, p.totalSessions)
    }
}
