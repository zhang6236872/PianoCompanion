package com.pianocompanion.chordfunctiontraining

/**
 * 和弦功能听辨训练（Chord Function Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和弦功能（Harmonic Function）**：在调性音乐中，每个和弦根据其在调性中的地位承担
 *   不同的功能角色。功能听辨训练不是让用户判断和弦的"品质"（大三/小三/属七等），
 *   而是让用户判断和弦的"功能"——它在大调中的和声角色。
 *
 *   - **主功能（Tonic, T）**：稳定、归属、到家了的感觉。I 和弦是主功能的核心，
 *     iii 和 vi 是主功能的替代和弦（同样具有稳定感）。
 *   - **下属功能（Subdominant, S）**：离开主音、向外运动的感觉。IV 是核心，
 *     ii 是替代。具有开阔、延伸的特质。
 *   - **属功能（Dominant, D）**：紧张、不稳定、渴望解决的感觉。V 是核心，
 *     vii° 是替代。具有强烈的方向性，推动和声回到主功能。
 *
 * - **训练流程**：播放一个调内和弦（自然音阶上的三和弦或七和弦），用户从 3 个选项
 *   （主功能 / 下属功能 / 属功能）中选择正确的功能。
 *
 * 难度分级：
 * - **初级**：仅 I / IV / V 三个正三和弦，功能对应最为清晰（I=T, IV=S, V=D）
 * - **中级**：全部 7 个自然音三和弦（加入 ii, iii, vi, vii°），需要识别功能替代和弦
 *   （例如 vi 是小三和弦但属于主功能，vii° 是减三和弦但属于属功能）
 * - **高级**：全部 7 个自然音七和弦，和声色彩更丰富，辨识难度最高
 */

/** 钢琴 MIDI 音域常量。 */
const val CF_MIN_MIDI = 21
const val CF_MAX_MIDI = 108

/**
 * 和声功能类型。
 *
 * @param displayName 中文显示名
 * @param symbol 功能标记符号（T/S/D，里曼功能理论的标记）
 * @param romanNumerals 属于该功能的罗马数字标记
 * @param description 听感描述（用于答题后的教学反馈）
 * @param tensionLevel 紧张度等级（0=最稳定, 1=中等运动, 2=最紧张）
 * @param colorHex Material 配色十六进制值
 */
enum class HarmonicFunction(
    val displayName: String,
    val symbol: String,
    val romanNumerals: String,
    val description: String,
    val tensionLevel: Int,
    val colorHex: String
) {
    TONIC(
        displayName = "主功能",
        symbol = "T",
        romanNumerals = "I · iii · vi",
        description = "稳定、归属、到家了的感觉。主功能和弦是调性的中心和归宿，" +
            "听到它们时你会感到「安定」「圆满」。在音乐中，主功能通常出现在乐句的开头和结尾，" +
            "建立和确认调性中心。I 和弦是主功能的核心；iii 和 vi 作为替代和弦，" +
            "同样具有稳定感，只是色彩略有不同（iii 偏暗，vi 更柔和）。",
        tensionLevel = 0,
        colorHex = "#4CAF50"
    ),
    SUBDOMINANT(
        displayName = "下属功能",
        symbol = "S",
        romanNumerals = "ii · IV",
        description = "离开主音、向外运动的感觉。下属功能将和声从主音推开，" +
            "产生一种开阔、延伸的特质，常被形容为「旅程的开始」。它桥接了稳定的主功能" +
            "和紧张的属功能，在和声进行 T→S→D→T 中扮演承上启下的角色。" +
            "IV 和弦明亮而开阔；ii 和弦（小三和弦）更为柔和，但同样具有离开主音的运动感。",
        tensionLevel = 1,
        colorHex = "#2196F3"
    ),
    DOMINANT(
        displayName = "属功能",
        symbol = "D",
        romanNumerals = "V · vii°",
        description = "紧张、不稳定、渴望解决。属功能包含导音（第七音级），" +
            "强烈地倾向于解决回主音。V 和弦（属和弦）是西方音乐中最具方向性的和弦，" +
            "听到它时你会本能地期待主和弦的到来。vii°（减三和弦）同样属于属功能，" +
            "紧张度更高，因含两个不稳定音程（减五度）而格外渴望解决。",
        tensionLevel = 2,
        colorHex = "#FF5722"
    );

    companion object {
        /** 所有和声功能。 */
        val ALL: List<HarmonicFunction> = entries.toList()
    }
}

/**
 * 音乐调性（大调）。
 *
 * @param displayName 中文显示名（如 "C 大调"）
 * @param tonicMidi 主音的 MIDI 音符号（用于构建调内和弦）
 * @param noteName 主音音名
 */
enum class MusicalKey(
    val displayName: String,
    val tonicMidi: Int,
    val noteName: String
) {
    C_MAJOR("C 大调", 48, "C"),
    G_MAJOR("G 大调", 55, "G"),
    F_MAJOR("F 大调", 53, "F"),
    D_MAJOR("D 大调", 50, "D");

    companion object {
        val ALL: List<MusicalKey> = entries.toList()
    }
}

