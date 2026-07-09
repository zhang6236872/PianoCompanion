package com.pianocompanion.keyidentificationtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调性中心辨识训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、旋律 MIDI 正确性、音阶级数映射、
 * 大调/小调音程结构、音域范围、难度配置等。
 */
class KeyIdentificationTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = KeyIdentificationTrainingEngine.withSeed(42L)
        val e2 = KeyIdentificationTrainingEngine.withSeed(42L)
        val q1 = e1.generate(KeyDifficulty.INTERMEDIATE)
        val q2 = e2.generate(KeyDifficulty.INTERMEDIATE)
        assertEquals(q1.key, q2.key)
        assertEquals(q1.tonicMidi, q2.tonicMidi)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var foundDifferent = false
        for (seed in 0..100) {
            val q1 = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(KeyDifficulty.ADVANCED)
            val q2 = KeyIdentificationTrainingEngine.withSeed((seed + 500).toLong()).generate(KeyDifficulty.ADVANCED)
            if (q1.key != q2.key || q1.melodyPattern != q2.melodyPattern) {
                foundDifferent = true
                break
            }
        }
        assertTrue("不同种子应该能产生不同题目", foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `beginner has 3 options`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 5 options`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.INTERMEDIATE)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `advanced has 6 options`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `correct answer is always in choices`() {
        for (difficulty in KeyDifficulty.ALL) {
            for (seed in 0..20) {
                val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                assertTrue(
                    "正确答案 '${q.correctAnswer}' 必须在选项中",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `all choices are unique`() {
        for (difficulty in KeyDifficulty.ALL) {
            val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(difficulty)
            assertEquals(
                "选项不能有重复",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `choices equal difficulty key set`() {
        for (difficulty in KeyDifficulty.ALL) {
            val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(difficulty)
            val expectedNames = MusicKey.forDifficulty(difficulty).map { it.displayName }.toSet()
            assertEquals(expectedNames, q.answerChoices.toSet())
        }
    }

    // ── 旋律 MIDI 正确性 ────────────────────────────────────

    @Test
    fun `all MIDI notes within piano range`() {
        for (difficulty in KeyDifficulty.ALL) {
            for (seed in 0..20) {
                val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                assertTrue(
                    "MIDI 音符必须在钢琴范围 [21, 108] 内",
                    q.midiNotes.all { it in 21..108 }
                )
            }
        }
    }

    @Test
    fun `melody starts and ends on tonic`() {
        for (difficulty in KeyDifficulty.ALL) {
            for (seed in 0..20) {
                val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                assertEquals(
                    "旋律第一个音必须是主音",
                    q.tonicMidi,
                    q.midiNotes.first()
                )
                assertEquals(
                    "旋律最后一个音必须是主音",
                    q.tonicMidi,
                    q.midiNotes.last()
                )
            }
        }
    }

    @Test
    fun `melody note count matches pattern`() {
        for (seed in 0..20) {
            val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(KeyDifficulty.ADVANCED)
            assertEquals(
                q.melodyPattern.scaleDegrees.size,
                q.midiNotes.size
            )
        }
    }

    // ── buildMelodyMidiNotes 精确验证 ──────────────────────

    @Test
    fun `ascending scale C major`() {
        // C major scale: C-D-E-F-G-A-B-C' = 60-62-64-65-67-69-71-72
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
        )
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72, 60), notes)
    }

    @Test
    fun `ascending scale G major`() {
        // G major scale: G-A-B-C-D-E-F#-G'-G = 67-69-71-72-74-76-78-79-67
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.G_MAJOR, 67, MelodyPattern.ASCENDING_SCALE
        )
        assertEquals(listOf(67, 69, 71, 72, 74, 76, 78, 79, 67), notes)
    }

    @Test
    fun `ascending scale F major`() {
        // F major scale: F-G-A-Bb-C-D-E-F'-F = 65-67-69-70-72-74-76-77-65
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.F_MAJOR, 65, MelodyPattern.ASCENDING_SCALE
        )
        assertEquals(listOf(65, 67, 69, 70, 72, 74, 76, 77, 65), notes)
    }

    @Test
    fun `ascending scale A minor (natural)`() {
        // A natural minor: A-B-C-D-E-F-G-A'-A = 69-71-72-74-76-77-79-81-69
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.A_MINOR, 69, MelodyPattern.ASCENDING_SCALE
        )
        assertEquals(listOf(69, 71, 72, 74, 76, 77, 79, 81, 69), notes)
    }

    @Test
    fun `ascending scale D major`() {
        // D major scale: D-E-F#-G-A-B-C#-D'-D = 62-64-66-67-69-71-73-74-62
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.D_MAJOR, 62, MelodyPattern.ASCENDING_SCALE
        )
        assertEquals(listOf(62, 64, 66, 67, 69, 71, 73, 74, 62), notes)
    }

    @Test
    fun `arpeggio C major`() {
        // C major arpeggio: C-E-G-C'-G-E-C = 60-64-67-72-67-64-60
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.ARPEGGIO
        )
        assertEquals(listOf(60, 64, 67, 72, 67, 64, 60), notes)
    }

    @Test
    fun `arpeggio A minor`() {
        // A minor arpeggio: A-C-E-A'-E-C-A = 69-72-76-81-76-72-69
        // Minor 3rd = 3 semitones, perfect 5th = 7 semitones
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.A_MINOR, 69, MelodyPattern.ARPEGGIO
        )
        assertEquals(listOf(69, 72, 76, 81, 76, 72, 69), notes)
    }

    @Test
    fun `fifth pattern C major`() {
        // C major fifth pattern: do-re-mi-fa-sol-fa-mi-re-do
        // = C-D-E-F-G-F-E-D-C = 60-62-64-65-67-65-64-62-60
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.FIFTH_PATTERN
        )
        assertEquals(listOf(60, 62, 64, 65, 67, 65, 64, 62, 60), notes)
    }

    @Test
    fun `fifth pattern A minor`() {
        // A minor fifth pattern: A-B-C-D-E-D-C-B-A
        // = 69-71-72-74-76-74-72-71-69
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.A_MINOR, 69, MelodyPattern.FIFTH_PATTERN
        )
        assertEquals(listOf(69, 71, 72, 74, 76, 74, 72, 71, 69), notes)
    }

    @Test
    fun `scale up down C major`() {
        // Full scale up and down: 15 notes
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.SCALE_UP_DOWN
        )
        assertEquals(15, notes.size)
        assertEquals(60, notes.first())
        assertEquals(60, notes.last())
        // Peak at position 7 (octave) = 72
        assertEquals(72, notes[7])
    }

    @Test
    fun `minor scale has different intervals than major`() {
        val majorScale = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
        )
        val minorScale = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.A_MINOR, 69, MelodyPattern.ASCENDING_SCALE
        )
        // 3rd degree (index 2): major = +4 semitones, minor = +3 semitones
        assertEquals(4, majorScale[2] - majorScale[0])
        assertEquals(3, minorScale[2] - minorScale[0])
    }

    @Test
    fun `buildMelodyMidiNotes clamps to piano range`() {
        // Extreme low tonic should still produce valid MIDI notes
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 21, MelodyPattern.ASCENDING_SCALE
        )
        assertTrue(notes.all { it in 21..108 })
    }

    @Test
    fun `different keys produce different melody notes`() {
        val cMajor = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
        )
        val gMajor = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.G_MAJOR, 67, MelodyPattern.ASCENDING_SCALE
        )
        assertNotEquals(cMajor, gMajor)
    }

    @Test
    fun `C major and A minor share same pitch classes but different tonic`() {
        val cMajor = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
        )
        val aMinor = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.A_MINOR, 69, MelodyPattern.ASCENDING_SCALE
        )
        // C major starts on C (60), A minor starts on A (69)
        assertNotEquals(cMajor.first(), aMinor.first())
        // Both use white keys only (same pitch classes set)
        val cMajorPcs = cMajor.map { it % 12 }.toSet()
        val aMinorPcs = aMinor.map { it % 12 }.toSet()
        assertEquals(cMajorPcs, aMinorPcs)
    }

    // ── 音域范围 ────────────────────────────────────────────

    @Test
    fun `tonic is in expected range`() {
        for (seed in 0..50) {
            val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(KeyDifficulty.ADVANCED)
            assertTrue(
                "主音 MIDI 应在 C4-B4 范围内 (60-71)",
                q.tonicMidi in 60..71
            )
        }
    }

    // ── 难度配置嵌套子集 ────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginner = MusicKey.forDifficulty(KeyDifficulty.BEGINNER).toSet()
        val intermediate = MusicKey.forDifficulty(KeyDifficulty.INTERMEDIATE).toSet()
        assertTrue(beginner.isNotEmpty())
        assertTrue(intermediate.isNotEmpty())
        assertTrue(
            "初级调集应是中级子集",
            intermediate.containsAll(beginner)
        )
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediate = MusicKey.forDifficulty(KeyDifficulty.INTERMEDIATE).toSet()
        val advanced = MusicKey.forDifficulty(KeyDifficulty.ADVANCED).toSet()
        assertTrue(
            "中级调集应是高级子集",
            advanced.containsAll(intermediate)
        )
    }

    @Test
    fun `advanced includes A minor`() {
        val advanced = MusicKey.forDifficulty(KeyDifficulty.ADVANCED)
        assertTrue(
            "高级应包含 A 小调",
            advanced.contains(MusicKey.A_MINOR)
        )
    }

    @Test
    fun `beginner does not include minor`() {
        val beginner = MusicKey.forDifficulty(KeyDifficulty.BEGINNER)
        assertTrue(
            "初级不应包含小调",
            beginner.none { it.category == KeyCategory.MINOR }
        )
    }

    @Test
    fun `option count increases with difficulty`() {
        val beginnerCount = MusicKey.forDifficulty(KeyDifficulty.BEGINNER).size
        val intermediateCount = MusicKey.forDifficulty(KeyDifficulty.INTERMEDIATE).size
        val advancedCount = MusicKey.forDifficulty(KeyDifficulty.ADVANCED).size
        assertTrue(beginnerCount < intermediateCount)
        assertTrue(intermediateCount < advancedCount)
    }

    // ── MusicKey / MelodyPattern 属性验证 ────────────────────

    @Test
    fun `all keys have non-blank display names`() {
        for (key in MusicKey.ALL) {
            assertTrue(key.displayName.isNotBlank())
            assertTrue(key.keySignature.isNotBlank())
            assertTrue(key.description.isNotBlank())
        }
    }

    @Test
    fun `major scale intervals are correct`() {
        for (key in MusicKey.ALL) {
            if (key.category == KeyCategory.MAJOR) {
                assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), key.scaleIntervals())
            }
        }
    }

    @Test
    fun `minor scale intervals are correct`() {
        for (key in MusicKey.ALL) {
            if (key.category == KeyCategory.MINOR) {
                assertEquals(listOf(0, 2, 3, 5, 7, 8, 10), key.scaleIntervals())
            }
        }
    }

    @Test
    fun `all melody patterns start and end on tonic degree`() {
        for (pattern in MelodyPattern.ALL) {
            assertEquals(
                "${pattern.name} 必须从主音(degree 0)开始",
                0,
                pattern.scaleDegrees.first()
            )
            assertEquals(
                "${pattern.name} 必须以主音(degree 0)结束",
                0,
                pattern.scaleDegrees.last()
            )
        }
    }

    @Test
    fun `all melody pattern degrees are valid`() {
        for (pattern in MelodyPattern.ALL) {
            assertTrue(
                "${pattern.name} 的级数必须在 0-7 范围内",
                pattern.scaleDegrees.all { it in 0..7 }
            )
        }
    }

    @Test
    fun `ASCENDING_SCALE has 9 notes`() {
        assertEquals(9, MelodyPattern.ASCENDING_SCALE.scaleDegrees.size)
    }

    @Test
    fun `SCALE_UP_DOWN is the longest pattern`() {
        val maxLength = MelodyPattern.ALL.maxOf { it.scaleDegrees.size }
        assertEquals(maxLength, MelodyPattern.SCALE_UP_DOWN.scaleDegrees.size)
    }

    @Test
    fun `all melody patterns have display names`() {
        for (pattern in MelodyPattern.ALL) {
            assertTrue(pattern.displayName.isNotBlank())
        }
    }

    // ── KeyQuestion 属性验证 ────────────────────────────────

    @Test
    fun `fullDescription includes key name and pattern`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.key.displayName))
        assertTrue(q.fullDescription.contains(q.melodyPattern.displayName))
    }

    @Test
    fun `keySignatureDescription includes key signature`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.BEGINNER)
        assertTrue(q.keySignatureDescription.contains(q.key.keySignature))
    }

    @Test
    fun `noteCount matches midiNotes size`() {
        for (seed in 0..10) {
            val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(KeyDifficulty.ADVANCED)
            assertEquals(q.midiNotes.size, q.noteCount)
        }
    }

    @Test
    fun `empty melody throws`() {
        try {
            KeyQuestion(
                key = MusicKey.C_MAJOR,
                tonicMidi = 60,
                tonicName = "C",
                melodyPattern = MelodyPattern.ASCENDING_SCALE,
                midiNotes = emptyList(),
                difficulty = KeyDifficulty.BEGINNER,
                answerChoices = listOf("A"),
                correctAnswer = "A"
            )
            assert(false) { "空旋律应抛出异常" }
        } catch (_: IllegalArgumentException) {
            // 预期行为
        }
    }

    @Test
    fun `out of range MIDI throws`() {
        try {
            KeyQuestion(
                key = MusicKey.C_MAJOR,
                tonicMidi = 60,
                tonicName = "C",
                melodyPattern = MelodyPattern.ASCENDING_SCALE,
                midiNotes = listOf(60, 62, 200),  // 200 out of range
                difficulty = KeyDifficulty.BEGINNER,
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
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.BEGINNER)
        val record = KeyAnswerRecord(q, q.correctAnswer, true)
        assertEquals(null, record.correctAnswerOrNull)
    }

    @Test
    fun `AnswerRecord correctAnswerOrNull is set when wrong`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.BEGINNER)
        val wrongAnswer = q.answerChoices.first { it != q.correctAnswer }
        val record = KeyAnswerRecord(q, wrongAnswer, false)
        assertEquals(q.correctAnswer, record.correctAnswerOrNull)
    }

    // ── tonicName 正确性 ────────────────────────────────────

    @Test
    fun `tonicName matches key pitch class`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.ADVANCED)
        // C_MAJOR should have tonicName "C", etc.
        when (q.key) {
            MusicKey.C_MAJOR -> assertEquals("C", q.tonicName)
            MusicKey.G_MAJOR -> assertEquals("G", q.tonicName)
            MusicKey.D_MAJOR -> assertEquals("D", q.tonicName)
            MusicKey.F_MAJOR -> assertEquals("F", q.tonicName)
            MusicKey.B_FLAT_MAJOR -> assertTrue(q.tonicName.contains("A") || q.tonicName.contains("B"))
            MusicKey.A_MINOR -> assertEquals("A", q.tonicName)
        }
    }
}
