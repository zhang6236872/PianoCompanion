package com.pianocompanion.meterrecognition

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 拍号听辨训练进度跟踪单元测试。
 */
class MeterRecognitionProgressTest {

    private lateinit var progress: MeterRecognitionProgress

    @Before
    fun setup() {
        progress = MeterRecognitionProgress()
    }

    // ── 初始状态 ──────────────────────────────────────

    @Test
    fun `初始进度为空`() {
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `getProgress返回空Entry当不存在`() {
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.sessionCount)
    }

    // ── recordSession ───────────────────────────────────

    @Test
    fun `recordSession记录单次会话`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 8, 10, 5)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(10, entry.totalAnswered)
        assertEquals(8, entry.totalCorrect)
        assertEquals(1, entry.sessionCount)
        assertEquals(5, entry.bestStreak)
    }

    @Test
    fun `recordSession累计多次会话`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 7, 10, 6)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(20, entry.totalAnswered)
        assertEquals(12, entry.totalCorrect)
        assertEquals(2, entry.sessionCount)
        assertEquals(6, entry.bestStreak)
    }

    // ── bestStreak 不降级 ────────────────────────────────

    @Test
    fun `bestStreak不降级`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 8)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 3, 10, 2)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(8, entry.bestStreak)
    }

    @Test
    fun `bestStreak升级`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 7, 10, 9)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(9, entry.bestStreak)
    }

    // ── bestAccuracy 不降级 ─────────────────────────────

    @Test
    fun `bestAccuracy不降级`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 10, 10, 5)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 3, 10, 1)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(1.0, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `bestAccuracy取最大值`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 1)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 9, 10, 2)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 7, 10, 3)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    // ── 难度+速度隔离 ───────────────────────────────────

    @Test
    fun `不同难度速度组合隔离`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        progress.recordSession(MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST, 2, 10, 5)
        val beginner = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        val advanced = progress.getProgress(MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST)
        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(10, advanced.totalAnswered)
        assertEquals(2, advanced.totalCorrect)
    }

    @Test
    fun `相同难度不同速度隔离`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.FAST, 3, 5, 2)
        val slow = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        val fast = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.FAST)
        assertEquals(10, slow.totalAnswered)
        assertEquals(5, fast.totalAnswered)
    }

    // ── 全局汇总 ────────────────────────────────────────

    @Test
    fun `全局汇总正确`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        progress.recordSession(MeterRecognitionDifficulty.INTERMEDIATE, MeterRecognitionTempo.MEDIUM, 7, 15, 8)
        progress.recordSession(MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST, 3, 5, 4)
        assertEquals(3, progress.totalSessions)
        assertEquals(30, progress.totalAnswered)
        assertEquals(15, progress.totalCorrect)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
        assertEquals(8, progress.overallBestStreak)
    }

    @Test
    fun `全局bestStreak取最大`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        progress.recordSession(MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST, 5, 10, 12)
        progress.recordSession(MeterRecognitionDifficulty.INTERMEDIATE, MeterRecognitionTempo.MEDIUM, 5, 10, 7)
        assertEquals(12, progress.overallBestStreak)
    }

    // ── JSON 往返 ───────────────────────────────────────

    @Test
    fun `JSON往返保持数据`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 8, 10, 5)
        progress.recordSession(MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST, 3, 5, 2)
        val json = progress.toJson()
        val restored = MeterRecognitionProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)

        val origEntry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        val restoredEntry = restored.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(origEntry.totalAnswered, restoredEntry.totalAnswered)
        assertEquals(origEntry.totalCorrect, restoredEntry.totalCorrect)
        assertEquals(origEntry.sessionCount, restoredEntry.sessionCount)
        assertEquals(origEntry.bestStreak, restoredEntry.bestStreak)
        assertEquals(origEntry.bestAccuracy, restoredEntry.bestAccuracy, 0.001)
    }

    @Test
    fun `空进度JSON往返`() {
        val json = progress.toJson()
        val restored = MeterRecognitionProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    // ── 容错解析 ────────────────────────────────────────

    @Test
    fun `损坏JSON返回空进度`() {
        val restored = MeterRecognitionProgress.fromJson("not valid json {{{")
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = MeterRecognitionProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏JSON忽略错误项`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 3)
        val json = progress.toJson()
        // 追加一些垃圾字符
        val corrupted = json + "garbage"
        val restored = MeterRecognitionProgress.fromJson(corrupted)
        // 至少不崩溃（可能解析到或不解析到，取决于实现）
        assertNotNull(restored)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val json = "{\"otherField\":123}"
        val restored = MeterRecognitionProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── cumulativeAccuracy ──────────────────────────────

    @Test
    fun `cumulativeAccuracy正确计算`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 5, 10, 1)
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 3, 10, 1)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(0.4, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `未答题时cumulativeAccuracy为0`() {
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    // ── key 格式 ────────────────────────────────────────

    @Test
    fun `key格式正确`() {
        val key = MeterRecognitionProgress.key(MeterRecognitionDifficulty.INTERMEDIATE, MeterRecognitionTempo.FAST)
        assertEquals("INTERMEDIATE_FAST", key)
    }

    // ── recordSession零总数边界 ─────────────────────────

    @Test
    fun `total为0时bestAccuracy不变`() {
        progress.recordSession(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW, 0, 0, 0)
        val entry = progress.getProgress(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.bestAccuracy, 0.001)
        assertEquals(1, entry.sessionCount)
    }
}
