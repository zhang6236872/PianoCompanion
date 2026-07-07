package com.pianocompanion.chordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordTrainingProgress] 单元测试。
 *
 * 验证进度跟踪：
 * - 空进度
 * - 单次/多次会话累计
 * - bestStreak / bestAccuracy 追踪
 * - 不同难度/播放方式分开统计
 * - 全局汇总统计
 * - JSON 往返一致性
 * - 容错解析（空/损坏/无 stats/空 stats）
 */
class ChordTrainingProgressTest {

    @Test
    fun `empty progress`() {
        val p = ChordTrainingProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `single session`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 8, 10, 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `multiple sessions accumulate`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 8, 10, 5)
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 7, 10, 6)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(15, p.totalCorrect)
        assertEquals(0.75, p.overallAccuracy, 0.001)
        assertEquals(6, p.overallBestStreak)
    }

    @Test
    fun `different difficulties tracked separately`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 10, 10, 5)
        p.recordSession(ChordEarDifficulty.ADVANCED, ChordPlayStyle.BLOCK, 5, 10, 3)
        val beginner = p.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        val advanced = p.getProgress(ChordEarDifficulty.ADVANCED, ChordPlayStyle.BLOCK)
        assertEquals(10, beginner.totalCorrect)
        assertEquals(5, advanced.totalCorrect)
        assertEquals(20, p.totalAnswered)
    }

    @Test
    fun `different play styles tracked separately`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.INTERMEDIATE, ChordPlayStyle.BLOCK, 6, 10, 4)
        p.recordSession(ChordEarDifficulty.INTERMEDIATE, ChordPlayStyle.ARPEGGIO, 9, 10, 7)
        val block = p.getProgress(ChordEarDifficulty.INTERMEDIATE, ChordPlayStyle.BLOCK)
        val arp = p.getProgress(ChordEarDifficulty.INTERMEDIATE, ChordPlayStyle.ARPEGGIO)
        assertEquals(6, block.totalCorrect)
        assertEquals(9, arp.totalCorrect)
        assertEquals(4, block.bestStreak)
        assertEquals(7, arp.bestStreak)
    }

    @Test
    fun `bestStreak only increases`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 5, 10, 8)
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 5, 10, 3)
        val entry = p.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        assertEquals(8, entry.bestStreak) // 不应降到 3
    }

    @Test
    fun `bestAccuracy tracks best session`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 5, 10, 3) // 50%
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 9, 10, 5) // 90%
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 7, 10, 4) // 70%
        val entry = p.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy differs from bestAccuracy`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 9, 10, 5) // 90% best
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 5, 10, 3) // 50%
        val entry = p.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        assertEquals(0.7, entry.cumulativeAccuracy, 0.001) // (9+5)/20
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `json roundtrip empty`() {
        val p = ChordTrainingProgress()
        val json = p.toJson()
        val restored = ChordTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalSessions)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `json roundtrip with data`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 8, 10, 5)
        p.recordSession(ChordEarDifficulty.ADVANCED, ChordPlayStyle.ARPEGGIO, 6, 10, 4)
        val json = p.toJson()
        val restored = ChordTrainingProgress.fromJson(json)
        assertEquals(2, restored.totalSessions)
        assertEquals(20, restored.totalAnswered)
        assertEquals(14, restored.totalCorrect)
        assertEquals(5, restored.overallBestStreak)
        val beginner = restored.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        assertEquals(8, beginner.totalCorrect)
        assertEquals(5, beginner.bestStreak)
        val advanced = restored.getProgress(ChordEarDifficulty.ADVANCED, ChordPlayStyle.ARPEGGIO)
        assertEquals(6, advanced.totalCorrect)
    }

    @Test
    fun `fromJson handles empty string`() {
        val p = ChordTrainingProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles corrupted json`() {
        val p = ChordTrainingProgress.fromJson("{corrupted: not valid}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles missing stats`() {
        val p = ChordTrainingProgress.fromJson("{\"version\":1}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson handles empty stats object`() {
        val p = ChordTrainingProgress.fromJson("{\"stats\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `key format is DIFFICULTY_PLAYSTYLE`() {
        val key = ChordTrainingProgress.key(ChordEarDifficulty.ADVANCED, ChordPlayStyle.ARPEGGIO)
        assertEquals("ADVANCED_ARPEGGIO", key)
    }

    @Test
    fun `getProgress for unknown combo returns empty entry`() {
        val p = ChordTrainingProgress()
        val entry = p.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `complex multi-key roundtrip`() {
        val p = ChordTrainingProgress()
        ChordEarDifficulty.ALL.forEach { d ->
            ChordPlayStyle.ALL.forEach { ps ->
                p.recordSession(d, ps, 5, 10, 3)
            }
        }
        val json = p.toJson()
        val restored = ChordTrainingProgress.fromJson(json)
        assertEquals(6, restored.totalSessions) // 3 difficulties × 2 styles
        assertEquals(60, restored.totalAnswered)
        ChordEarDifficulty.ALL.forEach { d ->
            ChordPlayStyle.ALL.forEach { ps ->
                val entry = restored.getProgress(d, ps)
                assertEquals(10, entry.totalAnswered)
                assertEquals(5, entry.totalCorrect)
            }
        }
    }

    @Test
    fun `recordSession with zero total does not update bestAccuracy`() {
        val p = ChordTrainingProgress()
        p.recordSession(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK, 0, 0, 0)
        val entry = p.getProgress(ChordEarDifficulty.BEGINNER, ChordPlayStyle.BLOCK)
        assertEquals(1, entry.sessionCount)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
        assertEquals(0, entry.totalAnswered)
    }

    @Test
    fun `entry toJson fromJson roundtrip`() {
        val entry = ChordTrainingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 30,
            sessionCount = 5,
            bestStreak = 8,
            bestAccuracy = 0.85
        )
        val json = entry.toJson()
        val restored = ChordTrainingProgressEntry.fromJson(json)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(30, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(8, restored.bestStreak)
        assertEquals(0.85, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `entry fromJson handles non-object`() {
        val restored = ChordTrainingProgressEntry.fromJson("not an object")
        assertEquals(null, restored)
    }

    @Test
    fun `entry cumulativeAccuracy zero when empty`() {
        val entry = ChordTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }
}
