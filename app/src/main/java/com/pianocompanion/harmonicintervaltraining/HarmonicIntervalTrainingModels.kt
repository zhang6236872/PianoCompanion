package com.pianocompanion.harmonicintervaltraining

/**
 * 和声音程辨识训练（Harmonic Interval Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和声音程（Harmonic Interval）**：两个音**同时**演奏，用户需要辨识两个音之间的
 *   音程类型（如大三度、纯五度、三全音等）。
 * - 与旋律音程训练的区别：
 *   - **旋律音程（Melodic Interval）**：两个音**先后**演奏，关注旋律跳跃距离
 *   - **和声音程（Harmonic Interval）**：两个音**同时**演奏，关注和弦色彩的"浓度"
 *     和谐/不协和感，训练对音程色彩的深层感知
 *
 * 本模块支持 8 种常见和声音程：
 *   1. MINOR_THIRD（小三度）3 半音 — 柔和暗淡，小调色彩
 *   2. MAJOR_THIRD（大三度）4 半音 — 明亮温暖，大调色彩
 *   3. PERFECT_FOURTH（纯四度）5 半音 — 悬浮开阔
 *   4. TRITONE（三全音）6 半音 — 紧张不协和
 *   5. PERFECT_FIFTH（纯五度）7 半音 — 空洞开放，完全协和
 *   6. MINOR_SIXTH（小六度）8 半音 — 丰富柔和
 *   7. MAJOR_SIXTH（大六度）9 半音 — 明亮甜美
 *   8. OCTAVE（纯八度）12 半音 — 融合统一，最高协和度
 */

/**
 * 和声音程类型（辨识目标）。
 *
 * 每种音程对应两个音之间的半音数。
 * 音频渲染时，下方音固定为 C4，上方音 = C4 + [semitones]。
 *
 * @param englishName 英文名（如 "Major Third"）
 * @param displayName 中文名（如 "大三度"）
 * @param shortSymbol 简写符号（如 "M3"）
 * @param semitones 半音数（如 4 表示大三度）
 * @param emoji 表情符号（UI 图标）
 * @param description 音程描述（答题后的教学反馈）
 * @param consonance 协和度等级（0=极度不协和 → 4=完全协和）
 */
