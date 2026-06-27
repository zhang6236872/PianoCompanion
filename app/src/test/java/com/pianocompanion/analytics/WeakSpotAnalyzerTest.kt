package com.pianocompanion.analytics

import com.pianocompanion.data.model.ErrorPosition
import com.pianocompanion.data.model.MatchStatus
import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WeakSpotAnalyzer] 纯 JVM 单元测试 —— 覆盖弱项判定、严重程度排序、趋势分析、
 * 段落合并、错误类型细分与各类边界情况。
 */
class WeakSpotAnalyzerTest {

    // ---- 测试数据构造辅助 ----

    private var timeSeed = 1_000L

    private fun err(
        measure: Int,
        type: MatchStatus = MatchStatus.WRONG_PITCH,
        expected: String = "C4",
        detected: String = "D4"
    ) = ErrorPosition(
        measureIndex = measure,
        expectedNote = expected,
        detectedNote = detected,
        errorType = type,
        timestamp = timeSeed
    )

    private fun nextTime(): Long = timeSeed++

    /**
     * 构建一个会话，errorsByMeasure 为 measureIndex -> 该小节的错误列表。
     */
    private fun session(
        errorsByMeasure: Map<Int, List<MatchStatus>> = emptyMap(),
        startTime: Long = nextTime()
    ): SessionRecord {
        val positions = errorsByMeasure.flatMap { (measure, types) ->
            types.map { err(measure, it) }
        }
        return SessionRecord(
            id = startTime,
            scoreTitle = "欢乐颂",
            startTime = startTime,
            durationMs = 60_000L,
            totalNotes = 100,
            correctNotes = 100 - positions.size,
            wrongNotes = positions.size,
            missedNotes = 0,
            extraNotes = 0,
            accuracy = if (positions.isNotEmpty()) 0.9f else 1.0f,
            errorPositions = positions
        )
    }

    // ---- 边界情况 ----

    @Test
    fun `空会话列表返回空报告`() {
        val report = WeakSpotAnalyzer.analyze(emptyList())
        assertFalse(report.hasWeakSpots)
        assertEquals(0, report.totalSessions)
        assertTrue(report.summary.contains("暂无练习数据"))
    }

    @Test
    fun `会话无任何错误时返回鼓励信息`() {
        val report = WeakSpotAnalyzer.analyze(listOf(session()))
        assertFalse(report.hasWeakSpots)
        assertEquals(1, report.totalSessions)
        assertTrue(report.summary.contains("表现优异"))
    }

