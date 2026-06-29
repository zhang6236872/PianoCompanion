package com.pianocompanion.training

/**
 * 听音训练（Ear Training）数据模型。
 *
 * 本文件包含所有与听音训练相关的音乐理论和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **音程（Interval）**：两个音之间的距离，如大三度、纯五度等
 * - **和弦（Chord）**：三个或更多音同时发声的组合，如大三和弦、属七和弦等
 * - **音阶（Scale）**：按音高排列的一组音，如大调、自然小调等
 */

/**
 * 练习类型。
 * - [INTERVAL] 音程识别：听两个音，判断音程名称
 * - [CHORD] 和弦识别：听和弦，判断和弦类型
 * - [SCALE] 音阶识别：听音阶，判断音阶类型
 */
enum class ExerciseType(val displayName: String) {
    INTERVAL("音程"),
    CHORD("和弦"),
    SCALE("音阶");

    companion object {
        val ALL = listOf(INTERVAL, CHORD, SCALE)
    }
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：少量选项，容易区分
 * - [INTERMEDIATE] 中级：增加更多选项
 * - [ADVANCED] 高级：全部选项，包含易混淆的
 */
enum class Difficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 播放模式。
 * - [ASCENDING] 上行旋律：音符从低到高依次弹奏
 * - [BLOCK] 柱式和弦：所有音符同时弹奏
 * - [DESCENDING] 下行旋律：音符从高到低依次弹奏
 */
enum class PlayMode(val displayName: String) {
    ASCENDING("上行"),
    BLOCK("同时"),
    DESCENDING("下行")
}

/**
 * 音程类型（按半音数定义）。
 *
 * @param semitones 两音之间的半音距离
 * @param abbreviation 简写（用于选项按钮显示）
 * @param fullName 中文全称
 */
enum class IntervalType(
    val semitones: Int,
    val abbreviation: String,
    val fullName: String
) {
    MINOR_2ND(1, "m2", "小二度"),
    MAJOR_2ND(2, "M2", "大二度"),
    MINOR_3RD(3, "m3", "小三度"),
    MAJOR_3RD(4, "M3", "大三度"),
    PERFECT_4TH(5, "P4", "纯四度"),
    TRITONE(6, "TT", "三全音"),
    PERFECT_5TH(7, "P5", "纯五度"),
    MINOR_6TH(8, "m6", "小六度"),
    MAJOR_6TH(9, "M6", "大六度"),
    MINOR_7TH(10, "m7", "小七度"),
    MAJOR_7TH(11, "M7", "大七度"),
    PERFECT_OCTAVE(12, "P8", "纯八度");

    companion object {
        /** 按半音数查找。 */
        fun fromSemitones(semitones: Int): IntervalType? =
            entries.firstOrNull { it.semitones == semitones }
    }
}

/**
 * 和弦类型（以半音偏移定义各音相对于根音的位置）。
 *
 * @param intervals 根音到各和弦音的半音偏移列表（含根音 0）
 * @param abbreviation 简写
 * @param fullName 中文全称
 */
enum class ChordType(
    val intervals: List<Int>,
    val abbreviation: String,
    val fullName: String
) {
    MAJOR(listOf(0, 4, 7), "M", "大三和弦"),
    MINOR(listOf(0, 3, 7), "m", "小三和弦"),
    DIMINISHED(listOf(0, 3, 6), "dim", "减三和弦"),
    AUGMENTED(listOf(0, 4, 8), "aug", "增三和弦"),
    DOMINANT_7TH(listOf(0, 4, 7, 10), "7", "属七和弦"),
    MAJOR_7TH(listOf(0, 4, 7, 11), "M7", "大七和弦"),
    MINOR_7TH(listOf(0, 3, 7, 10), "m7", "小七和弦");

    companion object {
        /** 三和弦（3 个音）。 */
        val TRIADS = listOf(MAJOR, MINOR, DIMINISHED, AUGMENTED)

        /** 七和弦（4 个音）。 */
        val SEVENTH_CHORDS = listOf(DOMINANT_7TH, MAJOR_7TH, MINOR_7TH)
    }
}

/**
 * 音阶类型（以半音偏移定义各级音相对于主音的位置）。
 *
 * @param intervals 主音到各级音的半音偏移列表（含主音 0，最后一个音通常是八度）
 * @param abbreviation 简写
 * @param fullName 中文全称
 */
enum class ScaleType(
    val intervals: List<Int>,
    val abbreviation: String,
    val fullName: String
) {
    MAJOR(listOf(0, 2, 4, 5, 7, 9, 11, 12), "大调", "自然大调"),
    NATURAL_MINOR(listOf(0, 2, 3, 5, 7, 8, 10, 12), "自然小调", "自然小调"),
    HARMONIC_MINOR(listOf(0, 2, 3, 5, 7, 8, 11, 12), "和声小调", "和声小调"),
    MELODIC_MINOR(listOf(0, 2, 3, 5, 7, 9, 11, 12), "旋律小调", "旋律小调"),
    CHROMATIC(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), "半音阶", "半音阶"),
    WHOLE_TONE(listOf(0, 2, 4, 6, 8, 10, 12), "全音阶", "全音阶");

    companion object {
        val COMMON = listOf(MAJOR, NATURAL_MINOR, HARMONIC_MINOR, MELODIC_MINOR)
    }
}

/**
 * 一次听音训练题目。
 *
 * @param exerciseType 练习类型
 * @param playMode 播放模式
 * @param midiNotes 要播放的 MIDI 音符列表（绝对 MIDI 编号，已包含根音偏移）
 * @param correctAnswer 正确答案的显示文本（如 "大三和弦" 或 "纯五度"）
 * @param answerChoices 所有选项列表（含正确答案，已打乱）
 * @param displayInfo 附加显示信息（如根音音名，用于答题后的讲解）
 */
data class EarTrainingQuestion(
    val exerciseType: ExerciseType,
    val playMode: PlayMode,
    val midiNotes: List<Int>,
    val correctAnswer: String,
    val answerChoices: List<String>,
    val displayInfo: String
) {
    /** 是否为多选题（选项 > 1）。 */
    val isMultipleChoice: Boolean get() = answerChoices.size > 1
}

/**
 * 一次答题结果。
 *
 * @param question 题目
 * @param userAnswer 用户选择的答案
 * @param isCorrect 是否答对
 */
data class AnswerRecord(
    val question: EarTrainingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
