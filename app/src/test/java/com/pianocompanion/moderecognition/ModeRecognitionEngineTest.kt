package com.pianocompanion.moderecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ModeRecognitionEngine] 单元测试。
 *
 * 验证：
 * - 上行 MIDI 序列构建（主音+音阶+八度，共 8 个音）
 * - 下行 MIDI 序列构建
 * - 各调式半音偏移正确
 * - 难度对应可用调式数量
 * - 出题确定性（相同种子相同题目）
 * - 选项正确性（含正确答案、唯一、数量匹配）
 * - 题目字段完整性
 * - MIDI 范围钳制
 */
class ModeRecognitionEngineTest {

    private val engine = ModeRecognitionEngine()

    // ── 上行 MIDI 序列构建 ──────────────────────────────

    @Test
    fun `ascending midi has 8 notes for all modes`() {
        for (mode in ModeType.ALL) {
            val notes = engine.buildAscendingMidi(0, mode) // C 为主音
            assertEquals(
                "${mode.englishName} 上行应有 8 个音（主音+6+八度），实际 ${notes.size}",
                8,
                notes.size
            )
        }
    }

    @Test
    fun `ascending C major scale is C D E F G A B C`() {
        val notes = engine.buildAscendingMidi(0, ModeType.MAJOR)
        // C 大调：60 62 64 65 67 69 71 72
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72), notes)
    }

    @Test
    fun `ascending C natural minor scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.NATURAL_MINOR)
        // C 自然小调：60 62 63 65 67 68 70 72
        assertEquals(listOf(60, 62, 63, 65, 67, 68, 70, 72), notes)
    }

    @Test
    fun `ascending C harmonic minor scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.HARMONIC_MINOR)
        // C 和声小调：60 62 63 65 67 68 71 72（含增二度 68→71）
        assertEquals(listOf(60, 62, 63, 65, 67, 68, 71, 72), notes)
    }

    @Test
    fun `ascending C dorian scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.DORIAN)
        // C 多利亚：60 62 63 65 67 69 70 72（升高六级）
        assertEquals(listOf(60, 62, 63, 65, 67, 69, 70, 72), notes)
    }

    @Test
    fun `ascending C phrygian scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.PHRYGIAN)
        // C 弗利吉亚：60 61 63 65 67 68 70 72（降二级）
        assertEquals(listOf(60, 61, 63, 65, 67, 68, 70, 72), notes)
    }

    @Test
    fun `ascending C lydian scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.LYDIAN)
        // C 利底亚：60 62 64 66 67 69 71 72（升四级）
        assertEquals(listOf(60, 62, 64, 66, 67, 69, 71, 72), notes)
    }

    @Test
    fun `ascending C mixolydian scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.MIXOLYDIAN)
        // C 混合利底亚：60 62 64 65 67 69 70 72（降七级）
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 70, 72), notes)
    }

    @Test
    fun `ascending C locrian scale`() {
        val notes = engine.buildAscendingMidi(0, ModeType.LOCRIAN)
        // C 洛克利亚：60 61 63 65 66 68 70 72（降二降五）
        assertEquals(listOf(60, 61, 63, 65, 66, 68, 70, 72), notes)
    }

    @Test
    fun `ascending notes are monotonically increasing`() {
        for (mode in ModeType.ALL) {
            for (pc in 0..11) {
                val notes = engine.buildAscendingMidi(pc, mode)
                for (i in 1 until notes.size) {
                    assertTrue(
                        "${mode.englishName} pc=$pc 第 $i 音应递增",
                        notes[i] > notes[i - 1]
                    )
                }
            }
        }
    }

    @Test
    fun `ascending first note equals BASE_MIDI plus tonic`() {
        val notes = engine.buildAscendingMidi(7, ModeType.MAJOR) // G
        assertEquals(ModeRecognitionEngine.BASE_MIDI + 7, notes[0])
    }

    @Test
    fun `ascending last note is octave above first`() {
        val notes = engine.buildAscendingMidi(0, ModeType.MAJOR)
        assertEquals(notes[0] + 12, notes.last())
    }

    // ── 下行 MIDI 序列构建 ──────────────────────────────

    @Test
    fun `descending midi has 8 notes for all modes`() {
        for (mode in ModeType.ALL) {
            val notes = engine.buildDescendingMidi(0, mode)
            assertEquals(
                "${mode.englishName} 下行应有 8 个音，实际 ${notes.size}",
                8,
                notes.size
            )
        }
    }

    @Test
    fun `descending C major scale`() {
        val notes = engine.buildDescendingMidi(0, ModeType.MAJOR)
        // C 大调下行：72 71 69 67 65 64 62 60
        assertEquals(listOf(72, 71, 69, 67, 65, 64, 62, 60), notes)
    }

    @Test
    fun `descending notes are monotonically decreasing`() {
        for (mode in ModeType.ALL) {
            for (pc in 0..11) {
                val notes = engine.buildDescendingMidi(pc, mode)
                for (i in 1 until notes.size) {
                    assertTrue(
                        "${mode.englishName} pc=$pc 下行第 $i 音应递减",
                        notes[i] < notes[i - 1]
                    )
                }
            }
        }
    }

    @Test
    fun `descending first note is octave above tonic`() {
        val notes = engine.buildDescendingMidi(0, ModeType.MAJOR)
        assertEquals(ModeRecognitionEngine.BASE_MIDI + 12, notes[0])
    }

    @Test
    fun `descending last note equals tonic`() {
        val notes = engine.buildDescendingMidi(5, ModeType.MAJOR) // F
        assertEquals(ModeRecognitionEngine.BASE_MIDI + 5, notes.last())
    }

    // ── MIDI 范围钳制 ──────────────────────────────────

    @Test
    fun `all midi notes are within piano range`() {
        for (mode in ModeType.ALL) {
            for (pc in 0..11) {
                val asc = engine.buildAscendingMidi(pc, mode)
                val desc = engine.buildDescendingMidi(pc, mode)
                asc.forEach { n ->
                    assertTrue("上行 MIDI $n 超出钢琴范围", n in 21..108)
                }
                desc.forEach { n ->
                    assertTrue("下行 MIDI $n 超出钢琴范围", n in 21..108)
                }
            }
        }
    }

    // ── 难度与可用调式 ──────────────────────────────────

    @Test
    fun `beginner has 2 modes`() {
        assertEquals(2, ModeType.forDifficulty(ModeDifficulty.BEGINNER).size)
        assertTrue(ModeType.forDifficulty(ModeDifficulty.BEGINNER).contains(ModeType.MAJOR))
        assertTrue(ModeType.forDifficulty(ModeDifficulty.BEGINNER).contains(ModeType.NATURAL_MINOR))
    }

    @Test
    fun `intermediate has 5 modes`() {
        assertEquals(5, ModeType.forDifficulty(ModeDifficulty.INTERMEDIATE).size)
        val modes = ModeType.forDifficulty(ModeDifficulty.INTERMEDIATE)
        assertTrue(modes.contains(ModeType.MAJOR))
        assertTrue(modes.contains(ModeType.NATURAL_MINOR))
        assertTrue(modes.contains(ModeType.DORIAN))
        assertTrue(modes.contains(ModeType.MIXOLYDIAN))
        assertTrue(modes.contains(ModeType.HARMONIC_MINOR))
    }

    @Test
    fun `advanced has all 8 modes`() {
        assertEquals(ModeType.ALL.size, ModeType.forDifficulty(ModeDifficulty.ADVANCED).size)
    }

    // ── 出题测试 ──────────────────────────────────────────

    @Test
    fun `generate returns valid question with all fields`() {
        val q = engine.generate(ModeDifficulty.ADVANCED)
        assertNotNull(q)
        assertEquals(ModeDifficulty.ADVANCED, q.difficulty)
        assertTrue(q.ascendingMidiNotes.isNotEmpty())
        assertTrue(q.answerChoices.isNotEmpty())
        assertTrue(q.correctAnswer.isNotEmpty())
    }

    @Test
    fun `generate produces deterministic output with same seed`() {
        val e1 = ModeRecognitionEngine.withSeed(42L)
        val e2 = ModeRecognitionEngine.withSeed(42L)
        val q1 = e1.generate(ModeDifficulty.ADVANCED, PlayMode.ASCENDING_DESCENDING)
        val q2 = e2.generate(ModeDifficulty.ADVANCED, PlayMode.ASCENDING_DESCENDING)
        assertEquals(q1.mode, q2.mode)
        assertEquals(q1.tonic, q2.tonic)
        assertEquals(q1.ascendingMidiNotes, q2.ascendingMidiNotes)
        assertEquals(q1.descendingMidiNotes, q2.descendingMidiNotes)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `correct answer is always in choices`() {
        val e = ModeRecognitionEngine.withSeed(7L)
        repeat(50) {
            val q = e.generate(ModeDifficulty.ALL[it % 3])
            assertTrue(
                "正确答案 ${q.correctAnswer} 不在选项 ${q.answerChoices} 中",
                q.answerChoices.contains(q.correctAnswer)
            )
        }
    }

    @Test
    fun `choices are unique`() {
        val e = ModeRecognitionEngine.withSeed(13L)
        repeat(50) {
            val q = e.generate(ModeDifficulty.ALL[it % 3])
            assertEquals(
                "选项有重复: ${q.answerChoices}",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `choices count matches available modes for difficulty`() {
        val e = ModeRecognitionEngine.withSeed(99L)
        repeat(30) {
            val q = e.generate(ModeDifficulty.ALL[it % 3])
            val expected = ModeType.forDifficulty(q.difficulty).size
            assertEquals(
                "难度 ${q.difficulty} 选项数应等于可用调式数 $expected",
                expected,
                q.answerChoices.size
            )
        }
    }

    @Test
    fun `ascending play mode has empty descending notes`() {
        val e = ModeRecognitionEngine.withSeed(1L)
        val q = e.generate(ModeDifficulty.ADVANCED, PlayMode.ASCENDING)
        assertTrue(q.descendingMidiNotes.isEmpty())
    }

    @Test
    fun `ascending_descending play mode has non-empty descending notes`() {
        val e = ModeRecognitionEngine.withSeed(1L)
        val q = e.generate(ModeDifficulty.ADVANCED, PlayMode.ASCENDING_DESCENDING)
        assertTrue(q.descendingMidiNotes.isNotEmpty())
        assertEquals(8, q.descendingMidiNotes.size)
    }

    @Test
    fun `question fullName contains tonic and mode`() {
        val e = ModeRecognitionEngine.withSeed(5L)
        repeat(20) {
            val q = e.generate(ModeDifficulty.ALL[it % 3])
            assertTrue("fullName 应包含调式名: ${q.fullName}", q.fullName.contains(q.mode.displayName))
        }
    }

    @Test
    fun `question noteCount is 8 for all generated questions`() {
        val e = ModeRecognitionEngine.withSeed(77L)
        repeat(30) {
            val q = e.generate(ModeDifficulty.ALL[it % 3])
            assertEquals(8, q.noteCount)
        }
    }

    @Test
    fun `tonic pitch class is in valid range`() {
        val e = ModeRecognitionEngine.withSeed(3L)
        repeat(50) {
            val q = e.generate(ModeDifficulty.ADVANCED)
            assertTrue(
                "主音音级类应在 0..11，实际 ${q.tonic.pitchClass}",
                q.tonic.pitchClass in 0..11
            )
        }
    }

    // ── 调式半音偏移验证 ──────────────────────────────────

    @Test
    fun `all modes have 6 intervals`() {
        for (mode in ModeType.ALL) {
            assertEquals(
                "${mode.englishName} 应有 6 个半音偏移",
                6,
                mode.intervals.size
            )
        }
    }

    @Test
    fun `major mode has correct intervals`() {
        assertEquals(listOf(2, 4, 5, 7, 9, 11), ModeType.MAJOR.intervals)
    }

    @Test
    fun `phrygian has minor second from tonic`() {
        // 弗利吉亚的特征是第二级与主音只有 1 个半音
        assertEquals(1, ModeType.PHRYGIAN.intervals[0])
    }

    @Test
    fun `locrian has diminished fifth`() {
        // 洛克利亚含减五度（第 6 个半音）
        assertTrue(ModeType.LOCRIAN.intervals.contains(6))
    }

    @Test
    fun `lydian has augmented fourth`() {
        // 利底亚含增四度（第 6 个半音）
        assertEquals(6, ModeType.LYDIAN.intervals[2])
    }

    @Test
    fun `dorian differs from natural minor at sixth degree`() {
        // 多利亚第 6 级（intervals[4]）是 9，自然小调是 8
        assertEquals(9, ModeType.DORIAN.intervals[4])
        assertEquals(8, ModeType.NATURAL_MINOR.intervals[4])
    }

    @Test
    fun `harmonic minor has raised seventh`() {
        // 和声小调第 7 级是 11（导音），自然小调是 10
        assertEquals(11, ModeType.HARMONIC_MINOR.intervals[5])
        assertEquals(10, ModeType.NATURAL_MINOR.intervals[5])
    }
}
