package com.pianocompanion.keysig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KeySigProgress] 单元测试。
 *
 * 验证跨会话进度跟踪、JSON 序列化/反序列化、统计聚合。
 */
class KeySigProgressTest {

    // ── 记录会话 ──────────────────────────────────────────────

    @Test
    fun `recordSession accumulates stats per clef and difficulty`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 7, 10, 4)
        val entry = progress.getProgress(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertEquals(20, entry.totalAnswered)
        assertEquals(15, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `different clef difficulty combinations are tracked separately`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(KeySigClef.BASS, KeySigDifficulty.ADVANCED, 9, 10, 7)
        val treble = progress.getProgress(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        val bass = progress.getProgress(KeySigClef.BASS, KeySigDifficulty.ADVANCED)
        assertEquals(10, treble.totalAnswered)
        assertEquals(10, bass.totalAnswered)
        assertEquals(3, treble.bestStreak)
        assertEquals(7, bass.bestStreak)
    }

    @Test
    fun `getProgress returns empty entry for unrecorded combination`() {
        val progress = KeySigProgress()
        val entry = progress.getProgress(KeySigClef.BASS, KeySigDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── 全局聚合 ──────────────────────────────────────────────

    @Test
    fun `totals aggregate across all combinations`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(KeySigClef.BASS, KeySigDifficulty.ADVANCED, 8, 10, 6)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(13, progress.totalCorrect)
        assertEquals(0.65, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `empty progress has zero totals`() {
        val progress = KeySigProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy tracks highest session accuracy`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 5, 10, 3) // 50%
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 9, 10, 6) // 90%
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 7, 10, 5) // 70%
        val entry = progress.getProgress(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `recordSession with zero total does not affect bestAccuracy`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 0, 0, 0)
        val entry = progress.getProgress(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy reflects running total`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 7, 10, 4)
        val entry = progress.getProgress(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertEquals(0.6, entry.cumulativeAccuracy, 0.001) // 12/20
    }

    // ── JSON 序列化 ───────────────────────────────────────────

    @Test
    fun `toJson and fromJson round trip preserves data`() {
        val progress = KeySigProgress()
        progress.recordSession(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(KeySigClef.BASS, KeySigDifficulty.ADVANCED, 9, 10, 7)
        val json = progress.toJson()
        val restored = KeySigProgress.fromJson(json)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        val treble = restored.getProgress(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertEquals(10, treble.totalAnswered)
        assertEquals(8, treble.totalCorrect)
        assertEquals(5, treble.bestStreak)
        assertEquals(0.8, treble.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson handles empty json`() {
        val restored = KeySigProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles malformed json gracefully`() {
        val restored = KeySigProgress.fromJson("not valid json {{{")
        assertNotNull(restored)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `toJson of empty progress is valid`() {
        val progress = KeySigProgress()
        val json = progress.toJson()
        val restored = KeySigProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `key format is CLEF_DIFFICULTY`() {
        val key = KeySigProgress.key(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertEquals("TREBLE_BEGINNER", key)
    }

    @Test
    fun `single entry serialization round trip`() {
        val entry = KeySigProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.875
        )
        val json = entry.toJson()
        val restored = KeySigProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(35, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.875, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `fromJson null on malformed entry`() {
        val restored = KeySigProgressEntry.fromJson("garbage")
        assertEquals(null, restored)
    }
}
