package com.pianocompanion.intervaltraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音程听辨训练进度跟踪单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class IntervalTrainingProgressTest {

    // ── 空进度 ────────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = IntervalTrainingProgress()
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0, p.totalSessions)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `getProgress for unrecorded returns empty entry`() {
        val p = IntervalTrainingProgress()
        val entry = p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── 单次/多次会话累计 ─────────────────────────────────

    @Test
    fun `single session is recorded`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 8, 10, 5)
        val entry = p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
        assertEquals(0.8, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.INTERMEDIATE, PlayDirection.ASCENDING, 7, 10, 3)
        p.recordSession(IntervalDifficulty.INTERMEDIATE, PlayDirection.ASCENDING, 9, 10, 6)
        val entry = p.getProgress(IntervalDifficulty.INTERMEDIATE, PlayDirection.ASCENDING)
        assertEquals(20, entry.totalAnswered)
        assertEquals(16, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(0.8, entry.cumulativeAccuracy, 0.001)
    }

    // ── bestStreak / bestAccuracy 追踪 ───────────────────

    @Test
    fun `bestStreak tracks maximum across sessions`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC, 5, 10, 4)
        assertEquals(4, p.getProgress(IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC).bestStreak)
        p.recordSession(IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC, 8, 10, 7)
        assertEquals(7, p.getProgress(IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC).bestStreak)
    }

    @Test
    fun `bestStreak does not decrease`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 10, 10, 8)
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 5, 10, 3)
        assertEquals(8, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).bestStreak)
    }

    @Test
    fun `bestAccuracy tracks maximum session accuracy`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 6, 10, 3) // 0.6
        assertEquals(0.6, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).bestAccuracy, 0.001)
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 9, 10, 5) // 0.9
        assertEquals(0.9, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).bestAccuracy, 0.001)
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 7, 10, 4) // 0.7
        assertEquals(0.9, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).bestAccuracy, 0.001)
    }

    @Test
    fun `zero total session does not update bestAccuracy`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 8, 10, 5)
        assertEquals(0.8, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).bestAccuracy, 0.001)
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 0, 0, 0)
        assertEquals(0.8, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).bestAccuracy, 0.001)
    }

    // ── 不同难度/方向分开统计 ─────────────────────────────

    @Test
    fun `different difficulties tracked separately`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 10, 10, 5)
        p.recordSession(IntervalDifficulty.ADVANCED, PlayDirection.ASCENDING, 5, 10, 3)
        assertEquals(10, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).totalAnswered)
        assertEquals(10, p.getProgress(IntervalDifficulty.ADVANCED, PlayDirection.ASCENDING).totalAnswered)
    }

    @Test
    fun `different directions tracked separately`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 10, 10, 5)
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.HARMONIC, 5, 10, 3)
        assertEquals(10, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING).totalAnswered)
        assertEquals(10, p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.HARMONIC).totalAnswered)
    }

    // ── 全局汇总统计 ──────────────────────────────────────

    @Test
    fun `global totals aggregate all keys`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 8, 10, 5)
        p.recordSession(IntervalDifficulty.INTERMEDIATE, PlayDirection.DESCENDING, 6, 10, 4)
        p.recordSession(IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC, 5, 10, 3)
        assertEquals(30, p.totalAnswered)
        assertEquals(19, p.totalCorrect)
        assertEquals(3, p.totalSessions)
        assertEquals(5, p.overallBestStreak)
        assertEquals(19.0 / 30.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `global accuracy is zero when no answers`() {
        val p = IntervalTrainingProgress()
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    // ── 键格式 ────────────────────────────────────────────

    @Test
    fun `key format combines difficulty and direction`() {
        assertEquals("BEGINNER_ASCENDING", IntervalTrainingProgress.key(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING))
        assertEquals("ADVANCED_HARMONIC", IntervalTrainingProgress.key(IntervalDifficulty.ADVANCED, PlayDirection.HARMONIC))
    }

    // ── JSON 往返 ─────────────────────────────────────────

    @Test
    fun `json round trip preserves data`() {
        val p = IntervalTrainingProgress()
        p.recordSession(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING, 8, 10, 5)
        p.recordSession(IntervalDifficulty.INTERMEDIATE, PlayDirection.DESCENDING, 6, 10, 4)
        val json = p.toJson()
        val restored = IntervalTrainingProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
    }

    @Test
    fun `json round trip multiple keys`() {
        val p = IntervalTrainingProgress()
        IntervalDifficulty.ALL.forEach { d ->
            PlayDirection.ALL.forEach { dir ->
                p.recordSession(d, dir, 5, 10, 3)
            }
        }
        val json = p.toJson()
        val restored = IntervalTrainingProgress.fromJson(json)
        assertEquals(p.stats.size, restored.stats.size)
        IntervalDifficulty.ALL.forEach { d ->
            PlayDirection.ALL.forEach { dir ->
                val orig = p.getProgress(d, dir)
                val rstr = restored.getProgress(d, dir)
                assertEquals(orig.totalAnswered, rstr.totalAnswered)
                assertEquals(orig.totalCorrect, rstr.totalCorrect)
                assertEquals(orig.bestStreak, rstr.bestStreak)
            }
        }
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `fromJson returns empty for empty string`() {
        val p = IntervalTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson returns empty for corrupt json`() {
        val p = IntervalTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson returns empty for json without stats`() {
        val p = IntervalTrainingProgress.fromJson("{\"other\":\"value\"}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles empty stats object`() {
        val p = IntervalTrainingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles partial entry`() {
        val json = "{\"stats\":{\"BEGINNER_ASCENDING\":{\"totalAnswered\":10,\"totalCorrect\":8}}}"
        val p = IntervalTrainingProgress.fromJson(json)
        val entry = p.getProgress(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(0, entry.sessionCount) // 缺失字段默认 0
    }

    // ── Entry 独立序列化 ─────────────────────────────────

    @Test
    fun `entry json round trip`() {
        val entry = IntervalTrainingProgressEntry(
            totalAnswered = 50,
            totalCorrect = 42,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.95
        )
        val json = entry.toJson()
        val restored = IntervalTrainingProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `entry fromJson returns null for invalid`() {
        assertEquals(null, IntervalTrainingProgressEntry.fromJson("not json"))
        assertEquals(null, IntervalTrainingProgressEntry.fromJson(""))
    }

    @Test
    fun `entry cumulativeAccuracy`() {
        val entry = IntervalTrainingProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
        val empty = IntervalTrainingProgressEntry()
        assertEquals(0.0, empty.cumulativeAccuracy, 0.001)
    }
}
