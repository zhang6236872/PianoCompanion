package com.pianocompanion.omr.image

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
}
