package com.pianocompanion.tempotraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 速度辨识训练进度跟踪单元测试。
 */
class TempoTrainingProgressTest {

    // ── 基础记录 ────────────────────────────────────────

    @Test
    fun `空进度所有统计为0`() {
        val p = TempoTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession增加统计`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `多次recordSession累计统计`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 2)
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 7, total = 10, bestStreak = 4)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
        assertEquals(4, p.overallBestStreak)
    }

    // ── 难度隔离 ────────────────────────────────────────

    @Test
    fun `不同难度独立统计`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 2)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 3, total = 10, bestStreak = 1)
        val beginner = p.getProgress(TempoTrainingDifficulty.BEGINNER)
        val advanced = p.getProgress(TempoTrainingDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(3, advanced.totalCorrect)
    }

    @Test
    fun `getProgress空时返回默认entry`() {
        val p = TempoTrainingProgress()
        val entry = p.getProgress(TempoTrainingDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.sessionCount)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── bestStreak 不降级 ──────────────────────────────

    @Test
    fun `bestStreak不降级`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 6)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 3, total = 10, bestStreak = 2)
        val entry = p.getProgress(TempoTrainingDifficulty.ADVANCED)
        assertEquals(6, entry.bestStreak)
    }

    @Test
    fun `bestStreak更新更大值`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 7)
        val entry = p.getProgress(TempoTrainingDifficulty.ADVANCED)
        assertEquals(7, entry.bestStreak)
    }

    // ── bestAccuracy 不降级 ────────────────────────────

    @Test
    fun `bestAccuracy不降级`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 9, total = 10, bestStreak = 1)
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 2, total = 10, bestStreak = 1)
        val entry = p.getProgress(TempoTrainingDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy更新更大值`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 1)
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 10, total = 10, bestStreak = 1)
        val entry = p.getProgress(TempoTrainingDifficulty.BEGINNER)
        assertEquals(1.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `total为0时不更新bestAccuracy`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 0, total = 0, bestStreak = 0)
        val entry = p.getProgress(TempoTrainingDifficulty.BEGINNER)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────

    @Test
    fun `JSON往返保持数据`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 7, total = 8, bestStreak = 4)
        val json = p.toJson()
        val restored = TempoTrainingProgress.fromJson(json)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON往返保持难度隔离`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(TempoTrainingDifficulty.INTERMEDIATE, correct = 7, total = 8, bestStreak = 4)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 2, total = 5, bestStreak = 1)
        val json = p.toJson()
        val restored = TempoTrainingProgress.fromJson(json)
        val origB = p.getProgress(TempoTrainingDifficulty.BEGINNER)
        val restB = restored.getProgress(TempoTrainingDifficulty.BEGINNER)
        assertEquals(origB.totalAnswered, restB.totalAnswered)
        assertEquals(origB.totalCorrect, restB.totalCorrect)
        assertEquals(origB.bestStreak, restB.bestStreak)
    }

    @Test
    fun `空进度JSON往返`() {
        val p = TempoTrainingProgress()
        val json = p.toJson()
        val restored = TempoTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = TempoTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = TempoTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺失stats字段返回空进度`() {
        val restored = TempoTrainingProgress.fromJson("{\"version\":1}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏entry不崩溃`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":"BAD","sessionCount":1}}}"""
        val restored = TempoTrainingProgress.fromJson(json)
        // 不崩溃即可，可能解析出部分数据
        assertNotNull(restored)
    }

    @Test
    fun `未知难度名不崩溃`() {
        val json = """{"stats":{"UNKNOWN_DIFFICULTY":{"totalAnswered":5,"totalCorrect":3,"sessionCount":1,"bestStreak":2,"bestAccuracy":0.6000}}}"""
        val restored = TempoTrainingProgress.fromJson(json)
        assertNotNull(restored)
    }

    // ── cumulativeAccuracy ─────────────────────────────

    @Test
    fun `cumulativeAccuracy计算正确`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 3, total = 10, bestStreak = 1)
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 7, total = 10, bestStreak = 1)
        val entry = p.getProgress(TempoTrainingDifficulty.BEGINNER)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `sessionCount正确递增`() {
        val p = TempoTrainingProgress()
        for (i in 1..5) {
            p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = i)
        }
        val entry = p.getProgress(TempoTrainingDifficulty.ADVANCED)
        assertEquals(5, entry.sessionCount)
    }

    // ── 全局汇总 ────────────────────────────────────────

    @Test
    fun `全局统计跨难度汇总`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 3, total = 5, bestStreak = 2)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 4, total = 5, bestStreak = 5)
        assertEquals(10, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `overallBestStreak取所有难度最大值`() {
        val p = TempoTrainingProgress()
        p.recordSession(TempoTrainingDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(TempoTrainingDifficulty.INTERMEDIATE, correct = 5, total = 10, bestStreak = 8)
        p.recordSession(TempoTrainingDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 5)
        assertEquals(8, p.overallBestStreak)
    }
}
