package com.pianocompanion.cadencetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终止式听辨训练音频构建器单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 验证渲染长度、采样率、不削波、和弦进行差异、estimateDurationMs 公式正确性。
 */
class CadenceTrainingAudioBuilderTest {

    private val builder = CadenceTrainingAudioBuilder()

    private fun makeQuestion(
        type: CadenceType = CadenceType.PERFECT_AUTHENTIC,
        tonicMidi: Int = 48
    ): CadenceQuestion {
        val progression = type.progression.map { fn -> fn.buildMidiNotes(tonicMidi) }
        return CadenceQuestion(
            type = type,
            tonicMidi = tonicMidi,
            tonicName = "C",
            difficulty = CadenceDifficulty.ADVANCED,
            chordProgression = progression,
            answerChoices = CadenceType.ALL.map { it.displayName },
            correctAnswer = type.displayName
        )
    }

    // ── 基本渲染 ──────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = makeQuestion()
        val buffer = builder.render(q)
        assertTrue("渲染缓冲区应非空", buffer.isNotEmpty())
    }

    @Test
    fun `renderProgression empty returns empty buffer`() {
        val buffer = builder.renderProgression(emptyList())
        assertTrue("空进行应返回空缓冲区", buffer.isEmpty())
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, CadenceTrainingAudioBuilder.SAMPLE_RATE)
    }

    // ── 不削波 ────────────────────────────────────────────

    @Test
    fun `render does not clip - all samples within minus 1 to 1`() {
        val q = makeQuestion()
        val buffer = builder.render(q)
        buffer.forEachIndexed { idx, sample ->
            assertTrue(
                "样本 $idx = $sample 超出 [-1, 1]",
                sample in -1.0f..1.0f
            )
        }
    }

    @Test
    fun `render all cadence types do not clip`() {
        CadenceType.ALL.forEach { type ->
            val q = makeQuestion(type)
            val buffer = builder.render(q)
            assertTrue(buffer.isNotEmpty())
            assertTrue(buffer.all { it in -1.0f..1.0f })
        }
    }

    @Test
    fun `render extreme tonic does not clip`() {
        // 极低主音，和弦可能跨越很大范围
        val q = makeQuestion(CadenceType.PERFECT_AUTHENTIC, tonicMidi = 21)
        val buffer = builder.render(q)
        assertTrue(buffer.all { it in -1.0f..1.0f })
    }

    // ── 差异性 ────────────────────────────────────────────

    @Test
    fun `different cadence types produce different audio`() {
        val pacBuffer = builder.render(makeQuestion(CadenceType.PERFECT_AUTHENTIC))
        val plagalBuffer = builder.render(makeQuestion(CadenceType.PLAGAL))
        // PAC=V→I, PC=IV→I, MIDI 音符不同，音频应不同
        assertNotEquals(pacBuffer.toList(), plagalBuffer.toList())
    }

    @Test
    fun `different tonic produces different audio`() {
        val a = builder.render(makeQuestion(CadenceType.PERFECT_AUTHENTIC, tonicMidi = 48))
        val b = builder.render(makeQuestion(CadenceType.PERFECT_AUTHENTIC, tonicMidi = 50))
        assertNotEquals(a.toList(), b.toList())
    }

    // ── 长度 ──────────────────────────────────────────────

    @Test
    fun `two-chord progression is longer than single chord`() {
        val twoChord = builder.renderProgression(
            listOf(ChordFunction.I.buildMidiNotes(48), ChordFunction.V.buildMidiNotes(48))
        )
        val oneChord = builder.renderProgression(
            listOf(ChordFunction.I.buildMidiNotes(48))
        )
        assertTrue(
            "双和弦进行 ($${twoChord.size}) 应长于单和弦 (${oneChord.size})",
            twoChord.size > oneChord.size
        )
    }

    @Test
    fun `render all difficulties produces non-empty audio`() {
        CadenceDifficulty.ALL.forEach { difficulty ->
            val q = CadenceTrainingEngine.withSeed(7L).generate(difficulty)
            val buffer = builder.render(q)
            assertTrue("难度 ${difficulty.name} 渲染应非空", buffer.isNotEmpty())
        }
    }

    // ── estimateDurationMs 公式正确性 ────────────────────

    @Test
    fun `estimateDurationMs matches formula for two-chord cadence`() {
        val q = makeQuestion()
        val expected = CadenceTrainingAudioBuilder.LEAD_SILENCE_MS +
            2 * CadenceTrainingAudioBuilder.CHORD_DURATION_MS +
            (2 - 1) * CadenceTrainingAudioBuilder.CHORD_GAP_MS +
            CadenceTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs scales with chord count`() {
        val twoChord = CadenceQuestion(
            type = CadenceType.PERFECT_AUTHENTIC,
            tonicMidi = 48,
            tonicName = "C",
            difficulty = CadenceDifficulty.ADVANCED,
            chordProgression = listOf(
                ChordFunction.V.buildMidiNotes(48),
                ChordFunction.I.buildMidiNotes(48)
            ),
            answerChoices = CadenceType.ALL.map { it.displayName },
            correctAnswer = CadenceType.PERFECT_AUTHENTIC.displayName
        )
        val threeChord = twoChord.copy(
            chordProgression = listOf(
                ChordFunction.I.buildMidiNotes(48),
                ChordFunction.V.buildMidiNotes(48),
                ChordFunction.I.buildMidiNotes(48)
            )
        )
        // 3 和弦比 2 和弦多一个 CHORD_DURATION_MS + 一个 CHORD_GAP_MS
        val diff = builder.estimateDurationMs(threeChord) - builder.estimateDurationMs(twoChord)
        assertEquals(
            CadenceTrainingAudioBuilder.CHORD_DURATION_MS + CadenceTrainingAudioBuilder.CHORD_GAP_MS,
            diff
        )
    }

    @Test
    fun `estimateDurationMs positive for all cadence types`() {
        CadenceType.ALL.forEach { type ->
            val q = makeQuestion(type)
            assertTrue(builder.estimateDurationMs(q) > 0)
        }
    }

    // ── 常量合理性 ────────────────────────────────────────

    @Test
    fun `chord duration is reasonable for listening`() {
        assertTrue(CadenceTrainingAudioBuilder.CHORD_DURATION_MS >= 800)
    }

    @Test
    fun `soft clip constant is positive`() {
        assertTrue(CadenceTrainingAudioBuilder.SOFTCLIP_K > 0)
    }
}
