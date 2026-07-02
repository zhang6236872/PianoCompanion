package com.pianocompanion.cadence

import com.pianocompanion.chord.ChordInversion
import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.chord.ChordType
import com.pianocompanion.chord.ChordVoicing

/**
 * 终止式参考库数据模型（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 终止式（cadence）是乐句或乐段结尾的和弦进行，类似于语言中的标点符号。
 * 它们为听众提供音乐的「呼吸点」和「句读感」，是和声学最核心的概念之一。
 *
 * 本模块涵盖 6 种最常见的终止式类型，支持 12 个大调和小调（和声小调），
 * 每种终止式都包含具体的和弦发音（voicing）、罗马数字分析和文字说明。
 */

/**
 * 终止式类型。
 *
 * - [PERFECT_AUTHENTIC] 完满完全终止 (PAC): V(7) → I，两个和弦均为原位，
 *   最后一个和弦的高声部落在主音上。这是所有终止式中「最完满」的，
 *   给人以完全结束的确定感。
 * - [IMPERFECT_AUTHENTIC] 不完满完全终止 (IAC): V → I 但不完全满足 PAC 的条件
 *   （使用转位、或高声部不在主音上）。感觉结束但不那么绝对。
 * - [PLAGAL] 变格终止 (PC): IV → I，常见于赞美诗结尾（「阿门」终止式）。
 *   比完全终止柔和，有虔诚庄严的感觉。
 * - [DECEPTIVE] 阻碍终止 / 伪终止 (DC): V → vi (大调) 或 V → VI (小调)。
 *   听众期待 V → I 却得到了 vi，产生「被骗」的意外感，常用于延长乐句。
 * - [HALF] 半终止 (HC): 任意和弦 → V。乐句停留在属和弦上，感觉悬而未决，
 *   像句子中的逗号，期待后续的解决。
 * - [PHRYGIAN_HALF] 弗里几亚半终止: iv₆ → V（仅小调）。下行半音的低声部
 *   （如 A♭ → G）赋予西班牙/弗拉明戈色彩，巴洛克时期常用。
 */
enum class CadenceType(
    val displayName: String,
    val abbreviation: String,
    val category: CadenceCategory,
    val description: String,
    val supportsMinor: Boolean = true
) {
    PERFECT_AUTHENTIC(
        "完满完全终止",
        "PAC",
        CadenceCategory.AUTHENTIC,
        "V⁷ → I（或 V → I），两和弦均原位，高声部解决到主音。" +
            "所有终止式中最确定、最完满的结束感，常用于乐段或全曲结尾。"
    ),
    IMPERFECT_AUTHENTIC(
        "不完满完全终止",
        "IAC",
        CadenceCategory.AUTHENTIC,
        "V → I 但不完全满足完满条件：使用转位（V⁶ 或 I⁶），" +
            "或高声部不在主音上。结束感比 PAC 柔和、不那么绝对。"
    ),
    PLAGAL(
        "变格终止",
        "PC",
        CadenceCategory.PLAGAL,
        "IV → I（大调）或 iv → i（小调），即下属和弦解决到主和弦。" +
            "又称「阿门终止式」，因赞美诗结尾常用此进行而得名。庄严肃穆。"
    ),
    DECEPTIVE(
        "阻碍终止",
        "DC",
        CadenceCategory.DECEPTIVE,
        "V → vi（大调）或 V → VI（小调）。听众期待 V → I 的完满解决，" +
            "却意外进行到 vi/VI，产生「被骗」的悬置感。常用于避免过早结束、延长乐句。"
    ),
    HALF(
        "半终止",
        "HC",
        CadenceCategory.HALF,
        "任意和弦 → V。乐句停留在属和弦上，感觉悬而未决、期待后续解决。" +
            "如同句子中的逗号，是最常用的中间停顿方式。"
    ),
    PHRYGIAN_HALF(
        "弗里几亚半终止",
        "PHC",
        CadenceCategory.HALF,
        "iv⁶ → V（仅小调）。下属第一转位解决到属和弦，低声部下行半音" +
            "（如 A♭ → G），赋予浓郁的西班牙/弗拉明戈色彩，巴洛克时期常用。",
        supportsMinor = true
    );

    /**
     * 该终止式是否支持大调。
     */
    val supportsMajor: Boolean get() = this != PHRYGIAN_HALF
}

/**
 * 终止式大类，用于 UI 分组。
 */
enum class CadenceCategory(val displayName: String) {
    AUTHENTIC("完全终止类"),
    PLAGAL("变格终止类"),
    DECEPTIVE("阻碍终止类"),
    HALF("半终止类")
}

/**
 * 调式（大调 / 和声小调）。
 */
enum class CadenceMode(val displayName: String) {
    MAJOR("大调"),
    HARMONIC_MINOR("和声小调")
}

/**
 * 终止式中的一个和弦步骤。
 *
 * @param degree 音阶级数（0 = 主音 I/i, 1 = 上主音 II/ii, …, 6 = 导音 VII/vii°）
 * @param chordType 和弦类型（大三、小三、属七等）
 * @param inversion 转位
 * @param romanNumeral 罗马数字标记（如 "V⁷"、"I"、"iv⁶"）
 * @param voicing 已构建好的和弦发音（MIDI 音符列表）
 */
data class CadenceStep(
    val degree: Int,
    val chordType: ChordType,
    val inversion: ChordInversion,
    val romanNumeral: String,
    val voicing: ChordVoicing
)

/**
 * 一个终止式的完整实例化：指定调性 + 终止式类型 → 具体的和弦序列。
 *
 * @param type 终止式类型
 * @param keyRoot 调性主音
 * @param mode 调式
 * @param steps 和弦步骤列表（通常 2 个和弦）
 * @param romanNumeralSummary 罗马数字摘要（如 "V⁷ → I"）
 * @param keyName 调性名称（如 "C大调"、"a和声小调"）
 * @param preferFlats 是否使用降号记法
 */
data class CadenceInstance(
    val type: CadenceType,
    val keyRoot: ChordRoot,
    val mode: CadenceMode,
    val steps: List<CadenceStep>,
    val romanNumeralSummary: String,
    val keyName: String,
    val preferFlats: Boolean
) {
    /** 终止式中的和弦数量。 */
    val chordCount: Int get() = steps.size

    /** 最后一个和弦（解决和弦或停留和弦）。 */
    val finalChord: CadenceStep? get() = steps.lastOrNull()

    /** 所有和弦的 MIDI 音符合集（用于键盘可视化高亮）。 */
    val allMidiNotes: List<Int>
        get() = steps.flatMap { it.voicing.midiNotes }.distinct().sorted()
}
