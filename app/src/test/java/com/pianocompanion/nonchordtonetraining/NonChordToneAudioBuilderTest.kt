package com.pianocompanion.nonchordtonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦外音辨识训练音频构建器单元测试。
 *
 * 验证 PCM 缓冲区有效性、采样范围、和弦+旋律事件结构、音高提取等。
 */
class NonChordToneAudioBuilderTest {

    private val builder = NonChordToneAudioBuilder()

    private fun makeQuestion(
        type: NonChordToneType,
        difficulty: NonChordToneDifficulty = NonChordToneDifficulty.ADVANCED
    ): NonChordToneQuestion {
        val template = type.templates.first()
        return NonChordToneQuestion(
            type = type,
            template = template,
            difficulty = difficulty,
            seed = 0L,
            rootMidi = 60,
            answerChoices = listOf(type.fullLabel),
            correctAnswer = type.fullLabel
        )
    }

    @Test
    fun `render produces non-empty buffer`() {
        val q = makeQuestion(NonChordToneType.PASSING_TONE)
        val pcm = builder.render(q)
        assertTrue("Buffer should not be empty", pcm.isNotEmpty())
    }

    @Test
    fun `rendered samples are within minus1 to 1`() {
        for (type in NonChordToneType.ALL) {
            val q = makeQuestion(type)
            val pcm = builder.render(q)
            for (sample in pcm) {
                assertTrue("${type} sample $sample out of range", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `buildNoteEvents includes 3 chord notes and 3 melody notes`() {
        val q = makeQuestion(NonChordToneType.NEIGHBOR_TONE)
        val events = builder.buildNoteEvents(q)
        // 3 和弦音 + 3 旋律音 = 6 个事件
        assertEquals(6, events.size)
    }

    @Test
    fun `chord notes are in lower octave`() {
        val q = makeQuestion(NonChordToneType.PASSING_TONE)
        val events = builder.buildNoteEvents(q)
        val chordMidi = events.filter { it.gain == NonChordToneAudioBuilder.CHORD_GAIN }.map { it.midi }
        // 根音 C3 = 48, E3 = 52, G3 = 55
        assertEquals(listOf(48, 52, 55), chordMidi.sorted())
    }

    @Test
    fun `melody notes follow the template offset by root`() {
        val q = makeQuestion(NonChordToneType.APPOGGIATURA)
        val events = builder.buildNoteEvents(q)
        val melodyMidi = events.filter { it.gain == NonChordToneAudioBuilder.MELODY_GAIN }.map { it.midi }
        assertEquals(q.melodyMidi, melodyMidi)
    }

    @Test
    fun `chord notes all start at onset 0`() {
        val q = makeQuestion(NonChordToneType.ESCAPE_TONE)
        val events = builder.buildNoteEvents(q)
        val chordEvents = events.filter { it.gain == NonChordToneAudioBuilder.CHORD_GAIN }
        assertTrue(chordEvents.all { it.onsetMs == 0.0 })
    }

    @Test
    fun `melody notes are sequential`() {
        val q = makeQuestion(NonChordToneType.PASSING_TONE)
        val events = builder.buildNoteEvents(q)
        val melodyEvents = events.filter { it.gain == NonChordToneAudioBuilder.MELODY_GAIN }
        // 每个后续旋律音的 onset 严格大于前一个
        for (i in 1 until melodyEvents.size) {
            assertTrue(
                "Melody note $i onset should be after ${i - 1}",
                melodyEvents[i].onsetMs > melodyEvents[i - 1].onsetMs
            )
        }
    }

    @Test
    fun `chord duration covers the whole melody`() {
        val q = makeQuestion(NonChordToneType.PASSING_TONE)
        val events = builder.buildNoteEvents(q)
        val chordDuration = events.first { it.gain == NonChordToneAudioBuilder.CHORD_GAIN }.durationMs
        val lastMelodyEnd = events
            .filter { it.gain == NonChordToneAudioBuilder.MELODY_GAIN }
            .maxOf { it.onsetMs + it.durationMs }
        assertTrue(
            "Chord ($chordDuration) should last at least as long as melody ($lastMelodyEnd)",
            chordDuration >= lastMelodyEnd
        )
    }

    @Test
    fun `extract pitches returns the 3 melody notes`() {
        for (type in NonChordToneType.ALL) {
            val q = makeQuestion(type)
            val pitches = builder.extractPitches(q)
            assertEquals(3, pitches.notes.size)
            assertEquals(q.melodyMidi, pitches.notes)
            // 中间音（和弦外音）= 旋律第二个音
            assertEquals(q.melodyMidi[1], pitches.nonChordMidi)
        }
    }

    @Test
    fun `non-chord midi is the middle melody note`() {
        val q = makeQuestion(NonChordToneType.PASSING_TONE)
        val pitches = builder.extractPitches(q)
        assertEquals(q.template[1] + 60, pitches.nonChordMidi)
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
        for (type in NonChordToneType.ALL) {
            val q = makeQuestion(type)
            assertTrue("${type} duration should be positive", builder.estimateDurationMs(q) > 0)
        }
    }

    @Test
    fun `render empty events returns empty array`() {
        val pcm = builder.renderEvents(emptyList())
        assertEquals(0, pcm.size)
    }

    @Test
    fun `all types render without error`() {
        for (type in NonChordToneType.ALL) {
            for (template in type.templates) {
                val q = NonChordToneQuestion(
                    type = type,
                    template = template,
                    difficulty = NonChordToneDifficulty.ADVANCED,
                    seed = 0L,
                    rootMidi = 60,
                    answerChoices = listOf(type.fullLabel),
                    correctAnswer = type.fullLabel
                )
                val pcm = builder.render(q)
                assertTrue("Failed for $type / $template", pcm.isNotEmpty())
            }
        }
    }

    @Test
    fun `generated engine question renders correctly`() {
        val engine = NonChordToneEngine.withSeed(42)
        for (difficulty in NonChordToneDifficulty.ALL) {
            repeat(5) {
                val q = engine.generate(difficulty)
                val pcm = builder.render(q)
                assertTrue("${difficulty} render failed", pcm.isNotEmpty())
                for (s in pcm) assertTrue(s in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `nyquist protection - very low chord does not crash`() {
        // 极低根音仍能正常渲染（高次谐波被 Nyquist 过滤）
        val q = NonChordToneQuestion(
            type = NonChordToneType.PASSING_TONE,
            template = listOf(0, 2, 4),
            difficulty = NonChordToneDifficulty.ADVANCED,
            seed = 0L,
            rootMidi = 24, // C1
            answerChoices = listOf("test"),
            correctAnswer = "test"
        )
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
        for (s in pcm) assertTrue(s in -1.0f..1.0f)
    }

    @Test
    fun `buffer length matches estimated duration`() {
        val q = makeQuestion(NonChordToneType.NEIGHBOR_TONE)
        val pcm = builder.render(q)
        val estMs = builder.estimateDurationMs(q)
        val expectedSamples = (NonChordToneAudioBuilder.DEFAULT_SAMPLE_RATE * estMs / 1000.0).toInt()
        // 允许 ±2 采样误差（浮点取整）
        assertTrue(
            "Buffer len ${pcm.size} vs expected ~$expectedSamples",
            kotlin.math.abs(pcm.size - expectedSamples) <= 2
        )
    }

    @Test
    fun `melody gain is higher than chord gain`() {
        // 旋律增益高于和弦增益以突出轮廓
        assertTrue(
            NonChordToneAudioBuilder.MELODY_GAIN > NonChordToneAudioBuilder.CHORD_GAIN
        )
    }
}
