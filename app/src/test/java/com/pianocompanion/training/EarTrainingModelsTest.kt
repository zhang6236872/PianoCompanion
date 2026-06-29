package com.pianocompanion.training

import org.junit.Assert.*
import org.junit.Test

/**
 * 听音训练数据模型单元测试。
 *
 * 验证 [IntervalType]、[ChordType]、[ScaleType] 的音乐理论正确性，
 * 以及 [EarTrainingQuestion]、[AnswerRecord] 的数据模型行为。
 */
class EarTrainingModelsTest {

    // ── IntervalType ──────────────────────────────────────

    @Test
    fun `音程半音数连续递增 1-12`() {
        val semitones = IntervalType.entries.map { it.semitones }
        assertEquals((1..12).toList(), semitones)
    }

    @Test
    fun `fromSemitones 正确查找`() {
        assertEquals(IntervalType.MINOR_2ND, IntervalType.fromSemitones(1))
        assertEquals(IntervalType.MAJOR_3RD, IntervalType.fromSemitones(4))
        assertEquals(IntervalType.PERFECT_5TH, IntervalType.fromSemitones(7))
        assertEquals(IntervalType.PERFECT_OCTAVE, IntervalType.fromSemitones(12))
    }

    @Test
    fun `fromSemitones 无效值返回 null`() {
        assertNull(IntervalType.fromSemitones(0))
        assertNull(IntervalType.fromSemitones(13))
        assertNull(IntervalType.fromSemitones(-1))
    }

    @Test
    fun `音程简称不为空`() {
        IntervalType.entries.forEach {
            assertTrue("${it.name} 简称为空", it.abbreviation.isNotEmpty())
            assertTrue("${it.name} 全称为空", it.fullName.isNotEmpty())
        }
    }

    // ── ChordType ────────────────────────────────────────

    @Test
    fun `和弦音程以 0 开头`() {
        ChordType.entries.forEach { chord ->
            assertEquals("${chord.name} 第一个音不是 0", 0, chord.intervals.first())
        }
    }

    @Test
    fun `和弦音程严格递增`() {
        ChordType.entries.forEach { chord ->
            for (i in 1 until chord.intervals.size) {
                assertTrue(
                    "${chord.name} 音程不递增: ${chord.intervals}",
                    chord.intervals[i] > chord.intervals[i - 1]
                )
            }
        }
    }

    @Test
    fun `大三和弦音程为 0-4-7`() {
        assertEquals(listOf(0, 4, 7), ChordType.MAJOR.intervals)
    }

    @Test
    fun `小三和弦音程为 0-3-7`() {
        assertEquals(listOf(0, 3, 7), ChordType.MINOR.intervals)
    }

    @Test
    fun `减三和弦音程为 0-3-6`() {
        assertEquals(listOf(0, 3, 6), ChordType.DIMINISHED.intervals)
    }

    @Test
    fun `增三和弦音程为 0-4-8`() {
        assertEquals(listOf(0, 4, 8), ChordType.AUGMENTED.intervals)
    }

    @Test
    fun `属七和弦音程为 0-4-7-10`() {
        assertEquals(listOf(0, 4, 7, 10), ChordType.DOMINANT_7TH.intervals)
    }

    @Test
    fun `大七和弦音程为 0-4-7-11`() {
        assertEquals(listOf(0, 4, 7, 11), ChordType.MAJOR_7TH.intervals)
    }

    @Test
    fun `TRIADS 包含四种三和弦`() {
        assertEquals(4, ChordType.TRIADS.size)
        assertTrue(ChordType.TRIADS.containsAll(listOf(
            ChordType.MAJOR, ChordType.MINOR, ChordType.DIMINISHED, ChordType.AUGMENTED
        )))
    }

    @Test
    fun `SEVENTH_CHORDS 包含三种七和弦`() {
        assertEquals(3, ChordType.SEVENTH_CHORDS.size)
    }

    // ── ScaleType ────────────────────────────────────────

    @Test
    fun `音阶音程以 0 开头 12 结尾（八度）`() {
        ScaleType.entries.forEach { scale ->
            assertEquals("${scale.name} 第一个音不是 0", 0, scale.intervals.first())
            assertEquals("${scale.name} 最后一个音不是八度(12)", 12, scale.intervals.last())
        }
    }

    @Test
    fun `音阶音程严格递增`() {
        ScaleType.entries.forEach { scale ->
            for (i in 1 until scale.intervals.size) {
                assertTrue(
                    "${scale.name} 音程不递增: ${scale.intervals}",
                    scale.intervals[i] > scale.intervals[i - 1]
                )
            }
        }
    }

