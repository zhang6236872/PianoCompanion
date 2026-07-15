package com.pianocompanion.contrapuntalmotiontraining

/**
 * 声部运动辨识训练（Contrapuntal Motion Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **声部运动（Contrapuntal Motion）**：当两条旋律线（声部）同时进行时，
 *   它们之间的运动关系。这是对位法和声部进行（voice leading）的基础概念。
 * - 在音乐理论中，两个声部之间的运动可分为四种基本类型，掌握它们是
 *   理解多声部音乐和编写良好声部进行的关键。
 *
 * 本模块支持 4 种声部运动类型：
 *   1. PARALLEL（平行进行）—— 两个声部同向运动且保持相同的音程距离
 *   2. SIMILAR（同向进行）—— 两个声部同向运动但音程距离发生变化
 *   3. CONTRARY（反向进行）—— 两个声部朝相反方向运动（一升一降）
 *   4. OBLIQUE（斜向进行）—— 一个声部保持不变，另一个声部运动
 */

/**
 * 声部运动类型（辨识目标）。
 *
 * @param englishName 英文名
 * @param displayName 中文名
 * @param emoji 表情符号
 * @param symbol 图示符号
 * @param description 运动描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class ContrapuntalMotionType(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val symbol: String,
    val description: String,
    val hint: String
) {
    PARALLEL(
        englishName = "Parallel",
        displayName = "平行进行",
        emoji = "⇈",
        symbol = "↑↑",
        description = "平行进行（Parallel Motion）：两个声部朝同一方向运动，且始终保持相同的音程距离。听起来像两条线同步攀升或下落，间距恒定不变——如卡农中的严格模仿。",
        hint = "两条线同步同向移动，间距不变"
    ),
    SIMILAR(
        englishName = "Similar",
        displayName = "同向进行",
        emoji = "↗↗",
        symbol = "↑↑",
        description = "同向进行（Similar Motion）：两个声部朝同一方向运动，但音程距离会发生变化。两条线大体同向，但间距时而扩大时而收缩——最常见的自然声部进行方式。",
        hint = "两条线同向移动，但间距在变化"
    ),
    CONTRARY(
        englishName = "Contrary",
        displayName = "反向进行",
        emoji = "⇅",
        symbol = "↑↓",
        description = "反向进行（Contrary Motion）：两个声部朝相反方向运动——一个上行，另一个下行。两条线背道而驰或互相靠拢，声部独立性最强——对位法中最推荐的运动方式。",
        hint = "一条上升，另一条下降（或反之）"
    ),
    OBLIQUE(
        englishName = "Oblique",
        displayName = "斜向进行",
        emoji = "⇄",
        symbol = "↑→",
        description = "斜向进行（Oblique Motion）：一个声部保持不动（重复音），另一个声部运动。听起来一条线在原地踏步，另一条在移动——常用于踏板音（pedal point）或持续音。",
        hint = "一条线保持不动，另一条在移动"
    );

    /** 完整标识（如 "平行进行 (Parallel)"）。 */
    val fullLabel: String get() = "$displayName ($englishName)"

    companion object {
        val ALL: List<ContrapuntalMotionType> = entries.toList()

        /** 初级声部运动：2 种差异最大的类型。 */
        val BEGINNER_TYPES: List<ContrapuntalMotionType> = listOf(PARALLEL, OBLIQUE)

        /** 中级声部运动：3 种（加入反向）。 */
        val INTERMEDIATE_TYPES: List<ContrapuntalMotionType> = listOf(PARALLEL, OBLIQUE, CONTRARY)

        /** 高级声部运动：全部 4 种（含同向，考验平行 vs 同向的区分）。 */
        val ADVANCED_TYPES: List<ContrapuntalMotionType> = ALL

        /**
         * 按难度返回可用声部运动集合。
         */
        fun forDifficulty(difficulty: ContrapuntalMotionDifficulty): List<ContrapuntalMotionType> = when (difficulty) {
            ContrapuntalMotionDifficulty.BEGINNER -> BEGINNER_TYPES
            ContrapuntalMotionDifficulty.INTERMEDIATE -> INTERMEDIATE_TYPES
            ContrapuntalMotionDifficulty.ADVANCED -> ADVANCED_TYPES
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
enum class ContrapuntalMotionDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "2 种差异最大的运动（2 选项）· 平行 / 斜向", 2),
    INTERMEDIATE("中级", "3 种运动含反向（3 选项）· 加入反向进行的辨识", 3),
    ADVANCED("高级", "全部 4 种运动（4 选项）· 区分平行与同向进行", 4);

    companion object {
        val ALL: List<ContrapuntalMotionDifficulty> = entries.toList()
    }
}

/**
 * 声部运动辨识训练题目。
 *
 * @param motion 正确的声部运动类型
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染）
 * @param answerChoices 所有选项（运动名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class ContrapuntalMotionQuestion(
    val motion: ContrapuntalMotionType,
    val difficulty: ContrapuntalMotionDifficulty,
    val seed: Long,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述。 */
    val fullName: String get() = motion.fullLabel
}

/**
 * 一次答题结果。
 */
data class ContrapuntalMotionAnswerRecord(
    val question: ContrapuntalMotionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
