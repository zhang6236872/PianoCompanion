package com.pianocompanion.ninthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 九和弦色彩听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、音域范围、难度配置等。
 */
class NinthChordTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = NinthChordTrainingEngine.withSeed(42L)
        val e2 = NinthChordTrainingEngine.withSeed(42L)
        val q1 = e1.generate(NinthChordDifficulty.INTERMEDIATE)
        val q2 = e2.generate(NinthChordDifficulty.INTERMEDIATE)
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
            val q1 = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            val q2 = NinthChordTrainingEngine.withSeed((seed + 500).toLong()).generate(NinthChordDifficulty.ADVANCED)
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
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 4 options`() {
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has 5 options`() {
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `beginner options match expected qualities`() {
        val expected = setOf(
            NinthChordQuality.MAJOR_9.displayName,
            NinthChordQuality.DOMINANT_9.displayName,
            NinthChordQuality.MINOR_9.displayName
        )
        for (seed in 0..20) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.BEGINNER)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `intermediate options include minor major`() {
        val expected = setOf(
            NinthChordQuality.MAJOR_9.displayName,
            NinthChordQuality.DOMINANT_9.displayName,
            NinthChordQuality.MINOR_9.displayName,
            NinthChordQuality.MINOR_MAJOR_9.displayName
        )
        for (seed in 0..20) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.INTERMEDIATE)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced options include all 5 qualities`() {
        val expected = NinthChordQuality.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── MIDI 音符正确性 ──────────────────────────────────────

    @Test
    fun `major 9 has correct intervals`() {
        val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(
            NinthChordQuality.MAJOR_9, 60
        )
        assertEquals(listOf(60, 64, 67, 71, 74), notes)
    }

    @Test
    fun `dominant 9 has correct intervals`() {
        val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(
            NinthChordQuality.DOMINANT_9, 60
        )
        assertEquals(listOf(60, 64, 67, 70, 74), notes)
    }

    @Test
    fun `minor 9 has correct intervals`() {
        val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(
            NinthChordQuality.MINOR_9, 60
        )
        assertEquals(listOf(60, 63, 67, 70, 74), notes)
    }

    @Test
    fun `minor major 9 has correct intervals`() {
        val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(
            NinthChordQuality.MINOR_MAJOR_9, 60
        )
        assertEquals(listOf(60, 63, 67, 71, 74), notes)
    }

    @Test
    fun `dominant 7 flat 9 has correct intervals`() {
        val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(
            NinthChordQuality.DOMINANT_7_FLAT_9, 60
        )
        assertEquals(listOf(60, 64, 67, 70, 73), notes)
    }

    @Test
    fun `major 9 from C4 is C E G B D`() {
        // C4=60, E4=64, G4=67, B4=71, D5=74
        val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(
            NinthChordQuality.MAJOR_9, 60
        )
        assertEquals(5, notes.size)
        assertEquals(60, notes[0]) // C
        assertEquals(64, notes[1]) // E
        assertEquals(67, notes[2]) // G
        assertEquals(71, notes[3]) // B
        assertEquals(74, notes[4]) // D
    }

    @Test
    fun `major 9 differs from dominant 9 only in seventh`() {
        val maj9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.MAJOR_9, 60)
        val dom9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.DOMINANT_9, 60)
        assertEquals(maj9[0], dom9[0])
        assertEquals(maj9[1], dom9[1])
        assertEquals(maj9[2], dom9[2])
        assertEquals(1, maj9[3] - dom9[3]) // maj9 seventh is 1 semitone higher
        assertEquals(maj9[4], dom9[4])
    }

    @Test
    fun `dominant 9 differs from minor 9 only in third`() {
        val dom9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.DOMINANT_9, 60)
        val min9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.MINOR_9, 60)
        assertEquals(dom9[0], min9[0])
        assertEquals(1, dom9[1] - min9[1]) // dominant third is 1 semitone higher
        assertEquals(dom9[2], min9[2])
        assertEquals(dom9[3], min9[3])
        assertEquals(dom9[4], min9[4])
    }

    @Test
    fun `minor 9 differs from minor major 9 only in seventh`() {
        val min9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.MINOR_9, 60)
        val mMaj9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.MINOR_MAJOR_9, 60)
        assertEquals(min9[0], mMaj9[0])
        assertEquals(min9[1], mMaj9[1])
        assertEquals(min9[2], mMaj9[2])
        assertEquals(1, mMaj9[3] - min9[3]) // minor-major seventh is 1 semitone higher
        assertEquals(min9[4], mMaj9[4])
    }

    @Test
    fun `dominant 9 differs from dominant 7 flat 9 only in ninth`() {
        val dom9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.DOMINANT_9, 60)
        val dom7b9 = NinthChordTrainingEngine.buildNinthChordMidiNotes(NinthChordQuality.DOMINANT_7_FLAT_9, 60)
        assertEquals(dom9[0], dom7b9[0])
        assertEquals(dom9[1], dom7b9[1])
        assertEquals(dom9[2], dom7b9[2])
        assertEquals(dom9[3], dom7b9[3])
        assertEquals(1, dom9[4] - dom7b9[4]) // dom9 ninth is 1 semitone higher
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in 21..108)
            }
        }
    }

    @Test
    fun `root note is in C3-G3 range`() {
        for (seed in 0..200) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
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
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            val expected = expectedNames[q.rootMidi]
            assertEquals("根音名不匹配 (seed=$seed)", expected, q.rootName)
        }
    }

    // ── 音符数量 ──────────────────────────────────────────────

    @Test
    fun `all questions have exactly 5 midi notes`() {
        for (seed in 0..100) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            assertEquals("九和弦应有 5 个音 (seed=$seed)", 5, q.midiNotes.size)
        }
    }

    @Test
    fun `midi notes are sorted ascending`() {
        for (seed in 0..100) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
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
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            assertEquals("根音应是最低音 (seed=$seed)", q.rootMidi, q.midiNotes[0])
        }
    }

    // ── 难度配置嵌套子集 ──────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginnerQualities = NinthChordQuality.forDifficulty(NinthChordDifficulty.BEGINNER).toSet()
        val intermediateQualities = NinthChordQuality.forDifficulty(NinthChordDifficulty.INTERMEDIATE).toSet()
        assertTrue("初级品质集应是中级的子集", intermediateQualities.containsAll(beginnerQualities))
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediateQualities = NinthChordQuality.forDifficulty(NinthChordDifficulty.INTERMEDIATE).toSet()
        val advancedQualities = NinthChordQuality.forDifficulty(NinthChordDifficulty.ADVANCED).toSet()
        assertTrue("中级品质集应是高级的子集", advancedQualities.containsAll(intermediateQualities))
    }

    @Test
    fun `beginner qualities are exactly 3`() {
        assertEquals(3, NinthChordQuality.forDifficulty(NinthChordDifficulty.BEGINNER).size)
    }

    @Test
    fun `intermediate qualities are exactly 4`() {
        assertEquals(4, NinthChordQuality.forDifficulty(NinthChordDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `advanced qualities are exactly 5`() {
        assertEquals(5, NinthChordQuality.forDifficulty(NinthChordDifficulty.ADVANCED).size)
    }

    // ── 品质属性 ──────────────────────────────────────────────

    @Test
    fun `richness levels are ordered 0 through 4`() {
        assertEquals(0, NinthChordQuality.MAJOR_9.richnessLevel)
        assertEquals(1, NinthChordQuality.DOMINANT_9.richnessLevel)
        assertEquals(2, NinthChordQuality.MINOR_9.richnessLevel)
        assertEquals(3, NinthChordQuality.MINOR_MAJOR_9.richnessLevel)
        assertEquals(4, NinthChordQuality.DOMINANT_7_FLAT_9.richnessLevel)
    }

    @Test
    fun `all qualities have 5 intervals`() {
        for (quality in NinthChordQuality.ALL) {
            assertEquals("${quality.displayName} 应有 5 个音程偏移", 5, quality.intervals.size)
        }
    }

    @Test
    fun `first interval is always 0`() {
        for (quality in NinthChordQuality.ALL) {
            assertEquals("${quality.displayName} 的第一个音程应为 0", 0, quality.intervals[0])
        }
    }

    @Test
    fun `all qualities have non-empty descriptions`() {
        for (quality in NinthChordQuality.ALL) {
            assertTrue("${quality.displayName} 的描述不应为空", quality.description.isNotEmpty())
        }
    }

    @Test
    fun `all qualities have non-empty symbols`() {
        for (quality in NinthChordQuality.ALL) {
            assertTrue("${quality.displayName} 的符号不应为空", quality.symbol.isNotEmpty())
        }
    }

    @Test
    fun `qualities have distinct display names`() {
        val names = NinthChordQuality.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    @Test
    fun `ninth interval is always 13 or 14`() {
        // 9th interval should be either 13 (flat 9) or 14 (natural 9)
        for (quality in NinthChordQuality.ALL) {
            val ninth = quality.intervals[4]
            assertTrue(
                "${quality.displayName} 的九音偏移应为 13 或 14，实际 $ninth",
                ninth == 13 || ninth == 14
            )
        }
    }

    // ── NinthChordQuestion 参数校验 ─────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 4 notes throws`() {
        NinthChordQuestion(
            quality = NinthChordQuality.MAJOR_9,
            rootMidi = 60,
            rootName = "C",
            difficulty = NinthChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 71),
            answerChoices = listOf("大九和弦"),
            correctAnswer = "大九和弦"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        NinthChordQuestion(
            quality = NinthChordQuality.MAJOR_9,
            rootMidi = 60,
            rootName = "C",
            difficulty = NinthChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 71, 200),
            answerChoices = listOf("大九和弦"),
            correctAnswer = "大九和弦"
        )
    }

    @Test
    fun `fullDescription contains root name and quality`() {
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.rootName))
        assertTrue(q.fullDescription.contains(q.quality.displayName))
        assertTrue(q.fullDescription.contains(q.quality.symbol))
    }
}
