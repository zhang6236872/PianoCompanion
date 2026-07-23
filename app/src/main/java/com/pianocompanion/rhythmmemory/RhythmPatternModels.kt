package com.pianocompanion.rhythmmemory

/**
 * 节奏型记忆训练（Rhythm Pattern Memory）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * **训练目标**：听后回忆并复现节奏型。播放一段由若干「节奏单元」（每拍一种细分模式）
 * 组成的短节奏型，用户需在听完后从若干**极为相似**的干扰选项中选出刚才听到的那一条。
 *
 * **核心概念 — 节奏单元（RhythmCellType）**：
 * 节奏型以「拍」为单位组织。每一拍包含一种**细分模式**（subdivision），即一拍内击打的
 * 次数与时长比例。例如「两个八分」= 一拍内两个均等的击打，「附点八分+十六分」=
 * 一拍内一长（3/4）一短（1/4）。一条节奏型就是若干拍的细分模式序列。
 *
 * **记忆难点**：干扰选项通过对正确节奏型做**细微变异**生成——交换相邻两拍、替换某一拍
 * 的细分模式等。用户必须精确记住听到的节奏序列（哪一拍是哪种细分），才能在相似选项中
 * 区分出正确答案。这训练的是**听觉短期记忆**与**节奏内化**能力。
 *
 * 与节拍听辨（compound meter）的区别：节拍听辨关注的是「强弱分组」（3+3 vs 2+2+2），
 * 而本模块关注的是「拍内细分」的精确序列记忆。
 */

/**
 * 单拍节奏单元（一拍内的击打细分模式）。
 *
 * @param displayName 中文显示名
 * @param symbol 节奏记号符号（用于选项展示）
 * @param subdivisions 一拍内各击打的相对时长（列表之和 = 1.0，即完整一拍）
 * @param description 说明
 */
