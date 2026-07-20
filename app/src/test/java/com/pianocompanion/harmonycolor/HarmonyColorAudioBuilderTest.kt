package com.pianocompanion.harmonycolor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和声色彩听辨训练音频构建器单元测试。
 */
class HarmonyColorAudioBuilderTest {

    private val builder = HarmonyColorAudioBuilder(sampleRate = 8000)

    private fun makeQuestion(color: HarmonyColor, root: Int = 60): HarmonyColorQuestion {
        return HarmonyColorQuestion(
            color = color,
            difficulty = HarmonyColorDifficulty.ADVANCED,
            seed = 1L,
            rootMidi = root,
            voicing = color.intervals.map { root + it },
            answerChoices = HarmonyColor.ALL.map { it.fullLabel },
            correctAnswer = color.fullLabel
        )
    }

    @Test
    fun `render produces non-empty buffer`() {
        val audio = builder.render(makeQuestion(HarmonyColor.MAJOR))
        assertTrue("Audio buffer should not be empty", audio.isNotEmpty())
    }

    @Test
    fun `render output is within valid PCM range`() {
        val audio = builder.render(makeQuestion(HarmonyColor.AUGMENTED))
        for ((idx, sample) in audio.withIndex()) {
            assertTrue("Sample $idx out of range: $sample", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `midi to freq conversion is correct`() {
        // A4 = MIDI 69 = 440 Hz
        assertEquals(440.0, builder.midiToFreq(69), 0.5)
        // A5 = MIDI 81 = 880 Hz
        assertEquals(880.0, builder.midiToFreq(81), 1.0)
        // C-1 = MIDI 0 ≈ 8.18 Hz
        assertEquals(8.18, builder.midiToFreq(0), 0.1)
    }

    @Test
    fun `major voicing has correct intervals`() {
        // Major = root + 4 + 7
        val q = makeQuestion(HarmonyColor.MAJOR, root = 60)
        assertEquals(listOf(60, 64, 67), q.voicing)
    }

    @Test
    fun `minor voicing has correct intervals`() {
        // Minor = root + 3 + 7
        val q = makeQuestion(HarmonyColor.MINOR, root = 60)
        assertEquals(listOf(60, 63, 67), q.voicing)
    }

    @Test
    fun `diminished voicing has correct intervals`() {
        // Diminished = root + 3 + 6
        val q = makeQuestion(HarmonyColor.DIMINISHED, root = 60)
        assertEquals(listOf(60, 63, 66), q.voicing)
    }

    @Test
    fun `augmented voicing has correct intervals`() {
        // Augmented = root + 4 + 8
        val q = makeQuestion(HarmonyColor.AUGMENTED, root = 60)
        assertEquals(listOf(60, 64, 68), q.voicing)
    }

    @Test
    fun `major and minor differ in middle note`() {
        val majorQ = makeQuestion(HarmonyColor.MAJOR, root = 60)
        val minorQ = makeQuestion(HarmonyColor.MINOR, root = 60)
        assertEquals(majorQ.voicing[0], minorQ.voicing[0]) // root same
        assertEquals(1, majorQ.voicing[1] - minorQ.voicing[1]) // major third one semitone higher
        assertEquals(majorQ.voicing[2], minorQ.voicing[2]) // fifth same
    }

    @Test
    fun `major and diminished differ in middle and top note`() {
        val majorQ = makeQuestion(HarmonyColor.MAJOR, root = 60)
        val dimQ = makeQuestion(HarmonyColor.DIMINISHED, root = 60)
        assertEquals(1, majorQ.voicing[1] - dimQ.voicing[1]) // third
        assertEquals(1, majorQ.voicing[2] - dimQ.voicing[2]) // fifth
    }

    @Test
    fun `major and augmented differ only in top note`() {
        val majorQ = makeQuestion(HarmonyColor.MAJOR, root = 60)
        val augQ = makeQuestion(HarmonyColor.AUGMENTED, root = 60)
        assertEquals(majorQ.voicing[0], augQ.voicing[0])
        assertEquals(majorQ.voicing[1], augQ.voicing[1])
        assertEquals(1, augQ.voicing[2] - majorQ.voicing[2])
    }

    @Test
    fun `chord events has exactly two plays`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val events = builder.buildChordEvents(q)
        assertEquals(2, events.size)
    }

    @Test
    fun `second onset is after first play ends`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val events = builder.buildChordEvents(q)
        val firstEnd = events[0].onsetMs + events[0].durationMs
        assertTrue(
            "Second onset ${events[1].onsetMs} should be >= first end $firstEnd",
            events[1].onsetMs >= firstEnd
        )
    }

    @Test
    fun `compute onsets returns two distinct times`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val onsets = builder.computeOnsets(q)
        assertEquals(2, onsets.size)
        assertTrue(onsets[1] > onsets[0])
    }

    @Test
    fun `first onset is zero`() {
        val q = makeQuestion(HarmonyColor.MINOR)
        val onsets = builder.computeOnsets(q)
        assertEquals(0.0, onsets[0], 0.001)
    }

