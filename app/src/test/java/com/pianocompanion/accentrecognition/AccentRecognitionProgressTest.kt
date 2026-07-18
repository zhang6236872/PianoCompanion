package com.pianocompanion.accentrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AccentProgress] 单元测试。
 *
 * 验证：累计统计、按难度隔离、全局聚合、JSON 往返一致性、容错解析、边界情况。
 */
class AccentRecognitionProgressTest {

    @Test
    fun `new progress is empty`() {
        val p = AccentProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession accumulates stats per difficulty`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 5)
        p.recordSession(AccentDifficulty.BEGINNER, correct = 6, total = 10, bestStreak = 7)

        val entry = p.getProgress(AccentDifficulty.BEGINNER)
        assertEquals(2, entry.sessionCount)
        assertEquals(20, entry.totalAnswered)
        assertEquals(14, entry.totalCorrect)
        assertEquals(7, entry.bestStreak)
    }

    @Test
    fun `stats are isolated per difficulty`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 5, total = 5, bestStreak = 5)
        p.recordSession(AccentDifficulty.ADVANCED, correct = 1, total = 5, bestStreak = 2)

        val beginner = p.getProgress(AccentDifficulty.BEGINNER)
        val advanced = p.getProgress(AccentDifficulty.ADVANCED)

        assertEquals(5, beginner.totalAnswered)
        assertEquals(5, advanced.totalAnswered)
        assertEquals(5, beginner.bestStreak)
        assertEquals(2, advanced.bestStreak)

        // 中级无记录
        val intermediate = p.getProgress(AccentDifficulty.INTERMEDIATE)
        assertEquals(0, intermediate.totalAnswered)
    }

    @Test
    fun `overall aggregates across difficulties`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3)
        p.recordSession(AccentDifficulty.INTERMEDIATE, correct = 7, total = 10, bestStreak = 8)

        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.0001)
        assertEquals(8, p.overallBestStreak)
    }

    @Test
    fun `bestAccuracy tracks per-session accuracy`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 5, total = 10, bestStreak = 3) // 50%
        assertEquals(0.5, p.getProgress(AccentDifficulty.BEGINNER).bestAccuracy, 0.0001)
        p.recordSession(AccentDifficulty.BEGINNER, correct = 9, total = 10, bestStreak = 3) // 90%
        assertEquals(0.9, p.getProgress(AccentDifficulty.BEGINNER).bestAccuracy, 0.0001)
        p.recordSession(AccentDifficulty.BEGINNER, correct = 1, total = 10, bestStreak = 3) // 10%
        assertEquals(0.9, p.getProgress(AccentDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }

    @Test
    fun `cumulativeAccuracy differs from bestAccuracy`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 9, total = 10, bestStreak = 3)
        p.recordSession(AccentDifficulty.BEGINNER, correct = 1, total = 10, bestStreak = 3)
        val entry = p.getProgress(AccentDifficulty.BEGINNER)
        assertEquals(0.5, entry.cumulativeAccuracy, 0.0001) // 10/20
        assertEquals(0.9, entry.bestAccuracy, 0.0001)       // 单次最高
    }

    // ── JSON 往返 ───────────────────────────────────────

    @Test
    fun `json round trip preserves data`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 5)
        p.recordSession(AccentDifficulty.ADVANCED, correct = 3, total = 7, bestStreak = 2)

        val json = p.toJson()
        val restored = AccentProgress.fromJson(json)

        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        val origBeginner = p.getProgress(AccentDifficulty.BEGINNER)
        val restBeginner = restored.getProgress(AccentDifficulty.BEGINNER)
        assertEquals(origBeginner.totalAnswered, restBeginner.totalAnswered)
        assertEquals(origBeginner.totalCorrect, restBeginner.totalCorrect)
        assertEquals(origBeginner.bestStreak, restBeginner.bestStreak)
        assertEquals(origBeginner.bestAccuracy, restBeginner.bestAccuracy, 0.0001)
    }

    @Test
    fun `json round trip all difficulties`() {
        val p = AccentProgress()
        for (d in AccentDifficulty.ALL) {
            p.recordSession(d, correct = 4, total = 6, bestStreak = 3)
        }
        val restored = AccentProgress.fromJson(p.toJson())
        for (d in AccentDifficulty.ALL) {
            val entry = restored.getProgress(d)
            assertEquals(6, entry.totalAnswered)
            assertEquals(4, entry.totalCorrect)
        }
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `fromJson handles corrupted json gracefully`() {
        val p = AccentProgress.fromJson("not valid json {{{")
        assertEquals(0, p.totalAnswered)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
    }

    @Test
    fun `fromJson handles empty string`() {
        val p = AccentProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats field`() {
        val p = AccentProgress.fromJson("{\"otherField\":42}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles partial entry`() {
        // 某个 entry 缺字段，应回退默认值不崩溃
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":5}}}"
        val p = AccentProgress.fromJson(json)
        // totalAnswered 字段存在但其他缺失 → 该 entry 解析失败返回 null → 不计入
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson ignores unknown difficulty keys gracefully`() {
        val json = "{\"stats\":{\"UNKNOWN_DIFF\":{\"totalAnswered\":10,\"totalCorrect\":5,\"sessionCount\":2,\"bestStreak\":1,\"bestAccuracy\":0.5}}}"
        val p = AccentProgress.fromJson(json)
        // 未知键被解析为字符串键存入 map，不影响已知难度
        assertEquals(10, p.totalAnswered)
    }

    @Test
    fun `empty progress serializes to valid json`() {
        val p = AccentProgress()
        val json = p.toJson()
        val restored = AccentProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `progress entry toJson includes all fields`() {
        val entry = AccentProgressEntry(
            totalAnswered = 10, totalCorrect = 7,
            sessionCount = 2, bestStreak = 5, bestAccuracy = 0.85
        )
        val json = entry.toJson()
        assertTrue("\"totalAnswered\":10" in json)
        assertTrue("\"totalCorrect\":7" in json)
        assertTrue("\"sessionCount\":2" in json)
        assertTrue("\"bestStreak\":5" in json)
        assertTrue("bestAccuracy" in json)
    }

    @Test
    fun `progress entry fromJson invalid returns null`() {
        assertEquals(null, AccentProgressEntry.fromJson("not json"))
        assertEquals(null, AccentProgressEntry.fromJson(""))
        assertEquals(null, AccentProgressEntry.fromJson("[1,2,3]"))
    }

    @Test
    fun `recordSession with zero total does not update bestAccuracy`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 0, total = 0, bestStreak = 0)
        val entry = p.getProgress(AccentDifficulty.BEGINNER)
        assertEquals(0.0, entry.bestAccuracy, 0.0001)
        assertEquals(1, entry.sessionCount)
    }

    @Test
    fun `bestStreak only increases never decreases`() {
        val p = AccentProgress()
        p.recordSession(AccentDifficulty.BEGINNER, correct = 5, total = 5, bestStreak = 10)
        p.recordSession(AccentDifficulty.BEGINNER, correct = 5, total = 5, bestStreak = 3)
        assertEquals(10, p.getProgress(AccentDifficulty.BEGINNER).bestStreak)
    }
}
