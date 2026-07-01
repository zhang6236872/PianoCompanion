package com.pianocompanion.scale

/**
 * 音阶词典数据模型（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 本模块提供钢琴音阶的完整参考库，涵盖自然大调、三种小调（自然/和声/旋律）、
 * 七种教会调式、大/小调五声音阶、蓝调音阶、半音阶和全音阶共 15 种音阶类型，
 * 支持 12 个根音。
 */

/**
 * 音阶根音（音级类，0 = C, 1 = C#/Db, ..., 11 = B）。
 *
 * 复用 ChordRoot 的概念，但保持独立以避免跨包耦合。
 */
enum class ScaleRoot(
    val pitchClass: Int,
    val sharpName: String,
    val flatName: String
) {
    C(0, "C", "C"),
    C_SHARP(1, "C♯", "D♭"),
    D(2, "D", "D"),
    E_FLAT(3, "D♯", "E♭"),
    E(4, "E", "E"),
    F(5, "F", "F"),
    F_SHARP(6, "F♯", "G♭"),
    G(7, "G", "G"),
    A_FLAT(8, "G♯", "A♭"),
    A(9, "A", "A"),
    B_FLAT(10, "A♯", "B♭"),
    B(11, "B", "B");

    /**
     * 根据记谱偏好返回根音名称。
     */
    fun name(preferFlats: Boolean = false): String =
        if (preferFlats) flatName else sharpName
}

/**
 * 音阶分类，用于 UI 分组。
 */
enum class ScaleCategory(val displayName: String) {
    MAJOR("大调音阶"),
    MINOR("小调音阶"),
    MODE("教会调式"),
    PENTATONIC("五声音阶"),
    BLUES("蓝调音阶"),
    OTHER("其他音阶")
}

/**
 * 音阶类型，定义音阶的音程结构。
 *
 * [ascendingIntervals] 是相对于根音的半音偏移列表（不含根音本身），
 * 用于构建上行音阶。
 *
 * [descendingIntervals] 是下行音阶的半音偏移列表（不含根音）。
 * 大多数音阶上行下行相同，只有旋律小调例外（上行旋律小调，下行自然小调）。
 *
 * 若 [descendingIntervals] 为 null，表示上行下行相同。
 *
 * [displayName] 是中文名称，[symbol] 是英文符号标记。
 */
