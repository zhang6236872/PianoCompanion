package com.pianocompanion.interval

/**
 * 音程识别训练（Interval Identification Trainer）数据模型。
 *
 * 本文件包含所有与音程识别训练相关的音乐理论和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **音程（Interval）**：两个音之间的距离，由"度数"（number）和"性质"（quality）共同描述
 * - **度数（Interval Number）**：按音名（字母）计算的间隔 + 1。例如 C→E 跨 2 个字母 = 三度
 * - **性质（Interval Quality）**：按半音数判定。纯(P)、大(M)、小(m)、增(A)、减(d)
 *
 * 音程理论：
 * - 一度/四度/五度/八度 → 纯音程系列（纯/增/减）
 * - 二度/三度/六度/七度 → 大小音程系列（大/小/增/减）
 *
 * 自然音（无升降号）音程表（C 大调白键之间的音程）：
 * ```
 * 小二度(m2): E-F, B-C          大二度(M2): C-D, D-E, F-G, G-A, A-B
 * 小三度(m3): D-F, E-G, A-C, B-D  大三度(M3): C-E, F-A, G-B
 * 纯四度(P4): C-F, D-G, E-A, G-C   增四度(A4): F-B
 * 减五度(d5): B-F                  纯五度(P5): C-G, D-A, E-B, F-C
 * 小六度(m6): E-C, A-F, B-G        大六度(M6): C-A, D-B
 * 小七度(m7): D-C, E-D, G-F        大七度(M7): C-B, F-E
 * 纯八度(P8): 任意音到其八度
 * ```
 */

/**
 * 谱号。
 * - [TREBLE] 高音谱号（G clef）：底线 = E4
 * - [BASS] 低音谱号（F clef）：底线 = G2
 */
enum class IntervalClef(val displayName: String) {
    TREBLE("高音谱号"),
    BASS("低音谱号");

    companion object {
        val ALL = listOf(TREBLE, BASS)
    }
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：仅判断度数（二度~八度），高音谱号，自然音
 * - [INTERMEDIATE] 中级：度数 + 性质（大/小/纯），高低音谱号，自然音
 * - [ADVANCED] 高级：全部性质（含增/减），高低音谱号，更宽音域
 */
enum class IntervalDifficulty(val displayName: String, val requiresQuality: Boolean) {
    BEGINNER("初级", false),
    INTERMEDIATE("中级", true),
    ADVANCED("高级", true);

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 音程度数。
 *
 * @param displayName 中文度数名称
 * @param diatonicSteps 该度数对应的自然音步距（度数 - 1）
 * @param isPerfect 是否属于纯音程系列（一度/四度/五度/八度）
 */
enum class IntervalNumber(
    val displayName: String,
    val diatonicSteps: Int,
    val isPerfect: Boolean
) {
    UNISON("一度", 0, true),
    SECOND("二度", 1, false),
    THIRD("三度", 2, false),
    FOURTH("四度", 3, true),
    FIFTH("五度", 4, true),
    SIXTH("六度", 5, false),
    SEVENTH("七度", 6, false),
    OCTAVE("八度", 7, true);

    companion object {
        /** 按自然音步距查找度数。 */
        fun fromDiatonicSteps(steps: Int): IntervalNumber? =
            entries.firstOrNull { it.diatonicSteps == steps }
    }
}

/**
 * 音程性质。
 *
 * @param displayName 中文性质名称（纯/大/小/增/减）
 * @param semitoneOffset 相对于"完美"音程的半音偏移（用于分类）
 */
enum class IntervalQuality(val displayName: String) {
    PERFECT("纯"),
    MAJOR("大"),
    MINOR("小"),
    AUGMENTED("增"),
    DIMINISHED("减");
}

/**
 * 完整的音程描述（度数 + 性质）。
 *
 * @param number 度数
 * @param quality 性质
 */
data class Interval(
    val number: IntervalNumber,
    val quality: IntervalQuality
) {
    /** 中文显示名称，如 "大三度"、"纯五度"、"增四度"。 */
    val displayName: String get() = "${quality.displayName}${number.displayName}"

    companion object {
        /**
         * 自然音（白键）音程分类表。
         * 键 = (度数, 半音差)，值 = 性质。
         *
         * 此表覆盖所有可能的白键音程组合。
         */
        private val NATURAL_CLASSIFICATION: Map<Pair<IntervalNumber, Int>, IntervalQuality> = mapOf(
            // 一度
            Pair(IntervalNumber.UNISON, 0) to IntervalQuality.PERFECT,
            // 二度
            Pair(IntervalNumber.SECOND, 1) to IntervalQuality.MINOR,
            Pair(IntervalNumber.SECOND, 2) to IntervalQuality.MAJOR,
            // 三度
            Pair(IntervalNumber.THIRD, 3) to IntervalQuality.MINOR,
            Pair(IntervalNumber.THIRD, 4) to IntervalQuality.MAJOR,
            // 四度
            Pair(IntervalNumber.FOURTH, 5) to IntervalQuality.PERFECT,
            Pair(IntervalNumber.FOURTH, 6) to IntervalQuality.AUGMENTED,
            // 五度
            Pair(IntervalNumber.FIFTH, 6) to IntervalQuality.DIMINISHED,
            Pair(IntervalNumber.FIFTH, 7) to IntervalQuality.PERFECT,
            // 六度
            Pair(IntervalNumber.SIXTH, 8) to IntervalQuality.MINOR,
            Pair(IntervalNumber.SIXTH, 9) to IntervalQuality.MAJOR,
            // 七度
            Pair(IntervalNumber.SEVENTH, 10) to IntervalQuality.MINOR,
            Pair(IntervalNumber.SEVENTH, 11) to IntervalQuality.MAJOR,
            // 八度
            Pair(IntervalNumber.OCTAVE, 12) to IntervalQuality.PERFECT
        )

        /**
         * 根据度数和半音差分类音程性质。
         *
         * @param number 度数
         * @param semitones 半音差
         * @return 对应的 [Interval]，如果无法分类则返回 null
         */
        fun classify(number: IntervalNumber, semitones: Int): Interval? {
            val quality = NATURAL_CLASSIFICATION[Pair(number, semitones)] ?: return null
            return Interval(number, quality)
        }
    }
}

/**
 * 音程识别训练题目。
 *
 * @param clef 谱号
 * @param difficulty 难度
 * @param lowerStaffStep 较低音的谱表位置（底线 = 0）
 * @param higherStaffStep 较高音的谱表位置（底线 = 0）
 * @param lowerMidi 较低音的 MIDI 音符号
 * @param higherMidi 较高音的 MIDI 音符号
 * @param lowerLetterName 较低音的音名字母（C/D/E/F/G/A/B）
 * @param higherLetterName 较高音的音名字母
 * @param interval 正确的音程
 * @param requiresQuality 本题是否需要回答性质（取决于难度）
 * @param answerChoices 所有选项列表（含正确答案，已打乱）
 * @param correctAnswer 正确答案文本
 */
data class IntervalQuestion(
    val clef: IntervalClef,
    val difficulty: IntervalDifficulty,
    val lowerStaffStep: Int,
    val higherStaffStep: Int,
    val lowerMidi: Int,
    val higherMidi: Int,
    val lowerLetterName: String,
    val higherLetterName: String,
    val interval: Interval,
    val requiresQuality: Boolean,
    val answerChoices: List<String>,
    val correctAnswer: String
)

/**
 * 一次答题结果。
 *
 * @param question 题目
 * @param userAnswer 用户选择的答案
 * @param isCorrect 是否答对
 */
data class IntervalAnswerRecord(
    val question: IntervalQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
