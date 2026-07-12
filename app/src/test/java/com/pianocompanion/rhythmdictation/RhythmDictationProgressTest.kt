package com.pianocompanion.rhythmdictation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 节奏听写进度跟踪单元测试。
 */
class RhythmDictationProgressTest {

    private lateinit var progress: RhythmDictationProgress

    @Before
    fun setUp() {
        progress = RhythmDictationProgress()
    }

    // ── 初始状态 ────────────────────────────────────────

    @Test
    fun `初始状态全部为零`() {
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    // ── recordSession ───────────────────────────────────

    @Test
    fun `recordSession 后统计更新`() {
        progress.recordSession(
            RhythmDictationDifficulty.BEGINNER,
            RhythmDictationTempo.SLOW,
            correct = 8,
            total = 10,
            bestStreak = 5
        )
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
        assertEquals(5, progress.overallBestStreak)
    }

    @Test
    fun `多次 recordSession 累积`() {
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 3, 5, 2)
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 4, 5, 3)
        assertEquals(2, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(7, progress.totalCorrect)
        assertEquals(0.7, progress.overallAccuracy, 0.001)
        assertEquals(3, progress.overallBestStreak)
    }

    // ── 难度/速度隔离 ───────────────────────────────────

    @Test
    fun `不同难度速度独立统计`() {
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 5, 5, 5)
        progress.recordSession(RhythmDictationDifficulty.ADVANCED, RhythmDictationTempo.FAST, 1, 5, 1)
        val beginner = progress.getProgress(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW)
        val advanced = progress.getProgress(RhythmDictationDifficulty.ADVANCED, RhythmDictationTempo.FAST)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(1, advanced.totalCorrect)
    }

    @Test
    fun `getProgress 未记录的组合返回空 entry`() {
        val entry = progress.getProgress(RhythmDictationDifficulty.INTERMEDIATE, RhythmDictationTempo.MEDIUM)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
    }

    // ── bestStreak 不降级 ───────────────────────────────

    @Test
    fun `bestStreak 只增不减`() {
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 5, 5, 10)
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 1, 5, 2)
        val entry = progress.getProgress(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW)
        assertEquals(10, entry.bestStreak)
    }

    // ── bestAccuracy 不降级 ─────────────────────────────

    @Test
    fun `bestAccuracy 只增不减`() {
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 9, 10, 3)
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 1, 10, 1)
        val entry = progress.getProgress(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    // ── JSON 往返 ───────────────────────────────────────

    @Test
    fun `JSON 往返保持数据一致`() {
        progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 8, 10, 5)
        progress.recordSession(RhythmDictationDifficulty.ADVANCED, RhythmDictationTempo.FAST, 3, 7, 2)
        val json = progress.toJson()
        val restored = RhythmDictationProgress.fromJson(json)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `JSON 键格式正确`() {
        progress.recordSession(RhythmDictationDifficulty.INTERMEDIATE, RhythmDictationTempo.MEDIUM, 1, 1, 1)
        val json = progress.toJson()
        assertTrue(json.contains("INTERMEDIATE_MEDIUM"))
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `空 JSON 返回空进度`() {
        val restored = RhythmDictationProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = RhythmDictationProgress.fromJson("not json{{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少 stats 字段返回空进度`() {
        val restored = RhythmDictationProgress.fromJson("{\"other\":42}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分 entry 损坏不影响其他 entry`() {
        val json = "{\"stats\":{" +
            "\"BEGINNER_SLOW\":{\"totalAnswered\":10,\"totalCorrect\":8,\"sessionCount\":2,\"bestStreak\":5,\"bestAccuracy\":0.8000}," +
            "\"BROKEN_KEY\":not_valid" +
            "}}"
        val restored = RhythmDictationProgress.fromJson(json)
        assertEquals(10, restored.totalAnswered)
        assertEquals(8, restored.totalCorrect)
    }

    @Test
    fun `entry 缺少字段用默认值`() {
        val json = "{\"stats\":{\"BEGINNER_SLOW\":{\"totalAnswered\":5}}}"
        val restored = RhythmDictationProgress.fromJson(json)
        val entry = restored.getProgress(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW)
        assertEquals(5, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
    }

    // ── ProgressEntry ───────────────────────────────────

    @Test
    fun `ProgressEntry cumulativeAccuracy 正确`() {
        val entry = RhythmDictationProgressEntry(totalAnswered = 20, totalCorrect = 15)
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 零答题时 cumulativeAccuracy 为 0`() {
        val entry = RhythmDictationProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry JSON 往返`() {
        val entry = RhythmDictationProgressEntry(
            totalAnswered = 100,
            totalCorrect = 75,
            sessionCount = 10,
            bestStreak = 20,
            bestAccuracy = 0.95
        )
        val json = entry.toJson()
        val restored = RhythmDictationProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(100, restored!!.totalAnswered)
        assertEquals(75, restored.totalCorrect)
        assertEquals(10, restored.sessionCount)
        assertEquals(20, restored.bestStreak)
        assertEquals(0.95, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 损坏 JSON 返回 null`() {
        assertNull(RhythmDictationProgressEntry.fromJson("xxx"))
    }

    @Test
    fun `ProgressEntry 非 JSON 返回 null`() {
        assertNull(RhythmDictationProgressEntry.fromJson("[1,2,3]"))
    }

    // ── 多次累积 ────────────────────────────────────────

    @Test
    fun `多次累积后全局准确率正确`() {
        for (i in 1..5) {
            progress.recordSession(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.SLOW, 2, 4, 1)
        }
        assertEquals(20, progress.totalAnswered)
        assertEquals(10, progress.totalCorrect)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
        assertEquals(5, progress.totalSessions)
    }

    // ── 字段完整性 ──────────────────────────────────────

    @Test
    fun `recordSession 后 entry 字段完整`() {
        progress.recordSession(RhythmDictationDifficulty.ADVANCED, RhythmDictationTempo.FAST, 6, 8, 4)
        val entry = progress.getProgress(RhythmDictationDifficulty.ADVANCED, RhythmDictationTempo.FAST)
        assertEquals(8, entry.totalAnswered)
        assertEquals(6, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(4, entry.bestStreak)
        assertEquals(0.75, entry.bestAccuracy, 0.001)
    }
}
