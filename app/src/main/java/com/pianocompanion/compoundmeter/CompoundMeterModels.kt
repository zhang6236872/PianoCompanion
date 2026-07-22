package com.pianocompanion.compoundmeter

/**
 * 复合节拍听辨训练（Compound Meter Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **简单拍子（Simple Meter）**：每拍可均分为两个等分（如 2/4、3/4、4/4）。
 * - **复合拍子（Compound Meter）**：每拍可均分为三个等分（如 6/8、9/8、12/8）。
 *   复合拍子的拍号上方数字是 3 的倍数（6、9、12），下方为 8，每个拍子单位是附点四分音符，
 *   包含三个八分音符。
 *
 * 复合拍子 vs 简单拍子的关键听辨线索：
 * - **分组模式**：复合拍子将八分音符按「3 个一组」分组（如 6/8 = 3+3），
 *   简单拍子按「2 个一组」分组（如 3/4 = 2+2+2）。
 * - 6/8 和 3/4 都有 6 个八分音符，但分组完全不同——这是最经典的听辨挑战。
 * - **强拍位置**：6/8 的强拍在第 1、4 个八分音符（两组之间间隔 3），
 *   3/4 的强拍在第 1、3、5 个八分音符（间隔 2）。
 *
 * 训练流程：
 * 1. 听一段带有重音模式的节拍（重音标识拍点，弱拍标识细分）
 * 2. 判断这段节拍属于哪种拍子（从选项中选出）
 */
