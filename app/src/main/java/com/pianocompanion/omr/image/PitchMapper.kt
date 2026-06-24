package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Accidental
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

    /**
     * 计算符头在指定谱表上的音名字母索引（C=0, D=1, E=2, F=3, G=4, A=5, B=6）。
     * 用于临时记号的小节内延续（同一字母的后续音符继承显式临时记号）。
     */
    fun letterForPosition(noteheadY: Int, system: StaffSystem, staff: Staff): Int {
        val spacing = system.lineSpacing
        if (spacing <= 0) return 0
        val stepPx = spacing / 2.0
        val stepsFromBottom = ((system.bottomLine.center - noteheadY) / stepPx).roundToInt()
        val gdc = bottomLineGdc(staff) + stepsFromBottom
        return Math.floorMod(gdc, 7)
    }

    /**
     * 计算临时记号的有效半音修正。
     *
     * 优先级：显式临时记号 > 小节内延续（carried）> 调号（key signature）。
     * - 显式 SHARP → +1, FLAT → -1, NATURAL → 0, DOUBLE_SHARP → +2, DOUBLE_FLAT → -2
     * - 延续（NATURAL 在小节内取消调号升降）
     * - 无显式/延续时回退到调号的 [KeySignature.accidentalOffset]
     */
    fun effectiveOffset(
        letter: Int,
        key: KeySignature?,
        explicitAccidental: Accidental?,
        carriedAccidental: Accidental?
    ): Int {
        val acc = explicitAccidental ?: carriedAccidental
            ?: return key?.accidentalOffset(letter) ?: 0
        return if (acc == Accidental.NONE) {
            key?.accidentalOffset(letter) ?: 0
        } else {
            acc.semitoneOffset
        }
    }
}
