package com.pianocompanion.chordtraining

/**
 * 和弦听辨训练（Chord Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和弦（Chord）**：三个或更多音高按三度关系叠置。听辨训练的核心是听出一个和弦的
 *   “色彩/性质”——由根音到三音（大三度/小三度）、根音到五音（纯五/减五/增五）、
 *   以及七音（如有）共同决定。
 * - **三和弦听感**：
 *   - 大三和弦（Major）：明亮、稳定、协和（大三度+纯五度）
 *   - 小三和弦（Minor）：柔和、暗淡、略带忧郁（小三度+纯五度）
 *   - 减三和弦（Diminished）：紧张、不稳定、收缩感（小三度+减五度）
 *   - 增三和弦（Augmented）：悬浮、梦幻、向外扩张（大三度+增五度）
 * - **七和弦听感**：在三和弦色彩上叠加七音，产生更丰富的爵士/古典色彩：
 *   - 大七和弦（Maj7）：明亮而华丽，带“水晶”般的光泽
 *   - 属七和弦（Dom7）：紧张、不稳定，强烈倾向解决（布鲁斯/爵士核心）
 *   - 小七和弦（Min7）：柔和、慵懒，爵士/波萨诺瓦常用
 *   - 减七和弦（Dim7）：极度紧张、戏剧化，浪漫派/电影配乐常用
 *
 * 本训练为**听辨训练**：播放一个和弦，让用户凭听觉判断是哪种和弦类型。
 */

/**
 * 和弦听辨类型。
 *
 * [intervals] 为相对于根音的半音偏移列表（不含根音本身，根音隐含为 0）。
 * 例如大三和弦 = 根音 + 大三度(4) + 纯五度(7) → intervals = [4, 7]。
 *
 * @param displayName 中文显示名
 * @param symbol 和弦标记符号（如 "m"、"°7"）
 * @param intervals 相对根音的半音偏移（升序，不含 0）
 * @param isSeventh 是否为七和弦
 * @param description 听感描述（用于答题后的教学反馈）
 */
