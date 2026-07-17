package com.pianocompanion.scaledegreetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调内音级辨识训练进度跟踪单元测试。
 */
class ScaleDegreeProgressTest {

    @Test
    fun `empty progress has zero totals`() {
        val p = ScaleDegreeProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates stats`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(5, p.overallBestStreak)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 6, 10, 3)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.0001)
    }

    @Test
    fun `stats are isolated by difficulty`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ScaleDegreeDifficulty.ADVANCED, 3, 10, 2)

        val beginner = p.getProgress(ScaleDegreeDifficulty.BEGINNER)
        val advanced = p.getProgress(ScaleDegreeDifficulty.ADVANCED)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(5, beginner.bestStreak)
        assertEquals(1, beginner.sessionCount)

        assertEquals(10, advanced.totalAnswered)
        assertEquals(3, advanced.totalCorrect)
        assertEquals(2, advanced.bestStreak)
        assertEquals(1, advanced.sessionCount)
    }

    @Test
    fun `best streak is maximum across sessions`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.INTERMEDIATE, 5, 5, 3)
        p.recordSession(ScaleDegreeDifficulty.INTERMEDIATE, 5, 5, 7)
        p.recordSession(ScaleDegreeDifficulty.INTERMEDIATE, 5, 5, 4)
        val entry = p.getProgress(ScaleDegreeDifficulty.INTERMEDIATE)
        assertEquals(7, entry.bestStreak)
    }

    @Test
    fun `best accuracy tracks session maximum`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 7, 10, 3) // 0.7
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 9, 10, 4) // 0.9
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 5, 10, 2) // 0.5
        val entry = p.getProgress(ScaleDegreeDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.0001)
    }

    @Test
    fun `json round trip preserves data`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ScaleDegreeDifficulty.INTERMEDIATE, 6, 12, 4)
        p.recordSession(ScaleDegreeDifficulty.ADVANCED, 15, 20, 9)

        val json = p.toJson()
        val restored = ScaleDegreeProgress.fromJson(json)

        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        ScaleDegreeDifficulty.ALL.forEach { d ->
            val orig = p.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.sessionCount, rest.sessionCount)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.0001)
        }
    }

    @Test
    fun `fromJson with corrupted json returns empty progress`() {
        val p = ScaleDegreeProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val p = ScaleDegreeProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with missing stats key returns empty progress`() {
        val p = ScaleDegreeProgress.fromJson("{\"otherKey\":42}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with partial entry does not crash`() {
        val p = ScaleDegreeProgress.fromJson("{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5}}}")
        val entry = p.getProgress(ScaleDegreeDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
    }

    @Test
    fun `getProgress for unknown difficulty returns empty entry`() {
        val p = ScaleDegreeProgress()
        val entry = p.getProgress(ScaleDegreeDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `cumulative accuracy computes correctly`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 7, 10, 3)
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 3, 10, 1)
        val entry = p.getProgress(ScaleDegreeDifficulty.BEGINNER)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `empty session with zero total does not inflate accuracy`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 0, 0, 0)
        val entry = p.getProgress(ScaleDegreeDifficulty.BEGINNER)
        assertEquals(0.0, entry.bestAccuracy, 0.0001)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `overall best streak considers all difficulties`() {
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 5, 5, 3)
        p.recordSession(ScaleDegreeDifficulty.ADVANCED, 5, 5, 8)
        p.recordSession(ScaleDegreeDifficulty.INTERMEDIATE, 5, 5, 6)
        assertEquals(8, p.overallBestStreak)
    }

    @Test
    fun `record session with correct greater than total clamps gracefully`() {
        // 防御性：理论上不应出现，但不应崩溃
        val p = ScaleDegreeProgress()
        p.recordSession(ScaleDegreeDifficulty.BEGINNER, 12, 10, 5)
        // accuracy 会 > 1.0，但 bestAccuracy 字段记录的就是 session accuracy
        val entry = p.getProgress(ScaleDegreeDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
    }
}
