package com.pianocompanion.melodymemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旋律记忆训练进度跟踪单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class MelodyMemoryProgressTest {

    // ── 空进度 ────────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = MelodyMemoryProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `getProgress returns empty entry for unknown key`() {
        val p = MelodyMemoryProgress()
        val entry = p.getProgress(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── 单次会话 ──────────────────────────────────────────

    @Test
    fun `single session updates stats`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 8, 10, 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `single session updates specific entry`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.INTERMEDIATE, MelodyTempo.NORMAL, 5, 10, 3)
        val entry = p.getProgress(MelodyDifficulty.INTERMEDIATE, MelodyTempo.NORMAL)
        assertEquals(1, entry.sessionCount)
        assertEquals(10, entry.totalAnswered)
        assertEquals(5, entry.totalCorrect)
        assertEquals(3, entry.bestStreak)
        assertEquals(0.5, entry.bestAccuracy, 0.001)
    }

    // ── 多次会话累计 ──────────────────────────────────────

    @Test
    fun `multiple sessions accumulate in same key`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 8, 10, 5)
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 6, 10, 4)
        val entry = p.getProgress(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW)
        assertEquals(2, entry.sessionCount)
        assertEquals(20, entry.totalAnswered)
        assertEquals(14, entry.totalCorrect)
        assertEquals(0.7, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `best streak does not decrease across sessions`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.ADVANCED, MelodyTempo.NORMAL, 10, 10, 7)
        p.recordSession(MelodyDifficulty.ADVANCED, MelodyTempo.NORMAL, 5, 10, 3)
        val entry = p.getProgress(MelodyDifficulty.ADVANCED, MelodyTempo.NORMAL)
        assertEquals(7, entry.bestStreak)
    }

    @Test
    fun `best accuracy does not decrease across sessions`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 9, 10, 5) // 0.9
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 5, 10, 3) // 0.5
        val entry = p.getProgress(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `best streak increases when better`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.INTERMEDIATE, MelodyTempo.SLOW, 5, 5, 3)
        p.recordSession(MelodyDifficulty.INTERMEDIATE, MelodyTempo.SLOW, 5, 5, 5)
        val entry = p.getProgress(MelodyDifficulty.INTERMEDIATE, MelodyTempo.SLOW)
        assertEquals(5, entry.bestStreak)
    }

    // ── 不同难度/速度分开统计 ────────────────────────────

    @Test
    fun `different difficulty stats are separate`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 10, 10, 5)
        p.recordSession(MelodyDifficulty.ADVANCED, MelodyTempo.SLOW, 3, 10, 2)
        assertEquals(5, p.getProgress(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW).bestStreak)
        assertEquals(2, p.getProgress(MelodyDifficulty.ADVANCED, MelodyTempo.SLOW).bestStreak)
    }

    @Test
    fun `different tempo stats are separate`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.INTERMEDIATE, MelodyTempo.SLOW, 8, 10, 4)
        p.recordSession(MelodyDifficulty.INTERMEDIATE, MelodyTempo.NORMAL, 3, 10, 1)
        assertEquals(4, p.getProgress(MelodyDifficulty.INTERMEDIATE, MelodyTempo.SLOW).bestStreak)
        assertEquals(1, p.getProgress(MelodyDifficulty.INTERMEDIATE, MelodyTempo.NORMAL).bestStreak)
    }

    // ── 全局汇总 ──────────────────────────────────────────

    @Test
    fun `global totals sum all keys`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 8, 10, 5)
        p.recordSession(MelodyDifficulty.INTERMEDIATE, MelodyTempo.NORMAL, 6, 10, 3)
        p.recordSession(MelodyDifficulty.ADVANCED, MelodyTempo.SLOW, 4, 10, 2)
        assertEquals(3, p.totalSessions)
        assertEquals(30, p.totalAnswered)
        assertEquals(18, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    // ── JSON 序列化 ───────────────────────────────────────

    @Test
    fun `json round trip preserves data`() {
        val p = MelodyMemoryProgress()
        p.recordSession(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW, 8, 10, 5)
        p.recordSession(MelodyDifficulty.ADVANCED, MelodyTempo.NORMAL, 6, 10, 7)
        val json = p.toJson()
        val restored = MelodyMemoryProgress.fromJson(json)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `json round trip multiple keys`() {
        val p = MelodyMemoryProgress()
        MelodyDifficulty.ALL.forEach { d ->
            MelodyTempo.ALL.forEach { t ->
                p.recordSession(d, t, 5, 10, 3)
            }
        }
        val json = p.toJson()
        val restored = MelodyMemoryProgress.fromJson(json)
        assertEquals(6, restored.totalSessions)
        assertEquals(60, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles empty string`() {
        val p = MelodyMemoryProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles corrupted json`() {
        val p = MelodyMemoryProgress.fromJson("{corrupted!!!")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles json without stats`() {
        val p = MelodyMemoryProgress.fromJson("{\"other\":123}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles empty stats object`() {
        val p = MelodyMemoryProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    // ── 键格式 ────────────────────────────────────────────

    @Test
    fun `key format is difficulty underscore tempo`() {
        assertEquals("BEGINNER_SLOW", MelodyMemoryProgress.key(MelodyDifficulty.BEGINNER, MelodyTempo.SLOW))
        assertEquals("ADVANCED_NORMAL", MelodyMemoryProgress.key(MelodyDifficulty.ADVANCED, MelodyTempo.NORMAL))
    }

    // ── Entry 独立序列化 ─────────────────────────────────

    @Test
    fun `entry json round trip`() {
        val entry = MelodyMemoryProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = MelodyMemoryProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulative accuracy is zero with no answers`() {
        val entry = MelodyMemoryProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }
}
