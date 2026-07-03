package com.pianocompanion.keysig

/**
 * 调号识别训练（Key Signature Identification Trainer）数据模型。
 *
 * 本文件包含所有与调号识别训练相关的音乐理论和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **调号（Key Signature）**：写在谱号后面的升降号组合，标明乐曲的调性。
 *   - 升号（♯）调：从 0 个升号（C 大调/a 小调）到 7 个升号（C♯ 大调/a♯ 小调），
 *     按 **F-C-G-D-A-E-B** 顺序依次增加。
 *   - 降号（♭）调：从 0 个降号到 7 个降号（C♭ 大调/a♭ 小调），
 *     按 **B-E-A-D-G-C-F** 顺序依次增加。
 * - **关系大小调**：同一调号可对应一个大调和一个小调（互为关系调），
 *   小调主音 = 大调主音下方小三度（3 个半音）。例如 C 大调 ↔ a 小调（0 升降号），
 *   G 大调 ↔ e 小调（1 个升号）。
 * - **五度圈**：升号调沿五度圈顺时针排列（C→G→D→A→E→B→F♯→C♯），
 *   降号调逆时针排列（C→F→B♭→E♭→A♭→D♭→G♭→C♭）。
 */

/**
 * 谱号。
 * - [TREBLE] 高音谱号（G clef）：底线 = E4
 * - [BASS] 低音谱号（F clef）：底线 = G2
 */
enum class KeySigClef(val displayName: String) {
    TREBLE("高音谱号"),
    BASS("低音谱号");

    companion object {
        val ALL = listOf(TREBLE, BASS)
    }
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：仅大调，0-3 个升降号（C/G/D/A/F/B♭/E♭ 大调）
 * - [INTERMEDIATE] 中级：仅大调，最多 5 个升降号
 * - [ADVANCED] 高级：大调 + 小调，最多 7 个升降号（需区分关系大小调）
 */
enum class KeySigDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 调式。
 */
enum class KeyMode(val displaySuffix: String) {
    MAJOR("大调"),
    MINOR("小调")
}

/**
 * 变音记号类型。
 */
enum class AccidentalType(val symbol: String) {
    NONE(""),
    SHARP("♯"),
    FLAT("♭")
}

/**
 * 调性信息（完整描述一个调及其调号）。
 *
 * @param tonicPitchClass 主音音级类（0-11，C=0）
 * @param tonicLetter 主音字母索引（0-6，C=0, D=1, ..., B=6）
 * @param accidentalModifier 主音变音记号（SHARP/FLAT/NONE，用于显示 ♯/♭）
 * @param mode 调式（大调/小调）
 * @param sharpCount 调号中升号数
 * @param flatCount 调号中降号数
 * @param accidentalType 调号类型（SHARP/FLAT/NONE）
 * @param preferFlats 音名显示时是否优先用降号
 */
data class KeyInfo(
    val tonicPitchClass: Int,
    val tonicLetter: Int,
    val accidentalModifier: AccidentalType,
    val mode: KeyMode,
    val sharpCount: Int,
    val flatCount: Int,
    val accidentalType: AccidentalType,
    val preferFlats: Boolean
) {
    /** 升降号总数（sharpCount 或 flatCount，0 调号为 C 大调/a 小调）。 */
    val accidentalCount: Int get() = sharpCount + flatCount

    /** 调号中的变音记号在五线谱上的位置（staff step，底线=0），按顺序排列。 */
    val accidentalSteps: List<Int>
        get() = accidentalPositions(accidentalType, accidentalCount)

    /**
     * 显示名称（如 "G大调"、"B♭大调"、"F♯小调"、"A小调"）。
     */
    val displayName: String
        get() {
            val letter = LETTER_NAMES[tonicLetter]
            val modifier = accidentalModifier.symbol
            return "$letter$modifier${mode.displaySuffix}"
        }

    /**
     * 音阶 MIDI 音符列表（单八度上行，用于音频试听）。
     * 大调音阶：全全半全全全半（0,2,4,5,7,9,11,+12）
     * 自然小调音阶：全半全全半全全（0,2,3,5,7,8,10,+12）
     */
    val scaleMidis: List<Int>
        get() {
            val baseOctaveMidi = 60 // C4 = 60 作为基准
            val rootMidi = baseOctaveMidi + tonicPitchClass
            val intervals = if (mode == KeyMode.MAJOR) {
                listOf(0, 2, 4, 5, 7, 9, 11, 12)
            } else {
                listOf(0, 2, 3, 5, 7, 8, 10, 12)
            }
            return intervals.map { (rootMidi + it).coerceIn(21, 108) }
        }

    companion object {
        /** 音名字母索引：C=0, D=1, E=2, F=3, G=4, A=5, B=6 */
        const val LETTER_C = 0
        const val LETTER_D = 1
        const val LETTER_E = 2
        const val LETTER_F = 3
        const val LETTER_G = 4
        const val LETTER_A = 5
        const val LETTER_B = 6

        /** 字母名列表（C 到 B）。 */
        val LETTER_NAMES = listOf("C", "D", "E", "F", "G", "A", "B")

        // ── 五线谱上调号位置（staff step，底线=0）──

        /** 高音谱号升号位置（F♯ C♯ G♯ D♯ A♯ E♯ B♯ 顺序）。 */
        val TREBLE_SHARP_STEPS = intArrayOf(8, 5, 9, 6, 3, 7, 4)

        /** 低音谱号升号位置。 */
        val BASS_SHARP_STEPS = intArrayOf(6, 3, 7, 4, 8, 5, 2)

        /** 高音谱号降号位置（B♭ E♭ A♭ D♭ G♭ C♭ F♭ 顺序）。 */
        val TREBLE_FLAT_STEPS = intArrayOf(4, 7, 3, 6, 2, 5, 8)

        /** 低音谱号降号位置。 */
        val BASS_FLAT_STEPS = intArrayOf(2, 5, 8, 4, 7, 3, 6)

        /**
         * 获取调号中升降号在五线谱上的位置列表。
         *
         * @param type 变音记号类型
         * @param count 升降号数量（0-7）
         * @return staff step 列表（空列表表示无升降号）
         */
        fun accidentalPositions(type: AccidentalType, count: Int): List<Int> {
            if (count <= 0) return emptyList()
            return when (type) {
                AccidentalType.SHARP -> TREBLE_SHARP_STEPS.take(count)
                AccidentalType.FLAT -> TREBLE_FLAT_STEPS.take(count)
                else -> emptyList()
            }
        }
    }
}

/**
 * 调号识别训练题目。
 *
 * @param clef 谱号
 * @param difficulty 难度
 * @param keyInfo 正确的调性信息
 * @param accidentalStaffSteps 调号升降号在五线谱上的位置列表
 * @param answerChoices 所有选项列表（含正确答案，已打乱）
 * @param correctAnswer 正确答案文本
 */
data class KeySigQuestion(
    val clef: KeySigClef,
    val difficulty: KeySigDifficulty,
    val keyInfo: KeyInfo,
    val accidentalStaffSteps: List<Int>,
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
data class KeySigAnswerRecord(
    val question: KeySigQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
