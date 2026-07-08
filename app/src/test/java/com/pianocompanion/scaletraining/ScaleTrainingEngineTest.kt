package com.pianocompanion.scaletraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音阶听辨训练出题引擎单元测试。
 */
class ScaleTrainingEngineTest {

    // ── 确定性测试 ──────────────────────────────────────────

    @Test
    fun `withSeed produces deterministic questions`() {
        val engine1 = ScaleTrainingEngine.withSeed(42L)
        val engine2 = ScaleTrainingEngine.withSeed(42L)

        val q1 = engine1.generate(ScaleDifficulty.BEGINNER)
        val q2 = engine2.generate(ScaleDifficulty.BEGINNER)

        assertEquals(q1.type, q2.type)
        assertEquals(q1.tonicMidi, q2.tonicMidi)
        assertEquals(q1.direction, q2.direction)
        assertEquals(q1.midiNotes, q2.midiNotes)
    }

    @Test
    fun `same seed produces same question across difficulties`() {
        val engine1 = ScaleTrainingEngine.withSeed(100L)
        val engine2 = ScaleTrainingEngine.withSeed(100L)

        for (difficulty in ScaleDifficulty.ALL) {
            val q1 = engine1.generate(difficulty)
            val q2 = engine2.generate(difficulty)
            assertEquals("Difficulty $difficulty: type mismatch", q1.type, q2.type)
            assertEquals("Difficulty $difficulty: midiNotes mismatch", q1.midiNotes, q2.midiNotes)
        }
    }

    @Test
    fun `different seeds produce different questions (probabilistic)`() {
        val engine1 = ScaleTrainingEngine.withSeed(1L)
        val engine2 = ScaleTrainingEngine.withSeed(99999L)

        var anyDifferent = false
        repeat(20) {
            val q1 = engine1.generate(ScaleDifficulty.ADVANCED)
            val q2 = engine2.generate(ScaleDifficulty.ADVANCED)
            if (q1.type != q2.type || q1.tonicMidi != q2.tonicMidi) {
                anyDifferent = true
            }
        }
        assertTrue("Different seeds should produce different questions", anyDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `answer choices contain correct answer`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue(
                "Correct answer '${q.correctAnswer}' must be in choices ${q.answerChoices}",
                q.answerChoices.contains(q.correctAnswer)
            )
        }
    }

