package com.pianocompanion.intervaltraining

import com.pianocompanion.util.MusicUtils

/**
 * 音程听辨训练（Interval Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **音程（interval）**：两个音之间的距离，以半音数（semitones）衡量。
 *   识别音程是听觉训练最基础的技能——它是和弦听辨、旋律听写、即兴演奏的前提。
 * - **训练流程**：播放两个音（旋律性或和声性），用户从给定选项中
 *   选择正确的音程名称（如"大三度""纯五度"）。
 *
 * 难度分级（按音程集合的丰富程度）：
 * - **初级**：4 个基础协和音程（大二度 / 大三度 / 纯四度 / 纯五度）
 * - **中级**：6 个音程（加入小二度 / 小三度，需区分大小）
 * - **高级**：8 个音程（加入增四度 / 大六度 / 纯八度）
 *
 * 播放方向：
 * - **上行旋律**：先弹低音，再弹高音
 * - **下行旋律**：先弹高音，再弹低音
 * - **和声**：两个音同时弹奏
 */

/**
 * 音程类型枚举（13 种，对应 0-12 半音）。
 *
 * @param semitones 半音数（两音之间的绝对距离）
 * @param displayName 中文显示名（如"大三度"）
 * @param abbreviation 缩写（如"M3"，用于紧凑展示）
 * @param consonant 是否协和音程（协和更易听辨，适合初级）
 * @param description 听感描述
 */
enum class IntervalType(
    val semitones: Int,
    val displayName: String,
    val abbreviation: String,
    val consonant: Boolean,
    val description: String
) {
    PERFECT_UNISON(0, "纯一度", "P1", true, "完全相同的两个音，无距离感"),
    MINOR_SECOND(1, "小二度", "m2", false, "紧张刺耳，像警报/电影惊悚"),
    MAJOR_SECOND(2, "大二度", "M2", false, "略带紧张，像《祝你生日快乐》开头"),
    MINOR_THIRD(3, "小三度", "m3", true, "忧伤暗淡，像《欢乐颂》暗调版"),
    MAJOR_THIRD(4, "大三度", "M3", true, "明亮开朗，像《当你微笑》"),
    PERFECT_FOURTH(5, "纯四度", "P4", true, "空灵开阔，像《婚礼进行曲》开头"),
    AUGMENTED_FOURTH(6, "增四度", "A4", false, "极度不协和，魔鬼音程"),
    PERFECT_FIFTH(7, "纯五度", "P5", true, "坚定开阔，像《星球大战》开头"),
    MINOR_SIXTH(8, "小六度", "m6", true, "渴望向往，像《爱情故事》"),
    MAJOR_SIXTH(9, "大六度", "M6", true, "温暖向上，像《NBC 台标》/《我的邦妮》"),
    MINOR_SEVENTH(10, "小七度", "m7", false, "忧郁爵士，属七和弦的根七音"),
    MAJOR_SEVENTH(11, "大七度", "M7", false, "梦幻浮空，爵士色彩"),
    PERFECT_OCTAVE(12, "纯八度", "P8", true, "完全同质，像《骊歌》开头");

    companion object {
        /** 半音数 → 音程类型（0-12）。 */
        fun fromSemitones(semitones: Int): IntervalType? =
            entries.firstOrNull { it.semitones == semitones }

        /** 全部 13 种音程。 */
        val ALL: List<IntervalType> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * @param intervals 该难度可出题的音程集合（同时也是选项集合）
 * @param description 难度说明
 */
enum class IntervalDifficulty(
    val displayName: String,
    val intervals: List<IntervalType>,
    val description: String
) {
    BEGINNER(
        displayName = "初级",
        intervals = listOf(
            IntervalType.MAJOR_SECOND,
            IntervalType.MAJOR_THIRD,
            IntervalType.PERFECT_FOURTH,
            IntervalType.PERFECT_FIFTH
        ),
        description = "4 个基础协和音程（4 选项）"
    ),
    INTERMEDIATE(
        displayName = "中级",
        intervals = listOf(
            IntervalType.MINOR_SECOND,
            IntervalType.MAJOR_SECOND,
            IntervalType.MINOR_THIRD,
            IntervalType.MAJOR_THIRD,
            IntervalType.PERFECT_FOURTH,
            IntervalType.PERFECT_FIFTH
        ),
        description = "6 个音程，需区分大小二/三度（6 选项）"
    ),
    ADVANCED(
        displayName = "高级",
        intervals = listOf(
            IntervalType.MINOR_SECOND,
            IntervalType.MAJOR_SECOND,
            IntervalType.MINOR_THIRD,
            IntervalType.MAJOR_THIRD,
            IntervalType.AUGMENTED_FOURTH,
            IntervalType.PERFECT_FIFTH,
            IntervalType.MAJOR_SIXTH,
            IntervalType.PERFECT_OCTAVE
        ),
        description = "8 个音程，含增四度/大六度/八度（8 选项）"
    );

    /** 选项数量（= 音程集合大小）。 */
    val optionCount: Int get() = intervals.size

    companion object {
        val ALL: List<IntervalDifficulty> = entries.toList()
    }
}

/**
 * 音程播放方向。
 *
 * @param harmonic 是否和声（同时发声）；false = 旋律（先后发声）
 */
enum class PlayDirection(
    val displayName: String,
    val harmonic: Boolean,
    val description: String
) {
    ASCENDING("上行旋律", false, "先弹低音，再弹高音"),
    DESCENDING("下行旋律", false, "先弹高音，再弹低音"),
    HARMONIC("和声", true, "两个音同时弹奏");

    companion object {
        val ALL: List<PlayDirection> = entries.toList()
    }
}

/**
 * 音程听辨训练题目。
 *
 * @param interval 正确的音程类型
 * @param playDirection 播放方向
 * @param lowerMidi 较低音的 MIDI 编号
 * @param upperMidi 较高音的 MIDI 编号（= lowerMidi + semitones）
 * @param playOrder 播放顺序的 MIDI 音符列表（和声模式 = [lower, upper]；上行 = [lower, upper]；下行 = [upper, lower]）
 * @param options 选项列表（IntervalType，已打乱，含正确答案）
 */
data class IntervalQuestion(
    val interval: IntervalType,
    val playDirection: PlayDirection,
    val lowerMidi: Int,
    val upperMidi: Int,
    val playOrder: List<Int>,
    val options: List<IntervalType>
) {
    /** 正确答案（音程类型）。 */
    val correctAnswer: IntervalType get() = interval

    init {
        require(playOrder.isNotEmpty()) { "播放顺序不能为空" }
    }

    /** 较低音名称。 */
    val lowerNoteName: String get() = MusicUtils.midiToNoteName(lowerMidi)

    /** 较高音名称。 */
    val upperNoteName: String get() = MusicUtils.midiToNoteName(upperMidi)

    /** 音程详情（用于答题后教学反馈）。 */
    val intervalDetail: String get() = "${interval.displayName}（${interval.semitones}半音）${interval.abbreviation}"

    /** 音符详情描述。 */
    val noteDetail: String get() = "$lowerNoteName → $upperNoteName"
}

/**
 * 一次答题结果。
 */
data class IntervalAnswerRecord(
    val question: IntervalQuestion,
    val userAnswer: IntervalType,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: IntervalType? get() = if (isCorrect) null else question.correctAnswer
}
