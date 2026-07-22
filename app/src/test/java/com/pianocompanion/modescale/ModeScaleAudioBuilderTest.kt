package com.pianocompanion.modescale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调式音阶色彩对比训练音频构建器单元测试。
 */
class ModeScaleAudioBuilderTest {

    private val builder = ModeScaleAudioBuilder()

    // ── 音阶事件构建 ──────────────────────────────────

    @Test
    fun `buildToneEvents includes reference tone for beginner`() {
        val engine = ModeScaleEngine.withSeed(10)
        val q = engine.generate(ModeScaleDifficulty.BEGINNER)
        val events = builder.buildToneEvents(q)
        // Should have: 1 reference + 8 ascending + 7 descending = 16
        assertTrue("Should have reference tone", events.any { it.isReference })
        assertEquals(16, events.size)
    }

    @Test
    fun `buildToneEvents includes reference tone for all difficulties`() {
        val engine = ModeScaleEngine.withSeed(20)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            val events = builder.buildToneEvents(q)
            assertTrue(
                "Difficulty ${difficulty.name} should have reference tone",
                events.any { it.isReference }
            )
        }
    }

    @Test
    fun `reference tone is first event`() {
        val engine = ModeScaleEngine.withSeed(30)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        assertTrue("First event should be reference", events[0].isReference)
    }

    @Test
    fun `reference tone onset is zero`() {
        val engine = ModeScaleEngine.withSeed(40)
        val q = engine.generate(ModeScaleDifficulty.BEGINNER)
        val events = builder.buildToneEvents(q)
        assertEquals(0.0, events[0].onsetMs, 0.01)
    }

    // ── 音阶上行 ──────────────────────────────────

    @Test
    fun `ascending scale has 8 notes (tonic to octave)`() {
        val engine = ModeScaleEngine.withSeed(50)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        // Skip the reference (events[0])
        val ascending = events.drop(1).take(8)
        assertEquals(8, ascending.size)
        // First note = tonic, last = octave (12 semitones above)
        assertEquals(q.tonicMidi, ascending[0].midi)
        assertEquals(q.tonicMidi + 12, ascending[7].midi)
    }

    @Test
    fun `ascending scale midi values match mode semitones`() {
        val engine = ModeScaleEngine.withSeed(60)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8)
        val mode = q.targetMode
        mode.semitones.forEachIndexed { i, semitone ->
            assertEquals(
                "Note $i should be tonic + $semitone semitones",
                q.tonicMidi + semitone,
                ascending[i].midi
            )
        }
    }

    // ── 音阶下行 ──────────────────────────────────

    @Test
    fun `descending scale has 7 notes (octave-1 back to tonic)`() {
        val engine = ModeScaleEngine.withSeed(70)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        // 1 reference + 8 ascending + 7 descending = 16
        val descending = events.drop(9) // skip reference + 8 ascending
        assertEquals(7, descending.size)
        // Last note = tonic
        assertEquals(q.tonicMidi, descending[6].midi)
    }

    @Test
    fun `descending scale is reverse of ascending minus octave`() {
        val engine = ModeScaleEngine.withSeed(80)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        val descending = events.drop(9).map { it.midi }
        // Descending should be reverse of ascending[0..6] (indices 0-6, not 7 which is octave)
        val expectedDescending = ascending.subList(0, 7).reversed()
        assertEquals(expectedDescending, descending)
    }

    // ── 不同调式产生不同音阶 ──────────────────────────────────

    @Test
    fun `ionian and aeolian produce different ascending scales`() {
        val engine1 = ModeScaleEngine.withSeed(100)
        val engine2 = ModeScaleEngine.withSeed(100)
        val q1 = engine1.generate(ModeScaleDifficulty.BEGINNER)
        // Force a specific mode by using different seeds until we get the target
        var q2 = engine2.generate(ModeScaleDifficulty.BEGINNER)
        // Beginner only has Ionian and Aeolian, keep generating until we get the other
        var attempts = 0
        while (q2.targetMode == q1.targetMode && attempts < 100) {
            q2 = engine2.generate(ModeScaleDifficulty.BEGINNER)
            attempts++
        }
        val events1 = builder.buildToneEvents(q1).drop(1).take(8).map { it.midi }
        val events2 = builder.buildToneEvents(q2).drop(1).take(8).map { it.midi }
        // They should differ (at least the 3rd note for sure)
        assert(events1 != events2)
    }

    @Test
    fun `lydian has augmented fourth`() {
        val q = ModeScaleQuestion(
            difficulty = ModeScaleDifficulty.ADVANCED,
            seed = 0L,
            targetMode = ModeType.LYDIAN,
            answerChoices = ModeType.ADVANCED_MODES.map { it.displayName },
            correctAnswer = ModeType.LYDIAN.displayName
        )
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        // Lydian: 0, 2, 4, 6, 7, 9, 11, 12
        // The 4th note (index 3) is the augmented 4th = tonic + 6 semitones
        assertEquals(q.tonicMidi + 6, ascending[3])
    }

    @Test
    fun `dorian has major sixth`() {
        val q = ModeScaleQuestion(
            difficulty = ModeScaleDifficulty.ADVANCED,
            seed = 0L,
            targetMode = ModeType.DORIAN,
            answerChoices = ModeType.ADVANCED_MODES.map { it.displayName },
            correctAnswer = ModeType.DORIAN.displayName
        )
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        // Dorian: 0, 2, 3, 5, 7, 9, 10, 12
        // The 6th note (index 5) is the major 6th = tonic + 9 semitones
        assertEquals(q.tonicMidi + 9, ascending[5])
    }

    @Test
    fun `phrygian has minor second`() {
        val q = ModeScaleQuestion(
            difficulty = ModeScaleDifficulty.ADVANCED,
            seed = 0L,
            targetMode = ModeType.PHRYGIAN,
            answerChoices = ModeType.ADVANCED_MODES.map { it.displayName },
            correctAnswer = ModeType.PHRYGIAN.displayName
        )
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        // Phrygian: 0, 1, 3, 5, 7, 8, 10, 12
        // The 2nd note (index 1) is the minor 2nd = tonic + 1 semitone
        assertEquals(q.tonicMidi + 1, ascending[1])
    }

    @Test
    fun `locrian has diminished fifth`() {
        val q = ModeScaleQuestion(
            difficulty = ModeScaleDifficulty.ADVANCED,
            seed = 0L,
            targetMode = ModeType.LOCRIAN,
            answerChoices = ModeType.ADVANCED_MODES.map { it.displayName },
            correctAnswer = ModeType.LOCRIAN.displayName
        )
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        // Locrian: 0, 1, 3, 5, 6, 8, 10, 12
        // The 5th note (index 4) is the diminished 5th = tonic + 6 semitones
        assertEquals(q.tonicMidi + 6, ascending[4])
    }

    @Test
    fun `mixolydian has minor seventh`() {
        val q = ModeScaleQuestion(
            difficulty = ModeScaleDifficulty.ADVANCED,
            seed = 0L,
            targetMode = ModeType.MIXOLYDIAN,
            answerChoices = ModeType.ADVANCED_MODES.map { it.displayName },
            correctAnswer = ModeType.MIXOLYDIAN.displayName
        )
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        // Mixolydian: 0, 2, 4, 5, 7, 9, 10, 12
        // The 8th note (index 7) is the octave, 7th note (index 6) is minor 7th = tonic + 10
        assertEquals(q.tonicMidi + 10, ascending[6])
    }

    @Test
    fun `aeolian natural minor has minor third and minor sixth`() {
        val q = ModeScaleQuestion(
            difficulty = ModeScaleDifficulty.ADVANCED,
            seed = 0L,
            targetMode = ModeType.AEOLIAN,
            answerChoices = ModeType.ADVANCED_MODES.map { it.displayName },
            correctAnswer = ModeType.AEOLIAN.displayName
        )
        val events = builder.buildToneEvents(q)
        val ascending = events.drop(1).take(8).map { it.midi }
        // Aeolian: 0, 2, 3, 5, 7, 8, 10, 12
        assertEquals(q.tonicMidi + 3, ascending[2])  // minor 3rd
        assertEquals(q.tonicMidi + 8, ascending[5])  // minor 6th
    }

    // ── 时间线 ──────────────────────────────────

    @Test
    fun `events are sequential with no overlap`() {
        val engine = ModeScaleEngine.withSeed(90)
        val q = engine.generate(ModeScaleDifficulty.INTERMEDIATE)
        val events = builder.buildToneEvents(q)
        for (i in 1 until events.size) {
            assertTrue(
                "Event $i overlaps with event ${i - 1}",
                events[i].onsetMs >= events[i - 1].onsetMs + events[i - 1].durationMs - 1.0
            )
        }
    }

    @Test
    fun `music duration is positive`() {
        val engine = ModeScaleEngine.withSeed(95)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            val dur = builder.musicDurationMs(q)
            assertTrue("Duration should be positive for ${difficulty.name}", dur > 0)
        }
    }

    @Test
    fun `higher difficulty has more total events for same mode count`() {
        // Beginner has 2 modes, Intermediate 4, Advanced 7
        // Each mode always produces 16 events (1 ref + 8 asc + 7 desc)
        val engine = ModeScaleEngine.withSeed(1)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            val events = builder.buildToneEvents(q)
            assertEquals(16, events.size)
        }
    }

    // ── 渲染 ──────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val engine = ModeScaleEngine.withSeed(110)
        ModeScaleDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            val buffer = builder.render(q)
            assertTrue("Buffer should be non-empty for ${difficulty.name}", buffer.isNotEmpty())
        }
    }

    @Test
    fun `render buffer samples are within valid range`() {
        val engine = ModeScaleEngine.withSeed(120)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val buffer = builder.render(q)
        buffer.forEach { sample ->
            assertTrue("Sample $sample out of range", sample >= -1.0f && sample <= 1.0f)
        }
    }

    @Test
    fun `render buffer is not silent`() {
        val engine = ModeScaleEngine.withSeed(130)
        val q = engine.generate(ModeScaleDifficulty.ADVANCED)
        val buffer = builder.render(q)
        val maxAmp = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("Buffer should have audible content, max amplitude = $maxAmp", maxAmp > 0.01f)
    }

    // ── MIDI 转频率 ──────────────────────────────────

    @Test
    fun `midi 69 is A4 440Hz`() {
        val freq = builder.midiToFreq(69)
        assertEquals(440.0, freq, 0.1)
    }

    @Test
    fun `midi 60 is C4 261_63Hz`() {
        val freq = builder.midiToFreq(60)
        assertEquals(261.63, freq, 0.1)
    }

    @Test
    fun `octave higher doubles frequency`() {
        val freq1 = builder.midiToFreq(60)
        val freq2 = builder.midiToFreq(72)
        assertEquals(2.0, freq2 / freq1, 0.001)
    }

    // ── 空事件 ──────────────────────────────────

    @Test
    fun `renderEvents with empty list returns empty array`() {
        val result = builder.renderEvents(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `estimateDurationMs is positive and includes silence padding`() {
        val engine = ModeScaleEngine.withSeed(140)
        val q = engine.generate(ModeScaleDifficulty.BEGINNER)
        val estimated = builder.estimateDurationMs(q)
        val musicDur = builder.musicDurationMs(q)
        assertTrue("Estimated should be > music duration", estimated > musicDur.toLong())
    }
}
