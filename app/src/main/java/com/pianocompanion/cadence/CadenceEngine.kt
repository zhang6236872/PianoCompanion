package com.pianocompanion.cadence

import com.pianocompanion.chord.ChordEngine
import com.pianocompanion.chord.ChordInversion
import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.chord.ChordType
import com.pianocompanion.chord.ChordVoicing

/**
 * 终止式构建引擎（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 负责根据调性（主音 + 调式）和终止式类型，构建出具体的和弦序列。
 * 每个终止式由 [CadenceStep] 列表组成，每步包含罗马数字标记和 [ChordVoicing]。
 *
 * 内部使用调内顺阶和弦表确定各级和弦的类型（大三/小三/减三/属七等），
 * 并为每种终止式类型选择合适的转位以产生教科书级的声部进行。
 *
 * 算法核心：
 * 1. 根据调式确定 7 个音级的半音偏移表（大调 / 和声小调）
 * 2. 根据调式确定各级三和弦的质量类型（大/小/减/增）
 * 3. 根据终止式类型选择 1-2 个级数 + 转位 + 七和弦扩展
 * 4. 用 [ChordEngine.build] 构建每个和弦的 voicing
 */
object CadenceEngine {

    // ── 音阶半音偏移表 ──

    /** 大调音阶各音级相对于主音的半音偏移：I=0, II=2, III=4, IV=5, V=7, VI=9, VII=11 */
    private val MAJOR_SCALE_OFFSETS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    /** 和声小调音阶各音级相对于主音的半音偏移：i=0, ii=2, III=3, iv=5, V=7, VI=8, vii=11 */
    private val HARMONIC_MINOR_SCALE_OFFSETS = intArrayOf(0, 2, 3, 5, 7, 8, 11)

    // ── 调内顺阶三和弦质量表 ──

    /** 大调顺阶三和弦类型：I=大, ii=小, iii=小, IV=大, V=大, vi=小, vii°=减 */
    private val MAJOR_TRIAD_QUALITIES = listOf(
        ChordType.MAJOR,      // I
        ChordType.MINOR,      // ii
        ChordType.MINOR,      // iii
        ChordType.MAJOR,      // IV
        ChordType.MAJOR,      // V
        ChordType.MINOR,      // vi
        ChordType.DIMINISHED  // vii°
    )

    /** 和声小调顺阶三和弦类型：i=小, ii°=减, III+=增, iv=小, V=大, VI=大, vii°=减 */
    private val HARMONIC_MINOR_TRIAD_QUALITIES = listOf(
        ChordType.MINOR,      // i
        ChordType.DIMINISHED, // ii°
        ChordType.AUGMENTED,  // III+
        ChordType.MINOR,      // iv
        ChordType.MAJOR,      // V
        ChordType.MAJOR,      // VI
        ChordType.DIMINISHED  // vii°
    )

    /**
     * 获取指定调式下某音级的半音偏移。
     */
    fun scaleOffset(degree: Int, mode: CadenceMode): Int {
        val table = if (mode == CadenceMode.MAJOR) MAJOR_SCALE_OFFSETS else HARMONIC_MINOR_SCALE_OFFSETS
        return table[degree.coerceIn(0, 6)]
    }

    /**
     * 获取指定调式下某音级的顺阶三和弦类型。
     */
    fun triadQuality(degree: Int, mode: CadenceMode): ChordType {
        val table = if (mode == CadenceMode.MAJOR) MAJOR_TRIAD_QUALITIES else HARMONIC_MINOR_TRIAD_QUALITIES
        return table[degree.coerceIn(0, 6)]
    }

    /**
     * 根据调性主音和音级，计算顺阶和弦的根音 [ChordRoot]。
     *
     * @param keyRoot 调性主音
     * @param degree 音级 (0-6)
     * @param mode 调式
     * @return 顺阶和弦的根音
     */
    fun chordRootForDegree(keyRoot: ChordRoot, degree: Int, mode: CadenceMode): ChordRoot {
        val offset = scaleOffset(degree, mode)
        val pc = ((keyRoot.pitchClass + offset) % 12 + 12) % 12
        return ChordRoot.entries.first { it.pitchClass == pc }
    }

    /**
     * 判断某调性是否应使用降号记法。
     * 委托给 [ChordEngine.preferFlatsKey] 以保持与和弦词典一致。
     */
    fun preferFlatsFor(keyRoot: ChordRoot): Boolean = ChordEngine.preferFlatsKey(keyRoot)

