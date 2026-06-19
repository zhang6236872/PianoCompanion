package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils
import kotlin.math.roundToInt

/**
 * Maps a notehead's vertical pixel position onto a MIDI pitch using staff geometry.
 *
 * Staff position model: one diatonic step (line↔adjacent space) spans half a
 * staff-line spacing in pixels. Counting steps up from a clef's bottom line and
 * converting through a global diatonic index yields the correct MIDI number,
 * correctly handling the non-uniform E–F and B–C semitone gaps.
 */
object PitchMapper {

    // White-key letters in C order with their semitone offset within an octave.
    private val C_ORDER_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11) // C D E F G A B

    // Global Diatonic Count (GDC) of each clef's bottom line, anchored at C4 = MIDI 60.
    // Treble bottom line = E4 → (4-4)*7 + 2 = 2.
    // Bass   bottom line = G2 → (2-4)*7 + 4 = -10.
    // Alto   bottom line = F3 → (3-4)*7 + 3 = -4.  (middle line = C4)
    // Tenor  bottom line = D3 → (3-4)*7 + 1 = -6.  (2nd line from top = C4)
    private fun bottomLineGdc(staff: Staff): Int = when (staff) {
        Staff.BASS -> -10
        Staff.ALTO -> -4
        Staff.TENOR -> -6
        else -> 2 // TREBLE / BOTH
    }

    /**
     * @param noteheadY vertical center of the notehead (image coords, y grows downward).
     * @param system the staff system geometry the notehead belongs to.
     * @param staff clef interpretation for this system.
     * @return MIDI note number, or -1 if geometry is invalid.
     */
    fun mapToMidi(noteheadY: Int, system: StaffSystem, staff: Staff): Int {
        val spacing = system.lineSpacing
        if (spacing <= 0) return -1
        val stepPx = spacing / 2.0
        val stepsFromBottom = ((system.bottomLine.center - noteheadY) / stepPx).roundToInt()
        return staffPositionToMidi(stepsFromBottom, staff)
    }

    /**
     * 调号感知版本：先按谱表位置算出"白键"音高，再叠加调号带来的升/降半音修正。
     *
     * @param key 谱号右侧识别到的调号；null 等同于 C 大调（无升降）。
     */
    fun mapToMidi(
        noteheadY: Int,
        system: StaffSystem,
        staff: Staff,
        key: KeySignature?
    ): Int {
        val base = mapToMidi(noteheadY, system, staff)
        if (key == null || key.accidentalCount == 0) return base
        val spacing = system.lineSpacing
        if (spacing <= 0) return base
        val stepPx = spacing / 2.0
        val stepsFromBottom = ((system.bottomLine.center - noteheadY) / stepPx).roundToInt()
        val gdc = bottomLineGdc(staff) + stepsFromBottom
        val letter = Math.floorMod(gdc, 7) // C=0..B=6
        return base + key.accidentalOffset(letter)
    }

    /**
     * Convert a diatonic staff-step index (0 = bottom line, +1 per line/space upward)
     * to a MIDI note number.
     */
    fun staffPositionToMidi(stepIndex: Int, staff: Staff): Int {
        val gdc = bottomLineGdc(staff) + stepIndex
        val octaves = Math.floorDiv(gdc, 7)
        val within = Math.floorMod(gdc, 7)
        return 60 + octaves * 12 + C_ORDER_SEMITONES[within]
    }

    /** Convenience: staff-step index → human-readable note name. */
    fun staffPositionToNoteName(stepIndex: Int, staff: Staff): String =
        MusicUtils.midiToNoteName(staffPositionToMidi(stepIndex, staff))
}
