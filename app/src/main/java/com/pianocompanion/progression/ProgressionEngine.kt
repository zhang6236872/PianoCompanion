package com.pianocompanion.progression

import com.pianocompanion.chord.ChordEngine
import com.pianocompanion.chord.ChordInversion
import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.chord.ChordType
import com.pianocompanion.chord.ChordVoicing

/**
 * 和弦进行构建引擎（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 负责将抽象的和弦进行模板（罗马数字序列）实例化为特定调性中的具体和弦发音。
 * 核心能力：
 * - 罗马数字 → 音阶级数 → 根音 pitch-class 偏移 → 具体根音
 * - 调式感知的和弦质量映射（大调 I=大三和弦, ii=小三和弦; 小调 i=小三和弦, III=大三和弦...）
 * - 顺阶平滑声部连接（voice-leading）优化
 * - 内置常见进行模板库
 */
object ProgressionEngine {

    private const val SEMITONES_PER_OCTAVE = 12

    /**
     * 大调音阶的音程结构（全全半全全全半）。
     * 用于计算各音级距主音的半音偏移。
     * degreeOffset[0]=0 (I), [1]=2 (ii), [2]=4 (iii), [3]=5 (IV), [4]=7 (V), [5]=9 (vi), [6]=11 (vii°)
     */
    private val MAJOR_SCALE_OFFSETS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    /**
     * 自然小调音阶的音程结构（全半全全半全全）。
     * degreeOffset[0]=0 (i), [1]=2 (ii°), [2]=3 (III), [3]=5 (iv), [4]=7 (v), [5]=8 (VI), [6]=10 (VII)
     */
    private val MINOR_SCALE_OFFSETS = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    /**
     * 大调各音级的三和弦类型（顺阶和弦）。
     * I=大三, ii=小三, iii=小三, IV=大三, V=大三, vi=小三, vii°=减三
     */
    private val MAJOR_TRIAD_TYPES = listOf(
        ChordType.MAJOR,    // I
        ChordType.MINOR,    // ii
        ChordType.MINOR,    // iii
        ChordType.MAJOR,    // IV
        ChordType.MAJOR,    // V
        ChordType.MINOR,    // vi
        ChordType.DIMINISHED // vii°
    )

    /**
     * 大调各音级的七和弦类型。
     */
    private val MAJOR_SEVENTH_TYPES = listOf(
        ChordType.MAJOR_7,      // Imaj7
        ChordType.MINOR_7,      // iim7
        ChordType.MINOR_7,      // iiim7
        ChordType.MAJOR_7,      // IVmaj7
        ChordType.DOMINANT_7,   // V7
        ChordType.MINOR_7,      // vim7
        ChordType.HALF_DIMINISHED_7 // viiø7
    )

    /**
     * 自然小调各音级的三和弦类型。
     * i=小三, ii°=减三, III=大三, iv=小三, v=小三, VI=大三, VII=大三
     */
    private val MINOR_TRIAD_TYPES = listOf(
        ChordType.MINOR,      // i
        ChordType.DIMINISHED, // ii°
        ChordType.MAJOR,      // III
        ChordType.MINOR,      // iv
        ChordType.MINOR,      // v (natural minor)
        ChordType.MAJOR,      // VI
        ChordType.MAJOR       // VII
    )

    /**
     * 小调各音级的七和弦类型（自然小调）。
     */
    private val MINOR_SEVENTH_TYPES = listOf(
        ChordType.MINOR_7,       // im7
        ChordType.HALF_DIMINISHED_7, // iiø7
        ChordType.MAJOR_7,       // IIImaj7
        ChordType.MINOR_7,       // ivm7
        ChordType.MINOR_7,       // vm7
        ChordType.DOMINANT_7,    // VI7
        ChordType.DOMINANT_7     // VII7
    )

