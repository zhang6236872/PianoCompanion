package com.pianocompanion.suspendedchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挂留和弦品质听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、音域范围、难度配置等。
 */
class SuspendedChordTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = SuspendedChordTrainingEngine.withSeed(42L)
        val e2 = SuspendedChordTrainingEngine.withSeed(42L)
        val q1 = e1.generate(SuspendedChordDifficulty.INTERMEDIATE)
        val q2 = e2.generate(SuspendedChordDifficulty.INTERMEDIATE)
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
            val q1 = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            val q2 = SuspendedChordTrainingEngine.withSeed((seed + 500).toLong()).generate(SuspendedChordDifficulty.ADVANCED)
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
        val q = SuspendedChordTrainingEngine.withSeed(1L).generate(SuspendedChordDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 4 options`() {
        val q = SuspendedChordTrainingEngine.withSeed(1L).generate(SuspendedChordDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has 5 options`() {
        val q = SuspendedChordTrainingEngine.withSeed(1L).generate(SuspendedChordDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `beginner options match expected qualities`() {
        val expected = setOf(
            SuspendedChordQuality.MAJOR_TRIAD.displayName,
            SuspendedChordQuality.SUS2.displayName,
            SuspendedChordQuality.SUS4.displayName
        )
        for (seed in 0..20) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.BEGINNER)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `intermediate options include minor`() {
        val expected = setOf(
            SuspendedChordQuality.MAJOR_TRIAD.displayName,
            SuspendedChordQuality.MINOR_TRIAD.displayName,
            SuspendedChordQuality.SUS2.displayName,
            SuspendedChordQuality.SUS4.displayName
        )
        for (seed in 0..20) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.INTERMEDIATE)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced options include all 5 qualities`() {
        val expected = SuspendedChordQuality.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── MIDI 音符正确性 ──────────────────────────────────────

    @Test
    fun `major triad has correct intervals`() {
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(
            SuspendedChordQuality.MAJOR_TRIAD, 60
        )
        assertEquals(listOf(60, 64, 67), notes)
    }

    @Test
    fun `minor triad has correct intervals`() {
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(
            SuspendedChordQuality.MINOR_TRIAD, 60
        )
        assertEquals(listOf(60, 63, 67), notes)
    }

    @Test
    fun `sus2 has correct intervals`() {
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(
            SuspendedChordQuality.SUS2, 60
        )
        assertEquals(listOf(60, 62, 67), notes)
    }

    @Test
    fun `sus4 has correct intervals`() {
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(
            SuspendedChordQuality.SUS4, 60
        )
        assertEquals(listOf(60, 65, 67), notes)
    }

    @Test
    fun `sus2sus4 has correct intervals`() {
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(
            SuspendedChordQuality.SUS2_SUS4, 60
        )
        assertEquals(listOf(60, 62, 65, 67), notes)
    }

    @Test
    fun `major triad from C4 is C E G`() {
        // C4=60, E4=64, G4=67
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(
            SuspendedChordQuality.MAJOR_TRIAD, 60
        )
        assertEquals(3, notes.size)
        assertEquals(60, notes[0]) // C
        assertEquals(64, notes[1]) // E
        assertEquals(67, notes[2]) // G
    }

    @Test
    fun `sus2 differs from major only in second note`() {
        val major = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(SuspendedChordQuality.MAJOR_TRIAD, 60)
        val sus2 = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(SuspendedChordQuality.SUS2, 60)
        assertEquals(major[0], sus2[0]) // root same
        assertEquals(2, major[1] - sus2[1]) // major 3rd is 2 semitones higher than 2nd
        assertEquals(major[2], sus2[2]) // fifth same
    }

    @Test
    fun `sus4 differs from major only in second note`() {
        val major = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(SuspendedChordQuality.MAJOR_TRIAD, 60)
        val sus4 = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(SuspendedChordQuality.SUS4, 60)
        assertEquals(major[0], sus4[0]) // root same
        assertEquals(1, sus4[1] - major[1]) // sus4 4th is 1 semitone higher than major 3rd
        assertEquals(major[2], sus4[2]) // fifth same
    }

    @Test
    fun `major differs from minor only in third`() {
        val major = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(SuspendedChordQuality.MAJOR_TRIAD, 60)
        val minor = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(SuspendedChordQuality.MINOR_TRIAD, 60)
        assertEquals(major[0], minor[0])
        assertEquals(1, major[1] - minor[1]) // major 3rd is 1 semitone higher
        assertEquals(major[2], minor[2])
    }

    @Test
    fun `sus2sus4 has 4 notes while others have 3`() {
        for (quality in SuspendedChordQuality.ALL) {
            val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(quality, 60)
            if (quality == SuspendedChordQuality.SUS2_SUS4) {
                assertEquals("${quality.displayName} 应有 4 个音", 4, notes.size)
            } else {
                assertEquals("${quality.displayName} 应有 3 个音", 3, notes.size)
            }
        }
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in 21..108)
            }
        }
    }

    @Test
    fun `root note is in C3-G3 range`() {
        for (seed in 0..200) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
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
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            val expected = expectedNames[q.rootMidi]
            assertEquals("根音名不匹配 (seed=$seed)", expected, q.rootName)
        }
    }

    // ── 音符排列 ──────────────────────────────────────────────

    @Test
    fun `midi notes are sorted ascending`() {
        for (seed in 0..100) {
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
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
            val q = SuspendedChordTrainingEngine.withSeed(seed.toLong()).generate(SuspendedChordDifficulty.ADVANCED)
            assertEquals("根音应是最低音 (seed=$seed)", q.rootMidi, q.midiNotes[0])
        }
    }

    // ── 难度配置嵌套子集 ──────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginnerQualities = SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.BEGINNER).toSet()
        val intermediateQualities = SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.INTERMEDIATE).toSet()
        assertTrue("初级品质集应是中级的子集", intermediateQualities.containsAll(beginnerQualities))
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediateQualities = SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.INTERMEDIATE).toSet()
        val advancedQualities = SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.ADVANCED).toSet()
        assertTrue("中级品质集应是高级的子集", advancedQualities.containsAll(intermediateQualities))
    }

    @Test
    fun `beginner qualities are exactly 3`() {
        assertEquals(3, SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.BEGINNER).size)
    }

    @Test
    fun `intermediate qualities are exactly 4`() {
        assertEquals(4, SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `advanced qualities are exactly 5`() {
        assertEquals(5, SuspendedChordQuality.forDifficulty(SuspendedChordDifficulty.ADVANCED).size)
    }

    // ── 品质属性 ──────────────────────────────────────────────

    @Test
    fun `openness levels are ordered 0 through 4`() {
        assertEquals(0, SuspendedChordQuality.MAJOR_TRIAD.opennessLevel)
        assertEquals(1, SuspendedChordQuality.MINOR_TRIAD.opennessLevel)
        assertEquals(2, SuspendedChordQuality.SUS2.opennessLevel)
        assertEquals(3, SuspendedChordQuality.SUS4.opennessLevel)
        assertEquals(4, SuspendedChordQuality.SUS2_SUS4.opennessLevel)
    }

    @Test
    fun `all qualities have non-empty descriptions`() {
        for (quality in SuspendedChordQuality.ALL) {
            assertTrue("${quality.displayName} 的描述不应为空", quality.description.isNotEmpty())
        }
    }

    @Test
    fun `all qualities have non-empty symbols`() {
        for (quality in SuspendedChordQuality.ALL) {
            assertTrue("${quality.displayName} 的符号不应为空", quality.symbol.isNotEmpty())
        }
    }

    @Test
    fun `qualities have distinct display names`() {
        val names = SuspendedChordQuality.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    @Test
    fun `triad qualities have 3 intervals and sus2sus4 has 4`() {
        for (quality in SuspendedChordQuality.ALL) {
            if (quality == SuspendedChordQuality.SUS2_SUS4) {
                assertEquals(4, quality.intervals.size)
            } else {
                assertEquals(3, quality.intervals.size)
            }
        }
    }

    @Test
    fun `first interval is always 0`() {
        for (quality in SuspendedChordQuality.ALL) {
            assertEquals("${quality.displayName} 的第一个音程应为 0", 0, quality.intervals[0])
        }
    }

    // ── SuspendedChordQuestion 参数校验 ───────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 2 notes throws`() {
        SuspendedChordQuestion(
            quality = SuspendedChordQuality.SUS2,
            rootMidi = 60,
            rootName = "C",
            difficulty = SuspendedChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 62),
            answerChoices = listOf("挂二和弦"),
            correctAnswer = "挂二和弦"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with 5 notes throws`() {
        SuspendedChordQuestion(
            quality = SuspendedChordQuality.SUS2_SUS4,
            rootMidi = 60,
            rootName = "C",
            difficulty = SuspendedChordDifficulty.ADVANCED,
            midiNotes = listOf(60, 62, 65, 67, 72),
            answerChoices = listOf("双挂和弦"),
            correctAnswer = "双挂和弦"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        SuspendedChordQuestion(
            quality = SuspendedChordQuality.MAJOR_TRIAD,
            rootMidi = 60,
            rootName = "C",
            difficulty = SuspendedChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 200),
            answerChoices = listOf("大三和弦"),
            correctAnswer = "大三和弦"
        )
    }

    @Test
    fun `question with 3 notes is valid`() {
        val q = SuspendedChordQuestion(
            quality = SuspendedChordQuality.MAJOR_TRIAD,
            rootMidi = 60,
            rootName = "C",
            difficulty = SuspendedChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67),
            answerChoices = listOf("大三和弦"),
            correctAnswer = "大三和弦"
        )
        assertEquals(3, q.midiNotes.size)
    }

    @Test
    fun `question with 4 notes is valid`() {
        val q = SuspendedChordQuestion(
            quality = SuspendedChordQuality.SUS2_SUS4,
            rootMidi = 60,
            rootName = "C",
            difficulty = SuspendedChordDifficulty.ADVANCED,
            midiNotes = listOf(60, 62, 65, 67),
            answerChoices = listOf("双挂和弦"),
            correctAnswer = "双挂和弦"
        )
        assertEquals(4, q.midiNotes.size)
    }

    @Test
    fun `fullDescription contains root name and quality`() {
        val q = SuspendedChordTrainingEngine.withSeed(1L).generate(SuspendedChordDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.rootName))
        assertTrue(q.fullDescription.contains(q.quality.displayName))
        assertTrue(q.fullDescription.contains(q.quality.symbol))
    }
}
