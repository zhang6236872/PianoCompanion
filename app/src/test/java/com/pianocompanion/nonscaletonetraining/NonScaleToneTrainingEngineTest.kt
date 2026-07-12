package com.pianocompanion.nonscaletonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调外音听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、旋律 MIDI 正确性、调外音偏移映射、音域范围、难度配置等。
 */
class NonScaleToneTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = NonScaleToneTrainingEngine.withSeed(42L)
        val e2 = NonScaleToneTrainingEngine.withSeed(42L)
        val q1 = e1.generate(NonScaleToneDifficulty.INTERMEDIATE)
        val q2 = e2.generate(NonScaleToneDifficulty.INTERMEDIATE)
        assertEquals(q1.type, q2.type)
        assertEquals(q1.key, q2.key)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var foundDifferent = false
        for (seed in 0..100) {
            val q1 = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            val q2 = NonScaleToneTrainingEngine.withSeed((seed + 500).toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            if (q1.type != q2.type || q1.key != q2.key) {
                foundDifferent = true
                break
            }
        }
        assertTrue("不同种子应该能产生不同题目", foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `beginner has 3 options`() {
        for (seed in 0..20) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.BEGINNER)
            assertEquals("初级应有 3 个选项 (seed=$seed)", 3, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate has 4 options`() {
        for (seed in 0..20) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.INTERMEDIATE)
            assertEquals("中级应有 4 个选项 (seed=$seed)", 4, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced has 5 options`() {
        for (seed in 0..20) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            assertEquals("高级应有 5 个选项 (seed=$seed)", 5, q.answerChoices.size)
        }
    }

    @Test
    fun `options are unique`() {
        for (seed in 0..50) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            assertEquals("选项应无重复 (seed=$seed)", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options contain correct answer`() {
        for (seed in 0..100) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            assertTrue("正确答案应在选项中 (seed=$seed)", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `beginner options are diatonic flat3 sharp4`() {
        val expected = NonScaleToneDifficulty.typesForDifficulty(NonScaleToneDifficulty.BEGINNER)
            .map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.BEGINNER)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    @Test
    fun `advanced options cover all 5 types`() {
        val expected = NonScaleToneType.ALL.map { it.displayName }.toSet()
        for (seed in 0..20) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── 正确答案匹配类型 ────────────────────────────────────

    @Test
    fun `correct answer matches selected type display name`() {
        for (seed in 0..200) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            assertEquals(
                "正确答案应是选中类型的显示名 (seed=$seed)",
                q.type.displayName, q.correctAnswer
            )
        }
    }

    // ── 旋律 MIDI 正确性 ────────────────────────────────────

    @Test
    fun `DIATONIC in C major is C D E F G`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.DIATONIC, 48)
        assertEquals(listOf(48, 50, 52, 53, 55), notes)
    }

    @Test
    fun `FLATTED_THIRD in C major lowers E to Eb`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.FLATTED_THIRD, 48)
        // C D Eb F G = 48 50 51 53 55
        assertEquals(listOf(48, 50, 51, 53, 55), notes)
    }

    @Test
    fun `RAISED_FOURTH in C major raises F to F#`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.RAISED_FOURTH, 48)
        // C D E F# G = 48 50 52 54 55
        assertEquals(listOf(48, 50, 52, 54, 55), notes)
    }

    @Test
    fun `FLATTED_FIFTH in C major lowers G to Gb`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.FLATTED_FIFTH, 48)
        // C D E F Gb = 48 50 52 53 54
        assertEquals(listOf(48, 50, 52, 53, 54), notes)
    }

    @Test
    fun `RAISED_SECOND in C major raises D to D#`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.RAISED_SECOND, 48)
        // C D# E F G = 48 51 52 53 55
        assertEquals(listOf(48, 51, 52, 53, 55), notes)
    }

    // ── 不同调性 ──────────────────────────────────────────────

    @Test
    fun `DIATONIC in G major is G A B C D`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.DIATONIC, 55)
        assertEquals(listOf(55, 57, 59, 60, 62), notes)
    }

    @Test
    fun `FLATTED_THIRD in G major lowers B to Bb`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.FLATTED_THIRD, 55)
        // G A Bb C D = 55 57 58 60 62
        assertEquals(listOf(55, 57, 58, 60, 62), notes)
    }

    @Test
    fun `DIATONIC in F major is F G A Bb C`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.DIATONIC, 53)
        // F G A Bb C = 53 55 57 58 60
        assertEquals(listOf(53, 55, 57, 58, 60), notes)
    }

    @Test
    fun `DIATONIC in D major is D E F# G A`() {
        val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.DIATONIC, 50)
        // D E F# G A = 50 52 54 55 57
        assertEquals(listOf(50, 52, 54, 55, 57), notes)
    }

    // ── 旋律音符数量和排列 ────────────────────────────────────

    @Test
    fun `phrase always has 5 notes`() {
        for (type in NonScaleToneType.ALL) {
            for (key in NstMusicalKey.ALL) {
                val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(type, key.tonicMidi)
                assertEquals("${type.displayName} ${key.displayName} 应有 5 个音符", 5, notes.size)
            }
        }
    }

    @Test
    fun `diatonic melody is strictly ascending`() {
        for (key in NstMusicalKey.ALL) {
            val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(NonScaleToneType.DIATONIC, key.tonicMidi)
            for (i in 0 until notes.size - 1) {
                assertTrue(
                    "${key.displayName} 调内旋律应严格上行 (idx=$i)",
                    notes[i] < notes[i + 1]
                )
            }
        }
    }

    // ── 音域范围 ──────────────────────────────────────────────

    @Test
    fun `all MIDI notes are in piano range`() {
        for (seed in 0..200) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.ADVANCED)
            for (midi in q.midiNotes) {
                assertTrue("MIDI $midi 应在 [21, 108] 范围内 (seed=$seed)", midi in NST_MIN_MIDI..NST_MAX_MIDI)
            }
        }
    }

    @Test
    fun `all types and keys produce valid MIDI`() {
        for (type in NonScaleToneType.ALL) {
            for (key in NstMusicalKey.ALL) {
                val notes = NonScaleToneTrainingEngine.buildPhraseMidiNotes(type, key.tonicMidi)
                for (midi in notes) {
                    assertTrue(
                        "${key.displayName} ${type.displayName}: MIDI $midi 应在范围内",
                        midi in NST_MIN_MIDI..NST_MAX_MIDI
                    )
                }
            }
        }
    }

    // ── 难度配置 ──────────────────────────────────────────────

    @Test
    fun `beginner types are first 3`() {
        val types = NonScaleToneDifficulty.typesForDifficulty(NonScaleToneDifficulty.BEGINNER)
        assertEquals(3, types.size)
        assertTrue(types.contains(NonScaleToneType.DIATONIC))
        assertTrue(types.contains(NonScaleToneType.FLATTED_THIRD))
        assertTrue(types.contains(NonScaleToneType.RAISED_FOURTH))
    }

    @Test
    fun `intermediate types are first 4`() {
        val types = NonScaleToneDifficulty.typesForDifficulty(NonScaleToneDifficulty.INTERMEDIATE)
        assertEquals(4, types.size)
        assertTrue(types.contains(NonScaleToneType.FLATTED_FIFTH))
    }

    @Test
    fun `advanced types are all 5`() {
        val types = NonScaleToneDifficulty.typesForDifficulty(NonScaleToneDifficulty.ADVANCED)
        assertEquals(5, types.size)
        assertEquals(NonScaleToneType.ALL.toSet(), types.toSet())
    }

    @Test
    fun `beginner only generates first 3 types`() {
        val allowed = NonScaleToneDifficulty.typesForDifficulty(NonScaleToneDifficulty.BEGINNER).toSet()
        for (seed in 0..200) {
            val q = NonScaleToneTrainingEngine.withSeed(seed.toLong()).generate(NonScaleToneDifficulty.BEGINNER)
            assertTrue(
                "初级只应出前 3 种类型 (seed=$seed, 实际 ${q.type})",
                q.type in allowed
            )
        }
    }

    // ── 调外音类型属性完备性 ───────────────────────────────────

    @Test
    fun `all types have non-empty descriptions`() {
        for (type in NonScaleToneType.ALL) {
            assertTrue("${type.displayName} 的描述不应为空", type.description.isNotEmpty())
        }
    }

    @Test
    fun `types have distinct display names`() {
        val names = NonScaleToneType.ALL.map { it.displayName }
        assertEquals("显示名应唯一", names.size, names.toSet().size)
    }

    @Test
    fun `types have distinct symbols`() {
        val symbols = NonScaleToneType.ALL.map { it.symbol }
        assertEquals("符号应唯一", symbols.size, symbols.toSet().size)
    }

    @Test
    fun `diatonic has no alteration`() {
        assertEquals(0, NonScaleToneType.DIATONIC.alteredDegree)
        assertEquals(0, NonScaleToneType.DIATONIC.semitoneDeviation)
    }

    @Test
    fun `all non-diatonic types have nonzero deviation`() {
        for (type in NonScaleToneType.ALL) {
            if (type != NonScaleToneType.DIATONIC) {
                assertTrue("${type.displayName} 应有非零半音偏移", type.semitoneDeviation != 0)
                assertTrue("${type.displayName} 应有非零变化音级", type.alteredDegree != 0)
            }
        }
    }

    @Test
    fun `difficulty ranks are 1 through 5`() {
        val ranks = NonScaleToneType.ALL.map { it.difficultyRank }.sorted()
        assertEquals(listOf(1, 2, 3, 4, 5), ranks)
    }

    @Test
    fun `ALL list is sorted by difficulty rank`() {
        val ranks = NonScaleToneType.ALL.map { it.difficultyRank }
        assertEquals(ranks, ranks.sorted())
    }

    @Test
    fun `there are exactly 4 keys`() {
        assertEquals(4, NstMusicalKey.ALL.size)
    }

    // ── NonScaleToneQuestion 参数校验 ───────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question with 4 notes throws`() {
        NonScaleToneQuestion(
            type = NonScaleToneType.DIATONIC,
            key = NstMusicalKey.C_MAJOR,
            difficulty = NonScaleToneDifficulty.BEGINNER,
            tonicMidi = 48,
            midiNotes = listOf(48, 50, 52, 53),
            answerChoices = listOf("调内（自然大调）"),
            correctAnswer = "调内（自然大调）"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with 6 notes throws`() {
        NonScaleToneQuestion(
            type = NonScaleToneType.DIATONIC,
            key = NstMusicalKey.C_MAJOR,
            difficulty = NonScaleToneDifficulty.BEGINNER,
            tonicMidi = 48,
            midiNotes = listOf(48, 50, 52, 53, 55, 57),
            answerChoices = listOf("调内（自然大调）"),
            correctAnswer = "调内（自然大调）"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question with out of range MIDI throws`() {
        NonScaleToneQuestion(
            type = NonScaleToneType.DIATONIC,
            key = NstMusicalKey.C_MAJOR,
            difficulty = NonScaleToneDifficulty.BEGINNER,
            tonicMidi = 48,
            midiNotes = listOf(48, 50, 52, 53, 200),
            answerChoices = listOf("调内（自然大调）"),
            correctAnswer = "调内（自然大调）"
        )
    }

    @Test
    fun `question with 5 notes is valid`() {
        val q = NonScaleToneQuestion(
            type = NonScaleToneType.DIATONIC,
            key = NstMusicalKey.C_MAJOR,
            difficulty = NonScaleToneDifficulty.BEGINNER,
            tonicMidi = 48,
            midiNotes = listOf(48, 50, 52, 53, 55),
            answerChoices = listOf("调内（自然大调）"),
            correctAnswer = "调内（自然大调）"
        )
        assertEquals(5, q.midiNotes.size)
    }

    @Test
    fun `fullDescription contains key name and type name`() {
        val q = NonScaleToneTrainingEngine.withSeed(1L).generate(NonScaleToneDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.key.displayName))
        assertTrue(q.fullDescription.contains(q.type.displayName))
    }

    // ── DIATONIC_DEGREE_OFFSETS 验证 ─────────────────────────

    @Test
    fun `diatonic degree offsets are correct`() {
        // 自然大调音阶 do(0) re(2) mi(4) fa(5) sol(7)
        assertEquals(listOf(0, 2, 4, 5, 7), DIATONIC_DEGREE_OFFSETS)
    }

    @Test
    fun `diatonic degree offsets has 5 entries`() {
        assertEquals(5, DIATONIC_DEGREE_OFFSETS.size)
    }
}
