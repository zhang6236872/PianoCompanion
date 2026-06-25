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
 * @param accidental 临时记号（升降号），记录该音符因前方临时记号而应用的半音修正类型。
 *   NONE 表示未检测到临时记号（沿用调号）。
 *   midiNumber 已包含此修正，此字段仅用于信息追溯和 UI 标注（显示 ♯/♭/♮）。
 * @param fingering 指法编号（0=未标注, 1=拇指, 2=食指, 3=中指, 4=无名指, 5=小指）。
 *   来自 OMR 指法数字检测，用于 UI 标注和辅助学习。不影响音高或时值。
 * @param isArpeggiated 是否为琶音和弦成员。
 *   来自 OMR 琶音检测——和弦左方的垂直波浪线指示滚奏。琶音和弦中的音符
 *   从下到上依次快速弹奏，而非同时。startTime 已包含序列延迟（每个音符
 *   比前一个晚 ARPEGGIO_DELAY_MS 毫秒），此字段用于 UI 标注和 score-follower 特殊处理。
 * @param tremoloSlashCount 震音(tremolo)斜线数量（0=无震音, 2=八分震音, 3=三十二分震音）。
 *   来自 OMR 震音检测——符干上的 2~3 条短斜线指示将音符快速反复弹奏。
 *   震音音符在演奏时会产生大量快速重复 onset，score-follower 需据此进入宽松匹配。
 *   此字段不影响音高或基础时值，仅用于演奏提示和 score-follower 特殊处理。
 * @param isGlissando 是否为滑音(glissando)的端点音符。
 *   来自 OMR 滑音检测——两音符间的斜向线指示从一个音快速滑动到另一个音。
 *   滑音在演奏时会产生大量连续快速 onset（手指滑过每个琴键），而乐谱只标记
 *   起点和终点。score-follower 需据此进入宽松匹配。此字段不影响音高或基础时值，
 *   仅用于演奏提示和 score-follower 特殊处理。
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
    val octaveShift: Int = 0,
    val accidental: Accidental = Accidental.NONE,
    val fingering: Int = 0,
    val isArpeggiated: Boolean = false,
    val tremoloSlashCount: Int = 0,
    val isGlissando: Boolean = false
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

/**
 * 临时记号（accidental），表示写在音符前方的升降号。
 *
 * - [NONE] 无临时记号（沿用调号 key signature）
 * - [SHARP] 升号（♯）：升高半音
 * - [FLAT] 降号（♭）：降低半音
 * - [NATURAL] 还原号（♮）：取消调号中的升/降，回到白键
 * - [DOUBLE_SHARP] 重升号（×）：升高全音（极少见）
 * - [DOUBLE_FLAT] 重降号（♭♭）：降低全音（极少见）
 *
 * 临时记号在一小节内对同一音名的后续音符持续有效，直到小节结束或被新的
 * 临时记号覆盖。OMR 管线先检测每个符头前方的显式临时记号，再在此基础
 * 上实现小节内延续（measure carryover）。
 */
enum class Accidental(val semitoneOffset: Int) {
    NONE(0), SHARP(1), FLAT(-1), NATURAL(0), DOUBLE_SHARP(2), DOUBLE_FLAT(-2)
}

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
