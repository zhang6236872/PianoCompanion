package com.pianocompanion.moderecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ModeRecognitionProgress] 单元测试。
 *
 * 验证：
 * - 会话结果记录
 * - 跨会话累计统计
 * - bestStreak / bestAccuracy 追踪
 * - JSON 序列化/反序列化往返
 * - 容错解析（损坏 JSON 返回空进度）
 * - 键格式
 */
class ModeRecognitionProgressTest {

    @Test
    fun `empty progress has zero stats`() {
        val p = ModeRecognitionProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    @Test
    fun `getProgress returns empty entry when no data`() {
        val p = ModeRecognitionProgress()
        val entry = p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    @Test
    fun `recordSession updates stats`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, correct = 8, total = 10, bestStreak = 4)
        val entry = p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(4, entry.bestStreak)
        assertEquals(0.8, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.INTERMEDIATE, PlayMode.ASCENDING, 7, 10, 3)
        p.recordSession(ModeDifficulty.INTERMEDIATE, PlayMode.ASCENDING, 9, 10, 5)
        val entry = p.getProgress(ModeDifficulty.INTERMEDIATE, PlayMode.ASCENDING)
        assertEquals(20, entry.totalAnswered)
        assertEquals(16, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `best streak tracks max across sessions`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.ADVANCED, PlayMode.ASCENDING, 5, 10, 3)
        p.recordSession(ModeDifficulty.ADVANCED, PlayMode.ASCENDING, 8, 10, 7)
        p.recordSession(ModeDifficulty.ADVANCED, PlayMode.ASCENDING, 6, 10, 2)
        val entry = p.getProgress(ModeDifficulty.ADVANCED, PlayMode.ASCENDING)
        assertEquals(7, entry.bestStreak)
    }

    @Test
    fun `best accuracy tracks max session accuracy`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 5, 10, 2) // 0.5
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 9, 10, 3) // 0.9
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 7, 10, 4) // 0.7
        val entry = p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `different difficulties are tracked separately`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 10, 10, 5)
        p.recordSession(ModeDifficulty.ADVANCED, PlayMode.ASCENDING, 3, 10, 1)
        assertEquals(10, p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING).totalCorrect)
        assertEquals(3, p.getProgress(ModeDifficulty.ADVANCED, PlayMode.ASCENDING).totalCorrect)
    }

    @Test
    fun `different play modes are tracked separately`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 8, 10, 4)
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING_DESCENDING, 6, 10, 2)
        assertEquals(8, p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING).totalCorrect)
        assertEquals(6, p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING_DESCENDING).totalCorrect)
    }

    @Test
    fun `total stats aggregate across all keys`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 5, 10, 3)
        p.recordSession(ModeDifficulty.INTERMEDIATE, PlayMode.ASCENDING_DESCENDING, 7, 10, 4)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(0.6, p.overallAccuracy, 0.001)
    }

    @Test
    fun `recordSession with zero total does not update bestAccuracy`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 0, 0, 0)
        val entry = p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
        assertEquals(1, entry.sessionCount)
    }

    // ── JSON 序列化往返 ─────────────────────────────────

    @Test
    fun `json round trip preserves data`() {
        val p = ModeRecognitionProgress()
        p.recordSession(ModeDifficulty.BEGINNER, PlayMode.ASCENDING, 8, 10, 4)
        p.recordSession(ModeDifficulty.INTERMEDIATE, PlayMode.ASCENDING_DESCENDING, 7, 10, 3)

        val json = p.toJson()
        val restored = ModeRecognitionProgress.fromJson(json)

        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)

        val origEntry = p.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        val restEntry = restored.getProgress(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        assertEquals(origEntry.totalAnswered, restEntry.totalAnswered)
        assertEquals(origEntry.totalCorrect, restEntry.totalCorrect)
        assertEquals(origEntry.sessionCount, restEntry.sessionCount)
        assertEquals(origEntry.bestStreak, restEntry.bestStreak)
        assertEquals(origEntry.bestAccuracy, restEntry.bestAccuracy, 0.001)
    }

    @Test
    fun `fromJson handles empty json`() {
        val p = ModeRecognitionProgress.fromJson("")
        assertNotNull(p)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson handles malformed json`() {
        val p = ModeRecognitionProgress.fromJson("{broken json!!!")
        assertNotNull(p)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson handles json without stats key`() {
        val p = ModeRecognitionProgress.fromJson("{\"other\":\"data\"}")
        assertNotNull(p)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `fromJson handles json with empty stats`() {
        val p = ModeRecognitionProgress.fromJson("{\"stats\":{}}")
        assertNotNull(p)
        assertEquals(0, p.totalSessions)
    }

    @Test
    fun `cumulative accuracy on entry`() {
        val entry = ModeProgressEntry(
            totalAnswered = 20,
            totalCorrect = 15
        )
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `entry cumulative accuracy is zero when no answers`() {
        val entry = ModeProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `key format is difficulty_playmode`() {
        assertEquals(
            "BEGINNER_ASCENDING",
            ModeRecognitionProgress.key(ModeDifficulty.BEGINNER, PlayMode.ASCENDING)
        )
        assertEquals(
            "ADVANCED_ASCENDING_DESCENDING",
            ModeRecognitionProgress.key(ModeDifficulty.ADVANCED, PlayMode.ASCENDING_DESCENDING)
        )
    }
}
