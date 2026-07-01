package com.pianocompanion.scale

import com.pianocompanion.util.MusicUtils

/**
 * 音阶构建引擎（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 负责根据根音和音阶类型构建具体的音阶音符序列，
 * 将抽象的音程结构映射为 MIDI 音符编号列表。
 *
 * 支持上行和下行两个方向，旋律小调上行使用旋律小调音阶、
 * 下行使用自然小调音阶。
 */
object ScaleEngine {

    const val BASE_OCTAVE = 4
    private const val SEMITONES_PER_OCTAVE = 12

    /**
     * 根音音级类 → C4 为基准时的 MIDI 起始编号。
     * 例如根音 C → 60, 根音 D → 62, 根音 B → 59 (上一八度)。
     */
    private fun rootMidi(root: ScaleRoot): Int {
        val base = (BASE_OCTAVE + 1) * SEMITONES_PER_OCTAVE // C4 = 60
        return base + root.pitchClass
    }

    /**
     * 为给定的 MIDI 音符选择正确的升号/降号记法。
     */
    private fun noteName(midi: Int, preferFlats: Boolean): String {
        val pc = ((midi % SEMITONES_PER_OCTAVE) + SEMITONES_PER_OCTAVE) % SEMITONES_PER_OCTAVE
        val octave = (midi / SEMITONES_PER_OCTAVE) - 1
        val name = if (preferFlats) {
            when (pc) {
                0 -> "C"; 1 -> "D♭"; 2 -> "D"; 3 -> "E♭"; 4 -> "E"
                5 -> "F"; 6 -> "G♭"; 7 -> "G"; 8 -> "A♭"; 9 -> "A"
                10 -> "B♭"; 11 -> "B"; else -> "?"
            }
        } else {
            when (pc) {
                0 -> "C"; 1 -> "C♯"; 2 -> "D"; 3 -> "D♯"; 4 -> "E"
                5 -> "F"; 6 -> "F♯"; 7 -> "G"; 8 -> "G♯"; 9 -> "A"
                10 -> "A♯"; 11 -> "B"; else -> "?"
            }
        }
        return "$name$octave"
    }

    /**
     * 构建音阶的完整音符序列。
     *
     * 上行序列：根音 → 各音阶音 → 八度根音（共 noteCount + 1 个音）
     * 下行序列：八度根音 → 各音阶音（逆序）→ 根音（共 noteCount + 1 个音）
     *
     * 旋律小调：上行使用旋律小调音程，下行使用自然小调音程。
     *
     * @param root 根音
     * @param type 音阶类型
     * @param preferFlats 是否使用降号记法（默认根据根音偏好）
     * @return [ScaleInfo] 包含上行/下行 MIDI 音符、音名、完整名称
     */
    fun build(
        root: ScaleRoot,
        type: ScaleType,
        preferFlats: Boolean = preferFlatsKey(root)
    ): ScaleInfo {
        val rootNote = rootMidi(root)

        // 构建上行序列：根音 + 各音阶音 + 八度根音
        val ascendingNotes = mutableListOf<Int>()
        for (interval in type.allAscendingIntervals) {
            ascendingNotes.add((rootNote + interval).coerceIn(21, 108))
        }
        ascendingNotes.add((rootNote + SEMITONES_PER_OCTAVE).coerceIn(21, 108))

        // 构建下行序列：八度根音 + 逆序音阶音 + 根音
        val descendingIntervals = type.allDescendingIntervals
        val descendingNotes = mutableListOf<Int>()
        descendingNotes.add((rootNote + SEMITONES_PER_OCTAVE).coerceIn(21, 108))
        // 跳过索引 0（根音），因为最后单独添加根音
        for (i in descendingIntervals.size - 1 downTo 1) {
            descendingNotes.add((rootNote + descendingIntervals[i]).coerceIn(21, 108))
        }
        descendingNotes.add(rootNote.coerceIn(21, 108))

        // 去重并保持顺序（全音阶/半音阶下行可能与上行有重复，但保持序列完整性）
        val noteNameList = ascendingNotes.map { noteName(it, preferFlats) }
        val name = formatScaleName(root, type, preferFlats)

        return ScaleInfo(
            root = root,
            type = type,
            ascendingMidiNotes = ascendingNotes,
            descendingMidiNotes = descendingNotes,
            noteNames = noteNameList,
            fullName = name,
            preferFlats = preferFlats
        )
    }

