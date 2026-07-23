package com.pianocompanion.intervalsequence

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class IntervalSequenceEngineTest {

    @Test
    fun `BEGINNER 选项数匹配 choiceCount`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.BEGINNER, Random(42L))
        val q = engine.generate(100L)
        assertEquals(IntervalSequenceDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
    }

    @Test
    fun `INTERMEDIATE 选项数匹配 choiceCount`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.INTERMEDIATE, Random(42L))
        val q = engine.generate(200L)
        assertEquals(IntervalSequenceDifficulty.INTERMEDIATE.choiceCount, q.answerChoices.size)
    }

    @Test
    fun `ADVANCED 选项数匹配 choiceCount`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        val q = engine.generate(300L)
        assertEquals(IntervalSequenceDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        for (seed in 1L..50L) {
            val q = engine.generate(seed)
            assertEquals("seed=$seed", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `正确答案在选项中`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.INTERMEDIATE, Random(42L))
        for (seed in 1L..50L) {
            val q = engine.generate(seed)
            assertTrue("seed=$seed", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `序列长度匹配难度`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.BEGINNER, Random(42L))
        val q = engine.generate(1L)
        assertEquals(IntervalSequenceDifficulty.BEGINNER.sequenceLength, q.sequenceLength)
    }

    @Test
    fun `确定性种子复现`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        val q1 = engine.generate(12345L)
        val q2 = engine.generate(12345L)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.targetSequence.displayString, q2.targetSequence.displayString)
    }

    @Test
    fun `不同种子产生不同序列`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        val answers = mutableSetOf<String>()
        for (seed in 1L..20L) {
            answers.add(engine.generate(seed).correctAnswer)
        }
        assertTrue("不同种子应产生不同序列", answers.size > 1)
    }

    @Test
    fun `BEGINNER 仅用 4 种音程`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.BEGINNER, Random(42L))
        val validNames = IntervalSequenceDifficulty.BEGINNER.availableIntervals.map { it.shortName }.toSet()
        for (seed in 1L..50L) {
            val q = engine.generate(seed)
            q.targetSequence.entries.forEach { entry ->
                assertTrue(
                    "seed=$seed entry=${entry.interval}",
                    entry.interval in IntervalSequenceDifficulty.BEGINNER.availableIntervals
                )
            }
        }
    }

    @Test
    fun `INTERMEDIATE 使用更多音程`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.INTERMEDIATE, Random(42L))
        val allIntervalsUsed = mutableSetOf<IntervalType>()
        for (seed in 1L..200L) {
            val q = engine.generate(seed)
            q.targetSequence.entries.forEach { allIntervalsUsed.add(it.interval) }
        }
        assertTrue("应使用多种音程", allIntervalsUsed.size >= 3)
    }

    @Test
    fun `大样本覆盖验证`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        for (seed in 1L..200L) {
            val q = engine.generate(seed)
            assertEquals(IntervalSequenceDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
            assertTrue(q.correctAnswer in q.answerChoices)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `withSeed 工厂方法生成有效题目`() {
        val engine = IntervalSequenceEngine.withSeed(IntervalSequenceDifficulty.INTERMEDIATE, 42L)
        val q = engine.generate(1L)
        assertNotNull(q)
        assertEquals(IntervalSequenceDifficulty.INTERMEDIATE, q.difficulty)
    }

    @Test
    fun `midiNotes 长度 = sequenceLength + 1`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        val q = engine.generate(1L)
        assertEquals(q.sequenceLength + 1, q.midiNotes.size)
    }

    @Test
    fun `旋律线首音 = startMidi`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.BEGINNER, Random(42L))
        val q = engine.generate(1L)
        assertEquals(IntervalSequenceDifficulty.BEGINNER.startMidi, q.midiNotes.first())
    }

    @Test
    fun `旋律线各音间距始终匹配音程半音数`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        val q = engine.generate(77L)
        val notes = q.midiNotes
        q.targetSequence.entries.forEachIndexed { i, entry ->
            val diff = notes[i + 1] - notes[i]
            assertEquals("entry $i: ${entry.fullDescription}", entry.signedSemitones, diff)
        }
    }

    @Test
    fun `上行音程使后音高于前音`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.BEGINNER, Random(42L))
        val q = engine.generate(5L)
        val ascendingEntries = q.targetSequence.entries.filter { it.ascending && it.interval != IntervalType.UNISON }
        if (ascendingEntries.isNotEmpty()) {
            val idx = q.targetSequence.entries.indexOf(ascendingEntries.first())
            assertTrue(q.midiNotes[idx + 1] > q.midiNotes[idx])
        }
    }

    @Test
    fun `下行音程使后音低于前音`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.BEGINNER, Random(42L))
        val q = engine.generate(10L)
        val descendingEntries = q.targetSequence.entries.filter { !it.ascending }
        if (descendingEntries.isNotEmpty()) {
            val idx = q.targetSequence.entries.indexOf(descendingEntries.first())
            assertTrue(q.midiNotes[idx + 1] < q.midiNotes[idx])
        }
    }

    @Test
    fun `所有选项都是合法序列展示`() {
        val engine = IntervalSequenceEngine(IntervalSequenceDifficulty.ADVANCED, Random(42L))
        val q = engine.generate(1L)
        q.answerChoices.forEach { choice ->
            assertTrue("choice=$choice", choice.isNotBlank())
            assertTrue(choice.contains("↑") || choice.contains("↓"))
        }
    }
}
