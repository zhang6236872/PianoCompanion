package com.pianocompanion.training

import org.junit.Assert.*
import org.junit.Test

/**
 * [EarTrainingProgress] 进度跟踪模型单元测试。
 *
 * 覆盖：会话记录、累计统计、序列化/反序列化往返、边界情况。
 */
class EarTrainingProgressTest {

    // ── 基础记录 ──────────────────────────────────────────

    @Test
    fun `新进度为空`() {
        val progress = EarTrainingProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `记录会话后统计数据正确`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 8, 10, 5)
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(8, progress.totalCorrect)
        assertEquals(0.8, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `多次记录累加`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 7, 10, 4)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
        assertEquals(0.6, progress.overallAccuracy, 0.001)
    }

    @Test
    fun `不同类型独立统计`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ExerciseType.CHORD, Difficulty.ADVANCED, 8, 10, 6)

        val intervalProgress = progress.getProgress(ExerciseType.INTERVAL, Difficulty.BEGINNER)
        val chordProgress = progress.getProgress(ExerciseType.CHORD, Difficulty.ADVANCED)

        assertEquals(10, intervalProgress.totalAnswered)
        assertEquals(5, intervalProgress.totalCorrect)
        assertEquals(10, chordProgress.totalAnswered)
        assertEquals(8, chordProgress.totalCorrect)
    }

    // ── bestStreak ────────────────────────────────────────

    @Test
    fun `bestStreak 记录最大值`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 5, 10, 7)
        val p = progress.getProgress(ExerciseType.INTERVAL, Difficulty.BEGINNER)
        assertEquals(7, p.bestStreak)
    }

    @Test
    fun `bestStreak 不降低`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 5, 10, 10)
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 5, 10, 3)
        val p = progress.getProgress(ExerciseType.INTERVAL, Difficulty.BEGINNER)
        assertEquals(10, p.bestStreak)
    }

    // ── bestAccuracy ──────────────────────────────────────

    @Test
    fun `bestAccuracy 记录最高会话准确率`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.CHORD, Difficulty.INTERMEDIATE, 7, 10, 3)
        progress.recordSession(ExerciseType.CHORD, Difficulty.INTERMEDIATE, 9, 10, 5)
        val p = progress.getProgress(ExerciseType.CHORD, Difficulty.INTERMEDIATE)
        assertEquals(0.9, p.bestAccuracy, 0.001)
    }

    @Test
    fun `total 为 0 时不更新 bestAccuracy`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.SCALE, Difficulty.ADVANCED, 0, 0, 0)
        val p = progress.getProgress(ExerciseType.SCALE, Difficulty.ADVANCED)
        assertEquals(0.0, p.bestAccuracy, 0.001)
    }

    // ── cumulativeAccuracy ────────────────────────────────

    @Test
    fun `cumulativeAccuracy 计算正确`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 3, 10, 2)
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 7, 10, 5)
        val p = progress.getProgress(ExerciseType.INTERVAL, Difficulty.BEGINNER)
        assertEquals(0.5, p.cumulativeAccuracy, 0.001)
    }

    // ── 未记录组合 ────────────────────────────────────────

    @Test
    fun `未记录组合返回空 ProgressEntry`() {
        val progress = EarTrainingProgress()
        val p = progress.getProgress(ExerciseType.SCALE, Difficulty.ADVANCED)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.sessionCount)
        assertEquals(0, p.bestStreak)
    }

    // ── 序列化往返 ────────────────────────────────────────

    @Test
    fun `JSON 序列化反序列化往返`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.INTERVAL, Difficulty.BEGINNER, 8, 10, 5)
        progress.recordSession(ExerciseType.CHORD, Difficulty.ADVANCED, 3, 5, 2)

        val json = progress.toJson()
        val restored = EarTrainingProgress.fromJson(json)

        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
    }

    @Test
    fun `JSON 往返保留 per-type 统计`() {
        val progress = EarTrainingProgress()
        progress.recordSession(ExerciseType.SCALE, Difficulty.INTERMEDIATE, 5, 10, 3)

        val json = progress.toJson()
        val restored = EarTrainingProgress.fromJson(json)
        val p1 = progress.getProgress(ExerciseType.SCALE, Difficulty.INTERMEDIATE)
        val p2 = restored.getProgress(ExerciseType.SCALE, Difficulty.INTERMEDIATE)

        assertEquals(p1.totalAnswered, p2.totalAnswered)
        assertEquals(p1.totalCorrect, p2.totalCorrect)
        assertEquals(p1.sessionCount, p2.sessionCount)
        assertEquals(p1.bestStreak, p2.bestStreak)
        assertEquals(p1.bestAccuracy, p2.bestAccuracy, 0.001)
    }

    @Test
    fun `空进度序列化为有效 JSON`() {
        val progress = EarTrainingProgress()
        val json = progress.toJson()
        assertTrue(json.startsWith("{"))
        val restored = EarTrainingProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = EarTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = EarTrainingProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `null 返回空进度`() {
        val restored = EarTrainingProgress.fromJson("null")
        assertEquals(0, restored.totalAnswered)
    }

    // ── 多组合序列化 ──────────────────────────────────────

    @Test
    fun `多组合序列化往返保留所有键`() {
        val progress = EarTrainingProgress()
        for (type in ExerciseType.ALL) {
            for (diff in Difficulty.ALL) {
                progress.recordSession(type, diff, 5, 10, 3)
            }
        }
        val json = progress.toJson()
        val restored = EarTrainingProgress.fromJson(json)
        // 9 种组合都应该恢复
        assertEquals(progress.totalSessions, restored.totalSessions)
        for (type in ExerciseType.ALL) {
            for (diff in Difficulty.ALL) {
                val p1 = progress.getProgress(type, diff)
                val p2 = restored.getProgress(type, diff)
                assertEquals("$type $diff", p1.totalAnswered, p2.totalAnswered)
            }
        }
    }

    // ── ProgressEntry ─────────────────────────────────────

    @Test
    fun `ProgressEntry JSON 往返`() {
        val entry = ProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 12,
            bestAccuracy = 0.8333
        )
        val json = entry.toJson()
        val restored = ProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(35, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.8333, restored.bestAccuracy, 0.001)
    }

    @Test
    fun `ProgressEntry 损坏 JSON 返回 null`() {
        val restored = ProgressEntry.fromJson("garbage")
        assertNull(restored)
    }

    @Test
    fun `ProgressEntry 缺少字段默认为 0`() {
        // 只有部分字段的 JSON
        val json = """{"totalAnswered":10,"totalCorrect":5}"""
        val restored = ProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(10, restored!!.totalAnswered)
        assertEquals(5, restored.totalCorrect)
        assertEquals(0, restored.sessionCount)
        assertEquals(0, restored.bestStreak)
    }
}
