package com.pianocompanion.nonscaletonetraining

/**
 * 调外音听辨训练（Non-Scale Tone Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **调外音（Non-Scale Tone / Chromatic Tone）**：在一段调性音乐（如 C 大调）中，
 *   凡是不属于该调自然音阶的音，都称为「调外音」（chromatic tone）。
 *   调外音通过升降号临时改变音阶中的某个音级，产生色彩、紧张感或风格特征。
 *
 * - **训练流程**：播放一段 5 音上行旋律短句（自然大调音阶的第 1~5 级 do-re-mi-fa-sol），
 *   其中可能有一个音被做了半音升降。用户凭听觉判断这段旋律是纯调内（无调外音），
 *   还是包含某种调外音，并从选项中选出正确的类型。
 *
 * - **5 种调外音类型（含调内对照）**：
 *   - DIATONIC（调内）：纯自然大调音阶，无任何变化
 *   - FLATTED_THIRD（降三度 ♭3）：第 3 级降半音（小调色彩 / 布鲁斯蓝调音）
 *   - RAISED_FOURTH（升四度 ♯4）：第 4 级升半音（利底亚色彩 / 三全音紧张）
 *   - FLATTED_FIFTH（降五度 ♭5）：第 5 级降半音（三全音 / 蓝调音）
 *   - RAISED_SECOND（升二度 ♯2）：第 2 级升半音（半音经过 / 弗里几亚色彩）
 *
 * - **难度分级**：选项数量递增，候选调外音类型递增
 *   - 初级（3 选项）：调内 / 降三度 / 升四度
 *   - 中级（4 选项）：+ 降五度
 *   - 高级（5 选项）：+ 升二度
 */

/** 钢琴 MIDI 音域常量。 */
const val NST_MIN_MIDI = 21
const val NST_MAX_MIDI = 108

/** 自然大调音阶第 1~5 级距主音的半音偏移量（do re mi fa sol）。 */
val DIATONIC_DEGREE_OFFSETS: List<Int> = listOf(0, 2, 4, 5, 7)

/**
 * 调外音类型。
 *
 * @param displayName 中文显示名
 * @param symbol 变化记号符号（如 "♭3"、"♯4"、"调内"）
 * @param description 听感描述（用于答题后的教学反馈）
 * @param alteredDegree 被变化音的音级（1~5）；DIATONIC 为 0（无变化）
 * @param semitoneDeviation 半音偏移量（-1=降、0=不变、+1=升）
 * @param difficultyRank 难度排序值（1=最易，越大越难）
 * @param colorHex Material 配色十六进制值
 */
enum class NonScaleToneType(
    val displayName: String,
    val symbol: String,
    val description: String,
    val alteredDegree: Int,
    val semitoneDeviation: Int,
    val difficultyRank: Int,
    val colorHex: String
) {
    DIATONIC(
        displayName = "调内（自然大调）",
        symbol = "调内",
        description = "纯自然大调音阶，没有任何变化音。这段旋律的每个音都属于调内，" +
            "听感自然、稳定、明亮。当你听不到任何「意外」或「色彩变化」时，" +
            "它就是一段纯净的调内旋律。这是所有调外音的对照基准——先听熟调内的感觉，" +
            "才能敏锐地察觉到哪怕一个半音的偏离。",
        alteredDegree = 0,
        semitoneDeviation = 0,
        difficultyRank = 1,
        colorHex = "#4CAF50"
    ),
    FLATTED_THIRD(
        displayName = "降三度（♭3）",
        symbol = "♭3",
        description = "大调音阶的第 3 级（mi）被降低了半音，变成降 mi。这是最显著的色彩变化——" +
            "它瞬间把明亮的大调色彩染上一层忧郁，听起来像「小调的色彩」闪现了一下。" +
            "降三度是布鲁斯、爵士和流行音乐中最常见的「蓝调音」，只需一个音就能让大调旋律" +
            "获得蓝调的韵味。听感线索：第 3 个音听起来偏暗、偏「扁」。",
        alteredDegree = 3,
        semitoneDeviation = -1,
        difficultyRank = 2,
        colorHex = "#2196F3"
    ),
    RAISED_FOURTH(
        displayName = "升四度（♯4）",
        symbol = "♯4",
        description = "大调音阶的第 4 级（fa）被升高了半音，变成升 fa。这是「利底亚」调式的特征音，" +
            "产生一种梦幻、空灵、向外漂浮的色彩。同时升 fa 与 do（主音）构成三全音（增四度），" +
            "带来一种特有的紧张与期待感。在电影配乐和爵士中常用。听感线索：第 4 个音听起来" +
            "偏高、带有一丝紧张和「漂浮」。",
        alteredDegree = 4,
        semitoneDeviation = +1,
        difficultyRank = 3,
        colorHex = "#9C27B0"
    ),
    FLATTED_FIFTH(
        displayName = "降五度（♭5）",
        symbol = "♭5",
        description = "大调音阶的第 5 级（sol）被降低了半音，变成降 sol。降 sol与 do（主音）" +
            "构成三全音，是西方音乐中最不协和的音程之一，因此降五度带有强烈的紧张与不稳定性。" +
            "它也是布鲁斯和爵士中著名的「蓝调音」之一，常与降三度一起使用，赋予旋律浓郁的蓝调韵味。" +
            "听感线索：第 5 个音（最后一个）听起来偏低、紧张、未完成。",
        alteredDegree = 5,
        semitoneDeviation = -1,
        difficultyRank = 4,
        colorHex = "#FF9800"
    ),
    RAISED_SECOND(
        displayName = "升二度（♯2）",
        symbol = "♯2",
        description = "大调音阶的第 2 级（re）被升高了半音，变成升 re。它与随后的 mi 构成极窄的" +
            "小二度（半音），听感紧凑、带有异域或「弗里几亚」色彩。升二度较少单独出现，" +
            "通常作为半音经过音，但在这里作为独立的调外音出现，辨识难度最高。" +
            "听感线索：第 2 个音听起来偏高，与第 3 个音之间几乎没有间距（挤在一起）。",
        alteredDegree = 2,
        semitoneDeviation = +1,
        difficultyRank = 5,
        colorHex = "#FF5722"
    );

    companion object {
        /** 所有调外音类型（含调内），按难度排序。 */
        val ALL: List<NonScaleToneType> = entries.sortedBy { it.difficultyRank }
    }
}

