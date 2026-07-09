package com.pianocompanion.inversiontraining

/**
 * 和弦转位听辨训练（Chord Inversion Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和弦转位（Chord Inversion）**：一个三和弦有三个音符（根音、三音、五音）。
 *   把不同音符放到最低声部就形成了不同的「转位」：
 *   - **原位（Root Position）**：根音在最低处，声音最稳定、最「落地」，如 C-E-G。
 *   - **第一转位（First Inversion）**：三音在最低处，声音更轻盈、活跃、有行进感，如 E-G-C（六和弦）。
 *   - **第二转位（Second Inversion）**：五音在最低处，声音悬而未决、渴望解决，如 G-C-E（四六和弦）。
 *
 * - **训练流程**：播放一个三和弦（柱式和弦，所有音同时发声），用户从选项中选择正确的转位类型。
 *   关键听觉线索是和弦的**最低音**以及整体的「稳定感 vs 行进感 vs 悬念感」。
 *
 * 难度分级：
 * - **初级**：原位 vs 第一转位（2 选项），仅大调三和弦——最基础的「稳定 vs 流动」对比
 * - **中级**：全部 3 种转位（3 选项），大调 + 小调三和弦——增加第二转位和小调和弦
 * - **高级**：全部 3 种转位（3 选项），大调 + 小调 + 增 + 减三和弦——和弦性质多变，难度最高
 */

/**
 * 和弦性质（三和弦类型）。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param intervals 三和弦从根音开始的半音偏移 [根, 三音, 五音]
 * @param description 听感描述（用于答题后的教学反馈）
 */
enum class ChordQuality(
    val displayName: String,
    val englishName: String,
    val intervals: List<Int>,
    val description: String
) {
    MAJOR(
        displayName = "大三和弦",
        englishName = "Major",
        intervals = listOf(0, 4, 7),
        description = "明亮、稳定、协和。大调音乐的基础，根音在最低处时最为「落地」。"
    ),
    MINOR(
        displayName = "小三和弦",
        englishName = "Minor",
        intervals = listOf(0, 3, 7),
        description = "柔和、忧郁、内敛。与大三和弦共享纯五度，但小三度带来暗淡色彩。"
    ),
    AUGMENTED(
        displayName = "增三和弦",
        englishName = "Augmented",
        intervals = listOf(0, 4, 8),
        description = "紧张、悬浮、神秘。两个大三度叠加，无纯五度支撑，听起来不稳定。"
    ),
    DIMINISHED(
        displayName = "减三和弦",
        englishName = "Diminished",
        intervals = listOf(0, 3, 6),
        description = "紧缩、不安、渴望解决。两个小三度叠加，减五度带来强烈的收缩张力。"
    );

    companion object {
        /** 所有和弦性质。 */
        val ALL: List<ChordQuality> = entries.toList()

        /**
         * 按难度返回可用的和弦性质集合。
         * - 初级：仅大三和弦（减少变量，专注转位识别）
         * - 中级：大三 + 小三（最常见的两种）
         * - 高级：大三 + 小三 + 增 + 减（和弦性质多变，难度最高）
         */
        fun forDifficulty(difficulty: InversionDifficulty): List<ChordQuality> = when (difficulty) {
            InversionDifficulty.BEGINNER -> listOf(MAJOR)
            InversionDifficulty.INTERMEDIATE -> listOf(MAJOR, MINOR)
            InversionDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 和弦转位类型。
 *
 * @param displayName 中文显示名
 * @param bassNoteName 最低声部音符的名称描述（用于教学）
 * @param symbol 记谱符号（如 5/3, 6, 6/4）
 * @param soundDescription 听感描述（用于答题后的教学反馈）
 * @param bassDegree 该转位最低音是三和弦的第几音（0=根音, 1=三音, 2=五音）
 */
enum class InversionType(
    val displayName: String,
    val symbol: String,
    val soundDescription: String,
    val bassDegree: Int
) {
    ROOT_POSITION(
        displayName = "原位",
        symbol = "5/3",
        soundDescription = "根音在最低处。声音最为稳定、完整、有「落地感」，像乐曲的「句号」。这是和弦的「家」。",
        bassDegree = 0
    ),
    FIRST_INVERSION(
        displayName = "第一转位",
        symbol = "6",
        soundDescription = "三音在最低处。声音更轻盈、活跃、有行进感，少了原位的厚重感。常用于乐句中间的流动。",
        bassDegree = 1
    ),
    SECOND_INVERSION(
        displayName = "第二转位",
        symbol = "6/4",
        soundDescription = "五音在最低处。声音悬而未决，四度音程在底部产生渴望解决的张力。常出现在终止式前的「期待」时刻。",
        bassDegree = 2
    );

    companion object {
        /** 所有转位类型。 */
        val ALL: List<InversionType> = entries.toList()

        /**
         * 按难度返回可用作答案的转位类型集合。
         * - 初级：原位 vs 第一转位（2 选项）
         * - 中级/高级：全部 3 种（3 选项）
         */
        fun forDifficulty(difficulty: InversionDifficulty): List<InversionType> = when (difficulty) {
            InversionDifficulty.BEGINNER -> listOf(ROOT_POSITION, FIRST_INVERSION)
            InversionDifficulty.INTERMEDIATE -> ALL
            InversionDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与和弦种类）
 */
enum class InversionDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "原位 vs 第一转位（2 选项）· 仅大三和弦"),
    INTERMEDIATE("中级", "全部 3 种转位（3 选项）· 大三 + 小三"),
    ADVANCED("高级", "全部 3 种转位（3 选项）· 大三 + 小三 + 增 + 减");

    companion object {
        val ALL: List<InversionDifficulty> = entries.toList()
    }
}

/**
 * 和弦转位听辨训练题目。
 *
 * @param quality 和弦性质（大三/小三/增/减）
 * @param inversion 正确的转位类型
 * @param rootMidi 根音 MIDI 音符号（决定和弦的实际音高）
 * @param rootName 根音名（如 "C", "G"）
 * @param difficulty 难度
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，3 个音）
 * @param answerChoices 所有选项（转位类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（转位类型显示名）
 */
data class InversionQuestion(
    val quality: ChordQuality,
    val inversion: InversionType,
    val rootMidi: Int,
    val rootName: String,
    val difficulty: InversionDifficulty,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size == 3) { "三和弦必须有 3 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 最低音（贝斯音）的 MIDI 音符号。 */
    val bassMidi: Int get() = midiNotes.first()

    /** 完整描述（如 "C 大三和弦 · 原位"）。 */
    val fullDescription: String
        get() = "$rootName ${quality.displayName} · ${inversion.displayName}（${inversion.symbol}）"

    /** 和弦性质描述。 */
    val qualityDescription: String get() = quality.description
}

/**
 * 一次答题结果。
 */
data class InversionAnswerRecord(
    val question: InversionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
