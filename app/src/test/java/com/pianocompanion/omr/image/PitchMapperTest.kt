package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Accidental
import com.pianocompanion.data.model.Staff
import org.junit.Assert.assertEquals
import org.junit.Test

class PitchMapperTest {

    @Test
    fun `treble bottom line maps to E4`() {
        assertEquals(64, PitchMapper.staffPositionToMidi(0, Staff.TREBLE))
    }

    @Test
    fun `treble staff positions ascend chromatically correct`() {
        // step 0..8 from the bottom line: E4 F4 G4 A4 B4 C5 D5 E5 F5
        val expected = listOf(64, 65, 67, 69, 71, 72, 74, 76, 77)
        expected.forEachIndexed { step, midi ->
            assertEquals("step $step", midi, PitchMapper.staffPositionToMidi(step, Staff.TREBLE))
        }
    }

    @Test
    fun `treble negative steps go below the staff to middle C`() {
        // E4(64) -> D4(62) -> C4(60)
        assertEquals(62, PitchMapper.staffPositionToMidi(-1, Staff.TREBLE))
        assertEquals(60, PitchMapper.staffPositionToMidi(-2, Staff.TREBLE))
    }

    @Test
    fun `bass bottom line maps to G2`() {
        assertEquals(43, PitchMapper.staffPositionToMidi(0, Staff.BASS))
    }

    @Test
    fun `bass staff positions ascend chromatically correct`() {
        // G2 A2 B2 C3 D3 from the bass bottom line
        val expected = listOf(43, 45, 47, 48, 50)
        expected.forEachIndexed { step, midi ->
            assertEquals("step $step", midi, PitchMapper.staffPositionToMidi(step, Staff.BASS))
        }
    }

    @Test
    fun `note name reflects the diatonic step`() {
        assertEquals("C4", PitchMapper.staffPositionToNoteName(-2, Staff.TREBLE))
        assertEquals("A4", PitchMapper.staffPositionToNoteName(3, Staff.TREBLE))
    }

    // ---- C 谱号 (中音 / 次中音) ------------------------------------------

    @Test
    fun `alto bottom line maps to F3`() {
        // 中音谱表底线 = F3 = MIDI 53
        assertEquals(53, PitchMapper.staffPositionToMidi(0, Staff.ALTO))
    }

    @Test
    fun `alto middle line maps to middle C`() {
        // step 4 = 中央线 = C4 = 60
        assertEquals(60, PitchMapper.staffPositionToMidi(4, Staff.ALTO))
    }

    @Test
    fun `alto staff positions ascend chromatically correct`() {
        // F3 G3 A3 B3 C4 D4 E4 F4 G4
        val expected = listOf(53, 55, 57, 59, 60, 62, 64, 65, 67)
        expected.forEachIndexed { step, midi ->
            assertEquals("step $step", midi, PitchMapper.staffPositionToMidi(step, Staff.ALTO))
        }
    }

    @Test
    fun `tenor bottom line maps to D3`() {
        // 次中音谱表底线 = D3 = MIDI 50
        assertEquals(50, PitchMapper.staffPositionToMidi(0, Staff.TENOR))
    }

    @Test
    fun `tenor second line from top maps to middle C`() {
        // step 6 = 自下而上第 4 线 = 自上而下第 2 线 = C4 = 60
        assertEquals(60, PitchMapper.staffPositionToMidi(6, Staff.TENOR))
    }

    @Test
    fun `tenor staff positions ascend chromatically correct`() {
        // D3 E3 F3 G3 A3 B3 C4 D4 E4
        val expected = listOf(50, 52, 53, 55, 57, 59, 60, 62, 64)
        expected.forEachIndexed { step, midi ->
            assertEquals("step $step", midi, PitchMapper.staffPositionToMidi(step, Staff.TENOR))
        }
    }

    // ========================================================================
    //  letterForPosition — 临时记号小节内延续所需的音名字母索引
    // ========================================================================

