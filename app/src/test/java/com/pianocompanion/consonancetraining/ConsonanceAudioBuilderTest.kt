package com.pianocompanion.consonancetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协和度辨识训练音频构建器单元测试。
 *
 * 验证 PCM 缓冲区有效性、采样范围、音程半音距离、呈现方式等。
 */
class ConsonanceAudioBuilderTest {

    private val builder = ConsonanceAudioBuilder()

    private fun makeQuestion(
        interval: MusicInterval,
        presentation: Presentation = Presentation.HARMONIC,
        octaveOffset: Int = 0,
        difficulty: ConsonanceDifficulty = ConsonanceDifficulty.INTERMEDIATE
    ): ConsonanceQuestion {
        val category = interval.category
        return ConsonanceQuestion(
            interval = interval,
            category = category,
            difficulty = difficulty,
            seed = 0L,
            baseMidi = 60,
            octaveOffset = octaveOffset,
            presentation = presentation,
            answerChoices = listOf(category.fullLabel),
            correctAnswer = category.fullLabel
        )
    }

    @Test
    fun `render produces non-empty buffer`() {
        val q = makeQuestion(MusicInterval.PERFECT_FIFTH)
        val pcm = builder.render(q)
        assertTrue("Buffer should not be empty", pcm.isNotEmpty())
    }

    @Test
    fun `rendered samples are within minus1 to 1`() {
        val q = makeQuestion(MusicInterval.TRITONE)
        val pcm = builder.render(q)
        for (sample in pcm) {
            assertTrue("Sample $sample out of range", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `melodic presentation has two sequential events`() {
        val q = makeQuestion(MusicInterval.MAJOR_THIRD, presentation = Presentation.MELODIC)
        val events = builder.buildNoteEvents(q)
        assertEquals(2, events.size)
        // Second note starts after first ends
        assertTrue(events[1].onsetMs > events[0].onsetMs)
    }

    @Test
    fun `harmonic presentation has two simultaneous events`() {
        val q = makeQuestion(MusicInterval.MAJOR_THIRD, presentation = Presentation.HARMONIC)
        val events = builder.buildNoteEvents(q)
        assertEquals(2, events.size)
        assertEquals(0.0, events[0].onsetMs, 0.001)
        assertEquals(0.0, events[1].onsetMs, 0.001)
    }

    @Test
    fun `melodic buffer is longer than harmonic buffer`() {
        val melodicQ = makeQuestion(MusicInterval.MAJOR_THIRD, presentation = Presentation.MELODIC)
        val harmonicQ = makeQuestion(MusicInterval.MAJOR_THIRD, presentation = Presentation.HARMONIC)
        val melodicPcm = builder.render(melodicQ)
        val harmonicPcm = builder.render(harmonicQ)
        assertTrue(
            "Melodic (${melodicPcm.size}) should be longer than harmonic (${harmonicPcm.size})",
            melodicPcm.size > harmonicPcm.size
        )
    }

    @Test
    fun `extract pitches returns correct interval distance`() {
        for (interval in MusicInterval.ALL) {
            val q = makeQuestion(interval)
            val pitches = builder.extractPitches(q)
            assertEquals(interval.semitones, pitches.semitoneDistance)
        }
    }

    @Test
    fun `octave offset shifts both pitches`() {
        val q = makeQuestion(MusicInterval.PERFECT_FIFTH, octaveOffset = 1)
        val pitches = builder.extractPitches(q)
        assertEquals(7, pitches.semitoneDistance)
        // baseMidi=60, offset=+1 → lower = 72
        assertEquals(72, pitches.lower)
        assertEquals(79, pitches.higher)
    }

    @Test
    fun `negative octave offset works`() {
        val q = makeQuestion(MusicInterval.MINOR_THIRD, octaveOffset = -1)
        val pitches = builder.extractPitches(q)
        assertEquals(3, pitches.semitoneDistance)
        assertEquals(48, pitches.lower)
        assertEquals(51, pitches.higher)
    }

    @Test
    fun `midi to freq for A4 is 440`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.01)
    }

    @Test
    fun `midi to freq for C4 is approximately 261_63`() {
        assertEquals(261.63, builder.midiToFreq(60), 0.1)
    }

    @Test
    fun `estimate duration is positive`() {
        for (interval in MusicInterval.ALL) {
            for (pres in listOf(Presentation.MELODIC, Presentation.HARMONIC)) {
                val q = makeQuestion(interval, presentation = pres)
                assertTrue(builder.estimateDurationMs(q) > 0)
            }
        }
    }

    @Test
    fun `harmonic estimate duration is shorter than melodic`() {
        val melodicQ = makeQuestion(MusicInterval.PERFECT_FOURTH, presentation = Presentation.MELODIC)
        val harmonicQ = makeQuestion(MusicInterval.PERFECT_FOURTH, presentation = Presentation.HARMONIC)
        assertTrue(
            builder.estimateDurationMs(melodicQ) > builder.estimateDurationMs(harmonicQ)
        )
    }

    @Test
    fun `render empty events returns empty array`() {
        val pcm = builder.renderEvents(emptyList())
        assertEquals(0, pcm.size)
    }

    @Test
    fun `all intervals render without error`() {
        for (interval in MusicInterval.ALL) {
            for (pres in listOf(Presentation.MELODIC, Presentation.HARMONIC)) {
                val q = makeQuestion(interval, presentation = pres)
                val pcm = builder.render(q)
                assertTrue("Failed for $interval/$pres", pcm.isNotEmpty())
            }
        }
    }

    @Test
    fun `unison excluded - minimum semitone distance is 1`() {
        val minSemitones = MusicInterval.ALL.minOf { it.semitones }
        assertTrue("Minimum interval should be at least minor second (1)", minSemitones >= 1)
    }

    @Test
    fun `beginner question renders as melodic`() {
        val engine = ConsonanceEngine.withSeed(42)
        val q = engine.generate(ConsonanceDifficulty.BEGINNER)
        assertEquals(Presentation.MELODIC, q.presentation)
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `intermediate question renders as harmonic`() {
        val engine = ConsonanceEngine.withSeed(42)
        val q = engine.generate(ConsonanceDifficulty.INTERMEDIATE)
        assertEquals(Presentation.HARMONIC, q.presentation)
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `nyquist protection - high frequency harmonics are filtered`() {
        // Very high MIDI note (e.g., 120) with harmonics above Nyquist should not crash
        val q = ConsonanceQuestion(
            interval = MusicInterval.MAJOR_SEVENTH,
            category = ConsonanceCategory.DISSONANCE,
            difficulty = ConsonanceDifficulty.ADVANCED,
            seed = 0L,
            baseMidi = 108, // C8
            octaveOffset = 1,
            presentation = Presentation.HARMONIC,
            answerChoices = listOf("test"),
            correctAnswer = "test"
        )
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
        for (s in pcm) {
            assertTrue(s in -1.0f..1.0f)
        }
    }
}
