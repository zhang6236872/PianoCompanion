package com.pianocompanion.scaletraining

/**
 * 音阶听辨训练（Scale Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **音阶（Scale）**：按特定音程模式排列的一组音符序列。不同的音阶拥有截然不同的
 *   「色彩」和「情感」——大调明亮欢快、小调忧郁悲伤、五声调空灵东方、和声小调异域风情。
 *
 * - **六种核心音阶**：
 *   - **大调音阶（Major Scale）**：全全半全全全半，最明亮、最稳定的「快乐」色彩。
 *   - **自然小调（Natural Minor）**：全半全全半全全，忧郁悲伤的「悲伤」色彩。
 *   - **和声小调（Harmonic Minor）**：自然小调升高第 VII 级，产生「异域/东方」色彩
 *     和增二度（VI→VII）的奇异张力。
 *   - **旋律小调（Melodic Minor）**：上行升高第 VI 和 VII 级，平滑的「爵士」色彩，
 *     兼具小调的情感和大调的明亮上行。
 *   - **五声大调（Major Pentatonic）**：去掉半音音程的 5 音音阶，空灵、纯净的
 *     「东方/乡村」色彩，无任何半音。
 *   - **五声小调（Minor Pentatonic）**：布鲁斯和摇滚的基础，深沉有力的「蓝调」色彩。
 *
 * - **训练流程**：播放一段音阶（上行或下行），用户从选项中选择正确的音阶类型。
 *
 * 难度分级：
 * - **初级**：大调 vs 自然小调（2 选项）——最基础的「明亮 vs 忧郁」色彩区分
 * - **中级**：大调 + 自然小调 + 和声小调 + 五声大调（4 选项）——增加异域色彩和五声调
 * - **高级**：全部 6 种（6 选项）——增加旋律小调和五声小调
 */

/** 音阶播放方向。 */
enum class ScaleDirection(val displayName: String) {
    ASCENDING("上行"),
    DESCENDING("下行")
}

/**
 * 音阶类型。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param intervals 从主音开始的半音偏移列表（上行八度内，包含八度音）
 * @param intervalPattern 音程模式描述（全/半/增二度标记，用于教学）
 * @param colorDescription 色彩听感描述（用于答题后的教学反馈）
 */
enum class ScaleType(
    val displayName: String,
    val englishName: String,
    val intervals: List<Int>,
    val intervalPattern: String,
    val colorDescription: String
) {
    MAJOR(
        displayName = "大调音阶",
        englishName = "Major",
        intervals = listOf(0, 2, 4, 5, 7, 9, 11, 12),
        intervalPattern = "全-全-半-全-全-全-半",
        colorDescription = "最明亮、最稳定的音阶。「快乐」的色彩，西方音乐的基础。全全半全全全半的模式令人感到安定和满足。"
    ),
    NATURAL_MINOR(
        displayName = "自然小调",
        englishName = "Natural Minor",
        intervals = listOf(0, 2, 3, 5, 7, 8, 10, 12),
        intervalPattern = "全-半-全-全-半-全-全",
        colorDescription = "忧郁、悲伤的音阶。与大调相比，第 III、VI、VII 级降低，带来「忧伤」的色彩。流行音乐常用。"
    ),
    HARMONIC_MINOR(
        displayName = "和声小调",
        englishName = "Harmonic Minor",
        intervals = listOf(0, 2, 3, 5, 7, 8, 11, 12),
        intervalPattern = "全-半-全-全-半-增二度-半",
        colorDescription = "自然小调升高第 VII 级。产生「异域/东方」色彩，VI→VII 的增二度（1.5 个全音）带来奇异而紧张的张力。"
    ),
    MELODIC_MINOR(
        displayName = "旋律小调",
        englishName = "Melodic Minor",
        intervals = listOf(0, 2, 3, 5, 7, 9, 11, 12),
        intervalPattern = "全-半-全-全-全-全-半",
        colorDescription = "上行升高第 VI 和 VII 级。兼具小调的情感和大调的明亮，平滑的「爵士」色彩，爵士乐的基石。"
    ),
    MAJOR_PENTATONIC(
        displayName = "五声大调",
        englishName = "Major Pentatonic",
        intervals = listOf(0, 2, 4, 7, 9, 12),
        intervalPattern = "全-全-小三度-全-小三度",
        colorDescription = "去掉半音的 5 音音阶。空灵、纯净，无任何半音摩擦。「东方/乡村」色彩，中国民乐和乡村音乐常用。"
    ),
    MINOR_PENTATONIC(
        displayName = "五声小调",
        englishName = "Minor Pentatonic",
        intervals = listOf(0, 3, 5, 7, 10, 12),
        intervalPattern = "小三度-全-全-小三度-全",
        colorDescription = "深沉有力的 5 音音阶。布鲁斯和摇滚的基础，「蓝调」色彩。与大调五声调共用相同音符但以不同音为主音。"
    );

    /** 音阶的音符数量（不含重复的八度音）。 */
    val noteCount: Int get() = intervals.size - 1

    companion object {
        /** 所有音阶类型。 */
        val ALL: List<ScaleType> = entries.toList()

        /**
         * 按难度返回可用的音阶类型集合。
         * - 初级：大调 vs 自然小调（最基础的明亮 vs 忧郁）
         * - 中级：大调 + 自然小调 + 和声小调 + 五声大调（增加异域色彩和五声调）
         * - 高级：全部 6 种（增加旋律小调和五声小调）
         */
        fun forDifficulty(difficulty: ScaleDifficulty): List<ScaleType> = when (difficulty) {
            ScaleDifficulty.BEGINNER -> listOf(MAJOR, NATURAL_MINOR)
            ScaleDifficulty.INTERMEDIATE -> listOf(MAJOR, NATURAL_MINOR, HARMONIC_MINOR, MAJOR_PENTATONIC)
            ScaleDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量）
 */
enum class ScaleDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大调 vs 自然小调（2 选项）"),
    INTERMEDIATE("中级", "大调 + 小调 + 和声小调 + 五声大调（4 选项）"),
    ADVANCED("高级", "全部 6 种音阶（6 选项）");

    companion object {
        val ALL: List<ScaleDifficulty> = entries.toList()
    }
}

/**
 * 音阶听辨训练题目。
 *
 * @param type 正确的音阶类型
 * @param tonicMidi 主音 MIDI 音符号（决定调性）
 * @param tonicName 主音名（如 "C", "G"）
 * @param difficulty 难度
 * @param direction 播放方向（上行/下行）
 * @param midiNotes 音阶的 MIDI 音符号列表（按播放顺序）
 * @param answerChoices 所有选项（音阶类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（音阶类型显示名）
 */
data class ScaleQuestion(
    val type: ScaleType,
    val tonicMidi: Int,
    val tonicName: String,
    val difficulty: ScaleDifficulty,
    val direction: ScaleDirection,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.isNotEmpty()) { "音阶音符不能为空" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 大调音阶（上行）"）。 */
    val fullDescription: String
        get() = "$tonicName ${type.displayName}（${direction.displayName}）"

    /** 音阶音符数量。 */
    val noteCount: Int get() = midiNotes.size

    /** 音程模式。 */
    val intervalPattern: String get() = type.intervalPattern
}

/**
 * 一次答题结果。
 */
data class ScaleAnswerRecord(
    val question: ScaleQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
