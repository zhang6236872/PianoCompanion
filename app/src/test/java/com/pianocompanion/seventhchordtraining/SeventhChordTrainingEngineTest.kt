package com.pianocompanion.seventhchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 七和弦品质听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、音域范围、难度配置等。
 */
class SeventhChordTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = SeventhChordTrainingEngine.withSeed(42L)
        val e2 = SeventhChordTrainingEngine.withSeed(42L)
        val q1 = e1.generate(SeventhChordDifficulty.INTERMEDIATE)
        val q2 = e2.generate(SeventhChordDifficulty.INTERMEDIATE)
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
            val q1 = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            val q2 = SeventhChordTrainingEngine.withSeed((seed + 500).toLong()).generate(SeventhChordDifficulty.ADVANCED)
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
        val q = SeventhChordTrainingEngine.withSeed(1L).generate(SeventhChordDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 4 options`() {
        val q = SeventhChordTrainingEngine.withSeed(1L).generate(SeventhChordDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has 5 options`() {
        val q = SeventhChordTrainingEngine.withSeed(1L).generate(SeventhChordDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `beginner options match expected qualities`() {
        val expected = setOf(
            SeventhChordQuality.MAJOR_7.displayName,
            SeventhChordQuality.DOMINANT_7.displayName,
            SeventhChordQuality.MINOR_7.displayName
        )
        for (seed in 0..20) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.BEGINNER)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `intermediate options include half diminished`() {
        val expected = setOf(
            SeventhChordQuality.MAJOR_7.displayName,
            SeventhChordQuality.DOMINANT_7.displayName,
            SeventhChordQuality.MINOR_7.displayName,
            SeventhChordQuality.HALF_DIMINISHED_7.displayName
        )
        for (seed in 0..20) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.INTERMEDIATE)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced options include all 5 qualities`() {
        val expected = SeventhChordQuality.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── MIDI 音符正确性 ──────────────────────────────────────

    @Test
    fun `major 7 has correct intervals`() {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.MAJOR_7, 60
        )
        assertEquals(listOf(60, 64, 67, 71), notes)
    }

    @Test
    fun `dominant 7 has correct intervals`() {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.DOMINANT_7, 60
        )
        assertEquals(listOf(60, 64, 67, 70), notes)
    }

    @Test
    fun `minor 7 has correct intervals`() {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.MINOR_7, 60
        )
        assertEquals(listOf(60, 63, 67, 70), notes)
    }

    @Test
    fun `half diminished 7 has correct intervals`() {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.HALF_DIMINISHED_7, 60
        )
        assertEquals(listOf(60, 63, 66, 70), notes)
    }

    @Test
    fun `diminished 7 has correct intervals`() {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.DIMINISHED_7, 60
        )
        assertEquals(listOf(60, 63, 66, 69), notes)
    }

    @Test
    fun `major 7 from C4 is C E G B`() {
        // C4=60, E4=64, G4=67, B4=71
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.MAJOR_7, 60
        )
        assertEquals(4, notes.size)
        assertEquals(60, notes[0]) // C
        assertEquals(64, notes[1]) // E
        assertEquals(67, notes[2]) // G
        assertEquals(71, notes[3]) // B
    }

    @Test
    fun `dominant 7 differs from major 7 only in seventh`() {
        val maj7 = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.MAJOR_7, 60)
        val dom7 = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.DOMINANT_7, 60)
        assertEquals(maj7[0], dom7[0])
        assertEquals(maj7[1], dom7[1])
        assertEquals(maj7[2], dom7[2])
        assertEquals(1, maj7[3] - dom7[3]) // maj7 is 1 semitone higher
    }

    @Test
    fun `minor 7 differs from dominant 7 only in third`() {
        val dom7 = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.DOMINANT_7, 60)
        val min7 = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.MINOR_7, 60)
        assertEquals(dom7[0], min7[0])
        assertEquals(1, dom7[1] - min7[1]) // dominant third is 1 semitone higher
        assertEquals(dom7[2], min7[2])
        assertEquals(dom7[3], min7[3])
    }

    @Test
    fun `half diminished differs from minor 7 only in fifth`() {
        val min7 = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.MINOR_7, 60)
        val halfDim = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.HALF_DIMINISHED_7, 60)
        assertEquals(min7[0], halfDim[0])
        assertEquals(min7[1], halfDim[1])
        assertEquals(1, min7[2] - halfDim[2]) // minor fifth is 1 semitone higher
        assertEquals(min7[3], halfDim[3])
    }

    @Test
    fun `diminished differs from half diminished only in seventh`() {
        val halfDim = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.HALF_DIMINISHED_7, 60)
        val dim7 = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(SeventhChordQuality.DIMINISHED_7, 60)
        assertEquals(halfDim[0], dim7[0])
        assertEquals(halfDim[1], dim7[1])
        assertEquals(halfDim[2], dim7[2])
        assertEquals(1, halfDim[3] - dim7[3]) // half-dim seventh is 1 semitone higher
    }

    @Test
    fun `diminished 7 has equal minor third spacing`() {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(
            SeventhChordQuality.DIMINISHED_7, 60
        )
        assertEquals(3, notes[1] - notes[0])
        assertEquals(3, notes[2] - notes[1])
        assertEquals(3, notes[3] - notes[2])
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in 21..108)
            }
        }
    }

    @Test
    fun `root note is in C3-G3 range`() {
        for (seed in 0..200) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
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
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            val expected = expectedNames[q.rootMidi]
            assertEquals("根音名不匹配 (seed=$seed)", expected, q.rootName)
        }
    }

    // ── 音符数量 ──────────────────────────────────────────────

    @Test
    fun `all questions have exactly 4 midi notes`() {
        for (seed in 0..100) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            assertEquals("七和弦应有 4 个音 (seed=$seed)", 4, q.midiNotes.size)
        }
    }

    @Test
    fun `midi notes are sorted ascending`() {
        for (seed in 0..100) {
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
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
            val q = SeventhChordTrainingEngine.withSeed(seed.toLong()).generate(SeventhChordDifficulty.ADVANCED)
            assertEquals("根音应是最低音 (seed=$seed)", q.rootMidi, q.midiNotes[0])
        }
    }

    // ── 难度配置嵌套子集 ──────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginnerQualities = SeventhChordQuality.forDifficulty(SeventhChordDifficulty.BEGINNER).toSet()
        val intermediateQualities = SeventhChordQuality.forDifficulty(SeventhChordDifficulty.INTERMEDIATE).toSet()
        assertTrue("初级品质集应是中级的子集", intermediateQualities.containsAll(beginnerQualities))
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediateQualities = SeventhChordQuality.forDifficulty(SeventhChordDifficulty.INTERMEDIATE).toSet()
        val advancedQualities = SeventhChordQuality.forDifficulty(SeventhChordDifficulty.ADVANCED).toSet()
        assertTrue("中级品质集应是高级的子集", advancedQualities.containsAll(intermediateQualities))
    }

    @Test
    fun `beginner qualities are exactly 3`() {
        assertEquals(3, SeventhChordQuality.forDifficulty(SeventhChordDifficulty.BEGINNER).size)
    }

    @Test
    fun `intermediate qualities are exactly 4`() {
        assertEquals(4, SeventhChordQuality.forDifficulty(SeventhChordDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `advanced qualities are exactly 5`() {
        assertEquals(5, SeventhChordQuality.forDifficulty(SeventhChordDifficulty.ADVANCED).size)
    }

    // ── 品质属性 ──────────────────────────────────────────────

    @Test
    fun `tension levels are ordered 0 through 4`() {
        assertEquals(0, SeventhChordQuality.MAJOR_7.tensionLevel)
        assertEquals(1, SeventhChordQuality.DOMINANT_7.tensionLevel)
        assertEquals(2, SeventhChordQuality.MINOR_7.tensionLevel)
        assertEquals(3, SeventhChordQuality.HALF_DIMINISHED_7.tensionLevel)
        assertEquals(4, SeventhChordQuality.DIMINISHED_7.tensionLevel)
    }

    @Test
    fun `all qualities have 4 intervals`() {
        for (quality in SeventhChordQuality.ALL) {
            assertEquals("${quality.displayName} 应有 4 个音程偏移", 4, quality.intervals.size)
        }
    }

    @Test
    fun `first interval is always 0`() {
        for (quality in SeventhChordQuality.ALL) {
            assertEquals("${quality.displayName} 的第一个音程应为 0", 0, quality.intervals[0])
        }
    }

    @Test
    fun `all qualities have non-empty descriptions`() {
        for (quality in SeventhChordQuality.ALL) {
            assertTrue("${quality.displayName} 的描述不应为空", quality.description.isNotEmpty())
        }
    }

    @Test
    fun `all qualities have non-empty symbols`() {
        for (quality in SeventhChordQuality.ALL) {
            assertTrue("${quality.displayName} 的符号不应为空", quality.symbol.isNotEmpty())
        }
    }

    @Test
    fun `qualities have distinct display names`() {
        val names = SeventhChordQuality.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    // ── SeventhChordQuestion 参数校验 ─────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 3 notes throws`() {
        SeventhChordQuestion(
            quality = SeventhChordQuality.MAJOR_7,
            rootMidi = 60,
            rootName = "C",
            difficulty = SeventhChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67),
            answerChoices = listOf("大七和弦"),
            correctAnswer = "大七和弦"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        SeventhChordQuestion(
            quality = SeventhChordQuality.MAJOR_7,
            rootMidi = 60,
            rootName = "C",
            difficulty = SeventhChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 200),
            answerChoices = listOf("大七和弦"),
            correctAnswer = "大七和弦"
        )
    }

    @Test
    fun `fullDescription contains root name and quality`() {
        val q = SeventhChordTrainingEngine.withSeed(1L).generate(SeventhChordDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.rootName))
        assertTrue(q.fullDescription.contains(q.quality.displayName))
        assertTrue(q.fullDescription.contains(q.quality.symbol))
    }
}
