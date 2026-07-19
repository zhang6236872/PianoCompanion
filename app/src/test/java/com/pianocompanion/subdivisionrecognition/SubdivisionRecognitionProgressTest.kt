package com.pianocompanion.subdivisionrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SubdivisionProgress] / [SubdivisionProgressEntry] 单元测试。
 *
 * 验证累计统计、难度隔离、JSON 往返序列化、容错解析、严格字段校验。
 */
class SubdivisionRecognitionProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = SubdivisionProgress()
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0, p.totalSessions)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates stats`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(1, p.totalSessions)
        assertEquals(5, p.overallBestStreak)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(SubdivisionDifficulty.BEGINNER, 7, 10, 4)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(2, p.totalSessions)
        assertEquals(4, p.overallBestStreak)
    }

    @Test
    fun `difficulties are isolated`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(SubdivisionDifficulty.ADVANCED, 2, 10, 1)
        val beginner = p.getProgress(SubdivisionDifficulty.BEGINNER)
        val advanced = p.getProgress(SubdivisionDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
        assertEquals(3, beginner.bestStreak)
        assertEquals(1, advanced.bestStreak)
    }

    @Test
    fun `getProgress returns empty for unknown difficulty`() {
        val p = SubdivisionProgress()
        val entry = p.getProgress(SubdivisionDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `bestStreak tracks maximum across sessions`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 5, 5, 3)
        p.recordSession(SubdivisionDifficulty.BEGINNER, 5, 5, 7)
        p.recordSession(SubdivisionDifficulty.BEGINNER, 5, 5, 2)
        assertEquals(7, p.getProgress(SubdivisionDifficulty.BEGINNER).bestStreak)
    }

    @Test
    fun `bestAccuracy tracks maximum session accuracy`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 5, 10, 1) // 0.5
        p.recordSession(SubdivisionDifficulty.BEGINNER, 9, 10, 1) // 0.9
        p.recordSession(SubdivisionDifficulty.BEGINNER, 7, 10, 1) // 0.7
        assertEquals(0.9, p.getProgress(SubdivisionDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }

    @Test
    fun `cumulative accuracy computes correctly`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.INTERMEDIATE, 3, 10, 1)
        p.recordSession(SubdivisionDifficulty.INTERMEDIATE, 7, 10, 1)
        assertEquals(0.5, p.getProgress(SubdivisionDifficulty.INTERMEDIATE).cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `overallBestStreak across difficulties`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 5, 5, 4)
        p.recordSession(SubdivisionDifficulty.ADVANCED, 5, 5, 9)
        p.recordSession(SubdivisionDifficulty.INTERMEDIATE, 5, 5, 6)
        assertEquals(9, p.overallBestStreak)
    }

    // ── JSON 序列化往返 ────────────────────────────────────

    @Test
    fun `json round trip preserves stats`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(SubdivisionDifficulty.ADVANCED, 3, 7, 2)

        val json = p.toJson()
        val restored = SubdivisionProgress.fromJson(json)

        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.0001)
    }

    @Test
    fun `json round trip preserves per-difficulty entries`() {
        val p = SubdivisionProgress()
        p.recordSession(SubdivisionDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(SubdivisionDifficulty.INTERMEDIATE, 6, 10, 3)

        val restored = SubdivisionProgress.fromJson(p.toJson())
        val beginner = restored.getProgress(SubdivisionDifficulty.BEGINNER)
        val intermediate = restored.getProgress(SubdivisionDifficulty.INTERMEDIATE)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(5, beginner.bestStreak)
        assertEquals(10, intermediate.totalAnswered)
        assertEquals(6, intermediate.totalCorrect)
    }

    @Test
    fun `toJson of empty progress is valid`() {
        val p = SubdivisionProgress()
        val json = p.toJson()
        val restored = SubdivisionProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ───────────────────────────────────────────

    @Test
    fun `fromJson handles corrupt json gracefully`() {
        val restored = SubdivisionProgress.fromJson("not json { broken")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles empty string`() {
        val restored = SubdivisionProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats key`() {
        val restored = SubdivisionProgress.fromJson("{\"other\":123}")
        assertEquals(0, restored.totalAnswered)
    }

    // ── 严格字段校验 ───────────────────────────────────────

    @Test
    fun `entry with missing field is rejected`() {
        // 缺少 bestAccuracy 字段 → 应被拒绝（返回 null），不计入
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":5," +
            "\"sessionCount\":1,\"bestStreak\":3}}}"
        val restored = SubdivisionProgress.fromJson(json)
        assertEquals("缺字段的 entry 不应计入", 0, restored.totalAnswered)
    }

    @Test
    fun `entry with all fields is accepted`() {
        val entry = SubdivisionProgressEntry(
            totalAnswered = 10, totalCorrect = 7,
            sessionCount = 2, bestStreak = 4, bestAccuracy = 0.7
        )
        val json = entry.toJson()
        val restored = SubdivisionProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(10, restored!!.totalAnswered)
        assertEquals(7, restored.totalCorrect)
        assertEquals(2, restored.sessionCount)
        assertEquals(4, restored.bestStreak)
        assertEquals(0.7, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `entry from non-object json returns null`() {
        assertNull(SubdivisionProgressEntry.fromJson("123"))
        assertNull(SubdivisionProgressEntry.fromJson("\"string\""))
    }

    @Test
    fun `partial entry mixed with valid entry`() {
        // 一个完整 entry + 一个缺字段 entry
        val json = "{\"stats\":{" +
            "\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":5,\"sessionCount\":1,\"bestStreak\":3,\"bestAccuracy\":0.5}," +
            "\"ADVANCED\":{\"totalAnswered\":10,\"totalCorrect\":5,\"sessionCount\":1}" +
            "}}"
        val restored = SubdivisionProgress.fromJson(json)
        // 只有 BEGINNER 被接受
        assertEquals(10, restored.totalAnswered)
        assertEquals(1, restored.stats.size)
        assertNotNull(restored.stats["BEGINNER"])
        // ADVANCED 因缺字段被拒
        assertNull(restored.stats["ADVANCED"])
    }

    @Test
    fun `entry with negative values parses to zero`() {
        // 值非法时回退 0（键存在）
        val json = "{\"totalAnswered\":-5,\"totalCorrect\":-2,\"sessionCount\":-1,\"bestStreak\":-1,\"bestAccuracy\":-0.5}"
        val restored = SubdivisionProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(0L, restored!!.totalAnswered.toLong())
    }
}
