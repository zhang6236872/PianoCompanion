package com.pianocompanion.thirteenthchordtraining

/**
 * 十三和弦色彩听辨训练（Thirteenth Chord Quality Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **十三和弦（Thirteenth Chord）**：在十一和弦（根音、三音、五音、七音、九音、十一音）之上
 *   叠加第十三个音（十三音），共 7 个音符。十三音是根音上方两个八度再加一个大六度——
 *   它是爵士和声中扩展和弦的极限。十三和弦拥有最丰富、最饱满、最复杂的音响色彩，
 *   是爵士钢琴和编曲的终极和声工具。
 *
 * - **训练流程**：播放一个柱式十三和弦（7 音同时发声），用户从选项中选择正确的和弦类型。
 *   关键听觉线索是和弦的「明亮度」——从辉煌灿烂的大十三到深沉幽暗的半减十三的渐变。
 *
 * - **与十一和弦训练的区别**：十一和弦关注 6 音和弦的空灵开放感；十三和弦关注 7 音和弦中
 *   十三音带来的终极色彩饱和。不同品质的十三和弦拥有截然不同的音响宇宙：
 *   大十三辉煌、属十三温暖（爵士/布鲁斯标准）、小十三深沉广阔、小大十三神秘戏剧、半减十三幽暗。
 *
 * 难度分级：
 * - **初级**：大十三 vs 属十三 vs 小十三（3 选项）——最常见的三种十三和弦
 * - **中级**：+ 小大十三（4 选项）——增加神秘戏剧色彩（小三度+大七度碰撞）
 * - **高级**：+ 半减十三（5 选项）——全部 5 种，半减十三最暗最难辨识
 */

/**
 * 十三和弦品质类型。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名（含常见符号）
 * @param symbol 和弦符号（如 "maj13", "13", "m13"）
 * @param intervals 从根音开始的半音偏移 [根, 三, 五, 七, 九, 十一, 十三]（7 个音）
 * @param description 听感描述（用于答题后的教学反馈）
 * @param brightnessLevel 明亮度等级（0=最明亮, 4=最暗沉），用于教学排序
 */
