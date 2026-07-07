package com.pianocompanion.rhythmpattern

/**
 * 节奏型听辨训练（Rhythm Pattern Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **节奏型（Rhythm Pattern）**：一小节（4/4 拍 = 4 拍）内音符时值的排列组合。
 *   不同的节奏型产生不同的"节奏感"——如进行曲般的均匀四分、跳跃的附点节奏、
 *   摇摆的三连音、慵懒的切分等。听辨训练的核心是听出一段节奏的时值结构。
 * - **听辨训练原理**：播放一段由等高"哒"声（click）组成的节奏序列，用户凭借
 *   各 click 之间的时间间距来判断节奏型类型。所有 click 音高和音量相同，
 *   唯一区分依据是**时间**——这与和弦/调式听辨（区分音高色彩）完全互补。
 *
 * 本模块支持的 8 种节奏型（均占满 4/4 一小节 = 4 拍）：
 *   1. 四分音符 —— 4 个均匀的四分音符，行进感
 *   2. 八分音符 —— 8 个均匀的八分音符，密集流动
 *   3. 二分音符 —— 2 个二分音符，缓慢舒展
 *   4. 附点节奏 —— 附点四分+八分，长短交替（进行曲/号角感）
 *   5. 后附点   —— 八分+附点四分，短长交替（苏格兰切分/苏格兰顿挫）
 *   6. 切分节奏 —— 重音落在弱拍上，爵士/流行常用
 *   7. 三连音   —— 每两拍三等分，摇摆感
 *   8. 混合八分 —— 四分+两个八分重复，奔马感
 */

/**
 * 节奏型类型。
 *
 * [durations] 为每个音符的时值（以拍为单位），列表元素之和 = 4.0（一小节 4/4）。
 * 例如四分音符 = `[1.0, 1.0, 1.0, 1.0]`，附点节奏 = `[1.5, 0.5, 1.5, 0.5]`。
 *
 * @param displayName 中文显示名
 * @param symbol 简谱/符号表示（用于 UI 辅助显示）
 * @param durations 各音符时值列表（拍），之和 = 4.0
 * @param description 听感描述（答题后的教学反馈）
 */
