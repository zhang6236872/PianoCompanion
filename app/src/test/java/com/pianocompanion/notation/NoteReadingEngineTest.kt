package com.pianocompanion.notation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoteReadingEngine] 单元测试。
 *
 * 验证：
 * - 谱表位置 → MIDI 换算（高音/低音谱号各线/间/加线）
 * - MIDI → 音名字母映射
 * - MIDI → 八度映射
 * - 难度池（初级/中级/高级的谱表位置范围）
 * - 出题确定性（相同种子相同题目）
 * - 选项构建（正确答案在选项中、选项唯一、选项数量）
 * - 各难度 × 各谱号的出题覆盖
 */
class NoteReadingEngineTest {

    private val engine = NoteReadingEngine()

    // ── 谱表位置 → MIDI（高音谱号）──────────────────────

    @Test
    fun `treble clef bottom line is E4`() {
        // step 0 = E4 = MIDI 64
        assertEquals(64, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 0))
    }

    @Test
    fun `treble clef lines are E4 G4 B4 D5 F5`() {
        assertEquals(64, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 0)) // E4
        assertEquals(67, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 2)) // G4
        assertEquals(71, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 4)) // B4
        assertEquals(74, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 6)) // D5
        assertEquals(77, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 8)) // F5
    }

    @Test
    fun `treble clef spaces are F4 A4 C5 E5`() {
        assertEquals(65, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 1)) // F4
        assertEquals(69, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 3)) // A4
        assertEquals(72, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 5)) // C5
        assertEquals(76, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 7)) // E5
    }

    @Test
    fun `treble clef middle C is step minus 2`() {
        // C4 (middle C) = MIDI 60, below the staff
        assertEquals(60, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, -2))
    }

    @Test
    fun `treble clef ledger lines above staff`() {
        // G5 = step 9 (space above top line), A5 = step 10 (1st ledger line above)
        assertEquals(79, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 9))  // G5
        assertEquals(81, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 10)) // A5
    }

    @Test
    fun `treble clef step minus 1 is D4`() {
        assertEquals(62, engine.diatonicStepToMidi(NoteReadingClef.TREBLE, -1)) // D4
    }

    // ── 谱表位置 → MIDI（低音谱号）──────────────────────

    @Test
    fun `bass clef bottom line is G2`() {
        // step 0 = G2 = MIDI 43
        assertEquals(43, engine.diatonicStepToMidi(NoteReadingClef.BASS, 0))
    }

    @Test
    fun `bass clef lines are G2 B2 D3 F3 A3`() {
        assertEquals(43, engine.diatonicStepToMidi(NoteReadingClef.BASS, 0)) // G2
        assertEquals(47, engine.diatonicStepToMidi(NoteReadingClef.BASS, 2)) // B2
        assertEquals(50, engine.diatonicStepToMidi(NoteReadingClef.BASS, 4)) // D3
        assertEquals(53, engine.diatonicStepToMidi(NoteReadingClef.BASS, 6)) // F3
        assertEquals(57, engine.diatonicStepToMidi(NoteReadingClef.BASS, 8)) // A3
    }

    @Test
    fun `bass clef spaces are A2 C3 E3 G3`() {
        assertEquals(45, engine.diatonicStepToMidi(NoteReadingClef.BASS, 1)) // A2
        assertEquals(48, engine.diatonicStepToMidi(NoteReadingClef.BASS, 3)) // C3
        assertEquals(52, engine.diatonicStepToMidi(NoteReadingClef.BASS, 5)) // E3
        assertEquals(55, engine.diatonicStepToMidi(NoteReadingClef.BASS, 7)) // G3
    }

    @Test
    fun `bass clef middle C is step 10`() {
        // C4 (middle C) = MIDI 60, above the bass staff
        assertEquals(60, engine.diatonicStepToMidi(NoteReadingClef.BASS, 10))
    }

    @Test
    fun `bass clef below staff`() {
        // F2 = step -1, E2 = step -2
        assertEquals(41, engine.diatonicStepToMidi(NoteReadingClef.BASS, -1)) // F2
        assertEquals(40, engine.diatonicStepToMidi(NoteReadingClef.BASS, -2)) // E2
    }

    // ── MIDI → 音名字母 ─────────────────────────────────

    @Test
    fun `midi to letter name natural notes`() {
        assertEquals("C", engine.midiToLetterName(60)) // C4
        assertEquals("D", engine.midiToLetterName(62)) // D4
        assertEquals("E", engine.midiToLetterName(64)) // E4
        assertEquals("F", engine.midiToLetterName(65)) // F4
        assertEquals("G", engine.midiToLetterName(67)) // G4
        assertEquals("A", engine.midiToLetterName(69)) // A4
        assertEquals("B", engine.midiToLetterName(71)) // B4
    }

    @Test
    fun `midi to letter name returns empty for accidentals`() {
        assertEquals("", engine.midiToLetterName(61)) // C#4
        assertEquals("", engine.midiToLetterName(63)) // D#4
        assertEquals("", engine.midiToLetterName(66)) // F#4
    }

    // ── MIDI → 八度 ──────────────────────────────────────

    @Test
    fun `midi to octave`() {
        assertEquals(4, engine.midiToOctave(60)) // C4
        assertEquals(4, engine.midiToOctave(71)) // B4
        assertEquals(5, engine.midiToOctave(72)) // C5
        assertEquals(2, engine.midiToOctave(43)) // G2
        assertEquals(0, engine.midiToOctave(12)) // C0
    }

    // ── 出题测试 ──────────────────────────────────────────

    @Test
    fun `generate returns valid question with all fields`() {
        val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertNotNull(q)
        assertEquals(NoteReadingClef.TREBLE, q.clef)
        assertEquals(NoteReadingDifficulty.BEGINNER, q.difficulty)
        assertTrue(q.midiNote > 0)
        assertTrue(q.letterName.isNotEmpty())
        assertTrue(q.fullNoteName.contains(q.letterName))
        assertTrue(q.answerChoices.isNotEmpty())
    }

    @Test
    fun `generate produces deterministic output with same seed`() {
        val e1 = NoteReadingEngine.withSeed(42L)
        val e2 = NoteReadingEngine.withSeed(42L)
        val q1 = e1.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.INTERMEDIATE)
        val q2 = e2.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.INTERMEDIATE)
        assertEquals(q1.staffStep, q2.staffStep)
        assertEquals(q1.midiNote, q2.midiNote)
        assertEquals(q1.letterName, q2.letterName)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `correct answer is always in choices`() {
        repeat(50) {
            val q = engine.generate(
                if (it % 2 == 0) NoteReadingClef.TREBLE else NoteReadingClef.BASS,
                NoteReadingDifficulty.ALL[it % 3]
            )
            assertTrue(
                "正确答案 ${q.letterName} 不在选项 ${q.answerChoices} 中",
                q.answerChoices.contains(q.letterName)
            )
        }
    }

    @Test
    fun `choices are unique`() {
        repeat(50) {
            val q = engine.generate(
                if (it % 2 == 0) NoteReadingClef.TREBLE else NoteReadingClef.BASS,
                NoteReadingDifficulty.ALL[it % 3]
            )
            assertEquals(
                "选项有重复: ${q.answerChoices}",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `beginner difficulty only produces line notes`() {
        val lineSteps = setOf(0, 2, 4, 6, 8)
        repeat(100) {
            val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
            assertTrue(
                "初级难度出现了非线位置 step=${q.staffStep}",
                q.staffStep in lineSteps
            )
        }
    }

    @Test
    fun `intermediate difficulty produces line and space notes`() {
        val validSteps = (0..8).toSet()
        repeat(100) {
            val q = engine.generate(NoteReadingClef.BASS, NoteReadingDifficulty.INTERMEDIATE)
            assertTrue(
                "中级难度出现了范围外的 step=${q.staffStep}",
                q.staffStep in validSteps
            )
        }
    }

    @Test
    fun `advanced difficulty can produce ledger line notes`() {
        val advancedSteps = mutableSetOf<Int>()
        // 用固定种子多次生成收集所有可能的 step
        for (seed in 0L..500L) {
            val e = NoteReadingEngine.withSeed(seed)
            val q = e.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.ADVANCED)
            advancedSteps.add(q.staffStep)
        }
        // 高级应包含加线音符（step < 0 或 step > 8）
        assertTrue(
            "高级难度未产生加线音符，所有 steps: $advancedSteps",
            advancedSteps.any { it < 0 || it > 8 }
        )
    }

    @Test
    fun `midi note matches staffStep for treble clef`() {
        repeat(50) {
            val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.ADVANCED)
            val expectedMidi = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, q.staffStep)
            assertEquals(expectedMidi, q.midiNote)
        }
    }

    @Test
    fun `midi note matches staffStep for bass clef`() {
        repeat(50) {
            val q = engine.generate(NoteReadingClef.BASS, NoteReadingDifficulty.ADVANCED)
            val expectedMidi = engine.diatonicStepToMidi(NoteReadingClef.BASS, q.staffStep)
            assertEquals(expectedMidi, q.midiNote)
        }
    }

    @Test
    fun `letter name matches midi note`() {
        repeat(100) {
            val clef = if (it % 2 == 0) NoteReadingClef.TREBLE else NoteReadingClef.BASS
            val diff = NoteReadingDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            assertEquals(
                engine.midiToLetterName(q.midiNote),
                q.letterName
            )
        }
    }

    @Test
    fun `full note name contains correct octave`() {
        val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        val expectedOctave = engine.midiToOctave(q.midiNote)
        assertTrue(
            "完整音名 ${q.fullNoteName} 不含正确八度 $expectedOctave",
            q.fullNoteName.endsWith(expectedOctave.toString())
        )
    }

    @Test
    fun `all treble beginner notes are natural notes`() {
        // 验证所有初级高音谱号线音符都是自然音（E, G, B, D, F）
        val validLetters = setOf("E", "G", "B", "D", "F")
        repeat(100) {
            val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
            assertTrue(
                "高音初级出现了非线字母 ${q.letterName}",
                q.letterName in validLetters
            )
        }
    }

    @Test
    fun `all bass beginner notes are natural notes`() {
        // 低音谱号线：G, B, D, F, A
        val validLetters = setOf("G", "B", "D", "F", "A")
        repeat(100) {
            val q = engine.generate(NoteReadingClef.BASS, NoteReadingDifficulty.BEGINNER)
            assertTrue(
                "低音初级出现了非线字母 ${q.letterName}",
                q.letterName in validLetters
            )
        }
    }

    @Test
    fun `choices count is reasonable`() {
        // 初级最少 5 个不同字母，选项应 ≤4
        val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
        assertTrue(q.answerChoices.size in 2..4)
    }

    @Test
    fun `beginner choices are subset of line letters`() {
        val lineLetters = setOf("E", "G", "B", "D", "F")
        repeat(50) {
            val q = engine.generate(NoteReadingClef.TREBLE, NoteReadingDifficulty.BEGINNER)
            q.answerChoices.forEach { choice ->
                assertTrue(
                    "选项 $choice 不在高音初级字母集中",
                    choice in lineLetters
                )
            }
        }
    }

    @Test
    fun `diatonicStepToMidi handles large positive steps`() {
        // step 14 from treble bottom = well above staff
        val midi = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 14)
        assertTrue("大正数 step MIDI 应在合理范围: $midi", midi in 21..108)
    }

    @Test
    fun `diatonicStepToMidi handles large negative steps`() {
        val midi = engine.diatonicStepToMidi(NoteReadingClef.BASS, -10)
        assertTrue("大负数 step MIDI 应在合理范围: $midi", midi >= 0)
    }

    @Test
    fun `semitone pattern between adjacent diatonic steps is correct`() {
        // 验证 E→F 是半音（+1），F→G 是全音（+2），等等
        val trebleSteps = (-2..10).toList()
        for (i in 0 until trebleSteps.size - 1) {
            val midi1 = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, trebleSteps[i])
            val midi2 = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, trebleSteps[i + 1])
            val diff = midi2 - midi1
            assertTrue(
                "相邻 step ${trebleSteps[i]}→${trebleSteps[i + 1]} 的半音差 $diff 不合法",
                diff == 1 || diff == 2
            )
        }
    }

    @Test
    fun `E to F step has 1 semitone`() {
        val e = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 0) // E4
        val f = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 1) // F4
        assertEquals(1, f - e)
    }

    @Test
    fun `B to C step has 1 semitone`() {
        val b = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 4) // B4
        val c = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 5) // C5
        assertEquals(1, c - b)
    }

    @Test
    fun `F to G step has 2 semitones`() {
        val f = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 1) // F4
        val g = engine.diatonicStepToMidi(NoteReadingClef.TREBLE, 2) // G4
        assertEquals(2, g - f)
    }
}
