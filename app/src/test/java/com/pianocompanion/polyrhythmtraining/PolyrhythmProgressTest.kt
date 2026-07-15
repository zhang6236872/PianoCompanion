package com.pianocompanion.polyrhythmtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 复合节奏辨识训练进度跟踪单元测试。
 */
class PolyrhythmProgressTest {

    // ── 基本记录 ────────────────────────────────────────

    @Test
    fun `空进度总有零统计`() {
        val progress = PolyrhythmTrainingProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession累计答题数`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 3, 5, 2)
        assertEquals(5, progress.totalAnswered)
        assertEquals(3, progress.totalCorrect)
    }

    @Test
    fun `多次recordSession累计`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 4, 5, 3)
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 2, 10, 1)
        assertEquals(20, progress.totalAnswered)
        assertEquals(9, progress.totalCorrect)
        assertEquals(3, progress.totalSessions)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `不同难度的统计独立`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 5, 5, 3)
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 0, 5, 0)
        val beginnerEntry = progress.getProgress(PolyrhythmDifficulty.BEGINNER)
        val advancedEntry = progress.getProgress(PolyrhythmDifficulty.ADVANCED)
        assertEquals(5, beginnerEntry.totalCorrect)
        assertEquals(0, advancedEntry.totalCorrect)
        assertEquals(3, beginnerEntry.bestStreak)
        assertEquals(0, advancedEntry.bestStreak)
    }

    @Test
    fun `未记录的难度返回空统计`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 1, 1, 1)
        val intermediate = progress.getProgress(PolyrhythmDifficulty.INTERMEDIATE)
        assertEquals(0, intermediate.totalAnswered)
        assertEquals(0, intermediate.sessionCount)
    }

    // ── 最佳记录 ────────────────────────────────────────

    @Test
    fun `bestStreak只增不减`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 5, 5, 5)
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 3, 5, 3)
        val entry = progress.getProgress(PolyrhythmDifficulty.ADVANCED)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `bestAccuracy只增不减`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 5, 5, 3) // 100%
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 1, 5, 1) // 20%
        val entry = progress.getProgress(PolyrhythmDifficulty.ADVANCED)
        assertEquals(1.0, entry.bestAccuracy, 0.001)
    }

    // ── JSON 往返 ───────────────────────────────────────

    @Test
    fun `JSON往返一致性`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 7, 10, 4)
        val json = progress.toJson()
        val restored = PolyrhythmTrainingProgress.fromJson(json)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON往返保留难度隔离`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(PolyrhythmDifficulty.INTERMEDIATE, 4, 5, 3)
        progress.recordSession(PolyrhythmDifficulty.ADVANCED, 7, 10, 4)
        val restored = PolyrhythmTrainingProgress.fromJson(progress.toJson())
        assertEquals(3, restored.getProgress(PolyrhythmDifficulty.BEGINNER).totalCorrect)
        assertEquals(4, restored.getProgress(PolyrhythmDifficulty.INTERMEDIATE).totalCorrect)
        assertEquals(7, restored.getProgress(PolyrhythmDifficulty.ADVANCED).totalCorrect)
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val progress = PolyrhythmTrainingProgress.fromJson("not json at all")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val progress = PolyrhythmTrainingProgress.fromJson("")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val progress = PolyrhythmTrainingProgress.fromJson("{\"foo\":{}}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `缺少字段时默认为0`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5}}}"
        val progress = PolyrhythmTrainingProgress.fromJson(json)
        val entry = progress.getProgress(PolyrhythmDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    // ── 空会话记录 ──────────────────────────────────────

    @Test
    fun `记录total为零的会话不影响bestAccuracy`() {
        val progress = PolyrhythmTrainingProgress()
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 5, 5, 3)
        progress.recordSession(PolyrhythmDifficulty.BEGINNER, 0, 0, 0)
        val entry = progress.getProgress(PolyrhythmDifficulty.BEGINNER)
        assertEquals(1.0, entry.bestAccuracy, 0.001)
    }
}
