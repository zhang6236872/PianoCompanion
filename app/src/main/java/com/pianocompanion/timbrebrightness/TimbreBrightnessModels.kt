package com.pianocompanion.timbrebrightness

/**
 * 音色亮度辨识训练（Timbre Brightness Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **音色亮度（Timbre Brightness / Harmonic Richness）**：同一基频下，泛音（谐波）的数量与强度
 *   决定了声音的「明亮度」。泛音越多、越强，声音越「亮」、越「尖锐」；泛音越少，声音越「暗」、
 *   越「柔和」。这是理解不同乐器音色差异的核心感知能力。
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.timbretraining] 问「这是哪种乐器」（按乐器辨识）
 * - 本模块问「这个声音有多亮 / 泛音有多丰富」——将泛音数量作为**唯一变量**进行感知训练
 *
 * 4 种音色亮度（辨识目标）：
 * - **纯净（Pure）**：仅基频（纯正弦波）—— 暗淡、沉闷、像音叉
 * - **柔和（Mellow）**：基频 + 2 个弱泛音 —— 温暖、像长笛
 * - **明亮（Bright）**：基频 + 5 个中等泛音 —— 典型乐器、像钢琴
 * - **辉煌（Brilliant）**：基频 + 10 个强泛音 —— 尖锐、辉煌、像小号
 *
 * 训练流程：
 * 1. 播放一个固定音高的单音（渲染两遍）
 * 2. 用户聆听其音色「明亮度 / 泛音丰富度」
 * 3. 从选项中选出正确的亮度等级
 */

/**
 * 音色亮度等级（辨识目标）。
 *
 * @param symbol 符号
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param brightnessWord 亮度印象词
 * @param harmonicCount 泛音个数（不含基频）
 * @param harmonicStrength 泛音强度衰减系数（每高一个泛音，幅度乘以此系数）
 * @param description 详细描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class TimbreBrightness(
    val symbol: String,
    val displayName: String,
    val englishName: String,
    val brightnessWord: String,
    val harmonicCount: Int,
    val harmonicStrength: Double,
    val description: String,
    val hint: String
) {
    PURE(
        symbol = "⚪",
        displayName = "纯净",
        englishName = "Pure",
        brightnessWord = "暗淡",
        harmonicCount = 0,
        harmonicStrength = 0.0,
        description = "纯净音色（Pure）：仅基频（纯正弦波），无任何泛音。听感暗淡、沉闷、" +
            "单调，像电子音叉或旧式电话铃声。所有能量集中在基频，缺乏色彩与层次。",
        hint = "暗淡、沉闷、像音叉——只有最基础的「嗡嗡」声，没有任何色彩"
    ),
    MELLOW(
        symbol = "◐",
        displayName = "柔和",
        englishName = "Mellow",
        brightnessWord = "温暖",
        harmonicCount = 2,
        harmonicStrength = 0.5,
        description = "柔和音色（Mellow）：基频 + 2 个弱泛音（2f / 3f）。听感温暖、圆润、柔和，" +
            "像长笛或木管乐器。少量泛音增添了一丝色彩，但整体仍然柔和内敛。",
        hint = "温暖、圆润、像长笛——有一点色彩但整体柔和"
    ),
    BRIGHT(
        symbol = "◑",
        displayName = "明亮",
        englishName = "Bright",
        brightnessWord = "清晰",
        harmonicCount = 5,
        harmonicStrength = 0.6,
        description = "明亮音色（Bright）：基频 + 5 个中等泛音（2f ~ 6f）。听感清晰、丰满、典型，" +
            "像钢琴或吉他。泛音层次丰富，色彩鲜明但不刺耳。",
        hint = "清晰、丰满、像钢琴——泛音丰富但不过分尖锐"
    ),
    BRILLIANT(
        symbol = "●",
        displayName = "辉煌",
        englishName = "Brilliant",
        brightnessWord = "尖锐",
        harmonicCount = 10,
        harmonicStrength = 0.75,
        description = "辉煌音色（Brilliant）：基频 + 10 个强泛音（2f ~ 11f）。听感尖锐、辉煌、" +
            "穿透力强，像小号、小提琴高把位或铙钹。高频泛音极强，声音明亮到刺耳。",
        hint = "尖锐、辉煌、像小号——高频泛音极强，穿透力十足"
    );

    /** 完整标签（如「明亮（Bright）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 带符号的标签（如「◑ 明亮」）。 */
    val symbolLabel: String get() = "$symbol $displayName"

    companion object {
        /** 全部亮度等级（按暗→亮排列）。 */
        val ALL: List<TimbreBrightness> = listOf(PURE, MELLOW, BRIGHT, BRILLIANT)

        /** 初级难度候选：纯净 vs 辉煌（最极端的暗/亮对比）。 */
        val BEGINNER_LEVELS: List<TimbreBrightness> = listOf(PURE, BRILLIANT)

        /** 中级难度候选：纯净 / 明亮 / 辉煌。 */
        val INTERMEDIATE_LEVELS: List<TimbreBrightness> = listOf(PURE, BRIGHT, BRILLIANT)

        /** 高级难度候选：全部 4 个亮度等级。 */
        val ADVANCED_LEVELS: List<TimbreBrightness> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param levels 该难度可用的亮度候选集
 * @param choiceCount 该难度的选项数量
 */
enum class TimbreBrightnessDifficulty(
    val displayName: String,
    val description: String,
    val levels: List<TimbreBrightness>,
    val choiceCount: Int
) {
    BEGINNER(
        "初级",
        "纯净 vs 辉煌 · 2 选项 · 最极端的暗亮对比",
        TimbreBrightness.BEGINNER_LEVELS,
        2
    ),
    INTERMEDIATE(
        "中级",
        "+ 明亮 · 3 选项 · 增加中间过渡",
        TimbreBrightness.INTERMEDIATE_LEVELS,
        3
    ),
    ADVANCED(
        "高级",
        "+ 柔和 · 4 选项 · 全部 4 个亮度等级",
        TimbreBrightness.ADVANCED_LEVELS,
        4
    );

    companion object {
        val ALL: List<TimbreBrightnessDifficulty> = entries.toList()
    }
}

/**
 * 音色亮度辨识训练题目。
 *
 * @param brightness 正确的亮度等级
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param fundamentalMidi 基频的 MIDI 音高
 * @param answerChoices 所有选项（亮度完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class TimbreBrightnessQuestion(
    val brightness: TimbreBrightness,
    val difficulty: TimbreBrightnessDifficulty,
    val seed: Long,
    val fundamentalMidi: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${brightness.displayName}（${brightness.englishName} ${brightness.symbol}）· ${brightness.brightnessWord}"

    init {
        require(fundamentalMidi in MIN_FUNDAMENTAL_MIDI..MAX_FUNDAMENTAL_MIDI) {
            "基频 MIDI $fundamentalMidi 超出范围 [$MIN_FUNDAMENTAL_MIDI, $MAX_FUNDAMENTAL_MIDI]"
        }
        require(brightness in difficulty.levels) {
            "亮度等级 ${brightness.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
        require(correctAnswer in answerChoices) {
            "正确答案不在选项中"
        }
    }
}

/**
 * 一次答题结果。
 */
data class TimbreBrightnessAnswerRecord(
    val question: TimbreBrightnessQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 基频 MIDI 有效范围常量。 */
const val MIN_FUNDAMENTAL_MIDI: Int = 48   // C3
const val MAX_FUNDAMENTAL_MIDI: Int = 72   // C5
