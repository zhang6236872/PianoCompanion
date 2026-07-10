package com.pianocompanion.ninthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 九和弦色彩听辨训练进度跟踪模型单元测试。
 */
class NinthChordTrainingProgressTest {

    private lateinit var progress: NinthChordTrainingProgress

    @Before
    fun setUp() {
        progress = NinthChordTrainingProgress()
    }

    // ── 基本记录 ──────────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates correctly`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 3)
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(3, progress.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 5, 10, 2)
        progress.recordSession(NinthChordDifficulty.BEGINNER, 7, 10, 4)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(4, progress.overallBestStreak)
    }

    // ── 分难度统计 ──────────────────────────────────────────

    @Test
    fun `different difficulties are tracked separately`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 5, 10, 2)
        progress.recordSession(NinthChordDifficulty.ADVANCED, 3, 10, 5)

        val beginner = progress.getProgress(NinthChordDifficulty.BEGINNER)
        val advanced = progress.getProgress(NinthChordDifficulty.ADVANCED)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(2, beginner.bestStreak)

        assertEquals(10, advanced.totalAnswered)
        assertEquals(3, advanced.totalCorrect)
        assertEquals(5, advanced.bestStreak)
    }

    @Test
    fun `getProgress returns empty for unrecorded difficulty`() {
        val entry = progress.getProgress(NinthChordDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── bestStreak/bestAccuracy 不降级 ─────────────────────

    @Test
    fun `bestStreak does not decrease`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 10, 10, 5)
        progress.recordSession(NinthChordDifficulty.BEGINNER, 1, 10, 2)
        val entry = progress.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `bestAccuracy does not decrease`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 9, 10, 3)
        progress.recordSession(NinthChordDifficulty.BEGINNER, 2, 10, 1)
        val entry = progress.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy tracks session accuracy`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 7, 10, 2)
        val entry = progress.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(0.7, entry.bestAccuracy, 0.001)

        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 3)
        val entry2 = progress.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(0.8, entry2.bestAccuracy, 0.001)
    }

    // ── 全局汇总 ──────────────────────────────────────────────

    @Test
    fun `overallAccuracy aggregates across difficulties`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 2)
        progress.recordSession(NinthChordDifficulty.INTERMEDIATE, 6, 10, 3)
        assertEquals(20, progress.totalAnswered)
        assertEquals(14, progress.totalCorrect)
        assertEquals(0.7, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak is max across difficulties`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 10, 10, 3)
        progress.recordSession(NinthChordDifficulty.ADVANCED, 5, 10, 7)
        assertEquals(7, progress.overallBestStreak)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `json roundtrip preserves data`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(NinthChordDifficulty.ADVANCED, 5, 12, 7)

        val json = progress.toJson()
        val restored = NinthChordTrainingProgress.fromJson(json)

        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `json roundtrip preserves per-difficulty stats`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(NinthChordDifficulty.INTERMEDIATE, 6, 10, 4)

        val json = progress.toJson()
        val restored = NinthChordTrainingProgress.fromJson(json)

        val origBeginner = progress.getProgress(NinthChordDifficulty.BEGINNER)
        val restBeginner = restored.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(origBeginner.totalAnswered, restBeginner.totalAnswered)
        assertEquals(origBeginner.totalCorrect, restBeginner.totalCorrect)
        assertEquals(origBeginner.sessionCount, restBeginner.sessionCount)
        assertEquals(origBeginner.bestStreak, restBeginner.bestStreak)
        assertEquals(origBeginner.bestAccuracy, restBeginner.bestAccuracy, 0.001)
    }

    @Test
    fun `empty progress json roundtrip`() {
        val json = progress.toJson()
        val restored = NinthChordTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    // ── 容错解析 ──────────────────────────────────────────────

    @Test
    fun `fromJson handles empty string`() {
        val restored = NinthChordTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles corrupted json`() {
        val restored = NinthChordTrainingProgress.fromJson("{corrupted!!!")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats field`() {
        val restored = NinthChordTrainingProgress.fromJson("{\"other\":\"value\"}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles partial entry`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5,\"totalCorrect\":3}}}"
        val restored = NinthChordTrainingProgress.fromJson(json)
        val entry = restored.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(3, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    @Test
    fun `fromJson handles missing fields in entry`() {
        val json = "{\"stats\":{\"ADVANCED\":{\"totalAnswered\":10,\"totalCorrect\":8,\"sessionCount\":2}}"
        val restored = NinthChordTrainingProgress.fromJson(json)
        val entry = restored.getProgress(NinthChordDifficulty.ADVANCED)
        assertEquals(10, entry.totalAnswered)
    }

    // ── 多次 roundtrip 稳定性 ─────────────────────────────────

    @Test
    fun `multiple roundtrips are stable`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(NinthChordDifficulty.INTERMEDIATE, 6, 10, 4)
        progress.recordSession(NinthChordDifficulty.ADVANCED, 4, 10, 2)

        var current = progress
        for (i in 1..5) {
            val json = current.toJson()
            current = NinthChordTrainingProgress.fromJson(json)
            assertEquals("roundtrip $i: totalAnswered mismatch", progress.totalAnswered, current.totalAnswered)
            assertEquals("roundtrip $i: totalCorrect mismatch", progress.totalCorrect, current.totalCorrect)
            assertEquals("roundtrip $i: overallBestStreak mismatch", progress.overallBestStreak, current.overallBestStreak)
        }
    }

    // ── cumulativeAccuracy ──────────────────────────────────

    @Test
    fun `cumulativeAccuracy is correct`() {
        progress.recordSession(NinthChordDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(NinthChordDifficulty.BEGINNER, 2, 10, 1)
        val entry = progress.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy is 0 for empty entry`() {
        val entry = progress.getProgress(NinthChordDifficulty.BEGINNER)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }
}
