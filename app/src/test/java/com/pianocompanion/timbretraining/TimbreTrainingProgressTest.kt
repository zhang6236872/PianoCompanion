package com.pianocompanion.timbretraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音色辨识训练进度跟踪单元测试。
 */
class TimbreTrainingProgressTest {

    // ── 基本记录 ────────────────────────────────────────

    @Test
    fun `空进度默认值为零`() {
        val progress = TimbreTrainingProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession累计统计`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
        assertEquals(5, progress.overallBestStreak)
    }

    @Test
    fun `多次recordSession累加`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 7, 10, 6)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `bestStreak取最大值`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.INTERMEDIATE, 5, 5, 3)
        progress.recordSession(TimbreTrainingDifficulty.INTERMEDIATE, 5, 5, 7)
        progress.recordSession(TimbreTrainingDifficulty.INTERMEDIATE, 5, 5, 4)
        assertEquals(7, progress.overallBestStreak)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `不同难度的统计独立`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 10, 10, 5)
        progress.recordSession(TimbreTrainingDifficulty.ADVANCED, 3, 10, 2)

        val beginnerStats = progress.getProgress(TimbreTrainingDifficulty.BEGINNER)
        val advancedStats = progress.getProgress(TimbreTrainingDifficulty.ADVANCED)

        assertEquals(10, beginnerStats.totalAnswered)
        assertEquals(10, beginnerStats.totalCorrect)
        assertEquals(5, beginnerStats.bestStreak)

        assertEquals(10, advancedStats.totalAnswered)
        assertEquals(3, advancedStats.totalCorrect)
        assertEquals(2, advancedStats.bestStreak)
    }

    @Test
    fun `getProgress未记录的难度返回空`() {
        val progress = TimbreTrainingProgress()
        val stats = progress.getProgress(TimbreTrainingDifficulty.ADVANCED)
        assertEquals(0, stats.totalAnswered)
        assertEquals(0, stats.totalCorrect)
        assertEquals(0, stats.sessionCount)
    }

    @Test
    fun `全局统计跨难度汇总`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(TimbreTrainingDifficulty.INTERMEDIATE, 6, 10, 5)
        progress.recordSession(TimbreTrainingDifficulty.ADVANCED, 4, 10, 7)

        assertEquals(3, progress.totalSessions)
        assertEquals(30, progress.totalAnswered)
        assertEquals(18, progress.totalCorrect)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
        assertEquals(7, progress.overallBestStreak)
    }

    // ── bestAccuracy ───────────────────────────────────

    @Test
    fun `bestAccuracy记录最高单次会话准确率`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 5, 10, 3) // 50%
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 9, 10, 5) // 90%
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 7, 10, 4) // 70%

        val stats = progress.getProgress(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(0.9, stats.bestAccuracy, 0.001)
    }

    @Test
    fun `total为零时bestAccuracy不变`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 0, 0, 0)
        val stats = progress.getProgress(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(0.0, stats.bestAccuracy, 0.001)
    }

    // ── JSON 序列化/反序列化 ───────────────────────────

    @Test
    fun `JSON往返保持数据一致`() {
        val original = TimbreTrainingProgress()
        original.recordSession(TimbreTrainingDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(TimbreTrainingDifficulty.ADVANCED, 3, 7, 2)

        val json = original.toJson()
        val restored = TimbreTrainingProgress.fromJson(json)

        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON包含所有难度`() {
        val original = TimbreTrainingProgress()
        for (difficulty in TimbreTrainingDifficulty.ALL) {
            original.recordSession(difficulty, 5, 10, 3)
        }

        val json = original.toJson()
        val restored = TimbreTrainingProgress.fromJson(json)

        for (difficulty in TimbreTrainingDifficulty.ALL) {
            val stats = restored.getProgress(difficulty)
            assertEquals(10, stats.totalAnswered)
            assertEquals(5, stats.totalCorrect)
        }
    }

    @Test
    fun `JSON包含bestAccuracy字段`() {
        val original = TimbreTrainingProgress()
        original.recordSession(TimbreTrainingDifficulty.INTERMEDIATE, 7, 10, 4)
        val json = original.toJson()
        assertTrue(json.contains("bestAccuracy"))
    }

    @Test
    fun `空进度JSON往返`() {
        val original = TimbreTrainingProgress()
        val json = original.toJson()
        val restored = TimbreTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = TimbreTrainingProgress.fromJson("not a json")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `空字符串JSON返回空进度`() {
        val restored = TimbreTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val restored = TimbreTrainingProgress.fromJson("{\"otherField\":123}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏的entry不影响其他entry`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8000},"ADVANCED":{"totalAnswered":BAD}}}"""
        val restored = TimbreTrainingProgress.fromJson(json)
        // BEGINNER 应正常解析
        val beginner = restored.getProgress(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
    }

    @Test
    fun `JSON字符串中含转义字符不影响解析`() {
        // 键名含特殊字符的边界情况
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5,\"totalCorrect\":5,\"sessionCount\":1,\"bestStreak\":3,\"bestAccuracy\":1.0000}}}"
        val restored = TimbreTrainingProgress.fromJson(json)
        val stats = restored.getProgress(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(5, stats.totalAnswered)
    }

    // ── cumulativeAccuracy ─────────────────────────────

    @Test
    fun `cumulativeAccuracy正确计算`() {
        val progress = TimbreTrainingProgress()
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 7, 10, 3)
        progress.recordSession(TimbreTrainingDifficulty.BEGINNER, 8, 10, 5)
        val stats = progress.getProgress(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(0.75, stats.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `未答题时cumulativeAccuracy为0`() {
        val stats = TimbreTrainingProgressEntry()
        assertEquals(0.0, stats.cumulativeAccuracy, 0.001)
    }

    // ── ProgressEntry JSON ─────────────────────────────

    @Test
    fun `ProgressEntry JSON往返`() {
        val original = TimbreTrainingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.85
        )
        val json = original.toJson()
        val restored = TimbreTrainingProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(30, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.85, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 损坏JSON返回null`() {
        val restored = TimbreTrainingProgressEntry.fromJson("not json")
        assertNull(restored)
    }

    @Test
    fun `ProgressEntry 缺少字段时使用默认值0`() {
        val json = "{\"totalAnswered\":10}"
        val restored = TimbreTrainingProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(10, restored!!.totalAnswered)
        assertEquals(0, restored.totalCorrect)
    }
}
