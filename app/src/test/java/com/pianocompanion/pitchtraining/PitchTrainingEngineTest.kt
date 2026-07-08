package com.pianocompanion.pitchtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绝对音高训练出题引擎单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class PitchTrainingEngineTest {

    // ── 基本生成 ──────────────────────────────────────────

    @Test
    fun `generate returns question with correct pitch class from difficulty pool`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val q = PitchTrainingEngine.withSeed(42L).generate(difficulty)
            assertTrue(
                "音级类 ${q.pitchClass} 应在难度 ${difficulty.name} 的集合中",
                difficulty.pitchClasses.contains(q.pitchClass)
            )
        }
    }

    @Test
    fun `generate options contain correct answer`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val q = PitchTrainingEngine.withSeed(3L).generate(difficulty)
            assertTrue("正确答案必须在选项中", q.options.contains(q.correctAnswer))
        }
    }

    @Test
    fun `generate options count matches difficulty`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val q = PitchTrainingEngine.withSeed(99L).generate(difficulty)
            assertEquals(difficulty.optionCount, q.options.size)
        }
    }

    @Test
    fun `generate options are all distinct`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val q = PitchTrainingEngine.withSeed(5L).generate(difficulty)
            assertEquals(q.options.size, q.options.toSet().size)
        }
    }

    @Test
    fun `generate options equal difficulty pitch class set`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val q = PitchTrainingEngine.withSeed(7L).generate(difficulty)
            assertEquals(difficulty.pitchClasses.toSet(), q.options.toSet())
        }
    }

    @Test
    fun `distractors differ from correct answer`() {
        val q = PitchTrainingEngine.withSeed(8L).generate(PitchTrainingDifficulty.INTERMEDIATE)
        val distractorCount = q.options.count { it != q.correctAnswer }
        assertEquals(q.options.size - 1, distractorCount)
    }

    // ── 确定性 ────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val q1 = PitchTrainingEngine.withSeed(123L).generate(PitchTrainingDifficulty.ADVANCED)
        val q2 = PitchTrainingEngine.withSeed(123L).generate(PitchTrainingDifficulty.ADVANCED)
        assertEquals(q1.pitchClass, q2.pitchClass)
        assertEquals(q1.midiNote, q2.midiNote)
        assertEquals(q1.options, q2.options)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var different = false
        repeat(20) { seed ->
            val q1 = PitchTrainingEngine.withSeed(seed.toLong()).generate(PitchTrainingDifficulty.ADVANCED)
            val q2 = PitchTrainingEngine.withSeed((seed + 1000).toLong()).generate(PitchTrainingDifficulty.ADVANCED)
            if (q1.pitchClass != q2.pitchClass || q1.midiNote != q2.midiNote) different = true
        }
        assertTrue("不同种子应大概率产生不同题目", different)
    }

    // ── MIDI 范围与音级映射 ───────────────────────────────

    @Test
    fun `midi note is within difficulty octave range`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            repeat(50) { seed ->
                val q = PitchTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                assertTrue(
                    "MIDI ${q.midiNote} 应在 ${difficulty.octaveLowest}..${difficulty.octaveHighest}",
                    q.midiNote in difficulty.octaveLowest..difficulty.octaveHighest
                )
            }
        }
    }

    @Test
    fun `midi note pitch class matches question pitch class`() {
        repeat(50) { seed ->
            val q = PitchTrainingEngine.withSeed(seed.toLong()).generate(PitchTrainingDifficulty.INTERMEDIATE)
            assertEquals(q.pitchClass, PitchClass.fromMidi(q.midiNote))
        }
    }

    @Test
    fun `all midi notes within piano range`() {
        repeat(50) { seed ->
            val q = PitchTrainingEngine.withSeed(seed.toLong()).generate(PitchTrainingDifficulty.ADVANCED)
            assertTrue(
                "MIDI ${q.midiNote} 超出钢琴范围",
                q.midiNote in PitchTrainingConstants.MIN_MIDI..PitchTrainingConstants.MAX_MIDI
            )
        }
    }

    @Test
    fun `beginner only generates white key pitch classes`() {
        repeat(100) { seed ->
            val q = PitchTrainingEngine.withSeed(seed.toLong()).generate(PitchTrainingDifficulty.BEGINNER)
            assertTrue(
                "初级难度不应出黑键 ${q.pitchClass}",
                q.pitchClass.isWhiteKey
            )
        }
    }

    @Test
    fun `intermediate generates both white and black keys`() {
        var hasWhite = false
        var hasBlack = false
        repeat(100) { seed ->
            val q = PitchTrainingEngine.withSeed(seed.toLong()).generate(PitchTrainingDifficulty.INTERMEDIATE)
            if (q.pitchClass.isWhiteKey) hasWhite = true else hasBlack = true
        }
        assertTrue("中级应包含白键", hasWhite)
        assertTrue("中级应包含黑键", hasBlack)
    }

    @Test
    fun `advanced can generate notes across multiple octaves`() {
        val octaves = mutableSetOf<Int>()
        repeat(200) { seed ->
            val q = PitchTrainingEngine.withSeed(seed.toLong()).generate(PitchTrainingDifficulty.ADVANCED)
            octaves.add(q.octave)
        }
        assertTrue("高级模式应跨多个八度，实际: $octaves", octaves.size >= 2)
    }

    // ── 难度传播 ──────────────────────────────────────────

    @Test
    fun `difficulty is propagated to question`() {
        PitchTrainingDifficulty.ALL.forEach { difficulty ->
            val q = PitchTrainingEngine.withSeed(1L).generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    // ── 难度音级集合正确性 ───────────────────────────────

    @Test
    fun `beginner has 7 white keys`() {
        assertEquals(7, PitchTrainingDifficulty.BEGINNER.optionCount)
        assertEquals(7, PitchTrainingDifficulty.BEGINNER.pitchClasses.size)
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.C))
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.D))
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.E))
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.F))
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.G))
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.A))
        assertTrue(PitchTrainingDifficulty.BEGINNER.pitchClasses.contains(PitchClass.B))
    }

    @Test
    fun `intermediate has 12 pitch classes`() {
        assertEquals(12, PitchTrainingDifficulty.INTERMEDIATE.optionCount)
    }

    @Test
    fun `advanced has 12 pitch classes`() {
        assertEquals(12, PitchTrainingDifficulty.ADVANCED.optionCount)
        assertTrue(PitchTrainingDifficulty.ADVANCED.pitchClasses.contains(PitchClass.C_SHARP))
    }

    @Test
    fun `beginner single octave`() {
        val d = PitchTrainingDifficulty.BEGINNER
        assertEquals(60, d.octaveLowest)
        assertEquals(71, d.octaveHighest)
    }

    @Test
    fun `advanced spans 3 octaves`() {
        val d = PitchTrainingDifficulty.ADVANCED
        assertEquals(48, d.octaveLowest)
        assertEquals(83, d.octaveHighest)
        // C3(48) ~ B5(83) 包含 36 个半音位置（3 个八度）
        assertEquals(36, d.octaveHighest - d.octaveLowest + 1)
    }

    // ── PitchClass 枚举测试 ──────────────────────────────

    @Test
    fun `all 12 pitch classes have distinct semitones`() {
        val semitones = PitchClass.ALL.map { it.semitonesFromC }
        assertEquals(12, semitones.toSet().size)
        assertEquals((0..11).toList(), semitones.sorted())
    }

    @Test
    fun `fromMidi returns correct pitch class`() {
        assertEquals(PitchClass.C, PitchClass.fromMidi(60))  // C4
        assertEquals(PitchClass.C, PitchClass.fromMidi(72))  // C5
        assertEquals(PitchClass.C_SHARP, PitchClass.fromMidi(61))  // C#4
        assertEquals(PitchClass.A, PitchClass.fromMidi(69))  // A4
        assertEquals(PitchClass.B, PitchClass.fromMidi(71))  // B4
    }

    @Test
    fun `fromSemitones returns correct pitch class`() {
        assertEquals(PitchClass.C, PitchClass.fromSemitones(0))
        assertEquals(PitchClass.D_SHARP, PitchClass.fromSemitones(3))
        assertEquals(PitchClass.G_SHARP, PitchClass.fromSemitones(8))
        assertEquals(PitchClass.B, PitchClass.fromSemitones(11))
    }

    @Test
    fun `fromSemitones clamps out of range`() {
        assertEquals(PitchClass.C, PitchClass.fromSemitones(-1))
        assertEquals(PitchClass.B, PitchClass.fromSemitones(99))
    }

    @Test
    fun `white keys are 7`() {
        assertEquals(7, PitchClass.WHITE_KEYS.size)
        PitchClass.WHITE_KEYS.forEach { assertTrue(it.isWhiteKey) }
    }

    @Test
    fun `black keys are 5`() {
        val blackKeys = PitchClass.ALL.filter { it.isBlackKey }
        assertEquals(5, blackKeys.size)
    }

    @Test
    fun `all pitch classes have non-blank names`() {
        PitchClass.ALL.forEach {
            assertTrue(it.sharpName.isNotBlank())
            assertTrue(it.flatName.isNotBlank())
            assertTrue(it.displayName.isNotBlank())
            assertTrue(it.solfegeName.isNotBlank())
        }
    }

    @Test
    fun `sharp and flat names differ for black keys`() {
        PitchClass.ALL.filter { it.isBlackKey }.forEach {
            assertNotEquals(it.sharpName, it.flatName)
        }
    }

    @Test
    fun `sharp and flat names same for white keys`() {
        PitchClass.WHITE_KEYS.forEach {
            assertEquals(it.sharpName, it.flatName)
        }
    }

    // ── 题目详情 ──────────────────────────────────────────

    @Test
    fun `pitch class detail contains name and solfege`() {
        val q = PitchTrainingEngine.withSeed(10L).generate(PitchTrainingDifficulty.INTERMEDIATE)
        assertTrue(q.pitchClassDetail.contains(q.pitchClass.displayName))
        assertTrue(q.pitchClassDetail.contains(q.pitchClass.solfegeName))
    }

    @Test
    fun `note name contains pitch class and octave`() {
        val q = PitchTrainingEngine.withSeed(10L).generate(PitchTrainingDifficulty.BEGINNER)
        assertTrue(q.noteName.contains(q.pitchClass.sharpName))
        assertTrue(q.noteName.contains(q.octave.toString()))
    }

    @Test
    fun `frequency is positive`() {
        val q = PitchTrainingEngine.withSeed(10L).generate(PitchTrainingDifficulty.ADVANCED)
        assertTrue(q.frequency > 0.0)
    }

    @Test
    fun `A4 frequency is approximately 440`() {
        // MIDI 69 = A4 = 440Hz
        val q = PitchQuestion(
            pitchClass = PitchClass.A,
            midiNote = 69,
            difficulty = PitchTrainingDifficulty.INTERMEDIATE,
            options = PitchClass.ALL
        )
        assertEquals(440.0, q.frequency, 1.0)
    }
}
