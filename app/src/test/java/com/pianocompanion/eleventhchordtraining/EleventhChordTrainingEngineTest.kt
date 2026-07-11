package com.pianocompanion.eleventhchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十一和弦色彩听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、音域范围、难度配置等。
 */
class EleventhChordTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = EleventhChordTrainingEngine.withSeed(42L)
        val e2 = EleventhChordTrainingEngine.withSeed(42L)
        val q1 = e1.generate(EleventhChordDifficulty.INTERMEDIATE)
        val q2 = e2.generate(EleventhChordDifficulty.INTERMEDIATE)
        assertEquals(q1.quality, q2.quality)
        assertEquals(q1.rootMidi, q2.rootMidi)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var foundDifferent = false
        for (seed in 0..100) {
            val q1 = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            val q2 = EleventhChordTrainingEngine.withSeed((seed + 500).toLong()).generate(EleventhChordDifficulty.ADVANCED)
            if (q1.quality != q2.quality || q1.rootMidi != q2.rootMidi) {
                foundDifferent = true
                break
            }
        }
        assertTrue("不同种子应该能产生不同题目", foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `beginner has 3 options`() {
        val q = EleventhChordTrainingEngine.withSeed(1L).generate(EleventhChordDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 4 options`() {
        val q = EleventhChordTrainingEngine.withSeed(1L).generate(EleventhChordDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has 5 options`() {
        val q = EleventhChordTrainingEngine.withSeed(1L).generate(EleventhChordDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `beginner options match expected qualities`() {
        val expected = setOf(
            EleventhChordQuality.MAJOR_11.displayName,
            EleventhChordQuality.DOMINANT_11.displayName,
            EleventhChordQuality.MINOR_11.displayName
        )
        for (seed in 0..20) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.BEGINNER)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `intermediate options include minor major`() {
        val expected = setOf(
            EleventhChordQuality.MAJOR_11.displayName,
            EleventhChordQuality.DOMINANT_11.displayName,
            EleventhChordQuality.MINOR_11.displayName,
            EleventhChordQuality.MINOR_MAJOR_11.displayName
        )
        for (seed in 0..20) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.INTERMEDIATE)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced options include all 5 qualities`() {
        val expected = EleventhChordQuality.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── MIDI 音符正确性 ──────────────────────────────────────

    @Test
    fun `major 11 has correct intervals`() {
        val notes = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(
            EleventhChordQuality.MAJOR_11, 60
        )
        assertEquals(listOf(60, 64, 67, 71, 74, 77), notes)
    }

    @Test
    fun `dominant 11 has correct intervals`() {
        val notes = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(
            EleventhChordQuality.DOMINANT_11, 60
        )
        assertEquals(listOf(60, 64, 67, 70, 74, 77), notes)
    }

    @Test
    fun `minor 11 has correct intervals`() {
        val notes = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(
            EleventhChordQuality.MINOR_11, 60
        )
        assertEquals(listOf(60, 63, 67, 70, 74, 77), notes)
    }

    @Test
    fun `minor major 11 has correct intervals`() {
        val notes = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(
            EleventhChordQuality.MINOR_MAJOR_11, 60
        )
        assertEquals(listOf(60, 63, 67, 71, 74, 77), notes)
    }

    @Test
    fun `half diminished 11 has correct intervals`() {
        val notes = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(
            EleventhChordQuality.HALF_DIMINISHED_11, 60
        )
        assertEquals(listOf(60, 63, 66, 70, 74, 77), notes)
    }

    @Test
    fun `major 11 from C4 is C E G B D F`() {
        // C4=60, E4=64, G4=67, B4=71, D5=74, F5=77
        val notes = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(
            EleventhChordQuality.MAJOR_11, 60
        )
        assertEquals(6, notes.size)
        assertEquals(60, notes[0]) // C
        assertEquals(64, notes[1]) // E
        assertEquals(67, notes[2]) // G
        assertEquals(71, notes[3]) // B
        assertEquals(74, notes[4]) // D
        assertEquals(77, notes[5]) // F
    }

    @Test
    fun `major 11 differs from dominant 11 only in seventh`() {
        val maj11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MAJOR_11, 60)
        val dom11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.DOMINANT_11, 60)
        assertEquals(maj11[0], dom11[0])
        assertEquals(maj11[1], dom11[1])
        assertEquals(maj11[2], dom11[2])
        assertEquals(1, maj11[3] - dom11[3]) // maj11 seventh is 1 semitone higher
        assertEquals(maj11[4], dom11[4])
        assertEquals(maj11[5], dom11[5])
    }

    @Test
    fun `dominant 11 differs from minor 11 only in third`() {
        val dom11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.DOMINANT_11, 60)
        val min11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MINOR_11, 60)
        assertEquals(dom11[0], min11[0])
        assertEquals(1, dom11[1] - min11[1]) // dominant third is 1 semitone higher
        assertEquals(dom11[2], min11[2])
        assertEquals(dom11[3], min11[3])
        assertEquals(dom11[4], min11[4])
        assertEquals(dom11[5], min11[5])
    }

    @Test
    fun `minor 11 differs from minor major 11 only in seventh`() {
        val min11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MINOR_11, 60)
        val mMaj11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MINOR_MAJOR_11, 60)
        assertEquals(min11[0], mMaj11[0])
        assertEquals(min11[1], mMaj11[1])
        assertEquals(min11[2], mMaj11[2])
        assertEquals(1, mMaj11[3] - min11[3]) // minor-major seventh is 1 semitone higher
        assertEquals(min11[4], mMaj11[4])
        assertEquals(min11[5], mMaj11[5])
    }

    @Test
    fun `minor 11 differs from half diminished 11 only in fifth`() {
        val min11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MINOR_11, 60)
        val halfDim11 = EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.HALF_DIMINISHED_11, 60)
        assertEquals(min11[0], halfDim11[0])
        assertEquals(min11[1], halfDim11[1])
        assertEquals(1, min11[2] - halfDim11[2]) // half-dim fifth is 1 semitone lower
        assertEquals(min11[3], halfDim11[3])
        assertEquals(min11[4], halfDim11[4])
        assertEquals(min11[5], halfDim11[5])
    }

    @Test
    fun `all qualities share the same eleventh interval`() {
        // The 11th interval (perfect 11th = 17 semitones) is the same for all qualities
        for (quality in EleventhChordQuality.ALL) {
            assertEquals(
                "${quality.displayName} 的十一音偏移应为 17",
                17,
                quality.intervals[5]
            )
        }
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in 21..108)
            }
        }
    }

    @Test
    fun `root note is in C3-G3 range`() {
        for (seed in 0..200) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            assertTrue(
                "根音 ${q.rootMidi} 应在 C3(48)-G3(55) 范围内 (seed=$seed)",
                q.rootMidi in 48..55
            )
        }
    }

    @Test
    fun `root name matches root midi`() {
        val expectedNames = mapOf(
            48 to "C", 50 to "D", 52 to "E", 53 to "F", 55 to "G"
        )
        for (seed in 0..200) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            val expected = expectedNames[q.rootMidi]
            assertEquals("根音名不匹配 (seed=$seed)", expected, q.rootName)
        }
    }

    // ── 音符数量 ──────────────────────────────────────────────

    @Test
    fun `all questions have exactly 6 midi notes`() {
        for (seed in 0..100) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            assertEquals("十一和弦应有 6 个音 (seed=$seed)", 6, q.midiNotes.size)
        }
    }

    @Test
    fun `midi notes are sorted ascending`() {
        for (seed in 0..100) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            for (i in 0 until q.midiNotes.size - 1) {
                assertTrue(
                    "音符应按升序排列 (seed=$seed, idx=$i)",
                    q.midiNotes[i] <= q.midiNotes[i + 1]
                )
            }
        }
    }

    @Test
    fun `root is lowest note`() {
        for (seed in 0..100) {
            val q = EleventhChordTrainingEngine.withSeed(seed.toLong()).generate(EleventhChordDifficulty.ADVANCED)
            assertEquals("根音应是最低音 (seed=$seed)", q.rootMidi, q.midiNotes[0])
        }
    }

    // ── 难度配置嵌套子集 ──────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginnerQualities = EleventhChordQuality.forDifficulty(EleventhChordDifficulty.BEGINNER).toSet()
        val intermediateQualities = EleventhChordQuality.forDifficulty(EleventhChordDifficulty.INTERMEDIATE).toSet()
        assertTrue("初级品质集应是中级的子集", intermediateQualities.containsAll(beginnerQualities))
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediateQualities = EleventhChordQuality.forDifficulty(EleventhChordDifficulty.INTERMEDIATE).toSet()
        val advancedQualities = EleventhChordQuality.forDifficulty(EleventhChordDifficulty.ADVANCED).toSet()
        assertTrue("中级品质集应是高级的子集", advancedQualities.containsAll(intermediateQualities))
    }

    @Test
    fun `beginner qualities are exactly 3`() {
        assertEquals(3, EleventhChordQuality.forDifficulty(EleventhChordDifficulty.BEGINNER).size)
    }

    @Test
    fun `intermediate qualities are exactly 4`() {
        assertEquals(4, EleventhChordQuality.forDifficulty(EleventhChordDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `advanced qualities are exactly 5`() {
        assertEquals(5, EleventhChordQuality.forDifficulty(EleventhChordDifficulty.ADVANCED).size)
    }

    // ── 品质属性 ──────────────────────────────────────────────

    @Test
    fun `brightness levels are ordered 0 through 4`() {
        assertEquals(0, EleventhChordQuality.MAJOR_11.brightnessLevel)
        assertEquals(1, EleventhChordQuality.DOMINANT_11.brightnessLevel)
        assertEquals(2, EleventhChordQuality.MINOR_11.brightnessLevel)
        assertEquals(3, EleventhChordQuality.MINOR_MAJOR_11.brightnessLevel)
        assertEquals(4, EleventhChordQuality.HALF_DIMINISHED_11.brightnessLevel)
    }

    @Test
    fun `all qualities have 6 intervals`() {
        for (quality in EleventhChordQuality.ALL) {
            assertEquals("${quality.displayName} 应有 6 个音程偏移", 6, quality.intervals.size)
        }
    }

    @Test
    fun `first interval is always 0`() {
        for (quality in EleventhChordQuality.ALL) {
            assertEquals("${quality.displayName} 的第一个音程应为 0", 0, quality.intervals[0])
        }
    }

    @Test
    fun `all qualities have non-empty descriptions`() {
        for (quality in EleventhChordQuality.ALL) {
            assertTrue("${quality.displayName} 的描述不应为空", quality.description.isNotEmpty())
        }
    }

    @Test
    fun `all qualities have non-empty symbols`() {
        for (quality in EleventhChordQuality.ALL) {
            assertTrue("${quality.displayName} 的符号不应为空", quality.symbol.isNotEmpty())
        }
    }

    @Test
    fun `qualities have distinct display names`() {
        val names = EleventhChordQuality.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    // ── EleventhChordQuestion 参数校验 ─────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 5 notes throws`() {
        EleventhChordQuestion(
            quality = EleventhChordQuality.MAJOR_11,
            rootMidi = 60,
            rootName = "C",
            difficulty = EleventhChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 71, 74),
            answerChoices = listOf("大十一和弦"),
            correctAnswer = "大十一和弦"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        EleventhChordQuestion(
            quality = EleventhChordQuality.MAJOR_11,
            rootMidi = 60,
            rootName = "C",
            difficulty = EleventhChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 71, 74, 200),
            answerChoices = listOf("大十一和弦"),
            correctAnswer = "大十一和弦"
        )
    }

    @Test
    fun `fullDescription contains root name and quality`() {
        val q = EleventhChordTrainingEngine.withSeed(1L).generate(EleventhChordDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.rootName))
        assertTrue(q.fullDescription.contains(q.quality.displayName))
        assertTrue(q.fullDescription.contains(q.quality.symbol))
    }
}
