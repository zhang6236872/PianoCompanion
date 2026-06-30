package com.pianocompanion.rhythm

import org.junit.Assert.*
import org.junit.Test

/**
 * [RhythmProgress] 进度跟踪模型单元测试。
 *
 * 覆盖：
 * - session 记录和统计累加
 * - JSON 序列化/反序列化往返
 * - 多难度独立统计
 * - 无效 JSON 安全处理
 * - 聚合统计（总会话数/总题数/通过率）
 */
class RhythmProgressTest {

    // ── 记录会话 ──────────────────────────────────────────

    @Test
    fun `recordSession创建新难度条目`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, passed = 3, total = 5, avgScore = 0.8, bestStreak = 2)
        val entry = progress.getProgress(RhythmDifficulty.BEGINNER)
        assertEquals(5, entry.totalQuestions)
        assertEquals(3, entry.totalPassed)
        assertEquals(1, entry.sessionCount)
        assertEquals(2, entry.bestStreak)
    }

    @Test
    fun `多次recordSession累加统计`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 2, 4, 0.6, 1)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 3, 4, 0.75, 3)
        val entry = progress.getProgress(RhythmDifficulty.INTERMEDIATE)
        assertEquals(8, entry.totalQuestions)
        assertEquals(5, entry.totalPassed)
        assertEquals(2, entry.sessionCount)
        assertEquals(3, entry.bestStreak) // 取最大
    }

    @Test
    fun `bestStreak取最大值`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.ADVANCED, 1, 2, 0.5, 5)
        progress.recordSession(RhythmDifficulty.ADVANCED, 1, 2, 0.5, 3)
        assertEquals(5, progress.getProgress(RhythmDifficulty.ADVANCED).bestStreak)
    }

    @Test
    fun `bestAvgScore记录最高会话平均分`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 2, 2, 0.7, 1)
        progress.recordSession(RhythmDifficulty.BEGINNER, 2, 2, 0.9, 1)
        assertEquals(0.9, progress.getProgress(RhythmDifficulty.BEGINNER).bestAvgScore, 0.01)
    }

    @Test
    fun `未记录的难度返回空条目`() {
        val progress = RhythmProgress()
        val entry = progress.getProgress(RhythmDifficulty.ADVANCED)
        assertEquals(0, entry.totalQuestions)
        assertEquals(0, entry.sessionCount)
    }

    // ── 聚合统计 ──────────────────────────────────────────

    @Test
    fun `totalSessions跨难度求和`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 1, 2, 0.5, 1)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 1, 2, 0.5, 1)
        progress.recordSession(RhythmDifficulty.ADVANCED, 1, 2, 0.5, 1)
        assertEquals(3, progress.totalSessions)
    }

    @Test
    fun `totalQuestions跨难度求和`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 2, 4, 0.5, 1)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 1, 3, 0.5, 1)
        assertEquals(7, progress.totalQuestions)
    }

    @Test
    fun `totalPassed跨难度求和`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 3, 5, 0.5, 1)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 2, 5, 0.5, 1)
        assertEquals(5, progress.totalPassed)
    }

    @Test
    fun `overallPassRate正确计算`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 3, 5, 0.5, 1)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 2, 5, 0.5, 1)
        assertEquals(0.5, progress.overallPassRate, 0.01)
    }

    @Test
    fun `空进度聚合统计为0`() {
        val progress = RhythmProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalQuestions)
        assertEquals(0, progress.totalPassed)
        assertEquals(0.0, progress.overallPassRate, 0.001)
    }

    // ── 各难度独立统计 ────────────────────────────────────

    @Test
    fun `不同难度互不影响`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 5, 5, 1.0, 5)
        progress.recordSession(RhythmDifficulty.ADVANCED, 0, 5, 0.0, 0)
        assertEquals(5, progress.getProgress(RhythmDifficulty.BEGINNER).totalPassed)
        assertEquals(0, progress.getProgress(RhythmDifficulty.ADVANCED).totalPassed)
    }

    // ── JSON 序列化 ───────────────────────────────────────

    @Test
    fun `toJson和fromJson往返保持数据`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 3, 5, 0.8, 2)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 2, 4, 0.6, 1)

        val json = progress.toJson()
        val restored = RhythmProgress.fromJson(json)

        val beginOrig = progress.getProgress(RhythmDifficulty.BEGINNER)
        val beginRestored = restored.getProgress(RhythmDifficulty.BEGINNER)
        assertEquals(beginOrig.totalQuestions, beginRestored.totalQuestions)
        assertEquals(beginOrig.totalPassed, beginRestored.totalPassed)
        assertEquals(beginOrig.sessionCount, beginRestored.sessionCount)
        assertEquals(beginOrig.bestStreak, beginRestored.bestStreak)
        assertEquals(beginOrig.bestAvgScore, beginRestored.bestAvgScore, 0.01)
    }

    @Test
    fun `fromJson处理无效JSON返回空进度`() {
        val restored = RhythmProgress.fromJson("not valid json")
        assertEquals(0, restored.totalSessions)
        assertEquals(0, restored.totalQuestions)
    }

    @Test
    fun `fromJson处理空字符串返回空进度`() {
        val restored = RhythmProgress.fromJson("")
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `fromJson处理部分缺失字段`() {
        // 缺少某些字段的 JSON
        val json = """{"stats":{"BEGINNER":{"totalQuestions":3,"totalPassed":2}}}"""
        val restored = RhythmProgress.fromJson(json)
        val entry = restored.getProgress(RhythmDifficulty.BEGINNER)
        assertEquals(3, entry.totalQuestions)
        assertEquals(2, entry.totalPassed)
        assertEquals(0, entry.sessionCount) // 缺失字段默认 0
    }

    @Test
    fun `toJson生成有效JSON结构`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 1, 2, 0.5, 1)
        val json = progress.toJson()
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"stats\""))
        assertTrue(json.contains("BEGINNER"))
        assertTrue(json.contains("totalQuestions"))
    }

    @Test
    fun `多难度JSON序列化往返`() {
        val progress = RhythmProgress()
        progress.recordSession(RhythmDifficulty.BEGINNER, 1, 1, 1.0, 1)
        progress.recordSession(RhythmDifficulty.INTERMEDIATE, 1, 1, 0.5, 1)
        progress.recordSession(RhythmDifficulty.ADVANCED, 0, 1, 0.0, 0)

        val json = progress.toJson()
        val restored = RhythmProgress.fromJson(json)

        assertEquals(3, restored.totalSessions)
        assertEquals(progress.getProgress(RhythmDifficulty.BEGINNER).totalPassed,
            restored.getProgress(RhythmDifficulty.BEGINNER).totalPassed)
        assertEquals(progress.getProgress(RhythmDifficulty.ADVANCED).totalPassed,
            restored.getProgress(RhythmDifficulty.ADVANCED).totalPassed)
    }

    // ── RhythmProgressEntry ───────────────────────────────

    @Test
    fun `Entry的averageScore计算`() {
        val entry = RhythmProgressEntry(
            totalQuestions = 10,
            cumulativeScore = 7.0
        )
        assertEquals(0.7, entry.averageScore, 0.01)
    }

    @Test
    fun `Entry的passRate计算`() {
        val entry = RhythmProgressEntry(
            totalQuestions = 8,
            totalPassed = 6
        )
        assertEquals(0.75, entry.passRate, 0.01)
    }

    @Test
    fun `空Entry的averageScore和passRate为0`() {
        val entry = RhythmProgressEntry()
        assertEquals(0.0, entry.averageScore, 0.001)
        assertEquals(0.0, entry.passRate, 0.001)
    }

    @Test
    fun `Entry toJson fromJson 往返`() {
        val entry = RhythmProgressEntry(
            totalQuestions = 15,
            totalPassed = 10,
            sessionCount = 3,
            bestStreak = 5,
            bestAvgScore = 0.85,
            cumulativeScore = 12.0
        )
        val json = entry.toJson()
        val restored = RhythmProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(15, restored!!.totalQuestions)
        assertEquals(10, restored.totalPassed)
        assertEquals(3, restored.sessionCount)
        assertEquals(5, restored.bestStreak)
        assertEquals(0.85, restored.bestAvgScore, 0.01)
        assertEquals(12.0, restored.cumulativeScore, 0.01)
    }
}