    /**
     * 格式化罗马数字标记。
     *
     * 大写 = 大三/增三和弦，小写 = 小三/减三和弦。
     * 七和弦附加 "⁷"，转位附加上标数字。
     *
     * @param degree 音级 (0-6)
     * @param type 和弦类型
     * @param inversion 转位
     * @return 罗马数字字符串（如 "V⁷"、"I"、"iv⁶"）
     */
    fun romanNumeral(
        degree: Int,
        type: ChordType,
        inversion: ChordInversion
    ): String {
        val numerals = listOf("I", "II", "III", "IV", "V", "VI", "VII")
        val base = numerals[degree.coerceIn(0, 6)]

        // 大小写：大三/增三 → 大写，小三/减三 → 小写
        val cased = when (type) {
            ChordType.MAJOR, ChordType.AUGMENTED,
            ChordType.DOMINANT_7, ChordType.MAJOR_7, ChordType.MAJOR_6,
            ChordType.DOMINANT_9, ChordType.MAJOR_9, ChordType.ADD9, ChordType.ADD2 -> base.uppercase()
            ChordType.MINOR, ChordType.DIMINISHED,
            ChordType.MINOR_7, ChordType.DIMINISHED_7, ChordType.HALF_DIMINISHED_7,
            ChordType.MINOR_6, ChordType.MINOR_9 -> base.lowercase()
            ChordType.SUS2, ChordType.SUS4 -> base.uppercase() // 挂留和弦通常大写
        }

        val sb = StringBuilder(cased)

        // 减三/减七标记
        if (type == ChordType.DIMINISHED || type == ChordType.DIMINISHED_7) {
            sb.append("°")
        }
        // 增三标记
        if (type == ChordType.AUGMENTED) {
            sb.append("+")
        }
        // 半减七标记
        if (type == ChordType.HALF_DIMINISHED_7) {
            sb.append("ø")
        }

        // 七和弦标记
        when (type) {
            ChordType.DOMINANT_7, ChordType.DIMINISHED_7,
            ChordType.HALF_DIMINISHED_7, ChordType.MINOR_7 -> sb.append("⁷")
            ChordType.MAJOR_7 -> sb.append("ᴹ⁷")
            ChordType.DOMINANT_9 -> sb.append("⁹")
            ChordType.MINOR_9 -> sb.append("⁹")
            ChordType.MAJOR_9 -> sb.append("ᴹ⁹")
            else -> {}
        }

        // 转位标记
        when (inversion) {
            ChordInversion.FIRST_INVERSION -> sb.append("⁶")
            ChordInversion.SECOND_INVERSION -> sb.append("⁶₄")
            ChordInversion.THIRD_INVERSION -> sb.append("⁴₂")
            else -> {}
        }

        return sb.toString()
    }

    /**
     * 构建一个顺阶和弦的 [CadenceStep]。
     *
     * @param keyRoot 调性主音
     * @param degree 音级 (0-6)
     * @param mode 调式
     * @param useDominantSeventh 如果为 true 且是属音(V级)，构建属七和弦而非三和弦
     * @param inversion 转位
     * @return 包含罗马数字和 voicing 的 [CadenceStep]
     */
    fun buildDiatonicStep(
        keyRoot: ChordRoot,
        degree: Int,
        mode: CadenceMode,
        useDominantSeventh: Boolean = false,
        inversion: ChordInversion = ChordInversion.ROOT_POSITION
    ): CadenceStep {
        val root = chordRootForDegree(keyRoot, degree, mode)
        val preferFlats = preferFlatsFor(keyRoot)

        // 确定和弦类型
        val type = when {
            useDominantSeventh && degree == 4 -> ChordType.DOMINANT_7  // V7
            else -> triadQuality(degree, mode)
        }

        val voicing = ChordEngine.build(root, type, inversion, preferFlats)
        val rn = romanNumeral(degree, type, inversion)

        return CadenceStep(
            degree = degree,
            chordType = type,
            inversion = inversion,
            romanNumeral = rn,
            voicing = voicing
        )
    }

    /**
     * 实例化一个终止式：给定调性和终止式类型，生成完整的和弦序列。
     *
     * @param keyRoot 调性主音
     * @param cadenceType 终止式类型
     * @param mode 调式（大调默认；和声小调用于小调终止式）
     * @return [CadenceInstance] 包含和弦步骤、罗马数字摘要和说明
     */
    fun instantiate(
        keyRoot: ChordRoot,
        cadenceType: CadenceType,
        mode: CadenceMode = CadenceMode.MAJOR
    ): CadenceInstance {
        val preferFlats = preferFlatsFor(keyRoot)
        val steps = buildSteps(cadenceType, mode)

        val realized = steps.map { spec ->
            buildDiatonicStep(
                keyRoot = keyRoot,
                degree = spec.degree,
                mode = mode,
                useDominantSeventh = spec.useSeventh,
                inversion = spec.inversion
            )
        }

        val rnSummary = realized.joinToString(" → ") { it.romanNumeral }
        val keyName = formatKeyName(keyRoot, mode, preferFlats)

        return CadenceInstance(
            type = cadenceType,
            keyRoot = keyRoot,
            mode = mode,
            steps = realized,
            romanNumeralSummary = rnSummary,
            keyName = keyName,
            preferFlats = preferFlats
        )
    }

