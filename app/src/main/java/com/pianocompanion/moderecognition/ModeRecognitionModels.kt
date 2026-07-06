package com.pianocompanion.moderecognition

/**
 * 调式识别训练（Mode Recognition Trainer）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **调式（Mode）**：一组以某个音（主音）为基础、按特定音程结构排列的音阶。
 *   西方音乐中最常用的七声音阶调式共有 7 种教会调式（伊奥尼亚=大调、爱奥利亚=自然小调、
 *   多利亚、弗利吉亚、利底亚、混合利底亚、洛克利亚），外加和声小调，共 8 种。
 * - 调式的"性格"由其独特的半音/全音分布决定，听感各异：
 *   - 大调（伊奥尼亚）：明朗、稳定
 *   - 自然小调（爱奥利亚）：忧郁、暗淡
 *   - 和声小调：带导音的小调，色彩浓烈（增二度）
 *   - 多利亚：小调但升高六级，带"上扬"色彩（爵士/民谣常用）
 *   - 弗利吉亚：小调但降二级，西班牙/弗拉门戈风味
 *   - 利底亚：大调但升四级，梦幻/悬浮感
 *   - 混合利底亚：大调但降七级，布鲁斯/摇滚常用
 *   - 洛克利亚：最暗淡、不稳定的调式（极少独立使用）
 *
 * 本训练为**听辨训练**：播放一个调式的音阶，让用户凭听觉判断是哪种调式。
 */

/**
 * 调式类型。
 *
 * [intervals] 为相对于主音的半音偏移列表（不含主音 0 和八度 12），
 * 所有调式均为七声音阶（7 个音 + 八度）。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param intervals 相对主音的半音偏移（升序，不含 0 和 12）
 * @param brightness 调性亮度（用于教学描述）：正数=偏明亮，负数=偏暗淡
 * @param description 听感描述
 */
