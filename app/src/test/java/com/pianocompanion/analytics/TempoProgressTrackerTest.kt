package com.pianocompanion.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TempoProgressTrackerTest {

    private lateinit var tracker: TempoProgressTracker

    @Before
    fun setUp() {
        tracker = TempoProgressTracker()
    }

    // ── TempoProgressRecord 数据类 ──

    @Test
    fun `sectionKey 正确生成`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false)
        assertEquals("欢乐颂::4-7", r.sectionKey)
    }

    @Test
    fun `bpmGain 计算`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 60, 85, 120, false)
        assertEquals(25, r.bpmGain)
    }

    @Test
    fun `bpmGain 负值时 startBpm 大于 peakBpm`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 80, 60, 120, false)
        assertEquals(-20, r.bpmGain)
    }

    @Test
    fun `completionRatio 正常计算`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 60, 90, 120, false)
        // (90-60)/(120-60) = 30/60 = 0.5
        assertEquals(0.5f, r.completionRatio, 0.001f)
    }

    @Test
    fun `completionRatio 完成时为1`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 60, 120, 120, true)
        assertEquals(1f, r.completionRatio, 0.001f)
    }

    @Test
    fun `completionRatio startBpm 等于 targetBpm 时为1`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 100, 100, 100, true)
        assertEquals(1f, r.completionRatio, 0.001f)
    }

    @Test
    fun `completionRatio peakBpm 超过 targetBpm 时 clamp 到1`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 60, 150, 120, true)
        assertEquals(1f, r.completionRatio, 0.001f)
    }

    @Test
    fun `completionRatio peakBpm 低于 startBpm 时 clamp 到0`() {
        val r = TempoProgressRecord("欢乐颂", 4, 7, 80, 50, 120, false)
        assertEquals(0f, r.completionRatio, 0.001f)
    }

    // ── record / getSectionRecords ──

    @Test
    fun `record 返回该段落记录总数`() {
        val n1 = tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false))
        assertEquals(1, n1)
        val n2 = tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 90, 120, false))
        assertEquals(2, n2)
    }

    @Test
    fun `getSectionRecords 按段落筛选`() {
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("欢乐颂", 8, 12, 60, 70, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 90, 120, false, timestamp = 3000))

        val section4_7 = tracker.getSectionRecords("欢乐颂", 4, 7)
        assertEquals(2, section4_7.size)
        assertEquals(80, section4_7[0].peakBpm)
        assertEquals(90, section4_7[1].peakBpm)
    }

    @Test
    fun `getSectionRecords 不同乐谱不混淆`() {
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false))
        tracker.record(TempoProgressRecord("小星星", 4, 7, 60, 70, 120, false))

        assertEquals(1, tracker.getSectionRecords("欢乐颂", 4, 7).size)
        assertEquals(1, tracker.getSectionRecords("小星星", 4, 7).size)
    }

    @Test
    fun `getSectionRecords 无记录返回空列表`() {
        assertTrue(tracker.getSectionRecords("欢乐颂", 4, 7).isEmpty())
    }

    @Test
    fun `records 按时间戳升序`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 2000))

        val all = tracker.records
        assertEquals(1000L, all[0].timestamp)
        assertEquals(2000L, all[1].timestamp)
        assertEquals(3000L, all[2].timestamp)
    }

    // ── getBestPeakBpm ──

    @Test
    fun `getBestPeakBpm 返回最高峰值`() {
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 100, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 90, 120, false, timestamp = 3000))

        assertEquals(100, tracker.getBestPeakBpm("欢乐颂", 4, 7))
    }

    @Test
    fun `getBestPeakBpm 无记录返回 null`() {
        assertNull(tracker.getBestPeakBpm("欢乐颂", 4, 7))
    }

    // ── getTrend ──

    @Test
    fun `getTrend 数据不足返回 INSUFFICIENT_DATA`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        assertEquals(TempoProgressTrend.INSUFFICIENT_DATA, tracker.getTrend("A", 0, 0))
    }

    @Test
    fun `getTrend 明显进步返回 IMPROVING`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 72, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 95, 120, false, timestamp = 4000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 100, 120, false, timestamp = 5000))

        assertEquals(TempoProgressTrend.IMPROVING, tracker.getTrend("A", 0, 0))
    }

    @Test
    fun `getTrend 无明显变化返回 STABLE`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 85, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 86, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 85, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 87, 120, false, timestamp = 4000))

        assertEquals(TempoProgressTrend.STABLE, tracker.getTrend("A", 0, 0))
    }

    @Test
    fun `getTrend 退步返回 STAGNANT`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 100, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 102, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 103, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 4000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 88, 120, false, timestamp = 5000))

        assertEquals(TempoProgressTrend.STAGNANT, tracker.getTrend("A", 0, 0))
    }

    @Test
    fun `getTrend 自定义阈值`() {
        // delta = 3，默认阈值 5 → STABLE；阈值 2 → IMPROVING
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 81, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 83, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 84, 120, false, timestamp = 4000))

        assertEquals(
            TempoProgressTrend.STABLE,
            tracker.getTrend("A", 0, 0, TempoProgressOptions(trendDeltaThreshold = 5))
        )
        assertEquals(
            TempoProgressTrend.IMPROVING,
            tracker.getTrend("A", 0, 0, TempoProgressOptions(trendDeltaThreshold = 2))
        )
    }

    // ── getSectionSummary ──

    @Test
    fun `getSectionSummary 无记录返回 null`() {
        assertNull(tracker.getSectionSummary("欢乐颂", 4, 7))
    }

    @Test
    fun `getSectionSummary 正确填充各字段`() {
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 90, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 100, 120, false, timestamp = 3000))

        val summary = tracker.getSectionSummary("欢乐颂", 4, 7)!!
        assertEquals("欢乐颂::4-7", summary.sectionKey)
        assertEquals("欢乐颂", summary.scoreTitle)
        assertEquals(4, summary.startMeasure)
        assertEquals(7, summary.endMeasure)
        assertEquals(3, summary.recordCount)
        assertEquals(80, summary.firstRecord?.peakBpm)
        assertEquals(100, summary.latestRecord?.peakBpm)
        assertEquals(100, summary.bestPeakBpm)
        assertEquals("第 5–8 小节", summary.displayMeasures)
    }

    @Test
    fun `bpmImprovement 计算首次到最近的差值`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 2000))

        val summary = tracker.getSectionSummary("A", 0, 0)!!
        assertEquals(20, summary.bpmImprovement)
    }

    @Test
    fun `hasReachedTarget 达到目标时为 true`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 120, 120, true, timestamp = 1000))
        val summary = tracker.getSectionSummary("A", 0, 0)!!
        assertTrue(summary.hasReachedTarget)
    }

    @Test
    fun `hasReachedTarget 未达到时为 false`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 1000))
        val summary = tracker.getSectionSummary("A", 0, 0)!!
        assertFalse(summary.hasReachedTarget)
    }

    @Test
    fun `avgPeakBpm 计算平均峰值`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 100, 120, false, timestamp = 2000))
        val summary = tracker.getSectionSummary("A", 0, 0)!!
        assertEquals(90f, summary.avgPeakBpm, 0.1f)
    }

    // ── getAllSectionSummaries ──

    @Test
    fun `getAllSectionSummaries 按最近练习时间降序`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("B", 0, 0, 60, 80, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 2000))

        val all = tracker.getAllSectionSummaries()
        assertEquals(2, all.size)
        // B 的最近时间是 3000，A 的是 2000
        assertEquals("B", all[0].scoreTitle)
        assertEquals("A", all[1].scoreTitle)
    }

    @Test
    fun `getAllSectionSummaries 空时返回空列表`() {
        assertTrue(tracker.getAllSectionSummaries().isEmpty())
    }

    // ── estimateSessionsToTarget ──

    @Test
    fun `estimateSessionsToTarget 已完成返回0`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 100, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 120, 120, true, timestamp = 2000))
        assertEquals(0, tracker.estimateSessionsToTarget("A", 0, 0))
    }

    @Test
    fun `estimateSessionsToTarget 单条记录返回 null`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        assertNull(tracker.estimateSessionsToTarget("A", 0, 0))
    }

    @Test
    fun `estimateSessionsToTarget 无进步时返回 null`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 3000))
        assertNull(tracker.estimateSessionsToTarget("A", 0, 0))
    }

    @Test
    fun `estimateSessionsToTarget 稳定进步时给出正数估算`() {
        // 每次 +10 BPM, 还差 30 → 约 3 次
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 3000))
        val estimate = tracker.estimateSessionsToTarget("A", 0, 0)
        assertNotNull(estimate)
        assertEquals(3, estimate)
    }

    // ── buildReadableSummary ──

    @Test
    fun `buildReadableSummary 无记录时返回提示`() {
        val text = tracker.buildReadableSummary("欢乐颂", 4, 7)
        assertTrue(text.contains("暂无渐速练习记录"))
    }

    @Test
    fun `buildReadableSummary 进步时包含提升信息`() {
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 70, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 72, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 90, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 95, 120, false, timestamp = 4000))
        tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 100, 120, false, timestamp = 5000))

        val text = tracker.buildReadableSummary("欢乐颂", 4, 7)
        assertTrue(text.contains("进步"))
        assertTrue(text.contains("70"))
        assertTrue(text.contains("100"))
    }

    @Test
    fun `buildReadableSummary 达到目标时包含庆祝标记`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 100, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 120, 120, true, timestamp = 2000))

        val text = tracker.buildReadableSummary("A", 0, 0)
        assertTrue(text.contains("🎉"))
    }

    @Test
    fun `buildReadableSummary 未达目标时包含剩余差距`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 95, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 100, 120, false, timestamp = 3000))

        val text = tracker.buildReadableSummary("A", 0, 0)
        assertTrue(text.contains("20 BPM"))  // 120 - 100 = 20
    }

    @Test
    fun `buildReadableSummary 稳定趋势时包含稳定字样`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 85, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 86, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 85, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 87, 120, false, timestamp = 4000))

        val text = tracker.buildReadableSummary("A", 0, 0)
        assertTrue(text.contains("稳定"))
    }

    // ── loadRecords ──

    @Test
    fun `loadRecords 替换现有记录并按时间排序`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 5000))
        val loaded = listOf(
            TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 2000),
            TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 1000)
        )
        tracker.loadRecords(loaded)

        assertEquals(2, tracker.records.size)
        assertEquals(1000L, tracker.records[0].timestamp)
        assertEquals(2000L, tracker.records[1].timestamp)
    }

    // ── clear ──

    @Test
    fun `clear 清除所有记录`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("B", 0, 0, 60, 90, 120, false, timestamp = 2000))
        assertEquals(2, tracker.records.size)

        tracker.clear()
        assertTrue(tracker.records.isEmpty())
    }

    // ── 边界情况 ──

    @Test
    fun `相同段落不同时间的记录正确分组`() {
        for (i in 1..10) {
            tracker.record(TempoProgressRecord("A", 0, 5, 60, 60 + i * 5, 120, false, timestamp = i.toLong() * 1000))
        }
        val section = tracker.getSectionRecords("A", 0, 5)
        assertEquals(10, section.size)
        assertEquals(65, section.first().peakBpm)
        assertEquals(110, section.last().peakBpm)
    }

    @Test
    fun `getTrend 奇数条记录正确分区`() {
        // 5 条记录：early=3, recent=2 (recentRatio=0.4 → 2)
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 70, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 4000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 95, 120, false, timestamp = 5000))

        // early avg = 70, recent avg = 92.5, delta = 22.5 >= 5 → IMPROVING
        assertEquals(TempoProgressTrend.IMPROVING, tracker.getTrend("A", 0, 0))
    }

    @Test
    fun `多条段落混合不影响各自统计`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 80, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("B", 0, 0, 60, 60, 120, false, timestamp = 2000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 90, 120, false, timestamp = 3000))
        tracker.record(TempoProgressRecord("B", 0, 0, 60, 65, 120, false, timestamp = 4000))

        assertEquals(90, tracker.getBestPeakBpm("A", 0, 0))
        assertEquals(65, tracker.getBestPeakBpm("B", 0, 0))
    }

    @Test
    fun `estimateSessionsToTarget 达到目标差距为0时返回0`() {
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 110, 120, false, timestamp = 1000))
        tracker.record(TempoProgressRecord("A", 0, 0, 60, 120, 120, true, timestamp = 2000))
        assertEquals(0, tracker.estimateSessionsToTarget("A", 0, 0))
    }
}
