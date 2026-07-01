package com.pianocompanion.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ScaleEngine 单元测试。
 *
 * 覆盖音阶构建（上行/下行 MIDI 音符验证）、名称格式化、
 * 升降号偏好、关系调、级数名称、指法建议、音程步进等。
 */
class ScaleEngineTest {

    // ════════════════════════════════════════
    //  音阶 MIDI 音符构建
    // ════════════════════════════════════════

    @Test
    fun `C 自然大调上行序列正确`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        // C4=60, D=62, E=64, F=65, G=67, A=69, B=71, C5=72
        assertEquals(
            listOf(60, 62, 64, 65, 67, 69, 71, 72),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `C 自然大调下行序列正确`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        assertEquals(
            listOf(72, 71, 69, 67, 65, 64, 62, 60),
            scale.descendingMidiNotes
        )
    }

    @Test
    fun `A 自然小调上行序列正确`() {
        // A minor = A, B, C, D, E, F, G, A
        // A3=57 (root octave=4, pitchClass=9, base=60 → 60+9=69? No...)
        // rootMidi: base = (4+1)*12 = 60, C=60, A=60+9=69? That's A4
        // Wait, pitchClass of A is 9, so rootMidi = 60 + 9 = 69 (A4)
        val scale = ScaleEngine.build(ScaleRoot.A, ScaleType.NATURAL_MINOR)
        // A4=69, B4=71, C5=72, D5=74, E5=76, F5=77, G5=79, A5=81
        assertEquals(
            listOf(69, 71, 72, 74, 76, 77, 79, 81),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `G 自然大调上行序列正确`() {
        // G major = G, A, B, C, D, E, F#, G
        // G4=67
        val scale = ScaleEngine.build(ScaleRoot.G, ScaleType.MAJOR)
        assertEquals(
            listOf(67, 69, 71, 72, 74, 76, 78, 79),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `F 自然大调使用降号记法`() {
        // F major = F, G, A, Bb, C, D, E, F
        val scale = ScaleEngine.build(ScaleRoot.F, ScaleType.MAJOR)
        // B should be Bb (flat)
        assertTrue("B♭应该在音名列表中", scale.noteNames.any { it.contains("♭") })
    }

    @Test
    fun `C 和声小调上行序列包含增二度`() {
        // C harmonic minor = C, D, Eb, F, G, Ab, B, C
        // Intervals: 2, 3, 5, 7, 8, 11
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.HARMONIC_MINOR)
        assertEquals(
            listOf(60, 62, 63, 65, 67, 68, 71, 72),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `旋律小调上行不同于下行`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MELODIC_MINOR)
        // 上行: C, D, Eb, F, G, A, B, C (intervals 2,3,5,7,9,11)
        assertEquals(
            listOf(60, 62, 63, 65, 67, 69, 71, 72),
            scale.ascendingMidiNotes
        )
        // 下行 = 自然小调: C, D, Eb, F, G, Ab, Bb, C (intervals 2,3,5,7,8,10)
        // 下行序列从高到低: C5=72, B4=71... wait, descending intervals are 2,3,5,7,8,10
        // descendingNotes starts at octave (72), then intervals in reverse: 10, 8, 7, 5, 3, 2, 0
        // 72, 60+10=70, 60+8=68, 60+7=67, 60+5=65, 60+3=63, 60+2=62, 60+0=60
        assertEquals(
            listOf(72, 70, 68, 67, 65, 63, 62, 60),
            scale.descendingMidiNotes
        )
    }

    @Test
    fun `旋律小调上行下行序列不同`() {
        val scale = ScaleEngine.build(ScaleRoot.A, ScaleType.MELODIC_MINOR)
        assertNotEqualsList(scale.ascendingMidiNotes, scale.descendingMidiNotes.reversed())
    }

    @Test
    fun `D 多利亚调式上行序列正确`() {
        // D Dorian = D, E, F, G, A, B, C, D (intervals 2,3,5,7,9,10)
        val scale = ScaleEngine.build(ScaleRoot.D, ScaleType.DORIAN)
        assertEquals(
            listOf(62, 64, 65, 67, 69, 71, 72, 74),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `E 弗利吉亚调式上行序列正确`() {
        // E Phrygian = E, F, G, A, B, C, D, E (intervals 1,3,5,7,8,10)
        val scale = ScaleEngine.build(ScaleRoot.E, ScaleType.PHRYGIAN)
        assertEquals(
            listOf(64, 65, 67, 69, 71, 72, 74, 76),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `F 利底亚调式上行序列正确`() {
        // F Lydian = F, G, A, B, C, D, E, F (intervals 2,4,6,7,9,11)
        // 利底亚的特征是增四度（#4），在 F 大调中 Bb 变为 B♮
        val scale = ScaleEngine.build(ScaleRoot.F, ScaleType.LYDIAN)
        assertEquals(
            listOf(65, 67, 69, 71, 72, 74, 76, 77),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `G 混合利底亚调式上行序列正确`() {
        // G Mixolydian = G, A, B, C, D, E, F, G (intervals 2,4,5,7,9,10)
        val scale = ScaleEngine.build(ScaleRoot.G, ScaleType.MIXOLYDIAN)
        assertEquals(
            listOf(67, 69, 71, 72, 74, 76, 77, 79),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `C 大调五声音阶上行序列正确`() {
        // C major pentatonic = C, D, E, G, A, C (intervals 2,4,7,9)
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR_PENTATONIC)
        assertEquals(
            listOf(60, 62, 64, 67, 69, 72),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `A 小调五声音阶上行序列正确`() {
        // A minor pentatonic = A, C, D, E, G, A (intervals 3,5,7,10)
        val scale = ScaleEngine.build(ScaleRoot.A, ScaleType.MINOR_PENTATONIC)
        assertEquals(
            listOf(69, 72, 74, 76, 79, 81),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `A 蓝调音阶上行序列正确`() {
        // A blues = A, C, D, Eb, E, G, A (intervals 3,5,6,7,10)
        val scale = ScaleEngine.build(ScaleRoot.A, ScaleType.BLUES)
        assertEquals(
            listOf(69, 72, 74, 75, 76, 79, 81),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `C 半音阶上行序列正确`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.CHROMATIC)
        assertEquals(
            listOf(60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `C 全音阶上行序列正确`() {
        // C whole tone = C, D, E, F#, G#, A#, C (intervals 2,4,6,8,10)
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.WHOLE_TONE)
        assertEquals(
            listOf(60, 62, 64, 66, 68, 70, 72),
            scale.ascendingMidiNotes
        )
    }

    @Test
    fun `上行和下行长度相等`() {
        for (type in ScaleType.entries) {
            val scale = ScaleEngine.build(ScaleRoot.C, type)
            assertEquals(
                "音阶 ${type.displayName} 上下行长度应相等",
                scale.ascendingMidiNotes.size,
                scale.descendingMidiNotes.size
            )
        }
    }

    @Test
    fun `上行序列首尾相差一个八度`() {
        for (type in ScaleType.entries) {
            val scale = ScaleEngine.build(ScaleRoot.C, type)
            val diff = scale.ascendingMidiNotes.last() - scale.ascendingMidiNotes.first()
            assertEquals(
                "音阶 ${type.displayName} 上下行相差应为12半音",
                12, diff
            )
        }
    }

    // ════════════════════════════════════════
    //  名称格式化
    // ════════════════════════════════════════

    @Test
    fun `格式化大调名称正确`() {
        val name = ScaleEngine.formatScaleName(ScaleRoot.C, ScaleType.MAJOR)
        assertEquals("C自然大调", name)
    }

    @Test
    fun `格式化小调名称正确`() {
        val name = ScaleEngine.formatScaleName(ScaleRoot.A, ScaleType.NATURAL_MINOR)
        assertEquals("A自然小调", name)
    }

    @Test
    fun `格式化降号调名称正确`() {
        val name = ScaleEngine.formatScaleName(ScaleRoot.B_FLAT, ScaleType.MAJOR)
        assertEquals("B♭自然大调", name)
    }

    @Test
    fun `格式化调式名称正确`() {
        val name = ScaleEngine.formatScaleName(ScaleRoot.D, ScaleType.DORIAN)
        assertEquals("D多利亚调式", name)
    }

    // ════════════════════════════════════════
    //  升降号偏好
    // ════════════════════════════════════════

    @Test
    fun `F调使用降号`() {
        assertTrue(ScaleEngine.preferFlatsKey(ScaleRoot.F))
    }

    @Test
    fun `B♭调使用降号`() {
        assertTrue(ScaleEngine.preferFlatsKey(ScaleRoot.B_FLAT))
    }

    @Test
    fun `C调不使用降号`() {
        assertFalse(ScaleEngine.preferFlatsKey(ScaleRoot.C))
    }

    @Test
    fun `G调不使用降号`() {
        assertFalse(ScaleEngine.preferFlatsKey(ScaleRoot.G))
    }

    @Test
    fun `E♭调使用降号`() {
        assertTrue(ScaleEngine.preferFlatsKey(ScaleRoot.E_FLAT))
    }

    // ════════════════════════════════════════
    //  关系调
    // ════════════════════════════════════════

    @Test
    fun `C大调的关系小调是A小调`() {
        assertEquals(ScaleRoot.A, ScaleEngine.relativeMinor(ScaleRoot.C))
    }

    @Test
    fun `G大调的关系小调是E小调`() {
        assertEquals(ScaleRoot.E, ScaleEngine.relativeMinor(ScaleRoot.G))
    }

    @Test
    fun `A小调的关系大调是C大调`() {
        assertEquals(ScaleRoot.C, ScaleEngine.relativeMajor(ScaleRoot.A))
    }

    @Test
    fun `E小调的关系大调是G大调`() {
        assertEquals(ScaleRoot.G, ScaleEngine.relativeMajor(ScaleRoot.E))
    }

    @Test
    fun `F大调的关系小调是D小调`() {
        assertEquals(ScaleRoot.D, ScaleEngine.relativeMinor(ScaleRoot.F))
    }

    @Test
    fun `关系大小调互逆`() {
        for (root in ScaleRoot.entries) {
            val minor = ScaleEngine.relativeMinor(root)
            val backToMajor = ScaleEngine.relativeMajor(minor)
            assertEquals(root.pitchClass, backToMajor.pitchClass)
        }
    }

    // ════════════════════════════════════════
    //  级数名称
    // ════════════════════════════════════════

    @Test
    fun `大调级数名称包含主音和属音`() {
        val names = ScaleEngine.degreeNames(ScaleType.MAJOR)
        assertTrue(names.any { it.contains("主音") })
        assertTrue(names.any { it.contains("属音") })
    }

    @Test
    fun `大调级数名称以主音开始和结束`() {
        val names = ScaleEngine.degreeNames(ScaleType.MAJOR)
        assertTrue(names.first().contains("主音"))
        assertTrue(names.last().contains("主音"))
    }

    @Test
    fun `大调级数名称长度等于音阶音符数加一`() {
        val names = ScaleEngine.degreeNames(ScaleType.MAJOR)
        // noteCount(7) + 1(八度根音) = 8
        assertEquals(8, names.size)
    }

    @Test
    fun `五声音阶级数名称长度正确`() {
        val names = ScaleEngine.degreeNames(ScaleType.MAJOR_PENTATONIC)
        assertEquals(6, names.size) // 5 notes + octave
    }

    // ════════════════════════════════════════
    //  指法建议
    // ════════════════════════════════════════

    @Test
    fun `大调指法右手12312345`() {
        val fingers = ScaleEngine.suggestedFingering(ScaleRoot.C, ScaleType.MAJOR)
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), fingers)
    }

    @Test
    fun `五声音阶指法长度正确`() {
        val fingers = ScaleEngine.suggestedFingering(ScaleRoot.C, ScaleType.MAJOR_PENTATONIC)
        assertEquals(6, fingers.size) // 5 notes + octave
    }

    @Test
    fun `半音阶指法长度正确`() {
        val fingers = ScaleEngine.suggestedFingering(ScaleRoot.C, ScaleType.CHROMATIC)
        assertEquals(13, fingers.size) // 12 notes + octave
    }

    @Test
    fun `指法数字在1到5之间`() {
        for (type in ScaleType.entries) {
            val fingers = ScaleEngine.suggestedFingering(ScaleRoot.C, type)
            for (f in fingers) {
                assertTrue("指法 $f 应在 1-5 范围内", f in 1..5)
            }
        }
    }

    // ════════════════════════════════════════
    //  音程步进
    // ════════════════════════════════════════

    @Test
    fun `大调音程步进是全全半全全全半`() {
        val steps = ScaleEngine.intervalSteps(ScaleType.MAJOR)
        assertEquals(listOf(2, 2, 1, 2, 2, 2, 1), steps)
    }

    @Test
    fun `自然小调音程步进是全半全全半全全`() {
        val steps = ScaleEngine.intervalSteps(ScaleType.NATURAL_MINOR)
        assertEquals(listOf(2, 1, 2, 2, 1, 2, 2), steps)
    }

    @Test
    fun `和声小调包含增二度`() {
        val steps = ScaleEngine.intervalSteps(ScaleType.HARMONIC_MINOR)
        // C harmonic minor: C, D, Eb, F, G, Ab, B, C
        // Steps: 2, 1, 2, 2, 1, 3, 1
        assertTrue(steps.contains(3)) // 增二度 = 3 semitones
    }

    @Test
    fun `全音阶所有步进为2`() {
        val steps = ScaleEngine.intervalSteps(ScaleType.WHOLE_TONE)
        assertTrue(steps.all { it == 2 })
    }

    @Test
    fun `半音阶所有步进为1`() {
        val steps = ScaleEngine.intervalSteps(ScaleType.CHROMATIC)
        assertTrue(steps.all { it == 1 })
    }

    @Test
    fun `音程步进总和为12`() {
        for (type in ScaleType.entries) {
            val steps = ScaleEngine.intervalSteps(type)
            assertEquals(
                "音阶 ${type.displayName} 步进总和应为12",
                12, steps.sum()
            )
        }
    }

    // ════════════════════════════════════════
    //  分类分组
    // ════════════════════════════════════════

    @Test
    fun `分类分组包含所有6类`() {
        val groups = ScaleEngine.allScalesByCategory()
        assertEquals(6, groups.size)
        assertTrue(groups.containsKey(ScaleCategory.MAJOR))
        assertTrue(groups.containsKey(ScaleCategory.MINOR))
        assertTrue(groups.containsKey(ScaleCategory.MODE))
        assertTrue(groups.containsKey(ScaleCategory.PENTATONIC))
        assertTrue(groups.containsKey(ScaleCategory.BLUES))
        assertTrue(groups.containsKey(ScaleCategory.OTHER))
    }

    @Test
    fun `大调分类包含1种`() {
        val groups = ScaleEngine.allScalesByCategory()
        assertEquals(1, groups[ScaleCategory.MAJOR]!!.size)
    }

    @Test
    fun `小调分类包含3种`() {
        val groups = ScaleEngine.allScalesByCategory()
        assertEquals(3, groups[ScaleCategory.MINOR]!!.size)
    }

    @Test
    fun `调式分类包含7种`() {
        val groups = ScaleEngine.allScalesByCategory()
        assertEquals(7, groups[ScaleCategory.MODE]!!.size)
    }

    // ════════════════════════════════════════
    //  频率
    // ════════════════════════════════════════

    @Test
    fun `C大调频率首音约为261_63Hz`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        val freqs = ScaleEngine.frequencies(scale)
        assertEquals(261.63, freqs.first(), 0.5)
    }

    @Test
    fun `频率列表长度等于上行音符数`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MAJOR)
        val freqs = ScaleEngine.frequencies(scale)
        assertEquals(scale.ascendingMidiNotes.size, freqs.size)
    }

    // ════════════════════════════════════════
    //  等价音阶
    // ════════════════════════════════════════

    @Test
    fun `大调和伊奥尼亚调式等价`() {
        assertTrue(ScaleEngine.areEquivalent(ScaleType.MAJOR, ScaleType.IONIAN))
    }

    @Test
    fun `自然小调和爱奥利亚调式等价`() {
        assertTrue(ScaleEngine.areEquivalent(ScaleType.NATURAL_MINOR, ScaleType.AEOLIAN))
    }

    @Test
    fun `大调和自然小调不等价`() {
        assertFalse(ScaleEngine.areEquivalent(ScaleType.MAJOR, ScaleType.NATURAL_MINOR))
    }

    // ════════════════════════════════════════
    //  钢琴范围钳位
    // ════════════════════════════════════════

    @Test
    fun `所有音符在钢琴范围内`() {
        for (root in ScaleRoot.entries) {
            for (type in ScaleType.entries) {
                val scale = ScaleEngine.build(root, type)
                for (note in scale.ascendingMidiNotes) {
                    assertTrue(
                        "MIDI $note 超出钢琴范围 (root=$root, type=$type)",
                        note in 21..108
                    )
                }
                for (note in scale.descendingMidiNotes) {
                    assertTrue(
                        "下行 MIDI $note 超出钢琴范围 (root=$root, type=$type)",
                        note in 21..108
                    )
                }
            }
        }
    }

    @Test
    fun `noteCount 属性正确`() {
        assertEquals(7, ScaleType.MAJOR.noteCount)
        assertEquals(7, ScaleType.NATURAL_MINOR.noteCount)
        assertEquals(5, ScaleType.MAJOR_PENTATONIC.noteCount)
        assertEquals(6, ScaleType.BLUES.noteCount)
        assertEquals(12, ScaleType.CHROMATIC.noteCount)
        assertEquals(6, ScaleType.WHOLE_TONE.noteCount)
    }

    // ════════════════════════════════════════
    //  旋律小调下行特殊处理
    // ════════════════════════════════════════

    @Test
    fun `旋律小调下行使用自然小调音程`() {
        val scale = ScaleEngine.build(ScaleRoot.C, ScaleType.MELODIC_MINOR)
        // 下行应为自然小调: C, D, Eb, F, G, Ab, Bb, C
        // descendingMidiNotes = [72, 70, 68, 67, 65, 63, 62, 60]
        // 72=C5, 70=Bb4, 68=Ab4, 67=G4, 65=F4, 63=Eb4, 62=D4, 60=C4
        assertEquals(72, scale.descendingMidiNotes[0])
        assertEquals(70, scale.descendingMidiNotes[1]) // Bb not B
        assertEquals(68, scale.descendingMidiNotes[2]) // Ab not A
    }

    @Test
    fun `非旋律小调上行下行相同`() {
        assertFalse(ScaleType.MAJOR.hasDifferentDescending)
        assertFalse(ScaleType.NATURAL_MINOR.hasDifferentDescending)
        assertFalse(ScaleType.HARMONIC_MINOR.hasDifferentDescending)
        assertFalse(ScaleType.DORIAN.hasDifferentDescending)
    }

    @Test
    fun `只有旋律小调上行下行不同`() {
        assertTrue(ScaleType.MELODIC_MINOR.hasDifferentDescending)
        var count = 0
        for (type in ScaleType.entries) {
            if (type.hasDifferentDescending) count++
        }
        assertEquals(1, count)
    }

    // ════════════════════════════════════════
    //  辅助方法
    // ════════════════════════════════════════

    private fun assertNotEqualsList(a: List<Int>, b: List<Int>) {
        assertFalse("列表不应相等: $a vs $b", a == b)
    }
}