enum class ModeType(
    val displayName: String,
    val englishName: String,
    val intervals: List<Int>,
    val brightness: Int,
    val description: String
) {
    MAJOR(
        displayName = "大调",
        englishName = "Major (Ionian)",
        intervals = listOf(2, 4, 5, 7, 9, 11),
        brightness = 0,
        description = "明朗、稳定、明亮的大调色彩（全全半全全全半）"
    ),
    NATURAL_MINOR(
        displayName = "自然小调",
        englishName = "Natural Minor (Aeolian)",
        intervals = listOf(2, 3, 5, 7, 8, 10),
        brightness = -3,
        description = "忧郁、暗淡的小调色彩（全半全全半全全）"
    ),
    HARMONIC_MINOR(
        displayName = "和声小调",
        englishName = "Harmonic Minor",
        intervals = listOf(2, 3, 5, 7, 8, 11),
        brightness = -2,
        description = "升高第七级的小调，带强烈导音倾向（含增二度）"
    ),
    DORIAN(
        displayName = "多利亚调式",
        englishName = "Dorian",
        intervals = listOf(2, 3, 5, 7, 9, 10),
        brightness = -2,
        description = "小调但升高六级，带\"上扬\"色彩（爵士/凯尔特民谣常用）"
    ),
    MIXOLYDIAN(
        displayName = "混合利底亚调式",
        englishName = "Mixolydian",
        intervals = listOf(2, 4, 5, 7, 9, 10),
        brightness = -1,
        description = "大调但降七级，布鲁斯/摇滚/爵士常用色彩"
    ),
    PHRYGIAN(
        displayName = "弗利吉亚调式",
        englishName = "Phrygian",
        intervals = listOf(1, 3, 5, 7, 8, 10),
        brightness = -4,
        description = "小调但降二级，西班牙/弗拉门戈/金属乐的异域风味"
    ),
    LYDIAN(
        displayName = "利底亚调式",
        englishName = "Lydian",
        intervals = listOf(2, 4, 6, 7, 9, 11),
        brightness = 1,
        description = "大调但升四级，梦幻、悬浮、空灵的色彩"
    ),
    LOCRIAN(
        displayName = "洛克利亚调式",
        englishName = "Locrian",
        intervals = listOf(1, 3, 5, 6, 8, 10),
        brightness = -5,
        description = "最暗淡、不稳定的调式（含减五度，极少独立使用）"
    );

    companion object {
        /** 所有调式（按教学顺序）。 */
        val ALL: List<ModeType> = entries.toList()

        /**
         * 按难度返回可用的调式集合。
         * - 初级：大调 vs 自然小调（最基础的大小调听辨）
         * - 中级：+ 多利亚、混合利底亚、和声小调（真实音乐中最常遇到的调式）
         * - 高级：+ 弗利吉亚、利底亚、洛克利亚（全部教会调式 + 和声小调）
         */
        fun forDifficulty(difficulty: ModeDifficulty): List<ModeType> = when (difficulty) {
            ModeDifficulty.BEGINNER -> listOf(MAJOR, NATURAL_MINOR)
            ModeDifficulty.INTERMEDIATE -> listOf(MAJOR, NATURAL_MINOR, DORIAN, MIXOLYDIAN, HARMONIC_MINOR)
            ModeDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param optionCount 该难度下的选项数量（= 可用调式数量）
 */
enum class ModeDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大调 vs 自然小调（2 选项）"),
    INTERMEDIATE("中级", "+ 多利亚/混合利底亚/和声小调（5 选项）"),
    ADVANCED("高级", "全部 8 种调式（8 选项）");

    companion object {
        val ALL: List<ModeDifficulty> = entries.toList()
    }
}

/**
 * 播放方向。
 */
enum class PlayMode(val displayName: String) {
    ASCENDING("上行"),
    ASCENDING_DESCENDING("上下行")
}

/**
 * 主音（音级类，0=C, 1=C♯/D♭, ..., 11=B）。
 *
 * 内部使用音级类数字表示，避免与 scale 包的枚举耦合。
 */
data class Tonic(
    val pitchClass: Int
) {
    init {
        require(pitchClass in 0..11) { "音级类必须在 0..11 范围内，实际: $pitchClass" }
    }

    /** 根据升降号偏好返回主音名（如 "C"、"D♭"、"F♯"）。 */
    fun name(preferFlats: Boolean): String = PC_NAME(if (preferFlats) FLAT_NAMES else SHARP_NAMES)

    private fun PC_NAME(names: List<String>): String = names[pitchClass]

    companion object {
        /** 升号记法（默认）。 */
        val SHARP_NAMES: List<String> = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

        /** 降号记法。 */
        val FLAT_NAMES: List<String> = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

        /** 12 个主音。 */
        val ALL: List<Tonic> = (0..11).map { Tonic(it) }

        /**
         * 是否偏好降号记法（根据五度圈惯例：F/B♭/E♭/A♭/D♭/G♭ 调使用降号）。
         */
        fun preferFlats(pitchClass: Int): Boolean = pitchClass in setOf(1, 3, 5, 6, 8, 10)
    }
}

/**
 * 调式识别训练题目。
 *
 * @param mode 正确的调式类型
 * @param tonic 主音
 * @param difficulty 难度
 * @param playMode 播放方向
 * @param ascendingMidiNotes 上行 MIDI 音符序列（含主音和八度主音）
 * @param descendingMidiNotes 下行 MIDI 音符序列（含八度主音和主音），可为空（仅上行时）
 * @param answerChoices 所有选项（含正确答案，已打乱）
 * @param correctAnswer 正确答案文本（调式显示名）
 */
data class ModeQuestion(
    val mode: ModeType,
    val tonic: Tonic,
    val difficulty: ModeDifficulty,
    val playMode: PlayMode,
    val ascendingMidiNotes: List<Int>,
    val descendingMidiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整名称（如 "C 大调"、"D♭ 多利亚调式"）。 */
    val fullName: String
        get() = "${tonic.name(Tonic.preferFlats(tonic.pitchClass))} ${mode.displayName}"

    /** 音符数量（上行）。 */
    val noteCount: Int get() = ascendingMidiNotes.size
}

/**
 * 一次答题结果。
 */
data class ModeAnswerRecord(
    val question: ModeQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
