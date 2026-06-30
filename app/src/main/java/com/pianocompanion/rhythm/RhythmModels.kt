package com.pianocompanion.rhythm

/**
 * 节奏训练（Rhythm Training）数据模型。
 *
 * 本文件包含所有与节奏训练相关的音乐理论和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **节奏型（RhythmPattern）**：一段由不同时值音符/休止符组成的节奏序列
 * - **音符起音（OnsetTime）**：某个音符在时间轴上的绝对位置（毫秒）
 * - **敲击匹配（TapMatchResult）**：将用户敲击时间与目标节奏比较，计算准确度
 */
// ═══════════════════════════════════════════════════════════════
// 时值类型
// ═══════════════════════════════════════════════════════════════

/**
 * 音符时值类型（以拍为单位）。
 *
 * @param beats 该时值占多少拍（4/4拍号下，四分音符 = 1.0 拍）
 * @param displayName 中文显示名称
 * @param displaySymbol 简化显示符号（用于 UI 文本）
 */
enum class RhythmDuration(
    val beats: Double,
    val displayName: String,
    val displaySymbol: String
) {
    HALF(2.0, "二分音符", "𝅗𝅥"),
    DOTTED_QUARTER(1.5, "附点四分音符", "♩."),
    QUARTER(1.0, "四分音符", "♩"),
    EIGHTH(0.5, "八分音符", "♪"),
    SIXTEENTH(0.25, "十六分音符", "𝅘𝅥𝅯"),
    QUARTER_REST(1.0, "四分休止", "𝄽"),
    EIGHTH_REST(0.5, "八分休止", "𝄾");

    /** 是否为休止符。 */
    val isRest: Boolean get() = this == QUARTER_REST || this == EIGHTH_REST

    companion object {
        /** 非休止符的时值。 */
        val NOTES = listOf(HALF, DOTTED_QUARTER, QUARTER, EIGHTH, SIXTEENTH)
    }
}

// ═══════════════════════════════════════════════════════════════
// 难度等级
// ═══════════════════════════════════════════════════════════════

/**
 * 节奏训练难度等级。
 * - [BEGINNER] 初级：仅四分音符和二分音符
 * - [INTERMEDIATE] 中级：加入八分音符、附点四分
 * - [ADVANCED] 高级：加入十六分音符、休止符
 */
enum class RhythmDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

// ═══════════════════════════════════════════════════════════════
// 节奏事件与模式
// ═══════════════════════════════════════════════════════════════

/**
 * 节奏序列中的一个事件（音符或休止）。
 *
 * @param duration 时值类型
 * @param midiNote 该音符的 MIDI 编号（休止符时忽略，默认 C4 = 60）
 */
data class RhythmEvent(
    val duration: RhythmDuration,
    val midiNote: Int = 60
) {
    /** 是否为休止。 */
    val isRest: Boolean get() = duration.isRest

    /** 该事件占多少拍。 */
    val beats: Double get() = duration.beats
}

/**
 * 音符在时间轴上的绝对位置。
 *
 * @param onsetMs 起始时间（毫秒，从模式开始算起）
 * @param durationMs 持续时间（毫秒）
 * @param isRest 是否为休止
 * @param midiNote MIDI 音符编号
 */
data class OnsetTime(
    val onsetMs: Long,
    val durationMs: Long,
    val isRest: Boolean,
    val midiNote: Int
)

/**
 * 完整的节奏型（通常为一个小节）。
 *
 * @param events 事件序列（按时间顺序排列）
 * @param tempoBpm 速度（BPM）
 * @param beatsPerMeasure 每小节拍数（默认 4）
 */
data class RhythmPattern(
    val events: List<RhythmEvent>,
    val tempoBpm: Int = 90,
    val beatsPerMeasure: Int = 4
) {
    /** 每拍毫秒数。 */
    val msPerBeat: Long get() = (60_000.0 / tempoBpm).toLong()

    /** 总拍数。 */
    val totalBeats: Double get() = events.sumOf { it.beats }

    /** 总时长（毫秒）。 */
    val totalDurationMs: Long get() = (totalBeats * msPerBeat).toLong()

    /**
     * 将事件序列转换为绝对时间轴上的起音列表。
     * @return 非休止事件的 [OnsetTime] 列表（用于敲击匹配）
     */
    fun toOnsetTimes(): List<OnsetTime> {
        val result = mutableListOf<OnsetTime>()
        var currentBeat = 0.0
        for (event in events) {
            val onsetMs = (currentBeat * msPerBeat).toLong()
            val durationMs = (event.beats * msPerBeat).toLong()
            result.add(OnsetTime(onsetMs, durationMs, event.isRest, event.midiNote))
            currentBeat += event.beats
        }
        return result
    }

    /**
     * 获取所有非休止事件的起音列表（用户需要敲击的目标）。
     */
    fun toTapTargets(): List<OnsetTime> = toOnsetTimes().filter { !it.isRest }

    /** 显示名称（符号序列）。 */
    val displaySymbols: String
        get() = events.joinToString(" ") { it.duration.displaySymbol }

    /** 描述性文本（用于 UI）。 */
    val description: String
        get() = events.joinToString(" ") { it.duration.displayName }
}