enum class ChordEarType(
    val displayName: String,
    val symbol: String,
    val intervals: List<Int>,
    val isSeventh: Boolean,
    val description: String
) {
    MAJOR(
        displayName = "大三和弦",
        symbol = "",
        intervals = listOf(4, 7),
        isSeventh = false,
        description = "明亮、稳定、协和。最常见的协和和弦（大三度 + 纯五度）。"
    ),
    MINOR(
        displayName = "小三和弦",
        symbol = "m",
        intervals = listOf(3, 7),
        isSeventh = false,
        description = "柔和、暗淡、略带忧郁。与大三和弦仅差一个三音（小三度 + 纯五度）。"
    ),
    DIMINISHED(
        displayName = "减三和弦",
        symbol = "°",
        intervals = listOf(3, 6),
        isSeventh = false,
        description = "紧张、不稳定、收缩感。含减五度，倾向解决到稳定和弦。"
    ),
    AUGMENTED(
        displayName = "增三和弦",
        symbol = "+",
        intervals = listOf(4, 8),
        isSeventh = false,
        description = "悬浮、梦幻、向外扩张。含增五度，整音平均等分八度为三份。"
    ),
    MAJOR_SEVENTH(
        displayName = "大七和弦",
        symbol = "maj7",
        intervals = listOf(4, 7, 11),
        isSeventh = true,
        description = "明亮而华丽，带“水晶”般的光泽。大七度音带来柔和的紧张色彩。"
    ),
    DOMINANT_SEVENTH(
        displayName = "属七和弦",
        symbol = "7",
        intervals = listOf(4, 7, 10),
        isSeventh = true,
        description = "紧张、不稳定，强烈倾向解决。布鲁斯、爵士和古典终止式的核心。"
    ),
    MINOR_SEVENTH(
        displayName = "小七和弦",
        symbol = "m7",
        intervals = listOf(3, 7, 10),
        isSeventh = true,
        description = "柔和、慵懒、放松。爵士与波萨诺瓦最常用的和弦色彩。"
    ),
    DIMINISHED_SEVENTH(
        displayName = "减七和弦",
        symbol = "°7",
        intervals = listOf(3, 6, 9),
        isSeventh = true,
        description = "极度紧张、戏剧化。四个音等分八度，浪漫派与电影配乐常用。"
    );

    /** 和弦包含的音符总数（含根音）。 */
    val noteCount: Int get() = intervals.size + 1

    /** 和弦所有音的半音偏移（含根音 0），已排序。 */
    val allIntervals: List<Int> get() = listOf(0) + intervals

    companion object {
        /** 所有和弦类型（按教学顺序：先三和弦后七和弦）。 */
        val ALL: List<ChordEarType> = entries.toList()

        /** 所有三和弦类型。 */
        val TRIADS: List<ChordEarType> = listOf(MAJOR, MINOR, DIMINISHED, AUGMENTED)

        /** 所有七和弦类型。 */
        val SEVENTHS: List<ChordEarType> =
            listOf(MAJOR_SEVENTH, DOMINANT_SEVENTH, MINOR_SEVENTH, DIMINISHED_SEVENTH)

        /**
         * 按难度返回可用的和弦类型集合。
         * - 初级：大三 vs 小三（最基础的协和和弦听辨）
         * - 中级：四种三和弦（大三/小三/减三/增三）
         * - 高级：三和弦 + 四种七和弦（共 8 种，含七和弦色彩）
         */
        fun forDifficulty(difficulty: ChordEarDifficulty): List<ChordEarType> = when (difficulty) {
            ChordEarDifficulty.BEGINNER -> listOf(MAJOR, MINOR)
            ChordEarDifficulty.INTERMEDIATE -> TRIADS
            ChordEarDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param description 该难度的说明（含选项数量）
 */
enum class ChordEarDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大三 vs 小三（2 选项）"),
    INTERMEDIATE("中级", "四种三和弦（4 选项）"),
    ADVANCED("高级", "三和弦 + 七和弦（8 选项）");

    companion object {
        val ALL: List<ChordEarDifficulty> = entries.toList()
    }
}

/**
 * 和弦播放方式。
 *
 * - [BLOCK] 柱式：所有音同时发声（标准听辨方式，考验整体色彩判断）
 * - [ARPEGGIO] 琶音：从低到高依次快速弹奏（帮助初学者分辨各音程）
 */
enum class ChordPlayStyle(val displayName: String, val description: String) {
    BLOCK("柱式", "同时弹奏，判断整体色彩"),
    ARPEGGIO("琶音", "依次弹奏，分辨各音程");

    companion object {
        val ALL: List<ChordPlayStyle> = entries.toList()
    }
}

/**
 * 根音（音级类，0=C, 1=C♯/D♭, ..., 11=B）。
 *
 * 内部使用音级类数字表示，与 chord 包解耦。
 */
data class ChordRoot(val pitchClass: Int) {
    init {
        require(pitchClass in 0..11) { "音级类必须在 0..11 范围内，实际: $pitchClass" }
    }

    /** 根据升降号偏好返回根音名（如 "C"、"D♭"、"F♯"）。 */
    fun name(preferFlats: Boolean): String =
        (if (preferFlats) FLAT_NAMES else SHARP_NAMES)[pitchClass]

    companion object {
        /** 升号记法（默认）。 */
        val SHARP_NAMES: List<String> = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

        /** 降号记法。 */
        val FLAT_NAMES: List<String> = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

        /** 12 个根音。 */
        val ALL: List<ChordRoot> = (0..11).map { ChordRoot(it) }

        /**
         * 是否偏好降号记法（五度圈惯例：D♭/E♭/G♭/A♭/B♭ 调使用降号）。
         */
        fun preferFlats(pitchClass: Int): Boolean = pitchClass in setOf(1, 3, 5, 6, 8, 10)
    }
}

/**
 * 和弦听辨训练题目。
 *
 * @param type 正确的和弦类型
 * @param root 根音
 * @param difficulty 难度
 * @param playStyle 播放方式
 * @param midiNotes 和弦各音的 MIDI 音符号列表（升序，已根据根音和音程构建并钳制到钢琴范围）
 * @param answerChoices 所有选项（含正确答案，已打乱）
 * @param correctAnswer 正确答案文本（和弦类型显示名）
 */
data class ChordEarQuestion(
    val type: ChordEarType,
    val root: ChordRoot,
    val difficulty: ChordEarDifficulty,
    val playStyle: ChordPlayStyle,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整名称（如 "C 大三和弦"、"D♭ 属七和弦"）。 */
    val fullName: String
        get() = "${root.name(ChordRoot.preferFlats(root.pitchClass))} ${type.displayName}"

    /** 音符数量。 */
    val noteCount: Int get() = midiNotes.size
}

/**
 * 一次答题结果。
 */
data class ChordEarAnswerRecord(
    val question: ChordEarQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