    /**
     * 从罗马数字解析出和弦类型。
     *
     * 规则优先级：
     * 1. 若指定了 [RomanNumeral.explicitType]，直接使用（用于非顺阶和弦，如蓝调属七）
     * 2. 七和弦使用顺阶七和弦类型表（Imaj7, iim7, V7 等在调内自然产生）
     * 3. 三和弦使用罗马数字大小写推断质量：
     *    - 大写（I, IV, V）→ 大三和弦
     *    - 小写（ii, iii, vi）→ 小三和弦
     *    - ° 标记 → 减三和弦（vii°）
     *    - + 标记 → 增三和弦
     *
     * 这样既能正确处理所有顺阶和弦（大小写与调内质量一致），
     * 也能处理和声小调的 V（大写=大三和弦）等变化和弦。
     */
    fun chordTypeFor(
        romanNumeral: RomanNumeral,
        mode: ProgressionMode,
        scaleDegree: Int = romanNumeral.scaleDegree
    ): ChordType {
        // 显式覆盖优先
        romanNumeral.explicitType?.let { return it }

        val numeral = romanNumeral.numeral

        if (romanNumeral.isSeventh) {
            val isMajorMode = mode == ProgressionMode.MAJOR
            return if (isMajorMode) MAJOR_SEVENTH_TYPES[scaleDegree] else MINOR_SEVENTH_TYPES[scaleDegree]
        }

        // 三和弦：使用罗马数字大小写推断和弦质量
        // 这对所有顺阶和弦都正确（大写=大三、小写=小三、°=减三）
        // 也正确处理和声小调 V（大写→大三和弦）
        val hasDiminished = numeral.contains("°")
        val hasAugmented = numeral.contains("+")
        val isUppercase = numeral.first().isUpperCase()

        return when {
            hasAugmented -> ChordType.AUGMENTED
            hasDiminished -> ChordType.DIMINISHED
            isUppercase -> ChordType.MAJOR
            else -> ChordType.MINOR
        }
    }

    /**
     * 计算指定音级在特定调式中的根音。
     *
     * @param key 调性根音
     * @param scaleDegree 音级（0-6）
     * @param mode 调式
     * @return 对应的 [ChordRoot]
     */
    fun chordRootForDegree(
        key: ChordRoot,
        scaleDegree: Int,
        mode: ProgressionMode
    ): ChordRoot {
        val offsets = if (mode == ProgressionMode.MAJOR) MAJOR_SCALE_OFFSETS else MINOR_SCALE_OFFSETS
        val offset = offsets[scaleDegree]
        val pc = ((key.pitchClass + offset) % SEMITONES_PER_OCTAVE + SEMITONES_PER_OCTAVE) % SEMITONES_PER_OCTAVE
        return ChordRoot.entries.first { it.pitchClass == pc }
    }

    /**
     * 实例化进行模板到特定调性。
     *
     * @param template 进行模板
     * @param key 调性根音
     * @param preferFlats 是否使用降号记法（默认自动判断）
     * @return [ProgressionInstance] 包含具体的和弦列表
     */
    fun instantiate(
        template: ProgressionTemplate,
        key: ChordRoot = template.exampleKey,
        preferFlats: Boolean = ChordEngine.preferFlatsKey(key)
    ): ProgressionInstance {
        val chords = template.numerals.mapIndexed { index, rn ->
            val chordRoot = chordRootForDegree(key, rn.scaleDegree, template.mode)
            val type = chordTypeFor(rn, template.mode)
            val voicing = ChordEngine.build(chordRoot, type, ChordInversion.ROOT_POSITION, preferFlats)
            ProgressionChord(
                romanNumeral = rn,
                voicing = voicing,
                measureIndex = index
            )
        }
        return ProgressionInstance(
            template = template,
            key = key,
            chords = chords,
            preferFlats = preferFlats
        )
    }

    /**
     * 将进行实例移调到新调性。
     *
     * 保持罗马数字结构不变，仅改变具体和弦的根音。
     */
    fun transpose(
        instance: ProgressionInstance,
        newKey: ChordRoot
    ): ProgressionInstance {
        return instantiate(instance.template, newKey, ChordEngine.preferFlatsKey(newKey))
    }

    /**
     * 获取进行中所有和弦的 MIDI 音符列表（用于音频渲染和键盘高亮）。
     */
    fun allMidiNotes(instance: ProgressionInstance): List<List<Int>> {
        return instance.chords.map { chord -> chord.voicing.midiNotes }
    }

    /**
     * 获取进行中所有和弦的音名列表。
     */
    fun allNoteNames(instance: ProgressionInstance): List<List<String>> {
        return instance.chords.map { chord -> chord.voicing.noteNames }
    }

    /**
     * 获取进行中所有和弦的完整名称列表。
     */
    fun allChordNames(instance: ProgressionInstance): List<String> {
        return instance.chords.map { chord -> chord.voicing.fullName }
    }

    /**
     * 判断根音是否适合使用降号记法。
     * 复用 ChordEngine 的逻辑。
     */
    fun preferFlatsFor(key: ChordRoot): Boolean = ChordEngine.preferFlatsKey(key)

    // ════════════════════════════════════════════════════════════
    //  内置进行模板库
    // ════════════════════════════════════════════════════════════

