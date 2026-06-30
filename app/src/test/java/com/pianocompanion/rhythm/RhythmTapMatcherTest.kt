package com.pianocompanion.rhythm

import org.junit.Assert.*
import org.junit.Test

/**
 * [RhythmTapMatcher] 敲击匹配器单元测试。
 *
 * 覆盖：
 * - Perfect/Good/Miss 判定窗口
 * - 评分计算（基础分 + 多余敲击惩罚）
 * - 评级阈值（PERFECT/GREAT/GOOD/TRY_AGAIN）
 * - 贪心匹配算法正确性
 * - 边界情况（空目标、空敲击、完全错过）
 */
class RhythmTapMatcherTest {

    private val matcher = RhythmTapMatcher()

    private fun onset(ms: Long, rest: Boolean = false): OnsetTime =
        OnsetTime(onsetMs = ms, durationMs = 500, isRest = rest, midiNote = 60)

    private fun onsets(vararg ms: Long): List<OnsetTime> =
        ms.map { onset(it) }

    // ── Perfect 判定 ────────────────────────────────────

    @Test
    fun `完全精准敲击全部Perfect`() {
        val targets = onsets(0, 500, 1000)
        val taps = listOf(0L, 500L, 1000L)
        val result = matcher.match(targets, taps)
        assertEquals(3, result.perfectHits)
        assertEquals(0, result.goodHits)
        assertEquals(0, result.missedNotes)
        assertEquals(0, result.extraTaps)
        assertEquals(3, result.totalTargets)
    }

    @Test
    fun `perfect窗口内偏移仍为Perfect`() {
        val targets = onsets(1000)
        val taps = listOf(1050L) // ±80ms 内
        val result = matcher.match(targets, taps)
        assertEquals(1, result.perfectHits)
        assertEquals(0, result.goodHits)
    }

    @Test
    fun `perfect窗口边界80ms仍为Perfect`() {
        val targets = onsets(1000)
        val taps = listOf(1080L) // 正好 80ms
        val result = matcher.match(targets, taps)
        assertEquals(1, result.perfectHits)
    }

    // ── Good 判定 ───────────────────────────────────────

    @Test
    fun `good窗口内偏移为Good`() {
        val targets = onsets(1000)
        val taps = listOf(1120L) // 80~150ms 之间
        val result = matcher.match(targets, taps)
        assertEquals(0, result.perfectHits)
        assertEquals(1, result.goodHits)
    }

    @Test
    fun `good窗口边界150ms仍为Good`() {
        val targets = onsets(1000)
        val taps = listOf(1150L) // 正好 150ms
        val result = matcher.match(targets, taps)
        assertEquals(1, result.goodHits)
    }

    // ── Miss 判定 ───────────────────────────────────────

    @Test
    fun `超出good窗口为Miss`() {
        val targets = onsets(1000)
        val taps = listOf(1200L) // 200ms，超出 150ms 窗口
        val result = matcher.match(targets, taps)
        assertEquals(0, result.perfectHits)
        assertEquals(0, result.goodHits)
        assertEquals(1, result.missedNotes)
    }

    @Test
    fun `完全未敲击全部Miss`() {
        val targets = onsets(0, 500, 1000)
        val result = matcher.match(targets, emptyList())
        assertEquals(3, result.missedNotes)
        assertEquals(0.0, result.score, 0.001)
    }

    // ── 多余敲击惩罚 ────────────────────────────────────

    @Test
    fun `多余敲击降低评分`() {
        val targets = onsets(0, 500)
        val taps = listOf(0L, 500L, 1000L, 1500L) // 2 个多余
        val result = matcher.match(targets, taps)
        assertEquals(2, result.perfectHits)
        assertEquals(2, result.extraTaps)
        // baseScore = 2*1.0/2 = 1.0, penalty = 2*0.15 = 0.3
        assertEquals(0.7, result.score, 0.01)
    }

