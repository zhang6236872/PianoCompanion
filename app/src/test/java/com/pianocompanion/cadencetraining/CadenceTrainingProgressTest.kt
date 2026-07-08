package com.pianocompanion.cadencetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终止式听辨训练进度跟踪单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：分难度累计统计、全局汇总、JSON 往返、容错解析（损坏/空/缺失字段）、
 * bestAccuracy 更新逻辑、Entry 独立序列化。
 */
class CadenceTrainingProgressTest {

    // ── 基本累计 ──────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = CadenceTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates per difficulty`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, correct = 3, total = 5, bestStreak = 2)
        val entry = p.getProgress(CadenceDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        assertEquals(3, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(2, entry.bestStreak)
    }

    @Test
    fun `recordSession multiple times accumulate in same difficulty`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 3, 5, 2)
        p.recordSession(CadenceDifficulty.BEGINNER, 4, 5, 3)
        val entry = p.getProgress(CadenceDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(3, entry.bestStreak)
    }

    @Test
    fun `recordSession tracks best streak across sessions`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.ADVANCED, 2, 5, 3)
        p.recordSession(CadenceDifficulty.ADVANCED, 2, 5, 1)
        p.recordSession(CadenceDifficulty.ADVANCED, 2, 5, 6)
        assertEquals(6, p.getProgress(CadenceDifficulty.ADVANCED).bestStreak)
    }

    @Test
    fun `different difficulties tracked separately`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 3, 5, 2)
        p.recordSession(CadenceDifficulty.ADVANCED, 1, 5, 1)
        assertEquals(5, p.getProgress(CadenceDifficulty.BEGINNER).totalAnswered)
        assertEquals(5, p.getProgress(CadenceDifficulty.ADVANCED).totalAnswered)
        assertEquals(0, p.getProgress(CadenceDifficulty.INTERMEDIATE).totalAnswered)
    }

    // ── 全局汇总 ──────────────────────────────────────────

    @Test
    fun `totalSessions sums across difficulties`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 1, 2, 1)
        p.recordSession(CadenceDifficulty.INTERMEDIATE, 1, 2, 1)
        p.recordSession(CadenceDifficulty.ADVANCED, 1, 2, 1)
        assertEquals(3, p.totalSessions)
    }

    @Test
    fun `overallAccuracy computes global ratio`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 4, 5, 0)
        p.recordSession(CadenceDifficulty.ADVANCED, 1, 5, 0)
        // 5 correct / 10 total = 0.5
        assertEquals(0.5, p.overallAccuracy, 0.001)
    }

    @Test
    fun `overallBestStreak is max across difficulties`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 1, 5, 3)
        p.recordSession(CadenceDifficulty.ADVANCED, 1, 5, 7)
        p.recordSession(CadenceDifficulty.INTERMEDIATE, 1, 5, 2)
        assertEquals(7, p.overallBestStreak)
    }

    // ── bestAccuracy ─────────────────────────────────────

    @Test
    fun `bestAccuracy tracks highest session accuracy`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 2, 4, 0)  // 0.5
        assertEquals(0.5, p.getProgress(CadenceDifficulty.BEGINNER).bestAccuracy, 0.001)
        p.recordSession(CadenceDifficulty.BEGINNER, 4, 5, 0)  // 0.8
        assertEquals(0.8, p.getProgress(CadenceDifficulty.BEGINNER).bestAccuracy, 0.001)
        p.recordSession(CadenceDifficulty.BEGINNER, 1, 5, 0)  // 0.2, 不应降低
        assertEquals(0.8, p.getProgress(CadenceDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy ignores zero-total sessions`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(CadenceDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── JSON 往返 ─────────────────────────────────────────

    @Test
    fun `JSON round-trip preserves data`() {
        val p = CadenceTrainingProgress()
        p.recordSession(CadenceDifficulty.BEGINNER, 3, 5, 2)
        p.recordSession(CadenceDifficulty.INTERMEDIATE, 4, 6, 3)
        p.recordSession(CadenceDifficulty.ADVANCED, 5, 10, 5)

        val json = p.toJson()
        val restored = CadenceTrainingProgress.fromJson(json)

        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        CadenceDifficulty.ALL.forEach { d ->
            val orig = p.getProgress(d)
            val rest = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rest.totalAnswered)
            assertEquals(orig.totalCorrect, rest.totalCorrect)
            assertEquals(orig.sessionCount, rest.sessionCount)
            assertEquals(orig.bestStreak, rest.bestStreak)
            assertEquals(orig.bestAccuracy, rest.bestAccuracy, 0.0001)
        }
    }

    @Test
    fun `empty progress JSON round-trip`() {
        val p = CadenceTrainingProgress()
        val json = p.toJson()
        val restored = CadenceTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `fromJson handles empty string`() {
        val p = CadenceTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles malformed JSON`() {
        val p = CadenceTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats key`() {
        val p = CadenceTrainingProgress.fromJson("{\"other\":123}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles partial entry gracefully`() {
        // 缺少部分字段的 entry
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5}}}"
        val p = CadenceTrainingProgress.fromJson(json)
        val entry = p.getProgress(CadenceDifficulty.BEGINNER)
        assertEquals(5, entry.totalAnswered)
        // 缺失字段应为默认值
        assertEquals(0, entry.totalCorrect)
    }

    @Test
    fun `fromJson handles null returns empty`() {
        val p = CadenceTrainingProgress.fromJson("null")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles empty stats object`() {
        val p = CadenceTrainingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    // ── ProgressEntry 独立序列化 ─────────────────────────

    @Test
    fun `entry JSON round-trip preserves data`() {
        val entry = CadenceTrainingProgressEntry(
            totalAnswered = 15,
            totalCorrect = 12,
            sessionCount = 3,
            bestStreak = 5,
            bestAccuracy = 0.8333
        )
        val json = entry.toJson()
        val restored = CadenceTrainingProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `entry cumulativeAccuracy computes correctly`() {
        val entry = CadenceTrainingProgressEntry(totalAnswered = 10, totalCorrect = 7)
        assertEquals(0.7, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `entry cumulativeAccuracy zero when no answers`() {
        val entry = CadenceTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `entry fromJson returns null for invalid`() {
        assertEquals(null, CadenceTrainingProgressEntry.fromJson(""))
        assertEquals(null, CadenceTrainingProgressEntry.fromJson("not json"))
        assertEquals(null, CadenceTrainingProgressEntry.fromJson("[1,2,3]"))
    }

    // ── key 一致性 ────────────────────────────────────────

    @Test
    fun `key uses difficulty name`() {
        CadenceDifficulty.ALL.forEach { d ->
            assertEquals(d.name, CadenceTrainingProgress.key(d))
        }
    }

    @Test
    fun `getProgress returns empty entry for unknown difficulty`() {
        val p = CadenceTrainingProgress()
        val entry = p.getProgress(CadenceDifficulty.ADVANCED)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }
}
