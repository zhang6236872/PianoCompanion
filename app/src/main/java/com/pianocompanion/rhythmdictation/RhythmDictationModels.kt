package com.pianocompanion.rhythmdictation

/**
 * 节奏听写训练（Rhythm Dictation Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **节奏听写（Rhythm Dictation）**：用户听到一段由等高「哒」声组成的节奏序列，
 *   凭时间间距判断具体的音符时值组合（即「写出」节奏），从选项中选出正确的乐谱记谱。
 * - 与[节奏型听辨训练]的区别：节奏型听辨训练识别整小节的**命名节奏型**（四分/八分/附点等
 *   概括性感受），而节奏听写训练聚焦于**具体音符时值的精确辨认**——听到「哒哒-哒」能判断
 *   出是「八分+八分+四分」还是「四分+八分+八分」，培养将听觉节奏转化为记谱的能力。
 *
 * 本模块支持的 8 种 2 拍节奏单元（均占满 2 拍 = 半个 4/4 小节）：
 *   1. 两个四分 ♩ ♩ —— 均匀两拍
 *   2. 两个八分+四分 ♪♪♩ —— 前密后疏
 *   3. 四分+两个八分 ♩♪♪ —— 前疏后密
 *   4. 四个八分 ♪♪♪♪ —— 均匀密集
 *   5. 二分音符 𝅗𝅥 —— 长音一拍半+余韵
 *   6. 附点四分+八分 ♩.♪ —— 长-短（附点节奏）
 *   7. 八分+附点四分 ♪♩. —— 短-长（后附点）
 *   8. 八分+四分+八分 ♪♩♪ —— 切分律动
 */

/**
 * 节奏单元类型。
 *
 * [durations] 为每个音符的时值（以拍为单位），列表元素之和 = 2.0（2 拍）。
 *
 * @param displayName 中文显示名
 * @param symbol 音乐符号表示（用于 UI 显示和选项答题）
 * @param durations 各音符时值列表（拍），之和 = 2.0
 * @param description 听感描述（答题后的教学反馈）
 */
