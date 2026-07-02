package com.pianocompanion.notation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoteReadingProgress] 单元测试。
 *
 * 验证：
 * - 跨会话进度记录（谱号×难度组合）
 * - 累计统计（总会话数/答题数/正确数/准确率）
 * - 最佳连击和最佳准确率追踪
 * - JSON 序列化/反序列化的往返一致性
 * - 损坏 JSON 的容错处理
 */
class NoteReadingProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = NoteReadingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession accumulates stats`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(5, p.totalCorrect)
        assertEquals(0.5, p.overallAccuracy, 0.001)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 3, 5, 2)
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 4, 5, 3)
        assertEquals(2, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
    }

    @Test
    fun `different clef difficulty tracked separately`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(NoteReadingClef.BASS, NoteReadingDifficulty.ADVANCED, 2, 8, 5)
        assertEquals(2, p.totalSessions)
        assertEquals(18, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
        // Individual
        val trebleBeg = p.getProgress(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertEquals(10, trebleBeg.totalAnswered)
        assertEquals(5, trebleBeg.totalCorrect)
        val bassAdv = p.getProgress(NoteReadingClef.BASS, NoteReadingDifficulty.ADVANCED)
        assertEquals(8, bassAdv.totalAnswered)
        assertEquals(2, bassAdv.totalCorrect)
    }

    @Test
    fun `getProgress returns empty for unknown combination`() {
        val p = NoteReadingProgress()
        val entry = p.getProgress(NoteReadingClef.TREBLE, NoteReadingDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.sessionCount)
    }

    @Test
    fun `best streak tracks maximum across sessions`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 3, 5, 4)
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 3, 5, 6)
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 3, 5, 2)
        val entry = p.getProgress(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertEquals(6, entry.bestStreak)
    }

    @Test
    fun `best accuracy tracks maximum session accuracy`() {
        val p = NoteReadingProgress()
        // Session 1: 80% accuracy
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 8, 10, 3)
        // Session 2: 60% accuracy
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 6, 10, 2)
        val entry = p.getProgress(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertEquals(0.8, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `overall accuracy combines all combinations`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(NoteReadingClef.BASS, NoteReadingDifficulty.INTERMEDIATE, 3, 10, 5)
        // Total: 8/20 = 0.4
        assertEquals(0.4, p.overallAccuracy, 0.001)
    }

    @Test
    fun `session with zero total does not update bestAccuracy`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 0, 0, 0)
        val entry = p.getProgress(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── JSON 序列化 ──────────────────────────────────────

    @Test
    fun `toJson and fromJson round trip preserves data`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(NoteReadingClef.BASS, NoteReadingDifficulty.ADVANCED, 8, 15, 7)
        val json = p.toJson()
        val restored = NoteReadingProgress.fromJson(json)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `fromJson preserves individual entries`() {
        val p = NoteReadingProgress()
        p.recordSession(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER, 5, 10, 3)
        val json = p.toJson()
        val restored = NoteReadingProgress.fromJson(json)
        val entry = restored.getProgress(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(5, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
        assertEquals(0.5, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson handles empty json`() {
        val restored = NoteReadingProgress.fromJson("{}")
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `fromJson handles invalid json gracefully`() {
        val restored = NoteReadingProgress.fromJson("not valid json at all")
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `fromJson handles null stats`() {
        val restored = NoteReadingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `toJson of empty progress is valid`() {
        val p = NoteReadingProgress()
        val json = p.toJson()
        assertNotNull(json)
        assertTrue(json.contains("stats"))
    }

    @Test
    fun `ProgressEntry cumulativeAccuracy`() {
        val entry = NoteReadingProgressEntry(
            totalAnswered = 20,
            totalCorrect = 15,
            sessionCount = 3,
            bestStreak = 8,
            bestAccuracy = 0.9
        )
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry with zero answered has zero accuracy`() {
        val entry = NoteReadingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry json round trip`() {
        val entry = NoteReadingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = NoteReadingProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(30, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.85, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry fromJson returns null for invalid json`() {
        assertNull(NoteReadingProgressEntry.fromJson("invalid"))
        assertNull(NoteReadingProgressEntry.fromJson("[1,2,3]"))
    }

    @Test
    fun `key function generates correct format`() {
        val key = NoteReadingProgress.key(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertEquals("TREBLE_BEGINNER", key)
    }

    @Test
    fun `all six clef-difficulty combinations are independently tracked`() {
        val p = NoteReadingProgress()
        for (clef in NoteReadingClef.ALL) {
            for (diff in NoteReadingDifficulty.ALL) {
                p.recordSession(clef, diff, 1, 1, 1)
            }
        }
        assertEquals(6, p.totalSessions)
        assertEquals(6, p.totalAnswered)
        for (clef in NoteReadingClef.ALL) {
            for (diff in NoteReadingDifficulty.ALL) {
                val entry = p.getProgress(clef, diff)
                assertEquals(1, entry.sessionCount)
                assertEquals(1, entry.totalAnswered)
            }
        }
    }
}
