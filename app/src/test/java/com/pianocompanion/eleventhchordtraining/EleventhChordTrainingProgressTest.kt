package com.pianocompanion.eleventhchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十一和弦色彩听辨训练进度跟踪单元测试。
 *
 * 验证分难度累计、全局汇总、bestAccuracy/bestStreak 不降级、JSON 往返、容错解析。
 */
class EleventhChordTrainingProgressTest {

    // ── 分难度累计 ──────────────────────────────────────────

    @Test
    fun `recordSession adds to correct difficulty`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 8, 10, 3)
        val entry = progress.getProgress(EleventhChordDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
    }

    @Test
    fun `different difficulties are tracked separately`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 5, 10, 2)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 3, 5, 1)
        assertEquals(10, progress.getProgress(EleventhChordDifficulty.BEGINNER).totalAnswered)
        assertEquals(5, progress.getProgress(EleventhChordDifficulty.ADVANCED).totalAnswered)
        assertEquals(0, progress.getProgress(EleventhChordDifficulty.INTERMEDIATE).totalAnswered)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.INTERMEDIATE, 7, 10, 3)
        progress.recordSession(EleventhChordDifficulty.INTERMEDIATE, 9, 10, 5)
        val entry = progress.getProgress(EleventhChordDifficulty.INTERMEDIATE)
        assertEquals(20, entry.totalAnswered)
        assertEquals(16, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
    }

    // ── 全局汇总 ──────────────────────────────────────────────

    @Test
    fun `totalAnswered sums all difficulties`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 5, 10, 2)
        progress.recordSession(EleventhChordDifficulty.INTERMEDIATE, 3, 8, 1)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 1, 5, 0)
        assertEquals(23, progress.totalAnswered)
        assertEquals(9, progress.totalCorrect)
    }

    @Test
    fun `overallAccuracy calculates correctly`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 8, 10, 0)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 2, 10, 0)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `overallAccuracy is zero with no data`() {
        val progress = EleventhChordTrainingProgress()
        assertEquals(0.0, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak is max across difficulties`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(EleventhChordDifficulty.INTERMEDIATE, 5, 10, 7)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 5, 10, 5)
        assertEquals(7, progress.overallBestStreak)
    }

    @Test
    fun `totalSessions sums correctly`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 5, 10, 0)
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 3, 5, 0)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 2, 4, 0)
        assertEquals(3, progress.totalSessions)
    }

    // ── bestAccuracy / bestStreak 不降级 ──────────────────────

    @Test
    fun `bestAccuracy does not decrease`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 9, 10, 0)
        assertEquals(0.9, progress.getProgress(EleventhChordDifficulty.BEGINNER).bestAccuracy, 0.001)
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 1, 10, 0)
        assertEquals(0.9, progress.getProgress(EleventhChordDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `bestStreak does not decrease`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 5, 10, 8)
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(8, progress.getProgress(EleventhChordDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `bestAccuracy updates when higher`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 5, 10, 0)
        assertEquals(0.5, progress.getProgress(EleventhChordDifficulty.ADVANCED).bestAccuracy, 0.001)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 10, 10, 0)
        assertEquals(1.0, progress.getProgress(EleventhChordDifficulty.ADVANCED).bestAccuracy, 0.001)
    }

    @Test
    fun `bestStreak updates when higher`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 5, 10, 3)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 5, 10, 6)
        assertEquals(6, progress.getProgress(EleventhChordDifficulty.ADVANCED).bestStreak)
    }

    // ── JSON 往返 ──────────────────────────────────────────────

    @Test
    fun `toJson and fromJson round trip`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.BEGINNER, 8, 10, 3)
        progress.recordSession(EleventhChordDifficulty.ADVANCED, 5, 8, 2)
        val json = progress.toJson()
        val restored = EleventhChordTrainingProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `toJson and fromJson preserves per-difficulty data`() {
        val progress = EleventhChordTrainingProgress()
        progress.recordSession(EleventhChordDifficulty.INTERMEDIATE, 7, 10, 4)
        val json = progress.toJson()
        val restored = EleventhChordTrainingProgress.fromJson(json)
        val entry = restored.getProgress(EleventhChordDifficulty.INTERMEDIATE)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(4, entry.bestStreak)
        assertEquals(0.7, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `empty progress toJson is valid`() {
        val progress = EleventhChordTrainingProgress()
        val json = progress.toJson()
        val restored = EleventhChordTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ──────────────────────────────────────────────

    @Test
    fun `fromJson handles empty string`() {
        val restored = EleventhChordTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles corrupted JSON`() {
        val restored = EleventhChordTrainingProgress.fromJson("{corrupted: not json")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats key`() {
        val restored = EleventhChordTrainingProgress.fromJson("{\"foo\":\"bar\"}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles partial entry`() {
        // An entry with only some fields
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5,"totalCorrect":3}}}"""
        val restored = EleventhChordTrainingProgress.fromJson(json)
        val entry = restored.getProgress(EleventhChordDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(3, entry.totalCorrect)
        assertEquals(0, entry.sessionCount) // missing → default 0
    }

    @Test
    fun `fromJson ignores unknown difficulties`() {
        val json = """{"stats":{"UNKNOWN_DIFFICULTY":{"totalAnswered":10,"totalCorrect":5}}}"""
        val restored = EleventhChordTrainingProgress.fromJson(json)
        // Should not crash, just store under the unknown key
        assertEquals(10, restored.totalAnswered)
    }

    @Test
    fun `cumulative accuracy for entry`() {
        val entry = EleventhChordTrainingProgressEntry(
            totalAnswered = 20,
            totalCorrect = 15
        )
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy is zero with no answers`() {
        val entry = EleventhChordTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }
}