    /**
     * 格式化完整音阶名称。
     *
     * 例如："C自然大调"、"A和声小调"、"D多利亚调式"、"F♯大调五声音阶"
     */
    fun formatScaleName(
        root: ScaleRoot,
        type: ScaleType,
        preferFlats: Boolean = preferFlatsKey(root)
    ): String {
        val rootName = root.name(preferFlats)
        return "$rootName${type.displayName}"
    }

    /**
     * 根据根音音级类决定升号/降号偏好。
     *
     * 降号调：F, B♭, E♭, A♭ → 使用降号记法
     * 升号调：C, G, D, A, E, B, F♯ → 使用升号记法
     */
    fun preferFlatsKey(root: ScaleRoot): Boolean {
        return root in setOf(
            ScaleRoot.F, ScaleRoot.B_FLAT, ScaleRoot.E_FLAT, ScaleRoot.A_FLAT
        )
    }

    /**
     * 获取指定根音的所有音阶类型列表（按分类分组）。
     */
    fun allScalesByCategory(): Map<ScaleCategory, List<ScaleType>> {
        return ScaleType.entries.groupBy { it.category }
    }

    /**
     * 计算音阶所有上行音符的频率列表（Hz），用于音频合成。
     */
    fun frequencies(info: ScaleInfo): List<Double> {
        return info.ascendingMidiNotes.map { MusicUtils.midiToFrequency(it) }
    }

    /**
     * 获取音阶各音的级数名称（音阶度数），用于教学展示。
     *
     * 例如自然大调 → ["主音(I)", "上主音(II)", "中音(III)", "下属音(IV)",
     *               "属音(V)", "下中音(VI)", "下主音(VII)", "主音(I)"]
     *
     * 级数基于半音偏移推算：
     * - 0 → I (主音)
     * - 大二度/小三度 → II (上主音)
     * - 大三度/纯四度 → III (中音)
     * - 增四度/纯五度 → IV (下属音)
     * - 小六度/大六度 → V (属音)
     * - 增五度/减五度 → 特殊处理
     */
    fun degreeNames(type: ScaleType): List<String> {
        val names = mutableListOf<String>()
        names.add("主音(I)")

        val intervals = type.ascendingIntervals
        var lastDegree = 1

        for (interval in intervals) {
            val degree = intervalToDegree(interval, lastDegree)
            names.add(degree)
            lastDegree = degreeNumber(degree)
        }

        // 八度根音
        names.add("主音(I)")

        return names
    }

    /**
     * 根据半音偏移和上一级数推算级数名称。
     * 使用半音数和级数递增逻辑来推断正确的度数。
     */
    private fun intervalToDegree(semitones: Int, prevDegree: Int): String {
        // 标准自然音阶的半音→度数映射（以自然音为基准）
        // I=0, II=2, III=4, IV=5, V=7, VI=9, VII=11
        val naturalDegrees = mapOf(
            0 to 1, 1 to 1, 2 to 2, 3 to 2, 4 to 3, 5 to 4,
            6 to 4, 7 to 5, 8 to 5, 9 to 6, 10 to 7, 11 to 7
        )
        val degree = naturalDegrees[semitones] ?: (prevDegree + 1)
        return degreeSymbol(degree, semitones)
    }

