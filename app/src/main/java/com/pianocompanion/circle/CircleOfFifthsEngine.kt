package com.pianocompanion.circle

import com.pianocompanion.util.MusicUtils

/**
 * 五度圈核心引擎（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 负责：
 * - 调性 → 圆环位置 / 角度的换算
 * - 调号（升号/降号数量与具体字母）计算
 * - 正确的等音记谱（diatonic spelling）：根据调号为每个音阶音分配正确的字母与升降号
 * - 调内顺阶三和弦（罗马数字分析 I ii iii IV V vi vii° 等）
 * - 关系调、近关系调（圆环相邻调性）
 */
object CircleOfFifthsEngine {

    private const val SEMITONES = 12
    private const val POSITIONS = 12

    /** 自然大调音阶相对于主音的半音偏移。 */
    val MAJOR_SEMITONES = listOf(0, 2, 4, 5, 7, 9, 11)
    /** 自然小调音阶相对于主音的半音偏移。 */
    val MINOR_SEMITONES = listOf(0, 2, 3, 5, 7, 8, 10)

    /** 七个自然音字母及其自然音级类。 */
    private val LETTERS = listOf('C', 'D', 'E', 'F', 'G', 'A', 'B')
    private val LETTER_NATURAL_PC = mapOf(
        'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5,
        'G' to 7, 'A' to 9, 'B' to 11
    )

    /**
     * 主音音级类 → 圆环位置（按大调计算）。
     *
     * 圆环每顺时针一格增加一个纯五度（+7 半音），故位置 p 满足
     * `pc ≡ 7·p (mod 12)`，其逆映射为 `p ≡ 7·pc (mod 12)`（因为 7·7=49≡1）。
     */
    fun majorPosition(tonicPc: Int): Int = ((7 * tonicPc) % SEMITONES + SEMITONES) % SEMITONES

    /**
     * 调性 → 圆环位置（0~11）。
     *
     * 大调：直接按主音计算。小调：等于其关系大调的位置
     * （关系大调主音 = 小调主音 + 3 半音）。
     */
    fun positionOf(key: CircleKey): Int {
        val majorPc = if (key.isMajor) key.tonicPc else (key.tonicPc + 3) % SEMITONES
        return majorPosition(majorPc)
    }

    /**
     * 圆环位置 → 角度（度），0° 在顶部（12 点钟方向），顺时针递增。
     * 用于 UI 绘制与点击命中检测。
     */
    fun angleDegrees(position: Int): Double = position * (360.0 / POSITIONS)

    /**
     * 计算指定调性的调号。
     */
    fun keySignature(key: CircleKey): KeySignature {
        val pos = positionOf(key)
        val sharps = if (pos in 1..6) pos else 0
        val flats = if (pos in 7..11) SEMITONES - pos else 0
        val sharpLetters = if (sharps > 0) KeySignature.SHARP_ORDER.take(sharps).toSet() else emptySet()
        val flatLetters = if (flats > 0) KeySignature.FLAT_ORDER.take(flats).toSet() else emptySet()
        return KeySignature(sharps, flats, sharpLetters, flatLetters)
    }

    /**
     * 根据圆环位置判断是否偏好降号记法（降号侧 7~11 使用降号）。
     */
    fun preferFlats(position: Int): Boolean = position in 7..11

    /**
     * 获取调性的主音字母（C/D/E/F/G/A/B）。
     *
     * 基于圆环位置的约定命名：
     * 位置 0~5：C, G, D, A, E, B（升号侧，自然音）
     * 位置 6：F（F♯ 大调，主音字母 F）
     * 位置 7~11：D, A, E, B, F（降号侧，主音字母为降号侧的自然字母）
     *
     * 小调主音字母 = 关系大调主音字母的第 6 级（向上数第 6 个字母）。
     */
    fun tonicLetter(key: CircleKey): Char {
        val pos = positionOf(key)
        val majorLettersByPos = charArrayOf('C', 'G', 'D', 'A', 'E', 'B', 'F', 'D', 'A', 'E', 'B', 'F')
        val majorLetter = majorLettersByPos[pos]
        return if (key.isMajor) {
            majorLetter
        } else {
            // 关系小调主音 = 大调音阶第 6 级（自然字母序列向上第 6 个）
            val startIdx = LETTERS.indexOf(majorLetter)
            LETTERS[(startIdx + 5) % 7]
        }
    }

    /**
     * 主音显示名（带升降号），如 "C"、"F♯"、"D♭"。
     */
    fun tonicName(key: CircleKey): String {
        val letter = tonicLetter(key)
        val sig = keySignature(key)
        return when {
            letter in sig.sharpenedLetters -> "$letter♯"
            letter in sig.flattenedLetters -> "$letter♭"
            else -> letter.toString()
        }
    }

