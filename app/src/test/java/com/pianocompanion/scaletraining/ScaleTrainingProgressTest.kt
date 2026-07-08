package com.pianocompanion.scaletraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音阶听辨训练进度跟踪单元测试。
 */
class ScaleTrainingProgressTest {

    // ── 基本记录 ──────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = ScaleTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates counts`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 8, 10, 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 5, 10, 2)
        p.recordSession(ScaleDifficulty.BEGINNER, 7, 10, 4)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
        assertEquals(4, p.overallBestStreak)
    }

    @Test
    fun `different difficulties tracked separately`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(ScaleDifficulty.ADVANCED, 5, 10, 6)

        val beg = p.getProgress(ScaleDifficulty.BEGINNER)
        val adv = p.getProgress(ScaleDifficulty.ADVANCED)

        assertEquals(10, beg.totalAnswered)
        assertEquals(8, beg.totalCorrect)
        assertEquals(3, beg.bestStreak)

        assertEquals(10, adv.totalAnswered)
        assertEquals(5, adv.totalCorrect)
        assertEquals(6, adv.bestStreak)
    }

    @Test
    fun `total sessions sums across difficulties`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 5, 10, 1)
        p.recordSession(ScaleDifficulty.INTERMEDIATE, 6, 10, 2)
        p.recordSession(ScaleDifficulty.ADVANCED, 7, 10, 3)
        assertEquals(3, p.totalSessions)
    }

    @Test
    fun `overall accuracy spans all difficulties`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 8, 10, 1)
        p.recordSession(ScaleDifficulty.ADVANCED, 4, 10, 1)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
    }

    // ── bestStreak 不降级 ────────────────────────────────

    @Test
    fun `bestStreak does not decrease with lower streak session`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 5, 10, 5)
        p.recordSession(ScaleDifficulty.BEGINNER, 3, 10, 2)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `bestStreak updates with higher streak`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 5, 10, 3)
        assertEquals(3, p.overallBestStreak)
        p.recordSession(ScaleDifficulty.BEGINNER, 7, 10, 8)
        assertEquals(8, p.overallBestStreak)
    }

    // ── bestAccuracy ──────────────────────────────────────

    @Test
    fun `bestAccuracy tracks best session accuracy`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 5, 10, 1)
        val e1 = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(0.5, e1.bestAccuracy, 0.001)

        p.recordSession(ScaleDifficulty.BEGINNER, 9, 10, 1)
        val e2 = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(0.9, e2.bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy does not decrease`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 9, 10, 1)
        p.recordSession(ScaleDifficulty.BEGINNER, 3, 10, 1)
        val e = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(0.9, e.bestAccuracy, 0.001)
    }

    @Test
    fun `zero total session does not update bestAccuracy`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 0, 0, 0)
        val e = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(0.0, e.bestAccuracy, 0.001)
    }

    // ── cumulativeAccuracy ────────────────────────────────

    @Test
    fun `cumulativeAccuracy for entry`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 7, 10, 1)
        p.recordSession(ScaleDifficulty.BEGINNER, 3, 10, 1)
        val e = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(0.5, e.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `empty entry has zero cumulativeAccuracy`() {
        val p = ScaleTrainingProgress()
        val e = p.getProgress(ScaleDifficulty.ADVANCED)
        assertEquals(0.0, e.cumulativeAccuracy, 0.001)
    }

    // ── getProgress for unrecorded difficulty ─────────────

    @Test
    fun `getProgress returns empty entry for unrecorded difficulty`() {
        val p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 5, 10, 1)
        val adv = p.getProgress(ScaleDifficulty.ADVANCED)
        assertEquals(0, adv.totalAnswered)
        assertEquals(0, adv.sessionCount)
    }

    // ── JSON 序列化 ──────────────────────────────────────

    @Test
    fun `toJson and fromJson roundtrip preserves data`() {
        val original = ScaleTrainingProgress()
        original.recordSession(ScaleDifficulty.BEGINNER, 8, 10, 3)
        original.recordSession(ScaleDifficulty.INTERMEDIATE, 6, 10, 5)
        original.recordSession(ScaleDifficulty.ADVANCED, 4, 10, 7)

        val json = original.toJson()
        val restored = ScaleTrainingProgress.fromJson(json)

        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)

        for (d in ScaleDifficulty.ALL) {
            val orig = original.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.sessionCount, rest.sessionCount)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `fromJson with empty json returns empty progress`() {
        val p = ScaleTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with malformed json returns empty progress`() {
        val p = ScaleTrainingProgress.fromJson("{ broken json }}}")
        assertNotNull(p)
    }

    @Test
    fun `fromJson with null-like content returns empty`() {
        val p = ScaleTrainingProgress.fromJson("null")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson with missing fields uses defaults`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":5}}}"""
        val p = ScaleTrainingProgress.fromJson(json)
        val entry = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect) // Missing field defaults to 0
        assertEquals(0, entry.sessionCount)
    }

    @Test
    fun `fromJson with partial entry preserves available fields`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":7,"sessionCount":2}}}"""
        val p = ScaleTrainingProgress.fromJson(json)
        val entry = p.getProgress(ScaleDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
    }

    @Test
    fun `empty progress serializes to valid json`() {
        val p = ScaleTrainingProgress()
        val json = p.toJson()
        val restored = ScaleTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── key consistency ───────────────────────────────────

    @Test
    fun `key is difficulty name`() {
        assertEquals("BEGINNER", ScaleTrainingProgress.key(ScaleDifficulty.BEGINNER))
        assertEquals("INTERMEDIATE", ScaleTrainingProgress.key(ScaleDifficulty.INTERMEDIATE))
        assertEquals("ADVANCED", ScaleTrainingProgress.key(ScaleDifficulty.ADVANCED))
    }

    // ── ProgressEntry 独立序列化 ──────────────────────────

    @Test
    fun `entry toJson fromJson roundtrip`() {
        val entry = ScaleTrainingProgressEntry(
            totalAnswered = 100,
            totalCorrect = 75,
            sessionCount = 10,
            bestStreak = 12,
            bestAccuracy = 0.95
        )
        val json = entry.toJson()
        val restored = ScaleTrainingProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(100, restored!!.totalAnswered)
        assertEquals(75, restored.totalCorrect)
        assertEquals(10, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.95, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `entry fromJson with invalid json returns null`() {
        val entry = ScaleTrainingProgressEntry.fromJson("not json")
        assertNull(entry)
    }

    @Test
    fun `entry fromJson with empty string returns null`() {
        val entry = ScaleTrainingProgressEntry.fromJson("")
        assertNull(entry)
    }

    // ── 多次 roundtrip 稳定性 ─────────────────────────────

    @Test
    fun `multiple roundtrips are stable`() {
        var p = ScaleTrainingProgress()
        p.recordSession(ScaleDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(ScaleDifficulty.ADVANCED, 4, 10, 7)

        repeat(5) {
            val json = p.toJson()
            p = ScaleTrainingProgress.fromJson(json)
        }

        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
        assertEquals(7, p.overallBestStreak)
    }
}
