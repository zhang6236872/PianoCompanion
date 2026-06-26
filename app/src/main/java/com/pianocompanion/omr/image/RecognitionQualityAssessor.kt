package com.pianocompanion.omr.image

import kotlin.math.ln
import kotlin.math.min

/**
 * OMR 识别质量评估器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 消费 [RecognitionMetrics]（从管线各阶段收集的诊断信号），输出 [RecognitionQuality]
 * （总体置信度 + 等级 + 各因子明细）。
 *
 * ## 评估维度（6 个因子，权重合计 1.0）
 *
 * | 因子 | 权重 | 评估内容 |
 * |------|------|----------|
 * | 谱线检测质量 | 0.20 | 系统数量 + 间距一致性 |
 * | 谱号识别 | 0.15 | 已识别 vs UNKNOWN 谱号比例 |
 * | 符头检测 | 0.20 | 符头数量是否合理 |
 * | 照片质量 | 0.15 | 是否需要 deskew/keystone/降噪 |
 * | 签名识别 | 0.15 | 拍号 + 速度记号是否检测到 |
 * | 结构完整性 | 0.15 | 小节线/休止符/节奏结构 |
 *
 * 总体置信度 = Σ(score × weight) / Σ(weight)
 *
 * ## 等级划分
 * - HIGH: ≥0.75 — 识别结果可信，用户可放心使用
 * - MEDIUM: 0.45~0.75 — 基本可用，建议核对关键内容
 * - LOW: <0.45 — 可靠性低，强烈建议人工校对
 */
object RecognitionQualityAssessor {

    /** HIGH 等级的下界。 */
    const val HIGH_THRESHOLD = 0.75

    /** MEDIUM 等级的下界（低于此值为 LOW）。 */
    const val MEDIUM_THRESHOLD = 0.45

    /**
     * 评估识别质量。
     *
     * @param metrics 从管线收集的诊断信号。
     * @return 综合质量评估结果。
     */
    fun assess(metrics: RecognitionMetrics): RecognitionQuality {
        // 管线早期失败（无谱表）——直接返回最低质量。
        if (metrics.systemCount == 0) {
            return RecognitionQuality(
                overallScore = 0.0,
                level = ConfidenceLevel.LOW,
                factors = listOf(
                    QualityFactor(
                        name = "谱线检测质量",
                        score = 0.0,
                        weight = 1.0,
                        detail = "未检测到任何五线谱系统，请拍摄清晰、端正的乐谱"
                    )
                ),
                summary = "识别失败：未检测到五线谱"
            )
        }

        val factors = listOf(
            assessStaffDetection(metrics),
            assessClefRecognition(metrics),
            assessNoteheadCount(metrics),
            assessPhotoQuality(metrics),
            assessSignatureRecognition(metrics),
            assessStructuralCompleteness(metrics)
        )

        val totalWeight = factors.sumOf { it.weight }
        val overall = if (totalWeight > 0) {
            factors.sumOf { it.score * it.weight } / totalWeight
        } else {
            0.0
        }.coerceIn(0.0, 1.0)

        val level = levelFor(overall)
        val summary = buildSummary(overall, level, metrics)

        return RecognitionQuality(overall, level, factors, summary)
    }

    // ── 因子 1：谱线检测质量（权重 0.20）─────────────────────────────────

    private fun assessStaffDetection(m: RecognitionMetrics): QualityFactor {
        val score: Double
        val detail: String

        if (m.systemCount == 0) {
            score = 0.0
            detail = "未检测到五线谱系统"
        } else {
            // 基础分：检测到至少一个系统即得 0.6
            val base = 0.6
            // 间距一致性奖励：变异系数(CV)越小越好
            // CV ≤ 0.05 → +0.4（非常一致）
            // CV ≤ 0.10 → +0.3
            // CV ≤ 0.15 → +0.2
            // CV > 0.15 → +0.1
            val consistencyBonus = when {
                m.lineSpacingCV <= 0.05 -> 0.4
                m.lineSpacingCV <= 0.10 -> 0.3
                m.lineSpacingCV <= 0.15 -> 0.2
                else -> 0.1
            }
            score = (base + consistencyBonus).coerceIn(0.0, 1.0)

            val spacingDesc = if (m.lineSpacingCV <= 0.10) "间距一致" else "间距略有不均"
            detail = "${m.systemCount} 个谱表系统，平均间距 ${m.avgLineSpacing}px，$spacingDesc"
        }

        return QualityFactor("谱线检测质量", score, 0.20, detail)
    }

    // ── 因子 2：谱号识别（权重 0.15）─────────────────────────────────────

    private fun assessClefRecognition(m: RecognitionMetrics): QualityFactor {
        val totalSystems = m.systemsWithKnownClef + m.systemsWithUnknownClef
        val score: Double
        val detail: String

        if (totalSystems == 0) {
            score = 0.3
            detail = "无法评估谱号（无系统数据）"
        } else {
            val knownRatio = m.systemsWithKnownClef.toDouble() / totalSystems
            // UNKNOWN 谱号时管线回退到竖直位置启发式（高音/低音），
            // 这对大多数乐谱正确但不保证。因此 UNKNOWN 给 0.3 而非 0.0。
            score = (0.3 + 0.7 * knownRatio).coerceIn(0.0, 1.0)

            detail = when {
                m.systemsWithUnknownClef == 0 ->
                    "全部 $totalSystems 个系统谱号已识别"
                m.systemsWithKnownClef == 0 ->
                    "$totalSystems 个系统谱号未识别（使用竖直位置推断，音高可能偏差）"
                else ->
                    "${m.systemsWithKnownClef}/$totalSystems 个系统谱号已识别，" +
                        "${m.systemsWithUnknownClef} 个未识别"
            }
        }

        return QualityFactor("谱号识别", score, 0.15, detail)
    }

