package com.pianocompanion.scaledegreetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调内音级辨识训练音频构建器单元测试。
 */
class ScaleDegreeAudioBuilderTest {

    private val builder = ScaleDegreeAudioBuilder()

    private fun makeQuestion(
        degree: ScaleDegree = ScaleDegree.DO,
        tonicMidi: Int = 60,
        difficulty: ScaleDegreeDifficulty = ScaleDegreeDifficulty.ADVANCED
    ): ScaleDegreeQuestion {
        return ScaleDegreeQuestion(
            degree = degree,
            difficulty = difficulty,
            seed = 1L,
            tonicMidi = tonicMidi,
            answerChoices = difficulty.degrees.map { it.fullLabel },
            correctAnswer = degree.fullLabel
        )
    }

    @Test
    fun `render produces non-empty buffer`() {
        val q = makeQuestion()
        val buffer = builder.render(q)
        assertTrue("Buffer should be non-empty", buffer.isNotEmpty())
    }

    @Test
    fun `all samples are in valid range`() {
        val q = makeQuestion(degree = ScaleDegree.TI)
        val buffer = builder.render(q)
        buffer.forEach { sample ->
            assertTrue("Sample $sample below -1.0", sample >= -1.0f)
            assertTrue("Sample $sample above 1.0", sample <= 1.0f)
        }
    }

    @Test
    fun `render for each degree produces audio`() {
        ScaleDegree.ALL.forEach { degree ->
            val q = makeQuestion(degree = degree)
            val buffer = builder.render(q)
            assertTrue("Buffer for ${degree.name} should be non-empty", buffer.isNotEmpty())
        }
    }

    @Test
    fun `buffer length is consistent with estimate`() {
        val q = makeQuestion(degree = ScaleDegree.SOL)
        val buffer = builder.render(q)
        val estimatedMs = builder.estimateDurationMs(q)
        val estimatedSamples = (ScaleDegreeAudioBuilder.DEFAULT_SAMPLE_RATE * estimatedMs / 1000.0).toInt()
        // 允许 1 个采样误差（浮点取整）
        assertTrue(
            "Buffer length ${buffer.size} should be close to estimate $estimatedSamples",
            kotlin.math.abs(buffer.size - estimatedSamples) <= 2
        )
    }

    @Test
    fun `higher degree produces higher target pitch`() {
        // 目标音 MIDI 随音级升高而升高（主音固定）
        val tonic = 60
        val doQ = makeQuestion(degree = ScaleDegree.DO, tonicMidi = tonic)
        val solQ = makeQuestion(degree = ScaleDegree.SOL, tonicMidi = tonic)
        val tiQ = makeQuestion(degree = ScaleDegree.TI, tonicMidi = tonic)

        assertTrue("Do target should be lowest", doQ.targetMidi < solQ.targetMidi)
        assertTrue("Sol target should be below Ti", solQ.targetMidi < tiQ.targetMidi)
    }

    @Test
    fun `tonic chord has correct intervals`() {
        val tonic = 60
        val chord = builder.buildTonicChord(tonic)
        assertEquals(tonic, chord.root)
        assertEquals(tonic + 4, chord.third) // 大三度
        assertEquals(tonic + 7, chord.fifth) // 纯五度
        assertEquals(4, chord.thirdSemitones)
        assertEquals(7, chord.fifthSemitones)
    }

    @Test
    fun `buildNoteEvents contains chord arpeggio and target`() {
        val q = makeQuestion(degree = ScaleDegree.MI, tonicMidi = 60)
        val events = builder.buildNoteEvents(q)
        // 主和弦 3 个音 + 目标音 1 个
        assertEquals(4, events.size)

        // 前三个为主和弦
        val chordMidis = events.take(3).map { it.midi }.toSet()
        assertTrue("Chord root 60 should be present", 60 in chordMidis)
        assertTrue("Chord third 64 should be present", 64 in chordMidis)
        assertTrue("Chord fifth 67 should be present", 67 in chordMidis)

        // 最后一个为目标音
        val targetEvent = events.last()
        assertEquals(q.targetMidi, targetEvent.midi)
    }

    @Test
    fun `arpeggio events have increasing onsets`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        val onsets = events.map { it.onsetMs }
        for (i in 1 until onsets.size) {
            assertTrue(
                "Onsets should be non-decreasing, got ${onsets[i - 1]} then ${onsets[i]}",
                onsets[i] >= onsets[i - 1]
            )
        }
    }

    @Test
    fun `target event starts after chord total plus gap`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        val targetOnset = events.last().onsetMs
        val expectedMin = ScaleDegreeAudioBuilder.CHORD_TOTAL_MS + ScaleDegreeAudioBuilder.TARGET_GAP_MS
        assertEquals(expectedMin, targetOnset, 0.001)
    }

    @Test
    fun `render events with empty list returns empty buffer`() {
        val buffer = builder.renderEvents(emptyList())
        assertEquals(0, buffer.size)
    }

    @Test
    fun `midiToFreq is correct for A4`() {
        // A4 = 440Hz, MIDI 69
        val freq = builder.midiToFreq(69)
        assertEquals(440.0, freq, 0.01)
    }

    @Test
    fun `midiToFreq doubles per octave`() {
        val c4 = builder.midiToFreq(60)
        val c5 = builder.midiToFreq(72)
        assertEquals(2.0, c5 / c4, 0.001)
    }

    @Test
    fun `buffer contains audible non-zero samples`() {
        val q = makeQuestion(degree = ScaleDegree.MI)
        val buffer = builder.render(q)
        val nonZero = buffer.count { kotlin.math.abs(it) > 0.001f }
        assertTrue(
            "Buffer should have many non-zero samples, got $nonZero / ${buffer.size}",
            nonZero > buffer.size / 10
        )
    }

    @Test
    fun `different tonics produce different length buffers only due to duration`() {
        // 不同主音不应影响缓冲区长度（时序参数相同）
        val q1 = makeQuestion(degree = ScaleDegree.DO, tonicMidi = 48)
        val q2 = makeQuestion(degree = ScaleDegree.DO, tonicMidi = 69)
        val b1 = builder.render(q1)
        val b2 = builder.render(q2)
        assertEquals(b1.size, b2.size)
    }

    @Test
    fun `estimate duration is positive`() {
        ScaleDegree.ALL.forEach { degree ->
            val q = makeQuestion(degree = degree)
            val ms = builder.estimateDurationMs(q)
            assertTrue("Duration for $degree should be positive: $ms", ms > 0)
        }
    }

    @Test
    fun `lead silence is at buffer start`() {
        val q = makeQuestion()
        val buffer = builder.render(q)
        val leadSamples = (ScaleDegreeAudioBuilder.DEFAULT_SAMPLE_RATE *
            ScaleDegreeAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前导静音区应为零或接近零
        val leadMax = (0 until leadSamples).maxOfOrNull { kotlin.math.abs(buffer[it]) } ?: 0f
        assertTrue(
            "Lead silence region should be near-zero, max=$leadMax",
            leadMax < 0.01f
        )
    }
}
