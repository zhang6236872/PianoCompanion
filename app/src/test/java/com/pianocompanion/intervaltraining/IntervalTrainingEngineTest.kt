package com.pianocompanion.intervaltraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音程听辨训练出题引擎单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class IntervalTrainingEngineTest {

    // ── 基本生成 ──────────────────────────────────────────

    @Test
    fun `generate returns question with correct interval from difficulty pool`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val q = IntervalTrainingEngine.withSeed(42L).generate(difficulty)
            assertTrue(
                "音程 ${q.interval} 应在难度 ${difficulty.name} 的集合中",
                difficulty.intervals.contains(q.interval)
            )
        }
    }

    @Test
    fun `generate options contain correct answer`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val q = IntervalTrainingEngine.withSeed(3L).generate(difficulty)
            assertTrue("正确答案必须在选项中", q.options.contains(q.correctAnswer))
        }
    }

    @Test
    fun `generate options count matches difficulty`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val q = IntervalTrainingEngine.withSeed(99L).generate(difficulty)
            assertEquals(difficulty.optionCount, q.options.size)
        }
    }

    @Test
    fun `generate options are all distinct`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val q = IntervalTrainingEngine.withSeed(5L).generate(difficulty)
            assertEquals(q.options.size, q.options.toSet().size)
        }
    }

    @Test
    fun `generate options equal difficulty interval set`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val q = IntervalTrainingEngine.withSeed(7L).generate(difficulty)
            assertEquals(difficulty.intervals.toSet(), q.options.toSet())
        }
    }

    @Test
    fun `distractors differ from correct answer`() {
        val q = IntervalTrainingEngine.withSeed(8L).generate(IntervalDifficulty.INTERMEDIATE)
        val distractorCount = q.options.count { it != q.correctAnswer }
        assertEquals(q.options.size - 1, distractorCount)
    }

    // ── 确定性 ────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val q1 = IntervalTrainingEngine.withSeed(123L).generate(IntervalDifficulty.ADVANCED)
        val q2 = IntervalTrainingEngine.withSeed(123L).generate(IntervalDifficulty.ADVANCED)
        assertEquals(q1.interval, q2.interval)
        assertEquals(q1.lowerMidi, q2.lowerMidi)
        assertEquals(q1.options, q2.options)
    }

    @Test
    fun `different seeds may produce different questions`() {
        var different = false
        repeat(20) { seed ->
            val q1 = IntervalTrainingEngine.withSeed(seed.toLong()).generate(IntervalDifficulty.ADVANCED)
            val q2 = IntervalTrainingEngine.withSeed((seed + 1000).toLong()).generate(IntervalDifficulty.ADVANCED)
            if (q1.interval != q2.interval || q1.lowerMidi != q2.lowerMidi) different = true
        }
        assertTrue("不同种子应大概率产生不同题目", different)
    }

    // ── MIDI 范围与半音距离 ───────────────────────────────

    @Test
    fun `lower midi is in comfortable range`() {
        repeat(50) { seed ->
            val q = IntervalTrainingEngine.withSeed(seed.toLong()).generate(IntervalDifficulty.INTERMEDIATE)
            assertTrue(
                "根音 ${q.lowerMidi} 应在 ${IntervalTrainingEngine.START_MIN}..${IntervalTrainingEngine.START_MAX}",
                q.lowerMidi in IntervalTrainingEngine.START_MIN..IntervalTrainingEngine.START_MAX
            )
        }
    }

    @Test
    fun `all midi notes within piano range`() {
        repeat(50) { seed ->
            val q = IntervalTrainingEngine.withSeed(seed.toLong()).generate(IntervalDifficulty.ADVANCED)
            q.playOrder.forEach { midi ->
                assertTrue("MIDI $midi 超出钢琴范围", midi in IntervalTrainingEngine.MIN_MIDI..IntervalTrainingEngine.MAX_MIDI)
            }
        }
    }

    @Test
    fun `upper midi equals lower plus semitones`() {
        repeat(50) { seed ->
            val q = IntervalTrainingEngine.withSeed(seed.toLong()).generate(IntervalDifficulty.ADVANCED)
            assertEquals(q.interval.semitones, q.upperMidi - q.lowerMidi)
        }
    }

    @Test
    fun `upper midi is higher or equal to lower`() {
        repeat(50) { seed ->
            val q = IntervalTrainingEngine.withSeed(seed.toLong()).generate(IntervalDifficulty.INTERMEDIATE)
            assertTrue(q.upperMidi >= q.lowerMidi)
        }
    }

    // ── 播放顺序 ──────────────────────────────────────────

    @Test
    fun `ascending play order is lower then upper`() {
        val q = IntervalTrainingEngine.withSeed(1L).generate(IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING)
        assertEquals(2, q.playOrder.size)
        assertEquals(q.lowerMidi, q.playOrder[0])
        assertEquals(q.upperMidi, q.playOrder[1])
    }

    @Test
    fun `descending play order is upper then lower`() {
        val q = IntervalTrainingEngine.withSeed(1L).generate(IntervalDifficulty.BEGINNER, PlayDirection.DESCENDING)
        assertEquals(2, q.playOrder.size)
        assertEquals(q.upperMidi, q.playOrder[0])
        assertEquals(q.lowerMidi, q.playOrder[1])
    }

    @Test
    fun `harmonic play order contains both notes`() {
        val q = IntervalTrainingEngine.withSeed(1L).generate(IntervalDifficulty.BEGINNER, PlayDirection.HARMONIC)
        assertEquals(2, q.playOrder.size)
        assertTrue(q.playOrder.contains(q.lowerMidi))
        assertTrue(q.playOrder.contains(q.upperMidi))
    }

    @Test
    fun `play direction is propagated to question`() {
        PlayDirection.ALL.forEach { dir ->
            val q = IntervalTrainingEngine.withSeed(1L).generate(IntervalDifficulty.BEGINNER, dir)
            assertEquals(dir, q.playDirection)
        }
    }

    @Test
    fun `difficulty is propagated to question`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val q = IntervalTrainingEngine.withSeed(1L).generate(difficulty)
            // interval should be from the difficulty pool
            assertTrue(difficulty.intervals.contains(q.interval))
        }
    }

    // ── 难度音程集合正确性 ───────────────────────────────

    @Test
    fun `beginner has 4 intervals`() {
        assertEquals(4, IntervalDifficulty.BEGINNER.optionCount)
        assertEquals(4, IntervalDifficulty.BEGINNER.intervals.size)
        // 包含大二度、大三度、纯四度、纯五度
        assertTrue(IntervalDifficulty.BEGINNER.intervals.contains(IntervalType.MAJOR_SECOND))
        assertTrue(IntervalDifficulty.BEGINNER.intervals.contains(IntervalType.MAJOR_THIRD))
        assertTrue(IntervalDifficulty.BEGINNER.intervals.contains(IntervalType.PERFECT_FOURTH))
        assertTrue(IntervalDifficulty.BEGINNER.intervals.contains(IntervalType.PERFECT_FIFTH))
    }

    @Test
    fun `intermediate has 6 intervals`() {
        assertEquals(6, IntervalDifficulty.INTERMEDIATE.optionCount)
    }

    @Test
    fun `advanced has 8 intervals`() {
        assertEquals(8, IntervalDifficulty.ADVANCED.optionCount)
        // 包含增四度
        assertTrue(IntervalDifficulty.ADVANCED.intervals.contains(IntervalType.AUGMENTED_FOURTH))
        // 包含纯八度
        assertTrue(IntervalDifficulty.ADVANCED.intervals.contains(IntervalType.PERFECT_OCTAVE))
    }

    // ── 音程类型映射 ──────────────────────────────────────

    @Test
    fun `fromSemitones returns correct interval`() {
        assertEquals(IntervalType.PERFECT_UNISON, IntervalType.fromSemitones(0))
        assertEquals(IntervalType.MINOR_SECOND, IntervalType.fromSemitones(1))
        assertEquals(IntervalType.MAJOR_THIRD, IntervalType.fromSemitones(4))
        assertEquals(IntervalType.AUGMENTED_FOURTH, IntervalType.fromSemitones(6))
        assertEquals(IntervalType.PERFECT_OCTAVE, IntervalType.fromSemitones(12))
    }

    @Test
    fun `fromSemitones returns null for invalid`() {
        assertEquals(null, IntervalType.fromSemitones(-1))
        assertEquals(null, IntervalType.fromSemitones(13))
    }

    @Test
    fun `all 13 interval types have distinct semitones`() {
        val semitones = IntervalType.ALL.map { it.semitones }
        assertEquals(13, semitones.toSet().size)
        assertEquals((0..12).toList(), semitones.sorted())
    }

    @Test
    fun `all interval types have non-blank names`() {
        IntervalType.ALL.forEach {
            assertTrue(it.displayName.isNotBlank())
            assertTrue(it.abbreviation.isNotBlank())
        }
    }

    @Test
    fun `interval detail contains name and semitones`() {
        val q = IntervalTrainingEngine.withSeed(10L).generate(IntervalDifficulty.INTERMEDIATE)
        assertTrue(q.intervalDetail.contains(q.interval.displayName))
        assertTrue(q.intervalDetail.contains("${q.interval.semitones}半音"))
    }

    @Test
    fun `note detail contains both note names`() {
        val q = IntervalTrainingEngine.withSeed(10L).generate(IntervalDifficulty.INTERMEDIATE)
        assertTrue(q.noteDetail.contains(q.lowerNoteName))
        assertTrue(q.noteDetail.contains(q.upperNoteName))
    }
}
