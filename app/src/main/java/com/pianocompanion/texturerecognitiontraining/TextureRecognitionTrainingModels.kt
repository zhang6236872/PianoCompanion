package com.pianocompanion.texturerecognitiontraining

/**
 * 织体辨识训练（Texture Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **织体（Texture）**：音乐中声部的组合方式——有几个声部、它们如何配合。
 *   织体是理解音乐整体结构和层次感的基础。
 *
 * 本模块支持 5 种织体类型：
 *   1. MONOPHONIC（单声部）—— 单条旋律线，一次只响一个音
 *   2. HOMOPHONIC_CHORDAL（柱式和弦伴奏）—— 旋律 + 同时响起的块状和弦伴奏
 *   3. HOMOPHONIC_ARPEGGIATED（分解和弦伴奏）—— 旋律 + 滚动琶音（分解和弦）伴奏
 *   4. POLYPHONIC（复调）—— 两条独立的旋律线，各有自己的节奏
 *   5. HETEROPHONIC（支声）—— 同一条旋律由两个声部演奏，其中一个加入装饰变化
 */

/**
 * 织体类型（辨识目标）。
 *
 * @param englishName 英文名
 * @param displayName 中文名
 * @param emoji 表情符号
 * @param description 织体描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class TextureType(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val hint: String
) {
    MONOPHONIC(
        englishName = "Monophonic",
        displayName = "单声部",
        emoji = "🎵",
        description = "单声部（Monophonic）：只有一条旋律线，一次只听到一个音在响。是最纯粹的织体形式，如无伴奏的独唱或齐唱。",
        hint = "只有一条线在响，干净纯粹"
    ),
    HOMOPHONIC_CHORDAL(
        englishName = "Homophonic (Chordal)",
        displayName = "柱式和弦",
        emoji = "🎹",
        description = "柱式和弦伴奏（Homophonic Chordal）：一条主旋律，伴奏用同时响起的和弦支撑。旋律与和弦节奏一致，层次分明——旋律在上，和弦在下。",
        hint = "旋律清晰，下方有同时敲响的和弦托底"
    ),
    HOMOPHONIC_ARPEGGIATED(
        englishName = "Homophonic (Arpeggiated)",
        displayName = "分解和弦",
        emoji = "🌊",
        description = "分解和弦伴奏（Homophonic Arpeggiated）：一条主旋律，伴奏用依次滚动的琶音（分解和弦）烘托。旋律在上方持续，下方有流水般的音符涌动。",
        hint = "旋律在上方，下方有流水般的琶音滚动"
    ),
    POLYPHONIC(
        englishName = "Polyphonic",
        displayName = "复调",
        emoji = "🎼",
        description = "复调（Polyphonic）：两条或多条独立的旋律线同时进行，每条线都有自己的节奏和走向。声部之间地位平等，互相交织——如巴赫的赋格。",
        hint = "两条旋律各有独立节奏，互相交织穿插"
    ),
    HETEROPHONIC(
        englishName = "Heterophonic",
        displayName = "支声",
        emoji = "🎭",
        description = "支声（Heterophonic）：同一个旋律由两个声部同时演奏，其中一个声部在原旋律基础上加入装饰音、经过音等变化。两条线基本相同但又略有不同——如民间音乐中的即兴变奏。",
        hint = "两条线弹的是同一旋律，但一条有装饰变化"
    );

    /** 完整标识（如 "单声部 (Monophonic)"）。 */
    val fullLabel: String get() = "$displayName ($englishName)"

    companion object {
        val ALL: List<TextureType> = entries.toList()

        /** 初级织体：3 种差异最大的类型。 */
        val BEGINNER_TYPES: List<TextureType> = listOf(MONOPHONIC, HOMOPHONIC_CHORDAL, POLYPHONIC)

        /** 中级织体：4 种（加入分解和弦）。 */
        val INTERMEDIATE_TYPES: List<TextureType> = listOf(
            MONOPHONIC, HOMOPHONIC_CHORDAL, HOMOPHONIC_ARPEGGIATED, POLYPHONIC
        )

        /** 高级织体：全部 5 种（含支声）。 */
        val ADVANCED_TYPES: List<TextureType> = ALL

        /**
         * 按难度返回可用织体集合。
         */
        fun forDifficulty(difficulty: TextureDifficulty): List<TextureType> = when (difficulty) {
            TextureDifficulty.BEGINNER -> BEGINNER_TYPES
            TextureDifficulty.INTERMEDIATE -> INTERMEDIATE_TYPES
            TextureDifficulty.ADVANCED -> ADVANCED_TYPES
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 该难度的选项数量
 */
enum class TextureDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种差异最大的织体（3 选项）· 单声部 / 柱式和弦 / 复调", 3),
    INTERMEDIATE("中级", "4 种织体含分解和弦（4 选项）· 加入柱式与分解和弦的区分", 4),
    ADVANCED("高级", "全部 5 种含支声织体（5 选项）· 细致织体辨识", 5);

    companion object {
        val ALL: List<TextureDifficulty> = entries.toList()
    }
}

/**
 * 织体辨识训练题目。
 *
 * @param texture 正确的织体类型
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染）
 * @param answerChoices 所有选项（织体名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class TextureQuestion(
    val texture: TextureType,
    val difficulty: TextureDifficulty,
    val seed: Long,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述。 */
    val fullName: String get() = texture.fullLabel
}

/**
 * 一次答题结果。
 */
data class TextureAnswerRecord(
    val question: TextureQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
