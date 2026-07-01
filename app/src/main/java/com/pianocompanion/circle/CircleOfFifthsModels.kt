package com.pianocompanion.circle

/**
 * 五度圈交互工具数据模型（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 五度圈（Circle of Fifths）是音乐理论中最核心的可视化工具之一：将 12 个调性
 * 按照纯五度关系排列成一个圆环。顺时针方向每移动一格增加一个升号，
 * 逆时针方向每移动一格增加一个降号。关系大小调（如 C 大调 / a 小调）共享
 * 同一调号，位于圆环的同一位置（大调在外环、小调在内环）。
 *
 * 本模块提供：
 * - 调号（升号/降号数量与具体音名）
 * - 调内顺阶三和弦（罗马数字分析）
 * - 关系调、近关系调
 * - 正确的等音记谱（diatonic spelling）
 */

/**
 * 调式：大调或小调。
 */
enum class CircleMode(val displayName: String) {
    MAJOR("大调"),
    MINOR("小调")
}

/**
 * 三和弦性质。
 */
enum class ChordQuality(val symbol: String, val displayName: String) {
    MAJOR("", "大三和弦"),
    MINOR("m", "小三和弦"),
    DIMINISHED("°", "减三和弦"),
    AUGMENTED("+", "增三和弦")
}

/**
 * 五度圈上的一个调：主音音级类（0 = C, ..., 11 = B）+ 调式。
 *
 * 音级类（pitch class）忽略八度，仅表示 12 平均律中的位置。
 */
data class CircleKey(
    val tonicPc: Int,
    val mode: CircleMode
) {
    val isMajor: Boolean get() = mode == CircleMode.MAJOR
    val isMinor: Boolean get() = mode == CircleMode.MINOR

    init {
        require(tonicPc in 0..11) { "主音音级类必须在 0..11 范围内，实际为 $tonicPc" }
    }
}

/**
 * 调号：升号数、降号数、被升高/降低的音名字母。
 *
 * 标准调号中升号与降号不会同时出现（其中一个为 0）。
 * 总升降号数 [totalAccidentals] 范围 0~7。
 *
 * 升号顺序（按出现的先后）：F, C, G, D, A, E, B
 * 降号顺序（按出现的先后）：B, E, A, D, G, C, F
 *
 * @param sharpenedLetters 被升高的音名字母集合
 * @param flattenedLetters 被降低的音名字母集合
 */
data class KeySignature(
    val sharpsCount: Int,
    val flatsCount: Int,
    val sharpenedLetters: Set<Char>,
    val flattenedLetters: Set<Char>
) {
    val isSharpKey: Boolean get() = sharpsCount > 0
    val isFlatKey: Boolean get() = flatsCount > 0
    val isNaturalKey: Boolean get() = sharpsCount == 0 && flatsCount == 0
    val totalAccidentals: Int get() = sharpsCount + flatsCount

    /**
     * 将升降号渲染为人类可读字符串，如 "F♯ C♯ G♯" 或 "B♭ E♭ A♭"，
     * 无升降号时返回 "无"。
     */
    fun displayString(): String {
        if (isNaturalKey) return "无"
        return if (isSharpKey) {
            SHARP_ORDER.take(sharpsCount).joinToString(" ") { "$it♯" }
        } else {
            FLAT_ORDER.take(flatsCount).joinToString(" ") { "$it♭" }
        }
    }

    companion object {
        /** 升号出现的顺序（五度圈升号方向）。 */
        val SHARP_ORDER = listOf('F', 'C', 'G', 'D', 'A', 'E', 'B')
        /** 降号出现的顺序（五度圈降号方向）。 */
        val FLAT_ORDER = listOf('B', 'E', 'A', 'D', 'G', 'C', 'F')

        /** C 大调 / a 小调：无升降号。 */
        val NATURAL = KeySignature(0, 0, emptySet(), emptySet())
    }
}

/**
 * 调内顺阶三和弦。
 *
 * @param degree 级数 1~7（I~VII）
 * @param romanNumeral 罗马数字标记（如 "I"、"ii"、"vii°"、"III+"）
 * @param rootPc 根音音级类
 * @param quality 和弦性质
 * @param noteNames 和弦各音的音名列表（含正确的等音记谱）
 * @param displayName 和弦显示名（如 "C"、"Dm"、"B°"）
 * @param midiNotes 和弦 MIDI 音符（用于音频合成），已钳位到钢琴范围
 */
data class DiatonicChord(
    val degree: Int,
    val romanNumeral: String,
    val rootPc: Int,
    val quality: ChordQuality,
    val noteNames: List<String>,
    val displayName: String,
    val midiNotes: List<Int>
)

/**
 * 调性的完整信息。
 *
 * @param key 原始调性
 * @param displayName 完整显示名（如 "C大调"、"a小调"、"F♯大调"）
 * @param tonicName 主音显示名（如 "C"、"a"、"F♯"）
 * @param signature 调号
 * @param preferFlats 是否偏好降号记法
 * @param position 圆环位置（0~11，0 = 顶部 C/a，顺时针递增）
 * @param angleDegrees 圆环角度（0° = 顶部，顺时针，用于 UI 绘制）
 * @param scalePcs 音阶音级类列表（上行 7 个音，含主音，不含八度）
 * @param scaleNoteNames 音阶各音名列表（正确等音记谱）
 * @param scaleMidiNotes 音阶 MIDI 音符（用于音频合成）
 * @param relativeKey 关系调（大调→关系小调，小调→关系大调）
 * @param relativeDisplayName 关系调显示名
 */
data class KeyInfo(
    val key: CircleKey,
    val displayName: String,
    val tonicName: String,
    val signature: KeySignature,
    val preferFlats: Boolean,
    val position: Int,
    val angleDegrees: Double,
    val scalePcs: List<Int>,
    val scaleNoteNames: List<String>,
    val scaleMidiNotes: List<Int>,
    val relativeKey: CircleKey,
    val relativeDisplayName: String
)
