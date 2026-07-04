package com.pianocompanion.rhythmreading

/**
 * 节奏视读训练（Rhythm Reading Trainer）数据模型。
 *
 * 本文件包含所有与节奏型识别训练相关的数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **节奏型（Rhythm Pattern）**：一条由若干音符/休止符按特定时值排列的节奏序列，
 *   总时值固定为 4 拍（4/4 拍号下的一个小节）。用户需从 4 个节奏型选项中找出与
 *   题目显示的节奏型完全一致的那个。
 * - **音符时值（RhythmDuration）**：以四分音符 = 1 拍为基准，记录每种音符/休止符
 *   的拍数、显示名称、符头是否实心、是否有符干、符尾层数等渲染属性。
 * - **视读训练目标**：训练用户快速、准确地辨认音符时值组合，建立节奏视觉认知。
 *   与已有的听觉节奏训练（[com.pianocompanion.rhythm.RhythmTrainer]）互补——
 *   后者是「听节奏→敲击」，本模块是「看节奏→辨认」。
 */

/**
 * 音符/休止符时值。
 *
 * @param beats 以四分音符为 1 拍的拍数（如八分 = 0.5）
 * @param displayName 中文显示名称（如 "四分音符"）
 * @param shortLabel 选项标签简称（如 "四"、"八"、"十六"、"休"、"半休"）
 * @param isRest 是否为休止符
 * @param isFilled 符头是否实心（全/二分 = 空心，四分及更短 = 实心）
 * @param hasStem 是否有符干（全音符无符干）
 * @param flagCount 符尾/横梁层数（四分 = 0、八分 = 1、十六分 = 2）
 */
enum class RhythmDuration(
    val beats: Double,
    val displayName: String,
    val shortLabel: String,
    val isRest: Boolean,
    val isFilled: Boolean,
    val hasStem: Boolean,
    val flagCount: Int
) {
    WHOLE(4.0, "全音符", "全", isRest = false, isFilled = false, hasStem = false, flagCount = 0),
    HALF(2.0, "二分音符", "二", isRest = false, isFilled = false, hasStem = true, flagCount = 0),
    QUARTER(1.0, "四分音符", "四", isRest = false, isFilled = true, hasStem = true, flagCount = 0),
    EIGHTH(0.5, "八分音符", "八", isRest = false, isFilled = true, hasStem = true, flagCount = 1),
    SIXTEENTH(0.25, "十六分音符", "十六", isRest = false, isFilled = true, hasStem = true, flagCount = 2),
    QUARTER_REST(1.0, "四分休止符", "休", isRest = true, isFilled = false, hasStem = false, flagCount = 0),
    EIGHTH_REST(0.5, "八分休止符", "半休", isRest = true, isFilled = false, hasStem = false, flagCount = 0);

    /** 是否为可加横梁的音符（八分/十六分）。 */
    val isBeamed: Boolean get() = !isRest && flagCount >= 1
}

/**
 * 节奏型中的一个元素（一个音符或休止符）。
 *
 * @param duration 时值
 */
data class RhythmItem(val duration: RhythmDuration) {
    /** 拍数（四分音符 = 1 拍）。 */
    val beats: Double get() = duration.beats

    /** 是否为休止符。 */
    val isRest: Boolean get() = duration.isRest
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：四分音符 + 八分音符（4 拍）
 * - [INTERMEDIATE] 中级：加入二分音符 + 四分休止符
 * - [ADVANCED] 高级：加入十六分音符 + 八分休止符
 */
enum class RhythmReadingDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 节奏型选项（用于多选答案）。
 *
 * @param items 节奏序列
 * @param label 简称拼接的标签（如 "四 四 八 八"）
 * @param fingerprint 用于判等的指纹（时值名拼接，如 "QUARTER|QUARTER|EIGHTH|EIGHTH"）
 */
data class RhythmPatternOption(
    val items: List<RhythmItem>,
    val label: String,
    val fingerprint: String
)

/**
 * 节奏视读训练题目。
 *
 * @param difficulty 难度
 * @param pattern 题目显示的节奏型
 * @param answerOptions 4 个选项（含正确答案，已打乱）
 * @param correctAnswer 正确答案的指纹
 */
data class RhythmReadingQuestion(
    val difficulty: RhythmReadingDifficulty,
    val pattern: List<RhythmItem>,
    val answerOptions: List<RhythmPatternOption>,
    val correctAnswer: String
) {
    /** 节奏型总拍数（始终为 4.0，4/4 拍号下一个小节）。 */
    val totalBeats: Double get() = pattern.sumOf { it.beats }

    /** 节奏型元素数量。 */
    val elementCount: Int get() = pattern.size
}

/**
 * 一次答题结果。
 *
 * @param question 题目
 * @param userAnswer 用户选择的选项指纹
 * @param isCorrect 是否答对
 */
data class RhythmReadingAnswerRecord(
    val question: RhythmReadingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