    /**
     * 将级数数字转为罗马数字符号（含升降修饰）。
     */
    private fun degreeSymbol(degree: Int, semitones: Int): String {
        val roman = when (degree) {
            1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"
            5 -> "V"; 6 -> "VI"; 7 -> "VII"; else -> "?"
        }
        val name = when (degree) {
            1 -> "主音"; 2 -> "上主音"; 3 -> "中音"; 4 -> "下属音"
            5 -> "属音"; 6 -> "下中音"; 7 -> "下主音"; else -> ""
        }
        return "$name($roman)"
    }

    /**
     * 从级数符号中提取级数数字。
     */
    private fun degreeNumber(degreeStr: String): Int {
        return when {
            degreeStr.contains("VII") -> 7
            degreeStr.contains("VI") && !degreeStr.contains("VII") -> 6
            degreeStr.contains("IV") -> 4
            degreeStr.contains("V") && !degreeStr.contains("I") -> 5
            degreeStr.contains("III") -> 3
            degreeStr.contains("II") -> 3  // II maps to degree 2, next would be 3
            else -> 1
        }
    }

    /**
     * 获取自然大调的关系小调根音（向下三个半音）。
     *
     * 例如：C大调 → A小调, G大调 → E小调
     */
    fun relativeMinor(root: ScaleRoot): ScaleRoot {
        val minorPc = ((root.pitchClass - 3 + SEMITONES_PER_OCTAVE) % SEMITONES_PER_OCTAVE)
        return ScaleRoot.entries.find { it.pitchClass == minorPc } ?: ScaleRoot.A
    }

    /**
     * 获取自然小调的关系大调根音（向上三个半音）。
     *
     * 例如：A小调 → C大调, E小调 → G大调
     */
    fun relativeMajor(root: ScaleRoot): ScaleRoot {
        val majorPc = (root.pitchClass + 3) % SEMITONES_PER_OCTAVE
        return ScaleRoot.entries.find { it.pitchClass == majorPc } ?: ScaleRoot.C
    }

    /**
     * 获取标准钢琴音阶指法建议（单八度，右手）。
     *
     * 大多数音阶使用 1-2-3-1-2-3-4-5 指法（穿指），
     * 但某些调性有特殊的指法规则。
     *
     * 返回指法数字列表（1=拇指, 5=小指），长度等于上行音符数。
     */
    fun suggestedFingering(root: ScaleRoot, type: ScaleType): List<Int> {
        val count = type.noteCount + 1 // 含八度根音

        // 五声音阶和蓝调音阶使用简化指法
        return when {
            count <= 5 -> (1..count).toList()
            count == 6 -> listOf(1, 2, 3, 1, 2, 5)  // 五声/蓝调
            else -> {
                // 标准七音音阶指法：1-2-3-1-2-3-4-5
                // F大调和B♭大调左手有特殊指法，但右手相同
                when (count) {
                    7 -> listOf(1, 2, 3, 1, 2, 3, 5)  // 六音音阶
                    8 -> listOf(1, 2, 3, 1, 2, 3, 4, 5)  // 七音音阶 + 八度
                    13 -> listOf(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 3) // 半音阶
                    else -> (1..count).toList()
                }
            }
        }
    }

    /**
     * 获取音阶的半音偏移序列（相对于根音），用于教学展示。
     */
    fun intervalSteps(type: ScaleType): List<Int> {
        val intervals = type.allAscendingIntervals
        val result = mutableListOf<Int>()
        // allAscendingIntervals 包含根音 (0)，跳过它，从第一个音阶音开始计算步进
        for (i in 1 until intervals.size) {
            result.add(intervals[i] - intervals[i - 1])
        }
        result.add(SEMITONES_PER_OCTAVE - intervals.last())
        return result
    }

    /**
     * 判断两个音阶是否为等价音阶（音程结构相同）。
     */
    fun areEquivalent(type1: ScaleType, type2: ScaleType): Boolean {
        return type1.ascendingIntervals == type2.ascendingIntervals
    }
}
