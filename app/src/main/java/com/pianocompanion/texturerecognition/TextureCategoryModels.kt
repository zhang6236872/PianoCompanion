package com.pianocompanion.texturerecognition

/**
 * 织体类型辨识训练（Texture Type Recognition）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * **与模块 #27「织体辨识训练」的区别：**
 * - #27（`texturerecognitiontraining`）采用 **5 类细分** 模型，将主调（Homophonic）拆分为
 *   柱式和弦伴奏 / 分解和弦伴奏两种；
 * - 本模块（#54）采用 **4 类基础** 模型，对应乐理教科书中对织体的**根本性分类**——
 *   单声部 / 主调 / 复调 / 支声复调，将主调视为统一类别。这四种是织体感知的「四象限」，
 *   训练目标是建立对织体最基础结构的直觉。
 *
 * 核心概念：
 * - **单声部（Monophonic）**：只有一条旋律线，一次只响一个音。最纯粹的织体。
 * - **主调（Homophonic）**：一条主旋律 + 和声伴奏，伴奏支撑旋律、与旋律节奏一致或跟随。
 *   旋律突出，伴奏是「背景」。
 * - **复调（Polyphonic）**：两条或多条**独立**的旋律线同时进行，各有自己的节奏与走向，
 *   地位平等、互相交织——如巴赫赋格。
 * - **支声复调（Heterophonic）**：同一条旋律由两个（或多个）声部同时演奏，其中某些声部
 *   加入装饰音、经过音等变化。两条线「同源」但「不同形」——如民间音乐的即兴变奏齐奏。
 *
 * **听辨要点（复调 vs 支声复调 是本模块的核心难点）：**
 * - 复调：两条线的旋律素材**不同**，听起来是两个独立的「故事」；
 * - 支声复调：两条线的旋律素材**相同**，只是其中一条被「装饰」了——听起来仍是同一首曲子，
 *   只是有一条在「加花」。
 */
enum class MusicTextureType(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val listenHint: String
) {
    MONOPHONIC(
        englishName = "Monophonic",
        displayName = "单声部",
        emoji = "🎵",
        description = "单声部（Monophonic）：只有一条旋律线，一次只听到一个音在响。是最纯粹、最干净的织体形式，如无伴奏独唱或齐唱。",
        listenHint = "从头到尾只有一条线，干净纯粹，没有伴奏"
    ),
    HOMOPHONIC(
        englishName = "Homophonic",
        displayName = "主调",
        emoji = "🎹",
        description = "主调（Homophonic）：一条突出的主旋律，下方有和声伴奏支撑。旋律是主角，伴奏是背景——二者节奏一致或伴奏跟随旋律。",
        listenHint = "旋律很突出，下方有和声/和弦像背景一样托着"
    ),
    POLYPHONIC(
        englishName = "Polyphonic",
        displayName = "复调",
        emoji = "🎼",
        description = "复调（Polyphonic）：两条或多条独立的旋律线同时进行，每条线都有自己的节奏和走向，地位平等、互相交织——如巴赫的赋格。",
        listenHint = "两条线各有独立的节奏和旋律，像两个平等的声音在对话"
    ),
    HETEROPHONIC(
        englishName = "Heterophonic",
        displayName = "支声复调",
        emoji = "🎭",
        description = "支声复调（Heterophonic）：同一条旋律由两个声部同时演奏，其中一个声部加入装饰音、经过音等变化。两条线同源而不同形——如民间音乐的即兴变奏齐奏。",
        listenHint = "两条线弹的是同一首旋律，但有一条在「加花」装饰"
    );

    /** 完整标识（如 "主调 (Homophonic)"）。 */
    val fullLabel: String get() = "$displayName ($englishName)"

    companion object {
        val ALL: List<MusicTextureType> = entries.toList()

        /** 初级：3 种差异最大的基础织体（不含支声复调）。 */
        val BEGINNER_TYPES: List<MusicTextureType> = listOf(MONOPHONIC, HOMOPHONIC, POLYPHONIC)

        /** 中级：全部 4 种基础织体（加入支声复调，难点在区分复调 vs 支声）。 */
        val INTERMEDIATE_TYPES: List<MusicTextureType> = ALL

        /** 高级：全部 4 种，但片段更复杂（更快、节奏更密、声部音区重叠）。 */
        val ADVANCED_TYPES: List<MusicTextureType> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 选项数量
 * @param types 该难度出现的织体集合
 * @param tempoBpm 片段速度（BPM，四分音符）
 * @param complexity 复杂度等级（影响片段的节奏密度与音区重叠程度）
 */
enum class MusicTextureDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val types: List<MusicTextureType>,
    val tempoBpm: Int,
    val complexity: Int
) {
    BEGINNER(
        displayName = "初级",
        description = "3 种差异最大的织体 · 单声部/主调/复调 · 3 选项",
        choiceCount = 3,
        types = MusicTextureType.BEGINNER_TYPES,
        tempoBpm = 96,
        complexity = 1
    ),
    INTERMEDIATE(
        displayName = "中级",
        description = "全部 4 种基础织体（含支声复调）· 4 选项",
        choiceCount = 4,
        types = MusicTextureType.INTERMEDIATE_TYPES,
        tempoBpm = 108,
        complexity = 2
    ),
    ADVANCED(
        displayName = "高级",
        description = "全部 4 种 · 更快更复杂 · 细致区分复调与支声 · 4 选项",
        choiceCount = 4,
        types = MusicTextureType.ADVANCED_TYPES,
        tempoBpm = 128,
        complexity = 3
    );

    companion object {
        val ALL: List<MusicTextureDifficulty> = entries.toList()
    }
}

/**
 * 织体类型辨识训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param targetTexture 正确的织体类型
 * @param answerChoices 所有选项标签（含正确答案，已打乱）
 * @param correctAnswer 正确答案标签
 */
data class TextureCategoryQuestion(
    val difficulty: MusicTextureDifficulty,
    val seed: Long,
    val targetTexture: MusicTextureType,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(targetTexture in difficulty.types) {
            "目标织体 ${targetTexture.displayName} 不在难度 ${difficulty.displayName} 的织体集合中"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(answerChoices.distinct().size == answerChoices.size) { "选项存在重复" }
        require(answerChoices.size == difficulty.choiceCount) {
            "选项数 (${answerChoices.size}) 与难度配置 (${difficulty.choiceCount}) 不一致"
        }
    }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${targetTexture.emoji} ${targetTexture.fullLabel} · ${targetTexture.description}"

    /** 听辨提示。 */
    val listenHint: String get() = targetTexture.listenHint

    /** 片段速度（BPM）。 */
    val tempoBpm: Int get() = difficulty.tempoBpm

    /** 复杂度。 */
    val complexity: Int get() = difficulty.complexity
}

/**
 * 一次答题结果。
 */
data class TextureCategoryAnswerRecord(
    val question: TextureCategoryQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