    /**
     * 音阶各音名（正确的等音记谱）。
     *
     * 从主音字母出发，取自然字母序列的 7 个连续字母，
     * 再根据调号为对应字母添加升号或降号。
     */
    fun scaleNoteNames(key: CircleKey): List<String> {
        val letter = tonicLetter(key)
        val sig = keySignature(key)
        val startIdx = LETTERS.indexOf(letter)
        return (0..6).map { i ->
            val l = LETTERS[(startIdx + i) % 7]
            when {
                l in sig.sharpenedLetters -> "$l♯"
                l in sig.flattenedLetters -> "$l♭"
                else -> l.toString()
            }
        }
    }

    /**
     * 音阶音级类列表（含主音，共 7 个）。
     */
    fun scalePcs(key: CircleKey): List<Int> {
        val pattern = if (key.isMajor) MAJOR_SEMITONES else MINOR_SEMITONES
        return pattern.map { (key.tonicPc + it) % SEMITONES }
    }

    /**
     * 音阶 MIDI 音符列表（主音锚定在 C4 附近，钳位到钢琴范围 21~108）。
     */
    fun scaleMidiNotes(key: CircleKey): List<Int> {
        val tonicMidi = 60 + key.tonicPc
        val pattern = if (key.isMajor) MAJOR_SEMITONES else MINOR_SEMITONES
        return pattern.map { (tonicMidi + it).coerceIn(21, 108) }
    }

    /**
     * 关系调（大调→关系小调：主音下移 3 半音；小调→关系大调：主音上移 3 半音）。
     */
    fun relativeKey(key: CircleKey): CircleKey {
        return if (key.isMajor) {
            CircleKey((key.tonicPc - 3 + SEMITONES) % SEMITONES, CircleMode.MINOR)
        } else {
            CircleKey((key.tonicPc + 3) % SEMITONES, CircleMode.MAJOR)
        }
    }

    /**
     * 根据根音-三音-五音的半音音程推断三和弦性质。
     *
     * @param interval3 三音相对根音的半音数
     * @param interval5 五音相对根音的半音数
     */
    fun triadQuality(interval3: Int, interval5: Int): ChordQuality = when {
        interval3 == 4 && interval5 == 7 -> ChordQuality.MAJOR
        interval3 == 3 && interval5 == 7 -> ChordQuality.MINOR
        interval3 == 3 && interval5 == 6 -> ChordQuality.DIMINISHED
        interval3 == 4 && interval5 == 8 -> ChordQuality.AUGMENTED
        else -> ChordQuality.MAJOR // 安全回退
    }