    /** 构建已知几何的谱表系统：底线 Y = bottomY，间距 = spacing，5 条线。 */
    private fun makeSystem(bottomY: Int, spacing: Int): StaffSystem {
        val lines = (0..4).map { i ->
            val y = bottomY - i * spacing
            StaffLine(y, y, 1.0)
        }.reversed() // 自上而下排序
        return StaffSystem(lines)
    }

    @Test
    fun `letterForPosition treble bottom line returns E`() {
        // 高音谱号底线 = E4 → 字母索引 2（C=0, D=1, E=2）
        val sys = makeSystem(bottomY = 90, spacing = 10)
        val letter = PitchMapper.letterForPosition(90, sys, Staff.TREBLE)
        assertEquals(2, letter) // E
    }

    @Test
    fun `letterForPosition treble middle line returns B`() {
        // 高音谱号中线 = B4 → 字母索引 6
        val sys = makeSystem(bottomY = 90, spacing = 10)
        // 中线 Y = 70 (底线 90 - 2*10 = 70)
        val letter = PitchMapper.letterForPosition(70, sys, Staff.TREBLE)
        assertEquals(6, letter) // B
    }

    @Test
    fun `letterForPosition bass bottom line returns G`() {
        // 低音谱号底线 = G2 → 字母索引 4
        val sys = makeSystem(bottomY = 90, spacing = 10)
        val letter = PitchMapper.letterForPosition(90, sys, Staff.BASS)
        assertEquals(4, letter) // G
    }

    // ========================================================================
    //  effectiveOffset — 临时记号的有效半音修正
    // ========================================================================

    @Test
    fun `effectiveOffset 无临时记号时回退到调号`() {
        // G 大调：字母 F(3) → 调号升 F → +1
        val offset = PitchMapper.effectiveOffset(3, KeySignature.G_MAJOR_E_MINOR, null, null)
        assertEquals(1, offset)
    }

    @Test
    fun `effectiveOffset 显式升号覆盖调号`() {
        // C 大调（无升降），显式升号 → +1
        val offset = PitchMapper.effectiveOffset(3, KeySignature.C_MAJOR_A_MINOR, Accidental.SHARP, null)
        assertEquals(1, offset)
    }

    @Test
    fun `effectiveOffset 显式降号`() {
        val offset = PitchMapper.effectiveOffset(0, KeySignature.C_MAJOR_A_MINOR, Accidental.FLAT, null)
        assertEquals(-1, offset)
    }

    @Test
    fun `effectiveOffset 还原号取消调号升降`() {
        // G 大调：字母 F 本应 +1（调号升 F），但还原号取消 → 0
        val offset = PitchMapper.effectiveOffset(3, KeySignature.G_MAJOR_E_MINOR, Accidental.NATURAL, null)
        assertEquals(0, offset)
    }

    @Test
    fun `effectiveOffset 小节内延续优先于调号`() {
        // G 大调：字母 F 本应 +1（调号），但小节内延续为还原号 → 0
        val offset = PitchMapper.effectiveOffset(3, KeySignature.G_MAJOR_E_MINOR, null, Accidental.NATURAL)
        assertEquals(0, offset)
    }

    @Test
    fun `effectiveOffset 显式临时记号优先于小节内延续`() {
        // 延续为升号（+1），但显式为降号 → -1
        val offset = PitchMapper.effectiveOffset(0, KeySignature.C_MAJOR_A_MINOR, Accidental.FLAT, Accidental.SHARP)
        assertEquals(-1, offset)
    }

    @Test
    fun `effectiveOffset 无调号无临时记号返回零`() {
        val offset = PitchMapper.effectiveOffset(3, null, null, null)
        assertEquals(0, offset)
    }

    @Test
    fun `effectiveOffset 重升号返回加二`() {
        val offset = PitchMapper.effectiveOffset(3, KeySignature.C_MAJOR_A_MINOR, Accidental.DOUBLE_SHARP, null)
        assertEquals(2, offset)
    }
}
