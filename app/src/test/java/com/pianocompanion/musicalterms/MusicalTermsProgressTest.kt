package com.pianocompanion.musicalterms

import org.junit.Test
import org.junit.Assert.*

/**
 * 音乐术语训练进度（MusicalTermsProgress）单元测试。
 *
 * 验证进度记录、JSON 序列化往返、容错解析等特性。
 */
class MusicalTermsProgressTest {

    @Test
    fun `空进度`() {
        val progress = MusicalTermsProgress()
        assertEquals(0, progress.totalSessions)
        assertEquals(0, progress.totalAnswered)
        assertEquals(0, progress.totalCorrect)
        assertEquals(0.0, progress.overallAccuracy, 0.001)
        assertEquals(0, progress.overallBestStreak)
    }

    @Test
    fun `recordSession 累加统计`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, null, 5, 10, 3)
        assertEquals(1, progress.totalSessions)
        assertEquals(10, progress.totalAnswered)
        assertEquals(5, progress.totalCorrect)
        assertEquals(0.5, progress.overallAccuracy, 0.001)
        assertEquals(3, progress.overallBestStreak)
    }

    @Test
    fun `多次 recordSession 累加`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, null, 5, 10, 3)
        progress.recordSession(TermDifficulty.BEGINNER, null, 8, 10, 7)
        assertEquals(2, progress.totalSessions)
        assertEquals(20, progress.totalAnswered)
        assertEquals(13, progress.totalCorrect)
        assertEquals(0.65, progress.overallAccuracy, 0.001)
        assertEquals(7, progress.overallBestStreak)
    }

    @Test
    fun `不同难度分开统计`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, null, 5, 10, 3)
        progress.recordSession(TermDifficulty.ADVANCED, null, 3, 10, 5)

        val beginner = progress.getProgress(TermDifficulty.BEGINNER, null)
        val advanced = progress.getProgress(TermDifficulty.ADVANCED, null)

        assertEquals(10, beginner.totalAnswered)
        assertEquals(5, beginner.totalCorrect)
        assertEquals(3, beginner.bestStreak)

        assertEquals(10, advanced.totalAnswered)
        assertEquals(3, advanced.totalCorrect)
        assertEquals(5, advanced.bestStreak)
    }

    @Test
    fun `不同类别分开统计`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, TermCategory.TEMPO, 5, 10, 3)
        progress.recordSession(TermDifficulty.BEGINNER, TermCategory.DYNAMICS, 3, 8, 2)

        val tempo = progress.getProgress(TermDifficulty.BEGINNER, TermCategory.TEMPO)
        val dynamics = progress.getProgress(TermDifficulty.BEGINNER, TermCategory.DYNAMICS)

        assertEquals(10, tempo.totalAnswered)
        assertEquals(8, dynamics.totalAnswered)
    }

    @Test
    fun `bestAccuracy 更新`() {
        val progress = MusicalTermsProgress()
        // 第一次 50%
        progress.recordSession(TermDifficulty.BEGINNER, null, 5, 10, 0)
        assertEquals(0.5, progress.getProgress(TermDifficulty.BEGINNER, null).bestAccuracy, 0.001)
        // 第二次 80%
        progress.recordSession(TermDifficulty.BEGINNER, null, 8, 10, 0)
        assertEquals(0.8, progress.getProgress(TermDifficulty.BEGINNER, null).bestAccuracy, 0.001)
        // 第三次 60%（不更新）
        progress.recordSession(TermDifficulty.BEGINNER, null, 6, 10, 0)
        assertEquals(0.8, progress.getProgress(TermDifficulty.BEGINNER, null).bestAccuracy, 0.001)
    }

    @Test
    fun `bestStreak 只增不减`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, null, 5, 5, 10)
        assertEquals(10, progress.getProgress(TermDifficulty.BEGINNER, null).bestStreak)
        progress.recordSession(TermDifficulty.BEGINNER, null, 5, 5, 3)
        assertEquals(10, progress.getProgress(TermDifficulty.BEGINNER, null).bestStreak)
    }

    @Test
    fun `JSON 序列化往返`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, TermCategory.TEMPO, 5, 10, 3)
        progress.recordSession(TermDifficulty.INTERMEDIATE, null, 8, 12, 7)

        val json = progress.toJson()
        val restored = MusicalTermsProgress.fromJson(json)

        assertEquals(progress.totalSessions, restored.totalSessions)
        assertEquals(progress.totalAnswered, restored.totalAnswered)
        assertEquals(progress.totalCorrect, restored.totalCorrect)
        assertEquals(progress.overallAccuracy, restored.overallAccuracy, 0.001)
        assertEquals(progress.overallBestStreak, restored.overallBestStreak)
    }

    @Test
    fun `空进度 JSON 往返`() {
        val progress = MusicalTermsProgress()
        val json = progress.toJson()
        val restored = MusicalTermsProgress.fromJson(json)
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `null JSON 返回空进度`() {
        val restored = MusicalTermsProgress.fromJson("")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `损坏 JSON 返回空进度`() {
        val restored = MusicalTermsProgress.fromJson("not json {{{")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `缺少 stats 字段返回空进度`() {
        val restored = MusicalTermsProgress.fromJson("{\"other\":123}")
        assertEquals(0, restored.totalAnswered)
    }

    @Test
    fun `TermProgressEntry JSON 往返`() {
        val entry = TermProgressEntry(
            totalAnswered = 42,
            totalCorrect = 35,
            sessionCount = 5,
            bestStreak = 8,
            bestAccuracy = 0.8333
        )
        val json = entry.toJson()
        val restored = TermProgressEntry.fromJson(json)!!
        assertEquals(42, restored.totalAnswered)
        assertEquals(35, restored.totalCorrect)
        assertEquals(5, restored.sessionCount)
        assertEquals(8, restored.bestStreak)
        assertEquals(0.8333, restored.bestAccuracy, 0.0001)
    }

    @Test
    fun `TermProgressEntry fromJson 无效 JSON 返回 null`() {
        assertNull(TermProgressEntry.fromJson("invalid"))
        assertNull(TermProgressEntry.fromJson(""))
    }

    @Test
    fun `TermProgressEntry cumulativeAccuracy`() {
        val entry = TermProgressEntry(totalAnswered = 10, totalCorrect = 7)
        assertEquals(0.7, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `TermProgressEntry cumulativeAccuracy 未答题为 0`() {
        val entry = TermProgressEntry()
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `key 生成正确`() {
        assertEquals("BEGINNER_ALL", MusicalTermsProgress.key(TermDifficulty.BEGINNER, null))
        assertEquals("ADVANCED_TEMPO", MusicalTermsProgress.key(TermDifficulty.ADVANCED, TermCategory.TEMPO))
        assertEquals("INTERMEDIATE_DYNAMICS", MusicalTermsProgress.key(TermDifficulty.INTERMEDIATE, TermCategory.DYNAMICS))
    }

    @Test
    fun `getProgress 未记录返回空 entry`() {
        val progress = MusicalTermsProgress()
        val entry = progress.getProgress(TermDifficulty.ADVANCED, TermCategory.EXPRESSION)
        assertEquals(0, entry.totalAnswered)
        assertEquals(0.0, entry.cumulativeAccuracy, 0.001)
    }

    @Test
    fun `零 total 答题不更新 bestAccuracy`() {
        val progress = MusicalTermsProgress()
        progress.recordSession(TermDifficulty.BEGINNER, null, 0, 0, 0)
        assertEquals(0.0, progress.getProgress(TermDifficulty.BEGINNER, null).bestAccuracy, 0.001)
    }

    @Test
    fun `复杂多键 JSON 往返`() {
        val progress = MusicalTermsProgress()
        for (diff in TermDifficulty.ALL) {
            for (cat in listOf<TermCategory?>(null, TermCategory.TEMPO, TermCategory.DYNAMICS)) {
                progress.recordSession(diff, cat, 3, 5, 2)
            }
        }
        val json = progress.toJson()
        val restored = MusicalTermsProgress.fromJson(json)
        assertEquals(progress.totalSessions, restored.totalSessions)
        for (diff in TermDifficulty.ALL) {
            for (cat in listOf<TermCategory?>(null, TermCategory.TEMPO, TermCategory.DYNAMICS)) {
                val orig = progress.getProgress(diff, cat)
                val rest = restored.getProgress(diff, cat)
                assertEquals(orig.totalAnswered, rest.totalAnswered)
                assertEquals(orig.bestStreak, rest.bestStreak)
            }
        }
    }
}