    // ── 因子 3：符头检测（权重 0.20）─────────────────────────────────────

    private fun assessNoteheadCount(m: RecognitionMetrics): QualityFactor {
        val n = m.noteheadCount
        val score: Double
        val detail: String

        score = when {
            n == 0 -> 0.0
            n <= 2 -> 0.25
            n <= 5 -> 0.55
            n <= 10 -> 0.75
            else -> {
                // 对数缩放：11→0.85, 30→0.93, 100→0.98
                val logScore = 0.85 + 0.15 * min(1.0, ln(n.toDouble()) / ln(100.0))
                logScore.coerceIn(0.0, 1.0)
            }
        }

        detail = when {
            n == 0 -> "未检测到音符"
            n <= 2 -> "仅检测到 $n 个音符（过少，可能遗漏）"
            n <= 5 -> "检测到 $n 个音符"
            n <= 10 -> "检测到 $n 个音符"
            else -> "检测到 $n 个音符"
        }

        return QualityFactor("符头检测", score, 0.20, detail)
    }

    // ── 因子 4：照片质量（权重 0.15）─────────────────────────────────────

    private fun assessPhotoQuality(m: RecognitionMetrics): QualityFactor {
        var score = 1.0
        val issues = ArrayList<String>()

        if (m.deskewApplied) {
            score -= 0.20
            issues.add("倾斜校正")
        }
        if (m.keystoneApplied) {
            score -= 0.30
            issues.add("透视校正")
        }
        // 噪声比例 >1% 扣分
        val noisePct = m.noiseRatio * 100
        if (noisePct > 5.0) {
            score -= 0.15
            issues.add("高噪点(%.1f%%)".format(noisePct))
        } else if (noisePct > 1.0) {
            score -= 0.08
            issues.add("轻微噪点(%.1f%%)".format(noisePct))
        }

        score = score.coerceIn(0.15, 1.0)

        val detail = if (issues.isEmpty()) {
            "照片质量良好，无需预处理校正"
        } else {
            "已应用${issues.joinToString("、")}（原始照片质量一般）"
        }

        return QualityFactor("照片质量", score, 0.15, detail)
    }

    // ── 因子 5：签名识别（权重 0.15）─────────────────────────────────────

    private fun assessSignatureRecognition(m: RecognitionMetrics): QualityFactor {
        var score = 0.3
        val found = ArrayList<String>()

        if (m.timeSignatureDetected) {
            score += 0.4
            found.add("拍号")
        }
        if (m.tempoDetected) {
            score += 0.3
            found.add("速度记号")
        }

        score = score.coerceIn(0.0, 1.0)

        val detail = if (found.isEmpty()) {
            "未检测到拍号/速度记号（使用默认 4/4 拍、120 BPM）"
        } else {
            "已识别${found.joinToString("、")}"
        }

        return QualityFactor("签名识别", score, 0.15, detail)
    }

    // ── 因子 6：结构完整性（权重 0.15）───────────────────────────────────

    private fun assessStructuralCompleteness(m: RecognitionMetrics): QualityFactor {
        if (m.noteheadCount == 0) {
            return QualityFactor("结构完整性", 0.0, 0.15, "无音符，无法评估节奏结构")
        }

        var score = 0.5
        val components = ArrayList<String>()

        if (m.barlineCount > 0) {
            score += 0.25
            components.add("${m.barlineCount} 条小节线")
        }
        if (m.restCount > 0) {
            score += 0.15
            components.add("${m.restCount} 个休止符")
        }
        // 小节线密度合理（大约每 4 个音符一条小节线）时额外加分
        val expectedBarlines = m.noteheadCount / 4
        if (m.barlineCount > 0 && m.barlineCount >= expectedBarlines.coerceAtLeast(1)) {
            score += 0.10
            components.add("小节结构完整")
        }

        score = score.coerceIn(0.0, 1.0)

        val detail = if (components.isEmpty()) {
            "无小节线/休止符（节奏结构不完整）"
        } else {
            components.joinToString("、")
        }

        return QualityFactor("结构完整性", score, 0.15, detail)
    }

    // ── 辅助函数 ─────────────────────────────────────────────────────────

    private fun levelFor(score: Double): ConfidenceLevel = when {
        score >= HIGH_THRESHOLD -> ConfidenceLevel.HIGH
        score >= MEDIUM_THRESHOLD -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }

    private fun buildSummary(
        overall: Double,
        level: ConfidenceLevel,
        m: RecognitionMetrics
    ): String {
        val pct = "${(overall * 100).toInt()}%"
        return when (level) {
            ConfidenceLevel.HIGH ->
                "识别置信度 $pct（高）— 结果可信，可直接用于练习"
            ConfidenceLevel.MEDIUM ->
                "识别置信度 $pct（中）— 结果基本可用，建议核对音高和节奏"
            ConfidenceLevel.LOW ->
                "识别置信度 $pct（低）— 结果可靠性有限，强烈建议人工校对" +
                    if (m.noteheadCount == 0) "" else "（${m.noteheadCount} 个音符）"
        }
    }
}