    /**
     * 级数 → 罗马数字（大写），后续根据性质调整大小写与符号。
     */
    private fun romanBase(degree: Int): String = when (degree) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"
        5 -> "V"; 6 -> "VI"; 7 -> "VII"; else -> "?"
    }

    /**
     * 构建调内顺阶三和弦（罗马数字分析）。
     *
     * 对音阶的每一级，取 根音-三音-五音（隔三度叠加），
     * 根据音程推断性质，生成罗马数字标记与和弦名。
     *
     * 大调结果：I, ii, iii, IV, V, vi, vii°
     * 小调结果：i, ii°, III, iv, v, VI, VII
     */
    fun diatonicChords(key: CircleKey): List<DiatonicChord> {
        val pattern = if (key.isMajor) MAJOR_SEMITONES else MINOR_SEMITONES
        val noteNames = scaleNoteNames(key)
        val tonicMidi = 60 + key.tonicPc
        val result = mutableListOf<DiatonicChord>()

        for (d in 0..6) {
            val rootSemi = pattern[d]
            val thirdDeg = (d + 2) % 7
            val fifthDeg = (d + 4) % 7

            // 处理跨八度回绕：若级数索引回到起点，补一个八度（+12）
            var thirdSemi = pattern[thirdDeg]
            if (thirdDeg < d) thirdSemi += SEMITONES
            // 第五音可能绕过第三音，需保证 > 三音
            var fifthSemi = pattern[fifthDeg]
            if (fifthDeg < d) fifthSemi += SEMITONES
            if (fifthSemi <= thirdSemi) fifthSemi += SEMITONES

            val interval3 = thirdSemi - rootSemi
            val interval5 = fifthSemi - rootSemi
            val quality = triadQuality(interval3, interval5)

            val rootPc = (key.tonicPc + rootSemi) % SEMITONES
            val chordNames = listOf(noteNames[d], noteNames[thirdDeg], noteNames[fifthDeg])

            // 罗马数字
            val base = romanBase(d + 1)
            val roman = when (quality) {
                ChordQuality.MAJOR -> base
                ChordQuality.MINOR -> base.lowercase()
                ChordQuality.DIMINISHED -> base.lowercase() + "°"
                ChordQuality.AUGMENTED -> base + "+"
            }

            // 和弦显示名：根音名 + 性质符号
            val display = noteNames[d] + quality.symbol

            // MIDI 音符（柱式和弦，根音锚定在音阶级位置）
            val rootMidi = (tonicMidi + rootSemi).coerceIn(21, 108)
            val thirdMidi = (rootMidi + interval3).coerceIn(21, 108)
            val fifthMidi = (rootMidi + interval5).coerceIn(21, 108)

            result.add(
                DiatonicChord(
                    degree = d + 1,
                    romanNumeral = roman,
                    rootPc = rootPc,
                    quality = quality,
                    noteNames = chordNames,
                    displayName = display,
                    midiNotes = listOf(rootMidi, thirdMidi, fifthMidi)
                )
            )
        }
        return result
    }

    /**
     * 构建调性的完整信息。
     */
    fun keyInfo(key: CircleKey): KeyInfo {
        val pos = positionOf(key)
        val sig = keySignature(key)
        val tName = tonicName(key)
        val displayName = if (key.isMajor) {
            "$tName${CircleMode.MAJOR.displayName}"
        } else {
            // 小调传统用小写主音字母
            "${tName.lowercase()}${CircleMode.MINOR.displayName}"
        }
        val relKey = relativeKey(key)
        val relName = tonicName(relKey)
        val relDisplay = if (relKey.isMajor) {
            "$relName${CircleMode.MAJOR.displayName}"
        } else {
            "${relName.lowercase()}${CircleMode.MINOR.displayName}"
        }

        return KeyInfo(
            key = key,
            displayName = displayName,
            tonicName = tName,
            signature = sig,
            preferFlats = preferFlats(pos),
            position = pos,
            angleDegrees = angleDegrees(pos),
            scalePcs = scalePcs(key),
            scaleNoteNames = scaleNoteNames(key),
            scaleMidiNotes = scaleMidiNotes(key),
            relativeKey = relKey,
            relativeDisplayName = relDisplay
        )
    }

    /**
     * 近关系调（圆环上顺时针与逆时针各一格的调性 + 关系调）。
     *
     * 近关系调是与当前调共享最多调号音的调，通常用于转调与和声进行。
     *
     * 返回 5 个近关系调（不含自身）：属调、下属调、关系调，
     * 以及属调与下属调各自的关系调。
     */
    fun closelyRelatedKeys(key: CircleKey): List<CircleKey> {
        val pos = positionOf(key)
        val clockwise = (pos + 1) % POSITIONS   // 属方向（+1 升号 / -1 降号）
        val counter = (pos - 1 + POSITIONS) % POSITIONS  // 下属方向
        val domPc = majorPcAt(clockwise, key.mode)
        val subPc = majorPcAt(counter, key.mode)
        val rel = relativeKey(key)
        val domRel = relativeKey(CircleKey(domPc, key.mode))
        val subRel = relativeKey(CircleKey(subPc, key.mode))
        return listOf(
            CircleKey(domPc, key.mode),  // 属调
            CircleKey(subPc, key.mode),  // 下属调
            rel,                         // 关系调
            domRel,                      // 属调的关系调
            subRel                       // 下属调的关系调
        ).distinct()
    }

    /**
     * 给定圆环位置与调式，计算主音音级类。
     * 位置 p 的主音 pc = 7·p (mod 12)（大调基准）；小调主音 = 大调主音 - 3。
     */
    fun majorPcAt(position: Int, mode: CircleMode): Int {
        val majorPc = ((7 * position) % SEMITONES + SEMITONES) % SEMITONES
        return if (mode == CircleMode.MAJOR) majorPc else (majorPc - 3 + SEMITONES) % SEMITONES
    }

    /**
     * 列出指定调式的全部 12 个调性（按圆环顺时针顺序）。
     */
    fun allKeys(mode: CircleMode): List<CircleKey> {
        return (0 until POSITIONS).map { pos ->
            CircleKey(majorPcAt(pos, mode), mode)
        }
    }

    /**
     * 计算音阶各音的频率列表（Hz），用于音频合成。
     */
    fun scaleFrequencies(key: CircleKey): List<Double> {
        return scaleMidiNotes(key).map { MusicUtils.midiToFrequency(it) }
    }
}
