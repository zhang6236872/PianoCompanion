package com.pianocompanion.chordfunctiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦功能听辨训练进度持久化单元测试。
 *
 * 覆盖：累计统计、JSON 序列化/反序列化、容错性、难度隔离。
 */
class ChordFunctionTrainingProgressTest {

    // ── 初始状态 ──────────────────────────────────────────────

    @Test
    fun `new progress has zero stats`() {
        val p = ChordFunctionTrainingProgress()
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0, p.overallBestStreak)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `new progress has empty stats per difficulty`() {
        val p = ChordFunctionTrainingProgress()
        for (d in ChordFunctionDifficulty.ALL) {
            val s = p.getProgress(d)
            assertEquals(0, s.totalAnswered)
            assertEquals(0, s.totalCorrect)
            assertEquals(0, s.bestStreak)
            assertEquals(0, s.sessionCount)
        }
    }

    @Test
    fun `new progress accuracy is 0`() {
        val p = ChordFunctionTrainingProgress()
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    // ── 记录会话 ──────────────────────────────────────────────

    @Test
    fun `recordSession increments counts`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        assertEquals(10, p.totalAnswered)
        assertEquals(5, p.totalCorrect)
        assertEquals(1, p.totalSessions)
    }

    @Test
    fun `recordSession tracks correct and incorrect separately`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 3, total = 10, bestStreak = 2)
        assertEquals(20, p.totalAnswered)
        assertEquals(11, p.totalCorrect)
        assertEquals(2, p.totalSessions)
    }

    @Test
    fun `recordSession updates best streak`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 3, total = 5, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 3, total = 5, bestStreak = 5)
        assertEquals(5, p.overallBestStreak)
        assertEquals(5, p.getProgress(ChordFunctionDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `best streak does not decrease`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 10)
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 1, total = 5, bestStreak = 2)
        assertEquals(10, p.overallBestStreak)
    }

    // ── 难度隔离 ──────────────────────────────────────────────

    @Test
    fun `stats are tracked separately per difficulty`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.INTERMEDIATE, correct = 2, total = 8, bestStreak = 1)
        assertEquals(10, p.getProgress(ChordFunctionDifficulty.BEGINNER).totalAnswered)
        assertEquals(8, p.getProgress(ChordFunctionDifficulty.INTERMEDIATE).totalAnswered)
        assertEquals(5, p.getProgress(ChordFunctionDifficulty.BEGINNER).totalCorrect)
        assertEquals(2, p.getProgress(ChordFunctionDifficulty.INTERMEDIATE).totalCorrect)
    }

    @Test
    fun `overall totals sum all difficulties`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.INTERMEDIATE, correct = 3, total = 7, bestStreak = 2)
        p.recordSession(ChordFunctionDifficulty.ADVANCED, correct = 1, total = 5, bestStreak = 1)
        assertEquals(22, p.totalAnswered)
        assertEquals(9, p.totalCorrect)
        assertEquals(3, p.totalSessions)
    }

    @Test
    fun `best streak is max across all difficulties`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 7)
        assertEquals(7, p.overallBestStreak)
    }

    // ── 准确率 ────────────────────────────────────────────────

    @Test
    fun `overall accuracy is correct ratio`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 7, bestStreak = 0)
        assertEquals(5.0 / 7.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `per difficulty cumulative accuracy`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.INTERMEDIATE, correct = 3, total = 5, bestStreak = 0)
        p.recordSession(ChordFunctionDifficulty.INTERMEDIATE, correct = 2, total = 5, bestStreak = 0)
        assertEquals(0.5, p.getProgress(ChordFunctionDifficulty.INTERMEDIATE).cumulativeAccuracy, 0.001)
    }

    @Test
    fun `accuracy with zero answers is 0`() {
        val p = ChordFunctionTrainingProgress()
        assertEquals(0.0, p.getProgress(ChordFunctionDifficulty.ADVANCED).cumulativeAccuracy, 0.001)
    }

    @Test
    fun `best accuracy tracks highest session accuracy`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 0) // 50%
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 9, total = 10, bestStreak = 0) // 90%
        assertEquals(0.9, p.getProgress(ChordFunctionDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── JSON 序列化 ──────────────────────────────────────────

    @Test
    fun `toJson and fromJson round trip preserves data`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.INTERMEDIATE, correct = 3, total = 7, bestStreak = 2)
        p.recordSession(ChordFunctionDifficulty.ADVANCED, correct = 4, total = 8, bestStreak = 5)

        val json = p.toJson()
        val restored = ChordFunctionTrainingProgress.fromJson(json)

        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.totalSessions, restored.totalSessions)
        for (d in ChordFunctionDifficulty.ALL) {
            assertEquals(p.getProgress(d).totalAnswered, restored.getProgress(d).totalAnswered)
            assertEquals(p.getProgress(d).totalCorrect, restored.getProgress(d).totalCorrect)
            assertEquals(p.getProgress(d).bestStreak, restored.getProgress(d).bestStreak)
            assertEquals(p.getProgress(d).sessionCount, restored.getProgress(d).sessionCount)
        }
    }

    @Test
    fun `toJson produces valid JSON string`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 1, total = 1, bestStreak = 1)
        val json = p.toJson()
        assertTrue("JSON 应包含花括号", json.contains("{"))
        assertTrue("JSON 应包含花括号", json.contains("}"))
        assertTrue("JSON 应包含 stats 键", json.contains("stats"))
    }

    @Test
    fun `empty progress toJson is valid`() {
        val p = ChordFunctionTrainingProgress()
        val json = p.toJson()
        val restored = ChordFunctionTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错性 ────────────────────────────────────────────────

    @Test
    fun `fromJson with invalid string returns empty progress`() {
        val restored = ChordFunctionTrainingProgress.fromJson("not valid json")
        assertNotNull(restored)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson with empty string returns empty progress`() {
        val restored = ChordFunctionTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson with partial JSON preserves available data`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5,"totalCorrect":3,"sessionCount":1,"bestStreak":2,"bestAccuracy":0.6000}}}"""
        val restored = ChordFunctionTrainingProgress.fromJson(json)
        assertEquals(5, restored.totalAnswered)
        assertEquals(3, restored.totalCorrect)
    }

    @Test
    fun `fromJson with unknown difficulty key is graceful`() {
        val json = """{"stats":{"UNKNOWN":{"totalAnswered":1,"totalCorrect":0,"sessionCount":1,"bestStreak":0,"bestAccuracy":0.0000}}}"""
        val restored = ChordFunctionTrainingProgress.fromJson(json)
        assertNotNull(restored)
        // UNKNOWN 会被存储为字符串键但不影响已知难度的统计
    }

    // ── 多次会话累积 ──────────────────────────────────────────

    @Test
    fun `multiple sessions accumulate correctly`() {
        val p = ChordFunctionTrainingProgress()
        for (session in 1..5) {
            p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = session)
        }
        assertEquals(50, p.totalAnswered)
        assertEquals(25, p.totalCorrect)
        assertEquals(5, p.totalSessions)
        assertEquals(5, p.overallBestStreak)
    }

    // ── 统计字段完整性 ────────────────────────────────────────

    @Test
    fun `stats contain all expected fields`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 2)
        val stats = p.getProgress(ChordFunctionDifficulty.BEGINNER)
        assertTrue(stats.totalAnswered >= 0)
        assertTrue(stats.totalCorrect >= 0)
        assertTrue(stats.bestStreak >= 0)
        assertTrue(stats.sessionCount >= 0)
        assertTrue(stats.totalCorrect <= stats.totalAnswered)
    }

    @Test
    fun `cumulative accuracy is correct after multiple sessions`() {
        val p = ChordFunctionTrainingProgress()
        p.recordSession(ChordFunctionDifficulty.ADVANCED, correct = 7, total = 10, bestStreak = 3)
        p.recordSession(ChordFunctionDifficulty.ADVANCED, correct = 8, total = 10, bestStreak = 4)
        val stats = p.getProgress(ChordFunctionDifficulty.ADVANCED)
        assertEquals(20, stats.totalAnswered)
        assertEquals(15, stats.totalCorrect)
        assertEquals(0.75, stats.cumulativeAccuracy, 0.001)
    }
}