enum class MeterType(
    val displayName: String,
    val timeSignature: String,
    val category: String,
    val description: String,
    val beatCount: Int,
    val subdivisionsPerBeat: Int,
    val eighthNotesPerBar: Int,
    val accentPattern: List<Float>
) {
    THREE_FOUR(
        displayName = "3/4拍（简单三拍子）",
        timeSignature = "3/4",
        category = "简单拍子",
        description = "华尔兹节拍 · 每小节3拍 · 每拍分2个八分音符",
        beatCount = 3,
        subdivisionsPerBeat = 2,
        eighthNotesPerBar = 6,
        // 强弱模式：强 弱 弱 强 弱 弱（第1拍强，第2、3拍弱）
        accentPattern = listOf(1.0f, 0.2f, 0.55f, 0.2f, 0.45f, 0.2f)
    ),
    FOUR_FOUR(
        displayName = "4/4拍（简单四拍子）",
        timeSignature = "4/4",
        category = "简单拍子",
        description = "常见节拍 · 每小节4拍 · 每拍分2个八分音符",
        beatCount = 4,
        subdivisionsPerBeat = 2,
        eighthNotesPerBar = 8,
        // 强弱模式：强 弱 次强 弱 强 弱 次强 弱
        accentPattern = listOf(1.0f, 0.2f, 0.55f, 0.2f, 0.45f, 0.2f, 0.55f, 0.2f)
    ),
    SIX_EIGHT(
        displayName = "6/8拍（复合二拍子）",
        timeSignature = "6/8",
        category = "复合拍子",
        description = "吉格舞曲节拍 · 每小节2个附点拍 · 每拍分3个八分音符",
        beatCount = 2,
        subdivisionsPerBeat = 3,
        eighthNotesPerBar = 6,
        // 强弱模式：强 弱弱 强 弱弱（3+3分组）
        accentPattern = listOf(1.0f, 0.2f, 0.2f, 0.55f, 0.2f, 0.2f)
    ),
    NINE_EIGHT(
        displayName = "9/8拍（复合三拍子）",
        timeSignature = "9/8",
        category = "复合拍子",
        description = "华尔兹变体 · 每小节3个附点拍 · 每拍分3个八分音符",
        beatCount = 3,
        subdivisionsPerBeat = 3,
        eighthNotesPerBar = 9,
        // 强弱模式：强 弱弱 强 弱弱 强 弱弱
        accentPattern = listOf(1.0f, 0.2f, 0.2f, 0.55f, 0.2f, 0.2f, 0.5f, 0.2f, 0.2f)
    ),
    TWELVE_EIGHT(
        displayName = "12/8拍（复合四拍子）",
        timeSignature = "12/8",
        category = "复合拍子",
        description = "蓝调/摇摆节拍 · 每小节4个附点拍 · 每拍分3个八分音符",
        beatCount = 4,
        subdivisionsPerBeat = 3,
        eighthNotesPerBar = 12,
        // 强弱模式：强 弱弱 次强 弱弱 次强 弱弱 次强 弱弱
        accentPattern = listOf(1.0f, 0.2f, 0.2f, 0.55f, 0.2f, 0.2f, 0.45f, 0.2f, 0.2f, 0.5f, 0.2f, 0.2f)
    );

    /** 完整标签。 */
    val fullLabel: String get() = "$displayName（$category）"

    /** 教学描述。 */
    val teachingDescription: String get() = "$displayName · $description · ${beatCount}拍×${subdivisionsPerBeat}细分"

    /** 判断是否为复合拍子。 */
    val isCompound: Boolean get() = subdivisionsPerBeat == 3

    /** 获取指定位置的八分音符是否为拍点（强拍或次强拍）。 */
    fun isBeatPosition(position: Int): Boolean {
        return position % subdivisionsPerBeat == 0
    }

    /** 获取指定位置是否为小节起始（最强弱拍）。 */
    fun isDownbeat(position: Int): Boolean = position == 0

    companion object {
        /** 初级难度使用的拍子（6/8 vs 3/4——同样6个八分音符，但分组完全不同）。 */
        val BEGINNER_METERS: List<MeterType> = listOf(SIX_EIGHT, THREE_FOUR)

        /** 中级难度使用的拍子（三种复合拍子）。 */
        val INTERMEDIATE_METERS: List<MeterType> = listOf(SIX_EIGHT, NINE_EIGHT, TWELVE_EIGHT)

        /** 高级难度使用的拍子（简单+复合全部混合）。 */
        val ADVANCED_METERS: List<MeterType> = listOf(THREE_FOUR, FOUR_FOUR, SIX_EIGHT, NINE_EIGHT, TWELVE_EIGHT)

        /** 所有复合拍子。 */
        val COMPOUND_METERS: List<MeterType> = listOf(SIX_EIGHT, NINE_EIGHT, TWELVE_EIGHT)
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 选项数量
 * @param meters 该难度使用的拍子集合
 * @param eighthNoteDurationMs 每个八分音符的时长（毫秒）
 * @param barCount 播放的小节数
 * @param barGapMs 小节之间的间隔（毫秒）
 */
enum class CompoundMeterDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val meters: List<MeterType>,
    val eighthNoteDurationMs: Int,
    val barCount: Int,
    val barGapMs: Int
) {
    BEGINNER(
        "初级",
        "6/8 vs 3/4 · 复合vs简单 · 2选项",
        choiceCount = 2,
        meters = MeterType.BEGINNER_METERS,
        eighthNoteDurationMs = 200,
        barCount = 2,
        barGapMs = 80
    ),
    INTERMEDIATE(
        "中级",
        "6/8 / 9/8 / 12/8 · 复合拍子 · 3选项",
        choiceCount = 3,
        meters = MeterType.INTERMEDIATE_METERS,
        eighthNoteDurationMs = 180,
        barCount = 2,
        barGapMs = 70
    ),
    ADVANCED(
        "高级",
        "3/4 / 4/4 / 6/8 / 9/8 / 12/8 · 全部混合 · 5选项",
        choiceCount = 5,
        meters = MeterType.ADVANCED_METERS,
        eighthNoteDurationMs = 160,
        barCount = 2,
        barGapMs = 60
    );

    companion object {
        val ALL: List<CompoundMeterDifficulty> = entries.toList()
    }
}

/**
 * 复合节拍听辨训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param targetMeter 正确的拍子类型
 * @param answerChoices 所有选项标签（含正确答案，已打乱）
 * @param correctAnswer 正确答案标签
 */
data class CompoundMeterQuestion(
    val difficulty: CompoundMeterDifficulty,
    val seed: Long,
    val targetMeter: MeterType,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(targetMeter in difficulty.meters) {
            "目标拍子 ${targetMeter.displayName} 不在难度 ${difficulty.displayName} 的拍子集合中"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(answerChoices.distinct().size == answerChoices.size) {
            "选项存在重复"
        }
        require(answerChoices.size == difficulty.choiceCount) {
            "选项数 (${answerChoices.size}) 与难度配置 (${difficulty.choiceCount}) 不一致"
        }
    }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String get() = targetMeter.teachingDescription

    /** 每个八分音符的时长。 */
    val eighthNoteDurationMs: Int get() = difficulty.eighthNoteDurationMs

    /** 播放的小节数。 */
    val barCount: Int get() = difficulty.barCount
}

/**
 * 一次答题结果。
 */
data class CompoundMeterAnswerRecord(
    val question: CompoundMeterQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
