package com.pianocompanion.nonchordtonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦外音辨识训练进度跟踪单元测试。
 *
 * 覆盖：累计统计、难度隔离、JSON 往返一致性、容错解析。
 */
class NonChordToneProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = NonChordToneProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `record session accumulates stats`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 3)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(NonChordToneDifficulty.BEGINNER, 5, 10, 7)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(13, p.totalCorrect)
        assertEquals(0.65, p.overallAccuracy, 0.0001)
        assertEquals(7, p.overallBestStreak)
    }

    @Test
    fun `difficulties are isolated`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(NonChordToneDifficulty.ADVANCED, 2, 10, 1)
        val beginner = p.getProgress(NonChordToneDifficulty.BEGINNER)
        val advanced = p.getProgress(NonChordToneDifficulty.ADVANCED)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(3, beginner.bestStreak)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
        assertEquals(1, advanced.bestStreak)
    }

    @Test
    fun `missing difficulty returns empty entry`() {
        val p = NonChordToneProgress()
        val entry = p.getProgress(NonChordToneDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `best streak is the max across sessions`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.ADVANCED, 5, 5, 4)
        p.recordSession(NonChordToneDifficulty.ADVANCED, 5, 5, 2)
        p.recordSession(NonChordToneDifficulty.ADVANCED, 5, 5, 9)
        p.recordSession(NonChordToneDifficulty.ADVANCED, 5, 5, 6)
        assertEquals(9, p.getProgress(NonChordToneDifficulty.ADVANCED).bestStreak)
        assertEquals(9, p.overallBestStreak)
    }

    @Test
    fun `best accuracy tracks the max session accuracy`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.BEGINNER, 7, 10, 1)  // 0.7
        p.recordSession(NonChordToneDifficulty.BEGINNER, 9, 10, 1)  // 0.9
        p.recordSession(NonChordToneDifficulty.BEGINNER, 6, 10, 1)  // 0.6
        assertEquals(0.9, p.getProgress(NonChordToneDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }

    @Test
    fun `json round trip preserves stats`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.BEGINNER, 8, 10, 3)
        p.recordSession(NonChordToneDifficulty.INTERMEDIATE, 5, 8, 4)
        p.recordSession(NonChordToneDifficulty.ADVANCED, 2, 10, 1)

        val json = p.toJson()
        val restored = NonChordToneProgress.fromJson(json)

        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)

        NonChordToneDifficulty.ALL.forEach { d ->
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
    fun `empty progress serializes and deserializes`() {
        val p = NonChordToneProgress()
        val json = p.toJson()
        val restored = NonChordToneProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0.0, restored.overallAccuracy, 0.0001)
    }

    @Test
    fun `corrupt json returns empty progress`() {
        val restored = NonChordToneProgress.fromJson("not a json {{{")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `partial json is tolerated`() {
        // 缺少部分字段不应崩溃
        val partial = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":7}}}"
        val restored = NonChordToneProgress.fromJson(partial)
        val entry = restored.getProgress(NonChordToneDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(7, entry.totalCorrect)
        // 缺失字段默认值
        assertEquals(0, entry.sessionCount)
        assertEquals(0, entry.bestStreak)
    }

    @Test
    fun `empty string returns empty progress`() {
        val restored = NonChordToneProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `json without stats key returns empty progress`() {
        val restored = NonChordToneProgress.fromJson("{\"other\":42}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `single session accuracy is recorded correctly`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.ADVANCED, 0, 5, 0)
        assertEquals(0.0, p.getProgress(NonChordToneDifficulty.ADVANCED).bestAccuracy, 0.0001)
    }

    @Test
    fun `zero total session does not update best accuracy`() {
        val p = NonChordToneProgress()
        // total=0 时不应更新 bestAccuracy（避免除零）
        p.recordSession(NonChordToneDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(NonChordToneDifficulty.BEGINNER).bestAccuracy, 0.0001)
        assertEquals(1, p.totalSessions)  // 会话数仍计数
    }

    @Test
    fun `cumulative accuracy per difficulty`() {
        val p = NonChordToneProgress()
        p.recordSession(NonChordToneDifficulty.INTERMEDIATE, 8, 10, 1)
        p.recordSession(NonChordToneDifficulty.INTERMEDIATE, 6, 10, 1)
        assertEquals(0.7, p.getProgress(NonChordToneDifficulty.INTERMEDIATE).cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `all difficulties tracked together`() {
        val p = NonChordToneProgress()
        NonChordToneDifficulty.ALL.forEach { d ->
            p.recordSession(d, 5, 10, 2)
        }
        assertEquals(3, p.totalSessions)
        assertEquals(30, p.totalAnswered)
        assertEquals(15, p.totalCorrect)
        assertEquals(2, p.overallBestStreak)
    }

    @Test
    fun `progress entry json round trip`() {
        val entry = NonChordToneProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 4,
            bestStreak = 11,
            bestAccuracy = 0.875
        )
        val json = entry.toJson()
        val restored = NonChordToneProgressEntry.fromJson(json)
        assertEquals(entry.totalAnswered, restored!!.totalAnswered)
        assertEquals(entry.totalCorrect, restored.totalCorrect)
        assertEquals(entry.sessionCount, restored.sessionCount)
        assertEquals(entry.bestStreak, restored.bestStreak)
        assertEquals(entry.bestAccuracy, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `progress entry fromJson returns null on malformed`() {
        assertEquals(null, NonChordToneProgressEntry.fromJson("not json"))
        assertEquals(null, NonChordToneProgressEntry.fromJson(""))
    }
}
