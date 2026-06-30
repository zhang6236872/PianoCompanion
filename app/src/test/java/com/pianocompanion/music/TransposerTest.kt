package com.pianocompanion.music

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transposer 单元测试 — 乐谱移调引擎。
 */
class TransposerTest {

    /** 构造简单 C 大调音阶乐谱。 */
    private fun cMajorScore(): Score {
        // C4 D4 E4 F4 G4 A4 B4 C5
        val pattern = listOf(60, 62, 64, 65, 67, 69, 71, 72)
        val notes = pattern.mapIndexed { idx, midi ->
            ScoreNote(
                midiNumber = midi,
                noteName = MusicUtils.midiToNoteName(midi),
                startTime = idx * 500L,
                duration = 450L,
                velocity = 64,
                staff = Staff.TREBLE,
                measureIndex = idx / 4
            )
        }
        return Score(
            id = "test_c_major",
            title = "测试C大调",
            composer = "测试",
            notes = notes,
            tempo = 120
        )
    }

    // =========================
    // 基础移调
    // =========================

    @Test
    fun `零半音移调返回原乐谱`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 0)
        assertEquals(score, result)
    }

    @Test
    fun `升高一个八度所有音符加12`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 12)
        result.notes.forEachIndexed { idx, note ->
            assertEquals(score.notes[idx].midiNumber + 12, note.midiNumber)
        }
    }

    @Test
    fun `升高3半音正确移调`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 3)
        // C4(60)+3=Eb4(63), D4(62)+3=F4(65), ...
        result.notes.forEachIndexed { idx, note ->
            assertEquals(score.notes[idx].midiNumber + 3, note.midiNumber)
        }
    }

    @Test
    fun `降低3半音正确移调`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, -3)
        result.notes.forEachIndexed { idx, note ->
            assertEquals(score.notes[idx].midiNumber - 3, note.midiNumber)
        }
    }

    // =========================
    // 音名更新
    // =========================

    @Test
    fun `移调后音名正确更新`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 2)
        // C4→D4
        assertEquals("D4", result.notes[0].noteName)
        // D4→E4
        assertEquals("E4", result.notes[1].noteName)
        // E4→F#4
        assertEquals("F#4", result.notes[2].noteName)
    }

    @Test
    fun `移调后音名与 MusicUtils 一致`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 7)
        result.notes.forEach { note ->
            assertEquals(MusicUtils.midiToNoteName(note.midiNumber), note.noteName)
        }
    }

    // =========================
    // 非音高属性保留
    // =========================

    @Test
    fun `移调保留时值和起始时间`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 5)
        result.notes.forEachIndexed { idx, note ->
            assertEquals(score.notes[idx].startTime, note.startTime)
            assertEquals(score.notes[idx].duration, note.duration)
        }
    }

    @Test
    fun `移调保留力度和谱表`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, -2)
        result.notes.forEachIndexed { idx, note ->
            assertEquals(score.notes[idx].velocity, note.velocity)
            assertEquals(score.notes[idx].staff, note.staff)
        }
    }

    @Test
    fun `移调保留小节索引`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 1)
        result.notes.forEachIndexed { idx, note ->
            assertEquals(score.notes[idx].measureIndex, note.measureIndex)
        }
    }

    @Test
    fun `移调保留演奏法和指法`() {
        val note = ScoreNote(
            midiNumber = 60,
            noteName = "C4",
            startTime = 0,
            duration = 500,
            velocity = 80,
            staff = Staff.BASS,
            fingering = 3,
            articulation = com.pianocompanion.data.model.Articulation.STACCATO
        )
        val score = Score("t", "T", "C", listOf(note))
        val result = Transposer.transpose(score, 5)
        val rn = result.notes[0]
        assertEquals(80, rn.velocity)
        assertEquals(Staff.BASS, rn.staff)
        assertEquals(3, rn.fingering)
        assertEquals(com.pianocompanion.data.model.Articulation.STACCATO, rn.articulation)
    }

    @Test
    fun `移调不修改原乐谱`() {
        val score = cMajorScore()
        val originalFirst = score.notes[0].midiNumber
        Transposer.transpose(score, 7)
        assertEquals(originalFirst, score.notes[0].midiNumber)
    }

    // =========================
    // 钢琴范围钳位
    // =========================

    @Test
    fun `超出高音范围钳位到 C8`() {
        val note = ScoreNote(108, "C8", 0, 500) // C8 = 最高
        val score = Score("t", "T", "C", listOf(note))
        val result = Transposer.transpose(score, 5)
        assertEquals(108, result.notes[0].midiNumber)
    }

    @Test
    fun `超出低音范围钳位到 A0`() {
        val note = ScoreNote(21, "A0", 0, 500) // A0 = 最低
        val score = Score("t", "T", "C", listOf(note))
        val result = Transposer.transpose(score, -5)
        assertEquals(21, result.notes[0].midiNumber)
    }

    @Test
    fun `中间音符正常 不受边界音符影响`() {
        val notes = listOf(
            ScoreNote(21, "A0", 0, 500),      // 21+3=24, still valid
            ScoreNote(60, "C4", 500, 500),     // 60+3=63, valid
            ScoreNote(108, "C8", 1000, 500)    // 108+3=111→108, clamped
        )
        val score = Score("t", "T", "C", notes)
        val result = Transposer.transpose(score, 3)
        assertEquals(24, result.notes[0].midiNumber)   // 21+3=24, valid
        assertEquals(63, result.notes[1].midiNumber)    // 60+3=63, valid
        assertEquals(108, result.notes[2].midiNumber)   // clamped to max
    }

    @Test
    fun `MIN_MIDI 和 MAX_MIDI 常量正确`() {
        assertEquals(21, Transposer.MIN_MIDI)
        assertEquals(108, Transposer.MAX_MIDI)
    }

    // =========================
    // 越界检测
    // =========================

    @Test
    fun `countOutOfRange 正常移调返回0`() {
        val score = cMajorScore()
        assertEquals(0, Transposer.countOutOfRange(score, 5))
    }

    @Test
    fun `countOutOfRange 检测越界音符`() {
        val notes = listOf(
            ScoreNote(23, "B0", 0, 500),     // 23-3=20 < 21, out of range for -3
            ScoreNote(60, "C4", 500, 500),   // valid both ways
            ScoreNote(107, "B7", 1000, 500)  // 107+3=110 > 108, out of range for +3
        )
        val score = Score("t", "T", "C", notes)
        assertEquals(1, Transposer.countOutOfRange(score, 3))   // only note 3 (107+3=110)
        assertEquals(1, Transposer.countOutOfRange(score, -3))  // only note 1 (23-3=20)
    }

    // =========================
    // 标题和 ID
    // =========================

    @Test
    fun `标题追加移调标注`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 3)
        assertEquals("测试C大调 [移调 +3]", result.title)
    }

    @Test
    fun `负半音标题标注`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, -2)
        assertEquals("测试C大调 [移调 -2]", result.title)
    }

    @Test
    fun `ID 追加移调后缀`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 5)
        assertEquals("test_c_major_transposed_5", result.id)
    }

    @Test
    fun `零移调不改标题和ID`() {
        val score = cMajorScore()
        val result = Transposer.transpose(score, 0)
        assertEquals(score.title, result.title)
        assertEquals(score.id, result.id)
    }

    // =========================
    // 移调到目标调
    // =========================

    @Test
    fun `transposeToKey 从 C 大调到 G 大调`() {
        val score = cMajorScore()
        val targetKey = KeyInfo(7, KeyMode.MAJOR, 1f) // G 大调
        val result = Transposer.transposeToKey(score, targetKey)
        // C→G = +7 半音
        assertEquals(67, result.notes[0].midiNumber) // C4(60)+7=G4(67)
    }

    @Test
    fun `transposeToKey 到 C 大调不变`() {
        val score = cMajorScore()
        val targetKey = KeyInfo(0, KeyMode.MAJOR, 1f) // C 大调
        val result = Transposer.transposeToKey(score, targetKey)
        assertEquals(60, result.notes[0].midiNumber)
    }

    // =========================
    // 半音偏移计算
    // =========================

    @Test
    fun `computeSemitoneOffset 同调返回0`() {
        val from = KeyInfo(0, KeyMode.MAJOR, 1f)
        val to = KeyInfo(0, KeyMode.MAJOR, 1f)
        assertEquals(0, Transposer.computeSemitoneOffset(from, to))
    }

    @Test
    fun `computeSemitoneOffset 取最短路径`() {
        // C(0) → B(11): 最短路径 = -1 而非 +11
        val from = KeyInfo(0, KeyMode.MAJOR, 1f)
        val to = KeyInfo(11, KeyMode.MAJOR, 1f)
        assertEquals(-1, Transposer.computeSemitoneOffset(from, to))
    }

    @Test
    fun `computeSemitoneOffset 正方向`() {
        val from = KeyInfo(0, KeyMode.MAJOR, 1f)
        val to = KeyInfo(7, KeyMode.MAJOR, 1f) // C→G = +7
        assertEquals(7, Transposer.computeSemitoneOffset(from, to))
    }

    @Test
    fun `computeSemitoneOffset 负方向`() {
        val from = KeyInfo(7, KeyMode.MAJOR, 1f)
        val to = KeyInfo(0, KeyMode.MAJOR, 1f) // G→C = -7→ 但 -7%12=5... 实际 -7 + 12 = 5
        // G(7) → C(0): diff = (0-7)%12 = -7, -7 < -6 → +12 = 5? 不对
        // diff = Math.floorMod(0-7, 12) = 5; 但代码用 % 12
        // (0 - 7) % 12 = -7 % 12 = -7 (Kotlin); -7 < -6 → +12 = 5
        // 这不对，G→C 最短路径应该是 -5 或 +7
        // 让我重新想: diff = (0-7) % 12 = -7; -7 < -6 → -7+12 = 5
        // 但 G→C 向上 +5 半音 (G→A→Bb→B→C? 不, G+5=C)
        // G(7) + 5 = 12 = 0 (C). 是的, +5 是正确的最短路径
        assertEquals(5, Transposer.computeSemitoneOffset(from, to))
    }

    // =========================
    // 常用调列表
    // =========================

    @Test
    fun `COMMON_KEYS 包含 C 大调原调`() {
        val cKey = Transposer.COMMON_KEYS.find { it.semitoneOffset == 0 }
        assert(cKey != null)
        assertEquals("原调(0)", cKey!!.shortLabel)
    }

    @Test
    fun `COMMON_KEYS 非空且合理`() {
        assertTrue(Transposer.COMMON_KEYS.isNotEmpty())
        assertTrue(Transposer.COMMON_KEYS.size >= 10)
    }

    // =========================
    // 空乐谱
    // =========================

    @Test
    fun `空乐谱移调不崩溃`() {
        val score = Score("empty", "空", "无", emptyList())
        val result = Transposer.transpose(score, 5)
        assertEquals(0, result.notes.size)
        assertEquals("空 [移调 +5]", result.title)
    }

    // =========================
    // 多声部
    // =========================

    @Test
    fun `多声部乐谱统一移调`() {
        val notes = listOf(
            ScoreNote(60, "C4", 0, 500, staff = Staff.TREBLE),
            ScoreNote(48, "C3", 0, 500, staff = Staff.BASS)
        )
        val score = Score("duo", "双手", "测试", notes)
        val result = Transposer.transpose(score, 2)
        assertEquals(62, result.notes[0].midiNumber) // C4+2=D4
        assertEquals(50, result.notes[1].midiNumber) // C3+2=D3
        assertEquals(Staff.TREBLE, result.notes[0].staff)
        assertEquals(Staff.BASS, result.notes[1].staff)
    }

    // =========================
    // 和弦移调
    // =========================

    @Test
    fun `和弦成员统一移调保持音程关系`() {
        // C大三和弦 C-E-G = 60-64-67
        val notes = listOf(
            ScoreNote(60, "C4", 0, 500),
            ScoreNote(64, "E4", 0, 500),
            ScoreNote(67, "G4", 0, 500)
        )
        val score = Score("chord", "和弦", "测试", notes)
        val result = Transposer.transpose(score, 4)
        // 移调后 E-G#-B = 64-68-71
        assertEquals(64, result.notes[0].midiNumber)
        assertEquals(68, result.notes[1].midiNumber)
        assertEquals(71, result.notes[2].midiNumber)
        // 音程关系不变
        val origInterval = notes[2].midiNumber - notes[0].midiNumber
        val newInterval = result.notes[2].midiNumber - result.notes[0].midiNumber
        assertEquals(origInterval, newInterval)
    }
}