    @Test
    fun `大调音阶为全全半全全全半`() {
        // 大调音阶半音模式：2,2,1,2,2,2,1
        val expected = listOf(0, 2, 4, 5, 7, 9, 11, 12)
        assertEquals(expected, ScaleType.MAJOR.intervals)
    }

    @Test
    fun `自然小调音阶为全半全全半全全`() {
        // 自然小调半音模式：2,1,2,2,1,2,2
        val expected = listOf(0, 2, 3, 5, 7, 8, 10, 12)
        assertEquals(expected, ScaleType.NATURAL_MINOR.intervals)
    }

    @Test
    fun `和声小调第 7 音升高半音`() {
        // 和声小调 = 自然小调 + 第 7 音升半音
        // 0,2,3,5,7,8,11,12
        assertEquals(listOf(0, 2, 3, 5, 7, 8, 11, 12), ScaleType.HARMONIC_MINOR.intervals)
    }

    @Test
    fun `半音阶有 13 个音`() {
        assertEquals(13, ScaleType.CHROMATIC.intervals.size)
        assertEquals((0..12).toList(), ScaleType.CHROMATIC.intervals)
    }

    @Test
    fun `全音阶全为全音间隔`() {
        // 全音阶：0,2,4,6,8,10,12
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12), ScaleType.WHOLE_TONE.intervals)
    }

    // ── ExerciseType / Difficulty / PlayMode ─────────────

    @Test
    fun `ExerciseType ALL 包含全部三种`() {
        assertEquals(3, ExerciseType.ALL.size)
        assertTrue(ExerciseType.ALL.containsAll(listOf(
            ExerciseType.INTERVAL, ExerciseType.CHORD, ExerciseType.SCALE
        )))
    }

    @Test
    fun `Difficulty ALL 包含全部三种`() {
        assertEquals(3, Difficulty.ALL.size)
        assertTrue(Difficulty.ALL.containsAll(listOf(
            Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.ADVANCED
        )))
    }

    @Test
    fun `PlayMode 有三种模式`() {
        assertEquals(3, PlayMode.entries.size)
    }

    @Test
    fun `枚举 displayName 不为空`() {
        ExerciseType.ALL.forEach { assertTrue(it.displayName.isNotEmpty()) }
        Difficulty.ALL.forEach { assertTrue(it.displayName.isNotEmpty()) }
        PlayMode.entries.forEach { assertTrue(it.displayName.isNotEmpty()) }
    }

    // ── EarTrainingQuestion ───────────────────────────────

    @Test
    fun `isMultipleChoice 选项大于 1 时为 true`() {
        val q = EarTrainingQuestion(
            exerciseType = ExerciseType.INTERVAL,
            playMode = PlayMode.ASCENDING,
            midiNotes = listOf(60, 64),
            correctAnswer = "大三度",
            answerChoices = listOf("大三度", "小三度", "纯四度"),
            displayInfo = "M3"
        )
        assertTrue(q.isMultipleChoice)
    }

    @Test
    fun `isMultipleChoice 选项等于 1 时为 false`() {
        val q = EarTrainingQuestion(
            exerciseType = ExerciseType.INTERVAL,
            playMode = PlayMode.ASCENDING,
            midiNotes = listOf(60, 64),
            correctAnswer = "大三度",
            answerChoices = listOf("大三度"),
            displayInfo = "M3"
        )
        assertFalse(q.isMultipleChoice)
    }

    // ── AnswerRecord ──────────────────────────────────────

    @Test
    fun `AnswerRecord 答对时 correctAnswer 为 null`() {
        val q = EarTrainingQuestion(
            exerciseType = ExerciseType.CHORD, playMode = PlayMode.BLOCK,
            midiNotes = listOf(60, 64, 67), correctAnswer = "大三和弦",
            answerChoices = listOf("大三和弦"), displayInfo = ""
        )
        val record = AnswerRecord(q, "大三和弦", true)
        assertTrue(record.isCorrect)
        assertNull(record.correctAnswer)
    }

    @Test
    fun `AnswerRecord 答错时 correctAnswer 返回正确答案`() {
        val q = EarTrainingQuestion(
            exerciseType = ExerciseType.CHORD, playMode = PlayMode.BLOCK,
            midiNotes = listOf(60, 64, 67), correctAnswer = "大三和弦",
            answerChoices = listOf("大三和弦", "小三和弦"), displayInfo = ""
        )
        val record = AnswerRecord(q, "小三和弦", false)
        assertFalse(record.isCorrect)
        assertEquals("大三和弦", record.correctAnswer)
    }
}
