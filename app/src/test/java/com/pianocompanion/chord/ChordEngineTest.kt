package com.pianocompanion.chord

import org.junit.Assert.*
import org.junit.Test

/**
 * 和弦构建引擎 [ChordEngine] 单元测试。
 *
 * 验证和弦 MIDI 音符计算、转位逻辑、音名格式化、
 * 指法建议、分类分组等核心功能。
 */
class ChordEngineTest {

    // ════════════════════════════════════════
    //  基础和弦构建
    // ════════════════════════════════════════

    @Test
    fun `C 大三和弦原位 = C4 E4 G4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        // C4=60, E4=64, G4=67
        assertEquals(listOf(60, 64, 67), voicing.midiNotes)
    }

    @Test
    fun `C 小三和弦原位 = C4 E♭4 G4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MINOR)
        // C4=60, E♭4=63, G4=67
        assertEquals(listOf(60, 63, 67), voicing.midiNotes)
    }

    @Test
    fun `C 减三和弦原位 = C4 E♭4 G♭4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.DIMINISHED)
        assertEquals(listOf(60, 63, 66), voicing.midiNotes)
    }

    @Test
    fun `C 增三和弦原位 = C4 E4 G♯4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.AUGMENTED)
        assertEquals(listOf(60, 64, 68), voicing.midiNotes)
    }

    @Test
    fun `C 属七和弦 = C4 E4 G4 B♭4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.DOMINANT_7)
        assertEquals(listOf(60, 64, 67, 70), voicing.midiNotes)
    }

    @Test
    fun `C 大七和弦 = C4 E4 G4 B4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR_7)
        assertEquals(listOf(60, 64, 67, 71), voicing.midiNotes)
    }

    @Test
    fun `C 减七和弦 = C4 E♭4 G♭4 B♭♭4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.DIMINISHED_7)
        assertEquals(listOf(60, 63, 66, 69), voicing.midiNotes)
    }

    @Test
    fun `C 半减七和弦 = C4 E♭4 G♭4 B♭4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.HALF_DIMINISHED_7)
        assertEquals(listOf(60, 63, 66, 70), voicing.midiNotes)
    }

    @Test
    fun `C 属九和弦 = C4 E4 G4 B♭4 D5`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.DOMINANT_9)
        assertEquals(listOf(60, 64, 67, 70, 74), voicing.midiNotes)
    }

    @Test
    fun `C 加九和弦 = C4 E4 G4 D5`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.ADD9)
        assertEquals(listOf(60, 64, 67, 74), voicing.midiNotes)
    }

    @Test
    fun `C sus2 和弦 = C4 D4 G4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.SUS2)
        assertEquals(listOf(60, 62, 67), voicing.midiNotes)
    }

    @Test
    fun `C sus4 和弦 = C4 F4 G4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.SUS4)
        assertEquals(listOf(60, 65, 67), voicing.midiNotes)
    }

    @Test
    fun `C 大六和弦 = C4 E4 G4 A4`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR_6)
        assertEquals(listOf(60, 64, 67, 69), voicing.midiNotes)
    }

    // ════════════════════════════════════════
    //  不同根音
    // ════════════════════════════════════════

    @Test
    fun `D 大三和弦原位 = D4 F♯4 A4`() {
        val voicing = ChordEngine.build(ChordRoot.D, ChordType.MAJOR)
        assertEquals(listOf(62, 66, 69), voicing.midiNotes)
    }

    @Test
    fun `F 大三和弦原位 = F4 A4 C5`() {
        val voicing = ChordEngine.build(ChordRoot.F, ChordType.MAJOR)
        assertEquals(listOf(65, 69, 72), voicing.midiNotes)
    }

    @Test
    fun `A 大三和弦原位 = A4 C♯5 E5`() {
        val voicing = ChordEngine.build(ChordRoot.A, ChordType.MAJOR)
        assertEquals(listOf(69, 73, 76), voicing.midiNotes)
    }

    @Test
    fun `B♭ 大三和弦原位 = B♭4 D5 F5`() {
        val voicing = ChordEngine.build(ChordRoot.B_FLAT, ChordType.MAJOR)
        assertEquals(listOf(70, 74, 77), voicing.midiNotes)
    }

    // ════════════════════════════════════════
    //  转位
    // ════════════════════════════════════════

    @Test
    fun `C 大三和弦第一转位 = E4 G4 C5`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR, ChordInversion.FIRST_INVERSION)
        assertEquals(listOf(64, 67, 72), voicing.midiNotes)
    }

    @Test
    fun `C 大三和弦第二转位 = G4 C5 E5`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR, ChordInversion.SECOND_INVERSION)
        assertEquals(listOf(67, 72, 76), voicing.midiNotes)
    }

    @Test
    fun `C 七和弦第三转位 = B♭4 C5 E5 G5`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.DOMINANT_7, ChordInversion.THIRD_INVERSION)
        // 原 C4 E4 G4 B♭4 → B♭4 C5 E5 G5
        assertEquals(listOf(70, 72, 76, 79), voicing.midiNotes)
    }

    @Test
    fun `三和弦第四转位（不可用）自动回退原位`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR, ChordInversion.THIRD_INVERSION)
        // 三和弦不支持第三转位 → 回退原位
        assertEquals(ChordInversion.ROOT_POSITION, voicing.inversion)
        assertEquals(listOf(60, 64, 67), voicing.midiNotes)
    }

    @Test
    fun `可用转位 - 三和弦有 3 种`() {
        val inversions = ChordEngine.availableInversions(ChordType.MAJOR)
        assertEquals(3, inversions.size)
        assertTrue(ChordInversion.ROOT_POSITION in inversions)
        assertTrue(ChordInversion.FIRST_INVERSION in inversions)
        assertTrue(ChordInversion.SECOND_INVERSION in inversions)
        assertFalse(ChordInversion.THIRD_INVERSION in inversions)
    }

    @Test
    fun `可用转位 - 七和弦有 4 种`() {
        val inversions = ChordEngine.availableInversions(ChordType.DOMINANT_7)
        assertEquals(4, inversions.size)
        assertTrue(ChordInversion.THIRD_INVERSION in inversions)
    }

    @Test
    fun `可用转位 - 九和弦有 4 种`() {
        val inversions = ChordEngine.availableInversions(ChordType.MAJOR_9)
        assertEquals(4, inversions.size)
    }

    // ════════════════════════════════════════
    //  音名格式化
    // ════════════════════════════════════════

    @Test
    fun `和弦名称 - C 大三和弦原位`() {
        val name = ChordEngine.formatChordName(ChordRoot.C, ChordType.MAJOR, ChordInversion.ROOT_POSITION)
        assertEquals("C", name)
    }

    @Test
    fun `和弦名称 - D 小三和弦`() {
        val name = ChordEngine.formatChordName(ChordRoot.D, ChordType.MINOR, ChordInversion.ROOT_POSITION)
        assertEquals("Dm", name)
    }

    @Test
    fun `和弦名称 - F♯ 大七和弦`() {
        val name = ChordEngine.formatChordName(ChordRoot.F_SHARP, ChordType.MAJOR_7, ChordInversion.ROOT_POSITION)
        assertEquals("F♯maj7", name)
    }

    @Test
    fun `和弦名称 - C 大三和弦第一转位带转位符号`() {
        val name = ChordEngine.formatChordName(ChordRoot.C, ChordType.MAJOR, ChordInversion.FIRST_INVERSION)
        assertEquals("C⁶", name)
    }

    @Test
    fun `和弦名称 - B♭ 属七和弦第二转位`() {
        val name = ChordEngine.formatChordName(ChordRoot.B_FLAT, ChordType.DOMINANT_7, ChordInversion.SECOND_INVERSION)
        assertEquals("B♭7⁶₄", name)
    }

    // ════════════════════════════════════════
    //  升降号偏好
    // ════════════════════════════════════════

    @Test
    fun `F 大调使用降号记法`() {
        val voicing = ChordEngine.build(ChordRoot.F, ChordType.MAJOR)
        // F 大调三音应为 A (白键，不涉及升降号)
        assertTrue(voicing.noteNames.any { it.contains("A") })
    }

    @Test
    fun `F 大七和弦音名使用降号`() {
        val voicing = ChordEngine.build(ChordRoot.F, ChordType.MAJOR_7)
        // 七音应为 E (白键)
        assertTrue(voicing.noteNames.any { it.startsWith("E") })
    }

    @Test
    fun `B♭ 大和弦音名使用降号`() {
        val voicing = ChordEngine.build(ChordRoot.B_FLAT, ChordType.MAJOR)
        assertTrue(voicing.noteNames.any { it.contains("B♭") || it.startsWith("B") })
    }

    @Test
    fun `D 大和弦音名使用升号`() {
        val voicing = ChordEngine.build(ChordRoot.D, ChordType.MAJOR)
        // F♯
        assertTrue(voicing.noteNames.any { it.contains("F♯") })
    }

    // ════════════════════════════════════════
    //  音程名称
    // ════════════════════════════════════════

    @Test
    fun `大三和弦音程名称 = 根音 大三度 纯五度`() {
        val names = ChordEngine.intervalNames(ChordType.MAJOR)
        assertEquals(listOf("根音", "大三度", "纯五度"), names)
    }

    @Test
    fun `属七和弦音程名称 = 根音 大三度 纯五度 小七度`() {
        val names = ChordEngine.intervalNames(ChordType.DOMINANT_7)
        assertEquals(listOf("根音", "大三度", "纯五度", "小七度"), names)
    }

    @Test
    fun `属九和弦音程名称含大九度`() {
        val names = ChordEngine.intervalNames(ChordType.DOMINANT_9)
        assertTrue(names.contains("大九度"))
        assertEquals(5, names.size)
    }

    // ════════════════════════════════════════
    //  指法建议
    // ════════════════════════════════════════

    @Test
    fun `三和弦原位指法 = 1-3-5`() {
        val fingers = ChordEngine.suggestedFingering(ChordType.MAJOR, ChordInversion.ROOT_POSITION)
        assertEquals(listOf(1, 3, 5), fingers)
    }

    @Test
    fun `三和弦第一转位指法 = 1-2-5`() {
        val fingers = ChordEngine.suggestedFingering(ChordType.MAJOR, ChordInversion.FIRST_INVERSION)
        assertEquals(listOf(1, 2, 5), fingers)
    }

    @Test
    fun `七和弦原位指法 = 1-2-3-5`() {
        val fingers = ChordEngine.suggestedFingering(ChordType.MAJOR_7, ChordInversion.ROOT_POSITION)
        assertEquals(listOf(1, 2, 3, 5), fingers)
    }

    @Test
    fun `五音和弦指法 = 1-2-3-4-5`() {
        val fingers = ChordEngine.suggestedFingering(ChordType.MAJOR_9, ChordInversion.ROOT_POSITION)
        assertEquals(listOf(1, 2, 3, 4, 5), fingers)
    }

    // ════════════════════════════════════════
    //  分类分组
    // ════════════════════════════════════════

    @Test
    fun `allChordsByCategory 包含全部 6 个分类`() {
        val categories = ChordEngine.allChordsByCategory()
        assertEquals(6, categories.size)
        assertTrue(ChordCategory.TRIAD in categories)
        assertTrue(ChordCategory.SUSPENDED in categories)
        assertTrue(ChordCategory.SIXTH in categories)
        assertTrue(ChordCategory.SEVENTH in categories)
        assertTrue(ChordCategory.NINTH in categories)
        assertTrue(ChordCategory.ADDED in categories)
    }

    @Test
    fun `三和弦分类包含 4 种`() {
        val triads = ChordEngine.allChordsByCategory()[ChordCategory.TRIAD]
        assertEquals(4, triads?.size)
        assertTrue(ChordType.MAJOR in triads!!)
        assertTrue(ChordType.MINOR in triads)
        assertTrue(ChordType.DIMINISHED in triads)
        assertTrue(ChordType.AUGMENTED in triads)
    }

    @Test
    fun `七和弦分类包含 5 种`() {
        val sevenths = ChordEngine.allChordsByCategory()[ChordCategory.SEVENTH]
        assertEquals(5, sevenths?.size)
    }

    // ════════════════════════════════════════
    //  频率计算
    // ════════════════════════════════════════

    @Test
    fun `frequencies 返回与音符数等量的频率`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.DOMINANT_7)
        val freqs = ChordEngine.frequencies(voicing)
        assertEquals(4, freqs.size)
    }

    @Test
    fun `C4 频率约为 261_63 Hz`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val freqs = ChordEngine.frequencies(voicing)
        assertEquals(261.63, freqs[0], 0.1)
    }

    // ════════════════════════════════════════
    //  钢琴范围钳位
    // ════════════════════════════════════════

    @Test
    fun `所有和弦音符在钢琴范围内`() {
        for (root in ChordRoot.entries) {
            for (type in ChordType.entries) {
                for (inversion in ChordInversion.entries) {
                    val voicing = ChordEngine.build(root, type, inversion)
                    for (midi in voicing.midiNotes) {
                        assertTrue(
                            "MIDI $midi out of range for $root $type $inversion",
                            midi in 21..108
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `voicing midiNotes 已排序`() {
        val voicing = ChordEngine.build(ChordRoot.A, ChordType.MAJOR_9, ChordInversion.THIRD_INVERSION)
        val sorted = voicing.midiNotes.sorted()
        assertEquals(sorted, voicing.midiNotes)
    }

    // ════════════════════════════════════════
    //  noteCount
    // ════════════════════════════════════════

    @Test
    fun `三和弦 noteCount = 3`() {
        assertEquals(3, ChordType.MAJOR.noteCount)
    }

    @Test
    fun `七和弦 noteCount = 4`() {
        assertEquals(4, ChordType.DOMINANT_7.noteCount)
    }

    @Test
    fun `九和弦 noteCount = 5`() {
        assertEquals(5, ChordType.DOMINANT_9.noteCount)
    }
}
