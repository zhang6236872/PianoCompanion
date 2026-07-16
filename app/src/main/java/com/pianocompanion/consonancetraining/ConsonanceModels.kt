package com.pianocompanion.consonancetraining

/**
 * 协和度辨识训练（Consonance & Dissonance Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **协和与不协和（Consonance & Dissonance）**：两个音同时或先后响起时产生的
 *   听觉感受。协和音程听起来稳定、融合、令人愉悦（有"解决"的安定感）；不协和音程
 *   听起来紧张、刺耳、有"需要解决"的倾向。这是理解对位法声部规则、和声张力/释放、
 *   终止式与音乐情绪起伏的最基础概念。
 *
 * - **三度分类法**（传统音乐理论）：
 *   - **完全协和（Perfect Consonance）**：纯八度(P8)、纯五度(P5)、纯四度(P4)。
 *     声音最空灵、最融合，几乎感觉不到两个独立的音。纯四度(P4)在现代和声语境中通常
 *     视为完全协和（注意：在严格的对位法中，纯四度在某些声部关系中视为不协和）。
 *     （纯一度 P1 = 同音，两个音完全相同，不作为辨识训练对象。）
 *   - **不完全协和（Imperfect Consonance）**：大三度(M3)、小三度(m3)、大六度(M6)、
 *     小六度(m6)。声音丰满而温暖，有色彩感，但仍感到协和——这是大小调色彩感的来源。
 *   - **不协和（Dissonance）**：大二度(M2)、小二度(m2)、大七度(M7)、小七度(m7)、
 *     增四度/减五度(A4/d5，即三全音/tritone)。声音尖锐、紧张，强烈倾向解决到协和音程。
 *     其中三全音 historically 被称为 "diabolus in musica"（音乐中的魔鬼）。
 *
 * 与 [com.pianocompanion.intervaltraining] 的区别：
 * - 音程听辨训练要求用户识别**确切的音程名称**（12+ 个类别）。
 * - 本训练要求用户判断音程的**协和度类别**（2~3 个大类）—— 一种更高层、更直觉的
 *   听觉感知技能，是理解音乐张力与情绪的基础。
 */

/**
 * 协和度类别（辨识目标）。
 *
 * @param englishName 英文名
 * @param displayName 中文名
 * @param emoji 表情符号
 * @param description 类别描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class ConsonanceCategory(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val description: String,
    val hint: String
) {
    PERFECT_CONSONANCE(
        englishName = "Perfect Consonance",
        displayName = "完全协和",
        emoji = "✨",
        description = "完全协和（Perfect Consonance）：纯八度、纯五度、纯四度。声音最空灵、最融合，几乎感觉不到两个独立的音在碰撞，有神圣、安定的感受。（纯一度 P1 为同音，不作训练对象。）",
        hint = "声音空灵融合，几乎听不出两个音在「打架」"
    ),
    IMPERFECT_CONSONANCE(
        englishName = "Imperfect Consonance",
        displayName = "不完全协和",
        emoji = "🌈",
        description = "不完全协和（Imperfect Consonance）：大三度、小三度、大六度、小六度。声音丰满温暖、有明确的色彩感（大调明亮 / 小调暗淡），但仍属于协和——这是大小调调性色彩的直接来源。",
        hint = "声音丰满有色彩，温暖但不刺耳，像和弦的「味道」"
    ),
    DISSONANCE(
        englishName = "Dissonance",
        displayName = "不协和",
        emoji = "⚡",
        description = "不协和（Dissonance）：大小二度、大小七度、三全音（增四度/减五度）。声音尖锐、紧张、有「需要解决」的强烈倾向。三全音曾被中世纪音乐家称为「音乐中的魔鬼」(diabolus in musica)。",
        hint = "声音刺耳紧张，有强烈的「想要解决」的冲动"
    );

    /** 完整标识（如 "完全协和 (Perfect Consonance)"）。 */
    val fullLabel: String get() = "$displayName ($englishName)"

    companion object {
        val ALL: List<ConsonanceCategory> = entries.toList()

        /**
         * 初级难度将「完全协和」与「不完全协和」合并为一个「协和」大类，
         * 与「不协和」形成二元对比——这是最基础、最直觉的听辨。
         */
        val BEGINNER_CONSONANT: List<ConsonanceCategory> =
            listOf(PERFECT_CONSONANCE, IMPERFECT_CONSONANCE)
    }
}

