package com.pianocompanion.eleventhchordtraining

/**
 * 十一和弦色彩听辨训练（Eleventh Chord Quality Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **十一和弦（Eleventh Chord）**：在九和弦（根音、三音、五音、七音、九音）之上叠加
 *   第十一个音（十一音），共 6 个音符。十一音是根音上方一个八度再加一个纯四度——
 *   它为和弦增添了空灵、开放、悬浮的色彩。十一和弦是爵士和声中最丰富的和弦类型之一。
 *
 * - **训练流程**：播放一个柱式十一和弦（6 音同时发声），用户从选项中选择正确的和弦类型。
 *   关键听觉线索是和弦的「明亮度」——从光辉开放的大十一到暗沉朦胧的半减十一的渐变。
 *
 * - **与九和弦训练的区别**：九和弦关注 5 音和弦的色彩丰富度；十一和弦关注 6 音和弦中
 *   十一音带来的空灵、开放感。不同品质的十一和弦拥有截然不同的音响世界：
 *   大十一光辉、小十一空灵（爵士标准配置）、属十一布鲁斯、小大十一神秘、半减十一暗沉。
 *
 * 难度分级：
 * - **初级**：大十一 vs 属十一 vs 小十一（3 选项）——最常见的三种十一和弦
 * - **中级**：+ 小大十一（4 选项）——增加神秘色彩（小三度+大七度碰撞）
 * - **高级**：+ 半减十一（5 选项）——全部 5 种，半减十一最暗最难辨识
 */

/**
 * 十一和弦品质类型。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名（含常见符号）
 * @param symbol 和弦符号（如 "maj11", "11", "m11"）
 * @param intervals 从根音开始的半音偏移 [根, 三, 五, 七, 九, 十一]（6 个音）
 * @param description 听感描述（用于答题后的教学反馈）
 * @param brightnessLevel 明亮度等级（0=最明亮开放, 4=最暗沉朦胧），用于教学排序
 */
