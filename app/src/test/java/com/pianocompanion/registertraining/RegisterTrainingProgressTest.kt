package com.pianocompanion.registertraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音区辨识训练进度跟踪单元测试。
 */
class RegisterTrainingProgressTest {

    // ── 基本功能 ──────────────────────────────────────

    @Test
    fun `初始进度为空`() {
        val progress = RegisterTrainingProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession后totalAnswered增加`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(10, progress.totalAnswered)
        assertEquals(5, progress.totalCorrect)
    }

    @Test
    fun `recordSession后sessionCount增加`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 1, 1, 1)
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 1, 1, 1)
        assertEquals(2, progress.totalSessions)
    }

    @Test
    fun `准确率计算正确`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 3, 10, 2)
        assertEquals(0.3, progress.overallAccuracy, 0.001)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `不同难度的统计相互隔离`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(RegisterTrainingDifficulty.ADVANCED, 8, 10, 5)

        val beginnerProgress = progress.getProgress(RegisterTrainingDifficulty.BEGINNER)
        val advancedProgress = progress.getProgress(RegisterTrainingDifficulty.ADVANCED)

        assertEquals(10, beginnerProgress.totalAnswered)
        assertEquals(5, beginnerProgress.totalCorrect)
        assertEquals(10, advancedProgress.totalAnswered)
        assertEquals(8, advancedProgress.totalCorrect)
    }

    @Test
    fun `未练习难度的进度为默认值`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        val advancedProgress = progress.getProgress(RegisterTrainingDifficulty.ADVANCED)
        assertEquals(0, advancedProgress.totalAnswered)
        assertEquals(0.0, advancedProgress.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `总体统计跨难度汇总`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(RegisterTrainingDifficulty.ADVANCED, 8, 10, 5)
        assertEquals(20, progress.totalAnswered)
        assertEquals(13, progress.totalCorrect)
        assertEquals(0.65, progress.overallAccuracy, 0.001)
    }

    // ── bestStreak 追踪 ────────────────────────────────

    @Test
    fun `bestStreak取最大值`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 8, 10, 7)
        assertEquals(7, progress.getProgress(RegisterTrainingDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `overallBestStreak跨难度取最大`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(RegisterTrainingDifficulty.ADVANCED, 8, 10, 6)
        assertEquals(6, progress.overallBestStreak)
    }

    // ── bestAccuracy 追踪 ──────────────────────────────

    @Test
    fun `bestAccuracy取最大值`() {
        val progress = RegisterTrainingProgress()
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 8, 10, 3) // 80%
        progress.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 2) // 50%
        assertEquals(0.8, progress.getProgress(RegisterTrainingDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────

    @Test
    fun `JSON序列化后反序列化结果一致`() {
        val original = RegisterTrainingProgress()
        original.recordSession(RegisterTrainingDifficulty.BEGINNER, 5, 10, 3)
        original.recordSession(RegisterTrainingDifficulty.INTERMEDIATE, 7, 10, 4)
        original.recordSession(RegisterTrainingDifficulty.ADVANCED, 8, 10, 5)

        val json = original.toJson()
        val restored = RegisterTrainingProgress.fromJson(json)

        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)

        for (d in RegisterTrainingDifficulty.ALL) {
            val origEntry = original.getProgress(d)
            val restEntry = restored.getProgress(d)
            assertEquals(origEntry.totalAnswered, restEntry.totalAnswered)
            assertEquals(origEntry.totalCorrect, restEntry.totalCorrect)
            assertEquals(origEntry.sessionCount, restEntry.sessionCount)
            assertEquals(origEntry.bestStreak, restEntry.bestStreak)
            assertEquals(origEntry.bestAccuracy, restEntry.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `空进度的JSON往返一致`() {
        val original = RegisterTrainingProgress()
        val json = original.toJson()
        val restored = RegisterTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = RegisterTrainingProgress.fromJson("invalid json")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = RegisterTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val restored = RegisterTrainingProgress.fromJson("{\"other\":123}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏的JSON尽可能保留有效数据`() {
        // 一个有效的条目 + 一个损坏的条目
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":5," +
            "\"sessionCount\":1,\"bestStreak\":3,\"bestAccuracy\":0.5000}," +
            "\"BROKEN\":{\"invalid}}}"
        val restored = RegisterTrainingProgress.fromJson(json)
        assertEquals(10, restored.totalAnswered)
        assertEquals(5, restored.totalCorrect)
    }
}
