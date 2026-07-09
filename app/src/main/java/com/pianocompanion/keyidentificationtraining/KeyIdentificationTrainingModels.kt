package com.pianocompanion.keyidentificationtraining

/**
 * 调性中心辨识训练（Key Identification Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **调性（Key / Tonal Center）**：一段旋律围绕其展开的「家」音（主音），
 *   以及由此主音建立的音阶体系（大调或小调）。不同调性拥有不同的色彩和情绪。
 *
 * - **训练流程**：播放一段明确建立调性的旋律（从主音出发、覆盖音阶特征音、
 *   回归主音），用户凭听觉判断这段旋律属于哪个调。
 *
 * - **关键听觉线索**：
 *   1. 旋律的「家」（主音）——旋律起点和终点都落在主音上，产生回归感
 *   2. 音阶的色彩——大调明亮、小调忧郁
 *   3. 特征音——不同调性使用不同的升降号（如 G 大调有 F♯），这些「偏离白键」
 *      的音符是辨识调性的核心线索
 *
 * 难度分级：
 * - **初级**：3 个调（3 选项）—— C/F/G 大调，最基础的白键调与单升降号调
 * - **中级**：5 个调（5 选项）—— 增加 D 大调、B♭ 大调（双升降号）
 * - **高级**：6 个调（6 选项）—— 增加 A 小调（与 C 大调同音不同主音）
 */

/**
 * 调性类别。
 */
enum class KeyCategory(val displayName: String) {
    MAJOR("大调"),
    MINOR("小调")
}

/**
 * 可辨识的调性。
 *
 * @param displayName 中文显示名（如 "C 大调"、"A 小调"）
 * @param tonicPitchClass 主音的音高类（0-11，对应 C-B）
 * @param category 大调/小调
 * @param keySignature 调号描述（升降号数量，用于教学反馈）
 * @param description 调性色彩描述（用于答题后的教学反馈）
 */
enum class MusicKey(
    val displayName: String,
    val tonicPitchClass: Int,
    val category: KeyCategory,
    val keySignature: String,
    val description: String
) {
    C_MAJOR(
        displayName = "C 大调",
        tonicPitchClass = 0,
        category = KeyCategory.MAJOR,
        keySignature = "无升降号（全白键）",
        description = "所有大调的「母调」，全部使用白键。明亮、纯净、稳定，是初学者最先接触的调。"
    ),
    G_MAJOR(
        displayName = "G 大调",
        tonicPitchClass = 7,
        category = KeyCategory.MAJOR,
        keySignature = "1 个升号（F♯）",
        description = "比 C 大调高一个五度，有一个 F♯。明亮开朗，是吉他和管乐器最常用的调之一。"
    ),
    D_MAJOR(
        displayName = "D 大调",
        tonicPitchClass = 2,
        category = KeyCategory.MAJOR,
        keySignature = "2 个升号（F♯, C♯）",
        description = "两个升号。光辉灿烂、充满活力，弦乐器（特别是小提琴）最钟爱的调性。"
    ),
    F_MAJOR(
        displayName = "F 大调",
        tonicPitchClass = 5,
        category = KeyCategory.MAJOR,
        keySignature = "1 个降号（B♭）",
        description = "一个降号。温暖柔和、庄重抒情，圆号的天然调性，常用于宗教音乐和牧歌。"
    ),
    B_FLAT_MAJOR(
        displayName = "B♭ 大调",
        tonicPitchClass = 10,
        category = KeyCategory.MAJOR,
        keySignature = "2 个降号（B♭, E♭）",
        description = "两个降号。温暖宽阔、从容大气，管乐器和爵士乐最常用的调性之一。"
    ),
    A_MINOR(
        displayName = "A 小调",
        tonicPitchClass = 9,
        category = KeyCategory.MINOR,
        keySignature = "无升降号（关系大调为 C 大调）",
        description = "C 大调的关系小调——使用完全相同的音，但以 A 为主音。忧郁、暗淡、沉思，是大调的「阴影面」。旋律虽然用同样的音，但「家」从 C 移到了 A。"
    );

    /**
     * 返回该调的音阶音程结构（从主音开始的半音偏移，7 个音）。
     * - 大调（自然大调）：[0, 2, 4, 5, 7, 9, 11]（全-全-半-全-全-全-半）
     * - 小调（自然小调）：[0, 2, 3, 5, 7, 8, 10]（全-半-全-全-半-全-全）
     */
    fun scaleIntervals(): List<Int> = if (category == KeyCategory.MAJOR) {
        listOf(0, 2, 4, 5, 7, 9, 11)
    } else {
        listOf(0, 2, 3, 5, 7, 8, 10)
    }

    companion object {
        val ALL: List<MusicKey> = entries.toList()

        /**
         * 按难度返回可用的调性集合。
         * - 初级：3 个调 —— C/F/G 大调（白键调 + 单升降号）
         * - 中级：5 个调 —— 增加 D 大调、B♭ 大调（双升降号）
         * - 高级：6 个调 —— 增加 A 小调（关系小调，与大调同音不同主音）
         */
        fun forDifficulty(difficulty: KeyDifficulty): List<MusicKey> = when (difficulty) {
            KeyDifficulty.BEGINNER -> listOf(C_MAJOR, F_MAJOR, G_MAJOR)
            KeyDifficulty.INTERMEDIATE -> listOf(C_MAJOR, G_MAJOR, D_MAJOR, F_MAJOR, B_FLAT_MAJOR)
            KeyDifficulty.ADVANCED -> listOf(C_MAJOR, G_MAJOR, D_MAJOR, F_MAJOR, B_FLAT_MAJOR, A_MINOR)
        }
    }
}

