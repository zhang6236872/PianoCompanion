package com.pianocompanion.melodymemory

import com.pianocompanion.util.MusicUtils

/**
 * 旋律记忆训练（Melody Memory / Melodic Contour Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **旋律走向（melodic contour）**：一段旋律中相邻音符之间的方向变化序列
 *   （上行 ↑ / 下行 ↓ / 同音 →）。识别旋律走向是听觉训练的基础技能——
 *   它训练大脑将听到的音高变化映射为空间方向，是旋律听写的前提。
 * - **训练流程**：播放一段短旋律（3-5 个音），用户从 4 个箭头走向选项中
 *   选择与所听旋律匹配的走向。
 *
 * 难度分级：
 * - **初级**：3 音旋律，仅二度音程（1-2 半音），只有上行/下行（无同音重复）
 * - **中级**：4 音旋律，含三度跳进（1-4 半音），允许同音重复
 * - **高级**：5 音旋律，含四度/五度大跳（1-7 半音），允许同音重复
 *
 * 速度分级：
 * - **慢速**：每音 550ms，便于初学者跟听
 * - **正常**：每音 380ms，接近真实演奏速度
 */

/**
 * 旋律走向方向。
 *
 * @param symbol 箭头符号（用于选项展示）
 * @param displayName 中文显示名
 */
enum class MelodicDirection(val symbol: String, val displayName: String) {
    UP("↑", "上行"),
    DOWN("↓", "下行"),
    SAME("→", "同音");

    companion object {
        /** 仅上行/下行（不含同音），用于初级难度。 */
        val UP_DOWN: List<MelodicDirection> = listOf(UP, DOWN)

        /** 全部三种方向（含同音），用于中/高级难度。 */
        val ALL: List<MelodicDirection> = entries.toList()
    }
}

/**
 * 旋律中两个相邻音符之间的音程走向。
 *
 * @param direction 走向方向
 * @param semitones 半音数（同音时为 0）
 */
data class ContourInterval(
    val direction: MelodicDirection,
    val semitones: Int
) {
    init {
        require(semitones >= 0) { "半音数不能为负: $semitones" }
        require(direction != MelodicDirection.SAME || semitones == 0) {
            "同音方向的半音数必须为 0，实际: $semitones"
        }
        require(direction == MelodicDirection.SAME || semitones > 0) {
            "上行/下行方向的半音数必须 > 0，实际: $semitones"
        }
    }

    /** 箭头符号。 */
    val arrow: String get() = direction.symbol

    /** 音程名称（中文）。 */
    val intervalName: String get() = intervalNameOf(semitones)

    companion object {
        /** 半音数 → 音程名称映射（0-12 半音）。 */
        fun intervalNameOf(semitones: Int): String = when (semitones) {
            0 -> "同音"
            1 -> "小二度"
            2 -> "大二度"
            3 -> "小三度"
            4 -> "大三度"
            5 -> "纯四度"
            6 -> "增四度"
            7 -> "纯五度"
            8 -> "小六度"
            9 -> "大六度"
            10 -> "小七度"
            11 -> "大七度"
            12 -> "纯八度"
            else -> "${semitones}半音"
        }
    }
}

/**
 * 难度等级。
 *
 * @param noteCount 旋律音符数
 * @param maxIntervalSemitones 最大允许音程（半音数）
 * @param allowSameDirection 是否允许同音重复方向
 * @param description 难度说明
 */
enum class MelodyDifficulty(
    val displayName: String,
    val noteCount: Int,
    val maxIntervalSemitones: Int,
    val allowSameDirection: Boolean,
    val description: String
) {
    BEGINNER(
        displayName = "初级",
        noteCount = 3,
        maxIntervalSemitones = 2,
        allowSameDirection = false,
        description = "3 音 · 二度音程（2 选项）"
    ),
    INTERMEDIATE(
        displayName = "中级",
        noteCount = 4,
        maxIntervalSemitones = 4,
        allowSameDirection = true,
        description = "4 音 · 含三度跳进（4 选项）"
    ),
    ADVANCED(
        displayName = "高级",
        noteCount = 5,
        maxIntervalSemitones = 7,
        allowSameDirection = true,
        description = "5 音 · 含四五度大跳（4 选项）"
    );

    companion object {
        val ALL: List<MelodyDifficulty> = entries.toList()

        /** 初级只有 2 个方向（上/下）× 2 个音程 = 4 种组合，使用 2 选 1 更友好。 */
        val optionCount: Int get() = 4
    }
}

/**
 * 旋律播放速度。
 *
 * @param noteDurationMs 每个音符的持续时长（毫秒）
 */
enum class MelodyTempo(val displayName: String, val noteDurationMs: Long, val description: String) {
    SLOW("慢速", 550L, "每音 550ms，便于跟听"),
    NORMAL("正常", 380L, "每音 380ms，接近演奏速度");

    companion object {
        val ALL: List<MelodyTempo> = entries.toList()
    }
}

/**
 * 旋律记忆训练题目。
 *
 * @param difficulty 难度
 * @param tempo 播放速度
 * @param startMidi 起始音 MIDI 编号
 * @param midiNotes 旋律全部音符的 MIDI 编号列表（按播放顺序）
 * @param contour 走向序列（相邻音符间的音程走向，长度 = noteCount - 1）
 * @param answerChoices 走向选项（箭头字符串，已打乱，含正确答案）
 * @param correctAnswer 正确走向字符串（箭头序列，如 "↑ ↑ ↓"）
 */
data class MelodyQuestion(
    val difficulty: MelodyDifficulty,
    val tempo: MelodyTempo,
    val startMidi: Int,
    val midiNotes: List<Int>,
    val contour: List<ContourInterval>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 音符数量。 */
    val noteCount: Int get() = midiNotes.size

    /** 正确走向的箭头序列。 */
    val contourArrows: String get() = contour.joinToString(" ") { it.arrow }

    /** 各音符名称（如 C4, D4, E4）。 */
    val noteNames: List<String> get() = midiNotes.map { MusicUtils.midiToNoteName(it) }

    /** 走向详情（箭头 + 音程名），用于答题后教学反馈。 */
    val contourDetail: String
        get() = contour.joinToString("  ") { "${it.arrow}${it.intervalName}" }

    /** 旋律的完整描述（音名序列）。 */
    val melodyDescription: String get() = noteNames.joinToString(" → ")
}

/**
 * 一次答题结果。
 */
data class MelodyAnswerRecord(
    val question: MelodyQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
