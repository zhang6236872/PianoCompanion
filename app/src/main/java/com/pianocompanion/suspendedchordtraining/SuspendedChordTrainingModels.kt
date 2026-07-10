package com.pianocompanion.suspendedchordtraining

/**
 * 挂留和弦品质听辨训练（Suspended Chord Quality Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **挂留和弦（Suspended Chord）**：将三和弦中的三度音「悬置」——用二度或四度替代，
 *   使和弦失去明确的大调/小调色彩，产生一种「悬而未决」的开放质感。
 *   sus2 [0,2,7] 用大二度替代三音，sus4 [0,5,7] 用纯四度替代三音。
 *
 * - **训练流程**：播放一个柱式和弦（3 或 4 音同时发声），用户从选项中选择正确的和弦类型。
 *   关键听觉线索是和弦的「开放度」——大三/小三有明确的色彩（明/暗），
 *   而挂留和弦既不明也不暗，是一种特殊的「空旷」感。
 *
 * 难度分级：
 * - **初级**：大三 vs 挂二 vs 挂四（3 选项）——核心对比：有色彩 vs 无色彩
 * - **中级**：+ 小三（4 选项）——增加小三和弦，四种三和弦全面对比
 * - **高级**：+ 双挂（5 选项）——全部 5 种，双挂和弦 [0,2,5,7] 4 音结构最难辨识
 */

/**
 * 挂留和弦品质类型。
 *
 * @param displayName 中文显示名
 * @param englishName 英文名（含常见符号）
 * @param symbol 和弦符号
 * @param intervals 从根音开始的半音偏移（升序，第一个为 0）
 * @param description 听感描述（用于答题后的教学反馈）
 * @param opennessLevel 开放度等级（0=最封闭/明确, 4=最开放/模糊）
 */
enum class SuspendedChordQuality(
    val displayName: String,
    val englishName: String,
    val symbol: String,
    val intervals: List<Int>,
    val description: String,
    val opennessLevel: Int
) {
    MAJOR_TRIAD(
        displayName = "大三和弦",
        englishName = "Major",
        symbol = "maj",
        intervals = listOf(0, 4, 7),
        description = "明亮而稳定，大调色彩的基石。根音上的大三度赋予它阳光、积极的气质。" +
            "当你听到一个明确的「明亮」和弦，就是它了。三度音明确地建立了大调感。",
        opennessLevel = 0
    ),
    MINOR_TRIAD(
        displayName = "小三和弦",
        englishName = "Minor",
        symbol = "min",
        intervals = listOf(0, 3, 7),
        description = "暗淡而稳定，小调色彩的代表。根音上的小三度带来忧郁、内省的情感。" +
            "与大三和弦一样，它有一个明确的三度音——只是向下低了半步。",
        opennessLevel = 1
    ),
    SUS2(
        displayName = "挂二和弦",
        englishName = "Suspended 2nd",
        symbol = "sus2",
        intervals = listOf(0, 2, 7),
        description = "空灵而开放。用大二度替代三度音，和弦失去明确的大/小调色彩。" +
            "在流行音乐中极为常见，带来一种「飘浮」般的质感。" +
            "听感介于大调和小调之间——既不快乐也不悲伤，而是空旷。",
        opennessLevel = 2
    ),
    SUS4(
        displayName = "挂四和弦",
        englishName = "Suspended 4th",
        symbol = "sus4",
        intervals = listOf(0, 5, 7),
        description = "紧张而悬而未决。用纯四度替代三度音，和弦渴望解决回三度音。" +
            "经典的应用场景是在属和弦上制造张力后解决到主和弦。" +
            "比挂二更有「推力」，因为它离三度音更远——向上推了一步。",
        opennessLevel = 3
    ),
    SUS2_SUS4(
        displayName = "双挂和弦",
        englishName = "Suspended 2nd & 4th",
        symbol = "sus2sus4",
        intervals = listOf(0, 2, 5, 7),
        description = "极度开放和模糊。同时包含二度和四度，省略三度音。" +
            "这是一个四音和弦（比其他三和弦多一个音），产生的音响最为空旷、" +
            "悬浮。在现代爵士和氛围音乐中偶有出现。",
        opennessLevel = 4
    );

    companion object {
        /** 所有挂留和弦品质。 */
        val ALL: List<SuspendedChordQuality> = entries.toList()

        /**
         * 按难度返回可用的品质集合。
         * - 初级：大三 + 挂二 + 挂四（核心对比：有色彩 vs 无色彩）
         * - 中级：+ 小三（四种三和弦全面对比）
         * - 高级：+ 双挂（全部 5 种，四音双挂最难辨识）
         */
        fun forDifficulty(difficulty: SuspendedChordDifficulty): List<SuspendedChordQuality> = when (difficulty) {
            SuspendedChordDifficulty.BEGINNER -> listOf(MAJOR_TRIAD, SUS2, SUS4)
            SuspendedChordDifficulty.INTERMEDIATE -> listOf(MAJOR_TRIAD, MINOR_TRIAD, SUS2, SUS4)
            SuspendedChordDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与和弦种类）
 */
enum class SuspendedChordDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "大三 / 挂二 / 挂四（3 选项）· 有色彩 vs 无色彩"),
    INTERMEDIATE("中级", "+ 小三（4 选项）· 四种三和弦全面对比"),
    ADVANCED("高级", "全部 5 种（5 选项）· 双挂和弦最难辨识");

    companion object {
        val ALL: List<SuspendedChordDifficulty> = entries.toList()
    }
}

/**
 * 挂留和弦品质听辨训练题目。
 *
 * @param quality 和弦品质（大三/小三/挂二/挂四/双挂）
 * @param rootMidi 根音 MIDI 音符号（决定和弦的实际音高）
 * @param rootName 根音名（如 "C", "G"）
 * @param difficulty 难度
 * @param midiNotes 和弦的 MIDI 音符号列表（从低到高排列，3 或 4 个音）
 * @param answerChoices 所有选项（和弦品质显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（和弦品质显示名）
 */
data class SuspendedChordQuestion(
    val quality: SuspendedChordQuality,
    val rootMidi: Int,
    val rootName: String,
    val difficulty: SuspendedChordDifficulty,
    val midiNotes: List<Int>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(midiNotes.size in 3..4) { "挂留和弦应有 3 或 4 个音符，实际 ${midiNotes.size}" }
        require(midiNotes.all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 完整描述（如 "C 挂二和弦 (sus2)"）。 */
    val fullDescription: String
        get() = "$rootName ${quality.displayName} (${quality.symbol})"

    /** 和弦品质描述。 */
    val qualityDescription: String get() = quality.description
}

/**
 * 一次答题结果。
 */
data class SuspendedChordAnswerRecord(
    val question: SuspendedChordQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