enum class ScaleType(
    val ascendingIntervals: List<Int>,
    val descendingIntervals: List<Int>?,
    val displayName: String,
    val symbol: String,
    val category: ScaleCategory
) {
    // ── 大调 ──
    MAJOR(
        ascendingIntervals = listOf(2, 4, 5, 7, 9, 11),
        descendingIntervals = null,
        displayName = "自然大调",
        symbol = "major",
        category = ScaleCategory.MAJOR
    ),

    // ── 小调 ──
    NATURAL_MINOR(
        ascendingIntervals = listOf(2, 3, 5, 7, 8, 10),
        descendingIntervals = null,
        displayName = "自然小调",
        symbol = "natural minor",
        category = ScaleCategory.MINOR
    ),
    HARMONIC_MINOR(
        ascendingIntervals = listOf(2, 3, 5, 7, 8, 11),
        descendingIntervals = null,
        displayName = "和声小调",
        symbol = "harmonic minor",
        category = ScaleCategory.MINOR
    ),
    MELODIC_MINOR(
        ascendingIntervals = listOf(2, 3, 5, 7, 9, 11),
        descendingIntervals = listOf(2, 3, 5, 7, 8, 10), // 下行 = 自然小调
        displayName = "旋律小调",
        symbol = "melodic minor",
        category = ScaleCategory.MINOR
    ),

    // ── 教会调式 ──
    IONIAN(
        ascendingIntervals = listOf(2, 4, 5, 7, 9, 11),
        descendingIntervals = null,
        displayName = "伊奥尼亚调式",
        symbol = "Ionian",
        category = ScaleCategory.MODE
    ),
    DORIAN(
        ascendingIntervals = listOf(2, 3, 5, 7, 9, 10),
        descendingIntervals = null,
        displayName = "多利亚调式",
        symbol = "Dorian",
        category = ScaleCategory.MODE
    ),
    PHRYGIAN(
        ascendingIntervals = listOf(1, 3, 5, 7, 8, 10),
        descendingIntervals = null,
        displayName = "弗利吉亚调式",
        symbol = "Phrygian",
        category = ScaleCategory.MODE
    ),
    LYDIAN(
        ascendingIntervals = listOf(2, 4, 6, 7, 9, 11),
        descendingIntervals = null,
        displayName = "利底亚调式",
        symbol = "Lydian",
        category = ScaleCategory.MODE
    ),
    MIXOLYDIAN(
        ascendingIntervals = listOf(2, 4, 5, 7, 9, 10),
        descendingIntervals = null,
        displayName = "混合利底亚调式",
        symbol = "Mixolydian",
        category = ScaleCategory.MODE
    ),
    AEOLIAN(
        ascendingIntervals = listOf(2, 3, 5, 7, 8, 10),
        descendingIntervals = null,
        displayName = "爱奥利亚调式",
        symbol = "Aeolian",
        category = ScaleCategory.MODE
    ),
    LOCRIAN(
        ascendingIntervals = listOf(1, 3, 5, 6, 8, 10),
        descendingIntervals = null,
        displayName = "洛克利亚调式",
        symbol = "Locrian",
        category = ScaleCategory.MODE
    ),

    // ── 五声音阶 ──
    MAJOR_PENTATONIC(
        ascendingIntervals = listOf(2, 4, 7, 9),
        descendingIntervals = null,
        displayName = "大调五声音阶",
        symbol = "major pentatonic",
        category = ScaleCategory.PENTATONIC
    ),
    MINOR_PENTATONIC(
        ascendingIntervals = listOf(3, 5, 7, 10),
        descendingIntervals = null,
        displayName = "小调五声音阶",
        symbol = "minor pentatonic",
        category = ScaleCategory.PENTATONIC
    ),

    // ── 蓝调音阶 ──
    BLUES(
        ascendingIntervals = listOf(3, 5, 6, 7, 10),
        descendingIntervals = null,
        displayName = "蓝调音阶",
        symbol = "blues",
        category = ScaleCategory.BLUES
    ),

    // ── 其他 ──
    CHROMATIC(
        ascendingIntervals = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        descendingIntervals = null,
        displayName = "半音阶",
        symbol = "chromatic",
        category = ScaleCategory.OTHER
    ),
    WHOLE_TONE(
        ascendingIntervals = listOf(2, 4, 6, 8, 10),
        descendingIntervals = null,
        displayName = "全音阶",
        symbol = "whole tone",
        category = ScaleCategory.OTHER
    );

    /**
     * 音阶包含的音符总数（含根音），基于上行结构。
     */
    val noteCount: Int get() = ascendingIntervals.size + 1

    /**
     * 上行音阶所有音的半音偏移（含根音 0），已排序。
     */
    val allAscendingIntervals: List<Int> get() = listOf(0) + ascendingIntervals

    /**
     * 下行音阶所有音的半音偏移（含根音 0），已排序。
     * 若上行下行相同，返回与上行相同的列表。
     */
    val allDescendingIntervals: List<Int>
        get() = descendingIntervals?.let { listOf(0) + it } ?: allAscendingIntervals

    /**
     * 上行下行是否不同（仅旋律小调为 true）。
     */
    val hasDifferentDescending: Boolean get() = descendingIntervals != null
}

/**
 * 音阶的完整表示：根音 + 类型 → 具体的 MIDI 音符列表（上行 + 下行）。
 *
 * 上行：从根音到上方八度根音（含两端），共 noteCount + 1 个音。
 * 下行：从上方八度根音回到根音（含两端），共 noteCount + 1 个音。
 *
 * @param root 根音
 * @param type 音阶类型
 * @param ascendingMidiNotes 上行 MIDI 音符列表（含起始根音和八度根音）
 * @param descendingMidiNotes 下行 MIDI 音符列表（含八度根音和起始根音）
 * @param noteNames 与上行音符一一对应的音名列表
 * @param fullName 完整音阶名称（如 "C自然大调"、"A和声小调"）
 * @param preferFlats 是否使用降号记法
 */
data class ScaleInfo(
    val root: ScaleRoot,
    val type: ScaleType,
    val ascendingMidiNotes: List<Int>,
    val descendingMidiNotes: List<Int>,
    val noteNames: List<String>,
    val fullName: String,
    val preferFlats: Boolean = false
)
