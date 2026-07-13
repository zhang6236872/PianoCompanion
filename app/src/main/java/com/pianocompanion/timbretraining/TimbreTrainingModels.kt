package com.pianocompanion.timbretraining

/**
 * 音色辨识训练（Timbre Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **音色辨识（Timbre Recognition）**：用户听到一段相同音高、相同长度的乐音，
 *   需要根据音色特征（谐波结构 + 包络）判断出对应的乐器。
 * - 即使所有乐器演奏同一个音（如 A4=440Hz），它们的音色截然不同——这是由
 *   **谐波含量**和**幅度包络**决定的。训练此能力可以提升对不同乐器的辨识力。
 *
 * 本模块支持 6 种乐器：
 *   1. PIANO（钢琴）— 衰减型包络 + 丰富但快速衰减的谐波
 *   2. VIOLIN（小提琴）— 弓弦持续 + 锯齿波（众多奇偶谐波）
 *   3. GUITAR（吉他）— 拨弦衰减 + 中等谐波
 *   4. FLUTE（长笛）— 近纯正弦 + 极少谐波
 *   5. CLARINET（单簧管）— 奇次谐波主导（管乐器圆柱腔特性）
 *   6. TRUMPET（小号）— 明亮丰富谐波 + 快速起音
 */

/**
 * 乐器类型（音色辨识目标）。
 *
 * @param englishName 英文名（如 "Piano"）
 * @param displayName 中文名（如 "钢琴"）
 * @param emoji 表情符号（UI 图标）
 * @param description 音色描述（答题后的教学反馈）
 * @param baseFrequency 基音频率（Hz），所有乐器统一使用此频率
 */
enum class TimbreInstrument(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val baseFrequency: Double
) {
    PIANO(
        englishName = "Piano",
        displayName = "钢琴",
        emoji = "🎹",
        description = "钢琴：敲击弦乐器。极快起音后呈指数衰减，谐波丰富但迅速减弱。清澈而富有打击感，如同水滴落入池塘。",
        baseFrequency = 440.0
    ),
    VIOLIN(
        englishName = "Violin",
        displayName = "小提琴",
        emoji = "🎻",
        description = "小提琴：弓弦乐器。缓慢起音后持续，锯齿波形赋予丰富的奇偶谐波，音色温暖而充满歌唱性。如同人声般的持续旋律。",
        baseFrequency = 440.0
    ),
    GUITAR(
        englishName = "Guitar",
        displayName = "吉他",
        emoji = "🎸",
        description = "吉他：拨弦乐器。快速起音后缓慢衰减，谐波中等丰富。明亮而有弹性，像被拨动的琴弦逐渐归于平静。",
        baseFrequency = 440.0
    ),
    FLUTE(
        englishName = "Flute",
        displayName = "长笛",
        emoji = "🪈",
        description = "长笛：木管吹奏乐器。接近纯正弦波，几乎无高次谐波，音色纯净柔和如同微风拂过。最「圆润」的音色。",
        baseFrequency = 440.0
    ),
    CLARINET(
        englishName = "Clarinet",
        displayName = "单簧管",
        emoji = "🎶",
        description = "单簧管：单簧木管乐器。奇次谐波主导（第3、5次谐波突出），赋予独特的「空洞」音色。温暖而略带鼻音的质感。",
        baseFrequency = 440.0
    ),
    TRUMPET(
        englishName = "Trumpet",
        displayName = "小号",
        emoji = "🎺",
        description = "小号：铜管吹奏乐器。明亮而富有穿透力，谐波极其丰富，快速起音后持续。辉煌灿烂如同光芒四射的号角。",
        baseFrequency = 440.0
    );

    /** 完整标识（如 "🎹 Piano  钢琴"）。 */
    val fullLabel: String get() = "$emoji $englishName  $displayName"

    /** 简短标识（不含 emoji，用于答题选项匹配）。 */
    val shortLabel: String get() = "$englishName  $displayName"

    companion object {
        val ALL: List<TimbreInstrument> = entries.toList()

        /** 初级乐器：音色差异最大（钢琴/长笛/小号），凭直觉即可区分。 */
        val BEGINNER_INSTRUMENTS: List<TimbreInstrument> = listOf(PIANO, FLUTE, TRUMPET)

        /** 中级乐器：中等差异（加入小提琴、单簧管）。 */
        val INTERMEDIATE_INSTRUMENTS: List<TimbreInstrument> = listOf(PIANO, VIOLIN, FLUTE, TRUMPET)

        /**
         * 按难度返回可用乐器集合。
         * - 初级：3 种差异极大的乐器（钢琴/长笛/小号）
         * - 中级：4 种（加入小提琴，需区分弓弦 vs 拨弦/敲击）
         * - 高级：全部 6 种（加入吉他、单簧管，考验精细辨识）
         */
        fun forDifficulty(difficulty: TimbreTrainingDifficulty): List<TimbreInstrument> = when (difficulty) {
            TimbreTrainingDifficulty.BEGINNER -> BEGINNER_INSTRUMENTS
            TimbreTrainingDifficulty.INTERMEDIATE -> INTERMEDIATE_INSTRUMENTS
            TimbreTrainingDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和乐器列表）
 * @param choiceCount 该难度的选项数量
 */
enum class TimbreTrainingDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种音色差异极大的乐器（3 选项）· 钢琴 / 长笛 / 小号", 3),
    INTERMEDIATE("中级", "4 种乐器（4 选项）· 加入小提琴", 4),
    ADVANCED("高级", "全部 6 种乐器（6 选项）· 加入吉他 / 单簧管", 6);

    companion object {
        val ALL: List<TimbreTrainingDifficulty> = entries.toList()
    }
}

/**
 * 音色辨识训练题目。
 *
 * @param instrument 正确的乐器类型
 * @param difficulty 难度
 * @param noteDurationMs 音符持续时间（毫秒）
 * @param answerChoices 所有选项（乐器名+中文，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class TimbreTrainingQuestion(
    val instrument: TimbreInstrument,
    val difficulty: TimbreTrainingDifficulty,
    val noteDurationMs: Long,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "🎹 Piano 钢琴"）。 */
    val fullName: String
        get() = "${instrument.emoji} ${instrument.englishName} ${instrument.displayName}"
}

/**
 * 一次答题结果。
 */
data class TimbreTrainingAnswerRecord(
    val question: TimbreTrainingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
