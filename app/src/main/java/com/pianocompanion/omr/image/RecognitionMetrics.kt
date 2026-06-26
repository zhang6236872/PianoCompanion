package com.pianocompanion.omr.image

/**
 * 从 OMR 管线各阶段收集的诊断信号，用于评估识别质量。
 *
 * 所有字段均为纯数据（无 Android 依赖），可在 JVM 单元测试中直接构造。
 * [RecognitionQualityAssessor] 消费此结构并输出 [RecognitionQuality] 评估结果。
 *
 * @param systemCount 检测到的谱表系统数量（0 = 完全未检测到谱线）。
 * @param avgLineSpacing 平均谱线间距（像素）。
 * @param lineSpacingCV 谱线间距变异系数（标准差 / 均值）。值越小表示谱表越规整，
 *   识别越可靠；>0.15 表示间距不一致，可能因图像质量差或透视变形残留。
 * @param noteheadCount 检测到的符头总数。
 * @param restCount 检测到的休止符总数。
 * @param barlineCount 检测到的小节线总数（不含 DASHED 虚线）。
 * @param systemsWithKnownClef 谱号被成功识别的系统数。
 * @param systemsWithUnknownClef 谱号未识别（UNKNOWN）的系统数。
 * @param timeSignatureDetected 是否检测到拍号。
 * @param tempoDetected 是否从图像中检测到速度记号（而非使用默认值）。
 * @param deskewApplied 倾斜校正是否被应用（照片不够端正）。
 * @param keystoneApplied 透视变形校正是否被应用（拍摄角度有偏航）。
 * @param noiseRatio 噪声像素比例 = (椒噪声 + 盐噪声) / 总像素数。
 *   >0.01 (1%) 表示照片质量较差。
 * @param totalImagePixels 图像总像素数（用于计算噪声比例的上下文）。
 */
data class RecognitionMetrics(
    val systemCount: Int,
    val avgLineSpacing: Int,
    val lineSpacingCV: Double,
    val noteheadCount: Int,
    val restCount: Int,
    val barlineCount: Int,
    val systemsWithKnownClef: Int,
    val systemsWithUnknownClef: Int,
    val timeSignatureDetected: Boolean,
    val tempoDetected: Boolean,
    val deskewApplied: Boolean,
    val keystoneApplied: Boolean,
    val noiseRatio: Double,
    val totalImagePixels: Long
) {
    companion object {
        /** 空指标——所有信号为零/默认值，用于管线早期失败时的占位。 */
        val EMPTY = RecognitionMetrics(
            systemCount = 0,
            avgLineSpacing = 0,
            lineSpacingCV = 0.0,
            noteheadCount = 0,
            restCount = 0,
            barlineCount = 0,
            systemsWithKnownClef = 0,
            systemsWithUnknownClef = 0,
            timeSignatureDetected = false,
            tempoDetected = false,
            deskewApplied = false,
            keystoneApplied = false,
            noiseRatio = 0.0,
            totalImagePixels = 0L
        )
    }
}

/**
 * OMR 识别质量的综合评估结果。
 *
 * @param overallScore 总体置信度（0.0~1.0），由各因子加权平均得出。
 * @param level 置信度等级（[ConfidenceLevel]）。
 * @param factors 各评估因子的明细列表，供 UI 展示给用户。
 * @param summary 人类可读的总体评估摘要（一句话）。
 */
data class RecognitionQuality(
    val overallScore: Double,
    val level: ConfidenceLevel,
    val factors: List<QualityFactor>,
    val summary: String
) {
    /** 置信度百分比字符串（如 "85%"）。 */
    val percentString: String get() = "${(overallScore * 100).toInt()}%"
}

/**
 * 置信度等级。
 * - [HIGH]：≥75%，识别结果可信度高，用户可放心使用。
 * - [MEDIUM]：45%~75%，识别结果基本可用但建议核对关键内容。
 * - [LOW]：<45%，识别结果可靠性低，强烈建议人工校对。
 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    /** 中文显示名称。 */
    val displayName: String get() = when (this) {
        HIGH -> "高"
        MEDIUM -> "中"
        LOW -> "低"
    }
}

/**
 * 单项质量评估因子。
 *
 * @param name 因子名称（如"谱线检测质量"）。
 * @param score 该因子得分（0.0~1.0）。
 * @param weight 该因子在总体评分中的权重。
 * @param detail 人类可读的评估细节（如"5 个谱表系统，间距一致"）。
 */
data class QualityFactor(
    val name: String,
    val score: Double,
    val weight: Double,
    val detail: String
)
