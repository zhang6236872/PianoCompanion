package com.pianocompanion.data.model

import com.pianocompanion.util.MusicUtils

/**
 * Represents a single musical note with pitch, timing, and duration.
 *
 * @param midiNumber MIDI note number (0-127). Middle C (C4) = 60.
 * @param noteName Human-readable name e.g. "C4", "F#5"
 * @param startTime Onset time in milliseconds from score start.
 * @param duration Note duration in milliseconds.
 * @param velocity MIDI velocity (0-127), used for dynamics.
 * @param staff Which staff (treble/bass) for piano.
 * @param tuplet 连音组类型（0=非连音, 3=三连音, 2=二连音, 5=五连音等）。
 * @param octaveShift 八度移位量（0=无移位, +12=8va, -12=8vb, +24=15ma, -24=15mb）。
 *   记录该音符因八度记号(ottava)而应用的半音移位，用于 UI 标注和调试。
 *   midiNumber 已包含此移位，此字段仅用于信息追溯。
 */
data class ScoreNote(
    val midiNumber: Int,
    val noteName: String,
    val startTime: Long,
    val duration: Long,
    val velocity: Int = 64,
    val staff: Staff = Staff.TREBLE,
    val measureIndex: Int = 0,
    val isGraceNote: Boolean = false,
    val articulation: Articulation = Articulation.NONE,
    val tuplet: Int = 0,
    val octaveShift: Int = 0
) {
    val endTime: Long get() = startTime + duration
    val frequency: Double get() = MusicUtils.midiToFrequency(midiNumber)
}

/**
 * 演奏法标记（articulation），影响音符的演奏方式。
 *
 * - [NONE] 无标记
 * - [STACCATO] 断奏（•）：音符应短促、断开演奏
 * - [TENUTO] 保持音（—）：音符应充分保持其时值，平稳演奏
 * - [ACCENT] 重音（>）：音符应加以强调、重击
 * - [STACCATISSIMO] 短断奏（▼/▔）：极短促、尖锐断开，比断奏更短
 * - [MARCATO] 强音（^）：强烈的强调，比重音更用力
 */
enum class Articulation { NONE, STACCATO, TENUTO, ACCENT, STACCATISSIMO, MARCATO }

data class DetectedNote(
    val midiNumber: Int,
    val frequency: Double,
    val startTime: Long,
    val duration: Long = 0,
    val confidence: Float = 0f
) {
    val noteName: String get() = MusicUtils.midiToNoteName(midiNumber)
    val endTime: Long get() = startTime + duration
}

/**
 * Which staff / clef a note belongs to.
 *
 * - [TREBLE] 高音谱号 (G clef) — 钢琴右手
 * - [BASS] 低音谱号 (F clef) — 钢琴左手
 * - [ALTO] 中音谱号 (C clef, 中央线) — 中提琴等单声部乐器
 * - [TENOR] 次中音谱号 (C clef, 自上而下第 2 线) — 大管/大提琴高音区
 * - [BOTH] 大谱表双手共用
 */
enum class Staff { TREBLE, BASS, ALTO, TENOR, BOTH }

/**
 * Represents a complete musical score.
 */
data class Score(
    val id: String,
    val title: String,
    val composer: String,
    val notes: List<ScoreNote>,
    val tempo: Int = 120, // BPM
    val timeSignature: String = "4/4",
    val source: ScoreSource = ScoreSource.MUSIC_XML
)

enum class ScoreSource { MUSIC_XML, MIDI, OMR }

/**
 * Result of comparing a detected note against the expected score.
 */
data class MatchResult(
    val expectedNote: ScoreNote?,
    val detectedNote: DetectedNote?,
    val status: MatchStatus,
    val deviationMs: Long = 0
)

enum class MatchStatus {
    CORRECT,       // Note matches
    WRONG_PITCH,   // Played a different note
    EXTRA_NOTE,    // Played something not in score
    MISSING_NOTE,  // Expected note not played
    RHYTHM_ERROR   // Right pitch, wrong timing
}
