package com.pianocompanion.scaletraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音阶听辨训练音频构建器单元测试。
 */
class ScaleTrainingAudioBuilderTest {

    private val builder = ScaleTrainingAudioBuilder()

    // ── 基本渲染 ──────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        val q = engine.generate(ScaleDifficulty.BEGINNER)
        val audio = builder.render(q)
        assertTrue("Audio buffer should be non-empty", audio.isNotEmpty())
    }

    @Test
    fun `render for all difficulties produces non-empty`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            val q = engine.generate(difficulty)
            val audio = builder.render(q)
            assertTrue("Difficulty $difficulty: audio should be non-empty", audio.isNotEmpty())
        }
    }

    @Test
    fun `render for all scale types produces non-empty`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        // Generate multiple questions to cover different scale types
        repeat(30) {
            val q = engine.generate(ScaleDifficulty.ADVANCED)
            val audio = builder.render(q)
            assertTrue("Audio should be non-empty", audio.isNotEmpty())
        }
    }

    // ── 不削波 ────────────────────────────────────────────

    @Test
    fun `render output stays within -1 to 1`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            val q = engine.generate(difficulty)
            val audio = builder.render(q)
            for (sample in audio) {
                assertTrue("Sample $sample below -1.0", sample >= -1.0f)
                assertTrue("Sample $sample above 1.0", sample <= 1.0f)
            }
        }
    }

    // ── 不同音阶产生不同音频 ──────────────────────────────

    @Test
    fun `different scales produce different audio`() {
        val majorNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 60, ScaleDirection.ASCENDING)
        val minorNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.NATURAL_MINOR, 60, ScaleDirection.ASCENDING)

        val majorAudio = builder.renderScale(majorNotes)
        val minorAudio = builder.renderScale(minorNotes)

        // They should differ in content
        var anyDiff = false
        val minLen = minOf(majorAudio.size, minorAudio.size)
        for (i in 0 until minLen) {
            if (majorAudio[i] != minorAudio[i]) {
                anyDiff = true
                break
            }
        }
        assertTrue("Major and minor scales should produce different audio", anyDiff)
    }

    @Test
    fun `different tonics produce different audio`() {
        val notes1 = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 48, ScaleDirection.ASCENDING)
        val notes2 = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 55, ScaleDirection.ASCENDING)

        val audio1 = builder.renderScale(notes1)
        val audio2 = builder.renderScale(notes2)

        assert(audio1.contentEquals(audio2).not()) { "Different tonics should produce different audio" }
    }

    // ── 空输入 ────────────────────────────────────────────

    @Test
    fun `renderScale with empty notes returns empty array`() {
        val audio = builder.renderScale(emptyList())
        assertEquals(0, audio.size)
    }

    // ── 长度合理性 ────────────────────────────────────────

    @Test
    fun `diatonic scale audio is longer than pentatonic`() {
        val diatonicNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 60, ScaleDirection.ASCENDING)
        val pentatonicNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR_PENTATONIC, 60, ScaleDirection.ASCENDING)

        val diatonicAudio = builder.renderScale(diatonicNotes)
        val pentatonicAudio = builder.renderScale(pentatonicNotes)

        assertTrue(
            "Diatonic (8 notes) should be longer than pentatonic (6 notes)",
            diatonicAudio.size > pentatonicAudio.size
        )
    }

    @Test
    fun `more notes produce longer audio`() {
        val majorNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 60, ScaleDirection.ASCENDING)
        val pentaNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR_PENTATONIC, 60, ScaleDirection.ASCENDING)

        assertTrue("Major has more notes", majorNotes.size > pentaNotes.size)

        val majorAudio = builder.renderScale(majorNotes)
        val pentaAudio = builder.renderScale(pentaNotes)

        assertTrue("More notes should produce longer audio", majorAudio.size > pentaAudio.size)
    }

    // ── estimateDurationMs ────────────────────────────────

    @Test
    fun `estimateDurationMs is positive`() {
        val engine = ScaleTrainingEngine.withSeed(1L)
        for (difficulty in ScaleDifficulty.ALL) {
            val q = engine.generate(difficulty)
            val duration = builder.estimateDurationMs(q)
            assertTrue("Duration should be positive for $difficulty", duration > 0)
        }
    }

    @Test
    fun `estimateDurationMs formula is correct for diatonic scale`() {
        // Create a question with known note count
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 60, ScaleDirection.ASCENDING)
        val q = ScaleQuestion(
            type = ScaleType.MAJOR,
            tonicMidi = 60,
            tonicName = "C",
            difficulty = ScaleDifficulty.BEGINNER,
            direction = ScaleDirection.ASCENDING,
            midiNotes = notes,
            answerChoices = listOf("大调音阶", "自然小调"),
            correctAnswer = "大调音阶"
        )

        val noteCount = 8
        val expected = ScaleTrainingAudioBuilder.LEAD_SILENCE_MS +
            noteCount * ScaleTrainingAudioBuilder.NOTE_DURATION_MS +
            (noteCount - 1) * ScaleTrainingAudioBuilder.NOTE_GAP_MS +
            ScaleTrainingAudioBuilder.TAIL_SILENCE_MS

        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs formula is correct for pentatonic scale`() {
        val notes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR_PENTATONIC, 60, ScaleDirection.ASCENDING)
        val q = ScaleQuestion(
            type = ScaleType.MAJOR_PENTATONIC,
            tonicMidi = 60,
            tonicName = "C",
            difficulty = ScaleDifficulty.INTERMEDIATE,
            direction = ScaleDirection.ASCENDING,
            midiNotes = notes,
            answerChoices = listOf("大调音阶", "自然小调", "和声小调", "五声大调"),
            correctAnswer = "五声大调"
        )

        val noteCount = 6
        val expected = ScaleTrainingAudioBuilder.LEAD_SILENCE_MS +
            noteCount * ScaleTrainingAudioBuilder.NOTE_DURATION_MS +
            (noteCount - 1) * ScaleTrainingAudioBuilder.NOTE_GAP_MS +
            ScaleTrainingAudioBuilder.TAIL_SILENCE_MS

        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `diatonic duration is longer than pentatonic`() {
        val diatonicNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR, 60, ScaleDirection.ASCENDING)
        val pentaNotes = ScaleTrainingEngine.buildScaleMidiNotes(ScaleType.MAJOR_PENTATONIC, 60, ScaleDirection.ASCENDING)

        val diatonicQ = ScaleQuestion(
            type = ScaleType.MAJOR, tonicMidi = 60, tonicName = "C",
            difficulty = ScaleDifficulty.BEGINNER, direction = ScaleDirection.ASCENDING,
            midiNotes = diatonicNotes, answerChoices = listOf("A", "B"), correctAnswer = "A"
        )
        val pentaQ = ScaleQuestion(
            type = ScaleType.MAJOR_PENTATONIC, tonicMidi = 60, tonicName = "C",
            difficulty = ScaleDifficulty.INTERMEDIATE, direction = ScaleDirection.ASCENDING,
            midiNotes = pentaNotes, answerChoices = listOf("A", "B"), correctAnswer = "A"
        )

        assertTrue(
            "Diatonic duration should be longer",
            builder.estimateDurationMs(diatonicQ) > builder.estimateDurationMs(pentaQ)
        )
    }

    @Test
    fun `duration scales with note count`() {
        for (type in ScaleType.ALL) {
            val notes = ScaleTrainingEngine.buildScaleMidiNotes(type, 60, ScaleDirection.ASCENDING)
            val q = ScaleQuestion(
                type = type, tonicMidi = 60, tonicName = "C",
                difficulty = ScaleDifficulty.ADVANCED, direction = ScaleDirection.ASCENDING,
                midiNotes = notes, answerChoices = ScaleType.ALL.map { it.displayName }, correctAnswer = type.displayName
            )
            val duration = builder.estimateDurationMs(q)
            assertTrue("${type.displayName} duration should scale with ${notes.size} notes", duration > 0)
        }
    }

    // ── 常量 ──────────────────────────────────────────────

    @Test
    fun `NOTE_DURATION_MS is reasonable`() {
        assertTrue(ScaleTrainingAudioBuilder.NOTE_DURATION_MS in 300L..1500L)
    }

    @Test
    fun `LEAD_SILENCE_MS and TAIL_SILENCE_MS are positive`() {
        assertTrue(ScaleTrainingAudioBuilder.LEAD_SILENCE_MS > 0)
        assertTrue(ScaleTrainingAudioBuilder.TAIL_SILENCE_MS > 0)
    }

    @Test
    fun `NOTE_GAP_MS is positive`() {
        assertTrue(ScaleTrainingAudioBuilder.NOTE_GAP_MS > 0)
    }

    @Test
    fun `DEFAULT_VELOCITY is in MIDI range`() {
        assertTrue(ScaleTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127)
    }
}