enum class HarmonicInterval(
    val englishName: String,
    val displayName: String,
    val shortSymbol: String,
    val semitones: Int,
    val emoji: String,
    val description: String,
    val consonance: Int
) {
    MINOR_THIRD(
        englishName = "Minor Third",
        displayName = "小三度",
        shortSymbol = "m3",
        semitones = 3,
        emoji = "🌑",
        description = "小三度（3 半音）：柔和暗淡，是小调色彩的灵魂。两个音叠在一起产生忧伤、内敛的声响。",
        consonance = 2
    ),
    MAJOR_THIRD(
        englishName = "Major Third",
        displayName = "大三度",
        shortSymbol = "M3",
        semitones = 4,
        emoji = "☀️",
        description = "大三度（4 半音）：明亮温暖，是大调色彩的根基。两个音叠在一起产生愉悦、明朗的声响。",
        consonance = 3
    ),
    PERFECT_FOURTH(
        englishName = "Perfect Fourth",
        displayName = "纯四度",
        shortSymbol = "P4",
        semitones = 5,
        emoji = "🌊",
        description = "纯四度（5 半音）：悬浮开阔，既有协和感又略带悬念。在功能和声中处于协和与不协和的边界。",
        consonance = 2
    ),
    TRITONE(
        englishName = "Tritone",
        displayName = "三全音",
        shortSymbol = "TT",
        semitones = 6,
        emoji = "⚡",
        description = "三全音（6 半音）：紧张刺耳，中世纪被称为「音乐中的魔鬼」（diabolus in musica）。最经典的不协和音程。",
        consonance = 0
    ),
    PERFECT_FIFTH(
        englishName = "Perfect Fifth",
        displayName = "纯五度",
        shortSymbol = "P5",
        semitones = 7,
        emoji = "🏔️",
        description = "纯五度（7 半音）：空洞开放，完全协和。是和弦的骨架音程，听起来稳定而坚定。",
        consonance = 4
    ),
    MINOR_SIXTH(
        englishName = "Minor Sixth",
        displayName = "小六度",
        shortSymbol = "m6",
        semitones = 8,
        emoji = "🍃",
        description = "小六度（8 半音）：丰富柔和，带有含蓄的温暖感。在爵士乐中经常使用。",
        consonance = 2
    ),
    MAJOR_SIXTH(
        englishName = "Major Sixth",
        displayName = "大六度",
        shortSymbol = "M6",
        semitones = 9,
        emoji = "🌟",
        description = "大六度（9 半音）：明亮甜美，如同清晨的阳光。是非常悦耳的协和音程。",
        consonance = 3
    ),
    OCTAVE(
        englishName = "Octave",
        displayName = "纯八度",
        shortSymbol = "P8",
        semitones = 12,
        emoji = "🪞",
        description = "纯八度（12 半音）：融合统一，两个音听起来几乎是同一个音，只是高低不同。最高协和度。",
        consonance = 4
    );

    init {
        check(semitones in 1..24) { "$englishName: semitones=$semitones 必须在 1..24" }
        check(consonance in 0..4) { "$englishName: consonance=$consonance 必须在 0..4" }
    }

    /** 完整标识（如 "小三度 (m3)"）。 */
    val fullLabel: String get() = "$displayName ($shortSymbol)"

    /** 摘要（如 "大三度 · 4 半音"）。 */
    val summary: String get() = "$displayName · $semitones 半音"

    companion object {
        val ALL: List<HarmonicInterval> = entries.toList()

        /** 初级音程：3 种差异最大的协和音程（八度/纯五度/大三度）。 */
        val BEGINNER_INTERVALS: List<HarmonicInterval> = listOf(OCTAVE, PERFECT_FIFTH, MAJOR_THIRD)

        /** 中级音程：5 种（加入小三度/纯四度），区分大/小三度。 */
        val INTERMEDIATE_INTERVALS: List<HarmonicInterval> = listOf(
            OCTAVE, PERFECT_FIFTH, MAJOR_THIRD, MINOR_THIRD, PERFECT_FOURTH
        )

        /** 高级音程：全部 8 种（含三全音/大小六度），考验精细色彩辨识。 */
        val ADVANCED_INTERVALS: List<HarmonicInterval> = ALL

        /**
         * 按难度返回可用音程集合。
         * - 初级：3 种差异最大的协和音程（八度/纯五度/大三度）
         * - 中级：5 种（加入小三度/纯四度），开始区分大/小三度
         * - 高级：全部 8 种（含三全音/大小六度），考验精细色彩辨识
         */
        fun forDifficulty(difficulty: HarmonicIntervalDifficulty): List<HarmonicInterval> = when (difficulty) {
            HarmonicIntervalDifficulty.BEGINNER -> BEGINNER_INTERVALS
            HarmonicIntervalDifficulty.INTERMEDIATE -> INTERMEDIATE_INTERVALS
            HarmonicIntervalDifficulty.ADVANCED -> ADVANCED_INTERVALS
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和音程列表）
 * @param choiceCount 该难度的选项数量
 */
enum class HarmonicIntervalDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种差异最大的协和音程（3 选项）· 纯八度 / 纯五度 / 大三度", 3),
    INTERMEDIATE("中级", "5 种音程含大小三度（5 选项）· 加入小三度 / 纯四度", 5),
    ADVANCED("高级", "全部 8 种含不协和音程（8 选项）· 细致色彩辨识", 8);

    companion object {
        val ALL: List<HarmonicIntervalDifficulty> = entries.toList()
    }
}

/**
 * 和声音程辨识训练题目。
 *
 * @param interval 正确的音程类型
 * @param difficulty 难度
 * @param lowerMidi 下方音 MIDI 编号（默认 C4=60）
 * @param answerChoices 所有选项（音程名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class HarmonicIntervalQuestion(
    val interval: HarmonicInterval,
    val difficulty: HarmonicIntervalDifficulty,
    val lowerMidi: Int = DEFAULT_LOWER_MIDI,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "大三度 (M3) · 4 半音"）。 */
    val fullName: String get() = interval.summary

    /** 上方音 MIDI 编号。 */
    val upperMidi: Int get() = lowerMidi + interval.semitones

    companion object {
        /** 下方音默认 MIDI（C4）。 */
        const val DEFAULT_LOWER_MIDI = 60
    }
}

/**
 * 一次答题结果。
 */
data class HarmonicIntervalAnswerRecord(
    val question: HarmonicIntervalQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
