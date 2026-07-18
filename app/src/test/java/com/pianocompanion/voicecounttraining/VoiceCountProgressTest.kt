package com.pianocompanion.voicecounttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceCountProgress] 单元测试。
 */
class VoiceCountProgressTest {

    @Test
    fun `空进度 - 总计为零`() {
        val p = VoiceCountProgress()
        assertEquals(0, p.totalSessions)
        assertEquals(0, p.totalAnswered)
        assertEquals(0, p.totalCorrect)
        assertEquals(0.0, p.overallAccuracy, 0.0001)
        assertEquals(0, p.overallBestStreak)
    }

    @Test
    fun `recordSession 累计统计`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.BEGINNER, correct = 8, total = 10, bestStreak = 5)
        assertEquals(1, p.totalSessions)
        assertEquals(10, p.totalAnswered)
        assertEquals(8, p.totalCorrect)
        assertEquals(0.8, p.overallAccuracy, 0.0001)
        assertEquals(5, p.overallBestStreak)
    }

    @Test
    fun `多次会话累加`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(VoiceCountDifficulty.BEGINNER, 6, 10, 7)
        assertEquals(2, p.totalSessions)
        assertEquals(20, p.totalAnswered)
        assertEquals(14, p.totalCorrect)
        assertEquals(0.7, p.overallAccuracy, 0.0001)
        assertEquals(7, p.overallBestStreak)
    }

    @Test
    fun `bestStreak 取最大值`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.INTERMEDIATE, 5, 5, 3)
        p.recordSession(VoiceCountDifficulty.INTERMEDIATE, 5, 5, 9)
        p.recordSession(VoiceCountDifficulty.INTERMEDIATE, 5, 5, 4)
        assertEquals(9, p.getProgress(VoiceCountDifficulty.INTERMEDIATE).bestStreak)
        assertEquals(9, p.overallBestStreak)
    }

    @Test
    fun `bestAccuracy 取会话最高准确率`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.ADVANCED, 5, 10, 1)  // 0.5
        p.recordSession(VoiceCountDifficulty.ADVANCED, 9, 10, 2)  // 0.9
        p.recordSession(VoiceCountDifficulty.ADVANCED, 7, 10, 3)  // 0.7
        assertEquals(0.9, p.getProgress(VoiceCountDifficulty.ADVANCED).bestAccuracy, 0.0001)
    }

    @Test
    fun `难度隔离 - 不同难度互不影响`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.BEGINNER, 10, 10, 10)
        p.recordSession(VoiceCountDifficulty.ADVANCED, 1, 10, 1)
        val beg = p.getProgress(VoiceCountDifficulty.BEGINNER)
        val adv = p.getProgress(VoiceCountDifficulty.ADVANCED)
        assertEquals(10, beg.totalAnswered)
        assertEquals(10, beg.totalCorrect)
        assertEquals(10, beg.bestStreak)
        assertEquals(10, adv.totalAnswered)
        assertEquals(1, adv.totalCorrect)
        assertEquals(1, adv.bestStreak)
    }

    @Test
    fun `getProgress 未记录难度返回空 entry`() {
        val p = VoiceCountProgress()
        val e = p.getProgress(VoiceCountDifficulty.ADVANCED)
        assertEquals(0, e.totalAnswered)
        assertEquals(0.0, e.cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `total 为 0 时不更新 bestAccuracy`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.BEGINNER, 0, 0, 0)
        assertEquals(0.0, p.getProgress(VoiceCountDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }

    @Test
    fun `JSON 往返 - 完整数据`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.BEGINNER, 8, 10, 5)
        p.recordSession(VoiceCountDifficulty.ADVANCED, 3, 6, 2)
        val json = p.toJson()
        val restored = VoiceCountProgress.fromJson(json)
        assertEquals(p.totalAnswered, restored.totalAnswered)
        assertEquals(p.totalCorrect, restored.totalCorrect)
        assertEquals(p.totalSessions, restored.totalSessions)
        assertEquals(p.overallBestStreak, restored.overallBestStreak)
        assertEquals(p.getProgress(VoiceCountDifficulty.BEGINNER).bestAccuracy,
            restored.getProgress(VoiceCountDifficulty.BEGINNER).bestAccuracy, 0.0001)
    }

    @Test
    fun `fromJson 损坏 JSON 返回空进度`() {
        val p = VoiceCountProgress.fromJson("not a json {{{")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson 空 JSON 返回空进度`() {
        val p = VoiceCountProgress.fromJson("")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `fromJson 缺失 stats 键返回空进度`() {
        val p = VoiceCountProgress.fromJson("{\"foo\":{}}")
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `entry 缺失字段被拒绝不计入`() {
        // 缺 bestAccuracy 字段
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5}}}"""
        val p = VoiceCountProgress.fromJson(json)
        assertEquals(0, p.totalAnswered)
    }

    @Test
    fun `entry 完整字段正常解析`() {
        val json = """{"stats":{"BEGINNER":{"totalAnswered":10,"totalCorrect":8,"sessionCount":1,"bestStreak":5,"bestAccuracy":0.8000}}}"""
        val p = VoiceCountProgress.fromJson(json)
        val e = p.getProgress(VoiceCountDifficulty.BEGINNER)
        assertEquals(10, e.totalAnswered)
        assertEquals(8, e.totalCorrect)
        assertEquals(1, e.sessionCount)
        assertEquals(5, e.bestStreak)
        assertEquals(0.8, e.bestAccuracy, 0.0001)
    }

    @Test
    fun `cumulativeAccuracy 计算正确`() {
        val p = VoiceCountProgress()
        p.recordSession(VoiceCountDifficulty.INTERMEDIATE, 3, 10, 1)
        assertEquals(0.3, p.getProgress(VoiceCountDifficulty.INTERMEDIATE).cumulativeAccuracy, 0.0001)
    }

    @Test
    fun `entry fromJson 非 JSON 返回 null`() {
        assertNull(VoiceCountProgressEntry.fromJson("xxx"))
    }

    @Test
    fun `多难度 JSON 往返`() {
        val p = VoiceCountProgress()
        for (d in VoiceCountDifficulty.ALL) {
            p.recordSession(d, 5, 10, 3)
        }
        val restored = VoiceCountProgress.fromJson(p.toJson())
        for (d in VoiceCountDifficulty.ALL) {
            val e = restored.getProgress(d)
            assertEquals(10, e.totalAnswered)
            assertEquals(5, e.totalCorrect)
            assertEquals(1, e.sessionCount)
            assertEquals(3, e.bestStreak)
        }
    }

    @Test
    fun `toJson 空进度产生合法 JSON`() {
        val json = VoiceCountProgress().toJson()
        val restored = VoiceCountProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }
}
