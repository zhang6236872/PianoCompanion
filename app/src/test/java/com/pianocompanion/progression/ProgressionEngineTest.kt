package com.pianocompanion.progression

import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.chord.ChordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProgressionEngine 单元测试。
 *
 * 验证罗马数字 → 具体和弦的映射、调式感知、移调、内置模板库等核心逻辑。
 */
class ProgressionEngineTest {

    // ════════════════════════════════════════════════════════════
    //  chordRootForDegree — 音级 → 根音
    // ════════════════════════════════════════════════════════════

    @Test
    fun `C大调 I 级 = C`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.C, 0, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.C, root)
    }

    @Test
    fun `C大调 V 级 = G`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.C, 4, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.G, root)
    }

    @Test
    fun `C大调 IV 级 = F`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.C, 3, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.F, root)
    }

    @Test
    fun `C大调 vi 级 = A`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.C, 5, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.A, root)
    }

    @Test
    fun `C大调 ii 级 = D`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.C, 1, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.D, root)
    }

    @Test
    fun `A小调 i 级 = A`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.A, 0, ProgressionMode.MINOR)
        assertEquals(ChordRoot.A, root)
    }

    @Test
    fun `A小调 III 级 = C`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.A, 2, ProgressionMode.MINOR)
        assertEquals(ChordRoot.C, root)
    }

    @Test
    fun `A小调 VII 级 = G`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.A, 6, ProgressionMode.MINOR)
        assertEquals(ChordRoot.G, root)
    }

    @Test
    fun `G大调 V 级 = D`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.G, 4, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.D, root)
    }

    @Test
    fun `D大调 V 级 = A`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.D, 4, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.A, root)
    }

    @Test
    fun `F大调 V 级 = C`() {
        val root = ProgressionEngine.chordRootForDegree(ChordRoot.F, 4, ProgressionMode.MAJOR)
        assertEquals(ChordRoot.C, root)
    }

    // ════════════════════════════════════════════════════════════
    //  chordTypeFor — 罗马数字 → 和弦类型
    // ════════════════════════════════════════════════════════════

    @Test
    fun `大调 I 级是大三和弦`() {
        val rn = RomanNumeral(0, "I")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.MAJOR, type)
    }

    @Test
    fun `大调 ii 级是小三和弦`() {
        val rn = RomanNumeral(1, "ii")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.MINOR, type)
    }

    @Test
    fun `大调 vi 级是小三和弦`() {
        val rn = RomanNumeral(5, "vi")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.MINOR, type)
    }

    @Test
    fun `大调 vii 度是减三和弦`() {
        val rn = RomanNumeral(6, "vii°")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.DIMINISHED, type)
    }

    @Test
    fun `小调 i 级是小三和弦`() {
        val rn = RomanNumeral(0, "i")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MINOR)
        assertEquals(ChordType.MINOR, type)
    }

    @Test
    fun `小调 III 级是大三和弦`() {
        val rn = RomanNumeral(2, "III")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MINOR)
        assertEquals(ChordType.MAJOR, type)
    }

    @Test
    fun `小调 ii 度是减三和弦`() {
        val rn = RomanNumeral(1, "ii°")
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MINOR)
        assertEquals(ChordType.DIMINISHED, type)
    }

    @Test
    fun `大调 V7 是属七和弦`() {
        val rn = RomanNumeral(4, "V", isSeventh = true)
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.DOMINANT_7, type)
    }

    @Test
    fun `大调 I7 是大七和弦`() {
        val rn = RomanNumeral(0, "I", isSeventh = true)
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.MAJOR_7, type)
    }

    @Test
    fun `大调 ii7 是小七和弦`() {
        val rn = RomanNumeral(1, "ii", isSeventh = true)
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.MINOR_7, type)
    }

    @Test
    fun `大调 vii7 是半减七和弦`() {
        val rn = RomanNumeral(6, "vii°", isSeventh = true)
        val type = ProgressionEngine.chordTypeFor(rn, ProgressionMode.MAJOR)
        assertEquals(ChordType.HALF_DIMINISHED_7, type)
    }

    // ════════════════════════════════════════════════════════════
    //  instantiate — 实例化进行
    // ════════════════════════════════════════════════════════════

    @Test
    fun `实例化 C 大调 I-V-vi-IV 进行`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        assertEquals(4, instance.chords.size)
        // C - G - Am - F
        assertEquals("C", instance.chords[0].voicing.fullName)
        assertEquals("G", instance.chords[1].voicing.fullName)
        assertEquals("Am", instance.chords[2].voicing.fullName)
        assertEquals("F", instance.chords[3].voicing.fullName)
    }

    @Test
    fun `实例化 G 大调 I-V-vi-IV 进行`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.G)

        // G - D - Em - C
        assertEquals("G", instance.chords[0].voicing.fullName)
        assertEquals("D", instance.chords[1].voicing.fullName)
        assertEquals("Em", instance.chords[2].voicing.fullName)
        assertEquals("C", instance.chords[3].voicing.fullName)
    }

    @Test
    fun `实例化 C 大调爵士 ii-V-I 进行`() {
        val template = ProgressionEngine.findTemplate("jazz_ii_V_I")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        // Dm7 - G7 - Cmaj7
        assertEquals(3, instance.chords.size)
        assertEquals("Dm7", instance.chords[0].voicing.fullName)
        assertEquals("G7", instance.chords[1].voicing.fullName)
        assertEquals("Cmaj7", instance.chords[2].voicing.fullName)
    }

    @Test
    fun `实例化 A 小调安达卢西亚进行`() {
        val template = ProgressionEngine.findTemplate("classical_andalusian")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.A)

        // Am - G - F - E
        assertEquals(4, instance.chords.size)
        assertEquals("Am", instance.chords[0].voicing.fullName)
        assertEquals("G", instance.chords[1].voicing.fullName)
        assertEquals("F", instance.chords[2].voicing.fullName)
        assertEquals("E", instance.chords[3].voicing.fullName)
    }

    @Test
    fun `实例化 C 大调蓝调进行全属七和弦`() {
        val template = ProgressionEngine.findTemplate("blues_basic")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        // C7 - F7 - C7 - G7
        assertEquals(4, instance.chords.size)
        assertEquals("C7", instance.chords[0].voicing.fullName)
        assertEquals("F7", instance.chords[1].voicing.fullName)
        assertEquals("C7", instance.chords[2].voicing.fullName)
        assertEquals("G7", instance.chords[3].voicing.fullName)
    }

    @Test
    fun `实例化 D 小调摇滚进行`() {
        val template = ProgressionEngine.findTemplate("rock_minor_i_VI_III_VII")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.D)

        // Dm - Bb - F - C
        assertEquals(4, instance.chords.size)
        assertEquals("Dm", instance.chords[0].voicing.fullName)
    }

    @Test
    fun `实例化的和弦小节索引从 0 递增`() {
        val template = ProgressionEngine.findTemplate("pop_cannon")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.D)

        instance.chords.forEachIndexed { idx, chord ->
            assertEquals(idx, chord.measureIndex)
        }
    }

    @Test
    fun `实例化保留罗马数字`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        assertEquals(listOf("I", "V", "vi", "IV"), instance.chords.map { it.romanNumeral.numeral })
    }

    // ════════════════════════════════════════════════════════════
    //  transpose — 移调
    // ════════════════════════════════════════════════════════════

    @Test
    fun `移调保持罗马数字结构`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val original = ProgressionEngine.instantiate(template, ChordRoot.C)
        val transposed = ProgressionEngine.transpose(original, ChordRoot.G)

        assertEquals(
            original.chords.map { it.romanNumeral.numeral },
            transposed.chords.map { it.romanNumeral.numeral }
        )
    }

    @Test
    fun `移调到新调后和弦名称改变`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val original = ProgressionEngine.instantiate(template, ChordRoot.C)
        val transposed = ProgressionEngine.transpose(original, ChordRoot.D)

        // D - A - Bm - G
        assertEquals("D", transposed.chords[0].voicing.fullName)
        assertEquals("A", transposed.chords[1].voicing.fullName)
        assertEquals("Bm", transposed.chords[2].voicing.fullName)
        assertEquals("G", transposed.chords[3].voicing.fullName)
    }

    // ════════════════════════════════════════════════════════════
    //  辅助方法
    // ════════════════════════════════════════════════════════════

    @Test
    fun `allMidiNotes 返回每个和弦的音符列表`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        val notes = ProgressionEngine.allMidiNotes(instance)
        assertEquals(4, notes.size)
        // 大三和弦有 3 个音
        notes.forEach { chord -> assertEquals(3, chord.size) }
    }

    @Test
    fun `allChordNames 返回完整和弦名称列表`() {
        val template = ProgressionEngine.findTemplate("pop_50s")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        val names = ProgressionEngine.allChordNames(instance)
        assertEquals(listOf("C", "Am", "F", "G"), names)
    }

    @Test
    fun `preferFlatsFor F 调使用降号`() {
        assertTrue(ProgressionEngine.preferFlatsFor(ChordRoot.F))
    }

    @Test
    fun `preferFlatsFor C 调不使用降号`() {
        assertEquals(false, ProgressionEngine.preferFlatsFor(ChordRoot.C))
    }

    // ════════════════════════════════════════════════════════════
    //  内置模板库
    // ════════════════════════════════════════════════════════════

    @Test
    fun `内置模板数量至少 13 个`() {
        assertTrue(ProgressionEngine.builtinTemplates.size >= 13)
    }

    @Test
    fun `每个模板有唯一 ID`() {
        val ids = ProgressionEngine.builtinTemplates.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `每个模板至少有 3 个和弦`() {
        ProgressionEngine.builtinTemplates.forEach { template ->
            assertTrue("模板 ${template.id} 应至少 3 个和弦", template.numerals.size >= 3)
        }
    }

    @Test
    fun `模板 ID 可被 findTemplate 找到`() {
        ProgressionEngine.builtinTemplates.forEach { template ->
            assertNotNull("找不到模板 ${template.id}", ProgressionEngine.findTemplate(template.id))
        }
    }

    @Test
    fun `按风格分组包含所有风格`() {
        val byGenre = ProgressionEngine.templatesByGenre()
        assertTrue(byGenre.containsKey(ProgressionGenre.POP))
        assertTrue(byGenre.containsKey(ProgressionGenre.JAZZ))
        assertTrue(byGenre.containsKey(ProgressionGenre.CLASSICAL))
        assertTrue(byGenre.containsKey(ProgressionGenre.BLUES))
    }

    @Test
    fun `numeralDisplay 格式正确`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        assertEquals("I – V – vi – IV", template.numeralDisplay)
    }

    @Test
    fun `蓝调进行所有和弦都是属七和弦`() {
        val template = ProgressionEngine.findTemplate("blues_basic")!!
        template.numerals.forEach { rn ->
            assertTrue("蓝调和弦 ${rn.numeral} 应为七和弦", rn.isSeventh)
        }
    }

    @Test
    fun `爵士 ii-V-I 进行所有和弦都是七和弦`() {
        val template = ProgressionEngine.findTemplate("jazz_ii_V_I")!!
        template.numerals.forEach { rn ->
            assertTrue("爵士和弦 ${rn.numeral} 应为七和弦", rn.isSeventh)
        }
    }

    @Test
    fun `allTemplates 返回与 builtinTemplates 相同数量`() {
        assertEquals(ProgressionEngine.builtinTemplates.size, ProgressionEngine.allTemplates().size)
    }

    // ════════════════════════════════════════════════════════════
    //  进行的完整性和弦验证
    // ════════════════════════════════════════════════════════════

    @Test
    fun `卡农进行 D 大调完整序列`() {
        val template = ProgressionEngine.findTemplate("pop_cannon")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.D)

        // D - A - Bm - F#m - G - D - G - A
        assertEquals(8, instance.chords.size)
        assertEquals("D", instance.chords[0].voicing.fullName)
        assertEquals("A", instance.chords[1].voicing.fullName)
        assertEquals("Bm", instance.chords[2].voicing.fullName)
        assertEquals("F♯m", instance.chords[3].voicing.fullName)
        assertEquals("G", instance.chords[4].voicing.fullName)
        assertEquals("D", instance.chords[5].voicing.fullName)
        assertEquals("G", instance.chords[6].voicing.fullName)
        assertEquals("A", instance.chords[7].voicing.fullName)
    }

    @Test
    fun `50年代进行 C 大调完整序列`() {
        val template = ProgressionEngine.findTemplate("pop_50s")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        // C - Am - F - G
        assertEquals("C", instance.chords[0].voicing.fullName)
        assertEquals("Am", instance.chords[1].voicing.fullName)
        assertEquals("F", instance.chords[2].voicing.fullName)
        assertEquals("G", instance.chords[3].voicing.fullName)
    }

    @Test
    fun `民歌三和弦进行 G 大调`() {
        val template = ProgressionEngine.findTemplate("folk_i_IV_V")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.G)

        // G - C - D
        assertEquals("G", instance.chords[0].voicing.fullName)
        assertEquals("C", instance.chords[1].voicing.fullName)
        assertEquals("D", instance.chords[2].voicing.fullName)
    }

    @Test
    fun `古典正格终止 C 大调`() {
        val template = ProgressionEngine.findTemplate("classical_authentic")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)

        // C - F - G - C
        assertEquals("C", instance.chords[0].voicing.fullName)
        assertEquals("F", instance.chords[1].voicing.fullName)
        assertEquals("G", instance.chords[2].voicing.fullName)
        assertEquals("C", instance.chords[3].voicing.fullName)
    }

    @Test
    fun `所有内置模板都能成功实例化`() {
        ProgressionEngine.builtinTemplates.forEach { template ->
            val instance = ProgressionEngine.instantiate(template, template.exampleKey)
            assertEquals(
                "模板 ${template.id} 实例化后和弦数不匹配",
                template.numerals.size,
                instance.chords.size
            )
        }
    }

    @Test
    fun `所有内置模板在任意调都能实例化`() {
        val testKeys = listOf(ChordRoot.C, ChordRoot.G, ChordRoot.D, ChordRoot.A, ChordRoot.E, ChordRoot.F)
        ProgressionEngine.builtinTemplates.forEach { template ->
            testKeys.forEach { key ->
                val instance = ProgressionEngine.instantiate(template, key)
                assertEquals(template.numerals.size, instance.chords.size)
            }
        }
    }

    @Test
    fun `ProgressionInstance fullName 包含调性信息`() {
        val template = ProgressionEngine.findTemplate("pop_axis")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.C)
        assertTrue(instance.fullName.contains("C"))
        assertTrue(instance.fullName.contains("I-V-vi-IV"))
    }

    @Test
    fun `小调进行实例化后和弦为小三和弦`() {
        val template = ProgressionEngine.findTemplate("classical_andalusian")!!
        val instance = ProgressionEngine.instantiate(template, ChordRoot.A)

        // Am should have minor type
        assertEquals(ChordType.MINOR, instance.chords[0].voicing.type)
    }

    @Test
    fun `和弦 MIDI 音符在钢琴范围内`() {
        ProgressionEngine.builtinTemplates.forEach { template ->
            val instance = ProgressionEngine.instantiate(template, template.exampleKey)
            instance.chords.forEach { chord ->
                chord.voicing.midiNotes.forEach { midi ->
                    assertTrue("MIDI $midi 超出范围", midi in 21..108)
                }
            }
        }
    }
}