    @Test
    fun `errorPositions 为空列表的会话也视为无错误`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(startTime = 5_000L))
        )
        assertFalse(report.hasWeakSpots)
    }

    // ---- 弱项判定阈值 ----

    @Test
    fun `单次偶发错误未达阈值不判为弱项`() {
        // 仅 1 次会话，第 3 小节 1 个错误。
        // minAbsoluteErrors=2 且 sessionRatio=1.0>=0.5 → 实际会判为弱项（因占比 100%）。
        // 这里验证：当提高 minAbsoluteErrors 且占比阈值更高时，单次 1 错不算弱项。
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(3 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L)),
            options = WeakSpotOptions(minSessionRatio = 1.1f, minAbsoluteErrors = 2)
        )
        assertFalse(report.hasWeakSpots)
    }

    @Test
    fun `占比达标的单错误判为弱项`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(3 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L))
        )
        assertTrue(report.hasWeakSpots)
        assertEquals(1, report.weakSpots.size)
        assertEquals(3, report.weakSpots[0].measureIndex)
        assertEquals(1, report.weakSpots[0].errorCount)
        assertEquals(1f, report.weakSpots[0].sessionRatio, 0.001f)
    }

    @Test
    fun `绝对错误数达标但占比不足仍判为弱项`() {
        // 4 次会话，仅 1 次（第 1 次）在第 2 小节出错 3 次。
        // 占比 = 1/4 = 0.25 < 0.5，但 errorCount=3 >= minAbsoluteErrors=2 → 仍判弱项。
        val sessions = listOf(
            session(mapOf(2 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE, MatchStatus.RHYTHM_ERROR)), startTime = 1L),
            session(startTime = 2L),
            session(startTime = 3L),
            session(startTime = 4L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        val spot = report.weakSpots.firstOrNull { it.measureIndex == 2 }
        assertNotNull(spot)
        assertEquals(3, spot!!.errorCount)
        assertEquals(0.25f, spot.sessionRatio, 0.001f)
    }

    @Test
    fun `占比与绝对数都不足的小节被过滤`() {
        // 4 次会话，第 5 小节仅在 1 次会话中出错 1 次（占比 0.25，errorCount 1）。
        val sessions = listOf(
            session(mapOf(5 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L),
            session(startTime = 2L),
            session(startTime = 3L),
            session(startTime = 4L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertFalse(report.hasWeakSpots)
    }

    // ---- 严重程度排序 ----

    @Test
    fun `弱项按严重程度降序排列`() {
        // 第 1 小节：4 次错误（更严重）；第 8 小节：2 次错误。
        // 两者 sessionRatio 均为 1.0，severityScore 由 errorCount 主导。
        val sessions = listOf(
            session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE, MatchStatus.EXTRA_NOTE, MatchStatus.RHYTHM_ERROR),
                8 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertEquals(2, report.weakSpots.size)
        assertEquals(1, report.weakSpots[0].measureIndex) // 4 错 > 2 错
        assertEquals(8, report.weakSpots[1].measureIndex)
    }

    @Test
    fun `持续性加权使多次会话出错的小节排名更高`() {
        // 小节 A：1 次会话中错 5 次（errorCount=5, ratio=1.0, score=5+10=15）
        // 小节 B：3 次会话中各错 2 次（errorCount=6, ratio=1.0, score=6+10=16）
        // → B 更严重。这里构造：3 次会话，B 在 3 次都出错（ratio 1.0）。
        val sessions = listOf(
            session(mapOf(
                0 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                9 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L),
            session(mapOf(9 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)), startTime = 2L),
            session(mapOf(9 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)), startTime = 3L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        // 小节 9: errorCount=6, ratio=1.0 → score 16; 小节 0: errorCount=5, ratio=0.33 → score 8.33
        assertEquals(9, report.weakSpots[0].measureIndex)
        assertEquals(0, report.weakSpots[1].measureIndex)
    }

    // ---- 错误类型细分 ----

    @Test
    fun `dominantErrorType 为最常见错误类型`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(
                4 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE)
            ), startTime = 1L))
        )
        assertEquals(MatchStatus.WRONG_PITCH, report.weakSpots[0].dominantErrorType)
    }

    @Test
    fun `errorTypeCounts 正确统计各类型`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(
                4 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE, MatchStatus.MISSING_NOTE, MatchStatus.EXTRA_NOTE)
            ), startTime = 1L))
        )
        val counts = report.weakSpots[0].errorTypeCounts
        assertEquals(1, counts[MatchStatus.WRONG_PITCH])
        assertEquals(2, counts[MatchStatus.MISSING_NOTE])
        assertEquals(1, counts[MatchStatus.EXTRA_NOTE])
    }

    // ---- 趋势分析 ----

    @Test
    fun `单会话趋势为数据不足`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(2 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L))
        )
        // 单会话：half=0，older 区间空 → INSUFFICIENT_DATA
        assertEquals(WeakSpotTrend.INSUFFICIENT_DATA, report.weakSpots[0].trend)
    }

    @Test
    fun `近期无错误判定为正在改善`() {
        // 4 次会话：前 2 次第 1 小节出错，后 2 次干净。
        val sessions = listOf(
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L),
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 2L),
            session(startTime = 3L),
            session(startTime = 4L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        val spot = report.weakSpots.first { it.measureIndex == 1 }
        assertEquals(WeakSpotTrend.IMPROVING, spot.trend)
    }

    @Test
    fun `早期无错误近期出错判定为恶化`() {
        // 4 次会话：前 2 次干净，后 2 次第 1 小节出错。
        val sessions = listOf(
            session(startTime = 1L),
            session(startTime = 2L),
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 3L),
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 4L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        val spot = report.weakSpots.first { it.measureIndex == 1 }
        assertEquals(WeakSpotTrend.WORSENING, spot.trend)
    }

    @Test
    fun `错误率持平判定为稳定`() {
        // 4 次会话，4 次都在第 1 小节各错 1 次 → early rate=1, recent rate=1。
        val sessions = (1..4).map { idx ->
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = idx.toLong())
        }
        val report = WeakSpotAnalyzer.analyze(sessions)
        val spot = report.weakSpots.first { it.measureIndex == 1 }
        assertEquals(WeakSpotTrend.STABLE, spot.trend)
    }

    @Test
    fun `奇数会话时近期区多取一个仍可判定趋势`() {
        // 3 次会话：第 1 次在第 2 小节错 2 次（达 minAbsoluteErrors），后 2 次干净。
        // half=1 → older=[0], recent=[1,2]。
        val sessions = listOf(
            session(mapOf(2 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)), startTime = 1L),
            session(startTime = 2L),
            session(startTime = 3L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        val spot = report.weakSpots.first { it.measureIndex == 2 }
        assertEquals(WeakSpotTrend.IMPROVING, spot.trend)
    }

    // ---- 段落合并 ----

    @Test
    fun `相邻弱项小节合并为单一段落`() {
        val sessions = listOf(
            session(mapOf(
                3 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                4 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                5 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertEquals(1, report.recommendedPassages.size)
        val passage = report.recommendedPassages[0]
        assertEquals(3, passage.startMeasure)
        assertEquals(5, passage.endMeasure)
        assertEquals(3, passage.measureCount)
        assertEquals(6, passage.totalErrors)
    }

    @Test
    fun `间隔超过阈值的小节拆分为多个段落`() {
        val sessions = listOf(
            session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                20 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertEquals(2, report.recommendedPassages.size)
    }

    @Test
    fun `maxPassageGap 允许间隔小节合并`() {
        // 小节 3 与 6：间隔 3。maxPassageGap=3 → 合并；默认 maxPassageGap=1 → 拆分。
        val sessions = listOf(
            session(mapOf(
                3 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                6 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L)
        )
        val merged = WeakSpotAnalyzer.analyze(sessions, WeakSpotOptions(maxPassageGap = 3))
        assertEquals(1, merged.recommendedPassages.size)

        val split = WeakSpotAnalyzer.analyze(sessions)
        assertEquals(2, split.recommendedPassages.size)
    }

    @Test
    fun `段落按累计错误数降序排列`() {
        val sessions = listOf(
            session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH),                          // passage A: 1 err
                10 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE, // passage B: 3 err
                    MatchStatus.EXTRA_NOTE)
            ), startTime = 1L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertEquals(10, report.recommendedPassages[0].startMeasure) // 3 错的在前
        assertEquals(1, report.recommendedPassages[1].startMeasure)
    }

    @Test
    fun `worstSpot 返回段落中最严重的小节`() {
        val sessions = listOf(
            session(mapOf(
                3 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                4 to listOf(MatchStatus.WRONG_PITCH)
            ), startTime = 1L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        val passage = report.recommendedPassages[0]
        assertNotNull(passage.worstSpot)
        assertEquals(3, passage.worstSpot!!.measureIndex)
    }

    // ---- 跨会话聚合 ----

    @Test
    fun `跨多次会话正确累计错误`() {
        val sessions = listOf(
            session(mapOf(2 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L),
            session(mapOf(2 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE)), startTime = 2L),
            session(mapOf(2 to listOf(MatchStatus.RHYTHM_ERROR)), startTime = 3L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        val spot = report.weakSpots.first { it.measureIndex == 2 }
        assertEquals(4, spot.errorCount)
        assertEquals(3, spot.sessionCount)
        assertEquals(1f, spot.sessionRatio, 0.001f)
    }

    @Test
    fun `totalSessions 反映传入会话总数`() {
        val sessions = (1..5).map { session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = it.toLong()) }
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertEquals(5, report.totalSessions)
    }

    @Test
    fun `measuresAnalyzed 收集所有出现过错误的小节`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(
                2 to listOf(MatchStatus.WRONG_PITCH),
                7 to listOf(MatchStatus.WRONG_PITCH),
                9 to listOf(MatchStatus.WRONG_PITCH)
            ), startTime = 1L))
        )
        assertEquals(setOf(2, 7, 9), report.measuresAnalyzed)
    }

    // ---- 摘要文案 ----

    @Test
    fun `无弱项摘要包含稳定字样`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L)),
            options = WeakSpotOptions(minSessionRatio = 1.1f, minAbsoluteErrors = 2)
        )
        assertFalse(report.hasWeakSpots)
        assertTrue(report.summary.contains("未发现明显弱项"))
    }

    @Test
    fun `有弱项摘要包含薄弱小节数与优先练习建议`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(
                3 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                4 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L))
        )
        assertTrue(report.summary.contains("2 个薄弱小节"))
        assertTrue(report.summary.contains("优先练习"))
        // 显示用 1 基：内部 3,4 → 显示 4,5
        assertTrue(report.summary.contains("4–5"))
    }

    @Test
    fun `全部改善时摘要提示卓有成效`() {
        // 4 会话，前 2 出错、后 2 干净 → 全部弱项 IMPROVING
        val sessions = listOf(
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L),
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 2L),
            session(startTime = 3L),
            session(startTime = 4L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertTrue(report.summary.contains("正在改善") || report.summary.contains("卓有成效"))
    }

    @Test
    fun `全部恶化时摘要提示重点关注`() {
        // 4 会话，前 2 干净、后 2 出错 → 全部 WORSENING
        val sessions = listOf(
            session(startTime = 1L),
            session(startTime = 2L),
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 3L),
            session(mapOf(1 to listOf(MatchStatus.WRONG_PITCH)), startTime = 4L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        assertTrue(report.summary.contains("重点关注"))
    }

    // ---- 综合场景 ----

    @Test
    fun `混合错误类型的多小节多会话综合分析`() {
        val sessions = listOf(
            session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE),
                2 to listOf(MatchStatus.RHYTHM_ERROR, MatchStatus.RHYTHM_ERROR)
            ), startTime = 1L),
            session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH),
                5 to listOf(MatchStatus.EXTRA_NOTE)
            ), startTime = 2L),
            session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.MISSING_NOTE)
            ), startTime = 3L)
        )
        val report = WeakSpotAnalyzer.analyze(sessions)
        // 小节 1：5 错（2+1+2），3/3 会话 → 最严重
        assertEquals(1, report.weakSpots[0].measureIndex)
        assertEquals(5, report.weakSpots[0].errorCount)
        assertEquals(MatchStatus.WRONG_PITCH, report.weakSpots[0].dominantErrorType)
        // 小节 2：2 错（>= minAbsoluteErrors）→ 弱项；小节 5 仅 1 错被过滤
        assertTrue(report.weakSpots.any { it.measureIndex == 1 })
        assertTrue(report.weakSpots.any { it.measureIndex == 2 })
        assertFalse(report.weakSpots.any { it.measureIndex == 5 })
        // 段落应包含小节 1-2（相邻）
        assertTrue(report.recommendedPassages.any { it.startMeasure <= 1 && it.endMeasure >= 2 })
    }

    @Test
    fun `自定义阈值可放宽弱项判定`() {
        // 默认阈值下小节 5（1 错、占比 1/3=0.33）被过滤。
        val sessions = listOf(
            session(mapOf(5 to listOf(MatchStatus.WRONG_PITCH)), startTime = 1L),
            session(startTime = 2L),
            session(startTime = 3L)
        )
        val strict = WeakSpotAnalyzer.analyze(sessions)
        assertFalse(strict.weakSpots.any { it.measureIndex == 5 })

        // 放宽 minSessionRatio 到 0.3、minAbsoluteErrors 到 1 → 判为弱项
        val lenient = WeakSpotAnalyzer.analyze(
            sessions, WeakSpotOptions(minSessionRatio = 0.3f, minAbsoluteErrors = 1)
        )
        assertTrue(lenient.weakSpots.any { it.measureIndex == 5 })
    }

    @Test
    fun `无弱项时段落为空`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(startTime = 1L), session(startTime = 2L))
        )
        assertTrue(report.recommendedPassages.isEmpty())
    }

    @Test
    fun `totalErrors 为所有弱项小节错误之和`() {
        val report = WeakSpotAnalyzer.analyze(
            listOf(session(mapOf(
                1 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH),
                9 to listOf(MatchStatus.WRONG_PITCH, MatchStatus.WRONG_PITCH)
            ), startTime = 1L))
        )
        assertEquals(5, report.totalErrors)
    }
}
