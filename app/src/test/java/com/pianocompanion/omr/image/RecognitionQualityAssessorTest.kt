package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RecognitionQualityAssessor] — the OMR confidence-scoring engine.
 *
 * The assessor consumes [RecognitionMetrics] (diagnostic signals collected from each
 * pipeline stage) and produces a [RecognitionQuality] verdict (overall score, level,
 * per-factor breakdown, and a human-readable summary).
 *
 * These are **pure-logic** tests: no Android dependency, no image I/O. Each test
 * constructs a [RecognitionMetrics] instance directly and asserts on the computed
 * score, [ConfidenceLevel], factor details, and summary text.
 *
 * ## Factor weights (must sum to 1.0)
 * | Factor | Weight |
 * |--------|--------|
 * | Staff detection quality | 0.20 |
 * | Clef recognition        | 0.15 |
 * | Notehead count          | 0.20 |
 * | Photo quality           | 0.15 |
 * | Signature recognition   | 0.15 |
 * | Structural completeness | 0.15 |
 */
class RecognitionQualityAssessorTest {

    // ── 辅助：构造高质量识别指标 ──────────────────────────────────────────
    private fun highQualityMetrics(): RecognitionMetrics = RecognitionMetrics(
        systemCount = 2,
        avgLineSpacing = 20,
        lineSpacingCV = 0.0,
        noteheadCount = 20,
        restCount = 2,
        barlineCount = 5,
        systemsWithKnownClef = 2,
        systemsWithUnknownClef = 0,
        timeSignatureDetected = true,
        tempoDetected = true,
        deskewApplied = false,
        keystoneApplied = false,
        noiseRatio = 0.0,
        totalImagePixels = 100_000L
    )

    // ════════════════════════════════════════════════════════════════════════
    //  1. 早期失败路径（无谱表）
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `no systems detected returns LOW with zero score`() {
        val metrics = RecognitionMetrics.EMPTY
        val result = RecognitionQualityAssessor.assess(metrics)

        assertEquals(0.0, result.overallScore, 0.001)
        assertEquals(ConfidenceLevel.LOW, result.level)
        // 只有 1 个因子（谱线检测），权重为 1.0
        assertEquals(1, result.factors.size)
        assertTrue(result.summary.contains("失败") || result.summary.contains("未检测"))
    }

    @Test
    fun `no systems detected summary mentions staff lines`() {
        val metrics = RecognitionMetrics.EMPTY
        val result = RecognitionQualityAssessor.assess(metrics)

        assertTrue("摘要应提及未检测到五线谱", result.summary.contains("五线谱"))
    }

    @Test
    fun `no systems but deskew was applied still returns LOW`() {
        // 即使做了 deskew，没检测到谱表仍然是失败
        val metrics = RecognitionMetrics.EMPTY.copy(
            systemCount = 0,
            deskewApplied = true,
            totalImagePixels = 50_000L
        )
        val result = RecognitionQualityAssessor.assess(metrics)

        assertEquals(0.0, result.overallScore, 0.001)
        assertEquals(ConfidenceLevel.LOW, result.level)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  2. 总体等级划分
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `perfect recognition yields HIGH confidence`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())

