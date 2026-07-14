package com.pianocompanion.melodicdirectiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 旋律方向辨识训练进度跟踪单元测试。
 */
class MelodicDirectionProgressTest {

    // ── recordSession ───────────────────────────────────

    @Test
    fun `记录一次会话后totalAnswered正确`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 2)
        assertEquals(5, progress.totalAnswered)
    }

    @Test
    fun `记录一次会话后totalCorrect正确`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 2)
        assertEquals(3, progress.totalCorrect)
    }

    @Test
    fun `多次记录累加`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 4, 5, 3)
        assertEquals(10, progress.totalAnswered)
        assertEquals(7, progress.totalCorrect)
    }

    @Test
    fun `sessionCount正确累加`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.INTERMEDIATE, 2, 3, 1)
        progress.recordSession(MelodicDirectionDifficulty.INTERMEDIATE, 1, 2, 1)
        assertEquals(2, progress.totalSessions)
    }

    @Test
    fun `不同难度独立统计`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 5, 5, 3)
        progress.recordSession(MelodicDirectionDifficulty.ADVANCED, 1, 5, 1)

        val beginner = progress.getProgress(MelodicDirectionDifficulty.BEGINNER)
        val advanced = progress.getProgress(MelodicDirectionDifficulty.ADVANCED)

        assertEquals(5, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(5, advanced.totalAnswered)
        assertEquals(1, advanced.totalCorrect)
    }

    // ── bestStreak ──────────────────────────────────────

    @Test
    fun `bestStreak保持最大值`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 5)
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 3)
        assertEquals(5, progress.overallBestStreak)
    }

    @Test
    fun `bestStreak跨难度取最大`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 3)
        progress.recordSession(MelodicDirectionDifficulty.ADVANCED, 4, 5, 7)
        assertEquals(7, progress.overallBestStreak)
    }

    // ── bestAccuracy ────────────────────────────────────

    @Test
    fun `bestAccuracy保持最高值`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 5, 5, 5) // 100%
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 2, 5, 2) // 40%
        val entry = progress.getProgress(MelodicDirectionDifficulty.BEGINNER)
        assertEquals(1.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy提升时更新`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 2, 5, 2) // 40%
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 5, 5, 5) // 100%
        val entry = progress.getProgress(MelodicDirectionDifficulty.BEGINNER)
        assertEquals(1.0, entry.bestAccuracy, 0.001)
    }

    // ── overallAccuracy ─────────────────────────────────

    @Test
    fun `空进度准确率为0`() {
        val progress = MelodicDirectionProgress()
        assertEquals(0.0, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `全局准确率跨难度汇总`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 4, 10, 3)
        progress.recordSession(MelodicDirectionDifficulty.ADVANCED, 6, 10, 4)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
    }

    // ── JSON 序列化 ─────────────────────────────────────

    @Test
    fun `JSON往返保持数据一致`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 4)
        progress.recordSession(MelodicDirectionDifficulty.INTERMEDIATE, 2, 4, 2)
        progress.recordSession(MelodicDirectionDifficulty.ADVANCED, 5, 5, 5)

        val json = progress.toJson()
        val restored = MelodicDirectionProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = MelodicDirectionProgress.fromJson("not valid json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空JSON返回空进度`() {
        val restored = MelodicDirectionProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏JSON尽力恢复`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 4)
        val json = progress.toJson()
        // 截断 JSON
        val truncated = json.substring(0, json.length / 2)
        val restored = MelodicDirectionProgress.fromJson(truncated)
        // 应至少不崩溃
        assertNotNull(restored)
    }

    // ── cumulativeAccuracy ──────────────────────────────

    @Test
    fun `cumulativeAccuracy正确`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 10, 3)
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 2, 5, 2)
        val entry = progress.getProgress(MelodicDirectionDifficulty.BEGINNER)
        // (3+2)/(10+5) = 5/15 = 0.333
        assertEquals(5.0 / 15.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `未记录难度的进度为默认值`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 3, 5, 4)
        val entry = progress.getProgress(MelodicDirectionDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── totalSessions ───────────────────────────────────

    @Test
    fun `totalSessions跨难度汇总`() {
        val progress = MelodicDirectionProgress()
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 1, 2, 1)
        progress.recordSession(MelodicDirectionDifficulty.BEGINNER, 1, 2, 1)
        progress.recordSession(MelodicDirectionDifficulty.ADVANCED, 1, 2, 1)
        assertEquals(3, progress.totalSessions)
    }
}