    /**
     * 获取指定终止式类型的调内和弦步骤规格。
     *
     * @return 步骤规格列表 [(级数, 是否用七和弦, 转位)]
     */
    private data class StepSpec(
        val degree: Int,
        val useSeventh: Boolean,
        val inversion: ChordInversion
    )

    private fun buildSteps(cadenceType: CadenceType, mode: CadenceMode): List<StepSpec> {
        return when (cadenceType) {
            // V⁷ → I (原位)
            CadenceType.PERFECT_AUTHENTIC -> listOf(
                StepSpec(4, useSeventh = true, ChordInversion.ROOT_POSITION),  // V⁷
                StepSpec(0, useSeventh = false, ChordInversion.ROOT_POSITION)  // I/i
            )
            // V⁶ → I (属和弦第一转位 → 主和弦原位)
            CadenceType.IMPERFECT_AUTHENTIC -> listOf(
                StepSpec(4, useSeventh = false, ChordInversion.FIRST_INVERSION), // V⁶
                StepSpec(0, useSeventh = false, ChordInversion.ROOT_POSITION)    // I/i
            )
            // IV → I (下属 → 主)
            CadenceType.PLAGAL -> listOf(
                StepSpec(3, useSeventh = false, ChordInversion.ROOT_POSITION),  // IV/iv
                StepSpec(0, useSeventh = false, ChordInversion.ROOT_POSITION)   // I/i
            )
            // V → vi (大调) / V → VI (小调)
            CadenceType.DECEPTIVE -> listOf(
                StepSpec(4, useSeventh = false, ChordInversion.ROOT_POSITION),  // V
                StepSpec(5, useSeventh = false, ChordInversion.ROOT_POSITION)   // vi/VI
            )
            // IV → V (下属 → 属)
            CadenceType.HALF -> listOf(
                StepSpec(3, useSeventh = false, ChordInversion.ROOT_POSITION),  // IV/iv
                StepSpec(4, useSeventh = false, ChordInversion.ROOT_POSITION)   // V
            )
            // iv⁶ → V (仅小调，下属第一转位 → 属)
            CadenceType.PHRYGIAN_HALF -> listOf(
                StepSpec(3, useSeventh = false, ChordInversion.FIRST_INVERSION), // iv⁶
                StepSpec(4, useSeventh = false, ChordInversion.ROOT_POSITION)    // V
            )
        }
    }

    /**
     * 格式化调性名称。
     *
     * 大调用大写字母（如 "C大调"），小调用小写字母（如 "a和声小调"）。
     */
    fun formatKeyName(keyRoot: ChordRoot, mode: CadenceMode, preferFlats: Boolean): String {
        val name = keyRoot.name(preferFlats)
        return when (mode) {
            CadenceMode.MAJOR -> "${name.uppercase()}大调"
            CadenceMode.HARMONIC_MINOR -> "${name.lowercase()}和声小调"
        }
    }

    /**
     * 获取终止式中某一步骤的和弦音名列表。
     */
    fun chordNoteNames(step: CadenceStep): List<String> = step.voicing.noteNames

    /**
     * 获取所有可用的终止式类型（按分类分组）。
     */
    fun allCadencesByCategory(): Map<CadenceCategory, List<CadenceType>> {
        return CadenceType.entries.groupBy { it.category }
    }

    /**
     * 获取指定终止式支持的调式列表。
     */
    fun supportedModes(cadenceType: CadenceType): List<CadenceMode> {
        return buildList {
            if (cadenceType.supportsMajor) add(CadenceMode.MAJOR)
            if (cadenceType.supportsMinor) add(CadenceMode.HARMONIC_MINOR)
        }
    }

    /**
     * 获取终止式中最后一步的解决和弦根音 [ChordRoot]。
     * 用于 UI 标注解决方向。
     */
    fun resolutionRoot(instance: CadenceInstance): ChordRoot? {
        return instance.finalChord?.let { step ->
            chordRootForDegree(instance.keyRoot, step.degree, instance.mode)
        }
    }
}
