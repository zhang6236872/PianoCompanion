package com.pianocompanion.pitchtraining

import com.pianocompanion.util.MusicUtils

/**
 * 绝对音高训练（Absolute Pitch Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **绝对音高（absolute / perfect pitch）**：在无参考音的情况下，仅凭一个音就能
 *   识别其音名（C / C# / D …）的能力。与相对音高（判断两音之间的距离/音程）不同，
 *   绝对音高要求大脑建立「频率 → 音名」的绝对映射。
 *
 * - **训练流程**：播放**单个**音符，用户从给定选项中选择正确的音名（音级类）。
 *
 * 与 [com.pianocompanion.intervaltraining] 的区别：
 * - 音程训练播放**两个**音，识别二者之间的**距离**（相对音高）。
 * - 绝对音高训练播放**一个**音，识别音的**名称**（绝对音高）。
 *
 * 难度分级：
 * - **初级**：仅白键音（C D E F G A B），单八度 C4-B4，7 个选项。
 * - **中级**：全部 12 个音级类（含升号），单八度 C4-B4，12 个选项。
 * - **高级**：全部 12 个音级类，横跨 3 个八度 C3-B5，12 个选项——同一音名出现在不同
 *   八度，因不同八度频率不同，真正考验绝对音高识别能力。
 */

/**
 * 12 个音级类（pitch class），表示一个八度内的 12 个半音位置。
 *
 * @param semitonesFromC 距 C 的半音数（0-11）
 * @param sharpName 升号记法显示名（如 "C#"）
 * @param flatName 降号记法显示名（如 "Db"）
 * @param isWhiteKey 是否白键（初级难度仅使用白键）
 * @param solfegeName 唱名（用于教学辅助）
 */
enum class PitchClass(
    val semitonesFromC: Int,
    val sharpName: String,
    val flatName: String,
    val isWhiteKey: Boolean,
    val solfegeName: String
) {
    C(0, "C", "C", true, "Do"),
    C_SHARP(1, "C#", "Db", false, "Di"),
    D(2, "D", "D", true, "Re"),
    D_SHARP(3, "D#", "Eb", false, "Ri"),
    E(4, "E", "E", true, "Mi"),
    F(5, "F", "F", true, "Fa"),
    F_SHARP(6, "F#", "Gb", false, "Fi"),
    G(7, "G", "G", true, "Sol"),
    G_SHARP(8, "G#", "Ab", false, "Si"),
    A(9, "A", "A", true, "La"),
    A_SHARP(10, "A#", "Bb", false, "Le"),
    B(11, "B", "B", true, "Ti");

    /** 默认显示名（使用升号记法）。 */
    val displayName: String get() = sharpName

    /** 是否黑键。 */
    val isBlackKey: Boolean get() = !isWhiteKey

    companion object {
        /** 全部 12 个音级类（按半音升序）。 */
        val ALL: List<PitchClass> = entries.toList()

        /** 仅白键音级类。 */
        val WHITE_KEYS: List<PitchClass> = entries.filter { it.isWhiteKey }

        /** 根据 MIDI 音符号获取对应音级类。 */
        fun fromMidi(midi: Int): PitchClass {
            val pc = midi % 12
            return entries.first { it.semitonesFromC == pc }
        }

        /** 根据半音数（0-11）获取音级类。 */
        fun fromSemitones(semitones: Int): PitchClass? =
            entries.firstOrNull { it.semitonesFromC == semitones.coerceIn(0, 11) }
    }
}

/**
 * 难度等级。
 *
 * @param pitchClasses 该难度可出题的音级类集合（同时也是选项集合）
 * @param octaveLowest 最低八度（MIDI 音符号的最低值）
 * @param octaveHighest 最高八度（MIDI 音符号的最高值）
 * @param description 难度说明
 */
enum class PitchTrainingDifficulty(
    val displayName: String,
    val pitchClasses: List<PitchClass>,
    val octaveLowest: Int,
    val octaveHighest: Int,
    val description: String
) {
    BEGINNER(
        displayName = "初级",
        pitchClasses = PitchClass.WHITE_KEYS,
        octaveLowest = 60,  // C4
        octaveHighest = 71, // B4
        description = "7 个白键音（C-B），单八度"
    ),
    INTERMEDIATE(
        displayName = "中级",
        pitchClasses = PitchClass.ALL,
        octaveLowest = 60,  // C4
        octaveHighest = 71, // B4
        description = "全部 12 音（含黑键），单八度"
    ),
    ADVANCED(
        displayName = "高级",
        pitchClasses = PitchClass.ALL,
        octaveLowest = 48,  // C3
        octaveHighest = 83, // B5
        description = "全部 12 音，横跨 3 个八度"
    );

    /** 选项数量（= 音级类集合大小）。 */
    val optionCount: Int get() = pitchClasses.size

    companion object {
        val ALL: List<PitchTrainingDifficulty> = entries.toList()
    }
}

/**
 * 绝对音高训练题目。
 *
 * @param pitchClass 正确的音级类
 * @param midiNote 播放的 MIDI 音符号
 * @param difficulty 难度
 * @param options 选项列表（音级类，已打乱，含正确答案）
 */
data class PitchQuestion(
    val pitchClass: PitchClass,
    val midiNote: Int,
    val difficulty: PitchTrainingDifficulty,
    val options: List<PitchClass>
) {
    /** 正确答案（音级类）。 */
    val correctAnswer: PitchClass get() = pitchClass

    init {
        require(options.isNotEmpty()) { "选项不能为空" }
        require(midiNote in PitchTrainingConstants.MIN_MIDI..PitchTrainingConstants.MAX_MIDI) {
            "MIDI 音符号超出钢琴范围: $midiNote"
        }
    }

    /** 播放音符的完整名称（含八度，如 "C4"）。 */
    val noteName: String get() = MusicUtils.midiToNoteName(midiNote)

    /** 八度编号。 */
    val octave: Int get() = (midiNote / 12) - 1

    /** 音级类详情（用于答题后教学反馈）。 */
    val pitchClassDetail: String get() = "${pitchClass.displayName}（唱名 ${pitchClass.solfegeName}）"

    /** 频率（Hz），用于展示教学信息。 */
    val frequency: Double get() = MusicUtils.midiToFrequency(midiNote)
}

/**
 * 一次答题结果。
 */
data class PitchAnswerRecord(
    val question: PitchQuestion,
    val userAnswer: PitchClass,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: PitchClass? get() = if (isCorrect) null else question.correctAnswer
}

/**
 * 全局常量。
 */
object PitchTrainingConstants {
    /** 钢琴最低音 A0。 */
    const val MIN_MIDI = 21

    /** 钢琴最高音 C8。 */
    const val MAX_MIDI = 108
}
