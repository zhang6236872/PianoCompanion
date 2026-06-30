package com.pianocompanion.chord

/**
 * 和弦词典数据模型（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 本模块提供钢琴和弦的完整参考库，涵盖三和弦、挂留和弦、六和弦、
 * 七和弦、九和弦、加九和弦等 18 种常见和弦类型，支持 12 个根音、
 * 最多四种转位。
 */

/**
 * 和弦根音（音级类，0 = C, 1 = C#/Db, ..., 11 = B）。
 *
 * 同时提供升号(♯)和降号(♭)两种记法名称，用户可在设置中切换偏好。
 */
enum class ChordRoot(
    val pitchClass: Int,
    val sharpName: String,
    val flatName: String,
    val displayName: String
) {
    C(0, "C", "C", "C"),
    C_SHARP(1, "C♯", "D♭", "C♯"),
    D(2, "D", "D", "D"),
    E_FLAT(3, "E♭", "E♭", "E♭"),
    E(4, "E", "E", "E"),
    F(5, "F", "F", "F"),
    F_SHARP(6, "F♯", "G♭", "F♯"),
    G(7, "G", "G", "G"),
    A_FLAT(8, "A♭", "A♭", "A♭"),
    A(9, "A", "A", "A"),
    B_FLAT(10, "B♭", "B♭", "B♭"),
    B(11, "B", "B", "B");

    /**
     * 根据记谱偏好返回根音名称。
     * @param preferFlats true 时使用降号记法（如 D♭），false 时使用升号记法（如 C♯）。
     */
    fun name(preferFlats: Boolean = false): String =
        if (preferFlats) flatName else sharpName
}

/**
 * 和弦类型，定义和弦的音程结构。
 *
 * [intervals] 是相对于根音的半音偏移列表（不含根音本身，即根音隐含为 0）。
 * 例如大三和弦 = 根音 + 大三度(4) + 纯五度(7) → intervals = [4, 7]。
 *
 * [symbol] 是和弦标记符号（如 "maj7", "m", "°"）。
 * [category] 是和弦分类，用于 UI 分组显示。
 */
enum class ChordType(
    val intervals: List<Int>,
    val symbol: String,
    val displayName: String,
    val category: ChordCategory
) {
    // ── 三和弦 (Triads) ──
    MAJOR(listOf(4, 7), "", "大三和弦", ChordCategory.TRIAD),
    MINOR(listOf(3, 7), "m", "小三和弦", ChordCategory.TRIAD),
    DIMINISHED(listOf(3, 6), "°", "减三和弦", ChordCategory.TRIAD),
    AUGMENTED(listOf(4, 8), "+", "增三和弦", ChordCategory.TRIAD),

    // ── 挂留和弦 (Suspended) ──
    SUS2(listOf(2, 7), "sus2", "挂二和弦", ChordCategory.SUSPENDED),
    SUS4(listOf(5, 7), "sus4", "挂四和弦", ChordCategory.SUSPENDED),

    // ── 六和弦 (6th chords) ──
    MAJOR_6(listOf(4, 7, 9), "6", "大六和弦", ChordCategory.SIXTH),
    MINOR_6(listOf(3, 7, 9), "m6", "小六和弦", ChordCategory.SIXTH),

    // ── 七和弦 (7th chords) ──
    DOMINANT_7(listOf(4, 7, 10), "7", "属七和弦", ChordCategory.SEVENTH),
    MAJOR_7(listOf(4, 7, 11), "maj7", "大七和弦", ChordCategory.SEVENTH),
    MINOR_7(listOf(3, 7, 10), "m7", "小七和弦", ChordCategory.SEVENTH),
    DIMINISHED_7(listOf(3, 6, 9), "°7", "减七和弦", ChordCategory.SEVENTH),
    HALF_DIMINISHED_7(listOf(3, 6, 10), "ø7", "半减七和弦", ChordCategory.SEVENTH),

    // ── 九和弦 (9th chords) ──
    DOMINANT_9(listOf(4, 7, 10, 14), "9", "属九和弦", ChordCategory.NINTH),
    MAJOR_9(listOf(4, 7, 11, 14), "maj9", "大九和弦", ChordCategory.NINTH),
    MINOR_9(listOf(3, 7, 10, 14), "m9", "小九和弦", ChordCategory.NINTH),

    // ── 加九和弦 (Added tone) ──
    ADD9(listOf(4, 7, 14), "add9", "加九和弦", ChordCategory.ADDED),
    ADD2(listOf(2, 4, 7), "add2", "加二和弦", ChordCategory.ADDED);

    /**
     * 和弦包含的音符总数（含根音）。
     */
    val noteCount: Int get() = intervals.size + 1

    /**
     * 和弦所有音的半音偏移（含根音 0），已排序。
     */
    val allIntervals: List<Int> get() = listOf(0) + intervals
}

/**
 * 和弦分类，用于 UI 分组。
 */
enum class ChordCategory(val displayName: String) {
    TRIAD("三和弦"),
    SUSPENDED("挂留和弦"),
    SIXTH("六和弦"),
    SEVENTH("七和弦"),
    NINTH("九和弦"),
    ADDED("加九和弦")
}

/**
 * 和弦转位。
 *
 * - [ROOT_POSITION] 原位：根音在最低声部
 * - [FIRST_INVERSION] 第一转位：三音（第 3 音）在最低声部
 * - [SECOND_INVERSION] 第二转位：五音（第 5 音）在最低声部
 * - [THIRD_INVERSION] 第三转位：七音在最低声部（仅七和弦及以上可用）
 */
enum class ChordInversion(val displayName: String, val displaySymbol: String) {
    ROOT_POSITION("原位", ""),
    FIRST_INVERSION("第一转位", "⁶"),
    SECOND_INVERSION("第二转位", "⁶₄"),
    THIRD_INVERSION("第三转位", "⁴₂")
}

/**
 * 和弦的完整表示：根音 + 类型 + 转位 → 具体的 MIDI 音符列表。
 *
 * @param root 根音
 * @param type 和弦类型
 * @param inversion 转位
 * @param midiNotes 升序排列的 MIDI 音符编号（已根据转位调整八度）
 * @param noteNames 与 [midiNotes] 一一对应的音名列表
 * @param fullName 完整和弦名称（如 "Cmaj7"、"F♯m⁶₄"）
 */
data class ChordVoicing(
    val root: ChordRoot,
    val type: ChordType,
    val inversion: ChordInversion,
    val midiNotes: List<Int>,
    val noteNames: List<String>,
    val fullName: String,
    val preferFlats: Boolean = false
)
