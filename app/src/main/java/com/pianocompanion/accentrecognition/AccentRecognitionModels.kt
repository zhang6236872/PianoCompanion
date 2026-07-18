package com.pianocompanion.accentrecognition

/**
 * 强拍 / 重音辨识训练（Accent / Strong-Beat Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **强拍辨识（Accent Recognition）**：用户听到一段等间隔的节拍（click）序列，其中
 *   **某一拍是强拍（重音）**——更响、更亮。用户需要判断这个重音落在小节的**第几拍**。
 * - 这是节奏训练中最基础的能力之一：**找到「1」在哪里**（locating the downbeat / strong beat）。
 *   无论拍号如何，演奏者都必须能感知到哪个拍位是重音所在的强拍。
 *
 * 与已有 [com.pianocompanion.meterrecognition]（拍号听辨）的区别：
 * - **拍号听辨**：听重音的**分组周期**，判断每小节有几拍（2/4 / 3/4 / 4/4 …）—— 答案是拍号。
 * - **强拍辨识**：已知每小节 N 拍，判断重音具体落在**第几拍**（第 1 / 2 / 3 / 4 … 拍）—— 答案是位置。
 *
 * 训练流程：
 * 1. 播放一段由若干 click 组成的小节，小节重复数次；其中某一拍是强拍（重音 click）。
 * 2. 用户聆听后，判断重音在第几拍。
 * 3. 从「第 1 拍 … 第 N 拍」选项中选出正确答案。
 */

/**
 * 强拍突出程度（重音与普通拍之间的对比强度）。
 *
 * 难度越高，重音与普通拍的差异越小，越难辨识。
 *
 * @param accentFrequency 强拍 click 频率（Hz）
 * @param accentAmplitude 强拍 click 振幅（0.0-1.0）
 * @param baseFrequency 普通拍 click 频率（Hz）
 * @param baseAmplitude 普通拍 click 振幅（0.0-1.0）
 * @param displayName 显示名
 */
enum class AccentStrength(
    val accentFrequency: Double,
    val accentAmplitude: Float,
    val baseFrequency: Double,
    val baseAmplitude: Float,
    val displayName: String
) {
    /** 鲜明重音：强拍高音+大音量，普通拍低音+小音量，差异明显。 */
    STRONG(
        accentFrequency = 920.0,
        accentAmplitude = 0.85f,
        baseFrequency = 600.0,
        baseAmplitude = 0.30f,
        displayName = "鲜明重音"
    ),

    /** 适中重音：差异适中，需要专注分辨。 */
    MEDIUM(
        accentFrequency = 820.0,
        accentAmplitude = 0.72f,
        baseFrequency = 600.0,
        baseAmplitude = 0.38f,
        displayName = "适中重音"
    ),

    /** 微妙重音：重音仅略响、略高，贴近真实演奏中的细微重音，最难辨识。 */
    SUBTLE(
        accentFrequency = 740.0,
        accentAmplitude = 0.64f,
        baseFrequency = 600.0,
        baseAmplitude = 0.46f,
        displayName = "微妙重音"
    )
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param beatsPerMeasureOptions 本难度可能的小节拍数集合（每题从中随机一种）
 * @param strength 本难度的重音突出程度
 * @param tempoIntervalMs 相邻拍之间的时间间隔（毫秒），越快越难追踪
 * @param measureRepeat 小节重复播放次数
 */
enum class AccentDifficulty(
    val displayName: String,
    val description: String,
    val beatsPerMeasureOptions: List<Int>,
    val strength: AccentStrength,
    val tempoIntervalMs: Double,
    val measureRepeat: Int
) {
    BEGINNER(
        displayName = "初级",
        description = "4 拍小节 · 鲜明重音 · 4 选项 · 慢速",
        beatsPerMeasureOptions = listOf(4),
        strength = AccentStrength.STRONG,
        tempoIntervalMs = 500.0,
        measureRepeat = 3
    ),

    INTERMEDIATE(
        displayName = "中级",
        description = "3 / 4 拍小节 · 适中重音 · 中速",
        beatsPerMeasureOptions = listOf(3, 4),
        strength = AccentStrength.MEDIUM,
        tempoIntervalMs = 400.0,
        measureRepeat = 3
    ),

    ADVANCED(
        displayName = "高级",
        description = "2 / 3 / 4 / 5 拍小节 · 微妙重音 · 快速",
        beatsPerMeasureOptions = listOf(2, 3, 4, 5),
        strength = AccentStrength.SUBTLE,
        tempoIntervalMs = 330.0,
        measureRepeat = 3
    );

    companion object {
        val ALL: List<AccentDifficulty> = entries.toList()
    }
}

/**
 * 强拍辨识训练题目。
 *
 * @param difficulty 难度
 * @param beatsPerMeasure 本题每小节拍数 N
 * @param accentPosition 强拍所在拍位（1..N，从 1 开始计数）
 * @param strength 重音突出程度
 * @param beatIntervalMs 相邻拍间隔（毫秒）
 * @param measureRepeat 小节重复次数
 * @param answerChoices 所有选项（「第 1 拍」…「第 N 拍」，按顺序排列，含正确答案）
 * @param correctAnswer 正确答案文本（「第 X 拍」）
 */
data class AccentQuestion(
    val difficulty: AccentDifficulty,
    val beatsPerMeasure: Int,
    val accentPosition: Int,
    val strength: AccentStrength,
    val beatIntervalMs: Double,
    val measureRepeat: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 总 click 次数（拍数 × 重复次数）。 */
    val totalClicks: Int get() = beatsPerMeasure * measureRepeat

    /** 拍位序号文本（如「第 3 拍」）。 */
    val positionLabel: String get() = "第 $accentPosition 拍"

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${beatsPerMeasure}拍小节 · 强拍在$positionLabel · ${strength.displayName}"

    init {
        require(beatsPerMeasure in 2..8) {
            "每小节拍数 $beatsPerMeasure 超出范围 [2, 8]"
        }
        require(accentPosition in 1..beatsPerMeasure) {
            "强拍位置 $accentPosition 超出范围 [1, $beatsPerMeasure]"
        }
        require(beatIntervalMs > 0) { "拍间隔必须为正数" }
        require(measureRepeat in 1..10) {
            "小节重复次数 $measureRepeat 超出范围 [1, 10]"
        }
        require(answerChoices.isNotEmpty()) { "选项不能为空" }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(beatsPerMeasure in difficulty.beatsPerMeasureOptions) {
            "拍数 $beatsPerMeasure 不在 ${difficulty.displayName} 的候选拍数集合中"
        }
    }
}

/**
 * 一次答题结果。
 */
data class AccentAnswerRecord(
    val question: AccentQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
