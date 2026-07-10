package com.pianocompanion.ninthchordtraining

/**
 * 九和弦色彩听辨训练（Ninth Chord Quality Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **九和弦（Ninth Chord）**：在七和弦（根音、三音、五音、七音）之上叠加第九个音（九音），
 *   共 5 个音符。九音是根音上方一个八度再加一个二度——它为和弦增添了丰富的色彩光泽。
 *
 * - **训练流程**：播放一个柱式九和弦（5 音同时发声），用户从选项中选择正确的和弦类型。
 *   关键听觉线索是和弦的「色彩丰富度」——从明亮协和的大九到刺耳异域的属七降九的渐变。
 *
 * - **与七和弦训练的区别**：七和弦训练关注 4 音和弦的紧张度；九和弦训练关注 5 音和弦中
 *   九音带来的额外色彩。不同品质的九和弦拥有截然不同的音响世界：大九梦幻、属九布鲁斯、
 *   小九都市、小大九悬疑（007 主题音乐）、属七降九异域（西班牙/吉普赛）。
 *
 * 难度分级：
 * - **初级**：大九 vs 属九 vs 小九（3 选项）——最常见的三种九和弦
 * - **中级**：+ 小大九（4 选项）——增加神秘悬疑色彩（小三度+大七度碰撞）
 * - **高级**：+ 属七降九（5 选项）——全部 5 种，属七降九最刺耳最难辨识
 */

/**
 * 九和弦品质类型。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名（含常见符号）
 * @param symbol 和弦符号（如 "maj9", "9", "m9"）
 * @param intervals 从根音开始的半音偏移 [根, 三, 五, 七, 九]（5 个音）
 * @param description 听感描述（用于答题后的教学反馈）
 * @param richnessLevel 色彩丰富度等级（0=最明亮协和, 4=最刺耳异域），用于教学排序
 */
enum class NinthChordQuality(
    val displayName: String,
    val englishName: String,
    val symbol: String,
    val intervals: List<Int>,
    val description: String,
    val richnessLevel: Int
) {
    MAJOR_9(
        displayName = "大九和弦",
        englishName = "Major 9th",
        symbol = "maj9",
        intervals = listOf(0, 4, 7, 11, 14),
        description = "最明亮华丽的九和弦，大七和弦的色彩升华。空灵梦幻、如水晶般清透，" +
            "爵士标准和声的标志。大七度与九度叠加产生丰富而和谐的泛音共鸣，" +
            "是 smooth jazz 和 neo-soul 最爱的「糖果色」和弦。",
        richnessLevel = 0
    ),
    DOMINANT_9(
        displayName = "属九和弦",
        englishName = "Dominant 9th",
        symbol = "9",
        intervals = listOf(0, 4, 7, 10, 14),
        description = "充满了布鲁斯色彩与推动力。在属七和弦的紧张感上叠加九度，" +
            "使紧张中多了丝华丽与温暖。摇滚、布鲁斯和福音音乐的核心。V9→I 是爵士中" +
            "比 V7→I 更丰满的经典解决。",
        richnessLevel = 1
    ),
    MINOR_9(
        displayName = "小九和弦",
        englishName = "Minor 9th",
        symbol = "m9",
        intervals = listOf(0, 3, 7, 10, 14),
        description = "柔和而精致，都市深夜的灵魂之声。小七和弦叠加九度后暗淡中" +
            "多了一丝空间感和光泽。爵士、灵魂乐和 R&B 的标志性「都会和弦」，" +
            "深夜电台最爱的氛围。",
        richnessLevel = 2
    ),
    MINOR_MAJOR_9(
        displayName = "小大九和弦",
        englishName = "Minor-Major 9th",
        symbol = "m(maj9)",
        intervals = listOf(0, 3, 7, 11, 14),
        description = "神秘而暗黑，间谍电影的标志性音响。小三度与大七度的碰撞产生" +
            "一种「不祥」的悬疑感——007 詹姆斯·邦德主题曲的经典和弦。" +
            "在暗淡中保留大七度的亮度，制造独特的紧张-华丽并存效果。",
        richnessLevel = 3
    ),
    DOMINANT_7_FLAT_9(
        displayName = "属七降九和弦",
        englishName = "Dominant 7th ♭9",
        symbol = "7♭9",
        intervals = listOf(0, 4, 7, 10, 13),
        description = "最刺耳的九和弦，充满了异域风情和戏剧张力。降九度（即小二度）" +
            "与根音形成极不协和的半音碰撞，产生西班牙、吉普赛和东方色彩的音响。" +
            "爵士 altered 和声和弗拉门戈的标志，作为属功能和弦制造最大程度的解决渴望。",
        richnessLevel = 4
    );

    companion object {
        /** 所有九和弦品质。 */
        val ALL: List<NinthChordQuality> = entries.toList()

        /**
         * 按难度返回可用的九和弦品质集合。
         * - 初级：大九 + 属九 + 小九（最常见的三种，听感差异大）
         * - 中级：+ 小大九（增加神秘悬疑色彩）
         * - 高级：+ 属七降九（全部 5 种，属七降九最难辨识）
         */
        fun forDifficulty(difficulty: NinthChordDifficulty): List<NinthChordQuality> = when (difficulty) {
            NinthChordDifficulty.BEGINNER -> listOf(MAJOR_9, DOMINANT_9, MINOR_9)
            NinthChordDifficulty.INTERMEDIATE -> listOf(MAJOR_9, DOMINANT_9, MINOR_9, MINOR_MAJOR_9)
            NinthChordDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与和弦种类）
 */
enum class NinthChordDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大九 / 属九 / 小九（3 选项）· 最常见的三种九和弦"),
    INTERMEDIATE("中级", "+ 小大九（4 选项）· 增加神秘悬疑色彩"),
    ADVANCED("高级", "全部 5 种（5 选项）· 属七降九最刺耳最难辨识");

    companion object {
        val ALL: List<NinthChordDifficulty> = entries.toList()
    }
}

/**
 * 九和弦色彩听辨训练题目。
 *
 * @param quality 和弦品质（大九/属九/小九/小大九/属七降九）
 * @param rootMidi 根音 MIDI 音符号（决定和弦的实际音高）
 * @param rootName 根音名（如 "C", "G"）
 * @param difficulty 难度
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，5 个音）
 * @param answerChoices 所有选项（和弦品质显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（和弦品质显示名）
 */
data class NinthChordQuestion(
    val quality: NinthChordQuality,
    val rootMidi: Int,
    val rootName: String,
    val difficulty: NinthChordDifficulty,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size == 5) { "九和弦必须有 5 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 大九和弦 (maj9)"）。 */
    val fullDescription: String
        get() = "$rootName ${quality.displayName} (${quality.symbol})"

    /** 和弦品质描述。 */
    val qualityDescription: String get() = quality.description
}

/**
 * 一次答题结果。
 */
data class NinthChordAnswerRecord(
    val question: NinthChordQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
