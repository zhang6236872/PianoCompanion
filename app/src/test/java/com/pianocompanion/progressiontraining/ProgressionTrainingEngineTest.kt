package com.pianocompanion.progressiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦进行听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、调内音级映射、音域范围、难度配置等。
 */
class ProgressionTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = ProgressionTrainingEngine.withSeed(42L)
        val e2 = ProgressionTrainingEngine.withSeed(42L)
        val q1 = e1.generate(ProgressionDifficulty.INTERMEDIATE)
        val q2 = e2.generate(ProgressionDifficulty.INTERMEDIATE)
        assertEquals(q1.type, q2.type)
        assertEquals(q1.tonicMidi, q2.tonicMidi)
        assertEquals(q1.chordProgression, q2.chordProgression)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var foundDifferent = false
        for (seed in 0..100) {
            val q1 = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(ProgressionDifficulty.ADVANCED)
            val q2 = ProgressionTrainingEngine.withSeed((seed + 500).toLong()).generate(ProgressionDifficulty.ADVANCED)
            if (q1.type != q2.type || q1.tonicMidi != q2.tonicMidi) {
                foundDifferent = true
                break
            }
        }
        assertTrue("不同种子应该能产生不同题目", foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `beginner has 3 options`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 4 options`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has 5 options`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `correct answer is always in choices`() {
        for (difficulty in ProgressionDifficulty.ALL) {
            for (seed in 0..20) {
                val q = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                assertTrue(
                    "正确答案 '${q.correctAnswer}' 必须在选项中",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `all choices are unique`() {
        for (difficulty in ProgressionDifficulty.ALL) {
            val q = ProgressionTrainingEngine.withSeed(1L).generate(difficulty)
            assertEquals(
                "选项不能有重复",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `choices equal difficulty progression set`() {
        for (difficulty in ProgressionDifficulty.ALL) {
            val q = ProgressionTrainingEngine.withSeed(1L).generate(difficulty)
            val expectedNames = ProgressionType.forDifficulty(difficulty).map { it.displayName }.toSet()
            assertEquals(expectedNames, q.answerChoices.toSet())
        }
    }

    // ── MIDI 音符正确性 ────────────────────────────────────

    @Test
    fun `each chord has 3 notes`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.ADVANCED)
        assertTrue(q.chordProgression.all { it.size == 3 })
    }

    @Test
    fun `chord count matches progression type`() {
        for (type in ProgressionType.ALL) {
            val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.ADVANCED)
            // 检查当前题目的和弦数与该进行类型的音级数一致
            if (q.type == type) {
                assertEquals(type.degrees.size, q.chordProgression.size)
            }
        }
    }

    @Test
    fun `all MIDI notes within piano range`() {
        for (difficulty in ProgressionDifficulty.ALL) {
            for (seed in 0..20) {
                val q = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                val allNotes = q.chordProgression.flatten()
                assertTrue(
                    "MIDI 音符必须在钢琴范围 [21, 108] 内",
                    allNotes.all { it in 21..108 }
                )
            }
        }
    }

    @Test
    fun `all MIDI notes sorted ascending within chord`() {
        for (seed in 0..20) {
            val q = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(ProgressionDifficulty.ADVANCED)
            for (chord in q.chordProgression) {
                for (i in 0 until chord.size - 1) {
                    assertTrue(
                        "和弦内音符必须从低到高排列",
                        chord[i] <= chord[i + 1]
                    )
                }
            }
        }
    }

    @Test
    fun `buildProgressionMidiNotes for CLASSIC I-IV-V-I in C`() {
        // C 大调: I=C(48), IV=F(53), V=G(55), I=C(48)
        val notes = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.CLASSIC, 48
        )
        assertEquals(4, notes.size)
        // I = C major triad: C-E-G = 48-52-55
        assertEquals(listOf(48, 52, 55), notes[0])
        // IV = F major triad: F-A-C = 53-57-60
        assertEquals(listOf(53, 57, 60), notes[1])
        // V = G major triad: G-B-D = 55-59-62
        assertEquals(listOf(55, 59, 62), notes[2])
        // I = C major triad again
        assertEquals(listOf(48, 52, 55), notes[3])
    }

    @Test
    fun `buildProgressionMidiNotes for POP_ANTHEM I-V-vi-IV in C`() {
        // C 大调: I=C(48), V=G(55), vi=A(57), IV=F(53)
        val notes = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.POP_ANTHEM, 48
        )
        assertEquals(4, notes.size)
        // I = C major: C-E-G = 48-52-55
        assertEquals(listOf(48, 52, 55), notes[0])
        // V = G major: G-B-D = 55-59-62
        assertEquals(listOf(55, 59, 62), notes[1])
        // vi = A minor: A-C-E = 57-60-64
        assertEquals(listOf(57, 60, 64), notes[2])
        // IV = F major: F-A-C = 53-57-60
        assertEquals(listOf(53, 57, 60), notes[3])
    }

    @Test
    fun `buildProgressionMidiNotes for JAZZ_TURNAROUND ii-V-I in C`() {
        // C 大调: ii=Dm(50), V=G(55), I=C(48)
        val notes = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.JAZZ_TURNAROUND, 48
        )
        assertEquals(3, notes.size)
        // ii = D minor: D-F-A = 50-53-57
        assertEquals(listOf(50, 53, 57), notes[0])
        // V = G major: G-B-D = 55-59-62
        assertEquals(listOf(55, 59, 62), notes[1])
        // I = C major: C-E-G = 48-52-55
        assertEquals(listOf(48, 52, 55), notes[2])
    }

    @Test
    fun `minor degree produces minor chord`() {
        // vi in C major = A minor = A-C-E = 57-60-64 (intervals [0,3,7] from A)
        val notes = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.DOO_WOP, 48  // I-vi-IV-V
        )
        // vi = A minor: should have minor third (3 semitones between root and third)
        val viChord = notes[1]
        assertEquals(57, viChord[0])  // A
        assertEquals(60, viChord[1])  // C (57+3 = minor third)
        assertEquals(64, viChord[2])  // E
    }

    @Test
    fun `different tonics produce different MIDI notes`() {
        val cMajor = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.CLASSIC, 48  // C3
        )
        val gMajor = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.CLASSIC, 55  // G3
        )
        assertNotEquals(cMajor, gMajor)
    }

    @Test
    fun `buildProgressionMidiNotes clamps to piano range`() {
        // Extreme low tonic should still produce valid MIDI notes
        val notes = ProgressionTrainingEngine.buildProgressionMidiNotes(
            ProgressionType.CLASSIC, 21  // A0
        )
        assertTrue(notes.flatten().all { it in 21..108 })
    }

    // ── 音域范围 ────────────────────────────────────────────

    @Test
    fun `tonic is in expected range`() {
        for (seed in 0..50) {
            val q = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(ProgressionDifficulty.ADVANCED)
            assertTrue(
                "主音 MIDI 应在 C3-G3 范围内 (48-55)",
                q.tonicMidi in 48..55
            )
        }
    }

    // ── 难度配置嵌套子集 ────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginner = ProgressionType.forDifficulty(ProgressionDifficulty.BEGINNER).toSet()
        val intermediate = ProgressionType.forDifficulty(ProgressionDifficulty.INTERMEDIATE).toSet()
        assertTrue(beginner.isNotEmpty())
        assertTrue(intermediate.isNotEmpty())
        assertTrue(
            "初级进行集应是中级子集",
            intermediate.containsAll(beginner)
        )
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediate = ProgressionType.forDifficulty(ProgressionDifficulty.INTERMEDIATE).toSet()
        val advanced = ProgressionType.forDifficulty(ProgressionDifficulty.ADVANCED).toSet()
        assertTrue(
            "中级进行集应是高级子集",
            advanced.containsAll(intermediate)
        )
    }

    @Test
    fun `advanced equals ALL`() {
        val advanced = ProgressionType.forDifficulty(ProgressionDifficulty.ADVANCED).toSet()
        assertEquals(ProgressionType.ALL.toSet(), advanced)
    }

    @Test
    fun `option count increases with difficulty`() {
        val beginnerCount = ProgressionType.forDifficulty(ProgressionDifficulty.BEGINNER).size
        val intermediateCount = ProgressionType.forDifficulty(ProgressionDifficulty.INTERMEDIATE).size
        val advancedCount = ProgressionType.forDifficulty(ProgressionDifficulty.ADVANCED).size
        assertTrue(beginnerCount < intermediateCount)
        assertTrue(intermediateCount < advancedCount)
    }

    // ── 进行类型属性 ────────────────────────────────────────

    @Test
    fun `all progression types have non-empty degrees`() {
        for (type in ProgressionType.ALL) {
            assertTrue(
                "${type.name} 必须有至少 1 个音级",
                type.degrees.isNotEmpty()
            )
        }
    }

    @Test
    fun `CLASSIC has 4 degrees`() {
        assertEquals(4, ProgressionType.CLASSIC.degrees.size)
    }

    @Test
    fun `JAZZ_TURNAROUND has 3 degrees`() {
        assertEquals(3, ProgressionType.JAZZ_TURNAROUND.degrees.size)
    }

    @Test
    fun `POP_LOOP starts with vi`() {
        assertEquals(DiatonicDegree.VI, ProgressionType.POP_LOOP.degrees.first())
    }

    @Test
    fun `all progression types have descriptions`() {
        for (type in ProgressionType.ALL) {
            assertTrue(type.description.isNotBlank())
            assertTrue(type.displayName.isNotBlank())
            assertTrue(type.romanNumerals.isNotBlank())
        }
    }

    @Test
    fun `DiatonicDegree chordIntervals are correct`() {
        assertEquals(listOf(0, 4, 7), DiatonicDegree.I.chordIntervals())      // major
        assertEquals(listOf(0, 3, 7), DiatonicDegree.II.chordIntervals())     // minor
        assertEquals(listOf(0, 3, 7), DiatonicDegree.III.chordIntervals())    // minor
        assertEquals(listOf(0, 4, 7), DiatonicDegree.IV.chordIntervals())     // major
        assertEquals(listOf(0, 4, 7), DiatonicDegree.V.chordIntervals())      // major
        assertEquals(listOf(0, 3, 7), DiatonicDegree.VI.chordIntervals())     // minor
        assertEquals(listOf(0, 3, 6), DiatonicDegree.VII.chordIntervals())    // diminished
    }

    @Test
    fun `DiatonicDegree semitones are correct`() {
        assertEquals(0, DiatonicDegree.I.semitoneFromTonic)
        assertEquals(2, DiatonicDegree.II.semitoneFromTonic)
        assertEquals(4, DiatonicDegree.III.semitoneFromTonic)
        assertEquals(5, DiatonicDegree.IV.semitoneFromTonic)
        assertEquals(7, DiatonicDegree.V.semitoneFromTonic)
        assertEquals(9, DiatonicDegree.VI.semitoneFromTonic)
        assertEquals(11, DiatonicDegree.VII.semitoneFromTonic)
    }

    // ── ProgressionQuestion 属性验证 ────────────────────────

    @Test
    fun `fullDescription includes tonic name and roman numerals`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.tonicName))
        assertTrue(q.fullDescription.contains(q.type.romanNumerals))
    }

    @Test
    fun `styleDescription includes style`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        assertTrue(q.styleDescription.contains(q.type.style))
    }

    @Test
    fun `chordCount matches progression size`() {
        for (seed in 0..10) {
            val q = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(ProgressionDifficulty.ADVANCED)
            assertEquals(q.type.degrees.size, q.chordCount)
        }
    }

    @Test
    fun `empty progression throws`() {
        try {
            ProgressionQuestion(
                type = ProgressionType.CLASSIC,
                tonicMidi = 48,
                tonicName = "C",
                difficulty = ProgressionDifficulty.BEGINNER,
                chordProgression = emptyList(),
                answerChoices = listOf("A"),
                correctAnswer = "A"
            )
            assert(false) { "空和弦进行应抛出异常" }
        } catch (_: IllegalArgumentException) {
            // 预期行为
        }
    }

    @Test
    fun `wrong chord size throws`() {
        try {
            ProgressionQuestion(
                type = ProgressionType.CLASSIC,
                tonicMidi = 48,
                tonicName = "C",
                difficulty = ProgressionDifficulty.BEGINNER,
                chordProgression = listOf(listOf(48, 52)),  // only 2 notes
                answerChoices = listOf("A"),
                correctAnswer = "A"
            )
            assert(false) { "非 3 音和弦应抛出异常" }
        } catch (_: IllegalArgumentException) {
            // 预期行为
        }
    }

    @Test
    fun `out of range MIDI throws`() {
        try {
            ProgressionQuestion(
                type = ProgressionType.CLASSIC,
                tonicMidi = 48,
                tonicName = "C",
                difficulty = ProgressionDifficulty.BEGINNER,
                chordProgression = listOf(listOf(48, 52, 200)),  // 200 out of range
                answerChoices = listOf("A"),
                correctAnswer = "A"
            )
            assert(false) { "超范围 MIDI 应抛出异常" }
        } catch (_: IllegalArgumentException) {
            // 预期行为
        }
    }

    @Test
    fun `AnswerRecord correctAnswerOrNull is null when correct`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        val record = ProgressionAnswerRecord(q, q.correctAnswer, true)
        assertEquals(null, record.correctAnswerOrNull)
    }

    @Test
    fun `AnswerRecord correctAnswerOrNull is set when wrong`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = ProgressionAnswerRecord(q, wrongAnswer, false)
        assertEquals(q.correctAnswer, record.correctAnswerOrNull)
    }
}
