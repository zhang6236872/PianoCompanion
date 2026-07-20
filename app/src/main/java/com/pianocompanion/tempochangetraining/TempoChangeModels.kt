package com.pianocompanion.tempochangetraining

/**
 * 速度变化方向辨识训练（Tempo Change Direction / Accelerando-Ritardando Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **速度（Tempo）**：音乐的快慢（拍频）。静态速度用速度术语表示（慢板/行板/快板…，
 *   见 [com.pianocompanion.tempotraining]）。
 * - **速度变化方向（Tempo Change Direction）**：速度随时间变化的「走势」，是音乐表情的
 *   核心维度之一（与「力度变化方向」[com.pianocompanion.dynamicsdirectiontraining] 互补）。
 *   本训练专注于辨识这种速度「走势」，而非单一的静态速度级别。
 *
 * 5 种速度变化方向（辨识目标）：
 * - **渐快 Accelerando (accel.)**：速度逐渐变快 —— 越走越急、推向高潮、积聚张力。
 * - **渐慢 Ritardando (rit.)**：速度逐渐变慢 —— 收束、舒缓、结束前的减速。
 * - **稳定 A tempo (=)**：速度保持不变 —— 无明显变化，作为参照。
 * - **渐快渐慢 Accel.-Rit. (⌒)**：先变快后变慢 —— 形成速度的「山丘」（一句之中先推进后放松）。
 * - **渐慢渐快 Rit.-Accel. (⌣)**：先变慢后变快 —— 形成速度的「山谷」（拉伸后重新加速）。
 *
 * 训练流程：
 * 1. 播放一段由若干音符组成的短句，音符之间的**时间间距**按某种「速度方向」走势变化
 * 2. 用户聆听后，判断这段音乐的速度是哪种走势
 * 3. 从选项中选出正确答案
 *
 * 音频设计要点：每个音符的音高与响度保持固定（避免音高/力度变化成为干扰），唯一变化的
 * 是相邻音符的起音时间间距（inter-onset interval），使「速度变化」成为唯一显著特征。
 */
/**
 * 速度变化方向（辨识目标）。
 *
 * @param symbol 记号符号（如「accel.」「rit.」「⌒」）
 * @param displayName 中文名（如「渐快」）
 * @param englishName 意大利语/英文名（如「Accelerando」）
 * @param description 详细描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class TempoChange(
    val symbol: String,
    val displayName: String,
    val englishName: String,
    val description: String,
    val hint: String
) {
    ACCELERANDO(
        symbol = "accel.",
        displayName = "渐快",
        englishName = "Accelerando",
        description = "渐快（Accelerando, accel.）：速度逐渐变快，像越走越急、或情绪不断高涨。" +
            "常用于推向高潮、制造紧张感或结尾的冲刺。",
        hint = "音符越走越快，一个接一个越来越急，像有人在加快脚步"
    ),
    RITARDANDO(
        symbol = "rit.",
        displayName = "渐慢",
        englishName = "Ritardando",
        description = "渐慢（Ritardando, rit.）：速度逐渐变慢，像渐渐放慢脚步、或缓缓收束。" +
            "常用于乐句/乐曲结尾、制造庄重感或情绪的平息。",
        hint = "音符越走越慢，之间的间隔越来越大，像有人在放慢脚步"
    ),
    STEADY(
        symbol = "a tempo",
        displayName = "稳定",
        englishName = "A Tempo",
        description = "速度稳定（a tempo / 常态）：每个音之间的时间间隔从头到尾保持一致，没有明显变化。" +
            "这是「没有渐快渐慢」的参照，用来与有变化的选项对比。",
        hint = "每个音之间的间隔都差不多，从头到尾节拍稳定不变"
    ),
    ACCEL_RIT(
        symbol = "⌒",
        displayName = "渐快渐慢",
        englishName = "Accel.-Rit.",
        description = "先渐快再渐慢（Accel.-Rit. / Push & Relax, ⌒）：速度先变快后变慢，形成一座「山丘」。" +
            "这是乐句常见的呼吸方式——中间最紧凑，两端较舒展。",
        hint = "先越来越快，到达中间后又越来越慢，像一座山丘，中间最急"
    ),
    RIT_ACCEL(
        symbol = "⌣",
        displayName = "渐慢渐快",
        englishName = "Rit.-Accel.",
        description = "先渐慢再渐快（Rit.-Accel., ⌣）：速度先变慢后变快，形成一个「山谷」。" +
            "较少见，制造先拖住、再重新起步加速的感觉。",
        hint = "先越来越慢，到达中间后又越来越快，像一个山谷，中间最慢"
    );

    /** 完整标签（如「渐快（Accelerando）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 带符号的标签（如「accel. 渐快」）。 */
    val symbolLabel: String get() = "$symbol $displayName"

    companion object {
        val ALL: List<TempoChange> = entries.toList()

        /** 初级难度候选：渐快 vs 渐慢（最根本的二元速度方向对比）。 */
        val BEGINNER_DIRECTIONS: List<TempoChange> = listOf(ACCELERANDO, RITARDANDO)

        /** 中级难度候选：渐快/渐慢/稳定（增加「无变化」参照）。 */
        val INTERMEDIATE_DIRECTIONS: List<TempoChange> = listOf(ACCELERANDO, RITARDANDO, STEADY)

        /** 高级难度候选：全部 5 种方向（含双向山丘/山谷）。 */
        val ADVANCED_DIRECTIONS: List<TempoChange> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param directions 该难度可用的速度方向候选集
 * @param choiceCount 该难度的选项数量
 */
enum class TempoChangeDifficulty(
    val displayName: String,
    val description: String,
    val directions: List<TempoChange>,
    val choiceCount: Int
) {
    BEGINNER(
        "初级",
        "渐快 vs 渐慢 · 2 选项 · 最基础的速度方向",
        TempoChange.BEGINNER_DIRECTIONS,
        2
    ),
    INTERMEDIATE(
        "中级",
        "+ 稳定 · 3 选项 · 增加「无变化」参照",
        TempoChange.INTERMEDIATE_DIRECTIONS,
        3
    ),
    ADVANCED(
        "高级",
        "+ 渐快渐慢 / 渐慢渐快 · 5 选项 · 双向山丘/山谷",
        TempoChange.ADVANCED_DIRECTIONS,
        5
    );

    companion object {
        val ALL: List<TempoChangeDifficulty> = entries.toList()
    }
}

/**
 * 速度变化方向辨识训练题目。
 *
 * @param direction 正确的速度方向
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param tonicMidi 旋律起始主音的 MIDI 音高（旋律围绕该音变化）
 * @param answerChoices 所有选项（速度方向完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class TempoChangeQuestion(
    val direction: TempoChange,
    val difficulty: TempoChangeDifficulty,
    val seed: Long,
    val tonicMidi: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${direction.displayName}（${direction.englishName} ${direction.symbol}）"

    init {
        require(tonicMidi in MIN_TONIC_MIDI..MAX_TONIC_MIDI) {
            "主音 MIDI $tonicMidi 超出范围 [$MIN_TONIC_MIDI, $MAX_TONIC_MIDI]"
        }
        require(direction in difficulty.directions) {
            "速度方向 ${direction.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
        require(correctAnswer in answerChoices) {
            "正确答案不在选项中"
        }
    }
}

/**
 * 一次答题结果。
 */
data class TempoChangeAnswerRecord(
    val question: TempoChangeQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 主音音域常量（C4=60 到 G4=67，白键起始音）。 */
const val MIN_TONIC_MIDI: Int = 60
const val MAX_TONIC_MIDI: Int = 67
