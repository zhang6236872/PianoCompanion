package com.pianocompanion.modulationrecognition

/**
 * 转调辨识训练（Modulation Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **转调（Modulation）**：音乐进行中从一个调性中心转移到另一个调性中心的过程。
 *   是音乐发展、段落对比和情感推进的重要手段。
 * - 转调辨识要求听者感知到调性中心的变化，并判断变化的方向和类型。
 *   这对理解大型曲式结构和和弦进行功能至关重要。
 *
 * 本模块支持 4 种转调类型：
 *   1. TO_DOMINANT（转入属调）—— 向上五度（或下四度）转调，最常见的"明亮"转调
 *   2. TO_SUBDOMINANT（转入下属调）—— 向下五度（或上四度）转调，常见的"柔和"转调
 *   3. TO_RELATIVE（转入关系调）—— 同音列大↔小调互换（如 C大调↔a小调）
 *   4. NO_MODULATION（无转调）—— 停留在原调，用终止式巩固调性
 */

/**
 * 转调类型（辨识目标）。
 *
 * @param englishName 英文名
 * @param displayName 中文名
 * @param emoji 表情符号
 * @param description 转调描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class ModulationType(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val hint: String
) {
    TO_DOMINANT(
        englishName = "To Dominant",
        displayName = "转入属调",
        emoji = "↑5",
        description = "转入属调（To Dominant）：从原调向上五度（或下四度）转调。例如 C大调→G大调。这是最常见、最自然的转调，带来明亮、开放的色彩变化，常用于副歌或高潮段落。",
        hint = "调中心上移，整体变得更明亮、开放"
    ),
    TO_SUBDOMINANT(
        englishName = "To Subdominant",
        displayName = "转入下属调",
        emoji = "↓5",
        description = "转入下属调（To Subdominant）：从原调向下五度（或上四度）转调。例如 C大调→F大调。这种转调带来柔和、温暖的色彩变化，常用于第二主题或抒情段落。",
        hint = "调中心下移，整体变得更柔和、温暖"
    ),
    TO_RELATIVE(
        englishName = "To Relative",
        displayName = "转入关系调",
        emoji = "↔",
        description = "转入关系调（To Relative）：在同音列的大调和小调之间转换。例如 C大调→a小调，或 a小调→C大调。这种转换使用相同的音符，但色彩从大调的明朗变为小调的忧郁（或反之），是一种戏剧性的情绪变化。",
        hint = "音高集合不变，但大小调色彩突然翻转"
    ),
    NO_MODULATION(
        englishName = "No Modulation",
        displayName = "无转调",
        emoji = "≡",
        description = "无转调（No Modulation）：和弦进行始终停留在原调中，通过 I-IV-V-I 等功能进行巩固调性中心。没有调性中心的转移，整体色彩稳定不变。",
        hint = "调中心稳定不变，色彩统一"
    );

    /** 完整标识（如 "转入属调 (To Dominant)"）。 */
    val fullLabel: String get() = "$displayName ($englishName)"

    companion object {
        val ALL: List<ModulationType> = entries.toList()

        /** 初级转调类型：2 种对比最明显的类型。 */
        val BEGINNER_TYPES: List<ModulationType> = listOf(TO_DOMINANT, NO_MODULATION)

        /** 中级转调类型：3 种（加入下属调）。 */
        val INTERMEDIATE_TYPES: List<ModulationType> = listOf(TO_DOMINANT, TO_SUBDOMINANT, NO_MODULATION)

        /** 高级转调类型：全部 4 种（加入关系调，考验大小调色彩辨识）。 */
        val ADVANCED_TYPES: List<ModulationType> = ALL

        /**
         * 按难度返回可用转调类型集合。
         */
        fun forDifficulty(difficulty: ModulationDifficulty): List<ModulationType> = when (difficulty) {
            ModulationDifficulty.BEGINNER -> BEGINNER_TYPES
            ModulationDifficulty.INTERMEDIATE -> INTERMEDIATE_TYPES
            ModulationDifficulty.ADVANCED -> ADVANCED_TYPES
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
enum class ModulationDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "2 种类型（属调 / 无转调）· 对比最明显", 2),
    INTERMEDIATE("中级", "3 种类型 · 加入下属调转调", 3),
    ADVANCED("高级", "全部 4 种类型 · 加入关系调大小调切换", 4);

    companion object {
        val ALL: List<ModulationDifficulty> = entries.toList()
    }
}

/**
 * 转调辨识训练题目。
 *
 * @param modulation 正确的转调类型
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染）
 * @param answerChoices 所有选项（转调名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class ModulationQuestion(
    val modulation: ModulationType,
    val difficulty: ModulationDifficulty,
    val seed: Long,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述。 */
    val fullName: String get() = modulation.fullLabel
}

/**
 * 一次答题结果。
 */
data class ModulationAnswerRecord(
    val question: ModulationQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