    @Test
    fun `gap between first end and second onset equals GAP_MS`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val onsets = builder.computeOnsets(q)
        val gap = onsets[1] - HarmonyColorAudioBuilder.CHORD_DURATION_MS
        assertEquals(HarmonyColorAudioBuilder.GAP_MS, gap, 0.001)
    }

    @Test
    fun `render includes lead silence`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val audio = builder.render(q)
        val leadSamples = (8000 * HarmonyColorAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // First leadSamples should be near zero (silence)
        for (i in 0 until leadSamples - 10) {
            assertTrue(
                "Lead silence region has non-zero sample at $i: ${audio[i]}",
                kotlin.math.abs(audio[i]) < 0.001f
            )
        }
    }

    @Test
    fun `render includes tail silence`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val audio = builder.render(q)
        val tailSamples = (8000 * HarmonyColorAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        // Last tailSamples should be near zero
        for (i in audio.size - tailSamples + 10 until audio.size) {
            assertTrue(
                "Tail silence region has non-zero sample at $i: ${audio[i]}",
                kotlin.math.abs(audio[i]) < 0.001f
            )
        }
    }

    @Test
    fun `audio has non-zero content in music region`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val audio = builder.render(q)
        val leadSamples = (8000 * HarmonyColorAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        var maxAmplitude = 0.0f
        for (i in leadSamples until audio.size) {
            if (kotlin.math.abs(audio[i]) > maxAmplitude) {
                maxAmplitude = kotlin.math.abs(audio[i])
            }
        }
        assertTrue("Music region should have audible content, max=$maxAmplitude", maxAmplitude > 0.01f)
    }

    @Test
    fun `render all four colors produces valid audio`() {
        HarmonyColor.ALL.forEach { color ->
            val audio = builder.render(makeQuestion(color))
            assertTrue("$color audio should not be empty", audio.isNotEmpty())
            assertTrue("$color audio should be within range", audio.all { it in -1.0f..1.0f })
        }
    }

    @Test
    fun `note frequency matches midi to freq`() {
        assertEquals(builder.midiToFreq(60), builder.noteFrequency(60), 0.001)
        assertEquals(builder.midiToFreq(69), builder.noteFrequency(69), 0.001)
    }

    @Test
    fun `estimate duration is positive`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val duration = builder.estimateDurationMs(q)
        assertTrue("Duration should be positive: $duration", duration > 0)
    }

    @Test
    fun `estimate duration includes lead and tail`() {
        val q = makeQuestion(HarmonyColor.MAJOR)
        val duration = builder.estimateDurationMs(q)
        val minimum = (HarmonyColorAudioBuilder.LEAD_SILENCE_MS +
            HarmonyColorAudioBuilder.SECOND_ONSET_MS +
            HarmonyColorAudioBuilder.CHORD_DURATION_MS +
            HarmonyColorAudioBuilder.TAIL_SILENCE_MS).toLong()
        assertTrue("Duration $duration should be >= minimum $minimum", duration >= minimum)
    }

    @Test
    fun `render empty voicing returns empty`() {
        val events = listOf<HarmonyColorAudioBuilder.ChordEvent>()
        val audio = builder.renderEvents(events)
        assertEquals(0, audio.size)
    }

    @Test
    fun `major and minor at same root produce different audio waveforms`() {
        val majorAudio = builder.render(makeQuestion(HarmonyColor.MAJOR, root = 60))
        val minorAudio = builder.render(makeQuestion(HarmonyColor.MINOR, root = 60))
        // The two should differ because the middle note differs (different harmonic content)
        var anyDifference = false
        val minLen = minOf(majorAudio.size, minorAudio.size)
        for (i in 0 until minLen step 5) {
            if (kotlin.math.abs(majorAudio[i] - minorAudio[i]) > 0.0005f) {
                anyDifference = true
                break
            }
        }
        assertTrue("Major and Minor audio should differ", anyDifference)
    }

    @Test
    fun `diminished and augmented at same root produce different audio waveforms`() {
        val dimAudio = builder.render(makeQuestion(HarmonyColor.DIMINISHED, root = 60))
        val augAudio = builder.render(makeQuestion(HarmonyColor.AUGMENTED, root = 60))
        var anyDifference = false
        val minLen = minOf(dimAudio.size, augAudio.size)
        for (i in 0 until minLen step 5) {
            if (kotlin.math.abs(dimAudio[i] - augAudio[i]) > 0.0005f) {
                anyDifference = true
                break
            }
        }
        assertTrue("Diminished and Augmented audio should differ", anyDifference)
    }

    @Test
    fun `chord voicing used in events matches question voicing`() {
        val q = makeQuestion(HarmonyColor.MAJOR, root = 55)
        val events = builder.buildChordEvents(q)
        events.forEach { event ->
            assertEquals(q.voicing, event.voicing)
        }
    }
}
