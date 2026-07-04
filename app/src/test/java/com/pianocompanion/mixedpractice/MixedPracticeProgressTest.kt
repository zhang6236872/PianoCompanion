package com.pianocompanion.mixedpractice

import org.junit.Assert.*
import org.junit.Test

/**
 * 综合练习进度持久化单元测试。
 */
class MixedPracticeProgressTest {

    @Test
    fun `空进度的 toJson 和 fromJson 往返一致`() {
        val original = MixedPracticeProgress()
        val json = original.toJson()
        val restored = MixedPracticeProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalCorrect)
        assertEquals(0, restored.totalSessions)
        assertEquals(0.0, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession 按难度记录统计`() {
        val progress = MixedPracticeProgress()
        progress.recordSession(MixedDifficulty.BEGINNER, 8, 10, 5)
        val entry = progress.getDifficultyProgress(MixedDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
        assertEquals(0.8, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `recordQuestion 按题型记录统计`() {
        val progress = MixedPracticeProgress()
        progress.recordQuestion(MixedQuestionType.NOTE_READING, true)
        progress.recordQuestion(MixedQuestionType.NOTE_READING, false)
        progress.recordQuestion(MixedQuestionType.INTERVAL, true)

        val noteEntry = progress.getTypeProgress(MixedQuestionType.NOTE_READING)
        assertEquals(2, noteEntry.totalAnswered)
        assertEquals(1, noteEntry.totalCorrect)

        val intervalEntry = progress.getTypeProgress(MixedQuestionType.INTERVAL)
        assertEquals(1, intervalEntry.totalAnswered)
        assertEquals(1, intervalEntry.totalCorrect)
    }

    @Test
    fun `多次 recordSession 累加统计`() {
        val progress = MixedPracticeProgress()
        progress.recordSession(MixedDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(MixedDifficulty.BEGINNER, 7, 10, 8)

        val entry = progress.getDifficultyProgress(MixedDifficulty.BEGINNER)
        assertEquals(20, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(8, entry.bestStreak)
        assertEquals(0.7, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `bestStreak 取最大值`() {
        val progress = MixedPracticeProgress()
        progress.recordSession(MixedDifficulty.ADVANCED, 5, 10, 3)
        progress.recordSession(MixedDifficulty.ADVANCED, 8, 10, 2)
        val entry = progress.getDifficultyProgress(MixedDifficulty.ADVANCED)
        assertEquals(3, entry.bestStreak)
    }

    @Test
    fun `toJson fromJson 完整往返一致`() {
        val original = MixedPracticeProgress()
        original.recordSession(MixedDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(MixedDifficulty.INTERMEDIATE, 6, 15, 4)
        original.recordQuestion(MixedQuestionType.NOTE_READING, true)
        original.recordQuestion(MixedQuestionType.INTERVAL, false)
        original.recordQuestion(MixedQuestionType.CHORD_READING, true)

        val json = original.toJson()
        val restored = MixedPracticeProgress.fromJson(json)

        // 难度统计
        assertEquals(original.getDifficultyProgress(MixedDifficulty.BEGINNER).totalAnswered,
            restored.getDifficultyProgress(MixedDifficulty.BEGINNER).totalAnswered)
        assertEquals(original.getDifficultyProgress(MixedDifficulty.BEGINNER).totalCorrect,
            restored.getDifficultyProgress(MixedDifficulty.BEGINNER).totalCorrect)
        assertEquals(original.getDifficultyProgress(MixedDifficulty.BEGINNER).bestStreak,
            restored.getDifficultyProgress(MixedDifficulty.BEGINNER).bestStreak)
        assertEquals(original.getDifficultyProgress(MixedDifficulty.BEGINNER).bestAccuracy,
            restored.getDifficultyProgress(MixedDifficulty.BEGINNER).bestAccuracy, 0.0001)

        // 题型统计
        assertEquals(original.getTypeProgress(MixedQuestionType.NOTE_READING).totalAnswered,
            restored.getTypeProgress(MixedQuestionType.NOTE_READING).totalAnswered)
        assertEquals(original.getTypeProgress(MixedQuestionType.NOTE_READING).totalCorrect,
            restored.getTypeProgress(MixedQuestionType.NOTE_READING).totalCorrect)
        assertEquals(original.getTypeProgress(MixedQuestionType.INTERVAL).totalAnswered,
            restored.getTypeProgress(MixedQuestionType.INTERVAL).totalAnswered)

        // 全局统计
        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.0001)
    }

    @Test
    fun `fromJson 解析无效 JSON 返回空进度`() {
        val restored = MixedPracticeProgress.fromJson("invalid json {{{")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalCorrect)
    }

    @Test
    fun `fromJson 解析空字符串返回空进度`() {
        val restored = MixedPracticeProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `overallAccuracy 计算正确`() {
        val progress = MixedPracticeProgress()
        progress.recordSession(MixedDifficulty.BEGINNER, 5, 10, 0)
        progress.recordSession(MixedDifficulty.ADVANCED, 5, 10, 0)
        assertEquals(20, progress.totalAnswered)
        assertEquals(10, progress.totalCorrect)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `MixedProgressEntry toJson fromJson 往返一致`() {
        val entry = MixedProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.8333
        )
        val json = entry.toJson()
        val restored = MixedProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(35, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.8333, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `cumulativeAccuracy 计算正确`() {
        val entry = MixedProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)

        val emptyEntry = MixedProgressEntry()
        assertEquals(0.0, emptyEntry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `不同难度的统计互不干扰`() {
        val progress = MixedPracticeProgress()
        progress.recordSession(MixedDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(MixedDifficulty.ADVANCED, 9, 10, 7)

        val beginner = progress.getDifficultyProgress(MixedDifficulty.BEGINNER)
        val advanced = progress.getDifficultyProgress(MixedDifficulty.ADVANCED)
        val intermediate = progress.getDifficultyProgress(MixedDifficulty.INTERMEDIATE)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(0, intermediate.totalAnswered)
    }

    @Test
    fun `全部 5 种题型统计独立`() {
        val progress = MixedPracticeProgress()
        MixedQuestionType.ALL.forEachIndexed { i, type ->
            repeat(i + 1) { progress.recordQuestion(type, i % 2 == 0) }
        }
        MixedQuestionType.ALL.forEachIndexed { i, type ->
            val entry = progress.getTypeProgress(type)
            assertEquals(i + 1, entry.totalAnswered)
        }
    }
}