enum class RhythmPatternType(
    val displayName: String,
    val symbol: String,
    val durations: List<Double>,
    val description: String
) {
    QUARTERS(
        displayName = "四分音符",
        symbol = "♩ ♩ ♩ ♩",
        durations = listOf(1.0, 1.0, 1.0, 1.0),
        description = "4 个均匀的四分音符，稳定而有行进感，像军队行进或节拍器。"
    ),
    EIGHTHS(
        displayName = "八分音符",
        symbol = "♪ ♪ ♪ ♪ ♪ ♪ ♪ ♪",
        durations = listOf(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5),
        description = "8 个均匀的八分音符，密集而流动，像小溪潺潺。"
    ),
    HALVES(
        displayName = "二分音符",
        symbol = "𝅗𝅥 𝅗𝅥",
        durations = listOf(2.0, 2.0),
        description = "2 个二分音符，缓慢而舒展，气息悠长。"
    ),
    DOTTED(
        displayName = "附点节奏",
        symbol = "♩. ♪ ♩. ♪",
        durations = listOf(1.5, 0.5, 1.5, 0.5),
        description = "附点四分音符 + 八分音符，长-短交替。明亮而有号角感，进行曲常用。"
    ),
    SCOTCH_SNAP(
        displayName = "后附点",
        symbol = "♪ ♩. ♪ ♩.",
        durations = listOf(0.5, 1.5, 0.5, 1.5),
        description = "八分音符 + 附点四分音符，短-长交替。轻快而跳跃，苏格兰民间舞曲特征。"
    ),
    SYNCOPATION(
        displayName = "切分节奏",
        symbol = "♪ ♩ ♪ ♪ ♩ ♪",
        durations = listOf(0.5, 1.0, 0.5, 0.5, 1.0, 0.5),
        description = "重音落在弱拍或弱位上，产生「错位」的律动感。爵士、放克、流行音乐核心。"
    ),
    TRIPLETS(
        displayName = "三连音",
        symbol = "𝅘𝅥𝅮 𝅘𝅥𝅮 𝅘𝅥𝅮 ×2",
        durations = listOf(2.0 / 3.0, 2.0 / 3.0, 2.0 / 3.0, 2.0 / 3.0, 2.0 / 3.0, 2.0 / 3.0),
        description = "每两拍均分为三等分，产生摇摆、圆舞般的律动感。蓝调、爵士的灵魂。"
    ),
    MIXED_EIGHTHS(
        displayName = "混合八分",
        symbol = "♩ ♪ ♪ ♩ ♪ ♪",
        durations = listOf(1.0, 0.5, 0.5, 1.0, 0.5, 0.5),
        description = "四分音符 + 两个八分音符重复，产生奔马般的节奏感。古典、流行广泛使用。"
    );

    /** 节奏型包含的音符数（即 click 次数）。 */
    val noteCount: Int get() = durations.size

    /** 各音符时值之和（应为 4.0 = 一小节）。 */
    val totalBeats: Double get() = durations.sum()

    init {
        // 运行时不变量：所有节奏型占满一小节（4 拍），容许浮点误差
        check(kotlin.math.abs(totalBeats - 4.0) < 0.01) {
            "$displayName 时值之和 = $totalBeats，应等于 4.0"
        }
    }

    companion object {
        /** 所有节奏型（按教学顺序：先简单后复杂）。 */
        val ALL: List<RhythmPatternType> = entries.toList()

        /** 初级节奏型（4 种基础型，最易区分）。 */
        val BEGINNER_PATTERNS: List<RhythmPatternType> =
            listOf(QUARTERS, EIGHTHS, HALVES, DOTTED)

        /** 中级节奏型（6 种，加入后附点和切分）。 */
        val INTERMEDIATE_PATTERNS: List<RhythmPatternType> =
            BEGINNER_PATTERNS + listOf(SCOTCH_SNAP, SYNCOPATION)

        /**
         * 按难度返回可用的节奏型集合。
         * - 初级：4 种基础型（四分/八分/二分/附点）
         * - 中级：+ 后附点、切分（6 种）
         * - 高级：全部 8 种（含三连音、混合八分）
         */
        fun forDifficulty(difficulty: RhythmDifficulty): List<RhythmPatternType> = when (difficulty) {
            RhythmDifficulty.BEGINNER -> BEGINNER_PATTERNS
            RhythmDifficulty.INTERMEDIATE -> INTERMEDIATE_PATTERNS
            RhythmDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量）
 */
enum class RhythmDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "4 种基础节奏型（4 选项）"),
    INTERMEDIATE("中级", "6 种节奏型含切分（6 选项）"),
    ADVANCED("高级", "全部 8 种节奏型含三连音（8 选项）");

    companion object {
        val ALL: List<RhythmDifficulty> = entries.toList()
    }
}

/**
 * 播放速度。
 *
 * 不同速度下同一节奏型的听感差异很大；快速更考验反应，慢速更考验时值判断。
 *
 * @param bpm 每分钟拍数
 */
enum class RhythmTempo(val displayName: String, val bpm: Int, val description: String) {
    SLOW("慢速", 80, "80 BPM — 舒缓，便于分辨长短时值"),
    FAST("快速", 140, "140 BPM — 紧凑，考验节奏反应");

    /** 一拍对应的毫秒数。 */
    val beatMs: Double get() = 60_000.0 / bpm

    companion object {
        val ALL: List<RhythmTempo> = entries.toList()
    }
}

/**
 * 节奏型听辨训练题目。
 *
 * @param type 正确的节奏型类型
 * @param difficulty 难度
 * @param tempo 播放速度
 * @param repeatCount 节奏型重复播放次数（帮助用户多听几遍）
 * @param answerChoices 所有选项（含正确答案，已打乱）
 * @param correctAnswer 正确答案文本（节奏型显示名）
 */
data class RhythmPatternQuestion(
    val type: RhythmPatternType,
    val difficulty: RhythmDifficulty,
    val tempo: RhythmTempo,
    val repeatCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "中速 · 附点节奏"）。 */
    val fullName: String
        get() = "${tempo.displayName} · ${type.displayName}"

    /** 音符数量。 */
    val noteCount: Int get() = type.noteCount
}

/**
 * 一次答题结果。
 */
data class RhythmPatternAnswerRecord(
    val question: RhythmPatternQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
