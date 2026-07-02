package com.pianocompanion.interval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IntervalProgress] 单元测试。
 *
 * 验证：
 * - 会话结果记录（累计答题/正确/会话数）
 * - 各谱号+难度独立统计
 * - 最长连击跟踪
 * - 最佳准确率跟踪
 * - JSON 序列化/反序列化的往返一致性
 * - 空进度 / 损坏 JSON 的容错处理
 * - 全局汇总统计
 */
class IntervalProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val progress = IntervalProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession stores correct totals`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 8, 10, 5)
        val entry = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 7, 10, 6)
        val entry = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertEquals(20, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(6, entry.bestStreak)
    }

    @Test
    fun `different clef and difficulty tracked separately`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(IntervalClef.BASS, IntervalDifficulty.ADVANCED, 8, 10, 7)

        val treble = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        val bass = progress.getProgress(IntervalClef.BASS, IntervalDifficulty.ADVANCED)

        assertEquals(10, treble.totalAnswered)
        assertEquals(5, treble.totalCorrect)
        assertEquals(3, treble.bestStreak)

        assertEquals(10, bass.totalAnswered)
        assertEquals(8, bass.totalCorrect)
        assertEquals(7, bass.bestStreak)
    }

    @Test
    fun `best streak is max not last`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 5, 10, 8)
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 5, 10, 3)
        val entry = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertEquals(8, entry.bestStreak)
    }

    @Test
    fun `best accuracy is max not last`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 3, 10, 1) // 30%
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 9, 10, 5) // 90%
        val entry = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy is correct`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 5, 10, 0)
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 8, 10, 0)
        val entry = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertEquals(0.65, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `overall accuracy across all combinations`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 5, 10, 0)
        progress.recordSession(IntervalClef.BASS, IntervalDifficulty.ADVANCED, 5, 10, 0)
        assertEquals(20, progress.totalAnswered)
        assertEquals(10, progress.totalCorrect)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `total sessions across combinations`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 1, 1, 0)
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE, 1, 1, 0)
        progress.recordSession(IntervalClef.BASS, IntervalDifficulty.BEGINNER, 1, 1, 0)
        assertEquals(3, progress.totalSessions)
    }

    @Test
    fun `getProgress returns empty entry for unrecorded combination`() {
        val progress = IntervalProgress()
        val entry = progress.getProgress(IntervalClef.TREBLE, IntervalDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.sessionCount)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── JSON 序列化 ──────────────────────────────────────

    @Test
    fun `toJson produces valid JSON`() {
        val progress = IntervalProgress()
        progress.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 8, 10, 5)
        val json = progress.toJson()
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("stats"))
        assertTrue(json.contains("TREBLE_BEGINNER"))
        assertTrue(json.contains("\"totalAnswered\":10"))
        assertTrue(json.contains("\"totalCorrect\":8"))
        assertTrue(json.contains("\"bestStreak\":5"))
    }

    @Test
    fun `fromJson restores saved progress`() {
        val original = IntervalProgress()
        original.recordSession(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(IntervalClef.BASS, IntervalDifficulty.ADVANCED, 6, 12, 4)

        val json = original.toJson()
        val restored = IntervalProgress.fromJson(json)

        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.totalSessions, restored.totalSessions)

        val treble = restored.getProgress(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertEquals(10, treble.totalAnswered)
        assertEquals(8, treble.totalCorrect)
        assertEquals(5, treble.bestStreak)

        val bass = restored.getProgress(IntervalClef.BASS, IntervalDifficulty.ADVANCED)
        assertEquals(12, bass.totalAnswered)
        assertEquals(6, bass.totalCorrect)
        assertEquals(4, bass.bestStreak)
    }

    @Test
    fun `fromJson of empty JSON returns empty progress`() {
        val restored = IntervalProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson of corrupt JSON returns empty progress`() {
        val restored = IntervalProgress.fromJson("not valid json at all!!!")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0.0, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `fromJson of empty string returns empty progress`() {
        val restored = IntervalProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `round trip preserves bestAccuracy`() {
        val original = IntervalProgress()
        original.recordSession(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE, 9, 10, 3)
        val json = original.toJson()
        val restored = IntervalProgress.fromJson(json)
        val entry = restored.getProgress(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE)
        assertEquals(0.9, entry.bestAccuracy, 0.01)
    }

    @Test
    fun `key format is CLEF_DIFFICULTY`() {
        assertEquals("TREBLE_BEGINNER", IntervalProgress.key(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER))
        assertEquals("BASS_ADVANCED", IntervalProgress.key(IntervalClef.BASS, IntervalDifficulty.ADVANCED))
    }
}
