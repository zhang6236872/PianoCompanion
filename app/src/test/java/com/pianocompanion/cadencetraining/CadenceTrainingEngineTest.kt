package com.pianocompanion.cadencetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终止式听辨训练出题引擎单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 覆盖：确定性出题、选项正确性/唯一性/完整性、和弦进行 MIDI 正确性、
 * 难度配置、各终止式类型定义、ChordFunction.buildMidiNotes 实例方法。
 */
class CadenceTrainingEngineTest {

    // ── 基本生成 ──────────────────────────────────────────

    @Test
    fun `generate returns question with cadence type from difficulty pool`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(42L).generate(difficulty)
            assertTrue(
                "终止式类型 ${q.type} 应在难度 ${difficulty.name} 的集合中",
                CadenceType.forDifficulty(difficulty).contains(q.type)
            )
        }
    }

    @Test
    fun `generate difficulty is preserved`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(7L).generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val a = CadenceTrainingEngine.withSeed(100L).generate(CadenceDifficulty.ADVANCED)
        val b = CadenceTrainingEngine.withSeed(100L).generate(CadenceDifficulty.ADVANCED)
        assertEquals(a, b)
    }

    @Test
    fun `different seeds likely produce different questions`() {
        val a = CadenceTrainingEngine.withSeed(1L).generate(CadenceDifficulty.ADVANCED)
        val b = CadenceTrainingEngine.withSeed(2L).generate(CadenceDifficulty.ADVANCED)
        assertNotEquals(a, b)
    }

    // ── 选项 ──────────────────────────────────────────────

    @Test
    fun `generate options contain correct answer`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(3L).generate(difficulty)
            assertTrue("正确答案必须在选项中", q.answerChoices.contains(q.correctAnswer))
        }
    }

    @Test
    fun `correct answer matches type display name`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(5L).generate(difficulty)
            assertEquals(q.type.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `generate options count matches difficulty`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(99L).generate(difficulty)
            assertEquals(CadenceType.forDifficulty(difficulty).size, q.answerChoices.size)
        }
    }

    @Test
    fun `generate options are all distinct`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(5L).generate(difficulty)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `generate options equal difficulty available types`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(7L).generate(difficulty)
            val expected = CadenceType.forDifficulty(difficulty).map { it.displayName }.toSet()
            assertEquals(expected, q.answerChoices.toSet())
        }
    }

    // ── 和弦进行 MIDI 正确性 ─────────────────────────────

    @Test
    fun `chordProgression has correct chord count for type`() {
        CadenceType.ALL.forEach { type ->
            val q = CadenceTrainingEngine.withSeed(11L).generate(
                difficultyContaining(type)
            )
            assertEquals(type.chordCount, q.chordProgression.size)
        }
    }

    @Test
    fun `chordProgression MIDI notes are within piano range`() {
        repeat(50) { i ->
            val q = CadenceTrainingEngine.withSeed(i.toLong()).generate(CadenceDifficulty.ADVANCED)
            q.chordProgression.flatten().forEach { midi ->
                assertTrue("MIDI $midi 应在钢琴范围 [21,108]", midi in 21..108)
            }
        }
    }

    @Test
    fun `each chord uses its own function intervals - V and I differ for PAC`() {
        // 关键回归测试：修复前引擎忽略 function 参数，导致进行中所有和弦相同。
        // 生成一个完全正格终止（V→I）并验证两个和弦不同。
        val q = generateSpecificType(CadenceType.PERFECT_AUTHENTIC)
        val firstChord = q.chordProgression[0]
        val secondChord = q.chordProgression[1]
        assertNotEquals(
            "PAC 的两个和弦（V 与 I）不应相同",
            firstChord,
            secondChord
        )
    }

    @Test
    fun `chordProgression MIDI notes match type function intervals`() {
        CadenceType.ALL.forEach { type ->
            val q = generateSpecificType(type)
            type.progression.forEachIndexed { idx, function ->
                val expected = function.intervalsFromTonic.map { (q.tonicMidi + it).coerceIn(21, 108) }
                assertEquals(
                    "终止式 ${type.name} 第 ${idx + 1} 个和弦 (${function.romanNumeral}) 的 MIDI 应匹配",
                    expected,
                    q.chordProgression[idx]
                )
            }
        }
    }

    @Test
    fun `romanNumerals match type progression`() {
        CadenceType.ALL.forEach { type ->
            val q = generateSpecificType(type)
            assertEquals(
                type.progression.map { it.romanNumeral },
                q.romanNumerals
            )
        }
    }

    @Test
    fun `fullDescription contains tonic name and cadence display name`() {
        val q = CadenceTrainingEngine.withSeed(1L).generate(CadenceDifficulty.BEGINNER)
        assertTrue(q.fullDescription.contains(q.tonicName))
        assertTrue(q.fullDescription.contains(q.type.displayName))
    }

    @Test
    fun `chordNoteCounts match function note counts`() {
        CadenceType.ALL.forEach { type ->
            val q = generateSpecificType(type)
            assertEquals(
                type.progression.map { it.noteCount },
                q.chordNoteCounts
            )
        }
    }

    // ── 主音 ──────────────────────────────────────────────

    @Test
    fun `tonicMidi is in valid octave range`() {
        repeat(20) { i ->
            val q = CadenceTrainingEngine.withSeed(i.toLong()).generate(CadenceDifficulty.ADVANCED)
            assertTrue(
                "主音 MIDI ${q.tonicMidi} 应在 C3-G3 范围 [48, 55]",
                q.tonicMidi in 48..55
            )
        }
    }

    @Test
    fun `tonicName matches tonicMidi pitch class`() {
        val names = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G")
        repeat(20) { i ->
            val q = CadenceTrainingEngine.withSeed(i.toLong()).generate(CadenceDifficulty.ADVANCED)
            val pc = q.tonicMidi - CadenceTrainingEngine.BASE_OCTAVE_MIDI
            assertEquals(names[pc], q.tonicName)
        }
    }

    // ── 难度配置 ──────────────────────────────────────────

    @Test
    fun `beginner has 2 cadence types`() {
        assertEquals(2, CadenceType.forDifficulty(CadenceDifficulty.BEGINNER).size)
    }

    @Test
    fun `intermediate has 3 cadence types`() {
        assertEquals(3, CadenceType.forDifficulty(CadenceDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `advanced has all 4 cadence types`() {
        assertEquals(4, CadenceType.forDifficulty(CadenceDifficulty.ADVANCED).size)
        assertEquals(CadenceType.ALL.size, CadenceType.forDifficulty(CadenceDifficulty.ADVANCED).size)
    }

    @Test
    fun `difficulty sets are nested - beginner subset of intermediate subset of advanced`() {
        val beginner = CadenceType.forDifficulty(CadenceDifficulty.BEGINNER).toSet()
        val intermediate = CadenceType.forDifficulty(CadenceDifficulty.INTERMEDIATE).toSet()
        val advanced = CadenceType.forDifficulty(CadenceDifficulty.ADVANCED).toSet()
        assertTrue(intermediate.containsAll(beginner))
        assertTrue(advanced.containsAll(intermediate))
    }

    // ── 终止式类型定义 ────────────────────────────────────

    @Test
    fun `PAC progression is V to I`() {
        assertEquals(listOf(ChordFunction.V, ChordFunction.I), CadenceType.PERFECT_AUTHENTIC.progression)
    }

    @Test
    fun `plagal progression is IV to I`() {
        assertEquals(listOf(ChordFunction.IV, ChordFunction.I), CadenceType.PLAGAL.progression)
    }

    @Test
    fun `half cadence progression ends on V`() {
        assertEquals(ChordFunction.V, CadenceType.HALF.progression.last())
    }

    @Test
    fun `deceptive cadence progression is V to vi`() {
        assertEquals(listOf(ChordFunction.V, ChordFunction.VI), CadenceType.DECEPTIVE.progression)
    }

    @Test
    fun `each cadence has exactly 2 chords`() {
        CadenceType.ALL.forEach { type ->
            assertEquals("终止式 ${type.name} 应有 2 个和弦", 2, type.chordCount)
        }
    }

    @Test
    fun `all cadence types have distinct display names`() {
        val names = CadenceType.ALL.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `all cadence types have distinct abbreviations`() {
        val abbrs = CadenceType.ALL.map { it.abbreviation }
        assertEquals(abbrs.size, abbrs.toSet().size)
    }

    // ── ChordFunction.buildMidiNotes 实例方法 ────────────

    @Test
    fun `ChordFunction I builds tonic triad`() {
        assertEquals(listOf(48, 52, 55), ChordFunction.I.buildMidiNotes(48))
    }

    @Test
    fun `ChordFunction V builds dominant triad`() {
        assertEquals(listOf(55, 59, 62), ChordFunction.V.buildMidiNotes(48))
    }

    @Test
    fun `ChordFunction V7 builds dominant seventh with 4 notes`() {
        val notes = ChordFunction.V7.buildMidiNotes(48)
        assertEquals(4, notes.size)
        assertEquals(listOf(55, 59, 62, 65), notes)
    }

    @Test
    fun `ChordFunction buildMidiNotes clamps to piano range`() {
        // 极低主音：intervals 可能超出范围，应钳制到 >= 21
        val notes = ChordFunction.V.buildMidiNotes(0)
        assertTrue(notes.all { it >= 21 })
        // 极高主音
        val highNotes = ChordFunction.I.buildMidiNotes(120)
        assertTrue(highNotes.all { it <= 108 })
    }

    @Test
    fun `ChordFunction noteCount matches intervals size`() {
        ChordFunction.entries.forEach { fn ->
            assertEquals(fn.intervalsFromTonic.size, fn.noteCount)
        }
    }

    // ── 辅助方法 ──────────────────────────────────────────

    /** 返回包含指定终止式类型的最低难度。 */
    private fun difficultyContaining(type: CadenceType): CadenceDifficulty {
        // 高级难度包含全部类型
        return CadenceDifficulty.ADVANCED
    }

    /** 反复生成直到得到指定类型的题目（高级难度下保证最终命中）。 */
    private fun generateSpecificType(target: CadenceType): CadenceQuestion {
        var seed = 0L
        while (true) {
            val q = CadenceTrainingEngine.withSeed(seed).generate(CadenceDifficulty.ADVANCED)
            if (q.type == target) return q
            seed++
            if (seed > 10000) throw AssertionError("无法生成类型 $target 的题目")
        }
    }
}