enum class EleventhChordQuality(
    val displayName: String,
    val englishName: String,
    val symbol: String,
    val intervals: List<Int>,
    val description: String,
    val brightnessLevel: Int
) {
    MAJOR_11(
        displayName = "大十一和弦",
        englishName = "Major 11th",
        symbol = "maj11",
        intervals = listOf(0, 4, 7, 11, 14, 17),
        description = "最光辉开放的十一和弦，大七与十一音的完美交融。明亮中带着\" +\n            \"一丝空灵的悬浮感，大三度与十一音（纯四度）形成的微妙张力赋予它独特的现代色彩。\" +\n            \"常用于 neo-soul、R&B 和当代爵士，是充满光辉感的「水晶和弦」。\" +\n            \"代表曲风：现代爵士、neo-soul、氛围音乐。",
        brightnessLevel = 0
    ),
    DOMINANT_11(
        displayName = "属十一和弦",
        englishName = "Dominant 11th",
        symbol = "11",
        intervals = listOf(0, 4, 7, 10, 14, 17),
        description = "充满了布鲁斯与放克色彩。属七的推动力叠加九音和十一音的开阔，\" +\n            \"使紧张中多了空间感和纵深。属功能和弦中最高程度的扩展，在 funk 和 jazz-fusion\" +\n            \"中制造层次丰富的运动。大三度与十一音的张力更增添了一丝\"悬念\"。\" +\n            \"代表曲风：放克、爵士融合、灵魂乐。",
        brightnessLevel = 1
    ),
    MINOR_11(
        displayName = "小十一和弦",
        englishName = "Minor 11th",
        symbol = "m11",
        intervals = listOf(0, 3, 7, 10, 14, 17),
        description = "十一和弦中最自然、最常用的类型。小三度与十一音（纯四度）形成和谐的大六度，\" +\n            \"没有冲突——小十一因此拥有最完美的空灵悬浮感。爵士钢琴的标志性声响，\" +\n            \"像深夜都市的灯光般柔和而辽阔。neo-soul 和 lo-fi hip-hop 的灵魂和弦。\" +\n            \"代表曲风：爵士、neo-soul、lo-fi hip-hop。",
        brightnessLevel = 2
    ),
    MINOR_MAJOR_11(
        displayName = "小大十一和弦",
        englishName = "Minor-Major 11th",
        symbol = "m(maj11)",
        intervals = listOf(0, 3, 7, 11, 14, 17),
        description = "神秘而暗黑，在空灵的十一音之上叠加小大七度的悬疑色彩。\" +\n            \"小三度与大七度的碰撞制造不祥的张力，而十一音的开放性又带来一种\" +\n            \"诡异的宁静——仿佛黑暗中透出的一丝光。极富戏剧性的和弦，\" +\n            \"在电影配乐和前卫爵士中制造独特的情绪。\" +\n            \"代表曲风：电影配乐、前卫爵士、氛围电子。",
        brightnessLevel = 3
    ),
    HALF_DIMINISHED_11(
        displayName = "半减十一和弦",
        englishName = "Half-Diminished 11th",
        symbol = "m11♭5",
        intervals = listOf(0, 3, 6, 10, 14, 17),
        description = "最暗沉朦胧的十一和弦。减五度（三全音）带来紧张不安，但十一音的\" +\n            \"开放性又提供一丝悬浮的宽慰——黑暗中挣扎的一缕光。作为 ii11♭5\" +\n            \"在小调 ii-V-i 进行中极为常见，是爵士和声的核心和弦之一。\" +\n            \"代表曲风：爵士、现代古典、电影配乐。",
        brightnessLevel = 4
    );

    companion object {
        /** 所有十一和弦品质。 */
        val ALL: List<EleventhChordQuality> = entries.toList()

        /**
         * 按难度返回可用的十一和弦品质集合。
         * - 初级：大十一 + 属十一 + 小十一（最常见的三种，听感差异大）
         * - 中级：+ 小大十一（增加神秘悬疑色彩）
         * - 高级：+ 半减十一（全部 5 种，半减十一最难辨识）
         */
        fun forDifficulty(difficulty: EleventhChordDifficulty): List<EleventhChordQuality> = when (difficulty) {
            EleventhChordDifficulty.BEGINNER -> listOf(MAJOR_11, DOMINANT_11, MINOR_11)
            EleventhChordDifficulty.INTERMEDIATE -> listOf(MAJOR_11, DOMINANT_11, MINOR_11, MINOR_MAJOR_11)
            EleventhChordDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与和弦种类）
 */
enum class EleventhChordDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大十一 / 属十一 / 小十一（3 选项）· 最常见的三种十一和弦"),
    INTERMEDIATE("中级", "+ 小大十一（4 选项）· 增加神秘悬疑色彩"),
    ADVANCED("高级", "全部 5 种（5 选项）· 半减十一最暗最难辨识");

    companion object {
        val ALL: List<EleventhChordDifficulty> = entries.toList()
    }
}

/**
 * 十一和弦色彩听辨训练题目。
 *
 * @param quality 和弦品质（大十一/属十一/小十一/小大十一/半减十一）
 * @param rootMidi 根音 MIDI 音符号（决定和弦的实际音高）
 * @param rootName 根音名（如 "C", "G"）
 * @param difficulty 难度
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，6 个音）
 * @param answerChoices 所有选项（和弦品质显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（和弦品质显示名）
 */
data class EleventhChordQuestion(
    val quality: EleventhChordQuality,
    val rootMidi: Int,
    val rootName: String,
    val difficulty: EleventhChordDifficulty,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size == 6) { "十一和弦必须有 6 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 大十一和弦 (maj11)"）。 */
    val fullDescription: String
        get() = "$rootName ${quality.displayName} (${quality.symbol})"

    /** 和弦品质描述。 */
    val qualityDescription: String get() = quality.description
}

/**
 * 一次答题结果。
 */
data class EleventhChordAnswerRecord(
    val question: EleventhChordQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
