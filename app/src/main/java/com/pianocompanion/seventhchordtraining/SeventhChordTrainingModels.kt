package com.pianocompanion.seventhchordtraining

/**
 * 七和弦品质听辨训练（Seventh Chord Quality Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **七和弦（Seventh Chord）**：在三和弦（根音、三音、五音）之上叠加第七个音（七音），
 *   共 4 个音符。不同的七音和三音组合产生截然不同的色彩——从明亮的 Cmaj7 到刺耳的 Cdim7。
 *
 * - **训练流程**：播放一个柱式七和弦（4 音同时发声），用户从选项中选择正确的和弦品质。
 *   关键听觉线索是和弦的「紧张度」——大七和弦最协和、属七和弦有推动力、
 *   半减七/减七和弦越来越刺耳。
 *
 * 难度分级：
 * - **初级**：大七 vs 属七 vs 小七（3 选项）——最常见的三种，听感差异较大
 * - **中级**：+ 半减七（4 选项）——增加半减七（m7♭5），音色更暗淡紧张
 * - **高级**：+ 减七（5 选项）——全部 5 种，半减七 vs 减七是最大的挑战
 */

/**
 * 七和弦品质类型。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名（含常见符号）
 * @param intervals 从根音开始的半音偏移 [根, 三音, 五音, 七音]
 * @param description 听感描述（用于答题后的教学反馈）
 * @param tensionLevel 紧张度等级（0=最协和, 4=最刺耳），用于教学排序
 */
enum class SeventhChordQuality(
    val displayName: String,
    val englishName: String,
    val symbol: String,
    val intervals: List<Int>,
    val description: String,
    val tensionLevel: Int
) {
    MAJOR_7(
        displayName = "大七和弦",
        englishName = "Major 7th",
        symbol = "maj7",
        intervals = listOf(0, 4, 7, 11),
        description = "最明亮的七和弦，大调色彩中带着一丝空灵和梦幻。爵士标准和声的基础，" +
            "常用于结尾的柔美收束。根音上的大七度与纯五度叠加产生丰富而和谐的共鸣。",
        tensionLevel = 0
    ),
    DOMINANT_7(
        displayName = "属七和弦",
        englishName = "Dominant 7th",
        symbol = "7",
        intervals = listOf(0, 4, 7, 10),
        description = "充满了紧张感和期待感，强烈渴望解决到主和弦。布鲁斯、摇滚和流行音乐的核心。" +
            "大调三和弦 + 小七度的碰撞赋予它独特的「推动力」，V7→I 是西方音乐最经典的解决。",
        tensionLevel = 1
    ),
    MINOR_7(
        displayName = "小七和弦",
        englishName = "Minor 7th",
        symbol = "m7",
        intervals = listOf(0, 3, 7, 10),
        description = "柔和而略带忧郁，比小三和弦多了一分空间的宽敞感。爵士、灵魂乐和 R&B 的标配。" +
            "小三度 + 小七度的组合在暗淡中保持柔和，没有半减七或减七的刺耳。",
        tensionLevel = 2
    ),
    HALF_DIMINISHED_7(
        displayName = "半减七和弦",
        englishName = "Half-Diminished 7th",
        symbol = "m7♭5",
        intervals = listOf(0, 3, 6, 10),
        description = "暗淡而紧张，又称「小七降五」。爵士 ii-ø7-V7 进程的特征和弦。" +
            "减五度带来收缩的不安，但小七度保留了些微的柔和，不像减七那样完全刺耳。",
        tensionLevel = 3
    ),
    DIMINISHED_7(
        displayName = "减七和弦",
        englishName = "Diminished 7th",
        symbol = "dim7",
        intervals = listOf(0, 3, 6, 9),
        description = "最刺耳的七和弦，充满了悬疑和戏剧性。恐怖片配乐的常客。" +
            "三个小三度完全对称叠加，所有音之间的间距相等，产生一种悬浮不定的紧张感。",
        tensionLevel = 4
    );

    companion object {
        /** 所有七和弦品质。 */
        val ALL: List<SeventhChordQuality> = entries.toList()

        /**
         * 按难度返回可用的七和弦品质集合。
         * - 初级：大七 + 属七 + 小七（最常见的三种，听感差异大）
         * - 中级：+ 半减七（音色更暗淡紧张）
         * - 高级：+ 减七（全部 5 种，半减七 vs 减七最难区分）
         */
        fun forDifficulty(difficulty: SeventhChordDifficulty): List<SeventhChordQuality> = when (difficulty) {
            SeventhChordDifficulty.BEGINNER -> listOf(MAJOR_7, DOMINANT_7, MINOR_7)
            SeventhChordDifficulty.INTERMEDIATE -> listOf(MAJOR_7, DOMINANT_7, MINOR_7, HALF_DIMINISHED_7)
            SeventhChordDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与和弦种类）
 */
enum class SeventhChordDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大七 / 属七 / 小七（3 选项）· 最常见的三种"),
    INTERMEDIATE("中级", "+ 半减七（4 选项）· 音色更暗淡紧张"),
    ADVANCED("高级", "全部 5 种（5 选项）· 半减七 vs 减七最难区分");

    companion object {
        val ALL: List<SeventhChordDifficulty> = entries.toList()
    }
}

/**
 * 七和弦品质听辨训练题目。
 *
 * @param quality 和弦品质（大七/属七/小七/半减七/减七）
 * @param rootMidi 根音 MIDI 音符号（决定和弦的实际音高）
 * @param rootName 根音名（如 "C", "G"）
 * @param difficulty 难度
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，4 个音）
 * @param answerChoices 所有选项（和弦品质显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（和弦品质显示名）
 */
data class SeventhChordQuestion(
    val quality: SeventhChordQuality,
    val rootMidi: Int,
    val rootName: String,
    val difficulty: SeventhChordDifficulty,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size == 4) { "七和弦必须有 4 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 大七和弦 (maj7)"）。 */
    val fullDescription: String
        get() = "$rootName ${quality.displayName} (${quality.symbol})"

    /** 和弦品质描述。 */
    val qualityDescription: String get() = quality.description
}

/**
 * 一次答题结果。
 */
data class SeventhChordAnswerRecord(
    val question: SeventhChordQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
