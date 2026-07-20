package com.pianocompanion.harmonycolor

/**
 * 和声色彩听辨训练（Harmony Color Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和声色彩（Harmony Color）**：不同三和弦品质带给人的「听觉色彩 / 情感印象」。
 *   大三和弦明亮稳定、小三和弦暗淡柔和、减三和弦紧张悬疑、增三和弦悬浮未决。
 *   这是辨认具体和弦类型（见 [com.pianocompanion.chordtraining]）之前的**前置感知能力**——
 *   先学会分辨和声的「色彩家族」，再学习精确和弦种类。
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.chordtraining] 问「具体是什么和弦」（大三/大七/属七/转位…）
 * - [com.pianocompanion.consonancetraining] 问「协和 / 不协和程度」（连续谱判断）
 * - 本模块问「这组和弦属于哪种**色彩家族**」——4 种三和弦品质的宏观情感色彩
 *
 * 4 种和声色彩（辨识目标）：
 * - **大调色彩（Major, 大三）**：根音 + 大三度(4 半音) + 纯五度(7 半音) —— 明亮、稳定、开放、愉快
 * - **小调色彩（Minor, 小三）**：根音 + 小三度(3 半音) + 纯五度(7 半音) —— 暗淡、柔和、内敛、忧伤
 * - **减三色彩（Diminished, 减三）**：根音 + 小三度(3 半音) + 减五度(6 半音) —— 紧张、悬疑、不安、收缩
 * - **增三色彩（Augmented, 增三）**：根音 + 大三度(4 半音) + 增五度(8 半音) —— 悬浮、梦幻、未决、扩张
 *
 * 训练流程：
 * 1. 播放一个同时鸣响的三和弦（柱式和弦 block chord）
 * 2. 用户聆听其整体「色彩 / 印象」
 * 3. 从选项中选出正确的色彩家族
 */

