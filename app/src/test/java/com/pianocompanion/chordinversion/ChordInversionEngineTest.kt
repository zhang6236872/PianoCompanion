package com.pianocompanion.chordinversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练出题引擎单元测试。
 */
class ChordInversionEngineTest {

    // ── 选项正确性 ──────────────────────────────────

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = ChordInversionEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(ChordInversionDifficulty.BEGINNER)
            assertEquals(ChordInversionDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = ChordInversionEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(ChordInversionDifficulty.INTERMEDIATE)
            assertEquals(ChordInversionDifficulty.INTERMEDIATE.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 4 choices`() {
        val engine = ChordInversionEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            assertEquals(ChordInversionDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = ChordInversionEngine.withSeed(3)
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Correct answer not in choices: ${q.correctAnswer}",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `choices contain no duplicates`() {
        val engine = ChordInversionEngine.withSeed(13)
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "Duplicate choices found: ${q.answerChoices}",
                    q.answerChoices.size,
                    q.answerChoices.distinct().size
                )
            }
        }
    }

    // ── 确定性种子 ──────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val engine1 = ChordInversionEngine.withSeed(100)
        val engine2 = ChordInversionEngine.withSeed(100)
        val q1 = engine1.generate(ChordInversionDifficulty.ADVANCED)
        val q2 = engine2.generate(ChordInversionDifficulty.ADVANCED)
        assertEquals(q1.rootMidi, q2.rootMidi)
        assertEquals(q1.chordType, q2.chordType)
        assertEquals(q1.targetInversion, q2.targetInversion)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds produce different sequences eventually`() {
        val engine1 = ChordInversionEngine.withSeed(1)
        val engine2 = ChordInversionEngine.withSeed(999)
        val targets1 = (1..50).map { engine1.generate(ChordInversionDifficulty.ADVANCED).targetInversion }
        val targets2 = (1..50).map { engine2.generate(ChordInversionDifficulty.ADVANCED).targetInversion }
        assertNotEquals(targets1, targets2)
    }

    // ── 难度缩放 ──────────────────────────────────

    @Test
    fun `beginner only uses major triad`() {
        val engine = ChordInversionEngine.withSeed(55)
        repeat(30) {
            val q = engine.generate(ChordInversionDifficulty.BEGINNER)
            assertTrue(
                "Beginner should only use MAJOR_TRIAD, got ${q.chordType}",
                q.chordType in ChordInversionDifficulty.BEGINNER.chords
            )
        }
    }

    @Test
    fun `intermediate uses only major and minor triads`() {
        val engine = ChordInversionEngine.withSeed(88)
        repeat(30) {
            val q = engine.generate(ChordInversionDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate should only use major/minor triads, got ${q.chordType}",
                q.chordType in ChordInversionDifficulty.INTERMEDIATE.chords
            )
        }
    }

    @Test
    fun `advanced uses all 6 chord types`() {
        val engine = ChordInversionEngine.withSeed(77)
        repeat(50) {
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            assertTrue(
                "Advanced chord not in advanced set: ${q.chordType}",
                q.chordType in ChordInversionDifficulty.ADVANCED.chords
            )
        }
    }

    // ── 转位有效性 ──────────────────────────────────

    @Test
    fun `target inversion is always valid for chord type`() {
        val engine = ChordInversionEngine.withSeed(33)
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            repeat(30) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Inversion ${q.targetInversion} (order=${q.targetInversion.order}) " +
                        "exceeds max for ${q.chordType} (max=${q.chordType.maxInversionOrder})",
                    q.targetInversion.order <= q.chordType.maxInversionOrder
                )
            }
        }
    }

    @Test
    fun `triad never gets third inversion as target`() {
        val engine = ChordInversionEngine.withSeed(44)
        repeat(50) {
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            if (q.chordType.category == "三和弦") {
                assertNotEquals(
                    "Triad should not get THIRD_INVERSION",
                    ChordInversion.THIRD_INVERSION,
                    q.targetInversion
                )
            }
        }
    }

    @Test
    fun `seventh chord can get third inversion as target`() {
        val seen = mutableSetOf<ChordInversion>()
        for (seed in 0L..999L) {
            val engine = ChordInversionEngine.withSeed(seed)
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            if (q.chordType.category == "七和弦") {
                seen.add(q.targetInversion)
            }
        }
        assertTrue(
            "Seventh chords should be able to get THIRD_INVERSION over many seeds",
            ChordInversion.THIRD_INVERSION in seen
        )
    }

    // ── 覆盖验证 ──────────────────────────────────

    @Test
    fun `advanced covers all 6 chord types over 1000 seeds`() {
        val seen = mutableSetOf<ChordType>()
        for (seed in 0L..999L) {
            val engine = ChordInversionEngine.withSeed(seed)
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            seen.add(q.chordType)
        }
        assertEquals(
            "Advanced should cover all 6 chord types",
            ChordType.ADVANCED_CHORDS.toSet(),
            seen
        )
    }

    @Test
    fun `intermediate covers both chord types over 200 seeds`() {
        val seen = mutableSetOf<ChordType>()
        for (seed in 0L..199L) {
            val engine = ChordInversionEngine.withSeed(seed)
            val q = engine.generate(ChordInversionDifficulty.INTERMEDIATE)
            seen.add(q.chordType)
        }
        assertEquals(
            "Intermediate should cover both major and minor triads",
            ChordType.INTERMEDIATE_CHORDS.toSet(),
            seen
        )
    }

    @Test
    fun `beginner covers both inversions over 200 seeds`() {
        val seen = mutableSetOf<ChordInversion>()
        for (seed in 0L..199L) {
            val engine = ChordInversionEngine.withSeed(seed)
            val q = engine.generate(ChordInversionDifficulty.BEGINNER)
            seen.add(q.targetInversion)
        }
        assertEquals(
            "Beginner should cover ROOT_POSITION and FIRST_INVERSION",
            listOf(ChordInversion.ROOT_POSITION, ChordInversion.FIRST_INVERSION).toSet(),
            seen
        )
    }

    @Test
    fun `intermediate covers all 3 inversions over 300 seeds`() {
        val seen = mutableSetOf<ChordInversion>()
        for (seed in 0L..299L) {
            val engine = ChordInversionEngine.withSeed(seed)
            val q = engine.generate(ChordInversionDifficulty.INTERMEDIATE)
            seen.add(q.targetInversion)
        }
        assertTrue("Should see ROOT_POSITION", ChordInversion.ROOT_POSITION in seen)
        assertTrue("Should see FIRST_INVERSION", ChordInversion.FIRST_INVERSION in seen)
        assertTrue("Should see SECOND_INVERSION", ChordInversion.SECOND_INVERSION in seen)
    }

    @Test
    fun `advanced covers all 4 inversions over 1000 seeds`() {
        val seen = mutableSetOf<ChordInversion>()
        for (seed in 0L..999L) {
            val engine = ChordInversionEngine.withSeed(seed)
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            seen.add(q.targetInversion)
        }
        assertTrue("Should see THIRD_INVERSION", ChordInversion.THIRD_INVERSION in seen)
        assertEquals(4, seen.size)
    }

    // ── 选项内容验证 ──────────────────────────────────

    @Test
    fun `all choices are valid inversion choice labels`() {
        val engine = ChordInversionEngine.withSeed(123)
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue(
                        "Choice '$choice' is not a valid inversion choice label",
                        ChordInversion.entries.any { it.choiceLabel == choice }
                    )
                }
            }
        }
    }

    @Test
    fun `target inversion choice label equals correct answer`() {
        val engine = ChordInversionEngine.withSeed(456)
        repeat(20) {
            val q = engine.generate(ChordInversionDifficulty.ADVANCED)
            assertEquals(q.targetInversion.choiceLabel, q.correctAnswer)
        }
    }

    @Test
    fun `root midi is always within difficulty range`() {
        val engine = ChordInversionEngine.withSeed(789)
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Root MIDI ${q.rootMidi} out of range [${difficulty.rootMidiMin}, ${difficulty.rootMidiMax}]",
                    q.rootMidi in difficulty.rootMidiMin..difficulty.rootMidiMax
                )
            }
        }
    }

    @Test
    fun `beginner choices include both root and first inversion`() {
        val engine = ChordInversionEngine.withSeed(222)
        val q = engine.generate(ChordInversionDifficulty.BEGINNER)
        assertTrue(
            "Beginner should have ROOT_POSITION option",
            q.answerChoices.any { it == ChordInversion.ROOT_POSITION.choiceLabel }
        )
        assertTrue(
            "Beginner should have FIRST_INVERSION option",
            q.answerChoices.any { it == ChordInversion.FIRST_INVERSION.choiceLabel }
        )
    }
}
