package com.pianocompanion.thirteenthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十三和弦色彩听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、音域范围、难度配置等。
 */
class ThirteenthChordTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = ThirteenthChordTrainingEngine.withSeed(42L)
        val e2 = ThirteenthChordTrainingEngine.withSeed(42L)
        val q1 = e1.generate(ThirteenthChordDifficulty.INTERMEDIATE)
        val q2 = e2.generate(ThirteenthChordDifficulty.INTERMEDIATE)
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
            val q1 = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            val q2 = ThirteenthChordTrainingEngine.withSeed((seed + 500).toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
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
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 4 options`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced has 5 options`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            assertEquals("选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `beginner options match expected qualities`() {
        val expected = setOf(
            ThirteenthChordQuality.MAJOR_13.displayName,
            ThirteenthChordQuality.DOMINANT_13.displayName,
            ThirteenthChordQuality.MINOR_13.displayName
        )
        for (seed in 0..20) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.BEGINNER)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `intermediate options include minor major`() {
        val expected = setOf(
            ThirteenthChordQuality.MAJOR_13.displayName,
            ThirteenthChordQuality.DOMINANT_13.displayName,
            ThirteenthChordQuality.MINOR_13.displayName,
            ThirteenthChordQuality.MINOR_MAJOR_13.displayName
        )
        for (seed in 0..20) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.INTERMEDIATE)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced options include all 5 qualities`() {
        val expected = ThirteenthChordQuality.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── MIDI 音符正确性 ──────────────────────────────────────

    @Test
    fun `major 13 has correct intervals`() {
        val notes = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(
            ThirteenthChordQuality.MAJOR_13, 60
        )
        assertEquals(listOf(60, 64, 67, 71, 74, 77, 81), notes)
    }

    @Test
    fun `dominant 13 has correct intervals`() {
        val notes = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(
            ThirteenthChordQuality.DOMINANT_13, 60
        )
        assertEquals(listOf(60, 64, 67, 70, 74, 77, 81), notes)
    }

    @Test
    fun `minor 13 has correct intervals`() {
        val notes = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(
            ThirteenthChordQuality.MINOR_13, 60
        )
        assertEquals(listOf(60, 63, 67, 70, 74, 77, 81), notes)
    }

    @Test
    fun `minor major 13 has correct intervals`() {
        val notes = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(
            ThirteenthChordQuality.MINOR_MAJOR_13, 60
        )
        assertEquals(listOf(60, 63, 67, 71, 74, 77, 81), notes)
    }

    @Test
    fun `half diminished 13 has correct intervals`() {
        val notes = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(
            ThirteenthChordQuality.HALF_DIMINISHED_13, 60
        )
        assertEquals(listOf(60, 63, 66, 70, 74, 77, 81), notes)
    }

    @Test
    fun `major 13 from C4 is C E G B D F A`() {
        // C4=60, E4=64, G4=67, B4=71, D5=74, F5=77, A5=81
        val notes = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(
            ThirteenthChordQuality.MAJOR_13, 60
        )
        assertEquals(7, notes.size)
        assertEquals(60, notes[0]) // C
        assertEquals(64, notes[1]) // E
        assertEquals(67, notes[2]) // G
        assertEquals(71, notes[3]) // B
        assertEquals(74, notes[4]) // D
        assertEquals(77, notes[5]) // F
        assertEquals(81, notes[6]) // A
    }

    @Test
    fun `major 13 differs from dominant 13 only in seventh`() {
        val maj13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MAJOR_13, 60)
        val dom13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.DOMINANT_13, 60)
        assertEquals(maj13[0], dom13[0])
        assertEquals(maj13[1], dom13[1])
        assertEquals(maj13[2], dom13[2])
        assertEquals(1, maj13[3] - dom13[3]) // maj13 seventh is 1 semitone higher
        assertEquals(maj13[4], dom13[4])
        assertEquals(maj13[5], dom13[5])
        assertEquals(maj13[6], dom13[6])
    }

    @Test
    fun `dominant 13 differs from minor 13 only in third`() {
        val dom13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.DOMINANT_13, 60)
        val min13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MINOR_13, 60)
        assertEquals(dom13[0], min13[0])
        assertEquals(1, dom13[1] - min13[1]) // dominant third is 1 semitone higher
        assertEquals(dom13[2], min13[2])
        assertEquals(dom13[3], min13[3])
        assertEquals(dom13[4], min13[4])
        assertEquals(dom13[5], min13[5])
        assertEquals(dom13[6], min13[6])
    }

    @Test
    fun `minor 13 differs from minor major 13 only in seventh`() {
        val min13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MINOR_13, 60)
        val mMaj13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MINOR_MAJOR_13, 60)
        assertEquals(min13[0], mMaj13[0])
        assertEquals(min13[1], mMaj13[1])
        assertEquals(min13[2], mMaj13[2])
        assertEquals(1, mMaj13[3] - min13[3]) // minor-major seventh is 1 semitone higher
        assertEquals(min13[4], mMaj13[4])
        assertEquals(min13[5], mMaj13[5])
        assertEquals(min13[6], mMaj13[6])
    }

    @Test
    fun `minor 13 differs from half diminished 13 only in fifth`() {
        val min13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MINOR_13, 60)
        val halfDim13 = ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.HALF_DIMINISHED_13, 60)
        assertEquals(min13[0], halfDim13[0])
        assertEquals(min13[1], halfDim13[1])
        assertEquals(1, min13[2] - halfDim13[2]) // half-dim fifth is 1 semitone lower
        assertEquals(min13[3], halfDim13[3])
        assertEquals(min13[4], halfDim13[4])
        assertEquals(min13[5], halfDim13[5])
        assertEquals(min13[6], halfDim13[6])
    }

    @Test
    fun `all qualities share the same thirteenth interval`() {
        // The 13th interval (major 13th = 21 semitones) is the same for all qualities
        for (quality in ThirteenthChordQuality.ALL) {
            assertEquals(
                "${quality.displayName} 的十三音偏移应为 21",
                21,
                quality.intervals[6]
            )
        }
    }

    @Test
    fun `all qualities share the same ninth and eleventh intervals`() {
        for (quality in ThirteenthChordQuality.ALL) {
            assertEquals(14, quality.intervals[4]) // 9th
            assertEquals(17, quality.intervals[5]) // 11th
        }
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in 21..108)
            }
        }
    }

    @Test
    fun `root note is in C3-G3 range`() {
        for (seed in 0..200) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
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
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            val expected = expectedNames[q.rootMidi]
            assertEquals("根音名不匹配 (seed=$seed)", expected, q.rootName)
        }
    }

    // ── 音符数量 ──────────────────────────────────────────────

    @Test
    fun `all questions have exactly 7 midi notes`() {
        for (seed in 0..100) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            assertEquals("十三和弦应有 7 个音 (seed=$seed)", 7, q.midiNotes.size)
        }
    }

    @Test
    fun `midi notes are sorted ascending`() {
        for (seed in 0..100) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
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
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            assertEquals("根音应是最低音 (seed=$seed)", q.rootMidi, q.midiNotes[0])
        }
    }

    // ── 难度配置嵌套子集 ──────────────────────────────────────

    @Test
    fun `beginner is subset of intermediate`() {
        val beginnerQualities = ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.BEGINNER).toSet()
        val intermediateQualities = ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.INTERMEDIATE).toSet()
        assertTrue("初级品质集应是中级的子集", intermediateQualities.containsAll(beginnerQualities))
    }

    @Test
    fun `intermediate is subset of advanced`() {
        val intermediateQualities = ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.INTERMEDIATE).toSet()
        val advancedQualities = ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.ADVANCED).toSet()
        assertTrue("中级品质集应是高级的子集", advancedQualities.containsAll(intermediateQualities))
    }

    @Test
    fun `beginner qualities are exactly 3`() {
        assertEquals(3, ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.BEGINNER).size)
    }

    @Test
    fun `intermediate qualities are exactly 4`() {
        assertEquals(4, ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `advanced qualities are exactly 5`() {
        assertEquals(5, ThirteenthChordQuality.forDifficulty(ThirteenthChordDifficulty.ADVANCED).size)
    }

    // ── 品质属性 ──────────────────────────────────────────────

    @Test
    fun `brightness levels are ordered 0 through 4`() {
        assertEquals(0, ThirteenthChordQuality.MAJOR_13.brightnessLevel)
        assertEquals(1, ThirteenthChordQuality.DOMINANT_13.brightnessLevel)
        assertEquals(2, ThirteenthChordQuality.MINOR_13.brightnessLevel)
        assertEquals(3, ThirteenthChordQuality.MINOR_MAJOR_13.brightnessLevel)
        assertEquals(4, ThirteenthChordQuality.HALF_DIMINISHED_13.brightnessLevel)
    }

    @Test
    fun `all qualities have 7 intervals`() {
        for (quality in ThirteenthChordQuality.ALL) {
            assertEquals("${quality.displayName} 应有 7 个音程偏移", 7, quality.intervals.size)
        }
    }

    @Test
    fun `first interval is always 0`() {
        for (quality in ThirteenthChordQuality.ALL) {
            assertEquals("${quality.displayName} 的第一个音程应为 0", 0, quality.intervals[0])
        }
    }

    @Test
    fun `all qualities have non-empty descriptions`() {
        for (quality in ThirteenthChordQuality.ALL) {
            assertTrue("${quality.displayName} 的描述不应为空", quality.description.isNotEmpty())
        }
    }

    @Test
    fun `all qualities have non-empty symbols`() {
        for (quality in ThirteenthChordQuality.ALL) {
            assertTrue("${quality.displayName} 的符号不应为空", quality.symbol.isNotEmpty())
        }
    }

    @Test
    fun `qualities have distinct display names`() {
        val names = ThirteenthChordQuality.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    // ── ThirteenthChordQuestion 参数校验 ───────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 6 notes throws`() {
        ThirteenthChordQuestion(
            quality = ThirteenthChordQuality.MAJOR_13,
            rootMidi = 60,
            rootName = "C",
            difficulty = ThirteenthChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 71, 74, 77),
            answerChoices = listOf("大十三和弦"),
            correctAnswer = "大十三和弦"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        ThirteenthChordQuestion(
            quality = ThirteenthChordQuality.MAJOR_13,
            rootMidi = 60,
            rootName = "C",
            difficulty = ThirteenthChordDifficulty.BEGINNER,
            midiNotes = listOf(60, 64, 67, 71, 74, 77, 200),
            answerChoices = listOf("大十三和弦"),
            correctAnswer = "大十三和弦"
        )
    }

    @Test
    fun `fullDescription contains root name and quality`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.rootName))
        assertTrue(q.fullDescription.contains(q.quality.displayName))
        assertTrue(q.fullDescription.contains(q.quality.symbol))
    }
}