enum class RhythmCellType(
    val displayName: String,
    val symbol: String,
    val subdivisions: List<Double>,
    val description: String
) {
    /** 四分音符：一拍一个音，稳定均匀。 */
    QUARTER(
        displayName = "四分",
        symbol = "♩",
        subdivisions = listOf(1.0),
        description = "一拍一个音，稳定均匀"
    ),

    /** 两个八分：一拍两个均等的音。 */
    TWO_EIGHTHS(
        displayName = "两个八分",
        symbol = "♪♪",
        subdivisions = listOf(0.5, 0.5),
        description = "一拍两个均等的音"
    ),

    /** 四个十六分：一拍四个快速的音。 */
    FOUR_SIXTEENTHS(
        displayName = "四个十六分",
        symbol = "♬♬",
        subdivisions = listOf(0.25, 0.25, 0.25, 0.25),
        description = "一拍四个快速的音"
    ),

    /** 长短短：八分 + 两个十六分。 */
    LONG_SHORT_SHORT(
        displayName = "长短短",
        symbol = "♪♬",
        subdivisions = listOf(0.5, 0.25, 0.25),
        description = "八分 + 两个十六分（长-短-短）"
    ),

    /** 短短长：两个十六分 + 八分。 */
    SHORT_SHORT_LONG(
        displayName = "短短长",
        symbol = "♬♪",
        subdivisions = listOf(0.25, 0.25, 0.5),
        description = "两个十六分 + 八分（短-短-长）"
    ),

    /** 附点长短：附点八分 + 十六分（3/4 + 1/4）。 */
    DOTTED_LONG_SHORT(
        displayName = "附点长短",
        symbol = "♪·♪",
        subdivisions = listOf(0.75, 0.25),
        description = "附点八分 + 十六分（长-短）"
    ),

    /** 三连音：一拍三个均等的音（各 1/3）。 */
    TRIPLET(
        displayName = "三连音",
        symbol = "♪³",
        subdivisions = listOf(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0),
        description = "一拍三个均等的音（三连音）"
    );

    /** 每拍击打次数（= 细分段数）。 */
    val hitCount: Int get() = subdivisions.size

    companion object {
        /** 初级节奏单元池：3 种最基础的细分（均匀 1/2/4 拍）。 */
        val BEGINNER_POOL: List<RhythmCellType> = listOf(QUARTER, TWO_EIGHTHS, FOUR_SIXTEENTHS)

        /** 中级节奏单元池：5 种（加入长短组合）。 */
        val INTERMEDIATE_POOL: List<RhythmCellType> =
            listOf(QUARTER, TWO_EIGHTHS, FOUR_SIXTEENTHS, LONG_SHORT_SHORT, SHORT_SHORT_LONG)

        /** 高级节奏单元池：全部 7 种（加入附点、三连音）。 */
        val ADVANCED_POOL: List<RhythmCellType> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 选项数量
 * @param beats 节奏型拍数
 * @param cellPool 该难度可用的节奏单元池
 * @param tempoBpm 速度（四分音符 BPM，越快越难记忆）
 */
enum class RhythmMemoryDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val beats: Int,
    val cellPool: List<RhythmCellType>,
    val tempoBpm: Int
) {
    BEGINNER(
        displayName = "初级",
        description = "3 拍 · 3 种基础节奏型 · 2 选项",
        choiceCount = 2,
        beats = 3,
        cellPool = RhythmCellType.BEGINNER_POOL,
        tempoBpm = 100
    ),
    INTERMEDIATE(
        displayName = "中级",
        description = "4 拍 · 5 种节奏型（含长短组合）· 3 选项",
        choiceCount = 3,
        beats = 4,
        cellPool = RhythmCellType.INTERMEDIATE_POOL,
        tempoBpm = 112
    ),
    ADVANCED(
        displayName = "高级",
        description = "4 拍 · 7 种节奏型（含附点、三连音）· 4 选项",
        choiceCount = 4,
        beats = 4,
        cellPool = RhythmCellType.ADVANCED_POOL,
        tempoBpm = 124
    );

    companion object {
        val ALL: List<RhythmMemoryDifficulty> = entries.toList()
    }
}

/**
 * 一条节奏型（若干拍节奏单元的序列）。
 */
data class RhythmPattern(val cells: List<RhythmCellType>) {
    init {
        require(cells.isNotEmpty()) { "节奏型不能为空" }
    }

    /** 拍数。 */
    val beats: Int get() = cells.size

    /** 节奏记号显示串（各拍符号用两个空格分隔，如 "♩  ♪♪  ♬♬"）。 */
    val displayString: String get() = cells.joinToString("  ") { it.symbol }

    /** 节奏单元名称串（各拍名用 → 分隔，如 "四分 → 两个八分 → 四个十六分"）。 */
    val nameString: String get() = cells.joinToString(" → ") { it.displayName }

    /** 总击打次数（各拍击打数之和）。 */
    val totalHits: Int get() = cells.sumOf { it.hitCount }

    /** 复制并返回可变副本。 */
    fun toMutable(): MutableList<RhythmCellType> = cells.toMutableList()

    companion object {
        /** 由单元列表构建节奏型。 */
        fun of(vararg cells: RhythmCellType): RhythmPattern = RhythmPattern(cells.toList())
    }
}

/**
 * 节奏型记忆训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param targetPattern 正确的节奏型
 * @param answerChoices 所有选项的显示串（含正确答案，已打乱）
 * @param correctAnswer 正确答案的显示串
 */
data class RhythmMemoryQuestion(
    val difficulty: RhythmMemoryDifficulty,
    val seed: Long,
    val targetPattern: RhythmPattern,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(answerChoices.distinct().size == answerChoices.size) { "选项存在重复" }
        require(answerChoices.size == difficulty.choiceCount) {
            "选项数 (${answerChoices.size}) 与难度配置 (${difficulty.choiceCount}) 不一致"
        }
        require(targetPattern.displayString == correctAnswer) { "目标节奏型与正确答案不匹配" }
    }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "正确节奏型：${targetPattern.nameString}（${targetPattern.displayString}）"

    /** 速度（BPM）。 */
    val tempoBpm: Int get() = difficulty.tempoBpm

    /** 拍数。 */
    val beats: Int get() = targetPattern.beats
}

/**
 * 一次答题结果。
 */
data class RhythmMemoryAnswerRecord(
    val question: RhythmMemoryQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
