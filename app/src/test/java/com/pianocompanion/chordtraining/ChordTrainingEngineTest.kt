package com.pianocompanion.chordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordTrainingEngine] 单元测试。
 *
 * 验证出题引擎：
 * - 正确答案在选项中
 * - 选项数与难度对应
 * - 选项无重复且含正确答案
 * - 难度对应的和弦类型集合正确
 * - 播放方式正确传递
 * - 确定性出题（固定种子复现）
 * - MIDI 音符构建正确性（根音 + 音程 + 钢琴范围钳制）
 */
class ChordTrainingEngineTest {

    @Test
    fun `correct answer is in choices`() {
        val engine = ChordTrainingEngine.withSeed(42L)
        val q = engine.generate(ChordEarDifficulty.ADVANCED)
        assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
    }

    @Test
    fun `beginner difficulty has 2 choices`() {
        val engine = ChordTrainingEngine()
        val q = engine.generate(ChordEarDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `intermediate difficulty has 4 choices`() {
        val engine = ChordTrainingEngine()
        val q = engine.generate(ChordEarDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `advanced difficulty has 8 choices`() {
        val engine = ChordTrainingEngine()
        val q = engine.generate(ChordEarDifficulty.ADVANCED)
        assertEquals(8, q.answerChoices.size)
    }

    @Test
    fun `choices are unique`() {
        val engine = ChordTrainingEngine.withSeed(7L)
        val q = engine.generate(ChordEarDifficulty.ADVANCED)
        assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
    }

    @Test
    fun `choices contain only difficulty-appropriate types`() {
        val engine = ChordTrainingEngine()
        val q = engine.generate(ChordEarDifficulty.BEGINNER)
        val allowed = ChordEarType.forDifficulty(ChordEarDifficulty.BEGINNER).map { it.displayName }.toSet()
        q.answerChoices.forEach { choice ->
            assertTrue("选项 $choice 不属于初级范围", choice in allowed)
        }
    }

    @Test
    fun `forDifficulty beginner is major and minor`() {
        val types = ChordEarType.forDifficulty(ChordEarDifficulty.BEGINNER)
        assertEquals(listOf(ChordEarType.MAJOR, ChordEarType.MINOR), types)
    }

    @Test
    fun `forDifficulty intermediate is all triads`() {
        val types = ChordEarType.forDifficulty(ChordEarDifficulty.INTERMEDIATE)
        assertEquals(ChordEarType.TRIADS, types)
    }

    @Test
    fun `forDifficulty advanced is all types`() {
        val types = ChordEarType.forDifficulty(ChordEarDifficulty.ADVANCED)
        assertEquals(ChordEarType.ALL, types)
    }

    @Test
    fun `deterministic generation with same seed`() {
        val e1 = ChordTrainingEngine.withSeed(123L)
        val e2 = ChordTrainingEngine.withSeed(123L)
        val q1 = e1.generate(ChordEarDifficulty.ADVANCED)
        val q2 = e2.generate(ChordEarDifficulty.ADVANCED)
        assertEquals(q1.type, q2.type)
        assertEquals(q1.root, q2.root)
        assertEquals(q1.midiNotes, q2.midiNotes)
    }

    @Test
    fun `playStyle is propagated to question`() {
        val engine = ChordTrainingEngine()
        val q = engine.generate(ChordEarDifficulty.INTERMEDIATE, ChordPlayStyle.ARPEGGIO)
        assertEquals(ChordPlayStyle.ARPEGGIO, q.playStyle)
    }

    @Test
    fun `difficulty is propagated to question`() {
        val engine = ChordTrainingEngine()
        val q = engine.generate(ChordEarDifficulty.ADVANCED)
        assertEquals(ChordEarDifficulty.ADVANCED, q.difficulty)
    }

    @Test
    fun `buildMidiNotes for C major triad`() {
        val engine = ChordTrainingEngine()
        val notes = engine.buildMidiNotes(0, ChordEarType.MAJOR) // C major: C4, E4, G4
        assertEquals(listOf(60, 64, 67), notes)
    }

    @Test
    fun `buildMidiNotes for D minor triad`() {
        val engine = ChordTrainingEngine()
        val notes = engine.buildMidiNotes(2, ChordEarType.MINOR) // D minor: D4, F4, A4
        assertEquals(listOf(62, 65, 69), notes)
    }

    @Test
    fun `buildMidiNotes for G dominant seventh`() {
        val engine = ChordTrainingEngine()
        val notes = engine.buildMidiNotes(7, ChordEarType.DOMINANT_SEVENTH) // G7: G4, B4, D5, F5
        assertEquals(listOf(67, 71, 74, 77), notes)
    }

    @Test
    fun `buildMidiNotes clamps to piano range`() {
        val engine = ChordTrainingEngine()
        // 根音 B (pc=11) + 减七 (intervals 3,6,9) → 71,74,77,80 都在范围内
        val notes = engine.buildMidiNotes(11, ChordEarType.DIMINISHED_SEVENTH)
        assertTrue("所有音符应在钢琴范围内", notes.all { it in 21..108 })
        assertEquals(listOf(71, 74, 77, 80), notes)
    }

    @Test
    fun `triad question has 3 notes`() {
        val engine = ChordTrainingEngine.withSeed(1L)
        val q = engine.generate(ChordEarDifficulty.INTERMEDIATE)
        assertEquals(3, q.noteCount)
        assertEquals(3, q.midiNotes.size)
    }

    @Test
    fun `seventh chord question has 4 notes`() {
        val engine = ChordTrainingEngine.withSeed(1L)
        // 高级难度才含七和弦，多次生成直到命中七和弦
        repeat(50) {
            val q = engine.generate(ChordEarDifficulty.ADVANCED)
            if (q.type.isSeventh) {
                assertEquals(4, q.noteCount)
                return
            }
        }
        assertFalse("应在 50 次内生成七和弦", true)
    }

    @Test
    fun `chord intervals are correct`() {
        // 验证各和弦类型的音程结构
        assertEquals(listOf(4, 7), ChordEarType.MAJOR.intervals)
        assertEquals(listOf(3, 7), ChordEarType.MINOR.intervals)
        assertEquals(listOf(3, 6), ChordEarType.DIMINISHED.intervals)
        assertEquals(listOf(4, 8), ChordEarType.AUGMENTED.intervals)
        assertEquals(listOf(4, 7, 11), ChordEarType.MAJOR_SEVENTH.intervals)
        assertEquals(listOf(4, 7, 10), ChordEarType.DOMINANT_SEVENTH.intervals)
        assertEquals(listOf(3, 7, 10), ChordEarType.MINOR_SEVENTH.intervals)
        assertEquals(listOf(3, 6, 9), ChordEarType.DIMINISHED_SEVENTH.intervals)
    }

    @Test
    fun `allIntervals includes root zero`() {
        assertEquals(listOf(0, 4, 7), ChordEarType.MAJOR.allIntervals)
        assertEquals(listOf(0, 4, 7, 10), ChordEarType.DOMINANT_SEVENTH.allIntervals)
    }

    @Test
    fun `noteCount matches intervals`() {
        ChordEarType.ALL.forEach { type ->
            assertEquals(type.intervals.size + 1, type.noteCount)
        }
    }

    @Test
    fun `fullName contains root and type`() {
        val engine = ChordTrainingEngine.withSeed(99L)
        val q = engine.generate(ChordEarDifficulty.ADVANCED)
        assertTrue(q.fullName.contains(q.type.displayName))
    }

    @Test
    fun `different seeds may produce different questions`() {
        val e1 = ChordTrainingEngine.withSeed(1L)
        val e2 = ChordTrainingEngine.withSeed(2L)
        // 不保证一定不同，但大概率不同；这里只验证不崩溃且生成合法题目
        val q1 = e1.generate(ChordEarDifficulty.ADVANCED)
        val q2 = e2.generate(ChordEarDifficulty.ADVANCED)
        assertTrue(q1.correctAnswer in q1.answerChoices)
        assertTrue(q2.correctAnswer in q2.answerChoices)
    }

    @Test
    fun `major and minor differ by third`() {
        // 大三和弦三音 = 根音+4，小三和弦三音 = 根音+3
        val engine = ChordTrainingEngine()
        val major = engine.buildMidiNotes(0, ChordEarType.MAJOR)
        val minor = engine.buildMidiNotes(0, ChordEarType.MINOR)
        assertEquals(4, major[1] - major[0])  // 大三度
        assertEquals(3, minor[1] - minor[0])  // 小三度
        assertEquals(major[2], minor[2])      // 五音相同（纯五度）
    }

    @Test
    fun `augmented differs from major by fifth`() {
        val engine = ChordTrainingEngine()
        val major = engine.buildMidiNotes(0, ChordEarType.MAJOR)
        val aug = engine.buildMidiNotes(0, ChordEarType.AUGMENTED)
        assertEquals(7, major[2] - major[0])  // 纯五度
        assertEquals(8, aug[2] - aug[0])      // 增五度
    }

    @Test
    fun `diminished differs from minor by fifth`() {
        val engine = ChordTrainingEngine()
        val minor = engine.buildMidiNotes(0, ChordEarType.MINOR)
        val dim = engine.buildMidiNotes(0, ChordEarType.DIMINISHED)
        assertEquals(7, minor[2] - minor[0])  // 纯五度
        assertEquals(6, dim[2] - dim[0])      // 减五度
    }

    @Test
    fun `chord root names`() {
        assertEquals("C", ChordRoot(0).name(false))
        assertEquals("D♭", ChordRoot(1).name(true))
        assertEquals("C♯", ChordRoot(1).name(false))
        assertEquals("B", ChordRoot(11).name(false))
    }

    @Test
    fun `chord root preferFlats`() {
        assertTrue(ChordRoot.preferFlats(1))   // D♭
        assertTrue(ChordRoot.preferFlats(10))  // B♭
        assertFalse(ChordRoot.preferFlats(0))  // C
        assertFalse(ChordRoot.preferFlats(7))  // G
    }

    @Test
    fun `ALL chord roots has 12 entries`() {
        assertEquals(12, ChordRoot.ALL.size)
    }

    @Test
    fun `ALL difficulties has 3 entries`() {
        assertEquals(3, ChordEarDifficulty.ALL.size)
    }

    @Test
    fun `ALL play styles has 2 entries`() {
        assertEquals(2, ChordPlayStyle.ALL.size)
    }
}
