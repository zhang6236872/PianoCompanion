package com.pianocompanion.intervalsequence

/**
 * 音程序列记忆训练数据模型（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 训练目标：用户听到一条由若干音程串联而成的旋律线后，
 * 需按顺序回忆出各段音程的类型（如「大三度 → 小三度 → 纯五度」）。
 *
 * **设计原理：**
 * - 一条「音程序列」由 [sequenceLength] 个连续音程组成，
 *   渲染为 sequenceLength+1 个音符的旋律线（后一音 = 前一音 ± 音程半音数）；
 * - 每个音程可以上行或下行，增强方向辨识；
 * - 难度决定序列长度、选项数量、可用音程集合；
 * - 题目的「正确答案」是该序列的展示字符串（如 "M3↑ m3↑ P5↑"）。
 */

/**
 * 单个音程类型（基于半音数）。
 *
 * @property semitones 半音数（0-12，相对于低音）
 * @property shortName 简称（如 "m3" = 小三度）
 * @property fullName 中文名（如 "小三度"）
 * @property englishName 英文名（如 "Minor 3rd"）
 */
enum class IntervalType(
    val semitones: Int,
    val shortName: String,
    val fullName: String,
    val englishName: String
) {
    UNISON(0, "P1", "纯一度", "Perfect Unison"),
    MINOR_2ND(1, "m2", "小二度", "Minor 2nd"),
    MAJOR_2ND(2, "M2", "大二度", "Major 2nd"),
    MINOR_3RD(3, "m3", "小三度", "Minor 3rd"),
    MAJOR_3RD(4, "M3", "大三度", "Major 3rd"),
    PERFECT_4TH(5, "P4", "纯四度", "Perfect 4th"),
    TRITONE(6, "A4", "增四度/三全音", "Tritone"),
    PERFECT_5TH(7, "P5", "纯五度", "Perfect 5th"),
    MINOR_6TH(8, "m6", "小六度", "Minor 6th"),
    MAJOR_6TH(9, "M6", "大六度", "Major 6th"),
    MINOR_7TH(10, "m7", "小七度", "Minor 7th"),
    MAJOR_7TH(11, "M7", "大七度", "Major 7th"),
    OCTAVE(12, "P8", "纯八度", "Octave");

    companion object {
        /** 从半音数查找音程类型。 */
        fun fromSemitones(semitones: Int): IntervalType =
            values().first { it.semitones == semitones }
    }
}

/**
 * 序列中的一个音程条目（含方向）。
 *
 * @param interval 音程类型
 * @param ascending true = 上行（后音高于前音），false = 下行
 */
data class IntervalEntry(
    val interval: IntervalType,
    val ascending: Boolean
) {
    /** 带方向的半音偏移（上行为正，下行为负）。 */
    val signedSemitones: Int
        get() = if (ascending) interval.semitones else -interval.semitones

    /** 展示字符串（如 "M3↑"）。 */
    val displayString: String
        get() = "${interval.shortName}${if (ascending) "↑" else "↓"}"

    /** 完整描述（如 "大三度↑"）。 */
    val fullDescription: String
        get() = "${interval.fullName}${if (ascending) "↑" else "↓"}"
}

/**
 * 一条完整音程序列。
 */
data class IntervalSequence(
    val entries: List<IntervalEntry>
) {
    /** 序列长度（音程数）。 */
    val length: Int get() = entries.size

    /** 旋律线音符数 = 音程数 + 1。 */
    val noteCount: Int get() = entries.size + 1

    /** 展示字符串（如 "M3↑ m3↑ P5↑"）。 */
    val displayString: String
        get() = entries.joinToString(" ") { it.displayString }

    init {
        require(entries.isNotEmpty()) { "序列至少需要 1 个音程" }
    }

    /**
     * 给定起始 MIDI 音高，计算旋律线全部音符的 MIDI 列表。
     * @param startMidi 起始音的 MIDI 值
     * @return 旋律线各音符的 MIDI 值列表（长度 = noteCount）
     */
    fun toMidiSequence(startMidi: Int): List<Int> {
        val result = mutableListOf(startMidi)
        var current = startMidi
        for (entry in entries) {
            current += entry.signedSemitones
            result.add(current)
        }
        return result
    }
}

/**
 * 训练难度级别。
 *
 * @property sequenceLength 音程序列长度（音程数）
 * @property choiceCount 选项数量
 * @property availableIntervals 该难度可用的音程集合
 * @property startMidi 起始 MIDI 音高
 * @property noteDurationMs 每个音符的持续时间（毫秒）
 * @property gapMs 音符间间隔（毫秒）
 * @property displayName 难度显示名称
 */
enum class IntervalSequenceDifficulty(
    val sequenceLength: Int,
    val choiceCount: Int,
    val availableIntervals: List<IntervalType>,
    val startMidi: Int,
    val noteDurationMs: Double,
    val gapMs: Double,
    val displayName: String
) {
    BEGINNER(
        sequenceLength = 3,
        choiceCount = 2,
        availableIntervals = listOf(
            IntervalType.MAJOR_2ND, IntervalType.MAJOR_3RD,
            IntervalType.PERFECT_4TH, IntervalType.PERFECT_5TH
        ),
        startMidi = 60, // C4
        noteDurationMs = 600.0,
        gapMs = 100.0,
        displayName = "初级"
    ),
    INTERMEDIATE(
        sequenceLength = 4,
        choiceCount = 3,
        availableIntervals = listOf(
            IntervalType.MINOR_2ND, IntervalType.MAJOR_2ND,
            IntervalType.MINOR_3RD, IntervalType.MAJOR_3RD,
            IntervalType.PERFECT_4TH, IntervalType.TRITONE,
            IntervalType.PERFECT_5TH
        ),
        startMidi = 60,
        noteDurationMs = 550.0,
        gapMs = 80.0,
        displayName = "中级"
    ),
    ADVANCED(
        sequenceLength = 4,
        choiceCount = 4,
        availableIntervals = listOf(
            IntervalType.MINOR_2ND, IntervalType.MAJOR_2ND,
            IntervalType.MINOR_3RD, IntervalType.MAJOR_3RD,
            IntervalType.PERFECT_4TH, IntervalType.TRITONE,
            IntervalType.PERFECT_5TH, IntervalType.MINOR_6TH,
            IntervalType.MAJOR_6TH, IntervalType.MINOR_7TH,
            IntervalType.MAJOR_7TH, IntervalType.OCTAVE
        ),
        startMidi = 60,
        noteDurationMs = 500.0,
        gapMs = 60.0,
        displayName = "高级"
    )
}

/**
 * 一道音程序列记忆题目。
 */
data class IntervalSequenceQuestion(
    val difficulty: IntervalSequenceDifficulty,
    val seed: Long,
    val targetSequence: IntervalSequence,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 序列长度。 */
    val sequenceLength: Int get() = targetSequence.length

    /** 旋律线音符 MIDI 列表。 */
    val midiNotes: List<Int>
        get() = targetSequence.toMidiSequence(difficulty.startMidi)

    init {
        require(answerChoices.size == difficulty.choiceCount) {
            "选项数 ${answerChoices.size} != 难度要求 ${difficulty.choiceCount}"
        }
        require(correctAnswer in answerChoices) {
            "正确答案不在选项中"
        }
        require(answerChoices.toSet().size == answerChoices.size) {
            "选项有重复"
        }
    }
}
