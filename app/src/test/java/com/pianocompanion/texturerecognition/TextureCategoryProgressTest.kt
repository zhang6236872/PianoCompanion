package com.pianocompanion.texturerecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureCategoryProgressTest {

    @Test
    fun `空进度序列化为合法 JSON`() {
        val p = TextureCategoryProgress()
        val json = p.toJson()
        val restored = TextureCategoryProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
        assertEquals(0, restored.totalSessions)
    }

    @Test
    fun `recordSession 累计统计正确`() {
        val p = TextureCategoryProgress()
        p.recordSession(MusicTextureDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 4)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(1, p.totalSessions)
        assertEquals(4, p.overallBestStreak)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
    }

    @Test
    fun `多难度按难度隔离统计`() {
        val p = TextureCategoryProgress()
        p.recordSession(MusicTextureDifficulty.BEGINNER, 5, 5, 3)
        p.recordSession(MusicTextureDifficulty.ADVANCED, 2, 5, 2)
        val beg = p.getProgress(MusicTextureDifficulty.BEGINNER)
        val adv = p.getProgress(MusicTextureDifficulty.ADVANCED)
        assertEquals(5, beg.totalAnswered)
        assertEquals(5, adv.totalAnswered)
        assertEquals(3, beg.bestStreak)
        assertEquals(2, adv.bestStreak)
        // 全局
        assertEquals(10, p.totalAnswered)
        assertEquals(7, p.totalCorrect)
        assertEquals(3, p.overallBestStreak)
    }

    @Test
    fun `bestStreak 跨会话保留最大值`() {
        val p = TextureCategoryProgress()
        p.recordSession(MusicTextureDifficulty.INTERMEDIATE, 3, 3, 5)
        p.recordSession(MusicTextureDifficulty.INTERMEDIATE, 3, 3, 2)
        assertEquals(5, p.getProgress(MusicTextureDifficulty.INTERMEDIATE).bestStreak)
    }

    @Test
    fun `bestAccuracy 仅在更高时更新`() {
        val p = TextureCategoryProgress()
        p.recordSession(MusicTextureDifficulty.BEGINNER, 8, 10, 1) // 0.8
        p.recordSession(MusicTextureDifficulty.BEGINNER, 5, 10, 1) // 0.5
        assertEquals(0.8, p.getProgress(MusicTextureDifficulty.BEGINNER).bestAccuracy, 0.0001)
        p.recordSession(MusicTextureDifficulty.BEGINNER, 10, 10, 1) // 1.0
        assertEquals(1.0, p.getProgress(MusicTextureDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }

    @Test
    fun `JSON 往返保持数据一致`() {
        val p = TextureCategoryProgress()
        p.recordSession(MusicTextureDifficulty.BEGINNER, 7, 10, 3)
        p.recordSession(MusicTextureDifficulty.ADVANCED, 4, 10, 2)
        val json = p.toJson()
        val restored = TextureCategoryProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.getProgress(MusicTextureDifficulty.ADVANCED).bestAccuracy,
            restored.getProgress(MusicTextureDifficulty.ADVANCED).bestAccuracy, 0.0001)
    }

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = TextureCategoryProgress.fromJson("not a json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺失字段的 entry 视为不完整返回 null`() {
        // 缺少 bestStreak 字段
        val incomplete = "{\"totalAnswered\":5,\"totalCorrect\":3,\"sessionCount\":1,\"bestAccuracy\":0.6}"
        val entry = TextureCategoryProgressEntry.fromJson(incomplete)
        assertNull(entry)
    }

    @Test
    fun `负数字段回退为 0`() {
        val json = "{\"totalAnswered\":-5,\"totalCorrect\":-2,\"sessionCount\":1,\"bestStreak\":0,\"bestAccuracy\":0.5}"
        val entry = TextureCategoryProgressEntry.fromJson(json)
        assertEquals(0, entry?.totalAnswered)
        assertEquals(0, entry?.totalCorrect)
    }

    @Test
    fun `未记录难度的 getProgress 返回空 entry`() {
        val p = TextureCategoryProgress()
        val e = p.getProgress(MusicTextureDifficulty.ADVANCED)
        assertEquals(0, e.totalAnswered)
        assertEquals(0.0, e.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `total 为 0 时不更新 bestAccuracy`() {
        val p = TextureCategoryProgress()
        p.recordSession(MusicTextureDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(MusicTextureDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }
}
