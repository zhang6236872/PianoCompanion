package com.pianocompanion.dynamicstraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 力度辨识进度跟踪模型单元测试。
 */
class DynamicsTrainingProgressTest {

    // ── 基本记录 ──────────────────────────────────────

    @Test
    fun `空进度所有统计为零`() {
        val progress = DynamicsTrainingProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession累加totalAnswered`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(10, progress.totalAnswered)
        assertEquals(5, progress.totalCorrect)
    }

    @Test
    fun `recordSession累加多次会话`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 4, 5, 3)
        assertEquals(10, progress.totalAnswered)
        assertEquals(7, progress.totalCorrect)
        assertEquals(2, progress.totalSessions)
    }

    @Test
    fun `recordSession更新bestStreak`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 3, 3, 2)
        assertEquals(2, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).bestStreak)
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 5, 5, 7)
        assertEquals(7, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).bestStreak)
        // 较低的连击不应降低 bestStreak
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 2, 2, 1)
        assertEquals(7, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `recordSession更新bestAccuracy`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 4, 10, 1)
        assertEquals(0.4, progress.getProgress(DynamicsTrainingDifficulty.ADVANCED).bestAccuracy, 0.001)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 8, 10, 1)
        assertEquals(0.8, progress.getProgress(DynamicsTrainingDifficulty.ADVANCED).bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy不降级`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 5, 5, 1) // 100%
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 1, 5, 1) // 20%
        assertEquals(1.0, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── 难度隔离 ──────────────────────────────────────

    @Test
    fun `不同难度统计相互隔离`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 3, 3, 3)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 1, 5, 1)

        assertEquals(3, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).totalAnswered)
        assertEquals(5, progress.getProgress(DynamicsTrainingDifficulty.ADVANCED).totalAnswered)
    }

    @Test
    fun `getProgress返回空entry当无记录`() {
        val progress = DynamicsTrainingProgress()
        val entry = progress.getProgress(DynamicsTrainingDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── 全局汇总 ──────────────────────────────────────

    @Test
    fun `overallAccuracy跨难度汇总`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 5, 10, 1)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 3, 10, 1)
        assertEquals(20, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.4, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak跨难度取最大`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 1, 1, 5)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 1, 1, 10)
        assertEquals(10, progress.overallBestStreak)
    }

    @Test
    fun `totalSessions跨难度汇总`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 1, 1, 1)
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 1, 1, 1)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 1, 1, 1)
        assertEquals(3, progress.totalSessions)
    }

    // ── JSON 序列化 ────────────────────────────────────

    @Test
    fun `JSON往返保持数据一致`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 7, 10, 3)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 5, 8, 5)

        val json = progress.toJson()
        val restored = DynamicsTrainingProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON往返保持bestAccuracy`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.INTERMEDIATE, 9, 10, 2)
        val json = progress.toJson()
        val restored = DynamicsTrainingProgress.fromJson(json)
        assertEquals(
            progress.getProgress(DynamicsTrainingDifficulty.INTERMEDIATE).bestAccuracy,
            restored.getProgress(DynamicsTrainingDifficulty.INTERMEDIATE).bestAccuracy,
            0.001
        )
    }

    @Test
    fun `JSON往返保持各难度数据`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(DynamicsTrainingDifficulty.INTERMEDIATE, 4, 6, 4)
        progress.recordSession(DynamicsTrainingDifficulty.ADVANCED, 5, 7, 6)

        val json = progress.toJson()
        val restored = DynamicsTrainingProgress.fromJson(json)

        for (difficulty in DynamicsTrainingDifficulty.ALL) {
            val original = progress.getProgress(difficulty)
            val restoredEntry = restored.getProgress(difficulty)
            assertEquals(original.totalAnswered, restoredEntry.totalAnswered)
            assertEquals(original.totalCorrect, restoredEntry.totalCorrect)
            assertEquals(original.bestStreak, restoredEntry.bestStreak)
            assertEquals(original.bestAccuracy, restoredEntry.bestAccuracy, 0.001)
            assertEquals(original.sessionCount, restoredEntry.sessionCount)
        }
    }

    // ── 容错解析 ──────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = DynamicsTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = DynamicsTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val restored = DynamicsTrainingProgress.fromJson("{\"version\":\"1.0\"}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空stats对象返回空进度`() {
        val restored = DynamicsTrainingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `部分损坏的entry被跳过`() {
        val json = """
            {"stats":{
                "BEGINNER":{"totalAnswered":5,"totalCorrect":3,"sessionCount":1,"bestStreak":2,"bestAccuracy":0.6000},
                "BROKEN":{"totalAnswered":"not_a_number"}
            }}
        """.trimIndent()
        val restored = DynamicsTrainingProgress.fromJson(json)
        assertEquals(5, restored.totalAnswered)
        assertEquals(3, restored.totalCorrect)
    }

    // ── cumulativeAccuracy ────────────────────────────

    @Test
    fun `cumulativeAccuracy计算正确`() {
        val progress = DynamicsTrainingProgress()
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 3, 10, 1)
        assertEquals(0.3, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).cumulativeAccuracy, 0.001)
        progress.recordSession(DynamicsTrainingDifficulty.BEGINNER, 5, 10, 1)
        assertEquals(0.4, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).cumulativeAccuracy, 0.001)
    }

    @Test
    fun `total为零时cumulativeAccuracy为零`() {
        val progress = DynamicsTrainingProgress()
        assertEquals(0.0, progress.getProgress(DynamicsTrainingDifficulty.BEGINNER).cumulativeAccuracy, 0.001)
    }
}
