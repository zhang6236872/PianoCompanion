package com.pianocompanion.cadencetraining

/**
 * 终止式听辨训练（Cadence Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **终止式（Cadence）**：乐句或乐曲结尾的和弦进行，标志着音乐的停顿或结束。
 *   不同的终止式产生不同的「结束感」——从完全解决到悬念悬挂，是音乐语法的标点符号。
 *
 * - **四种核心终止式**：
 *   - **完全正格终止（Perfect Authentic Cadence, PAC）**：V→I，两个和弦均为根音位置，
 *     高音声部解决到主音。最强烈的「句号」感，古典音乐乐章结尾的标准终止。
 *   - **变格终止（Plagal Cadence, PC）**：IV→I，即「阿门终止」。温和、庄重的结束感，
 *     赞美诗结尾常用。
 *   - **半终止（Half Cadence, HC）**：任意和弦→V，结束在属和弦上。产生「逗号」或
 *     「问号」的悬而未决感，乐句中间停顿常用。
 *   - **伪终止（Deceptive Cadence, DC）**：V→vi，属和弦本应解决到 I，却意外进行到
 *     vi。产生「惊喜」或「转折」效果。
 *
 * - **训练流程**：依次播放两个和弦（和弦进行），用户从选项中选择正确的终止式类型。
 *
 * 难度分级：
 * - **初级**：完全正格终止 vs 变格终止（2 选项）——区分「强烈结束」与「温和结束」
 * - **中级**：完全正格 + 变格 + 半终止（3 选项）——增加「未解决」悬停感
 * - **高级**：全部 4 种（4 选项）——增加「意外转折」识别
 */

/**
 * 调内和弦功能（罗马数字分析）。
 *
 * @param romanNumeral 罗马数字标记（如 "I", "IV", "V", "vi"）
 * @param intervalsFromTonic 相对于主音的半音偏移列表（根音位置三和弦/七和弦）
 * @param displayName 中文名（用于 UI 显示）
 */
enum class ChordFunction(
    val romanNumeral: String,
    val intervalsFromTonic: List<Int>,
    val displayName: String
) {
    /** 主和弦（Tonic）：调性中心，稳定。 */
    I("I", listOf(0, 4, 7), "主和弦"),

    /** 上主和弦（Supertonic）：导向属功能。 */
    II("ii", listOf(2, 5, 9), "上主和弦"),

    /** 下属和弦（Subdominant）：变格终止的起始和弦。 */
    IV("IV", listOf(5, 9, 12), "下属和弦"),

    /** 属和弦（Dominant）：不稳定，强烈倾向解决到主和弦。 */
    V("V", listOf(7, 11, 14), "属和弦"),

    /** 属七和弦（Dominant Seventh）：更紧张的属功能，增加小七度音。 */
    V7("V7", listOf(7, 11, 14, 17), "属七和弦"),

    /** 下中和弦（Submediant）：伪终止的目标和弦。 */
    VI("vi", listOf(9, 12, 16), "下中和弦");

    /** 该和弦功能的音符数量。 */
    val noteCount: Int get() = intervalsFromTonic.size

    /**
     * 根据主音 MIDI 音符号构建该和弦功能的 MIDI 音符列表。
     *
     * @param tonicMidi 主音 MIDI 音符号
     * @return 和弦各音的 MIDI 音符号列表（已钳制到钢琴范围 [21, 108]）
     */
    fun buildMidiNotes(tonicMidi: Int): List<Int> =
        intervalsFromTonic.map { (tonicMidi + it).coerceIn(MIN_MIDI, MAX_MIDI) }
}

/**
 * 终止式类型。
 *
 * @param displayName 中文显示名
 * @param abbreviation 英文缩写（如 "PAC", "PC", "HC", "DC"）
 * @param progression 和弦进行（按播放顺序的和弦功能列表）
 * @param resolutionLabel 解决感标签（用于 UI 色彩区分）
 * @param description 听感描述（用于答题后的教学反馈）
 */
