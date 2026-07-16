package com.pianocompanion.consonancetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协和度辨识训练进度跟踪模型单元测试。
 */
class ConsonanceProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = ConsonanceProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `record session accumulates stats`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(5, p.totalCorrect)
        assertEquals(0.5, p.overallAccuracy, 0.001)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.BEGINNER, 8, 10, 4)
        p.recordSession(ConsonanceDifficulty.INTERMEDIATE, 6, 10, 2)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.001)
        assertEquals(4, p.overallBestStreak)
    }

    @Test
    fun `difficulty isolation`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(ConsonanceDifficulty.ADVANCED, 2, 10, 7)
        val beginner = p.getProgress(ConsonanceDifficulty.BEGINNER)
        val advanced = p.getProgress(ConsonanceDifficulty.ADVANCED)
        val intermediate = p.getProgress(ConsonanceDifficulty.INTERMEDIATE)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(3, beginner.bestStreak)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
        assertEquals(7, advanced.bestStreak)
        assertEquals(0, intermediate.totalAnswered)
    }

    @Test
    fun `best accuracy tracks session maximum`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.INTERMEDIATE, 5, 10, 0) // 50%
        assertEquals(0.5, p.getProgress(ConsonanceDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
        p.recordSession(ConsonanceDifficulty.INTERMEDIATE, 9, 10, 5) // 90%
        assertEquals(0.9, p.getProgress(ConsonanceDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
        p.recordSession(ConsonanceDifficulty.INTERMEDIATE, 3, 10, 1) // 30%
        assertEquals(0.9, p.getProgress(ConsonanceDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
    }

    @Test
    fun `best streak tracks maximum across sessions`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.BEGINNER, 5, 5, 5)
        assertEquals(5, p.getProgress(ConsonanceDifficulty.BEGINNER).bestStreak)
        p.recordSession(ConsonanceDifficulty.BEGINNER, 3, 3, 3)
        assertEquals(5, p.getProgress(ConsonanceDifficulty.BEGINNER).bestStreak)
        p.recordSession(ConsonanceDifficulty.BEGINNER, 7, 7, 8)
        assertEquals(8, p.getProgress(ConsonanceDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `json round trip preserves data`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(ConsonanceDifficulty.INTERMEDIATE, 6, 10, 5)
        p.recordSession(ConsonanceDifficulty.ADVANCED, 9, 10, 7)
        val json = p.toJson()
        val restored = ConsonanceProgress.fromJson(json)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        ConsonanceDifficulty.ALL.forEach { d ->
            val orig = p.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `fromJson handles corrupt json gracefully`() {
        val p = ConsonanceProgress.fromJson("not valid json at all")
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson handles empty json`() {
        val p = ConsonanceProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles partial json`() {
        val p = ConsonanceProgress.fromJson("{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5}}}")
        // Should parse what it can without crashing
        assertEquals(5, p.getProgress(ConsonanceDifficulty.BEGINNER).totalAnswered)
    }

    @Test
    fun `cumulative accuracy for entry`() {
        val entry = ConsonanceProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy zero for empty entry`() {
        val entry = ConsonanceProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `session count increments`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.ADVANCED, 1, 1, 1)
        p.recordSession(ConsonanceDifficulty.ADVANCED, 1, 1, 1)
        p.recordSession(ConsonanceDifficulty.ADVANCED, 1, 1, 1)
        assertEquals(3, p.getProgress(ConsonanceDifficulty.ADVANCED).sessionCount)
        assertEquals(3, p.totalSessions)
    }

    @Test
    fun `entry json round trip`() {
        val entry = ConsonanceProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = ConsonanceProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `overall best streak across difficulties`() {
        val p = ConsonanceProgress()
        p.recordSession(ConsonanceDifficulty.BEGINNER, 5, 5, 4)
        p.recordSession(ConsonanceDifficulty.INTERMEDIATE, 5, 5, 9)
        p.recordSession(ConsonanceDifficulty.ADVANCED, 5, 5, 6)
        assertEquals(9, p.overallBestStreak)
    }
}
