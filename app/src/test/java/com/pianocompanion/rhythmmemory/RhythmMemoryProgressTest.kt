package com.pianocompanion.rhythmmemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmMemoryProgress] 单元测试。
 */
class RhythmMemoryProgressTest {

    @Test
    fun `空进度 JSON 往返`() {
        val p = RhythmMemoryProgress()
        val json = p.toJson()
        val restored = RhythmMemoryProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalCorrect)
    }

    @Test
    fun `累计统计正确`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 6, 10, 3)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(2, p.totalSessions)
    }

    @Test
    fun `难度隔离`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(RhythmMemoryDifficulty.ADVANCED, 9, 10, 7)
        val beg = p.getProgress(RhythmMemoryDifficulty.BEGINNER)
        val adv = p.getProgress(RhythmMemoryDifficulty.ADVANCED)
        assertEquals(10, beg.totalAnswered)
        assertEquals(5, beg.totalCorrect)
        assertEquals(10, adv.totalAnswered)
        assertEquals(9, adv.totalCorrect)
    }

    @Test
    fun `bestStreak 跨会话取最大值`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.INTERMEDIATE, 5, 10, 6)
        p.recordSession(RhythmMemoryDifficulty.INTERMEDIATE, 7, 10, 4)
        assertEquals(6, p.getProgress(RhythmMemoryDifficulty.INTERMEDIATE).bestStreak)
        assertEquals(6, p.overallBestStreak)
    }

    @Test
    fun `bestAccuracy 只增不减`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 9, 10, 0) // 90%
        assertEquals(0.9, p.getProgress(RhythmMemoryDifficulty.BEGINNER).bestAccuracy, 0.001)
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 5, 10, 0) // 50%
        assertEquals(0.9, p.getProgress(RhythmMemoryDifficulty.BEGINNER).bestAccuracy, 0.001)
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 10, 10, 0) // 100%
        assertEquals(1.0, p.getProgress(RhythmMemoryDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `JSON 往返保留数据`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 7, 10, 4)
        p.recordSession(RhythmMemoryDifficulty.ADVANCED, 3, 8, 2)
        val json = p.toJson()
        val restored = RhythmMemoryProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(
            p.getProgress(RhythmMemoryDifficulty.ADVANCED).bestAccuracy,
            restored.getProgress(RhythmMemoryDifficulty.ADVANCED).bestAccuracy,
            0.0001
        )
    }

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = RhythmMemoryProgress.fromJson("not a json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺失字段返回空 entry`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10}}}"""
        val restored = RhythmMemoryProgress.fromJson(json)
        // 缺失其他字段 → entry 为 null → 不计入
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `负数回退 0`() {
        // 手工构造带负数的 JSON
        val json = """{"stats":{"BEGINNER":{"totalAnswered":-5,"totalCorrect":-3,"sessionCount":-1,"bestStreak":-2,"bestAccuracy":-0.5}}}"""
        val restored = RhythmMemoryProgress.fromJson(json)
        val entry = restored.getProgress(RhythmMemoryDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `total 为 0 时 bestAccuracy 不更新`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(RhythmMemoryDifficulty.BEGINNER).bestAccuracy, 0.0)
    }

    @Test
    fun `overallAccuracy 计算正确`() {
        val p = RhythmMemoryProgress()
        p.recordSession(RhythmMemoryDifficulty.BEGINNER, 5, 10, 0)
        p.recordSession(RhythmMemoryDifficulty.INTERMEDIATE, 8, 10, 0)
        assertEquals(13.0 / 20.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `getProgress 未记录难度返回空 entry`() {
        val p = RhythmMemoryProgress()
        val entry = p.getProgress(RhythmMemoryDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.bestAccuracy, 0.0)
    }

    @Test
    fun `严格 5 字段校验 - 完整数据通过`() {
        val entry = RhythmMemoryProgressEntry.fromJson(
            """{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestStreak":5,"bestAccuracy":0.8}"""
        )
        assertTrue(entry != null)
        assertEquals(10, entry!!.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
        assertEquals(0.8, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `严格 5 字段校验 - 缺 bestStreak 拒绝`() {
        val entry = RhythmMemoryProgressEntry.fromJson(
            """{"totalAnswered":10,"totalCorrect":7,"sessionCount":2,"bestAccuracy":0.8}"""
        )
        // 缺 bestStreak → 返回 null
        assertTrue(entry == null)
    }

    @Test
    fun `cumulativeAccuracy 计算正确`() {
        val entry = RhythmMemoryProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
        val empty = RhythmMemoryProgressEntry()
        assertEquals(0.0, empty.cumulativeAccuracy, 0.0)
    }
}