/**
 * 自然音阶音级（大调中的七个调内和弦）。
 *
 * @param romanNumeral 罗马数字标记（大写=大三和弦，小写=小三和弦，°=减三和弦）
 * @param scaleOffset 从主音开始的半音偏移（大调音阶各音级的偏移）
 * @param function 该音级的和声功能
 * @param triadIntervals 三和弦的音程（从根音开始的半音偏移）
 * @param seventhIntervals 七和弦的音程
 * @param triadTypeName 三和弦类型名（如 "大三和弦"）
 * @param seventhTypeName 七和弦类型名（如 "大七和弦"）
 */
enum class ScaleDegree(
    val romanNumeral: String,
    val scaleOffset: Int,
    val function: HarmonicFunction,
    val triadIntervals: List<Int>,
    val seventhIntervals: List<Int>,
    val triadTypeName: String,
    val seventhTypeName: String
) {
    I(
        romanNumeral = "I", scaleOffset = 0, function = HarmonicFunction.TONIC,
        triadIntervals = listOf(0, 4, 7), seventhIntervals = listOf(0, 4, 7, 11),
        triadTypeName = "大三和弦", seventhTypeName = "大七和弦"
    ),
    II(
        romanNumeral = "ii", scaleOffset = 2, function = HarmonicFunction.SUBDOMINANT,
        triadIntervals = listOf(0, 3, 7), seventhIntervals = listOf(0, 3, 7, 10),
        triadTypeName = "小三和弦", seventhTypeName = "小七和弦"
    ),
    III(
        romanNumeral = "iii", scaleOffset = 4, function = HarmonicFunction.TONIC,
        triadIntervals = listOf(0, 3, 7), seventhIntervals = listOf(0, 3, 7, 10),
        triadTypeName = "小三和弦", seventhTypeName = "小七和弦"
    ),
    IV(
        romanNumeral = "IV", scaleOffset = 5, function = HarmonicFunction.SUBDOMINANT,
        triadIntervals = listOf(0, 4, 7), seventhIntervals = listOf(0, 4, 7, 11),
        triadTypeName = "大三和弦", seventhTypeName = "大七和弦"
    ),
    V(
        romanNumeral = "V", scaleOffset = 7, function = HarmonicFunction.DOMINANT,
        triadIntervals = listOf(0, 4, 7), seventhIntervals = listOf(0, 4, 7, 10),
        triadTypeName = "大三和弦", seventhTypeName = "属七和弦"
    ),
    VI(
        romanNumeral = "vi", scaleOffset = 9, function = HarmonicFunction.TONIC,
        triadIntervals = listOf(0, 3, 7), seventhIntervals = listOf(0, 3, 7, 10),
        triadTypeName = "小三和弦", seventhTypeName = "小七和弦"
    ),
    VII_DIM(
        romanNumeral = "vii°", scaleOffset = 11, function = HarmonicFunction.DOMINANT,
        triadIntervals = listOf(0, 3, 6), seventhIntervals = listOf(0, 3, 6, 10),
        triadTypeName = "减三和弦", seventhTypeName = "半减七和弦"
    );

    companion object {
        /** 正三和弦（I, IV, V）——初学者使用的核心和弦。 */
        val PRIMARY_TRIADS: List<ScaleDegree> = listOf(I, IV, V)

        /** 全部 7 个自然音阶音级。 */
        val ALL: List<ScaleDegree> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 */
enum class ChordFunctionDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "仅 I/IV/V 正三和弦（3 选项）· 三大功能入门"),
    INTERMEDIATE("中级", "全部 7 个自然音三和弦（3 选项）· 功能替代和弦"),
    ADVANCED("高级", "全部 7 个自然音七和弦（3 选项）· 丰富和声色彩");

    companion object {
        val ALL: List<ChordFunctionDifficulty> = entries.toList()
    }
}

/**
 * 和弦功能听辨训练题目。
 *
 * @param scaleDegree 音级（I/ii/iii/IV/V/vi/vii°）
 * @param function 正确的和声功能
 * @param key 所在的调性
 * @param chordRootMidi 和弦根音的 MIDI 音符号
 * @param difficulty 难度
 * @param useSeventh 是否使用七和弦（true=七和弦, false=三和弦）
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，3 或 4 个音）
 * @param answerChoices 所有选项（功能显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（功能显示名）
 */
data class ChordFunctionQuestion(
    val scaleDegree: ScaleDegree,
    val function: HarmonicFunction,
    val key: MusicalKey,
    val chordRootMidi: Int,
    val difficulty: ChordFunctionDifficulty,
    val useSeventh: Boolean,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size in 3..4) { "和弦应有 3 或 4 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in CF_MIN_MIDI..CF_MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 大调 V 和弦（属七和弦）"）。 */
    val fullDescription: String
        get() {
            val chordType = if (useSeventh) scaleDegree.seventhTypeName else scaleDegree.triadTypeName
            return "${key.displayName} ${scaleDegree.romanNumeral} 和弦（$chordType）"
        }

    /** 功能描述。 */
    val functionDescription: String get() = function.description

    /** 功能符号（T/S/D）。 */
    val functionSymbol: String get() = function.symbol
}

/**
 * 一次答题结果。
 */
data class ChordFunctionAnswerRecord(
    val question: ChordFunctionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
