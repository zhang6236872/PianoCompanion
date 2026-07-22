package com.pianocompanion.modescale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调式音阶色彩对比训练进度跟踪模型单元测试。
 */
class ModeScaleProgressTest {

    // ── 记录会话 ──────────────────────────────────

    @Test
    fun `recordSession updates entry for difficulty`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 5)
        val entry = progress.getProgress(ModeScaleDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `recordSession accumulates across sessions`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, correct = 5, total = 10, bestStreak = 3)
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, correct = 7, total = 10, bestStreak = 4)
        val entry = progress.getProgress(ModeScaleDifficulty.INTERMEDIATE)
        assertEquals(20, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(4, entry.bestStreak)
    }

    @Test
    fun `bestStreak only increases`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 5)
        progress.recordSession(ModeScaleDifficulty.ADVANCED, correct = 5, total = 10, bestStreak = 2)
        val entry = progress.getProgress(ModeScaleDifficulty.ADVANCED)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `bestAccuracy tracks highest session accuracy`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, correct = 7, total = 10, bestStreak = 3)
        assertEquals(0.7, progress.getProgress(ModeScaleDifficulty.BEGINNER).bestAccuracy, 0.01)
        progress.recordSession(ModeScaleDifficulty.BEGINNER, correct = 9, total = 10, bestStreak = 5)
        assertEquals(0.9, progress.getProgress(ModeScaleDifficulty.BEGINNER).bestAccuracy, 0.01)
    }

    @Test
    fun `total zero does not update accuracy`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, correct = 0, total = 0, bestStreak = 0)
        val entry = progress.getProgress(ModeScaleDifficulty.BEGINNER)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── 多难度隔离 ──────────────────────────────────

    @Test
    fun `different difficulties are tracked separately`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 5)
        progress.recordSession(ModeScaleDifficulty.ADVANCED, correct = 3, total = 10, bestStreak = 2)
        assertEquals(10, progress.getProgress(ModeScaleDifficulty.BEGINNER).totalAnswered)
        assertEquals(10, progress.getProgress(ModeScaleDifficulty.ADVANCED).totalAnswered)
        assertEquals(8, progress.getProgress(ModeScaleDifficulty.BEGINNER).totalCorrect)
        assertEquals(3, progress.getProgress(ModeScaleDifficulty.ADVANCED).totalCorrect)
    }

    // ── 聚合统计 ──────────────────────────────────

    @Test
    fun `totalSessions sums across difficulties`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, 7, 10, 4)
        progress.recordSession(ModeScaleDifficulty.ADVANCED, 3, 10, 1)
        assertEquals(3, progress.totalSessions)
    }

    @Test
    fun `totalAnswered sums across difficulties`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, 7, 10, 4)
        assertEquals(20, progress.totalAnswered)
    }

    @Test
    fun `totalCorrect sums across difficulties`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, 7, 10, 4)
        assertEquals(12, progress.totalCorrect)
    }

    @Test
    fun `overallAccuracy computes correctly`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, 7, 10, 4)
        assertEquals(0.6, progress.overallAccuracy, 0.01)
    }

    @Test
    fun `overallBestStreak finds maximum across difficulties`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ModeScaleDifficulty.INTERMEDIATE, 7, 10, 8)
        progress.recordSession(ModeScaleDifficulty.ADVANCED, 3, 10, 5)
        assertEquals(8, progress.overallBestStreak)
    }

    @Test
    fun `empty progress has zero stats`() {
        val progress = ModeScaleProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    // ── JSON 序列化 ──────────────────────────────────

    @Test
    fun `toJson and fromJson roundtrip preserves data`() {
        val progress = ModeScaleProgress()
        progress.recordSession(ModeScaleDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(ModeScaleDifficulty.ADVANCED, 6, 15, 4)
        val json = progress.toJson()
        val restored = ModeScaleProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)

        val origBeginner = progress.getProgress(ModeScaleDifficulty.BEGINNER)
        val restoredBeginner = restored.getProgress(ModeScaleDifficulty.BEGINNER)
        assertEquals(origBeginner.totalAnswered, restoredBeginner.totalAnswered)
        assertEquals(origBeginner.totalCorrect, restoredBeginner.totalCorrect)
        assertEquals(origBeginner.bestStreak, restoredBeginner.bestStreak)
        assertEquals(origBeginner.bestAccuracy, restoredBeginner.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson with invalid JSON returns empty progress`() {
        val progress = ModeScaleProgress.fromJson("not json at all")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with missing stats key returns empty progress`() {
        val progress = ModeScaleProgress.fromJson("{\"other\":{}}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson with incomplete entry is rejected`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10}}}"
        val progress = ModeScaleProgress.fromJson(json)
        // Entry missing required fields should be rejected
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `fromJson handles negative values gracefully`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":-5,\"totalCorrect\":-3,\"sessionCount\":1,\"bestStreak\":-1,\"bestAccuracy\":0.5}}}"
        val progress = ModeScaleProgress.fromJson(json)
        val entry = progress.getProgress(ModeScaleDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
    }

    @Test
    fun `cumulativeAccuracy computes correctly`() {
        val entry = ModeScaleProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy is zero when no questions`() {
        val entry = ModeScaleProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }
}