/**
 * 音乐调性（大调）。
 *
 * @param displayName 中文显示名（如 "C 大调"）
 * @param tonicMidi 主音的 MIDI 音符号（用于构建旋律短句）
 * @param noteName 主音音名
 */
enum class NstMusicalKey(
    val displayName: String,
    val tonicMidi: Int,
    val noteName: String
) {
    C_MAJOR("C 大调", 48, "C"),
    G_MAJOR("G 大调", 55, "G"),
    F_MAJOR("F 大调", 53, "F"),
    D_MAJOR("D 大调", 50, "D");

    companion object {
        val ALL: List<NstMusicalKey> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 */
enum class NonScaleToneDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "3 选项（调内 / 降三度 / 升四度）· 核心色彩对照"),
    INTERMEDIATE("中级", "4 选项（+ 降五度）· 加入三全音紧张"),
    ADVANCED("高级", "5 选项（+ 升二度）· 全部 5 种调外音辨识");

    companion object {
        val ALL: List<NonScaleToneDifficulty> = entries.toList()

        /**
         * 返回该难度下可用的调外音类型集合（含调内）。
         * - 初级：DIATONIC, FLATTED_THIRD, RAISED_FOURTH
         * - 中级：+ FLATTED_FIFTH
         * - 高级：+ RAISED_SECOND（全部 5 种）
         */
        fun typesForDifficulty(difficulty: NonScaleToneDifficulty): List<NonScaleToneType> =
            NonScaleToneType.ALL.take(when (difficulty) {
                BEGINNER -> 3
                INTERMEDIATE -> 4
                ADVANCED -> 5
            })
    }
}

/**
 * 调外音听辨训练题目。
 *
 * @param type 调外音类型（含调内）
 * @param key 所在的调性
 * @param difficulty 难度
 * @param tonicMidi 主音的 MIDI 音符号
 * @param midiNotes 旋律短句的 MIDI 音符号列表（5 个音，按上行旋律顺序，**不排序**）
 * @param answerChoices 所有选项（类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（类型显示名）
 */
data class NonScaleToneQuestion(
    val type: NonScaleToneType,
    val key: NstMusicalKey,
    val difficulty: NonScaleToneDifficulty,
    val tonicMidi: Int,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size == PHRASE_NOTE_COUNT) {
            "旋律短句应有 $PHRASE_NOTE_COUNT 个音符，实际 ${midiNotes.size}"
        }
        require(midiNotes.all { it in NST_MIN_MIDI..NST_MAX_MIDI }) {
            "MIDI 音符超出钢琴范围 [$NST_MIN_MIDI, $NST_MAX_MIDI]"
        }
    }

    /** 完整描述（如 "C 大调 调外音听辨"）。 */
    val fullDescription: String
        get() = "${key.displayName} ${type.displayName}"

    /** 类型描述（教学反馈）。 */
    val typeDescription: String get() = type.description

    /**
     * 被变化音所在的位置描述（如 "第 3 个音"），调内时为 "无变化音"。
     * 注意：UI 中旋律按 1~5 编号。
     */
    val alteredPositionDescription: String
        get() = if (type.alteredDegree == 0) "无变化音（纯调内）" else "第 ${type.alteredDegree} 个音是调外音"

    companion object {
        /** 旋律短句的音符数量（固定 5 个：do re mi fa sol）。 */
        const val PHRASE_NOTE_COUNT = 5
    }
}

/**
 * 一次答题结果。
 */
data class NonScaleToneAnswerRecord(
    val question: NonScaleToneQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
