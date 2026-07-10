package com.pianocompanion.suspendedchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挂留和弦品质听辨训练进度跟踪单元测试。
 *
 * 验证分难度累计、全局汇总、bestAccuracy/bestStreak 不降级、
 * JSON 往返、容错解析（空/损坏/缺失字段/部分 entry）、多次 roundtrip 稳定性。
 */
class SuspendedChordTrainingProgressTest {

    // ── 分难度累计 ────────────────────────────────────────────

    @Test
    fun `recordSession accumulates per difficulty`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 4, 5, 3)

        val entry = progress.getProgress(SuspendedChordDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
    }

    @Test
    fun `different difficulties are independent`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 1, 5, 1)

        assertEquals(5, progress.getProgress(SuspendedChordDifficulty.BEGINNER).totalAnswered)
        assertEquals(5, progress.getProgress(SuspendedChordDifficulty.ADVANCED).totalAnswered)
    }

    @Test
    fun `empty difficulty returns default entry`() {
        val progress = SuspendedChordTrainingProgress()
        val entry = progress.getProgress(SuspendedChordDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── 全局汇总 ──────────────────────────────────────────────

    @Test
    fun `totalAnswered sums across difficulties`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 2, 10, 1)
        assertEquals(15, progress.totalAnswered)
    }

    @Test
    fun `totalCorrect sums across difficulties`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 2, 10, 1)
        assertEquals(5, progress.totalCorrect)
    }

    @Test
    fun `totalSessions sums across difficulties`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 4, 5, 3)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 2, 10, 1)
        assertEquals(3, progress.totalSessions)
    }

    @Test
    fun `overallAccuracy computes correctly`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `overallAccuracy is zero when no answers`() {
        val progress = SuspendedChordTrainingProgress()
        assertEquals(0.0, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak takes max across difficulties`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 7)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 2, 5, 4)
        assertEquals(7, progress.overallBestStreak)
    }

    // ── bestAccuracy / bestStreak 不降级 ──────────────────────

    @Test
    fun `bestStreak does not decrease`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 5, 5, 10)
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 1, 5, 2)
        assertEquals(10, progress.getProgress(SuspendedChordDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `bestAccuracy does not decrease`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 5, 5, 3) // 100%
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 0, 5, 0) // 0%
        assertEquals(1.0, progress.getProgress(SuspendedChordDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy updates when higher`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 1) // 60%
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 5, 5, 3) // 100%
        assertEquals(1.0, progress.getProgress(SuspendedChordDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── JSON 往返 ─────────────────────────────────────────────

    @Test
    fun `json roundtrip preserves data`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.INTERMEDIATE, 4, 5, 3)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 2, 5, 1)

        val json = progress.toJson()
        val restored = SuspendedChordTrainingProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `json roundtrip preserves per-difficulty data`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 7, 10, 5)
        val json = progress.toJson()
        val restored = SuspendedChordTrainingProgress.fromJson(json)
        val entry = restored.getProgress(SuspendedChordDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
        assertEquals(0.7, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `empty progress serializes to valid json`() {
        val progress = SuspendedChordTrainingProgress()
        val json = progress.toJson()
        val restored = SuspendedChordTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ──────────────────────────────────────────────

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val progress = SuspendedChordTrainingProgress.fromJson("")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with garbage returns empty progress`() {
        val progress = SuspendedChordTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with null-like returns empty progress`() {
        val progress = SuspendedChordTrainingProgress.fromJson("null")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with missing stats field returns empty progress`() {
        val progress = SuspendedChordTrainingProgress.fromJson("{\"version\":1}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with missing fields in entry defaults to zero`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5}}}"""
        val progress = SuspendedChordTrainingProgress.fromJson(json)
        val entry = progress.getProgress(SuspendedChordDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson with partial entries preserves valid ones`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5,"totalCorrect":3,"sessionCount":1,"bestStreak":2,"bestAccuracy":0.6000},"INTERMEDIATE":{"totalAnswered":"garbage"}}}"""
        val progress = SuspendedChordTrainingProgress.fromJson(json)
        val beginner = progress.getProgress(SuspendedChordDifficulty.BEGINNER)
        assertEquals(5, beginner.totalAnswered)
        assertEquals(3, beginner.totalCorrect)
    }

    // ── 多次 roundtrip 稳定性 ─────────────────────────────────

    @Test
    fun `multiple roundtrips are stable`() {
        var progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(SuspendedChordDifficulty.ADVANCED, 4, 5, 3)

        for (i in 1..5) {
            val json = progress.toJson()
            progress = SuspendedChordTrainingProgress.fromJson(json)
        }

        assertEquals(10, progress.totalAnswered)
        assertEquals(7, progress.totalCorrect)
        assertEquals(3, progress.overallBestStreak)
    }

    // ── cumulativeAccuracy ───────────────────────────────────

    @Test
    fun `cumulativeAccuracy computes correctly`() {
        val progress = SuspendedChordTrainingProgress()
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 3, 10, 2)
        progress.recordSession(SuspendedChordDifficulty.BEGINNER, 5, 10, 4)
        val entry = progress.getProgress(SuspendedChordDifficulty.BEGINNER)
        assertEquals(0.4, entry.cumulativeAccuracy, 0.001) // 8/20
    }

    @Test
    fun `cumulativeAccuracy is zero when no answers`() {
        val entry = SuspendedChordTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── key 函数 ─────────────────────────────────────────────

    @Test
    fun `key function returns difficulty name`() {
        assertEquals("BEGINNER", SuspendedChordTrainingProgress.key(SuspendedChordDifficulty.BEGINNER))
        assertEquals("INTERMEDIATE", SuspendedChordTrainingProgress.key(SuspendedChordDifficulty.INTERMEDIATE))
        assertEquals("ADVANCED", SuspendedChordTrainingProgress.key(SuspendedChordDifficulty.ADVANCED))
    }
}
