package com.pianocompanion.progression

import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.chord.ChordType
import com.pianocompanion.chord.ChordInversion

/**
 * 和弦进行数据模型（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 本模块提供常见和弦进行的完整参考库。和弦进行是音乐中一系列和弦按特定顺序排列的
 * 模式，是伴奏、即兴、编曲和作曲的基础。通过罗马数字分析（Roman numeral analysis），
 * 同一个进行模板可以在任意调性中实例化。
 *
 * 罗马数字约定（大调）:
 * - I = 主和弦（大三和弦）
 * - ii = 上主和弦（小三和弦）
 * - iii = 中和弦（小三和弦）
 * - IV = 下属和弦（大三和弦）
 * - V = 属和弦（大三和弦）
 * - vi = 下中和弦（小三和弦，关系小调主和弦）
 * - vii° = 导和弦（减三和弦）
 *
 * 罗马数字约定（小调，自然小调）:
 * - i = 主和弦（小三和弦）
 * - ii° = 上主和弦（减三和弦）
 * - III = 中和弦（大三和弦）
 * - iv = 下属和弦（小三和弦）
 * - v = 属和弦（小三和弦，自然小调）/ V（和声小调，大三和弦）
 * - VI = 下中和弦（大三和弦）
 * - VII = 下主和弦（大三和弦，自然小调）/ vii°（和声小调，减三和弦）
 */

/**
 * 调式模式：大调或小调。
 * 决定罗马数字 → 具体和弦类型的映射规则。
 */
enum class ProgressionMode(val displayName: String, val symbol: String) {
    MAJOR("大调", ""),
    MINOR("小调", "m")
}

/**
 * 和弦进行的音乐功能分类。
 *
 * 不同风格的音乐使用不同的典型进行模式。
 */
enum class ProgressionGenre(val displayName: String) {
    POP("流行"),
    JAZZ("爵士"),
    CLASSICAL("古典"),
    BLUES("蓝调"),
    FOLK("民歌"),
    ROCK("摇滚")
}

/**
 * 罗马数字音阶级数。
 *
 * @param scaleDegree 音阶级数（0 = 主音/I, 1 = 上主音/ii, ..., 6 = 下主音/vii）
 * @param numeral 罗马数字字符串（如 "I", "ii", "V7", "vi", "iv"）
 * @param isSeventh 是否为七和弦（影响和弦类型选择）
 */
data class RomanNumeral(
    val scaleDegree: Int,
    val numeral: String,
    val isSeventh: Boolean = false,
    /**
     * 显式指定的和弦类型覆盖（可选）。
     *
     * 用于非顺阶和弦，例如蓝调中所有和弦为属七和弦（I7=dominant 7 而非 maj7）。
     * 为 null 时使用罗马数字大小写推断和弦质量。
     */
    val explicitType: ChordType? = null
) {
    init {
        require(scaleDegree in 0..6) { "scaleDegree must be 0-6, was $scaleDegree" }
    }

    companion object {
        /** 大调自然音阶和弦的罗马数字（三和弦） */
        val MAJOR_TRIADS = listOf(
            RomanNumeral(0, "I"),
            RomanNumeral(1, "ii"),
            RomanNumeral(2, "iii"),
            RomanNumeral(3, "IV"),
            RomanNumeral(4, "V"),
            RomanNumeral(5, "vi"),
            RomanNumeral(6, "vii°")
        )

        /** 小调自然音阶和弦的罗马数字（三和弦） */
        val MINOR_TRIADS = listOf(
            RomanNumeral(0, "i"),
            RomanNumeral(1, "ii°"),
            RomanNumeral(2, "III"),
            RomanNumeral(3, "iv"),
            RomanNumeral(4, "v"),
            RomanNumeral(5, "VI"),
            RomanNumeral(6, "VII")
        )

        /** 小调和声小调的 V 和 vii° 使用大三和弦/减三和弦 */
        val HARMONIC_MINOR_V = RomanNumeral(4, "V")
        val HARMONIC_MINOR_VII = RomanNumeral(6, "vii°")
    }
}

/**
 * 进行中的一个和弦位置。
 *
 * 由罗马数字 + 调性根音实例化后得到具体的和弦发音。
 *
 * @param romanNumeral 罗马数字标记
 * @param voicing 具体的和弦发音（MIDI 音符等）
 * @param measureIndex 所在小节（从 0 开始）
 */
data class ProgressionChord(
    val romanNumeral: RomanNumeral,
    val voicing: com.pianocompanion.chord.ChordVoicing,
    val measureIndex: Int
)

/**
 * 和弦进行模板（抽象的、与调性无关的进行模式）。
 *
 * @param id 唯一标识符
 * @param name 进行名称（如 "I-V-vi-IV"）
 * @param displayName 显示名称（如 "流行万能进行"）
 * @param description 描述说明
 * @param genre 音乐风格分类
 * @param mode 调式（大调/小调）
 * @param numerals 罗马数字序列
 * @param exampleKey 示例调性根音
 * @param beatsPerChord 每个和弦持续的拍数（默认 4 拍 = 1 小节）
 */
data class ProgressionTemplate(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String,
    val genre: ProgressionGenre,
    val mode: ProgressionMode,
    val numerals: List<RomanNumeral>,
    val exampleKey: ChordRoot,
    val beatsPerChord: Int = 4
) {
    /** 进行中的和弦数量。 */
    val chordCount: Int get() = numerals.size

    /** 罗马数字序列的字符串表示（如 "I – V – vi – IV"）。 */
    val numeralDisplay: String
        get() = numerals.joinToString(" – ") { it.numeral }
}

/**
 * 完整的进行实例（在特定调性中的具体和弦序列）。
 *
 * @param template 来源模板
 * @param key 调性根音
 * @param chords 具体和弦列表（已实例化到调性）
 * @param preferFlats 是否使用降号记法
 */
data class ProgressionInstance(
    val template: ProgressionTemplate,
    val key: ChordRoot,
    val chords: List<ProgressionChord>,
    val preferFlats: Boolean = false
) {
    /** 进行名称（含调性，如 "C大调 I-V-vi-IV"）。 */
    val fullName: String
        get() {
            val keyName = if (preferFlats) key.flatName else key.sharpName
            val modeName = if (template.mode == ProgressionMode.MINOR) keyName.lowercase() else keyName
            return "$modeName ${template.name}"
        }
}
