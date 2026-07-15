package com.pianocompanion.contrapuntalmotiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 声部运动辨识训练进度跟踪单元测试。
 *
 * 验证累计统计、难度隔离、JSON 往返一致性、容错解析。
 */
class ContrapuntalMotionProgressTest {

    // ── 基本功能 ──────────────────────────────────────────

    @Test
    fun `初始进度为空`() {
        val progress = ContrapuntalMotionProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession 累加统计`() {
        val progress = ContrapuntalMotionProgress()
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 8, 10, 5)

        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
        assertEquals(5, progress.overallBestStreak)
    }

    @Test
    fun `多次会话累计`() {
        val progress = ContrapuntalMotionProgress()
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 7, 10, 6)

        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
        assertEquals(6, progress.overallBestStreak)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `不同难度独立统计`() {
        val progress = ContrapuntalMotionProgress()
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ContrapuntalMotionDifficulty.ADVANCED, 2, 10, 1)

        val beginner = progress.getProgress(ContrapuntalMotionDifficulty.BEGINNER)
        val advanced = progress.getProgress(ContrapuntalMotionDifficulty.ADVANCED)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(3, beginner.bestStreak)

        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
        assertEquals(1, advanced.bestStreak)
    }

    @Test
    fun `未练习的难度返回空统计`() {
        val progress = ContrapuntalMotionProgress()
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 5, 10, 3)

        val advanced = progress.getProgress(ContrapuntalMotionDifficulty.ADVANCED)
        assertEquals(0, advanced.totalAnswered)
        assertEquals(0.0, advanced.cumulativeAccuracy, 0.001)
    }

    // ── bestAccuracy 更新 ──────────────────────────────────

    @Test
    fun `bestAccuracy 记录最佳会话准确率`() {
        val progress = ContrapuntalMotionProgress()
        progress.recordSession(ContrapuntalMotionDifficulty.INTERMEDIATE, 9, 10, 5)
        assertEquals(0.9, progress.getProgress(ContrapuntalMotionDifficulty.INTERMEDIATE).bestAccuracy, 0.001)

        progress.recordSession(ContrapuntalMotionDifficulty.INTERMEDIATE, 5, 10, 3)
        assertEquals(0.9, progress.getProgress(ContrapuntalMotionDifficulty.INTERMEDIATE).bestAccuracy, 0.001)

        progress.recordSession(ContrapuntalMotionDifficulty.INTERMEDIATE, 10, 10, 8)
        assertEquals(1.0, progress.getProgress(ContrapuntalMotionDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
    }

    // ── bestStreak 更新 ──────────────────────────────────

    @Test
    fun `bestStreak 只增不减`() {
        val progress = ContrapuntalMotionProgress()
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 5, 10, 7)
        progress.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 3, 10, 2)

        assertEquals(7, progress.getProgress(ContrapuntalMotionDifficulty.BEGINNER).bestStreak)
    }

    // ── JSON 序列化 ──────────────────────────────────────

    @Test
    fun `JSON 往返一致性`() {
        val original = ContrapuntalMotionProgress()
        original.recordSession(ContrapuntalMotionDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(ContrapuntalMotionDifficulty.INTERMEDIATE, 6, 10, 3)
        original.recordSession(ContrapuntalMotionDifficulty.ADVANCED, 4, 10, 2)

        val json = original.toJson()
        val restored = ContrapuntalMotionProgress.fromJson(json)

        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)

        // 逐难度验证
        ContrapuntalMotionDifficulty.ALL.forEach { d ->
            val orig = original.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals("$d totalAnswered", orig.totalAnswered, rest.totalAnswered)
            assertEquals("$d totalCorrect", orig.totalCorrect, rest.totalCorrect)
            assertEquals("$d bestStreak", orig.bestStreak, rest.bestStreak)
            assertEquals("$d bestAccuracy", orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `空进度 JSON 往返`() {
        val original = ContrapuntalMotionProgress()
        val json = original.toJson()
        val restored = ContrapuntalMotionProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = ContrapuntalMotionProgress.fromJson("not valid json")
        assertNotNull(restored)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = ContrapuntalMotionProgress.fromJson("")
        assertNotNull(restored)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少 stats 键返回空进度`() {
        val restored = ContrapuntalMotionProgress.fromJson("{\"version\":1}")
        assertNotNull(restored)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏的 entry 被跳过`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":8," +
            "\"sessionCount\":1,\"bestStreak\":5,\"bestAccuracy\":0.8000}," +
            "\"BROKEN\":{\"bad\":\"data\"}}}"
        val restored = ContrapuntalMotionProgress.fromJson(json)
        // BEGINNER 应恢复
        assertEquals(10, restored.getProgress(ContrapuntalMotionDifficulty.BEGINNER).totalAnswered)
        // BROKEN 应被跳过
        assertEquals(10, restored.totalAnswered)
    }

    @Test
    fun `数值缺失时默认为 0`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5}}}"
        val restored = ContrapuntalMotionProgress.fromJson(json)
        val entry = restored.getProgress(ContrapuntalMotionDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
    }

    // ── ProgressEntry ──────────────────────────────────────

    @Test
    fun `ProgressEntry cumulativeAccuracy`() {
        val entry = ContrapuntalMotionProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 零分母不崩溃`() {
        val entry = ContrapuntalMotionProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry JSON 往返`() {
        val entry = ContrapuntalMotionProgressEntry(
            totalAnswered = 100,
            totalCorrect = 80,
            sessionCount = 10,
            bestStreak = 15,
            bestAccuracy = 0.95
        )
        val json = entry.toJson()
        val restored = ContrapuntalMotionProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(100, restored!!.totalAnswered)
        assertEquals(80, restored.totalCorrect)
        assertEquals(10, restored.sessionCount)
        assertEquals(15, restored.bestStreak)
        assertEquals(0.95, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry fromJson 无效返回 null`() {
        val restored = ContrapuntalMotionProgressEntry.fromJson("not json")
        assertNull(restored)
    }
}
