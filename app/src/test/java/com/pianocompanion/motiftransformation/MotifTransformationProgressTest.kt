package com.pianocompanion.motiftransformation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动机发展辨识训练进度序列化与跨会话统计单元测试。
 */
class MotifTransformationProgressTest {

    @Test
    fun `fresh progress has defaults`() {
        val p = MotifTransformationProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
        assertTrue(p.stats.isEmpty())
    }

    @Test
    fun `recordSession updates totals`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates across sessions`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 6, total = 10, bestStreak = 5)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `recordSession tracks per-difficulty separately`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 5, total = 5, bestStreak = 5)
        p.recordSession(MotifTransformationDifficulty.ADVANCED, correct = 1, total = 5, bestStreak = 1)
        val begin = p.getProgress(MotifTransformationDifficulty.BEGINNER)
        val adv = p.getProgress(MotifTransformationDifficulty.ADVANCED)
        assertEquals(5, begin.totalCorrect)
        assertEquals(1, adv.totalCorrect)
    }

    @Test
    fun `getProgress returns empty entry for unrecorded difficulty`() {
        val p = MotifTransformationProgress()
        val entry = p.getProgress(MotifTransformationDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak tracks maximum across difficulties`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 3, total = 5, bestStreak = 4)
        p.recordSession(MotifTransformationDifficulty.ADVANCED, correct = 2, total = 5, bestStreak = 7)
        p.recordSession(MotifTransformationDifficulty.INTERMEDIATE, correct = 1, total = 5, bestStreak = 2)
        assertEquals(7, p.overallBestStreak)
    }

    @Test
    fun `bestStreak only increases`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 5, total = 5, bestStreak = 5)
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 2, total = 5, bestStreak = 2)
        val entry = p.getProgress(MotifTransformationDifficulty.BEGINNER)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `bestAccuracy tracks per-difficulty`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        // session accuracy = 0.5
        val entry1 = p.getProgress(MotifTransformationDifficulty.BEGINNER)
        assertEquals(0.5, entry1.bestAccuracy, 0.001)
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 9, total = 10, bestStreak = 3)
        // session accuracy = 0.9, should update bestAccuracy
        val entry2 = p.getProgress(MotifTransformationDifficulty.BEGINNER)
        assertEquals(0.9, entry2.bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy only increases`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.ADVANCED, correct = 9, total = 10, bestStreak = 3)
        p.recordSession(MotifTransformationDifficulty.ADVANCED, correct = 1, total = 10, bestStreak = 3)
        val entry = p.getProgress(MotifTransformationDifficulty.ADVANCED)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy reflects all sessions for difficulty`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.INTERMEDIATE, correct = 8, total = 10, bestStreak = 3)
        p.recordSession(MotifTransformationDifficulty.INTERMEDIATE, correct = 2, total = 10, bestStreak = 3)
        val entry = p.getProgress(MotifTransformationDifficulty.INTERMEDIATE)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `toJson serializes without throwing`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        val json = p.toJson()
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("\"stats\""))
        assertTrue(json.contains("\"BEGINNER\""))
        assertTrue(json.contains("\"totalAnswered\":10"))
        assertTrue(json.contains("\"totalCorrect\":8"))
        assertTrue(json.contains("\"bestStreak\":3"))
        assertTrue(json.contains("\"sessionCount\":1"))
    }

    @Test
    fun `fromJson round-trip restores all fields`() {
        val original = MotifTransformationProgress()
        original.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        original.recordSession(MotifTransformationDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 5)
        original.recordSession(MotifTransformationDifficulty.INTERMEDIATE, correct = 10, total = 10, bestStreak = 10)

        val json = original.toJson()
        val restored = MotifTransformationProgress.fromJson(json)

        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)
        assertEquals(original.stats.size, restored.stats.size)
    }

    @Test
    fun `fromJson restores per-difficulty entry`() {
        val original = MotifTransformationProgress()
        original.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 7, total = 10, bestStreak = 4)

        val json = original.toJson()
        val restored = MotifTransformationProgress.fromJson(json)

        val entry = restored.getProgress(MotifTransformationDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(4, entry.bestStreak)
    }

    @Test
    fun `fromJson handles empty json gracefully`() {
        val restored = MotifTransformationProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
        assertTrue(restored.stats.isEmpty())
    }

    @Test
    fun `fromJson handles malformed json gracefully`() {
        val restored = MotifTransformationProgress.fromJson("{this is not valid json")
        assertEquals(0, restored.totalAnswered)
        assertFalse(restored.toString().isEmpty())
    }

    @Test
    fun `fromJson handles null-like input`() {
        val restored = MotifTransformationProgress.fromJson("null")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles partial json with no stats`() {
        val restored = MotifTransformationProgress.fromJson("{\"someOtherKey\":42}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson with incomplete entry returns null and is skipped`() {
        // entry missing required fields should be skipped (not counted)
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":5}}}"""
        val restored = MotifTransformationProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `overallAccuracy is zero when no attempts`() {
        val p = MotifTransformationProgress()
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `overallAccuracy calculates correctly across difficulties`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(MotifTransformationDifficulty.ADVANCED, correct = 3, total = 10, bestStreak = 3)
        // 8/20 = 0.4
        assertEquals(0.4, p.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession with total zero does not crash`() {
        val p = MotifTransformationProgress()
        p.recordSession(MotifTransformationDifficulty.BEGINNER, correct = 0, total = 0, bestStreak = 0)
        assertEquals(0, p.totalAnswered)
        assertEquals(1, p.totalSessions)
    }

    @Test
    fun `multiple difficulties serialize and restore`() {
        val original = MotifTransformationProgress()
        MotifTransformationDifficulty.ALL.forEach { diff ->
            original.recordSession(diff, correct = 5, total = 10, bestStreak = 3)
        }

        val json = original.toJson()
        val restored = MotifTransformationProgress.fromJson(json)

        assertEquals(3, restored.stats.size)
        MotifTransformationDifficulty.ALL.forEach { diff ->
            val entry = restored.getProgress(diff)
            assertEquals(10, entry.totalAnswered)
            assertEquals(5, entry.totalCorrect)
            assertEquals(1, entry.sessionCount)
        }
    }
}