enum class RhythmCellType(
    val displayName: String,
    val symbol: String,
    val durations: List<Double>,
    val description: String
) {
    TWO_QUARTERS(
        displayName = "两个四分",
        symbol = "♩  ♩",
        durations = listOf(1.0, 1.0),
        description = "两个均匀的四分音符，稳定、对称。每一拍一个「哒」，像行进步伐。"
    ),
    EIGHTHS_QUARTER(
        displayName = "八分八分四分",
        symbol = "♪ ♪ ♩",
        durations = listOf(0.5, 0.5, 1.0),
        description = "两个八分接一个四分，前密后疏。开头紧凑，尾部拉长——像加速后停住。"
    ),
    QUARTER_EIGHTHS(
        displayName = "四分八分八分",
        symbol = "♩ ♪ ♪",
        durations = listOf(1.0, 0.5, 0.5),
        description = "一个四分接两个八分，前疏后密。开头舒缓，尾部紧凑——像慢慢加速。"
    ),
    FOUR_EIGHTHS(
        displayName = "四个八分",
        symbol = "♪ ♪ ♪ ♪",
        durations = listOf(0.5, 0.5, 0.5, 0.5),
        description = "四个均匀的八分音符，密集而流畅。比四分快一倍，像小溪流水。"
    ),
    HALF_NOTE(
        displayName = "二分音符",
        symbol = "𝅗𝅥",
        durations = listOf(2.0),
        description = "一个二分音符，占满整个 2 拍。只有一个「哒」后是长延音，最舒缓。"
    ),
    DOTTED_QUARTER_EIGHTH(
        displayName = "附点四分接八分",
        symbol = "♩. ♪",
        durations = listOf(1.5, 0.5),
        description = "附点四分音符接八分音符（长-短）。号角般的节奏感，进行曲常用。"
    ),
    EIGHTH_DOTTED_QUARTER(
        displayName = "八分接附点四分",
        symbol = "♪ ♩.",
        durations = listOf(0.5, 1.5),
        description = "八分音符接附点四分音符（短-长）。苏格兰民间舞曲的「反向附点」特征。"
    ),
    SYNCOPATED(
        displayName = "八分四分八分",
        symbol = "♪ ♩ ♪",
        durations = listOf(0.5, 1.0, 0.5),
        description = "八分-四分-八分（短-长-短）。中间的四分跨过拍点，产生切分律动感。"
    );

    /** 节奏单元包含的音符数（click 次数）。 */
    val noteCount: Int get() = durations.size

    /** 各音符时值之和（应为 2.0 = 2 拍）。 */
    val totalBeats: Double get() = durations.sum()

    init {
        // 运行时不变量：所有节奏单元占满 2 拍
        check(kotlin.math.abs(totalBeats - 2.0) < 0.001) {
            "$displayName 时值之和 = $totalBeats，应等于 2.0"
        }
    }

    companion object {
        /** 所有节奏单元（按教学顺序：先简单后复杂）。 */
        val ALL: List<RhythmCellType> = entries.toList()

        /** 初级节奏单元（4 种基础型，时值均匀、易区分）。 */
        val BEGINNER_CELLS: List<RhythmCellType> =
            listOf(TWO_QUARTERS, EIGHTHS_QUARTER, QUARTER_EIGHTHS, FOUR_EIGHTHS)

        /** 中级节奏单元（+ 二分、附点、后附点，共 7 种）。 */
        val INTERMEDIATE_CELLS: List<RhythmCellType> =
            BEGINNER_CELLS + listOf(HALF_NOTE, DOTTED_QUARTER_EIGHTH, EIGHTH_DOTTED_QUARTER)

        /**
         * 按难度返回可用的节奏单元集合。
         * - 初级：4 种基础型（两个四分/八分八分四分/四分八分八分/四个八分）
         * - 中级：+ 二分音符、附点节奏、后附点（7 种）
         * - 高级：+ 切分节奏（8 种）
         */
        fun forDifficulty(difficulty: RhythmDictationDifficulty): List<RhythmCellType> = when (difficulty) {
            RhythmDictationDifficulty.BEGINNER -> BEGINNER_CELLS
            RhythmDictationDifficulty.INTERMEDIATE -> INTERMEDIATE_CELLS
            RhythmDictationDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量）
 * @param choiceCount 该难度的选项数量
 */
enum class RhythmDictationDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "4 种基础节奏（4 选项）· 均匀时值", 4),
    INTERMEDIATE("中级", "7 种节奏含附点（4 选项）· 加入长短对比", 4),
    ADVANCED("高级", "全部 8 种含切分（5 选项）· 最高听写挑战", 5);

    companion object {
        val ALL: List<RhythmDictationDifficulty> = entries.toList()
    }
}

/**
 * 播放速度。
 *
 * @param bpm 每分钟拍数
 */
enum class RhythmDictationTempo(val displayName: String, val bpm: Int, val description: String) {
    SLOW("慢速", 72, "72 BPM — 舒缓，便于分辨长短时值"),
    MEDIUM("中速", 100, "100 BPM — 自然，接近日常演奏速度"),
    FAST("快速", 132, "132 BPM — 紧凑，考验节奏反应力");

    /** 一拍对应的毫秒数。 */
    val beatMs: Double get() = 60_000.0 / bpm

    companion object {
        val ALL: List<RhythmDictationTempo> = entries.toList()
    }
}

/**
 * 节奏听写训练题目。
 *
 * @param cell 正确的节奏单元类型
 * @param difficulty 难度
 * @param tempo 播放速度
 * @param repeatCount 节奏单元重复播放次数（帮助用户多听几遍）
 * @param answerChoices 所有选项（符号+显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（节奏单元显示名）
 */
data class RhythmDictationQuestion(
    val cell: RhythmCellType,
    val difficulty: RhythmDictationDifficulty,
    val tempo: RhythmDictationTempo,
    val repeatCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "中速 · 两个四分"）。 */
    val fullName: String
        get() = "${tempo.displayName} · ${cell.displayName}"

    /** 音符数量。 */
    val noteCount: Int get() = cell.noteCount
}

/**
 * 一次答题结果。
 */
data class RhythmDictationAnswerRecord(
    val question: RhythmDictationQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
