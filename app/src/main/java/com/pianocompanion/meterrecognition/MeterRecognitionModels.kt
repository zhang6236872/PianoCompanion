package com.pianocompanion.meterrecognition

/**
 * 拍号听辨训练（Meter Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **拍号听辨（Meter Recognition）**：用户听到一段循环播放的重音节拍序列，
 *   需要根据强拍/弱拍的分组模式判断出正确的拍号（2/4、3/4、4/4 等）。
 * - 与节奏听写/节奏型听辨的区别：
 *   - **节奏听写**：听 2 拍内的具体音符时值组合（四分/八分/附点）
 *   - **节奏型听辨**：识别整小节的命名节奏型名称
 *   - **拍号听辨**：关注节拍的**宏观分组**——几个拍子构成一个小节、强拍落在哪里
 *
 * 本模块支持的 6 种拍号：
 *   1. 2/4（二拍子）— 强弱，每小节 2 拍
 *   2. 3/4（三拍子/圆舞曲）— 强弱弱，每小节 3 拍
 *   3. 4/4（四拍子/常见拍）— 强弱次强弱，每小节 4 拍
 *   4. 6/8（复合二拍子）— 强弱弱次强弱弱，每小节 6 个八分音符
 *   5. 5/4（混合拍子）— 强弱弱次强弱（2+3），每小节 5 拍
 *   6. 7/8（混合拍子）— 强弱强弱强弱弱（2+2+3），每小节 7 个八分音符
 */

/**
 * 重音级别。不同级别用不同音高+音量区分，帮助用户感知节拍分组。
 *
 * @param frequency click 频率（Hz）
 * @param amplitude click 振幅（0.0-1.0）
 */
enum class AccentLevel(val frequency: Double, val amplitude: Float) {
    /** 强拍（小节起始）。最高音、最大音量。 */
    STRONG(880.0, 0.85f),

    /** 次强拍（4/4 第 3 拍、6/8 第 4 拍、5/4 第 4 拍等）。中音、中等音量。 */
    MEDIUM(740.0, 0.55f),

    /** 弱拍。低音、小音量。 */
    WEAK(660.0, 0.30f)
}

/**
 * 拍号类型。
 *
 * @param displayName 中文显示名
 * @param symbol 拍号符号（如 "2/4"）
 * @param beatsPerMeasure 每小节拍数（click 次数）
 * @param accentPattern 重音模式列表（长度 = [beatsPerMeasure]）
 * @param description 听感描述（答题后的教学反馈）
 */
