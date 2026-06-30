package com.pianocompanion.chord

import com.pianocompanion.util.MusicUtils

/**
 * 和弦构建引擎（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 负责根据根音、和弦类型、转位构建具体的和弦发音（voicing），
 * 将抽象的音程结构映射为 MIDI 音符编号列表。
 */
object ChordEngine {

    /**
     * 默认根音八度：C4 = MIDI 60（中央 C）。
     * 和弦的最低音以此为基准构建，确保落在钢琴舒适音区。
     */
    const val BASE_OCTAVE = 4
    private const val SEMITONES_PER_OCTAVE = 12

    /**
     * 根音音级类 → C4 为基准时的 MIDI 起始编号。
     * 例如根音 C → 60, 根音 D → 62, 根音 B → 59 (上一八度)。
     */
    private fun rootMidi(root: ChordRoot): Int {
        val base = (BASE_OCTAVE + 1) * SEMITONES_PER_OCTAVE // C4 = 60
        return base + root.pitchClass
    }

    /**
     * 为给定的和弦音名选择正确的升号/降号记法。
     *
     * 根据根音偏好（升号/降号调）选择适当的记谱方式，
     * 使得和弦音符名称与调性风格一致。
     */
    private fun noteName(midi: Int, preferFlats: Boolean): String {
        val pc = ((midi % SEMITONES_PER_OCTAVE) + SEMITONES_PER_OCTAVE) % SEMITONES_PER_OCTAVE
        val octave = (midi / SEMITONES_PER_OCTAVE) - 1
        val name = if (preferFlats) {
            // 降号记法
            when (pc) {
                0 -> "C"; 1 -> "D♭"; 2 -> "D"; 3 -> "E♭"; 4 -> "E"
                5 -> "F"; 6 -> "G♭"; 7 -> "G"; 8 -> "A♭"; 9 -> "A"
                10 -> "B♭"; 11 -> "B"; else -> "?"
            }
        } else {
            // 升号记法
            when (pc) {
                0 -> "C"; 1 -> "C♯"; 2 -> "D"; 3 -> "D♯"; 4 -> "E"
                5 -> "F"; 6 -> "F♯"; 7 -> "G"; 8 -> "G♯"; 9 -> "A"
                10 -> "A♯"; 11 -> "B"; else -> "?"
            }
        }
        return "$name$octave"
    }

    /**
     * 判断给定和弦类型可用的转位数。
     *
     * 三和弦：原位 + 2 转位 = 3 种
     * 四音和弦（七/六和弦等）：原位 + 3 转位 = 4 种
     * 五音和弦（九和弦等）：原位 + 3 转位（第 4 转位不常用，封顶 4 种）
     *
     * @return 可用的转位列表
     */
    fun availableInversions(type: ChordType): List<ChordInversion> {
        return when (type.noteCount) {
            3 -> listOf(ChordInversion.ROOT_POSITION, ChordInversion.FIRST_INVERSION, ChordInversion.SECOND_INVERSION)
            else -> ChordInversion.entries // 4 种转位
        }
    }

    /**
     * 构建和弦发音（voicing）。
     *
     * 算法：
     * 1. 从根音八度开始，按 [type.allIntervals] 生成 MIDI 音符列表
     * 2. 应用转位：将前 [inversion.ordinal] 个音符升高一个八度
     * 3. 确保 MIDI 音符不超出钢琴范围 (A0=21 ~ C8=108)
     *
     * @param root 根音
     * @param type 和弦类型
     * @param inversion 转位（若不适用于该和弦类型，自动回退到原位）
     * @param preferFlats 是否使用降号记法
     * @return [ChordVoicing] 包含 MIDI 音符、音名、完整名称
     */
    fun build(
        root: ChordRoot,
        type: ChordType,
        inversion: ChordInversion = ChordInversion.ROOT_POSITION,
        preferFlats: Boolean = preferFlatsKey(root)
    ): ChordVoicing {
        val validInversions = availableInversions(type)
        val actualInversion = if (inversion in validInversions) inversion else ChordInversion.ROOT_POSITION

        // 构建原位 MIDI 音符列表
        val rootMidi = rootMidi(root)
        val baseNotes = type.allIntervals.map { interval ->
            rootMidi + interval
        }.toMutableList()

        // 应用转位：将前 n 个音符升一个八度
        val inversionCount = actualInversion.ordinal
        if (inversionCount > 0) {
            for (i in 0 until minOf(inversionCount, baseNotes.size)) {
                baseNotes[i] += SEMITONES_PER_OCTAVE
            }
        }

        // 钢琴范围钳位（A0=21 ~ C8=108）
        val clampedNotes = baseNotes.map { it.coerceIn(21, 108) }.sorted()

        val noteNameList = clampedNotes.map { noteName(it, preferFlats) }
        val name = formatChordName(root, type, actualInversion, preferFlats)

        return ChordVoicing(
            root = root,
            type = type,
            inversion = actualInversion,
            midiNotes = clampedNotes,
            noteNames = noteNameList,
            fullName = name,
            preferFlats = preferFlats
        )
    }

