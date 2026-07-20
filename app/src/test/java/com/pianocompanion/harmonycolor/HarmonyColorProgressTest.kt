package com.pianocompanion.harmonycolor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和声色彩听辨训练进度跟踪单元测试。
 */
class HarmonyColorProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val progress = HarmonyColorProgress()
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0, progress.totalSessions)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `record session accumulates stats`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 8, 10, 3)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(1, progress.totalSessions)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
        assertEquals(3, progress.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 5, 10, 2)
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 7, 10, 4)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(2, progress.totalSessions)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
        assertEquals(4, progress.overallBestStreak)
    }

    @Test
    fun `difficulty isolation`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 5, 10, 2)
        progress.recordSession(HarmonyColorDifficulty.ADVANCED, 9, 10, 5)
        val beginner = progress.getProgress(HarmonyColorDifficulty.BEGINNER)
        val advanced = progress.getProgress(HarmonyColorDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(2, beginner.bestStreak)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(9, advanced.totalCorrect)
        assertEquals(5, advanced.bestStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.INTERMEDIATE, 5, 10, 7)
        progress.recordSession(HarmonyColorDifficulty.INTERMEDIATE, 5, 10, 3)
        val entry = progress.getProgress(HarmonyColorDifficulty.INTERMEDIATE)
        assertEquals(7, entry.bestStreak)
    }

    @Test
    fun `best accuracy does not decrease`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.INTERMEDIATE, 9, 10, 0) // 0.9
        progress.recordSession(HarmonyColorDifficulty.INTERMEDIATE, 5, 10, 0) // 0.5
        val entry = progress.getProgress(HarmonyColorDifficulty.INTERMEDIATE)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `json round trip preserves data`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(HarmonyColorDifficulty.ADVANCED, 6, 10, 4)
        val json = progress.toJson()
        val restored = HarmonyColorProgress.fromJson(json)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
        val b1 = progress.getProgress(HarmonyColorDifficulty.BEGINNER)
        val b2 = restored.getProgress(HarmonyColorDifficulty.BEGINNER)
        assertEquals(b1.totalAnswered, b2.totalAnswered)
        assertEquals(b1.totalCorrect, b2.totalCorrect)
        assertEquals(b1.bestStreak, b2.bestStreak)
        assertEquals(b1.bestAccuracy, b2.bestAccuracy, 0.001)
    }

    @Test
    fun `from json with empty string returns empty progress`() {
        val progress = HarmonyColorProgress.fromJson("")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `from json with malformed string returns empty progress`() {
        val progress = HarmonyColorProgress.fromJson("{ broken json }}}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `from json with null-like content returns empty progress`() {
        val progress = HarmonyColorProgress.fromJson("null")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `from json without stats key returns empty progress`() {
        val progress = HarmonyColorProgress.fromJson("{\"other\": {}}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `from json with partial entry is rejected`() {
        // Entry missing required fields should not be loaded
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":5}}}"
        val progress = HarmonyColorProgress.fromJson(json)
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `from json with complete entry is accepted`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":5," +
            "\"sessionCount\":1,\"bestStreak\":3,\"bestAccuracy\":0.5000}}}"
        val progress = HarmonyColorProgress.fromJson(json)
        assertEquals(10, progress.totalAnswered)
        assertEquals(5, progress.totalCorrect)
        assertEquals(1, progress.totalSessions)
        assertEquals(3, progress.overallBestStreak)
    }

    @Test
    fun `negative values fall back to zero`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":-5,\"totalCorrect\":-3," +
            "\"sessionCount\":-1,\"bestStreak\":-2,\"bestAccuracy\":-0.5}}}"
        val progress = HarmonyColorProgress.fromJson(json)
        val entry = progress.getProgress(HarmonyColorDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy calculation`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.ADVANCED, 3, 10, 0)
        val entry = progress.getProgress(HarmonyColorDifficulty.ADVANCED)
        assertEquals(0.3, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy is zero when no answers`() {
        val progress = HarmonyColorProgress()
        val entry = progress.getProgress(HarmonyColorDifficulty.ADVANCED)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `get progress for unseen difficulty returns empty entry`() {
        val progress = HarmonyColorProgress()
        val entry = progress.getProgress(HarmonyColorDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.bestStreak)
    }

    @Test
    fun `multiple difficulties in json`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 5, 10, 1)
        progress.recordSession(HarmonyColorDifficulty.INTERMEDIATE, 7, 10, 2)
        progress.recordSession(HarmonyColorDifficulty.ADVANCED, 9, 10, 5)
        val json = progress.toJson()
        val restored = HarmonyColorProgress.fromJson(json)
        for (d in HarmonyColorDifficulty.ALL) {
            val orig = progress.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
        }
    }

    @Test
    fun `overall best streak across difficulties`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(HarmonyColorDifficulty.ADVANCED, 5, 10, 8)
        progress.recordSession(HarmonyColorDifficulty.INTERMEDIATE, 5, 10, 5)
        assertEquals(8, progress.overallBestStreak)
    }

    @Test
    fun `session with zero total does not update best accuracy`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 0, 0, 0)
        val entry = progress.getProgress(HarmonyColorDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `json output is valid json format`() {
        val progress = HarmonyColorProgress()
        progress.recordSession(HarmonyColorDifficulty.BEGINNER, 5, 10, 2)
        val json = progress.toJson()
        assertNotNull(json)
        assertTrue("JSON should start with {", json.startsWith("{"))
        assertTrue("JSON should end with }", json.endsWith("}"))
        assertTrue("JSON should contain stats key", json.contains("\"stats\""))
    }
}