/**
 * 和声色彩（三和弦品质家族，辨识目标）。
 *
 * @param symbol 记号符号（如「大三」「小三」「减三」「增三」）
 * @param displayName 中文名（如「大调色彩」）
 * @param englishName 英文名（如「Major」）
 * @param colorWord 色彩印象词（如「明亮」「暗淡」「紧张」「悬浮」）
 * @param intervals 相对根音的半音偏移（根音 + 三音 + 五音）
 * @param description 详细描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class HarmonyColor(
    val symbol: String,
    val displayName: String,
    val englishName: String,
    val colorWord: String,
    val intervals: IntArray,
    val description: String,
    val hint: String
) {
    MAJOR(
        symbol = "大三",
        displayName = "大调色彩",
        englishName = "Major",
        colorWord = "明亮",
        intervals = intArrayOf(0, 4, 7),
        description = "大三和弦（Major, 大三）：根音 + 大三度(4 半音) + 纯五度(7 半音)。" +
            "听感明亮、稳定、开放、愉快，是最「正」、最协和的三和弦色彩，" +
            "几乎所有大调音乐都以它为归宿。",
        hint = "明亮、开阔、像晴天——这是最稳定、最「正」的色彩"
    ),
    MINOR(
        symbol = "小三",
        displayName = "小调色彩",
        englishName = "Minor",
        colorWord = "暗淡",
        intervals = intArrayOf(0, 3, 7),
        description = "小三和弦（Minor, 小三）：根音 + 小三度(3 半音) + 纯五度(7 半音)。" +
            "听感暗淡、柔和、内敛、略带忧伤，只比大三和弦的中音低半音，" +
            "色彩立刻从明亮转为阴郁。",
        hint = "暗淡、柔和、像阴天——和大三只差中音低半音，色彩立刻变忧郁"
    ),
    DIMINISHED(
        symbol = "减三",
        displayName = "减三色彩",
        englishName = "Diminished",
        colorWord = "紧张",
        intervals = intArrayOf(0, 3, 6),
        description = "减三和弦（Diminished, 减三）：根音 + 小三度(3 半音) + 减五度(6 半音)。" +
            "三音与五音都向内收缩，听感紧张、悬疑、不安，像电影里的惊悚时刻，" +
            "亟需解决到协和和弦。",
        hint = "紧张、收缩、像悬疑片——两组半音向内挤压，听感不安"
    ),
    AUGMENTED(
        symbol = "增三",
        displayName = "增三色彩",
        englishName = "Augmented",
        colorWord = "悬浮",
        intervals = intArrayOf(0, 4, 8),
        description = "增三和弦（Augmented, 增三）：根音 + 大三度(4 半音) + 增五度(8 半音)。" +
            "五音向外扩张半音，三音之间均为大三度（完全对称），听感悬浮、梦幻、" +
            "无明确归属，仿佛飘在空中无法落地。",
        hint = "悬浮、梦幻、像失重——五度被撑开，声音飘着落不下来"
    );

    /** 完整标签（如「大调色彩（Major）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 带符号的标签（如「大三 大调色彩」）。 */
    val symbolLabel: String get() = "$symbol $displayName"

    companion object {
        /** 全部色彩。 */
        val ALL: List<HarmonyColor> = entries.toList()

        /** 初级难度候选：大调 vs 小调（最根本的明/暗二元色彩对比）。 */
        val BEGINNER_COLORS: List<HarmonyColor> = listOf(MAJOR, MINOR)

        /** 中级难度候选：大/小/减（增加紧张的减三色彩）。 */
        val INTERMEDIATE_COLORS: List<HarmonyColor> = listOf(MAJOR, MINOR, DIMINISHED)

        /** 高级难度候选：全部 4 种色彩（含悬浮的增三）。 */
        val ADVANCED_COLORS: List<HarmonyColor> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param colors 该难度可用的色彩候选集
 * @param choiceCount 该难度的选项数量
 */
enum class HarmonyColorDifficulty(
    val displayName: String,
    val description: String,
    val colors: List<HarmonyColor>,
    val choiceCount: Int
) {
    BEGINNER(
        "初级",
        "大调 vs 小调 · 2 选项 · 最基础的明暗色彩",
        HarmonyColor.BEGINNER_COLORS,
        2
    ),
    INTERMEDIATE(
        "中级",
        "+ 减三 · 3 选项 · 增加紧张色彩",
        HarmonyColor.INTERMEDIATE_COLORS,
        3
    ),
    ADVANCED(
        "高级",
        "+ 增三 · 4 选项 · 全部 4 种色彩",
        HarmonyColor.ADVANCED_COLORS,
        4
    );

    companion object {
        val ALL: List<HarmonyColorDifficulty> = entries.toList()
    }
}

/**
 * 和声色彩听辨训练题目。
 *
 * @param color 正确的和声色彩
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param rootMidi 根音的 MIDI 音高
 * @param voicing 和弦的 MIDI 音高序列（根音 + intervals）
 * @param answerChoices 所有选项（色彩完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class HarmonyColorQuestion(
    val color: HarmonyColor,
    val difficulty: HarmonyColorDifficulty,
    val seed: Long,
    val rootMidi: Int,
    val voicing: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${color.displayName}（${color.englishName} ${color.symbol}）· ${color.colorWord}"

    init {
        require(rootMidi in MIN_ROOT_MIDI..MAX_ROOT_MIDI) {
            "根音 MIDI $rootMidi 超出范围 [$MIN_ROOT_MIDI, $MAX_ROOT_MIDI]"
        }
        require(color in difficulty.colors) {
            "和声色彩 ${color.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
        require(correctAnswer in answerChoices) {
            "正确答案不在选项中"
        }
        require(voicing.isNotEmpty()) {
            "和弦音序列不能为空"
        }
        require(voicing.all { it in 0..127 }) {
            "和弦音 $voicing 含超出 [0,127] 的 MIDI 值"
        }
    }
}

/**
 * 一次答题结果。
 */
data class HarmonyColorAnswerRecord(
    val question: HarmonyColorQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 根音 MIDI 有效范围常量（全 MIDI 音域，0-127）。 */
const val MIN_ROOT_MIDI: Int = 0
const val MAX_ROOT_MIDI: Int = 127