    /**
     * 格式化完整和弦名称。
     *
     * 例如："C"、"D♭m7"、"F♯maj7⁶₄"
     */
    fun formatChordName(
        root: ChordRoot,
        type: ChordType,
        inversion: ChordInversion,
        preferFlats: Boolean = preferFlatsKey(root)
    ): String {
        val rootName = root.name(preferFlats)
        val symbol = if (type == ChordType.MAJOR) "" else type.symbol
        val invSymbol = if (inversion == ChordInversion.ROOT_POSITION) "" else inversion.displaySymbol
        return "$rootName$symbol$invSymbol"
    }

    /**
     * 根据根音音级类决定升号/降号偏好。
     *
     * 降号调：F, B♭, E♭, A♭, D♭, G♭ → 使用降号记法
     * 升号调：C, G, D, A, E, B, F♯ → 使用升号记法
     *
     * 这样和弦音名与调性风格一致（如 F 大调的和弦音用 B♭ 而非 A♯）。
     */
    fun preferFlatsKey(root: ChordRoot): Boolean {
        // F, B♭, E♭, A♭ 使用降号记法
        return root in setOf(ChordRoot.F, ChordRoot.B_FLAT, ChordRoot.E_FLAT, ChordRoot.A_FLAT)
    }

    /**
     * 获取指定根音的所有和弦类型列表（按分类分组）。
     */
    fun allChordsByCategory(): Map<ChordCategory, List<ChordType>> {
        return ChordType.entries.groupBy { it.category }
    }

    /**
     * 计算和弦所有音符的频率列表（Hz），用于音频合成。
     */
    fun frequencies(voicing: ChordVoicing): List<Double> {
        return voicing.midiNotes.map { MusicUtils.midiToFrequency(it) }
    }

    /**
     * 获取和弦各音的音程名称（相对于根音），用于教学展示。
     *
     * 例如大三和弦 → ["根音", "大三度", "纯五度"]
     */
    fun intervalNames(type: ChordType): List<String> {
        val names = mutableListOf("根音")
        for (interval in type.intervals) {
            names.add(intervalToName(interval))
        }
        return names
    }

    /**
     * 半音数 → 音程名称。
     */
    private fun intervalToName(semitones: Int): String = when (semitones) {
        1 -> "小二度"
        2 -> "大二度"
        3 -> "小三度"
        4 -> "大三度"
        5 -> "纯四度"
        6 -> "增四度"
        7 -> "纯五度"
        8 -> "增五度"
        9 -> "大六度"
        10 -> "小七度"
        11 -> "大七度"
        12 -> "纯八度"
        13 -> "小九度"
        14 -> "大九度"
        else -> "${semitones}半音"
    }

    /**
     * 获取和弦的标准指法建议（右手）。
     *
     * 基于常见的钢琴教学法指法规则。对于三和弦：
     * - 原位：1-3-5
     * - 第一转位：1-2-5 或 1-3-5
     * - 第二转位：1-3-5 或 1-2-5
     *
     * 返回指法数字列表（1=拇指, 5=小指），长度等于音符数。
     * 若无标准指法规则，返回空列表。
     */
    fun suggestedFingering(type: ChordType, inversion: ChordInversion): List<Int> {
        val count = type.noteCount
        return when (count) {
            3 -> when (inversion) {
                ChordInversion.ROOT_POSITION -> listOf(1, 3, 5)
                ChordInversion.FIRST_INVERSION -> listOf(1, 2, 5)
                ChordInversion.SECOND_INVERSION -> listOf(1, 3, 5)
                else -> listOf(1, 3, 5)
            }
            4 -> when (inversion) {
                ChordInversion.ROOT_POSITION -> listOf(1, 2, 3, 5)
                ChordInversion.FIRST_INVERSION -> listOf(1, 2, 4, 5)
                ChordInversion.SECOND_INVERSION -> listOf(1, 3, 4, 5)
                ChordInversion.THIRD_INVERSION -> listOf(1, 2, 3, 5)
            }
            else -> {
                // 五音和弦：使用 1-2-3-4-5
                (1..count).toList()
            }
        }
    }
}