    @Test
    fun `多余敲击评分不低于0`() {
        val targets = onsets(0)
        val taps = listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L, 3000L, 3500L) // 7 个多余
        val result = matcher.match(targets, taps)
        assertEquals(7, result.extraTaps)
        assertEquals(0.0, result.score, 0.001)
    }

    // ── 评级 ────────────────────────────────────────────

    @Test
    fun `满分且无遗漏为PERFECT`() {
        val targets = onsets(0, 500, 1000)
        val taps = listOf(0L, 500L, 1000L)
        val result = matcher.match(targets, taps)
        assertEquals(TapGrade.PERFECT, result.grade)
    }

    @Test
    fun `高分有Good无Miss为GREAT`() {
        // 2 perfect + 1 good → score = (2+0.6)/3 ≈ 0.867 → GREAT
        val targets = onsets(0, 500, 1000)
        val taps = listOf(0L, 500L, 1120L) // 第三个偏 120ms → good
        val result = matcher.match(targets, taps)
        assertTrue(result.score >= 0.75)
        assertEquals(TapGrade.GREAT, result.grade)
    }

    @Test
    fun `中分为GOOD`() {
        // 1 perfect + 1 good + 1 miss → score = (1+0.6)/3 ≈ 0.533
        val targets = onsets(0, 500, 1000)
        val taps = listOf(0L, 620L) // 第一个 perfect, 第二个 good, 第三个 miss
        val result = matcher.match(targets, taps)
        assertTrue(result.score >= 0.5)
        assertEquals(TapGrade.GOOD, result.grade)
    }

    @Test
    fun `低分为TRY_AGAIN`() {
        val targets = onsets(0, 500, 1000)
        val taps = listOf(800L) // 只敲一个且偏移大
        val result = matcher.match(targets, taps)
        assertEquals(TapGrade.TRY_AGAIN, result.grade)
    }

    // ── 贪心匹配 ────────────────────────────────────────

    @Test
    fun `每个目标匹配最近的未使用敲击`() {
        val targets = onsets(0, 500)
        val taps = listOf(10L, 490L) // 分别最近
        val result = matcher.match(targets, taps)
        assertEquals(2, result.perfectHits)
    }

    @Test
    fun `无序敲击按时间排序后匹配`() {
        val targets = onsets(0, 500, 1000)
        val taps = listOf(1000L, 0L, 500L) // 乱序
        val result = matcher.match(targets, taps)
        assertEquals(3, result.perfectHits)
    }

    @Test
    fun `敲击数少于目标时正确统计Miss`() {
        val targets = onsets(0, 500, 1000, 1500)
        val taps = listOf(0L, 1000L)
        val result = matcher.match(targets, taps)
        assertEquals(2, result.perfectHits)
        assertEquals(2, result.missedNotes)
        assertEquals(0, result.extraTaps)
    }

    // ── 边界情况 ────────────────────────────────────────

    @Test
    fun `空目标返回零分`() {
        val result = matcher.match(emptyList(), listOf(0L, 500L))
        assertEquals(0, result.totalTargets)
        assertEquals(0.0, result.score, 0.001)
        assertEquals(TapGrade.TRY_AGAIN, result.grade)
        assertEquals(2, result.extraTaps)
    }

    @Test
    fun `timingErrors记录每个目标的误差`() {
        val targets = onsets(0, 500)
        val taps = listOf(20L, 490L) // 误差 +20, -10
        val result = matcher.match(targets, taps)
        assertEquals(2, result.timingErrors.size)
        assertEquals(20L, result.timingErrors[0])
        assertEquals(-10L, result.timingErrors[1])
    }

    @Test
    fun `missed目标的timingError为MAX_VALUE`() {
        val targets = onsets(0, 500)
        val taps = listOf(10L) // 只敲第一个
        val result = matcher.match(targets, taps)
        assertEquals(2, result.timingErrors.size)
        assertEquals(10L, result.timingErrors[0])
        assertEquals(Long.MAX_VALUE, result.timingErrors[1])
    }

    @Test
    fun `totalHits等于perfect加good`() {
        val targets = onsets(0, 500, 1000)
        val taps = listOf(0L, 500L, 1120L)
        val result = matcher.match(targets, taps)
        assertEquals(result.perfectHits + result.goodHits, result.totalHits)
    }

    @Test
    fun `scorePercent格式正确`() {
        val targets = onsets(0)
        val taps = listOf(0L)
        val result = matcher.match(targets, taps)
        assertEquals("100%", result.scorePercent)
    }

    @Test
    fun `isPerfect判定`() {
        val targets = onsets(0, 500)
        val taps = listOf(0L, 500L)
        val result = matcher.match(targets, taps)
        assertTrue(result.isPerfect)

        val imperfect = matcher.match(targets, listOf(0L, 600L))
        assertFalse(imperfect.isPerfect)
    }

    @Test
    fun `自定义窗口大小`() {
        val strictMatcher = RhythmTapMatcher(perfectWindowMs = 30, goodWindowMs = 60)
        val targets = onsets(1000)
        // 50ms 偏移：strictMatcher 的 perfect 窗口(30)外、good 窗口(60)内 → Good
        val result = strictMatcher.match(targets, listOf(1050L))
        assertEquals(0, result.perfectHits)
        assertEquals(1, result.goodHits)
    }
}