    /**
     * 内置常见和弦进行模板库。
     *
     * 涵盖流行、爵士、古典、蓝调等主要风格中最常用的进行模式。
     */
    val builtinTemplates: List<ProgressionTemplate> = listOf(
        // ── 流行 (Pop) ──
        ProgressionTemplate(
            id = "pop_axis",
            name = "I-V-vi-IV",
            displayName = "流行万能进行",
            description = "现代流行音乐中最常见的和弦进行，被数千首流行歌曲使用。" +
                "又称「轴心进行」（Axis progression），情感丰富且极易记忆。",
            genre = ProgressionGenre.POP,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I"), RomanNumeral(4, "V"),
                RomanNumeral(5, "vi"), RomanNumeral(3, "IV")
            ),
            exampleKey = ChordRoot.C
        ),
        ProgressionTemplate(
            id = "pop_50s",
            name = "I-vi-IV-V",
            displayName = "50年代进行",
            description = "50-60年代经典流行歌曲的标准进行，又称「Doo-wop进行」。" +
                "柔和怀旧的经典和声走向。",
            genre = ProgressionGenre.POP,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I"), RomanNumeral(5, "vi"),
                RomanNumeral(3, "IV"), RomanNumeral(4, "V")
            ),
            exampleKey = ChordRoot.C
        ),
        ProgressionTemplate(
            id = "pop_cannon",
            name = "I-V-vi-iii-IV-I-IV-V",
            displayName = "卡农进行",
            description = "帕赫贝尔《D大调卡农》的经典低音线条进行，" +
                "现代流行乐中广泛借用，优美而富有层次。",
            genre = ProgressionGenre.CLASSICAL,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I"), RomanNumeral(4, "V"),
                RomanNumeral(5, "vi"), RomanNumeral(2, "iii"),
                RomanNumeral(3, "IV"), RomanNumeral(0, "I"),
                RomanNumeral(3, "IV"), RomanNumeral(4, "V")
            ),
            exampleKey = ChordRoot.D
        ),
        ProgressionTemplate(
            id = "pop_vi_IV",
            name = "vi-IV-I-V",
            displayName = "流行变体进行",
            description = "从 vi 开始的流行进行变体，具有更柔和、更感性的色彩，" +
                "适合抒情和情感丰富的段落。",
            genre = ProgressionGenre.POP,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(5, "vi"), RomanNumeral(3, "IV"),
                RomanNumeral(0, "I"), RomanNumeral(4, "V")
            ),
            exampleKey = ChordRoot.G
        ),

        // ── 爵士 (Jazz) ──
        ProgressionTemplate(
            id = "jazz_ii_V_I",
            name = "ii-V-I",
            displayName = "爵士 ii-V-I",
            description = "爵士乐中最核心的和弦进行，是所有爵士标准曲的基础。" +
                "展现了属七和弦到主和弦的经典张力-解决运动。",
            genre = ProgressionGenre.JAZZ,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(1, "ii", isSeventh = true),
                RomanNumeral(4, "V", isSeventh = true),
                RomanNumeral(0, "I", isSeventh = true)
            ),
            exampleKey = ChordRoot.C
        ),
        ProgressionTemplate(
            id = "jazz_rhythm_changes",
            name = "I-vi-ii-V",
            displayName = "节奏变换进行",
            description = "格什温《I Got Rhythm》的 A 段进行，爵士乐中仅次于" +
                "蓝调的第二常用进行，无数 Bebop 名曲基于此。",
            genre = ProgressionGenre.JAZZ,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I", isSeventh = true),
                RomanNumeral(5, "vi", isSeventh = true),
                RomanNumeral(1, "ii", isSeventh = true),
                RomanNumeral(4, "V", isSeventh = true)
            ),
            exampleKey = ChordRoot.B_FLAT
        ),
        ProgressionTemplate(
            id = "jazz_minor_ii_V_i",
            name = "ii°-V-i",
            displayName = "小调 ii-V-i",
            description = "小调爵士中的标准进行，ii° 为半减七和弦，" +
                "V 为属七和弦（和声小调），解决到小调主和弦。",
            genre = ProgressionGenre.JAZZ,
            mode = ProgressionMode.MINOR,
            numerals = listOf(
                RomanNumeral(1, "ii°", isSeventh = true),
                RomanNumeral(4, "V", isSeventh = true),
                RomanNumeral(0, "i", isSeventh = true)
            ),
            exampleKey = ChordRoot.A
        ),

        // ── 古典 (Classical) ──
        ProgressionTemplate(
            id = "classical_authentic",
            name = "I-IV-V-I",
            displayName = "古典正格终止",
            description = "古典音乐中最基本的完全正格终止，" +
                "I→IV→V→I 展现了功能和声中下属→属→主的核心运动。",
            genre = ProgressionGenre.CLASSICAL,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I"), RomanNumeral(3, "IV"),
                RomanNumeral(4, "V"), RomanNumeral(0, "I")
            ),
            exampleKey = ChordRoot.C
        ),
        ProgressionTemplate(
            id = "classical_plagal",
            name = "I-IV-I",
            displayName = "变格终止",
            description = "变格终止（Plagal cadence），I→IV→I 又称「阿门终止」，" +
                "教堂音乐和圣歌的标志性进行。",
            genre = ProgressionGenre.CLASSICAL,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I"), RomanNumeral(3, "IV"), RomanNumeral(0, "I")
            ),
            exampleKey = ChordRoot.F
        ),
        ProgressionTemplate(
            id = "classical_andalusian",
            name = "i-VII-VI-V",
            displayName = "安达卢西亚进行",
            description = "弗拉门戈和西班牙音乐中的标志性进行，" +
                "小调音阶下行，富有戏剧性和异域风情。",
            genre = ProgressionGenre.CLASSICAL,
            mode = ProgressionMode.MINOR,
            numerals = listOf(
                RomanNumeral(0, "i"), RomanNumeral(6, "VII"),
                RomanNumeral(5, "VI"), RomanNumeral(4, "V")
            ),
            exampleKey = ChordRoot.A
        ),

        // ── 蓝调 (Blues) ──
        ProgressionTemplate(
            id = "blues_basic",
            name = "I7-IV7-I7-V7",
            displayName = "蓝调基本进行",
            description = "12小节蓝调的核心和声骨架，所有和弦都是属七和弦，" +
                "蓝调、摇滚和爵士布鲁斯的基础。",
            genre = ProgressionGenre.BLUES,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I", isSeventh = true, explicitType = ChordType.DOMINANT_7),
                RomanNumeral(3, "IV", isSeventh = true, explicitType = ChordType.DOMINANT_7),
                RomanNumeral(0, "I", isSeventh = true, explicitType = ChordType.DOMINANT_7),
                RomanNumeral(4, "V", isSeventh = true, explicitType = ChordType.DOMINANT_7)
            ),
            exampleKey = ChordRoot.C,
            beatsPerChord = 4
        ),

        // ── 民歌 (Folk) ──
        ProgressionTemplate(
            id = "folk_i_IV_V",
            name = "I-IV-V",
            displayName = "民歌三和弦进行",
            description = "最基础的三和弦进行，无数民歌、儿歌和乡村歌曲的基础。" +
                "简单、明亮、充满生命力。",
            genre = ProgressionGenre.FOLK,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(0, "I"), RomanNumeral(3, "IV"), RomanNumeral(4, "V")
            ),
            exampleKey = ChordRoot.G
        ),

        // ── 摇滚 (Rock) ──
        ProgressionTemplate(
            id = "rock_bVII_IV_I",
            name = "bVII-IV-I",
            displayName = "摇滚混合利底亚进行",
            description = "摇滚乐中的经典进行，使用降 VII 级和弦（混合利底亚调式特征），" +
                "产生宏大、开放的声响效果。",
            genre = ProgressionGenre.ROCK,
            mode = ProgressionMode.MAJOR,
            numerals = listOf(
                RomanNumeral(6, "VII"), RomanNumeral(3, "IV"), RomanNumeral(0, "I")
            ),
            exampleKey = ChordRoot.D
        ),
        ProgressionTemplate(
            id = "rock_minor_i_VI_III_VII",
            name = "i-VI-III-VII",
            displayName = "小调摇滚进行",
            description = "小调摇滚和金属乐的常用进行，气势磅礴且富有张力。",
            genre = ProgressionGenre.ROCK,
            mode = ProgressionMode.MINOR,
            numerals = listOf(
                RomanNumeral(0, "i"), RomanNumeral(5, "VI"),
                RomanNumeral(2, "III"), RomanNumeral(6, "VII")
            ),
            exampleKey = ChordRoot.E
        )
    )

    /**
     * 按风格分组返回进行模板。
     */
    fun templatesByGenre(): Map<ProgressionGenre, List<ProgressionTemplate>> {
        return builtinTemplates.groupBy { it.genre }
    }

    /**
     * 根据 ID 查找进行模板。
     */
    fun findTemplate(id: String): ProgressionTemplate? {
        return builtinTemplates.firstOrNull { it.id == id }
    }

    /**
     * 获取适用于指定调性的所有可用进行模板（所有模板都可在任意调使用，这里仅做分类返回）。
     */
    fun allTemplates(): List<ProgressionTemplate> = builtinTemplates
}
