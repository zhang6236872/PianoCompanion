package com.pianocompanion.articulationtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 演奏法辨识训练进度跟踪模型单元测试。
 */
class ArticulationProgressTest {

    @Test
    fun `空进度总答题为0`() {
        val progress = ArticulationTrainingProgress()
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0, progress.totalSessions)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    // ── recordSession ──────────────────────────────────────

    @Test
    fun `recordSession累加答题数`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 7, 10, 5)
        assertEquals(20, progress.totalAnswered)
        assertEquals(12, progress.totalCorrect)
    }

    @Test
    fun `recordSession增加会话数`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 3, 5, 2)
        progress.recordSession(ArticulationTrainingDifficulty.ADVANCED, 4, 5, 3)
        assertEquals(2, progress.totalSessions)
    }

    @Test
    fun `recordSession更新bestStreak`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 5, 3)
        assertEquals(3, progress.overallBestStreak)
        progress.recordSession(ArticulationTrainingDifficulty.ADVANCED, 5, 5, 7)
        assertEquals(7, progress.overallBestStreak)
        // 不应降低
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 5, 2)
        assertEquals(7, progress.overallBestStreak)
    }

    @Test
    fun `recordSession更新bestAccuracy`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 8, 10, 3)
        val entry1 = progress.getProgress(ArticulationTrainingDifficulty.BEGINNER)
        assertEquals(0.8, entry1.bestAccuracy, 0.001)
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 9, 10, 5)
        val entry2 = progress.getProgress(ArticulationTrainingDifficulty.BEGINNER)
        assertEquals(0.9, entry2.bestAccuracy, 0.001)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `不同难度的统计互相隔离`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ArticulationTrainingDifficulty.ADVANCED, 8, 10, 6)
        val beg = progress.getProgress(ArticulationTrainingDifficulty.BEGINNER)
        val adv = progress.getProgress(ArticulationTrainingDifficulty.ADVANCED)
        assertEquals(10, beg.totalAnswered)
        assertEquals(5, beg.totalCorrect)
        assertEquals(3, beg.bestStreak)
        assertEquals(10, adv.totalAnswered)
        assertEquals(8, adv.totalCorrect)
        assertEquals(6, adv.bestStreak)
    }

    @Test
    fun `getProgress返回空Entry对未练习难度`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 10, 3)
        val adv = progress.getProgress(ArticulationTrainingDifficulty.ADVANCED)
        assertEquals(0, adv.totalAnswered)
        assertEquals(0, adv.totalCorrect)
        assertEquals(0, adv.bestStreak)
        assertEquals(0.0, adv.bestAccuracy, 0.001)
    }

    @Test
    fun `全局准确率跨难度合并`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ArticulationTrainingDifficulty.ADVANCED, 8, 10, 6)
        assertEquals(13.0 / 20, progress.overallAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `JSON往返保持一致`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 5, 10, 3)
        progress.recordSession(ArticulationTrainingDifficulty.INTERMEDIATE, 7, 10, 4)
        progress.recordSession(ArticulationTrainingDifficulty.ADVANCED, 9, 10, 8)

        val json = progress.toJson()
        val restored = ArticulationTrainingProgress.fromJson(json)

        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.0001)

        for (d in ArticulationTrainingDifficulty.ALL) {
            val orig = progress.getProgress(d)
            val r = restored.getProgress(d)
            assertEquals(orig.totalAnswered, r.totalAnswered)
            assertEquals(orig.totalCorrect, r.totalCorrect)
            assertEquals(orig.bestStreak, r.bestStreak)
            assertEquals(orig.bestAccuracy, r.bestAccuracy, 0.0001)
        }
    }

    @Test
    fun `JSON往返包含所有3种难度`() {
        val progress = ArticulationTrainingProgress()
        progress.recordSession(ArticulationTrainingDifficulty.BEGINNER, 1, 2, 1)
        progress.recordSession(ArticulationTrainingDifficulty.INTERMEDIATE, 1, 2, 1)
        progress.recordSession(ArticulationTrainingDifficulty.ADVANCED, 1, 2, 1)
        val json = progress.toJson()
        val restored = ArticulationTrainingProgress.fromJson(json)
        assertEquals(3, restored.stats.size)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `空JSON返回空进度`() {
        val progress = ArticulationTrainingProgress.fromJson("")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `无效JSON返回空进度`() {
        val progress = ArticulationTrainingProgress.fromJson("not valid json {{{")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `缺少stats字段返回空进度`() {
        val progress = ArticulationTrainingProgress.fromJson("{\"other\":123}")
        assertEquals(0, progress.totalAnswered)
    }

    @Test
    fun `损坏的Entry值被跳过`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":5," +
            "\"sessionCount\":2,\"bestStreak\":3,\"bestAccuracy\":0.5}," +
            "\"BAD\":\"notanobject\"}}"
        val progress = ArticulationTrainingProgress.fromJson(json)
        assertEquals(10, progress.totalAnswered)
        assertEquals(5, progress.totalCorrect)
    }

    @Test
    fun `部分字段缺失使用默认值`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10}}}"
        val progress = ArticulationTrainingProgress.fromJson(json)
        val entry = progress.getProgress(ArticulationTrainingDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
    }

    // ── ProgressEntry ──────────────────────────────────────

    @Test
    fun `cumulativeAccuracy计算正确`() {
        val entry = ArticulationTrainingProgressEntry(
            totalAnswered = 20,
            totalCorrect = 15
        )
        assertEquals(0.75, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy零答题返回0`() {
        val entry = ArticulationTrainingProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `Entry JSON往返一致`() {
        val entry = ArticulationTrainingProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 7,
            bestStreak = 12,
            bestAccuracy = 0.875
        )
        val json = entry.toJson()
        val restored = ArticulationTrainingProgressEntry.fromJson(json)
        assertNotNull(restored)
        assertEquals(42, restored!!.totalAnswered)
        assertEquals(35, restored.totalCorrect)
        assertEquals(7, restored.sessionCount)
        assertEquals(12, restored.bestStreak)
        assertEquals(0.875, restored.bestAccuracy, 0.0001)
    }
}