enum class CadenceType(
    val displayName: String,
    val abbreviation: String,
    val progression: List<ChordFunction>,
    val resolutionLabel: String,
    val description: String
) {
    PERFECT_AUTHENTIC(
        displayName = "完全正格终止",
        abbreviation = "PAC",
        progression = listOf(ChordFunction.V, ChordFunction.I),
        resolutionLabel = "完全解决",
        description = "V→I，最强烈的「句号」感。属和弦到主和弦的完全解决，古典乐章结尾的标准终止。"
    ),
    PLAGAL(
        displayName = "变格终止",
        abbreviation = "PC",
        progression = listOf(ChordFunction.IV, ChordFunction.I),
        resolutionLabel = "温和结束",
        description = "IV→I，即「阿门终止」。下属和弦到主和弦，温和庄重的结束感，赞美诗结尾常用。"
    ),
    HALF(
        displayName = "半终止",
        abbreviation = "HC",
        progression = listOf(ChordFunction.I, ChordFunction.V),
        resolutionLabel = "悬而未决",
        description = "→V，结束在属和弦上。产生「逗号」或「问号」的悬停感，乐句中间停顿常用。"
    ),
    DECEPTIVE(
        displayName = "伪终止",
        abbreviation = "DC",
        progression = listOf(ChordFunction.V, ChordFunction.VI),
        resolutionLabel = "意外转折",
        description = "V→vi，属和弦本应解决到 I 却意外进行到 vi。产生「惊喜」或「转折」效果。"
    );

    /** 和弦数量（进行中的和弦数）。 */
    val chordCount: Int get() = progression.size

    /** 进行的罗马数字标记（如 "V → I"）。 */
    val progressionLabel: String get() = progression.joinToString(" → ") { it.romanNumeral }

    companion object {
        /** 所有终止式类型。 */
        val ALL: List<CadenceType> = entries.toList()

        /**
         * 按难度返回可用的终止式类型集合。
         * - 初级：完全正格 vs 变格（最基础的两种「结束」终止式）
         * - 中级：完全正格 + 变格 + 半终止（增加悬停感识别）
         * - 高级：全部 4 种（增加意外转折识别）
         */
        fun forDifficulty(difficulty: CadenceDifficulty): List<CadenceType> = when (difficulty) {
            CadenceDifficulty.BEGINNER -> listOf(PERFECT_AUTHENTIC, PLAGAL)
            CadenceDifficulty.INTERMEDIATE -> listOf(PERFECT_AUTHENTIC, PLAGAL, HALF)
            CadenceDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param description 该难度的说明（含选项数量）
 */
enum class CadenceDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "完全正格 vs 变格（2 选项）"),
    INTERMEDIATE("中级", "完全正格 + 变格 + 半终止（3 选项）"),
    ADVANCED("高级", "全部 4 种终止式（4 选项）");

    companion object {
        val ALL: List<CadenceDifficulty> = entries.toList()
    }
}

/**
 * 终止式听辨训练题目。
 *
 * @param type 正确的终止式类型
 * @param tonicMidi 主音 MIDI 音符号（决定调性）
 * @param tonicName 主音名（如 "C", "G"）
 * @param difficulty 难度
 * @param chordProgression 和弦进行（每个元素为该和弦的 MIDI 音符列表，按播放顺序）
 * @param answerChoices 所有选项（终止式类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（终止式类型显示名）
 */
data class CadenceQuestion(
    val type: CadenceType,
    val tonicMidi: Int,
    val tonicName: String,
    val difficulty: CadenceDifficulty,
    val chordProgression: List<List<Int>>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(chordProgression.isNotEmpty()) { "和弦进行不能为空" }
        require(tonicMidi in MIN_MIDI..MAX_MIDI) { "主音 MIDI 超出钢琴范围: $tonicMidi" }
    }

    /** 完整描述（如 "C 大调: V → I 完全正格终止"）。 */
    val fullDescription: String
        get() = "$tonicName 大调: ${type.progressionLabel} ${type.displayName}"

    /** 和弦总数。 */
    val totalChordCount: Int get() = chordProgression.size

    /** 各和弦的音符总数列表（用于教学信息）。 */
    val chordNoteCounts: List<Int> get() = chordProgression.map { it.size }

    /** 进行中每个和弦的罗马数字（如 ["V", "I"]）。 */
    val romanNumerals: List<String> get() = type.progression.map { it.romanNumeral }
}

/**
 * 一次答题结果。
 */
data class CadenceAnswerRecord(
    val question: CadenceQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
private const val MIN_MIDI = 21
private const val MAX_MIDI = 108
