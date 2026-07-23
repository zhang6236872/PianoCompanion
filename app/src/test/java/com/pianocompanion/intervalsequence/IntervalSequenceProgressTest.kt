package com.pianocompanion.intervalsequence

import org.junit.Assert.*
import org.junit.Test

class IntervalSequenceProgressTest {

    @Test
    fun `初始状态 totalAnswered 为 0`() {
        val p = IntervalSequenceProgress()
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `初始状态 overallAccuracy 为 0`() {
        val p = IntervalSequenceProgress()
        assertEquals(0.0, p.overallAccuracy, 0.0)
    }

    @Test
    fun `recordSession 累计统计正确`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
    }

    @Test
    fun `多次 recordSession 累计`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 6, 10, 4)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(14.0 / 20.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `难度隔离统计`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(IntervalSequenceDifficulty.ADVANCED, 5, 10, 2)
        val beginner = p.getProgress(IntervalSequenceDifficulty.BEGINNER)
        val advanced = p.getProgress(IntervalSequenceDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(5, advanced.totalCorrect)
    }

    @Test
    fun `bestAccuracy 只增不减`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 9, 10, 3)
        val acc1 = p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestAccuracy
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 5, 10, 2)
        val acc2 = p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestAccuracy
        assertEquals(acc1, acc2, 0.001)
    }

    @Test
    fun `bestStreak 保留最大值`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.INTERMEDIATE, 8, 10, 7)
        p.recordSession(IntervalSequenceDifficulty.INTERMEDIATE, 6, 10, 3)
        assertEquals(7, p.getProgress(IntervalSequenceDifficulty.INTERMEDIATE).bestStreak)
    }

    @Test
    fun `longestStreak 跨难度取最大`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(IntervalSequenceDifficulty.ADVANCED, 8, 10, 10)
        assertEquals(10, p.longestStreak)
    }

    @Test
    fun `JSON 往返序列化`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(IntervalSequenceDifficulty.ADVANCED, 6, 10, 3)
        val json = p.toJson()
        val restored = IntervalSequenceProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(
            p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestStreak,
            restored.getProgress(IntervalSequenceDifficulty.BEGINNER).bestStreak
        )
        assertEquals(
            p.getProgress(IntervalSequenceDifficulty.ADVANCED).bestAccuracy,
            restored.getProgress(IntervalSequenceDifficulty.ADVANCED).bestAccuracy,
            0.001
        )
    }

    @Test
    fun `容错解析 - 损坏 JSON 返回空进度`() {
        val p = IntervalSequenceProgress.fromJson("this is not json {{{")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `容错解析 - 空字符串`() {
        val p = IntervalSequenceProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `严格 5 字段校验 - 完整数据通过`() {
        val json = """{"stats":[{"difficulty":"BEGINNER","totalAnswered":10,"totalCorrect":8,"bestAccuracy":0.8,"bestStreak":5}]}"""
        val p = IntervalSequenceProgress.fromJson(json)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
    }

    @Test
    fun `缺字段被拒绝`() {
        val json = """{"stats":[{"difficulty":"BEGINNER","totalAnswered":10}]}"""
        val p = IntervalSequenceProgress.fromJson(json)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `负数回退为 0`() {
        val json = """{"stats":[{"difficulty":"BEGINNER","totalAnswered":-5,"totalCorrect":-3,"bestAccuracy":-0.5,"bestStreak":-2}]}"""
        val p = IntervalSequenceProgress.fromJson(json)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestAccuracy, 0.001)
        assertEquals(0, p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `total 为 0 时 bestAccuracy 不更新`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestAccuracy, 0.0)
    }

    @Test
    fun `getProgress 未记录难度返回空 entry`() {
        val p = IntervalSequenceProgress()
        val entry = p.getProgress(IntervalSequenceDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.bestAccuracy, 0.0)
    }

    @Test
    fun `多难度 JSON 序列化往返`() {
        val p = IntervalSequenceProgress()
        IntervalSequenceDifficulty.values().forEach { diff ->
            p.recordSession(diff, 7, 10, 3)
        }
        val json = p.toJson()
        val restored = IntervalSequenceProgress.fromJson(json)
        IntervalSequenceDifficulty.values().forEach { diff ->
            assertEquals(10, restored.getProgress(diff).totalAnswered)
            assertEquals(7, restored.getProgress(diff).totalCorrect)
        }
    }

    @Test
    fun `overallAccuracy 计算正确`() {
        val p = IntervalSequenceProgress()
        p.recordSession(IntervalSequenceDifficulty.BEGINNER, 5, 10, 0)
        p.recordSession(IntervalSequenceDifficulty.INTERMEDIATE, 8, 10, 0)
        assertEquals(13.0 / 20.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy 超过 1 点 0 回退`() {
        val json = """{"stats":[{"difficulty":"BEGINNER","totalAnswered":10,"totalCorrect":8,"bestAccuracy":1.5,"bestStreak":5}]}"""
        val p = IntervalSequenceProgress.fromJson(json)
        assertEquals(1.0, p.getProgress(IntervalSequenceDifficulty.BEGINNER).bestAccuracy, 0.001)
    }
}
