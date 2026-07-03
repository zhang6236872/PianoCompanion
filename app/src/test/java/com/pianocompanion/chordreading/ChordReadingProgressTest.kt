package com.pianocompanion.chordreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordReadingProgress] 单元测试。
 *
 * 验证：
 * - recordSession 累计统计
 * - getProgress 按谱号+难度隔离
 * - 全局统计汇总
 * - JSON 序列化/反序列化往返
 */
class ChordReadingProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = ChordReadingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession accumulates counts`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 8, 10, 5)
        val entry = p.getProgress(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 6, 10, 7)
        val entry = p.getProgress(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertEquals(20, entry.totalAnswered)
        assertEquals(14, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(7, entry.bestStreak) // 取最大
    }

    @Test
    fun `different clef difficulty combos are isolated`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 5, 10, 2)
        p.recordSession(ChordReadingClef.BASS, ChordReadingDifficulty.ADVANCED, 3, 10, 4)
        val treble = p.getProgress(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        val bass = p.getProgress(ChordReadingClef.BASS, ChordReadingDifficulty.ADVANCED)
        assertEquals(10, treble.totalAnswered)
        assertEquals(5, treble.totalCorrect)
        assertEquals(10, bass.totalAnswered)
        assertEquals(3, bass.totalCorrect)
    }

    @Test
    fun `global totals aggregate across all combos`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 5, 10, 2)
        p.recordSession(ChordReadingClef.BASS, ChordReadingDifficulty.ADVANCED, 3, 10, 4)
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.INTERMEDIATE, 7, 10, 6)
        assertEquals(3, p.totalSessions)
        assertEquals(30, p.totalAnswered)
        assertEquals(15, p.totalCorrect)
        assertEquals(0.5, p.overallAccuracy, 0.001)
    }

    @Test
    fun `best accuracy tracks max session accuracy`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 5, 10, 2) // 0.5
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 9, 10, 3) // 0.9
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 4, 10, 1) // 0.4
        val entry = p.getProgress(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `json round trip preserves data`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(ChordReadingClef.BASS, ChordReadingDifficulty.ADVANCED, 3, 7, 2)
        val json = p.toJson()
        val restored = ChordReadingProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        val t = restored.getProgress(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertEquals(10, t.totalAnswered)
        assertEquals(8, t.totalCorrect)
        assertEquals(5, t.bestStreak)
        val b = restored.getProgress(ChordReadingClef.BASS, ChordReadingDifficulty.ADVANCED)
        assertEquals(7, b.totalAnswered)
        assertEquals(3, b.totalCorrect)
    }

    @Test
    fun `fromJson handles empty json gracefully`() {
        val p = ChordReadingProgress.fromJson("{}")
        assertNotNull(p)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles invalid json gracefully`() {
        val p = ChordReadingProgress.fromJson("not valid json {{{")
        assertNotNull(p)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `key format is clef underscore difficulty`() {
        val key = ChordReadingProgress.key(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertEquals("TREBLE_BEGINNER", key)
    }

    @Test
    fun `getProgress returns empty entry for unrecorded combo`() {
        val p = ChordReadingProgress()
        val entry = p.getProgress(ChordReadingClef.BASS, ChordReadingDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy computes correctly`() {
        val p = ChordReadingProgress()
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 3, 10, 1)
        p.recordSession(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER, 7, 10, 2)
        val entry = p.getProgress(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.001)
    }
}