/**
 * 旋律模式（以音阶级数表示，调性无关）。
 *
 * 所有模式都从主音（degree 0）出发并回归主音，确保旋律明确建立调性。
 * degree 0=主音, 1=2级, ..., 6=7级, 7=八度（高八度主音）。
 *
 * @param displayName 中文显示名
 * @param scaleDegrees 音阶级数序列（0-7），用于映射到具体 MIDI 音符
 */
enum class MelodyPattern(val displayName: String, val scaleDegrees: List<Int>) {
    /** 上行音阶+回归：do-re-mi-fa-sol-la-ti-do'-do（9 音，上行覆盖全音阶后回归主音） */
    ASCENDING_SCALE("上行音阶", listOf(0, 1, 2, 3, 4, 5, 6, 7, 0)),

    /** 五度往返：do-re-mi-fa-sol-fa-mi-re-do（9 音，强调主音回归） */
    FIFTH_PATTERN("五度往返", listOf(0, 1, 2, 3, 4, 3, 2, 1, 0)),

    /** 主三和弦琶音：do-mi-sol-do'-sol-mi-do（7 音，突出和弦色彩） */
    ARPEGGIO("主三和弦琶音", listOf(0, 2, 4, 7, 4, 2, 0)),

    /** 完整音阶上下行：do-re-mi-fa-sol-la-ti-do'-ti-la-sol-fa-mi-re-do（15 音，最大程度建立调性） */
    SCALE_UP_DOWN("完整音阶上下行", listOf(0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0));

    companion object {
        val ALL: List<MelodyPattern> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与调性范围）
 */
enum class KeyDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "3 个调（3 选项）· C/F/G 大调"),
    INTERMEDIATE("中级", "5 个调（5 选项）· + D/B♭ 大调"),
    ADVANCED("高级", "6 个调（6 选项）· + A 小调（关系调）");

    companion object {
        val ALL: List<KeyDifficulty> = entries.toList()
    }
}

/**
 * 调性中心辨识训练题目。
 *
 * @param key 正确的调性
 * @param tonicMidi 主音 MIDI 音符号
 * @param tonicName 主音名（如 "C", "G", "A"）
 * @param melodyPattern 旋律模式
 * @param midiNotes 旋律的 MIDI 音符号列表（按播放顺序）
 * @param difficulty 难度
 * @param answerChoices 所有选项（调性显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（调性显示名）
 */
data class KeyQuestion(
    val key: MusicKey,
    val tonicMidi: Int,
    val tonicName: String,
    val melodyPattern: MelodyPattern,
    val midiNotes: List<Int>,
    val difficulty: KeyDifficulty,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.isNotEmpty()) { "旋律不能为空" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 旋律音符数量。 */
    val noteCount: Int get() = midiNotes.size

    /** 完整描述（如 "C 大调 · 上行音阶"）。 */
    val fullDescription: String
        get() = "$displayName · ${melodyPattern.displayName}"

    /** 调性显示名（如 "C 大调"）。 */
    val displayName: String get() = key.displayName

    /** 调号描述。 */
    val keySignatureDescription: String get() = "调号：${key.keySignature}"

    /** 调性色彩描述。 */
    val colorDescription: String get() = key.description
}

/**
 * 一次答题结果。
 */
data class KeyAnswerRecord(
    val question: KeyQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswerOrNull: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