        assertEquals(ConfidenceLevel.HIGH, result.level)
        assertTrue("应 ≥ 0.75", result.overallScore >= 0.75)
    }

    @Test
    fun `perfect recognition score is very high`() {
        // 6 个因子中 5 个满分(1.0)，仅符头因对数缩放略低于 1.0
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())

        // 期望约 0.98（符头因子 = 0.85 + 0.15*ln(20)/ln(100) ≈ 0.9476）
        assertTrue("完美识别分数应 ≥ 0.95，实际 ${result.overallScore}", result.overallScore >= 0.95)
    }

    @Test
    fun `medium quality yields MEDIUM confidence`() {
        // 中等：1 系统、CV=0.12、4 音符、谱号已知、无拍号/速度、无小节线/休止符、无校正
        val metrics = RecognitionMetrics(
            systemCount = 1,
            avgLineSpacing = 15,
            lineSpacingCV = 0.12,
            noteheadCount = 4,
            restCount = 0,
            barlineCount = 0,
            systemsWithKnownClef = 1,
            systemsWithUnknownClef = 0,
            timeSignatureDetected = false,
            tempoDetected = false,
            deskewApplied = false,
            keystoneApplied = false,
            noiseRatio = 0.0,
            totalImagePixels = 80_000L
        )
        val result = RecognitionQualityAssessor.assess(metrics)

        assertEquals(ConfidenceLevel.MEDIUM, result.level)
        assertTrue("应在 0.45~0.75 范围，实际 ${result.overallScore}",
            result.overallScore >= 0.45 && result.overallScore < 0.75)
    }

    @Test
    fun `low quality yields LOW confidence`() {
        // 低质量：1 系统、CV=0.20(不规整)、1 音符、谱号未知、无拍号/速度、无小节线、
        // 做了 deskew + keystone 校正（说明照片质量差）
        val metrics = RecognitionMetrics(
            systemCount = 1,
            avgLineSpacing = 15,
            lineSpacingCV = 0.20,
            noteheadCount = 1,
            restCount = 0,
            barlineCount = 0,
            systemsWithKnownClef = 0,
            systemsWithUnknownClef = 1,
            timeSignatureDetected = false,
            tempoDetected = false,
            deskewApplied = true,
            keystoneApplied = true,
            noiseRatio = 0.0,
            totalImagePixels = 80_000L
        )
        val result = RecognitionQualityAssessor.assess(metrics)

        assertEquals(ConfidenceLevel.LOW, result.level)
        assertTrue("应 < 0.45，实际 ${result.overallScore}", result.overallScore < 0.45)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  3. 因子 1：谱线检测质量
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `staff factor rewards consistent spacing`() {
        val consistent = highQualityMetrics().copy(lineSpacingCV = 0.02)
        val inconsistent = highQualityMetrics().copy(lineSpacingCV = 0.20)

        val r1 = RecognitionQualityAssessor.assess(consistent)
        val r2 = RecognitionQualityAssessor.assess(inconsistent)

        val staff1 = r1.factors.first { it.name == "谱线检测质量" }
        val staff2 = r2.factors.first { it.name == "谱线检测质量" }

        assertTrue("CV 小的谱线得分应更高", staff1.score > staff2.score)
        assertEquals(1.0, staff1.score, 0.001) // CV ≤ 0.05 → 满分
        assertTrue(staff1.detail.contains("间距一致"))
    }

    @Test
    fun `staff factor base score when system detected`() {
        // 检测到至少一个系统即得基础分 0.6
        val metrics = highQualityMetrics().copy(lineSpacingCV = 0.30)
        val result = RecognitionQualityAssessor.assess(metrics)
        val staff = result.factors.first { it.name == "谱线检测质量" }

        // CV > 0.15 → bonus = 0.1 → score = 0.7
        assertEquals(0.7, staff.score, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  4. 因子 2：谱号识别
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `clef factor full score when all clefs known`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        val clef = result.factors.first { it.name == "谱号识别" }
        assertEquals(1.0, clef.score, 0.001)
        assertTrue(clef.detail.contains("已识别"))
    }

    @Test
    fun `clef factor penalizes unknown clefs`() {
        val metrics = highQualityMetrics().copy(
            systemsWithKnownClef = 1,
            systemsWithUnknownClef = 1
        )
        val result = RecognitionQualityAssessor.assess(metrics)
        val clef = result.factors.first { it.name == "谱号识别" }
        // knownRatio = 0.5 → 0.3 + 0.7*0.5 = 0.65
        assertEquals(0.65, clef.score, 0.001)
    }

    @Test
    fun `clef factor floor when no system data`() {
        val metrics = RecognitionMetrics(
            systemCount = 1,
            avgLineSpacing = 15,
            lineSpacingCV = 0.0,
            noteheadCount = 5,
            restCount = 0,
            barlineCount = 0,
            systemsWithKnownClef = 0,
            systemsWithUnknownClef = 0, // 无系统数据
            timeSignatureDetected = false,
            tempoDetected = false,
            deskewApplied = false,
            keystoneApplied = false,
            noiseRatio = 0.0,
            totalImagePixels = 10_000L
        )
        val result = RecognitionQualityAssessor.assess(metrics)
        val clef = result.factors.first { it.name == "谱号识别" }
        assertEquals(0.3, clef.score, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  5. 因子 3：符头检测
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `notehead factor zero when no noteheads`() {
        val metrics = highQualityMetrics().copy(noteheadCount = 0)
        val result = RecognitionQualityAssessor.assess(metrics)
        val nh = result.factors.first { it.name == "符头检测" }
        assertEquals(0.0, nh.score, 0.001)
        assertTrue(nh.detail.contains("未检测到音符"))
    }

    @Test
    fun `notehead factor increases with count`() {
        val scores = mutableListOf<Double>()
        for (n in listOf(1, 4, 8, 20)) {
            val m = highQualityMetrics().copy(noteheadCount = n)
            val r = RecognitionQualityAssessor.assess(m)
            scores += r.factors.first { it.name == "符头检测" }.score
        }
        // 得分应单调递增
        for (i in 0 until scores.size - 1) {
            assertTrue("符头越多得分越高 (n 阶梯)", scores[i] <= scores[i + 1])
        }
    }

    @Test
    fun `notehead factor low score for very few notes`() {
        val metrics = highQualityMetrics().copy(noteheadCount = 1)
        val result = RecognitionQualityAssessor.assess(metrics)
        val nh = result.factors.first { it.name == "符头检测" }
        assertEquals(0.25, nh.score, 0.001)
        assertTrue(nh.detail.contains("过少"))
    }

    // ════════════════════════════════════════════════════════════════════════
    //  6. 因子 4：照片质量
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `photo factor full score when no corrections needed`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        val photo = result.factors.first { it.name == "照片质量" }
        assertEquals(1.0, photo.score, 0.001)
        assertTrue(photo.detail.contains("良好"))
    }

    @Test
    fun `photo factor penalizes deskew`() {
        val metrics = highQualityMetrics().copy(deskewApplied = true)
        val result = RecognitionQualityAssessor.assess(metrics)
        val photo = result.factors.first { it.name == "照片质量" }
        // 1.0 - 0.20 = 0.80
        assertEquals(0.80, photo.score, 0.001)
        assertTrue(photo.detail.contains("倾斜校正"))
    }

    @Test
    fun `photo factor penalizes keystone more than deskew`() {
        val metrics = highQualityMetrics().copy(keystoneApplied = true)
        val result = RecognitionQualityAssessor.assess(metrics)
        val photo = result.factors.first { it.name == "照片质量" }
        // 1.0 - 0.30 = 0.70
        assertEquals(0.70, photo.score, 0.001)
        assertTrue(photo.detail.contains("透视校正"))
    }

    @Test
    fun `photo factor penalizes high noise`() {
        val metrics = highQualityMetrics().copy(noiseRatio = 0.06) // 6%
        val result = RecognitionQualityAssessor.assess(metrics)
        val photo = result.factors.first { it.name == "照片质量" }
        // 1.0 - 0.15 = 0.85
        assertEquals(0.85, photo.score, 0.001)
        assertTrue(photo.detail.contains("高噪点"))
    }

    @Test
    fun `photo factor penalizes mild noise`() {
        val metrics = highQualityMetrics().copy(noiseRatio = 0.02) // 2%
        val result = RecognitionQualityAssessor.assess(metrics)
        val photo = result.factors.first { it.name == "照片质量" }
        // 1.0 - 0.08 = 0.92
        assertEquals(0.92, photo.score, 0.001)
        assertTrue(photo.detail.contains("轻微噪点"))
    }

    @Test
    fun `photo factor has minimum floor`() {
        val metrics = highQualityMetrics().copy(
            deskewApplied = true,
            keystoneApplied = true,
            noiseRatio = 0.50 // 50%
        )
        val result = RecognitionQualityAssessor.assess(metrics)
        val photo = result.factors.first { it.name == "照片质量" }
        // 1.0 - 0.20 - 0.30 - 0.15 = 0.35 → coerceIn(0.15, 1.0) = 0.35
        assertEquals(0.35, photo.score, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  7. 因子 5：签名识别（拍号 + 速度）
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `signature factor floor when nothing detected`() {
        val metrics = highQualityMetrics().copy(
            timeSignatureDetected = false,
            tempoDetected = false
        )
        val result = RecognitionQualityAssessor.assess(metrics)
        val sig = result.factors.first { it.name == "签名识别" }
        assertEquals(0.3, sig.score, 0.001)
        assertTrue(sig.detail.contains("默认"))
    }

    @Test
    fun `signature factor partial with only time signature`() {
        val metrics = highQualityMetrics().copy(
            timeSignatureDetected = true,
            tempoDetected = false
        )
        val result = RecognitionQualityAssessor.assess(metrics)
        val sig = result.factors.first { it.name == "签名识别" }
        // 0.3 + 0.4 = 0.7
        assertEquals(0.7, sig.score, 0.001)
    }

    @Test
    fun `signature factor full with time sig and tempo`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        val sig = result.factors.first { it.name == "签名识别" }
        assertEquals(1.0, sig.score, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  8. 因子 6：结构完整性
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `structural factor zero when no noteheads`() {
        val metrics = highQualityMetrics().copy(noteheadCount = 0)
        val result = RecognitionQualityAssessor.assess(metrics)
        val struct = result.factors.first { it.name == "结构完整性" }
        assertEquals(0.0, struct.score, 0.001)
    }

    @Test
    fun `structural factor base with no barlines or rests`() {
        val metrics = highQualityMetrics().copy(barlineCount = 0, restCount = 0)
        val result = RecognitionQualityAssessor.assess(metrics)
        val struct = result.factors.first { it.name == "结构完整性" }
        assertEquals(0.5, struct.score, 0.001)
    }

    @Test
    fun `structural factor rewards barlines and rests`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        val struct = result.factors.first { it.name == "结构完整性" }
        // base 0.5 + barlines 0.25 + rests 0.15 + structure bonus 0.10 = 1.0
        assertEquals(1.0, struct.score, 0.001)
        assertTrue(struct.detail.contains("小节线"))
    }

    @Test
    fun `structural factor dense barlines get bonus`() {
        // 20 音符 / 4 = 5 期望小节线；实际有 6 ≥ 5 → 结构完整加分
        val metrics = highQualityMetrics().copy(barlineCount = 6, restCount = 2)
        val result = RecognitionQualityAssessor.assess(metrics)
        val struct = result.factors.first { it.name == "结构完整性" }
        assertTrue("密集小节线应获结构加分，得分 ${struct.score}", struct.score >= 1.0)
        assertTrue(struct.detail.contains("完整"))
    }

    // ════════════════════════════════════════════════════════════════════════
    //  9. 权重一致性
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `factor weights sum to 1`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        val totalWeight = result.factors.sumOf { it.weight }
        assertEquals(1.0, totalWeight, 0.001)
    }

    @Test
    fun `each factor has expected weight`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        val weightsByName = result.factors.associate { it.name to it.weight }
        assertEquals(0.20, weightsByName["谱线检测质量"]!!, 0.001)
        assertEquals(0.15, weightsByName["谱号识别"]!!, 0.001)
        assertEquals(0.20, weightsByName["符头检测"]!!, 0.001)
        assertEquals(0.15, weightsByName["照片质量"]!!, 0.001)
        assertEquals(0.15, weightsByName["签名识别"]!!, 0.001)
        assertEquals(0.15, weightsByName["结构完整性"]!!, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. 摘要与百分比字符串
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `summary for HIGH level mentions confidence`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        assertTrue(result.summary.contains("高"))
        assertTrue(result.summary.contains("%"))
    }

    @Test
    fun `summary for LOW level mentions manual review`() {
        val metrics = RecognitionMetrics(
            systemCount = 1,
            avgLineSpacing = 15,
            lineSpacingCV = 0.20,
            noteheadCount = 1,
            restCount = 0,
            barlineCount = 0,
            systemsWithKnownClef = 0,
            systemsWithUnknownClef = 1,
            timeSignatureDetected = false,
            tempoDetected = false,
            deskewApplied = true,
            keystoneApplied = true,
            noiseRatio = 0.0,
            totalImagePixels = 10_000L
        )
        val result = RecognitionQualityAssessor.assess(metrics)
        assertTrue("LOW 摘要应提及人工校对: ${result.summary}",
            result.summary.contains("人工校对"))
    }

    @Test
    fun `percent string formats correctly`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        assertTrue("百分比字符串应以 % 结尾: ${result.percentString}",
            result.percentString.endsWith("%"))
        // 应是数字
        val numPart = result.percentString.dropLast(1)
        numPart.toIntOrNull() ?: error("百分比部分不是整数: $numPart")
    }

    @Test
    fun `all factors have non-empty detail`() {
        val result = RecognitionQualityAssessor.assess(highQualityMetrics())
        for (factor in result.factors) {
            assertTrue("因子 ${factor.name} 的 detail 不应为空", factor.detail.isNotEmpty())
            assertTrue("因子 ${factor.name} 的得分应在 [0,1]", factor.score in 0.0..1.0)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 11. ConfidenceLevel 枚举
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `confidence level display names are Chinese`() {
        assertEquals("高", ConfidenceLevel.HIGH.displayName)
        assertEquals("中", ConfidenceLevel.MEDIUM.displayName)
        assertEquals("低", ConfidenceLevel.LOW.displayName)
    }

    @Test
    fun `thresholds are at expected boundaries`() {
        assertEquals(0.75, RecognitionQualityAssessor.HIGH_THRESHOLD, 0.001)
        assertEquals(0.45, RecognitionQualityAssessor.MEDIUM_THRESHOLD, 0.001)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. RecognitionQuality / QualityFactor 数据模型
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `RecognitionQuality percentString matches score`() {
        val q = RecognitionQuality(
            overallScore = 0.85,
            level = ConfidenceLevel.HIGH,
            factors = emptyList(),
            summary = "test"
        )
        assertEquals("85%", q.percentString)
    }

    @Test
    fun `EMPTY metrics has all defaults`() {
        val e = RecognitionMetrics.EMPTY
        assertEquals(0, e.systemCount)
        assertEquals(0, e.noteheadCount)
        assertEquals(0.0, e.lineSpacingCV, 0.001)
        assertEquals(0.0, e.noiseRatio, 0.001)
        assertFalse(e.timeSignatureDetected)
        assertFalse(e.deskewApplied)
    }
}