enum class MeterType(
    val displayName: String,
    val symbol: String,
    val beatsPerMeasure: Int,
    val accentPattern: List<AccentLevel>,
    val description: String
) {
    TWO_FOUR(
        displayName = "二拍子 (2/4)",
        symbol = "2/4",
        beatsPerMeasure = 2,
        accentPattern = listOf(AccentLevel.STRONG, AccentLevel.WEAK),
        description = "2/4 拍：每小节 2 拍，强弱交替。进行曲、波尔卡的典型拍子，稳健而有行进感。"
    ),
    THREE_FOUR(
        displayName = "三拍子 (3/4)",
        symbol = "3/4",
        beatsPerMeasure = 3,
        accentPattern = listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK),
        description = "3/4 拍：每小节 3 拍，强弱弱。圆舞曲（华尔兹）的经典拍子，优雅旋转的感觉。"
    ),
    FOUR_FOUR(
        displayName = "四拍子 (4/4)",
        symbol = "4/4",
        beatsPerMeasure = 4,
        accentPattern = listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK),
        description = "4/4 拍：每小节 4 拍，强弱次强弱。最常用的拍号，流行乐、摇滚、爵士的首选。"
    ),
    SIX_EIGHT(
        displayName = "六八拍 (6/8)",
        symbol = "6/8",
        beatsPerMeasure = 6,
        accentPattern = listOf(
            AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK,
            AccentLevel.MEDIUM, AccentLevel.WEAK, AccentLevel.WEAK
        ),
        description = "6/8 拍：复合二拍子，两个三拍组（强-弱-弱 + 次强-弱-弱）。吉格舞曲、船歌的典型拍子，有摇摆感。"
    ),
    FIVE_FOUR(
        displayName = "五四拍 (5/4)",
        symbol = "5/4",
        beatsPerMeasure = 5,
        accentPattern = listOf(
            AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK,
            AccentLevel.MEDIUM, AccentLevel.WEAK
        ),
        description = "5/4 拍：混合拍子（2+3 分组）。Dave Brubeck《Take Five》的标志性拍子，不对称的现代爵士感。"
    ),
    SEVEN_EIGHT(
        displayName = "七八拍 (7/8)",
        symbol = "7/8",
        beatsPerMeasure = 7,
        accentPattern = listOf(
            AccentLevel.STRONG, AccentLevel.WEAK,
            AccentLevel.MEDIUM, AccentLevel.WEAK,
            AccentLevel.MEDIUM, AccentLevel.WEAK, AccentLevel.WEAK
        ),
        description = "7/8 拍：混合拍子（2+2+3 分组）。巴尔干半岛民谣、Pink Floyd《Money》的拍子，具有前进中摇摆的独特律动。"
    );

    init {
        check(accentPattern.size == beatsPerMeasure) {
            "$displayName: accentPattern 长度(${accentPattern.size}) != beatsPerMeasure($beatsPerMeasure)"
        }
    }

    companion object {
        val ALL: List<MeterType> = entries.toList()

        /** 初级拍号：基础二/三/四拍子。 */
        val BEGINNER_METERS: List<MeterType> = listOf(TWO_FOUR, THREE_FOUR, FOUR_FOUR)

        /** 中级拍号：+ 复合拍 6/8。 */
        val INTERMEDIATE_METERS: List<MeterType> = BEGINNER_METERS + SIX_EIGHT

        /**
         * 按难度返回可用拍号集合。
         * - 初级：3 种基础拍号（2/4、3/4、4/4）
         * - 中级：+ 6/8 复合拍（4 种）
         * - 高级：+ 5/4、7/8 混合拍（6 种）
         */
        fun forDifficulty(difficulty: MeterRecognitionDifficulty): List<MeterType> = when (difficulty) {
            MeterRecognitionDifficulty.BEGINNER -> BEGINNER_METERS
            MeterRecognitionDifficulty.INTERMEDIATE -> INTERMEDIATE_METERS
            MeterRecognitionDifficulty.ADVANCED -> ALL
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
enum class MeterRecognitionDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种基础拍号（3 选项）· 2/4、3/4、4/4", 3),
    INTERMEDIATE("中级", "4 种含复合拍（4 选项）· 加入 6/8", 4),
    ADVANCED("高级", "全部 6 种含混合拍（4 选项）· 加入 5/4、7/8", 4);

    companion object {
        val ALL: List<MeterRecognitionDifficulty> = entries.toList()
    }
}

/**
 * 播放速度（click 间隔）。
 *
 * @param clickIntervalMs 相邻两个 click 之间的时间间隔（毫秒）
 */
enum class MeterRecognitionTempo(val displayName: String, val clickIntervalMs: Double, val description: String) {
    SLOW("慢速", 500.0, "500ms 间隔 — 从容，便于数拍子"),
    MEDIUM("中速", 400.0, "400ms 间隔 — 自然，接近演奏速度"),
    FAST("快速", 300.0, "300ms 间隔 — 紧凑，考验快速反应");

    companion object {
        val ALL: List<MeterRecognitionTempo> = entries.toList()
    }
}

/**
 * 拍号听辨训练题目。
 *
 * @param meter 正确的拍号
 * @param difficulty 难度
 * @param tempo 播放速度
 * @param measureRepeat 小节重复播放次数（帮助用户多听几遍）
 * @param answerChoices 所有选项（拍号符号+显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class MeterRecognitionQuestion(
    val meter: MeterType,
    val difficulty: MeterRecognitionDifficulty,
    val tempo: MeterRecognitionTempo,
    val measureRepeat: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "慢速 · 3/4 三拍子"）。 */
    val fullName: String
        get() = "${tempo.displayName} · ${meter.displayName}"

    /** 每小节拍数。 */
    val beatsPerMeasure: Int get() = meter.beatsPerMeasure

    /** 总 click 次数。 */
    val totalClicks: Int get() = meter.beatsPerMeasure * measureRepeat
}

/**
 * 一次答题结果。
 */
data class MeterRecognitionAnswerRecord(
    val question: MeterRecognitionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