    @Test
    fun `answer choices are unique`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(
                "Choices should be unique",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `beginner has exactly 2 choices`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `intermediate has exactly 4 choices`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has exactly 6 choices`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `beginner choices equal the beginner scale types`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.BEGINNER)
        val expectedNames = ScaleType.forDifficulty(ScaleDifficulty.BEGINNER).map { it.displayName }.toSet()
        assertEquals(expectedNames, q.answerChoices.toSet())
    }

    @Test
    fun `intermediate choices equal the intermediate scale types`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.INTERMEDIATE)
        val expectedNames = ScaleType.forDifficulty(ScaleDifficulty.INTERMEDIATE).map { it.displayName }.toSet()
        assertEquals(expectedNames, q.answerChoices.toSet())
    }

    @Test
    fun `advanced choices equal all scale types`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.ADVANCED)
        val expectedNames = ScaleType.ALL.map { it.displayName }.toSet()
        assertEquals(expectedNames, q.answerChoices.toSet())
    }

    // ── 音阶 MIDI 音符正确性 ──────────────────────────────

    @Test
    fun `ascending scale starts at tonic`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, tonicMidi, ScaleDirection.ASCENDING)
        assertEquals(tonicMidi, notes.first())
    }

    @Test
    fun `ascending scale ends at octave`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, tonicMidi, ScaleDirection.ASCENDING)
        assertEquals(tonicMidi + 12, notes.last())
    }

    @Test
    fun `descending scale starts at octave and ends at tonic`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, tonicMidi, ScaleDirection.DESCENDING)
        assertEquals(tonicMidi + 12, notes.first())
        assertEquals(tonicMidi, notes.last())
    }

    @Test
    fun `descending scale is reverse of ascending`() {
        val tonicMidi = 55 // G3
        for (type in ScaleType.ALL) {
            val ascending = ScaleTrainingEngine.buildScaleMidiNotes(type, tonicMidi, ScaleDirection.ASCENDING)
            val descending = ScaleTrainingEngine.buildScaleMidiNotes(type, tonicMidi, ScaleDirection.DESCENDING)
            assertEquals("Type $type", ascending.reversed(), descending)
        }
    }

    @Test
    fun `major scale intervals are correct`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, tonicMidi, ScaleDirection.ASCENDING)
        val expected = listOf(60, 62, 64, 65, 67, 69, 71, 72)
        assertEquals(expected, notes)
    }

    @Test
    fun `natural minor scale intervals are correct`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.NATURAL_MINOR, tonicMidi, ScaleDirection.ASCENDING)
        val expected = listOf(60, 62, 63, 65, 67, 68, 70, 72)
        assertEquals(expected, notes)
    }

    @Test
    fun `harmonic minor scale intervals are correct`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.HARMONIC_MINOR, tonicMidi, ScaleDirection.ASCENDING)
        val expected = listOf(60, 62, 63, 65, 67, 68, 71, 72)
        assertEquals(expected, notes)
    }

    @Test
    fun `melodic minor scale intervals are correct`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MELODIC_MINOR, tonicMidi, ScaleDirection.ASCENDING)
        val expected = listOf(60, 62, 63, 65, 67, 69, 71, 72)
        assertEquals(expected, notes)
    }

    @Test
    fun `major pentatonic scale intervals are correct`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR_PENTATONIC, tonicMidi, ScaleDirection.ASCENDING)
        val expected = listOf(60, 62, 64, 67, 69, 72)
        assertEquals(expected, notes)
    }

    @Test
    fun `minor pentatonic scale intervals are correct`() {
        val tonicMidi = 60 // C4
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MINOR_PENTATONIC, tonicMidi, ScaleDirection.ASCENDING)
        val expected = listOf(60, 63, 65, 67, 70, 72)
        assertEquals(expected, notes)
    }

    @Test
    fun `diatonic scales have 8 notes (7 + octave)`() {
        for (type in listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR)) {
            val notes = ScaleTrainingEngine.buildScaleMidiNotes(type, 60, ScaleDirection.ASCENDING)
            assertEquals("${type.displayName} should have 8 notes", 8, notes.size)
        }
    }

    @Test
    fun `pentatonic scales have 6 notes (5 + octave)`() {
        for (type in listOf(ScaleType.MAJOR_PENTATONIC, ScaleType.MINOR_PENTATONIC)) {
            val notes = ScaleTrainingEngine.buildScaleMidiNotes(type, 60, ScaleDirection.ASCENDING)
            assertEquals("${type.displayName} should have 6 notes", 6, notes.size)
        }
    }

    // ── 音阶区分性（不同音阶必须有不同的 MIDI 音符） ──────

    @Test
    fun `major and natural minor differ`() {
        val tonic = 60
        val major = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, tonic, ScaleDirection.ASCENDING)
        val minor = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.NATURAL_MINOR, tonic, ScaleDirection.ASCENDING)
        assertNotEquals(major, minor)
    }

    @Test
    fun `natural minor and harmonic minor differ`() {
        val tonic = 60
        val natMinor = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.NATURAL_MINOR, tonic, ScaleDirection.ASCENDING)
        val harmMinor = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.HARMONIC_MINOR, tonic, ScaleDirection.ASCENDING)
        assertNotEquals(natMinor, harmMinor)
    }

    @Test
    fun `harmonic minor and melodic minor differ`() {
        val tonic = 60
        val harmMinor = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.HARMONIC_MINOR, tonic, ScaleDirection.ASCENDING)
        val meloMinor = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MELODIC_MINOR, tonic, ScaleDirection.ASCENDING)
        assertNotEquals(harmMinor, meloMinor)
    }

    @Test
    fun `major pentatonic and minor pentatonic differ`() {
        val tonic = 60
        val majPenta = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR_PENTATONIC, tonic, ScaleDirection.ASCENDING)
        val minPenta = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MINOR_PENTATONIC, tonic, ScaleDirection.ASCENDING)
        assertNotEquals(majPenta, minPenta)
    }

    @Test
    fun `melodic minor ascending differs from major`() {
        val tonic = 60
        val meloMinor = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MELODIC_MINOR, tonic, ScaleDirection.ASCENDING)
        val major = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, tonic, ScaleDirection.ASCENDING)
        assertNotEquals(meloMinor, major)
    }

    // ── MIDI 音域范围 ──────────────────────────────────────

    @Test
    fun `all generated notes are within piano range`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            repeat(50) {
                val q = engine.generate(difficulty)
                for (note in q.midiNotes) {
                    assertTrue("MIDI note $note below piano range (min=${ScaleTrainingEngine.MIN_MIDI})", note >= ScaleTrainingEngine.MIN_MIDI)
                    assertTrue("MIDI note $note above piano range (max=${ScaleTrainingEngine.MAX_MIDI})", note <= ScaleTrainingEngine.MAX_MIDI)
                }
            }
        }
    }

    @Test
    fun `tonic midi is within C3-G3 range`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            repeat(50) {
                val q = engine.generate(difficulty)
                assertTrue("Tonic ${q.tonicMidi} below C3 (48)", q.tonicMidi >= 48)
                assertTrue("Tonic ${q.tonicMidi} above G3 (55)", q.tonicMidi <= 55)
            }
        }
    }

    // ── 难度配置正确性 ────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginner = ScaleType.forDifficulty(ScaleDifficulty.BEGINNER).toSet()
        val intermediate = ScaleType.forDifficulty(ScaleDifficulty.INTERMEDIATE).toSet()
        assertTrue("Beginner should be subset of intermediate", intermediate.containsAll(beginner))
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediate = ScaleType.forDifficulty(ScaleDifficulty.INTERMEDIATE).toSet()
        val advanced = ScaleType.forDifficulty(ScaleDifficulty.ADVANCED).toSet()
        assertTrue("Intermediate should be subset of advanced", advanced.containsAll(intermediate))
    }

    @Test
    fun `advanced equals all scale types`() {
        val advanced = ScaleType.forDifficulty(ScaleDifficulty.ADVANCED)
        assertEquals(ScaleType.ALL, advanced)
    }

    // ── ScaleType 属性 ────────────────────────────────────

    @Test
    fun `all scale types have non-empty intervals`() {
        for (type in ScaleType.ALL) {
            assertTrue("${type.displayName} intervals should be non-empty", type.intervals.isNotEmpty())
        }
    }

    @Test
    fun `all scale types start at 0`() {
        for (type in ScaleType.ALL) {
            assertEquals("${type.displayName} should start at interval 0", 0, type.intervals.first())
        }
    }

    @Test
    fun `all scale types end at 12 (octave)`() {
        for (type in ScaleType.ALL) {
            assertEquals("${type.displayName} should end at octave (12)", 12, type.intervals.last())
        }
    }

    @Test
    fun `all scale types have non-blank display names`() {
        for (type in ScaleType.ALL) {
            assertTrue("${type} displayName should be non-blank", type.displayName.isNotBlank())
        }
    }

    @Test
    fun `all scale types have non-blank color descriptions`() {
        for (type in ScaleType.ALL) {
            assertTrue("${type} colorDescription should be non-blank", type.colorDescription.isNotBlank())
        }
    }

    @Test
    fun `noteCount excludes octave`() {
        for (type in ScaleType.ALL) {
            assertEquals(
                "${type.displayName} noteCount should be intervals.size - 1",
                type.intervals.size - 1,
                type.noteCount
            )
        }
    }

    @Test
    fun `diatonic scales have 7 note count`() {
        for (type in listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR)) {
            assertEquals("${type.displayName} should have noteCount 7", 7, type.noteCount)
        }
    }

    @Test
    fun `pentatonic scales have 5 note count`() {
        for (type in listOf(ScaleType.MAJOR_PENTATONIC, ScaleType.MINOR_PENTATONIC)) {
            assertEquals("${type.displayName} should have noteCount 5", 5, type.noteCount)
        }
    }

    // ── ScaleDirection ────────────────────────────────────

    @Test
    fun `ascending and descending have different display names`() {
        assertNotEquals(ScaleDirection.ASCENDING.displayName, ScaleDirection.DESCENDING.displayName)
    }

    // ── 题目属性 ──────────────────────────────────────────

    @Test
    fun `question difficulty matches requested`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    @Test
    fun `question tonic name is non-blank`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.BEGINNER)
        assertTrue(q.tonicName.isNotBlank())
    }

    @Test
    fun `question fullDescription is non-blank`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.BEGINNER)
        assertTrue(q.fullDescription.isNotBlank())
    }

    @Test
    fun `question intervalPattern is non-blank`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.BEGINNER)
        assertTrue(q.intervalPattern.isNotBlank())
    }

    // ── BASE_OCTAVE_MIDI 常量 ─────────────────────────────

    @Test
    fun `base octave midi is C3 (48)`() {
        assertEquals(48, ScaleTrainingEngine.BASE_OCTAVE_MIDI)
    }

    @Test
    fun `MIN_MIDI is 21 and MAX_MIDI is 108`() {
        assertEquals(21, ScaleTrainingEngine.MIN_MIDI)
        assertEquals(108, ScaleTrainingEngine.MAX_MIDI)
    }

    // ── ScaleQuestion validation ──────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `empty midiNotes throws`() {
        ScaleQuestion(
            type = ScaleType.MAJOR,
            tonicMidi = 60,
            tonicName = "C",
            difficulty = ScaleDifficulty.BEGINNER,
            direction = ScaleDirection.ASCENDING,
            midiNotes = emptyList(),
            answerChoices = listOf("大调音阶", "自然小调"),
            correctAnswer = "大调音阶"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range midi throws`() {
        ScaleQuestion(
            type = ScaleType.MAJOR,
            tonicMidi = 60,
            tonicName = "C",
            difficulty = ScaleDifficulty.BEGINNER,
            direction = ScaleDirection.ASCENDING,
            midiNotes = listOf(0, 200),
            answerChoices = listOf("大调音阶", "自然小调"),
            correctAnswer = "大调音阶"
        )
    }
}
