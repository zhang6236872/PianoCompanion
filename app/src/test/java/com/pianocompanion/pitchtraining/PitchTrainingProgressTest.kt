package com.pianocompanion.pitchtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绝对音高训练进度跟踪单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class PitchTrainingProgressTest {

    // ── 空进度 ────────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = PitchTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `getProgress on empty returns default entry`() {
        val p = PitchTrainingProgress()
        val entry = p.getProgress(PitchTrainingDifficulty.BEGINNER)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    // ── 单次记录 ──────────────────────────────────────────

    @Test
    fun `recordSession adds to totals`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.BEGINNER, correct = 7, total = 10, bestStreak = 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
        assertEquals(5, p.overallBestStreak)
        assertEquals(0.7, p.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession updates entry stats`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.INTERMEDIATE, correct = 5, total = 8, bestStreak = 3)
        val entry = p.getProgress(PitchTrainingDifficulty.INTERMEDIATE)
        assertEquals(8, entry.totalAnswered)
        assertEquals(5, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
        assertEquals(0.625, entry.bestAccuracy, 0.001)
        assertEquals(0.625, entry.cumulativeAccuracy, 0.001)
    }

    // ── 多次记录 ──────────────────────────────────────────

    @Test
    fun `multiple sessions accumulate`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 8, 10, 6)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(13, p.totalCorrect)
        assertEquals(0.65, p.overallAccuracy, 0.001)
    }

    @Test
    fun `bestStreak is max across sessions`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 7)
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 2)
        assertEquals(7, p.overallBestStreak)
        assertEquals(7, p.getProgress(PitchTrainingDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `bestAccuracy tracks best session accuracy`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.ADVANCED, 3, 10, 2) // 30%
        p.recordSession(PitchTrainingDifficulty.ADVANCED, 9, 10, 5) // 90%
        p.recordSession(PitchTrainingDifficulty.ADVANCED, 5, 10, 3) // 50%
        val entry = p.getProgress(PitchTrainingDifficulty.ADVANCED)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    // ── 分难度独立 ────────────────────────────────────────

    @Test
    fun `different difficulties tracked separately`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(PitchTrainingDifficulty.INTERMEDIATE, 7, 10, 4)
        p.recordSession(PitchTrainingDifficulty.ADVANCED, 3, 10, 2)
        assertEquals(10, p.getProgress(PitchTrainingDifficulty.BEGINNER).totalAnswered)
        assertEquals(10, p.getProgress(PitchTrainingDifficulty.INTERMEDIATE).totalAnswered)
        assertEquals(10, p.getProgress(PitchTrainingDifficulty.ADVANCED).totalAnswered)
        assertEquals(5, p.getProgress(PitchTrainingDifficulty.BEGINNER).totalCorrect)
        assertEquals(7, p.getProgress(PitchTrainingDifficulty.INTERMEDIATE).totalCorrect)
        assertEquals(3, p.getProgress(PitchTrainingDifficulty.ADVANCED).totalCorrect)
    }

    @Test
    fun `global stats sum across difficulties`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(PitchTrainingDifficulty.ADVANCED, 8, 10, 6)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(13, p.totalCorrect)
        assertEquals(6, p.overallBestStreak)
    }

    // ── JSON 序列化 ───────────────────────────────────────

    @Test
    fun `toJson fromJson round trip`() {
        val p = PitchTrainingProgress()
        p.recordSession(PitchTrainingDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(PitchTrainingDifficulty.INTERMEDIATE, 7, 10, 4)
        p.recordSession(PitchTrainingDifficulty.ADVANCED, 3, 10, 2)
        val json = p.toJson()
        val restored = PitchTrainingProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `fromJson handles empty json`() {
        val p = PitchTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles corrupted json`() {
        val p = PitchTrainingProgress.fromJson("{invalid json!!!")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles json without stats key`() {
        val p = PitchTrainingProgress.fromJson("{\"other\":123}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles empty stats object`() {
        val p = PitchTrainingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles partial entry`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":7}}}"
        val p = PitchTrainingProgress.fromJson(json)
        val entry = p.getProgress(PitchTrainingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(0, entry.sessionCount) // missing, defaults to 0
    }

    // ── Entry 序列化 ─────────────────────────────────────

    @Test
    fun `entry toJson fromJson round trip`() {
        val entry = PitchTrainingProgressEntry(
            totalAnswered = 50,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.875
        )
        val json = entry.toJson()
        val restored = PitchTrainingProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `entry fromJson returns null for non-object`() {
        assertNull(PitchTrainingProgressEntry.fromJson("not json"))
        assertNull(PitchTrainingProgressEntry.fromJson("[1,2,3]"))
    }

    // ── 键格式 ────────────────────────────────────────────

    @Test
    fun `key format is difficulty name`() {
        assertEquals("BEGINNER", PitchTrainingProgress.key(PitchTrainingDifficulty.BEGINNER))
        assertEquals("INTERMEDIATE", PitchTrainingProgress.key(PitchTrainingDifficulty.INTERMEDIATE))
        assertEquals("ADVANCED", PitchTrainingProgress.key(PitchTrainingDifficulty.ADVANCED))
    }

    // ── 6 键完整往返 ──────────────────────────────────────

    @Test
    fun `all 3 difficulties round trip`() {
        val p = PitchTrainingProgress()
        PitchTrainingDifficulty.ALL.forEach { d ->
            p.recordSession(d, 5, 10, 3)
        }
        val json = p.toJson()
        val restored = PitchTrainingProgress.fromJson(json)
        PitchTrainingDifficulty.ALL.forEach { d ->
            val orig = p.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }
}
