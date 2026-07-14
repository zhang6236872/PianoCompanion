package com.pianocompanion.texturerecognitiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 织体辨识训练进度跟踪单元测试。
 *
 * 验证累计统计、难度隔离、JSON 往返、容错解析。
 */
class TextureProgressTest {

    // ── 基础记录 ──────────────────────────────────────────

    @Test
    fun `新进度对象为空`() {
        val p = TextureProgress()
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0, p.totalSessions)
        assertEquals(0.0, p.overallAccuracy, 0.001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession 累加答题数和正确数`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 8, 10, 5)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(1, p.totalSessions)
    }

    @Test
    fun `多次 recordSession 累加`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 5, 10, 3)
        p.recordSession(TextureDifficulty.BEGINNER, 7, 10, 6)
        assertEquals(20, p.totalAnswered)
        assertEquals(12, p.totalCorrect)
        assertEquals(2, p.totalSessions)
    }

    // ── 准确率 ──────────────────────────────────────────

    @Test
    fun `overallAccuracy 计算正确`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 8, 10, 0)
        assertEquals(0.8, p.overallAccuracy, 0.001)
    }

    @Test
    fun `跨难度准确率汇总`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 5, 10, 0)
        p.recordSession(TextureDifficulty.ADVANCED, 3, 10, 0)
        assertEquals(20, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.4, p.overallAccuracy, 0.001)
    }

    @Test
    fun `未答题时准确率为零`() {
        val p = TextureProgress()
        assertEquals(0.0, p.overallAccuracy, 0.001)
    }

    // ── 连击 ──────────────────────────────────────────

    @Test
    fun `overallBestStreak 取跨难度最大值`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 5, 10, 7)
        p.recordSession(TextureDifficulty.ADVANCED, 3, 10, 12)
        assertEquals(12, p.overallBestStreak)
    }

    @Test
    fun `bestStreak 只增不减`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 5, 10, 8)
        p.recordSession(TextureDifficulty.BEGINNER, 3, 10, 4)
        // 第二次连击较低，bestStreak 应保持 8
        val entry = p.getProgress(TextureDifficulty.BEGINNER)
        assertEquals(8, entry.bestStreak)
    }

    // ── 难度隔离 ──────────────────────────────────────────

    @Test
    fun `不同难度统计独立`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(TextureDifficulty.ADVANCED, 2, 10, 1)

        val beg = p.getProgress(TextureDifficulty.BEGINNER)
        val adv = p.getProgress(TextureDifficulty.ADVANCED)

        assertEquals(10, beg.totalAnswered)
        assertEquals(8, beg.totalCorrect)
        assertEquals(5, beg.bestStreak)

        assertEquals(10, adv.totalAnswered)
        assertEquals(2, adv.totalCorrect)
        assertEquals(1, adv.bestStreak)
    }

    @Test
    fun `未记录的难度返回空统计`() {
        val p = TextureProgress()
        val entry = p.getProgress(TextureDifficulty.INTERMEDIATE)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0, entry.totalCorrect)
        assertEquals(0, entry.bestStreak)
    }

    // ── bestAccuracy ──────────────────────────────────────────

    @Test
    fun `bestAccuracy 记录最高会话准确率`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 5, 10, 0) // 50%
        p.recordSession(TextureDifficulty.BEGINNER, 9, 10, 0) // 90%
        p.recordSession(TextureDifficulty.BEGINNER, 3, 10, 0) // 30%
        val entry = p.getProgress(TextureDifficulty.BEGINNER)
        assertEquals(0.9, entry.bestAccuracy, 0.001)
    }

    @Test
    fun `cumulativeAccuracy 与累计值一致`() {
        val p = TextureProgress()
        p.recordSession(TextureDifficulty.BEGINNER, 5, 10, 0)
        p.recordSession(TextureDifficulty.BEGINNER, 7, 10, 0)
        val entry = p.getProgress(TextureDifficulty.BEGINNER)
        assertEquals(0.6, entry.cumulativeAccuracy, 0.001)
    }

    // ── JSON 往返 ──────────────────────────────────────────

    @Test
    fun `JSON 序列化往返保持数据`() {
        val original = TextureProgress()
        original.recordSession(TextureDifficulty.BEGINNER, 8, 10, 5)
        original.recordSession(TextureDifficulty.INTERMEDIATE, 6, 10, 3)
        original.recordSession(TextureDifficulty.ADVANCED, 4, 10, 2)

        val json = original.toJson()
        val restored = TextureProgress.fromJson(json)

        assertEquals(original.totalAnswered, restored.totalAnswered)
        assertEquals(original.totalCorrect, restored.totalCorrect)
        assertEquals(original.totalSessions, restored.totalSessions)
        assertEquals(original.overallAccuracy, restored.overallAccuracy, 0.0001)
        assertEquals(original.overallBestStreak, restored.overallBestStreak)

        val origBeg = original.getProgress(TextureDifficulty.BEGINNER)
        val restBeg = restored.getProgress(TextureDifficulty.BEGINNER)
        assertEquals(origBeg.totalAnswered, restBeg.totalAnswered)
        assertEquals(origBeg.totalCorrect, restBeg.totalCorrect)
        assertEquals(origBeg.bestStreak, restBeg.bestStreak)
        assertEquals(origBeg.bestAccuracy, restBeg.bestAccuracy, 0.0001)
    }

    @Test
    fun `空进度 JSON 往返`() {
        val original = TextureProgress()
        val json = original.toJson()
        val restored = TextureProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    // ── 容错解析 ──────────────────────────────────────────

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = TextureProgress.fromJson("not a json")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `空字符串返回空进度`() {
        val restored = TextureProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少 stats 字段返回空进度`() {
        val restored = TextureProgress.fromJson("{\"other\":\"value\"}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `部分损坏的 stats 不影响已解析部分`() {
        // 一个有效的难度 + 一个损坏的条目
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10,\"totalCorrect\":8,\"sessionCount\":1,\"bestStreak\":5,\"bestAccuracy\":0.8},\"BROKEN\":xxx}}"
        val restored = TextureProgress.fromJson(json)
        assertEquals(10, restored.totalAnswered)
        assertEquals(8, restored.totalCorrect)
    }

    @Test
    fun `数值缺失时回退为默认值`() {
        val json = "{\"stats\":{\"BEGINNER\":{\"totalAnswered\":10}}"
        val restored = TextureProgress.fromJson(json)
        // 即使部分字段缺失也不崩溃
        val entry = restored.getProgress(TextureDifficulty.BEGINNER)
        assertEquals(10, entry.totalAnswered)
    }
}