enum class ThirteenthChordQuality(
    val displayName: String,
    val englishName: String,
    val symbol: String,
    val intervals: List<Int>,
    val description: String,
    val brightnessLevel: Int
) {
    MAJOR_13(
        displayName = "大十三和弦",
        englishName = "Major 13th",
        symbol = "maj13",
        intervals = listOf(0, 4, 7, 11, 14, 17, 21),
        description = "最辉煌灿烂的十三和弦，七音完美叠加，色彩饱和度达到极致。" +
            "大七度的温暖与十三音（大六度）的开阔完美交融，像黎明时分的第一缕阳光。" +
            "丰富的泛音交织出水晶般的透明感，是现代爵士和声中最华丽的色彩。" +
            "代表曲风：现代爵士、neo-soul、氛围音乐。"
,
        brightnessLevel = 0
    ),
    DOMINANT_13(
        displayName = "属十三和弦",
        englishName = "Dominant 13th",
        symbol = "13",
        intervals = listOf(0, 4, 7, 10, 14, 17, 21),
        description = "充满温暖与推进力的十三和弦。属七的张力与九音、十一音、十三音的" +
            "丰富色彩叠加，形成爵士和布鲁斯中最经典、最饱满的和弦声响。" +
            "十三音（大六度）带来温暖而开放的色彩，在属功能中创造出层次极为丰富的运动。" +
            "代表曲风：爵士、布鲁斯、R&B、灵魂乐。",
        brightnessLevel = 1
    ),
    MINOR_13(
        displayName = "小十三和弦",
        englishName = "Minor 13th",
        symbol = "m13",
        intervals = listOf(0, 3, 7, 10, 14, 17, 21),
        description = "深沉而广阔的十三和弦。小三度的忧郁与扩展音的辽阔交织，" +
            "像深夜海面上最后一抹余晖。十三音与小三度形成完美的大六度关系，没有冲突——" +
            "因此小十三拥有最自然、最深沉的空旷感。neo-soul 和 lo-fi 的标志性声响。" +
            "代表曲风：neo-soul、lo-fi hip-hop、氛围爵士。",
        brightnessLevel = 2
    ),
    MINOR_MAJOR_13(
        displayName = "小大十三和弦",
        englishName = "Minor-Major 13th",
        symbol = "m(maj13)",
        intervals = listOf(0, 3, 7, 11, 14, 17, 21),
        description = "神秘而戏剧化的十三和弦。在十三音的辽阔之上叠加小大七度的" +
            "诡谲色彩，小三度与大七度的碰撞制造深层的不安与张力，却又因十三音的开放性" +
            "而呈现出一种诡异的宁静——如同暴风雨前的沉寂。极富戏剧性和叙事感。" +
            "代表曲风：电影配乐、前卫爵士、氛围电子。",
        brightnessLevel = 3
    ),
    HALF_DIMINISHED_13(
        displayName = "半减十三和弦",
        englishName = "Half-Diminished 13th",
        symbol = "m13♭5",
        intervals = listOf(0, 3, 6, 10, 14, 17, 21),
        description = "最幽暗深沉的十三和弦。减五度（三全音）带来强烈的不安与悬疑，" +
            "扩展音的辽阔又提供一丝悬浮的宽慰——黑暗深处挣扎的一缕光。作为 ii13♭5 " +
            "在小调 ii-V-i 进行中极为重要，是爵士和声中最复杂的色彩之一。" +
            "代表曲风：现代爵士、电影配乐、前卫古典。",
        brightnessLevel = 4
    );

    companion object {
        /** 所有十三和弦品质。 */
        val ALL: List<ThirteenthChordQuality> = entries.toList()

        /**
         * 按难度返回可用的十三和弦品质集合。
         * - 初级：大十三 + 属十三 + 小十三（最常见的三种，听感差异大）
         * - 中级：+ 小大十三（增加神秘戏剧色彩）
         * - 高级：+ 半减十三（全部 5 种，半减十三最难辨识）
         */
        fun forDifficulty(difficulty: ThirteenthChordDifficulty): List<ThirteenthChordQuality> = when (difficulty) {
            ThirteenthChordDifficulty.BEGINNER -> listOf(MAJOR_13, DOMINANT_13, MINOR_13)
            ThirteenthChordDifficulty.INTERMEDIATE -> listOf(MAJOR_13, DOMINANT_13, MINOR_13, MINOR_MAJOR_13)
            ThirteenthChordDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与和弦种类）
 */
enum class ThirteenthChordDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大十三 / 属十三 / 小十三（3 选项）· 最常见的三种十三和弦"),
    INTERMEDIATE("中级", "+ 小大十三（4 选项）· 增加神秘戏剧色彩"),
    ADVANCED("高级", "全部 5 种（5 选项）· 半减十三最暗最难辨识");

    companion object {
        val ALL: List<ThirteenthChordDifficulty> = entries.toList()
    }
}

/**
 * 十三和弦色彩听辨训练题目。
 *
 * @param quality 和弦品质（大十三/属十三/小十三/小大十三/半减十三）
 * @param rootMidi 根音 MIDI 音符号（决定和弦的实际音高）
 * @param rootName 根音名（如 "C", "G"）
 * @param difficulty 难度
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，7 个音）
 * @param answerChoices 所有选项（和弦品质显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（和弦品质显示名）
 */
data class ThirteenthChordQuestion(
    val quality: ThirteenthChordQuality,
    val rootMidi: Int,
    val rootName: String,
    val difficulty: ThirteenthChordDifficulty,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size == 7) { "十三和弦必须有 7 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 大十三和弦 (maj13)"）。 */
    val fullDescription: String
        get() = "$rootName ${quality.displayName} (${quality.symbol})"

    /** 和弦品质描述。 */
    val qualityDescription: String get() = quality.description
}

/**
 * 一次答题结果。
 */
data class ThirteenthChordAnswerRecord(
    val question: ThirteenthChordQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
