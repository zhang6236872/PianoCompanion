package com.pianocompanion.sequencetraining

/**
 * 模进辨识训练（Sequence Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **模进（Sequence）**：将一个旋律动机（motif）在不同音高上重复，是西方音乐中
 *   最基础、最常用的旋律发展手法之一。模进让旋律在保持「同一性」的同时不断向上或
 *   向下推进，产生强烈的方向感和推动力。辨识模进是高级听觉分析能力——要求听者从
 *   连续的音符流中识别出「这一段和上一段形状一样，只是高/低了一些」。
 *
 * - **四种旋律构造类型**：
 *   - **上行模进（Ascending Sequence）**：动机在更高的音高上重复，旋律整体向上攀升。
 *   - **下行模进（Descending Sequence）**：动机在更低的音高上重复，旋律整体向下回落。
 *   - **重复（Repetition）**：动机在相同音高上重复（类似固定音型 ostinato）。
 *   - **自由进行（Free Melody）**：音符之间没有清晰的模进/重复关系，旋律自由流动。
 *
 * - **训练流程**：播放一段由动机重复构成的旋律，用户从选项中选择正确的构造类型。
 *
 * 难度分级：
 * - **初级**：上行模进 vs 下行模进（2 选项）——仅区分方向
 * - **中级**：上行 + 下行 + 自由（3 选项）——增加「无模进」识别
 * - **高级**：上行 + 下行 + 重复 + 自由（4 选项）——增加「同音重复」识别
 */

/**
 * 旋律构造类型（即本题的正确答案类别）。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param description 听感描述（用于答题后的教学反馈）
 * @param listeningHint 听辨提示（用于 UI 引导）
 */
enum class SequenceType(
    val displayName: String,
    val englishName: String,
    val description: String,
    val listeningHint: String
) {
    /**
     * 上行模进：动机在更高的音高上重复，旋律整体向上攀升，产生「上升」「推进」的听感。
     */
    ASCENDING(
        displayName = "上行模进",
        englishName = "Ascending Sequence",
        description = "上行模进：同一段旋律动机在更高的音高上重复，每次都「抬高」一些，" +
            "产生明确的上升感和推动力。这是音乐中营造紧张度增长、情绪攀升的经典手法。",
        listeningHint = "听感像「逐级往上爬」——同样的旋律形状反复出现，但一次比一次高"
    ),

    /**
     * 下行模进：动机在更低的音高上重复，旋律整体向下回落，产生「下降」「松弛」的听感。
     */
    DESCENDING(
        displayName = "下行模进",
        englishName = "Descending Sequence",
        description = "下行模进：同一段旋律动机在更低的音高上重复，每次都「降低」一些，" +
            "产生明确的下降感。常用于情绪的舒缓、收束或「阶梯式」下行。",
        listeningHint = "听感像「逐级往下走」——同样的旋律形状反复出现，但一次比一次低"
    ),

    /**
     * 重复：动机在相同音高上重复（类似固定音型），产生「锚定」「循环」的听感。
     */
    REPETITION(
        displayName = "重复",
        englishName = "Repetition",
        description = "重复：同一段旋律动机在相同音高上反复出现，没有升降，" +
            "产生锚定、循环或固定音型（ostinato）的效果。强调「同一性」而非发展。",
        listeningHint = "听感像「原地打转」——同样的旋律在完全相同的高度反复出现"
    ),

    /**
     * 自由进行：音符之间没有清晰的模进/重复关系，旋律自由流动，无规律可循。
     */
    FREE(
        displayName = "自由进行",
        englishName = "Free Melody",
        description = "自由进行：音符之间没有清晰的模进或重复关系，旋律自由流动。" +
            "听不到「同一段旋律在不同高度再现」的感觉。",
        listeningHint = "听感像「没有规律」——找不到反复出现的相同旋律形状"
    );

    companion object {
        /** 所有构造类型。 */
        val ALL: List<SequenceType> = entries.toList()

        /**
         * 按难度返回可用的构造类型集合。
         * - 初级：上行 vs 下行（最基础的方向区分）
         * - 中级：上行 + 下行 + 自由（增加「无模进」识别）
         * - 高级：全部 4 种（增加「同音重复」识别）
         */
        fun forDifficulty(difficulty: SequenceDifficulty): List<SequenceType> = when (difficulty) {
            SequenceDifficulty.BEGINNER -> listOf(ASCENDING, DESCENDING)
            SequenceDifficulty.INTERMEDIATE -> listOf(ASCENDING, DESCENDING, FREE)
            SequenceDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量）
 */
enum class SequenceDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "上行 vs 下行（2 选项）"),
    INTERMEDIATE("中级", "上行 + 下行 + 自由（3 选项）"),
    ADVANCED("高级", "上行 + 下行 + 重复 + 自由（4 选项）");

    companion object {
        val ALL: List<SequenceDifficulty> = entries.toList()
    }
}

/**
 * 模进辨识训练题目。
 *
 * @param type 正确的旋律构造类型
 * @param startMidi 第一个音符的 MIDI 音符号
 * @param startNoteName 起始音名（如 "C5", "G4"）
 * @param motifOffsets 动机的相对半音偏移序列（3 个音符，第一个为 0）
 * @param stepSemitones 模进步距（半音数）。上行为正、下行为负、重复/自由为 0
 * @param statementCount 动机重复次数（通常 3 次）
 * @param noteMidiSequence 完整旋律的 MIDI 音符号序列（依次播放）
 * @param noteDurationMs 每个音符的时长（毫秒）
 * @param difficulty 难度
 * @param answerChoices 所有选项（构造类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（构造类型显示名）
 */
data class SequenceQuestion(
    val type: SequenceType,
    val startMidi: Int,
    val startNoteName: String,
    val motifOffsets: List<Int>,
    val stepSemitones: Int,
    val statementCount: Int,
    val noteMidiSequence: List<Int>,
    val noteDurationMs: Long,
    val difficulty: SequenceDifficulty,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(motifOffsets.isNotEmpty()) { "动机偏移序列不能为空" }
        require(motifOffsets.first() == 0) { "动机第一个音的偏移必须为 0，实际 ${motifOffsets.first()}" }
        require(statementCount >= 2) { "动机至少重复 2 次，实际 $statementCount" }
        require(noteMidiSequence.isNotEmpty()) { "旋律 MIDI 序列不能为空" }
        require(noteDurationMs > 0) { "音符时长必须为正，实际 $noteDurationMs" }
        require(startMidi in MIN_MIDI..MAX_MIDI) { "起始音 MIDI 超出钢琴范围: $startMidi" }
        noteMidiSequence.forEach { midi ->
            require(midi in MIN_MIDI..MAX_MIDI) { "音符 MIDI $midi 超出钢琴范围 [21,108]" }
        }
    }

    /** 旋律音符总数。 */
    val noteCount: Int get() = noteMidiSequence.size

    /** 动机长度（音符数）。 */
    val motifLength: Int get() = motifOffsets.size

    /** 旋律总时长（毫秒）。 */
    val sequenceDurationMs: Long get() = noteDurationMs * noteCount

    /** 完整描述（如 "C5 起的上行模进"）。 */
    val fullDescription: String
        get() = "$startNoteName 起的${type.displayName}"
}

/**
 * 一次答题结果。
 */
data class SequenceAnswerRecord(
    val question: SequenceQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
