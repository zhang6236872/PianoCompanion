package com.pianocompanion.chordfunctiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦功能听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、功能映射、音域范围、难度配置等。
 */
class ChordFunctionTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = ChordFunctionTrainingEngine.withSeed(42L)
        val e2 = ChordFunctionTrainingEngine.withSeed(42L)
        val q1 = e1.generate(ChordFunctionDifficulty.INTERMEDIATE)
        val q2 = e2.generate(ChordFunctionDifficulty.INTERMEDIATE)
        assertEquals(q1.scaleDegree, q2.scaleDegree)
        assertEquals(q1.key, q2.key)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var foundDifferent = false
        for (seed in 0..100) {
            val q1 = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            val q2 = ChordFunctionTrainingEngine.withSeed((seed + 500).toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            if (q1.scaleDegree != q2.scaleDegree || q1.key != q2.key) {
                foundDifferent = true
                break
            }
        }
        assertTrue("不同种子应该能产生不同题目", foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `all difficulties have 3 options`() {
        for (difficulty in ChordFunctionDifficulty.ALL) {
            val q = ChordFunctionTrainingEngine.withSeed(1L).generate(difficulty)
            assertEquals("${difficulty.displayName}: 应有 3 个选项", 3, q.answerChoices.size)
        }
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `options contain all 3 functions`() {
        val expected = HarmonicFunction.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── 功能映射正确性 ────────────────────────────────────────

    @Test
    fun `degree I maps to TONIC`() {
        assertEquals(HarmonicFunction.TONIC, ScaleDegree.I.function)
    }

    @Test
    fun `degree ii maps to SUBDOMINANT`() {
        assertEquals(HarmonicFunction.SUBDOMINANT, ScaleDegree.II.function)
    }

    @Test
    fun `degree iii maps to TONIC`() {
        assertEquals(HarmonicFunction.TONIC, ScaleDegree.III.function)
    }

    @Test
    fun `degree IV maps to SUBDOMINANT`() {
        assertEquals(HarmonicFunction.SUBDOMINANT, ScaleDegree.IV.function)
    }

    @Test
    fun `degree V maps to DOMINANT`() {
        assertEquals(HarmonicFunction.DOMINANT, ScaleDegree.V.function)
    }

    @Test
    fun `degree vi maps to TONIC`() {
        assertEquals(HarmonicFunction.TONIC, ScaleDegree.VI.function)
    }

    @Test
    fun `degree viio maps to DOMINANT`() {
        assertEquals(HarmonicFunction.DOMINANT, ScaleDegree.VII_DIM.function)
    }

    @Test
    fun `correct answer matches degree function`() {
        for (seed in 0..200) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.INTERMEDIATE)
            assertEquals(
                "${q.scaleDegree.romanNumeral} 的正确答案应是 ${q.scaleDegree.function.displayName}",
                q.scaleDegree.function.displayName,
                q.correctAnswer
            )
        }
    }

    // ── MIDI 音符正确性 ──────────────────────────────────────

    @Test
    fun `I triad in C major is C E G`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.I, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(48, 52, 55), notes)
    }

    @Test
    fun `IV triad in C major is F A C`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.IV, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(53, 57, 60), notes)
    }

    @Test
    fun `V triad in C major is G B D`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.V, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(55, 59, 62), notes)
    }

    @Test
    fun `ii triad in C major is D F A`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.II, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(50, 53, 57), notes)
    }

    @Test
    fun `vi triad in C major is A C E`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.VI, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(57, 60, 64), notes)
    }

    @Test
    fun `viio triad in C major is B D F`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.VII_DIM, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(59, 62, 65), notes)
    }

    @Test
    fun `iii triad in C major is E G B`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.III, MusicalKey.C_MAJOR.tonicMidi)
        assertEquals(listOf(52, 55, 59), notes)
    }

    // ── 七和弦 MIDI 正确性 ───────────────────────────────────

    @Test
    fun `I seventh in C major is C E G B`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.I, MusicalKey.C_MAJOR.tonicMidi, useSeventh = true)
        assertEquals(listOf(48, 52, 55, 59), notes)
    }

    @Test
    fun `V seventh in C major is G B D F (dominant seventh)`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.V, MusicalKey.C_MAJOR.tonicMidi, useSeventh = true)
        assertEquals(listOf(55, 59, 62, 65), notes)
    }

    @Test
    fun `viio seventh in C major is B D F A (half-diminished)`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.VII_DIM, MusicalKey.C_MAJOR.tonicMidi, useSeventh = true)
        assertEquals(listOf(59, 62, 65, 69), notes)
    }

    @Test
    fun `seventh chord has 4 notes and triad has 3`() {
        for (degree in ScaleDegree.ALL) {
            val triad = ChordFunctionTrainingEngine.buildChordMidiNotes(degree, MusicalKey.C_MAJOR.tonicMidi)
            val seventh = ChordFunctionTrainingEngine.buildChordMidiNotes(degree, MusicalKey.C_MAJOR.tonicMidi, useSeventh = true)
            assertEquals(3, triad.size)
            assertEquals(4, seventh.size)
        }
    }

    // ── 不同调性 ──────────────────────────────────────────────

    @Test
    fun `I triad in G major is G B D`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.I, MusicalKey.G_MAJOR.tonicMidi)
        assertEquals(listOf(55, 59, 62), notes)
    }

    @Test
    fun `V triad in G major is D F# A`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.V, MusicalKey.G_MAJOR.tonicMidi)
        assertEquals(listOf(62, 66, 69), notes)
    }

    @Test
    fun `I triad in F major is F A C`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.I, MusicalKey.F_MAJOR.tonicMidi)
        assertEquals(listOf(53, 57, 60), notes)
    }

    @Test
    fun `I triad in D major is D F# A`() {
        val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(ScaleDegree.I, MusicalKey.D_MAJOR.tonicMidi)
        assertEquals(listOf(50, 54, 57), notes)
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in 21..108)
            }
        }
    }

    @Test
    fun `all keys and degrees produce valid MIDI`() {
        for (key in MusicalKey.ALL) {
            for (degree in ScaleDegree.ALL) {
                val notes = ChordFunctionTrainingEngine.buildChordMidiNotes(degree, key.tonicMidi, useSeventh = true)
                for (midi in notes) {
                    assertTrue(
                        "${key.displayName} ${degree.romanNumeral}七和弦: MIDI $midi 应在范围内",
                        midi in 21..108
                    )
                }
            }
        }
    }

    // ── 音符排列 ──────────────────────────────────────────────

    @Test
    fun `midi notes are sorted ascending`() {
        for (seed in 0..100) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            for (i in 0 until q.midiNotes.size - 1) {
                assertTrue(
                    "音符应按升序排列 (seed=$seed, idx=$i)",
                    q.midiNotes[i] <= q.midiNotes[i + 1]
                )
            }
        }
    }

    @Test
    fun `chord root is lowest note`() {
        for (seed in 0..100) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            assertEquals("和弦根音应是最低音 (seed=$seed)", q.chordRootMidi, q.midiNotes[0])
        }
    }

    // ── 难度配置 ──────────────────────────────────────────────

    @Test
    fun `beginner uses only primary triads`() {
        val degrees = ChordFunctionTrainingEngine.degreesForDifficulty(ChordFunctionDifficulty.BEGINNER)
        assertEquals(ScaleDegree.PRIMARY_TRIADS.toSet(), degrees.toSet())
    }

    @Test
    fun `intermediate uses all 7 degrees`() {
        val degrees = ChordFunctionTrainingEngine.degreesForDifficulty(ChordFunctionDifficulty.INTERMEDIATE)
        assertEquals(7, degrees.size)
        assertEquals(ScaleDegree.ALL.toSet(), degrees.toSet())
    }

    @Test
    fun `advanced uses all 7 degrees`() {
        val degrees = ChordFunctionTrainingEngine.degreesForDifficulty(ChordFunctionDifficulty.ADVANCED)
        assertEquals(7, degrees.size)
    }

    @Test
    fun `beginner uses triads not sevenths`() {
        for (seed in 0..100) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.BEGINNER)
            assertFalse("初级应使用三和弦 (seed=$seed)", q.useSeventh)
            assertEquals("初级应有 3 个音 (seed=$seed)", 3, q.midiNotes.size)
        }
    }

    @Test
    fun `intermediate uses triads not sevenths`() {
        for (seed in 0..100) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.INTERMEDIATE)
            assertFalse("中级应使用三和弦 (seed=$seed)", q.useSeventh)
        }
    }

    @Test
    fun `advanced uses sevenths`() {
        for (seed in 0..100) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.ADVANCED)
            assertTrue("高级应使用七和弦 (seed=$seed)", q.useSeventh)
            assertEquals("高级应有 4 个音 (seed=$seed)", 4, q.midiNotes.size)
        }
    }

    @Test
    fun `beginner only generates I IV V`() {
        for (seed in 0..200) {
            val q = ChordFunctionTrainingEngine.withSeed(seed.toLong()).generate(ChordFunctionDifficulty.BEGINNER)
            assertTrue(
                "初级只应出 I/IV/V (seed=$seed, 实际 ${q.scaleDegree.romanNumeral})",
                q.scaleDegree in ScaleDegree.PRIMARY_TRIADS
            )
        }
    }

    // ── 功能与属性的完备性 ───────────────────────────────────

    @Test
    fun `all functions have non-empty descriptions`() {
        for (func in HarmonicFunction.ALL) {
            assertTrue("${func.displayName} 的描述不应为空", func.description.isNotEmpty())
        }
    }

    @Test
    fun `functions have distinct display names`() {
        val names = HarmonicFunction.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    @Test
    fun `tension levels are ordered 0 through 2`() {
        assertEquals(0, HarmonicFunction.TONIC.tensionLevel)
        assertEquals(1, HarmonicFunction.SUBDOMINANT.tensionLevel)
        assertEquals(2, HarmonicFunction.DOMINANT.tensionLevel)
    }

    @Test
    fun `function symbols are T S D`() {
        assertEquals("T", HarmonicFunction.TONIC.symbol)
        assertEquals("S", HarmonicFunction.SUBDOMINANT.symbol)
        assertEquals("D", HarmonicFunction.DOMINANT.symbol)
    }

    @Test
    fun `tonic functions are I iii vi`() {
        val tonicDegrees = ScaleDegree.ALL.filter { it.function == HarmonicFunction.TONIC }
        assertEquals(3, tonicDegrees.size)
        assertTrue(tonicDegrees.contains(ScaleDegree.I))
        assertTrue(tonicDegrees.contains(ScaleDegree.III))
        assertTrue(tonicDegrees.contains(ScaleDegree.VI))
    }

    @Test
    fun `subdominant functions are ii IV`() {
        val subDegrees = ScaleDegree.ALL.filter { it.function == HarmonicFunction.SUBDOMINANT }
        assertEquals(2, subDegrees.size)
        assertTrue(subDegrees.contains(ScaleDegree.II))
        assertTrue(subDegrees.contains(ScaleDegree.IV))
    }

    @Test
    fun `dominant functions are V viio`() {
        val domDegrees = ScaleDegree.ALL.filter { it.function == HarmonicFunction.DOMINANT }
        assertEquals(2, domDegrees.size)
        assertTrue(domDegrees.contains(ScaleDegree.V))
        assertTrue(domDegrees.contains(ScaleDegree.VII_DIM))
    }

    @Test
    fun `all 7 degrees are accounted for`() {
        val allByFunction = ScaleDegree.ALL.groupBy { it.function }
        val total = allByFunction.values.sumOf { it.size }
        assertEquals(7, total)
    }

    @Test
    fun `primary triads are I IV V`() {
        assertEquals(3, ScaleDegree.PRIMARY_TRIADS.size)
        assertTrue(ScaleDegree.PRIMARY_TRIADS.contains(ScaleDegree.I))
        assertTrue(ScaleDegree.PRIMARY_TRIADS.contains(ScaleDegree.IV))
        assertTrue(ScaleDegree.PRIMARY_TRIADS.contains(ScaleDegree.V))
    }

    @Test
    fun `all keys have 4 entries`() {
        assertEquals(4, MusicalKey.ALL.size)
    }

    // ── ChordFunctionQuestion 参数校验 ───────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 2 notes throws`() {
        ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = 48,
            difficulty = ChordFunctionDifficulty.BEGINNER,
            useSeventh = false,
            midiNotes = listOf(48, 52),
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with 5 notes throws`() {
        ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = 48,
            difficulty = ChordFunctionDifficulty.ADVANCED,
            useSeventh = true,
            midiNotes = listOf(48, 52, 55, 59, 72),
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = 48,
            difficulty = ChordFunctionDifficulty.BEGINNER,
            useSeventh = false,
            midiNotes = listOf(48, 52, 200),
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
    }

    @Test
    fun `question with 3 notes is valid`() {
        val q = ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = 48,
            difficulty = ChordFunctionDifficulty.BEGINNER,
            useSeventh = false,
            midiNotes = listOf(48, 52, 55),
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
        assertEquals(3, q.midiNotes.size)
    }

    @Test
    fun `question with 4 notes is valid`() {
        val q = ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = 48,
            difficulty = ChordFunctionDifficulty.ADVANCED,
            useSeventh = true,
            midiNotes = listOf(48, 52, 55, 59),
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
        assertEquals(4, q.midiNotes.size)
    }

    @Test
    fun `fullDescription contains key name and degree`() {
        val q = ChordFunctionTrainingEngine.withSeed(1L).generate(ChordFunctionDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.key.displayName))
        assertTrue(q.fullDescription.contains(q.scaleDegree.romanNumeral))
    }

}
