package com.pianocompanion.modulationrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转调辨识训练进度跟踪单元测试。
 *
 * 验证：
 * - 累计统计正确
 * - 难度隔离（各难度独立统计）
 * - JSON 序列化/反序列化往返一致性
 * - 容错解析（损坏 JSON 返回空进度）
 */
class ModulationProgressTest {

    @Test
    fun `新建进度为空`() {
        val progress = ModulationProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    // ── 记录会话 ──────────────────────────────────────────

    @Test
    fun `recordSession 累加答题数`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(ModulationDifficulty.BEGINNER, 7, 10, 4)

        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(15, progress.totalCorrect)
    }

    @Test
    fun `recordSession 计算准确率`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.ADVANCED, 8, 10, 5)

        assertEquals(0.8, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession 更新最佳连击`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.BEGINNER, 5, 5, 3)
        progress.recordSession(ModulationDifficulty.BEGINNER, 5, 5, 7)

        assertEquals(7, progress.overallBestStreak)
    }

    @Test
    fun `recordSession 最佳连击不降级`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.BEGINNER, 5, 5, 10)
        progress.recordSession(ModulationDifficulty.BEGINNER, 5, 5, 3)

        assertEquals(10, progress.overallBestStreak)
    }

    @Test
    fun `recordSession 更新最佳准确率`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.BEGINNER, 5, 10, 3) // 50%
        progress.recordSession(ModulationDifficulty.BEGINNER, 9, 10, 5) // 90%

        val entry = progress.getProgress(ModulationDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `不同难度的统计互相隔离`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ModulationDifficulty.ADVANCED, 8, 10, 7)

        val beginner = progress.getProgress(ModulationDifficulty.BEGINNER)
        val advanced = progress.getProgress(ModulationDifficulty.ADVANCED)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(8, advanced.totalCorrect)
    }

    @Test
    fun `getProgress 未记录的难度返回空`() {
        val progress = ModulationProgress()
        val entry = progress.getProgress(ModulationDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `JSON 序列化往返一致`() {
        val original = ModulationProgress()
        original.recordSession(ModulationDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(ModulationDifficulty.INTERMEDIATE, 7, 10, 4)
        original.recordSession(ModulationDifficulty.ADVANCED, 9, 10, 8)

        val json = original.toJson()
        val restored = ModulationProgress.fromJson(json)

        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON 包含各难度独立统计`() {
        val original = ModulationProgress()
        original.recordSession(ModulationDifficulty.BEGINNER, 3, 5, 2)
        original.recordSession(ModulationDifficulty.ADVANCED, 4, 5, 4)

        val restored = ModulationProgress.fromJson(original.toJson())

        val beginner = restored.getProgress(ModulationDifficulty.BEGINNER)
        val advanced = restored.getProgress(ModulationDifficulty.ADVANCED)

        assertEquals(5, beginner.totalAnswered)
        assertEquals(3, beginner.totalCorrect)
        assertEquals(5, advanced.totalAnswered)
        assertEquals(4, advanced.totalCorrect)
        assertEquals(4, advanced.bestStreak)
    }

    @Test
    fun `JSON 保持 bestAccuracy 精度`() {
        val progress = ModulationProgress()
        progress.recordSession(ModulationDifficulty.BEGINNER, 7, 10, 3)

        val restored = ModulationProgress.fromJson(progress.toJson())
        val entry = restored.getProgress(ModulationDifficulty.BEGINNER)

        assertEquals(0.7, entry.bestAccuracy, 0.001)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `空 JSON 字符串返回空进度`() {
        val result = ModulationProgress.fromJson("")
        assertEquals(0, result.totalAnswered)
    }

    @Test
    fun `损坏 JSON 返回空进度`() {
        val result = ModulationProgress.fromJson("{broken json!!!")
        assertEquals(0, result.totalAnswered)
    }

    @Test
    fun `缺少 stats 字段返回空进度`() {
        val result = ModulationProgress.fromJson("{\"other\":123}")
        assertEquals(0, result.totalAnswered)
    }

    @Test
    fun `缺少字段使用默认值`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10}}}"
        val result = ModulationProgress.fromJson(json)
        val entry = result.getProgress(ModulationDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    @Test
    fun `空 stats 对象返回空进度`() {
        val result = ModulationProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, result.totalAnswered)
    }

    // ── ProgressEntry ──────────────────────────────────────────

    @Test
    fun `ProgressEntry cumulativeAccuracy 正确`() {
        val entry = ModulationProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 空时 cumulativeAccuracy 为 0`() {
        val entry = ModulationProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry JSON 往返`() {
        val original = ModulationProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.8333
        )
        val json = original.toJson()
        val restored = ModulationProgressEntry.fromJson(json)

        assertNotNull(restored)
        assertEquals(original.totalAnswered, restored!!.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.sessionCount, restored.sessionCount)
        assertEquals(original.bestStreak, restored.bestStreak)
        assertEquals(original.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    private fun assertNotNull(value: Any?) {
        org.junit.Assert.assertNotNull(value)
    }
}