/**
 * 音乐音程（两个音之间的距离）。
 *
 * @param semitones 半音数（0-11，为一个八度内的简单音程）
 * @param abbreviation 简写标记（如 "P5"、"M3"、"TT"）
 * @param displayName 中文音程名
 * @param category 该音程的协和度类别
 */
enum class MusicInterval(
    val semitones: Int,
    val abbreviation: String,
    val displayName: String,
    val category: ConsonanceCategory
) {
    // 纯一度 (P1, semitones=0) 为同音，两个音完全相同，不作为辨识训练对象。
    MINOR_SECOND(1, "m2", "小二度", ConsonanceCategory.DISSONANCE),
    MAJOR_SECOND(2, "M2", "大二度", ConsonanceCategory.DISSONANCE),
    MINOR_THIRD(3, "m3", "小三度", ConsonanceCategory.IMPERFECT_CONSONANCE),
    MAJOR_THIRD(4, "M3", "大三度", ConsonanceCategory.IMPERFECT_CONSONANCE),
    PERFECT_FOURTH(5, "P4", "纯四度", ConsonanceCategory.PERFECT_CONSONANCE),
    TRITONE(6, "TT", "三全音", ConsonanceCategory.DISSONANCE),
    PERFECT_FIFTH(7, "P5", "纯五度", ConsonanceCategory.PERFECT_CONSONANCE),
    MINOR_SIXTH(8, "m6", "小六度", ConsonanceCategory.IMPERFECT_CONSONANCE),
    MAJOR_SIXTH(9, "M6", "大六度", ConsonanceCategory.IMPERFECT_CONSONANCE),
    MINOR_SEVENTH(10, "m7", "小七度", ConsonanceCategory.DISSONANCE),
    MAJOR_SEVENTH(11, "M7", "大七度", ConsonanceCategory.DISSONANCE);

    companion object {
        val ALL: List<MusicInterval> = entries.toList()

        /** 该协和度类别下的所有音程。 */
        fun forCategory(category: ConsonanceCategory): List<MusicInterval> =
            ALL.filter { it.category == category }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 该难度的选项数量（协和度大类数）
 * @param harmonic 是否用和声（同时发响）方式呈现；false = 旋律（先后发响）方式
 */
enum class ConsonanceDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val harmonic: Boolean
) {
    BEGINNER(
        "初级",
        "协和 vs 不协和 · 2 选项 · 旋律方式（先后发响）",
        2,
        harmonic = false
    ),
    INTERMEDIATE(
        "中级",
        "完全协和 vs 不完全协和 vs 不协和 · 3 选项 · 和声方式（同时发响）",
        3,
        harmonic = true
    ),
    ADVANCED(
        "高级",
        "完全协和 vs 不完全协和 vs 不协和 · 3 选项 · 和声方式 · 全音程含复合八度",
        3,
        harmonic = true
    );

    companion object {
        val ALL: List<ConsonanceDifficulty> = entries.toList()
    }
}

/**
 * 音频呈现方式。
 */
enum class Presentation {
    /** 旋律方式：两个音先后发响。 */
    MELODIC,

    /** 和声方式：两个音同时发响。 */
    HARMONIC
}

/**
 * 协和度辨识训练题目。
 *
 * @param interval 正确的音程
 * @param category 正确的协和度类别
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染）
 * @param baseMidi 基准 MIDI 音高（较低音）
 * @param octaveOffset 八度偏移（高级难度随机变化，使音程出现在不同音区）
 * @param presentation 音频呈现方式（旋律 / 和声）
 * @param answerChoices 所有选项（协和度类别名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class ConsonanceQuestion(
    val interval: MusicInterval,
    val category: ConsonanceCategory,
    val difficulty: ConsonanceDifficulty,
    val seed: Long,
    val baseMidi: Int,
    val octaveOffset: Int,
    val presentation: Presentation,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 较低音的 MIDI 音高（含八度偏移）。 */
    val lowerMidi: Int get() = baseMidi + octaveOffset * 12

    /** 较高音的 MIDI 音高。 */
    val higherMidi: Int get() = lowerMidi + interval.semitones

    /** 完整描述。 */
    val fullDescription: String
        get() = "${interval.displayName}（${interval.abbreviation}）→ ${category.displayName}"
}

/**
 * 一次答题结果。
 */
data class ConsonanceAnswerRecord(
    val question: ConsonanceQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
