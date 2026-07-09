package com.pianocompanion.inversiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练进度跟踪单元测试。
 *
 * 验证分难度累计、全局汇总、bestAccuracy/bestStreak 不降级、JSON 往返、容错解析等。
 */
class InversionTrainingProgressTest {

    // ── 基础记录 ────────────────────────────────────────────

    @Test
    fun `empty progress has zero stats`() {
        val p = InversionTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates per difficulty`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 8, 10, 5)
        val entry = p.getProgress(InversionDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.INTERMEDIATE, 5, 10, 3)
        p.recordSession(InversionDifficulty.INTERMEDIATE, 7, 10, 6)
        val entry = p.getProgress(InversionDifficulty.INTERMEDIATE)
        assertEquals(20, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(6, entry.bestStreak) // 取最大值
    }

    // ── 全局汇总 ───────────────────────────────────────────

    @Test
    fun `global stats aggregate across difficulties`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(InversionDifficulty.ADVANCED, 6, 10, 7)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(14.0 / 20.0, p.overallAccuracy, 0.001)
        assertEquals(7, p.overallBestStreak)
    }

    // ── bestStreak/bestAccuracy 不降级 ───────────────────

    @Test
    fun `bestStreak does not downgrade`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 5, 5, 10)
        p.recordSession(InversionDifficulty.BEGINNER, 3, 5, 2)
        assertEquals(10, p.getProgress(InversionDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `bestAccuracy does not downgrade`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 10, 10, 5) // 100%
        p.recordSession(InversionDifficulty.BEGINNER, 3, 10, 2)  // 30%
        assertEquals(1.0, p.getProgress(InversionDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy tracks session accuracy`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.INTERMEDIATE, 7, 10, 3)
        assertEquals(0.7, p.getProgress(InversionDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
        p.recordSession(InversionDifficulty.INTERMEDIATE, 9, 10, 4)
        assertEquals(0.9, p.getProgress(InversionDifficulty.INTERMEDIATE).bestAccuracy, 0.001)
    }

    // ── total=0 不更新 bestAccuracy ────────────────────────

    @Test
    fun `zero total session does not corrupt bestAccuracy`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(InversionDifficulty.BEGINNER).bestAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `json roundtrip preserves data`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(InversionDifficulty.INTERMEDIATE, 6, 10, 5)
        p.recordSession(InversionDifficulty.ADVANCED, 4, 10, 2)

        val json = p.toJson()
        val restored = InversionTrainingProgress.fromJson(json)

        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        InversionDifficulty.ALL.forEach { d ->
            val orig = p.getProgress(d)
            val rstr = restored.getProgress(d)
            assertEquals(orig.totalAnswered, rstr.totalAnswered)
            assertEquals(orig.totalCorrect, rstr.totalCorrect)
            assertEquals(orig.sessionCount, rstr.sessionCount)
            assertEquals(orig.bestStreak, rstr.bestStreak)
            assertEquals(orig.bestAccuracy, rstr.bestAccuracy, 0.001)
        }
    }

    @Test
    fun `empty progress json roundtrip`() {
        val p = InversionTrainingProgress()
        val json = p.toJson()
        val restored = InversionTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `multiple roundtrips are stable`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.ADVANCED, 7, 10, 4)
        var current = p
        repeat(5) {
            val json = current.toJson()
            current = InversionTrainingProgress.fromJson(json)
        }
        assertEquals(p.totalAnswered, current.totalAnswered)
        assertEquals(p.overallBestStreak, current.overallBestStreak)
    }

    // ── 容错解析 ───────────────────────────────────────────

    @Test
    fun `corrupted json returns empty progress`() {
        val restored = InversionTrainingProgress.fromJson("garbage{not json")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `empty string returns empty progress`() {
        val restored = InversionTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `missing stats key returns empty progress`() {
        val restored = InversionTrainingProgress.fromJson("{\"other\":{}}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `partial entry survives`() {
        // 一个 entry 完整，一个损坏
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 5, 10, 3)
        val json = p.toJson()
        // 注入一个损坏的 entry
        val corrupted = json.replace("}}", ",\"ADVANCED\":{\"totalAnswered\":notanumber}}")
        val restored = InversionTrainingProgress.fromJson(corrupted)
        // 至少 BEGINNER 应该存活
        assertEquals(10, restored.getProgress(InversionDifficulty.BEGINNER).totalAnswered)
    }

    @Test
    fun `missing fields default to zero`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10}}}"
        val restored = InversionTrainingProgress.fromJson(json)
        val entry = restored.getProgress(InversionDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
    }

    // ── Entry 独立序列化 ───────────────────────────────────

    @Test
    fun `entry json roundtrip`() {
        val entry = InversionTrainingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = InversionTrainingProgressEntry.fromJson(json)
        assertTrue(restored != null)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(30, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.85, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `entry cumulativeAccuracy`() {
        val entry = InversionTrainingProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `entry zero answered cumulativeAccuracy is zero`() {
        val entry = InversionTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `entry fromJson null for invalid input`() {
        assertEquals(null, InversionTrainingProgressEntry.fromJson("not json"))
        assertEquals(null, InversionTrainingProgressEntry.fromJson(""))
    }

    // ── key 一致性 ─────────────────────────────────────────

    @Test
    fun `key matches difficulty name`() {
        InversionDifficulty.ALL.forEach { d ->
            assertEquals(d.name, InversionTrainingProgress.key(d))
        }
    }

    @Test
    fun `getProgress returns empty entry for unrecorded difficulty`() {
        val p = InversionTrainingProgress()
        p.recordSession(InversionDifficulty.BEGINNER, 5, 10, 2)
        val adv = p.getProgress(InversionDifficulty.ADVANCED)
        assertEquals(0, adv.totalAnswered)
    }
}
