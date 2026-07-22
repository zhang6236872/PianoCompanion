package com.pianocompanion.chordinversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练音频构建器单元测试。
 *
 * 重点验证：
 * - MIDI 音符转位计算的正确性（原位/第一/第二/第三转位）
 * - 低音音符为最低音
 * - 频率转换正确（A4=440Hz, MIDI 69）
 * - 所有音符同时起音（柱式和弦）
 * - 渲染输出非空且在 [-1, 1] 值域内
 */
class ChordInversionAudioBuilderTest {

    private val builder = ChordInversionAudioBuilder()

    // ── MIDI 音符转位计算 ──────────────────────────────────

    @Test
    fun `root position major triad has root in bass`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, rootMidi = 60)
        // C major root position: C4, E4, G4
        assertEquals(listOf(60, 64, 67), q.midiNotes)
        assertEquals(60, q.bassMidi) // C4 is bass
    }

    @Test
    fun `first inversion major triad has third in bass`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.FIRST_INVERSION, rootMidi = 60)
        // C major first inversion: E4, G4, C5
        assertEquals(listOf(64, 67, 72), q.midiNotes)
        assertEquals(64, q.bassMidi) // E4 is bass
    }

    @Test
    fun `second inversion major triad has fifth in bass`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.SECOND_INVERSION, rootMidi = 60)
        // C major second inversion: G4, C5, E5
        assertEquals(listOf(67, 72, 76), q.midiNotes)
        assertEquals(67, q.bassMidi) // G4 is bass
    }

    @Test
    fun `root position minor triad`() {
        val q = createQuestion(ChordType.MINOR_TRIAD, ChordInversion.ROOT_POSITION, rootMidi = 57)
        // A minor root position: A3, C4, E4
        assertEquals(listOf(57, 60, 64), q.midiNotes)
    }

    @Test
    fun `first inversion minor triad`() {
        val q = createQuestion(ChordType.MINOR_TRIAD, ChordInversion.FIRST_INVERSION, rootMidi = 57)
        // A minor first inversion: C4, E4, A4
        assertEquals(listOf(60, 64, 69), q.midiNotes)
        assertEquals(60, q.bassMidi) // C4 is bass
    }

    @Test
    fun `root position diminished triad`() {
        val q = createQuestion(ChordType.DIMINISHED_TRIAD, ChordInversion.ROOT_POSITION, rootMidi = 60)
        // C diminished root position: C4, Eb4, Gb4
        assertEquals(listOf(60, 63, 66), q.midiNotes)
    }

    @Test
    fun `root position augmented triad`() {
        val q = createQuestion(ChordType.AUGMENTED_TRIAD, ChordInversion.ROOT_POSITION, rootMidi = 60)
        // C augmented root position: C4, E4, G#4
        assertEquals(listOf(60, 64, 68), q.midiNotes)
    }

    // ── 七和弦转位 ──────────────────────────────────

    @Test
    fun `root position dominant seventh`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.ROOT_POSITION, rootMidi = 60)
        // C7 root position: C4, E4, G4, Bb4
        assertEquals(listOf(60, 64, 67, 70), q.midiNotes)
        assertEquals(60, q.bassMidi)
    }

    @Test
    fun `first inversion dominant seventh`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.FIRST_INVERSION, rootMidi = 60)
        // C7 first inversion: E4, G4, Bb4, C5
        assertEquals(listOf(64, 67, 70, 72), q.midiNotes)
        assertEquals(64, q.bassMidi)
    }

    @Test
    fun `second inversion dominant seventh`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.SECOND_INVERSION, rootMidi = 60)
        // C7 second inversion: G4, Bb4, C5, E5
        assertEquals(listOf(67, 70, 72, 76), q.midiNotes)
        assertEquals(67, q.bassMidi)
    }

    @Test
    fun `third inversion dominant seventh`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.THIRD_INVERSION, rootMidi = 60)
        // C7 third inversion: Bb4, C5, E5, G5
        assertEquals(listOf(70, 72, 76, 79), q.midiNotes)
        assertEquals(70, q.bassMidi)
    }

    @Test
    fun `root position major seventh`() {
        val q = createQuestion(ChordType.MAJOR_SEVENTH, ChordInversion.ROOT_POSITION, rootMidi = 60)
        // Cmaj7 root position: C4, E4, G4, B4
        assertEquals(listOf(60, 64, 67, 71), q.midiNotes)
    }

    @Test
    fun `third inversion major seventh`() {
        val q = createQuestion(ChordType.MAJOR_SEVENTH, ChordInversion.THIRD_INVERSION, rootMidi = 60)
        // Cmaj7 third inversion: B4, C5, E5, G5
        assertEquals(listOf(71, 72, 76, 79), q.midiNotes)
        assertEquals(71, q.bassMidi)
    }

    // ── 低音始终最低 ──────────────────────────────────

    @Test
    fun `bass note is always the lowest in all inversions`() {
        ChordType.entries.forEach { chordType ->
            val maxInv = chordType.maxInversionOrder
            for (invOrder in 0..maxInv) {
                val inv = ChordInversion.forOrder(invOrder)
                val q = createQuestion(chordType, inv, rootMidi = 60)
                val notes = q.midiNotes
                val bass = q.bassMidi
                assertEquals("Bass should equal min of notes for $chordType $inv", notes.minOrNull(), bass)
            }
        }
    }

    @Test
    fun `bass member name matches inversion`() {
        val q1 = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, 60)
        assertEquals("根音", q1.bassMemberName)
        val q2 = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.FIRST_INVERSION, 60)
        assertEquals("三音", q2.bassMemberName)
        val q3 = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.SECOND_INVERSION, 60)
        assertEquals("五音", q3.bassMemberName)
        val q4 = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.THIRD_INVERSION, 60)
        assertEquals("七音", q4.bassMemberName)
    }

    // ── 音符数量 ──────────────────────────────────

    @Test
    fun `triad has 3 notes in all inversions`() {
        listOf(ChordType.MAJOR_TRIAD, ChordType.MINOR_TRIAD, ChordType.DIMINISHED_TRIAD, ChordType.AUGMENTED_TRIAD)
            .forEach { chordType ->
                ChordInversion.TRIAD_INVERSIONS.forEach { inv ->
                    val q = createQuestion(chordType, inv, 60)
                    assertEquals(3, q.midiNotes.size)
                }
            }
    }

    @Test
    fun `seventh chord has 4 notes in all inversions`() {
        listOf(ChordType.DOMINANT_SEVENTH, ChordType.MAJOR_SEVENTH).forEach { chordType ->
            ChordInversion.SEVENTH_INVERSIONS.forEach { inv ->
                val q = createQuestion(chordType, inv, 60)
                assertEquals(4, q.midiNotes.size)
            }
        }
    }

    // ── 频率转换 ──────────────────────────────────

    @Test
    fun `midi 69 equals A4 440 Hz`() {
        assertEquals(440.0, ChordInversionAudioBuilder.midiToFrequency(69), 0.01)
    }

    @Test
    fun `midi 60 equals C4 approximately 261_63 Hz`() {
        assertEquals(261.63, ChordInversionAudioBuilder.midiToFrequency(60), 0.1)
    }

    @Test
    fun `octave doubles frequency`() {
        val f1 = ChordInversionAudioBuilder.midiToFrequency(60) // C4
        val f2 = ChordInversionAudioBuilder.midiToFrequency(72) // C5
        assertEquals(2.0, f2 / f1, 0.001)
    }

    // ── 事件构建 ──────────────────────────────────

    @Test
    fun `all notes have same onset for block chord`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.FIRST_INVERSION, 60)
        val events = builder.buildChordNoteEvents(q)
        assertEquals(3, events.size)
        events.forEach { event ->
            assertEquals(0.0, event.onsetMs, 0.001)
        }
    }

    @Test
    fun `bass note has higher amplitude`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.ROOT_POSITION, 60)
        val events = builder.buildChordNoteEvents(q)
        val bassEvent = events.first { it.isBass }
        val nonBassEvents = events.filter { !it.isBass }
        assertTrue("Bass amplitude should be higher", bassEvent.amplitude > nonBassEvents.first().amplitude)
    }

    @Test
    fun `event member names match inversion`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.FIRST_INVERSION, 60)
        val events = builder.buildChordNoteEvents(q)
        // First inversion: bass = 三音
        val bassEvent = events.first { it.isBass }
        assertEquals("三音", bassEvent.memberName)
    }

    @Test
    fun `event midi values match question notes`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.SECOND_INVERSION, 57)
        val events = builder.buildChordNoteEvents(q)
        val eventMidiSet = events.map { it.midi }.sorted()
        val questionMidiSet = q.midiNotes.sorted()
        assertEquals(questionMidiSet, eventMidiSet)
    }

    @Test
    fun `event frequencies match midi`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, 60)
        val events = builder.buildChordNoteEvents(q)
        events.forEach { event ->
            val expectedFreq = ChordInversionAudioBuilder.midiToFrequency(event.midi)
            assertEquals(expectedFreq, event.frequencyHz, 0.01)
        }
    }

    // ── 渲染 ──────────────────────────────────

    @Test
    fun `render produces non-empty output`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, 60)
        val audio = builder.render(q)
        assertTrue("Audio should be non-empty", audio.isNotEmpty())
    }

    @Test
    fun `render output is within valid range`() {
        val q = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.THIRD_INVERSION, 57)
        val audio = builder.render(q)
        audio.forEach { sample ->
            assertTrue("Sample $sample out of range", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `render is deterministic for same question`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.SECOND_INVERSION, 60)
        val audio1 = builder.render(q)
        val audio2 = builder.render(q)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `render all difficulties without error`() {
        ChordInversionDifficulty.ALL.forEach { difficulty ->
            val engine = ChordInversionEngine.withSeed(42)
            repeat(5) {
                val q = engine.generate(difficulty)
                val audio = builder.render(q)
                assertTrue("Audio for $difficulty should be non-empty", audio.isNotEmpty())
            }
        }
    }

    @Test
    fun `render all chord types and inversions without error`() {
        ChordType.entries.forEach { chordType ->
            val maxInv = chordType.maxInversionOrder
            for (invOrder in 0..maxInv) {
                val inv = ChordInversion.forOrder(invOrder)
                val q = createQuestion(chordType, inv, 60)
                val audio = builder.render(q)
                assertTrue("Audio for $chordType $inv should be non-empty", audio.isNotEmpty())
            }
        }
    }

    @Test
    fun `noteCount matches chord member count`() {
        val triadQ = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, 60)
        assertEquals(3, builder.noteCount(triadQ))
        val seventhQ = createQuestion(ChordType.DOMINANT_SEVENTH, ChordInversion.THIRD_INVERSION, 60)
        assertEquals(4, builder.noteCount(seventhQ))
    }

    @Test
    fun `estimateDurationMs includes lead and tail silence`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, 60)
        val duration = builder.estimateDurationMs(q)
        val expected = (ChordInversionAudioBuilder.LEAD_SILENCE_MS +
            q.chordDurationMs +
            ChordInversionAudioBuilder.TAIL_SILENCE_MS).toLong()
        assertEquals(expected, duration)
    }

    @Test
    fun `custom sample rate produces different length output`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.ROOT_POSITION, 60)
        val builder44k = ChordInversionAudioBuilder(44100)
        val builder22k = ChordInversionAudioBuilder(22050)
        val audio44k = builder44k.render(q)
        val audio22k = builder22k.render(q)
        assertTrue("44.1kHz should produce longer output", audio44k.size > audio22k.size)
    }

    @Test
    fun `rendered audio has non-zero content`() {
        val q = createQuestion(ChordType.MAJOR_TRIAD, ChordInversion.FIRST_INVERSION, 60)
        val audio = builder.render(q)
        val nonZero = audio.count { it != 0.0f }
        assertTrue("Should have significant non-zero content", nonZero > audio.size / 2)
    }

    // ── 辅助 ──────────────────────────────────

    /**
     * 直接构建指定和弦+转位+根音的题目（绕过引擎随机性）。
     */
    private fun createQuestion(
        chordType: ChordType,
        inversion: ChordInversion,
        rootMidi: Int
    ): ChordInversionQuestion {
        // 选择一个包含该和弦类型的难度
        val difficulty = when {
            chordType in ChordInversionDifficulty.ADVANCED.chords &&
                inversion in ChordInversionDifficulty.ADVANCED.inversionOptions &&
                rootMidi in ChordInversionDifficulty.ADVANCED.rootMidiMin..ChordInversionDifficulty.ADVANCED.rootMidiMax
            -> ChordInversionDifficulty.ADVANCED
            chordType in ChordInversionDifficulty.INTERMEDIATE.chords &&
                inversion in ChordInversionDifficulty.INTERMEDIATE.inversionOptions &&
                rootMidi in ChordInversionDifficulty.INTERMEDIATE.rootMidiMin..ChordInversionDifficulty.INTERMEDIATE.rootMidiMax
            -> ChordInversionDifficulty.INTERMEDIATE
            else -> ChordInversionDifficulty.BEGINNER
        }
        // 使用高级难度的完整选项集构建选项
        val inversionsForChoices = difficulty.inversionOptions
        val choices = inversionsForChoices.map { it.choiceLabel }.shuffled(kotlin.random.Random(42))
        return ChordInversionQuestion(
            difficulty = difficulty,
            seed = 12345L,
            rootMidi = rootMidi,
            chordType = chordType,
            targetInversion = inversion,
            answerChoices = choices.take(difficulty.choiceCount).let { all ->
                if (inversion.choiceLabel in all) all
                else (listOf(inversion.choiceLabel) + all.filter { it != inversion.choiceLabel })
                    .take(difficulty.choiceCount)
            },
            correctAnswer = inversion.choiceLabel
        )
    }
}
