package com.pianocompanion.seventhchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 七和弦品质听辨训练音频构建器单元测试。
 *
 * 验证渲染非空、不削波、不同和弦差异、时长估算、常量合理性。
 */
class SeventhChordTrainingAudioBuilderTest {

    private val builder = SeventhChordTrainingAudioBuilder()

    private fun createQuestion(quality: SeventhChordQuality, rootMidi: Int = 60): SeventhChordQuestion {
        val notes = SeventhChordTrainingEngine.buildSeventhChordMidiNotes(quality, rootMidi)
        return SeventhChordQuestion(
            quality = quality,
            rootMidi = rootMidi,
            rootName = "C",
            difficulty = SeventhChordDifficulty.ADVANCED,
            midiNotes = notes,
            answerChoices = listOf(quality.displayName),
            correctAnswer = quality.displayName
        )
    }

    // ── 渲染非空 ──────────────────────────────────────────────

    @Test
    fun `render produces non-empty audio`() {
        val q = createQuestion(SeventhChordQuality.MAJOR_7)
        val audio = builder.render(q)
        assertTrue("渲染结果应非空", audio.isNotEmpty())
    }

    @Test
    fun `render produces audio with reasonable length`() {
        val q = createQuestion(SeventhChordQuality.MAJOR_7)
        val audio = builder.render(q)
        // At least 1 second of audio (44100 samples)
        assertTrue("渲染结果应至少 1 秒长 (实际 ${audio.size} 样本)", audio.size >= 44100)
    }

    // ── 不削波 ────────────────────────────────────────────────

    @Test
    fun `render output is within minus 1 to 1`() {
        for (quality in SeventhChordQuality.ALL) {
            val q = createQuestion(quality)
            val audio = builder.render(q)
            for (sample in audio) {
                assertTrue(
                    "${quality.displayName}: 样本 $sample 应在 [-1, 1] 范围内",
                    sample in -1.0f..1.0f
                )
            }
        }
    }

    @Test
    fun `render has non-zero samples`() {
        val q = createQuestion(SeventhChordQuality.MAJOR_7)
        val audio = builder.render(q)
        val nonZeroCount = audio.count { it != 0.0f }
        assertTrue("应有非零样本 (实际 $nonZeroCount)", nonZeroCount > 1000)
    }

    // ── 不同和弦差异 ──────────────────────────────────────────

    @Test
    fun `different qualities produce different audio`() {
        val maj7 = builder.render(createQuestion(SeventhChordQuality.MAJOR_7))
        val dom7 = builder.render(createQuestion(SeventhChordQuality.DOMINANT_7))
        // Compare the middle portion (after lead silence)
        var different = false
        val start = 44100 / 10 // ~0.1s in
        val end = minOf(maj7.size, dom7.size)
        for (i in start until end) {
            if (kotlin.math.abs(maj7[i] - dom7[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("大七和属七的音频应有差异", different)
    }

    @Test
    fun `diminished sounds different from half diminished`() {
        val dim7 = builder.render(createQuestion(SeventhChordQuality.DIMINISHED_7))
        val halfDim = builder.render(createQuestion(SeventhChordQuality.HALF_DIMINISHED_7))
        var different = false
        val start = 44100 / 10
        val end = minOf(dim7.size, halfDim.size)
        for (i in start until end) {
            if (kotlin.math.abs(dim7[i] - halfDim[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("减七和半减七的音频应有差异", different)
    }

    // ── estimateDurationMs ────────────────────────────────────

    @Test
    fun `estimateDurationMs equals lead plus chord plus tail`() {
        val q = createQuestion(SeventhChordQuality.MAJOR_7)
        val expected = SeventhChordTrainingAudioBuilder.LEAD_SILENCE_MS +
            SeventhChordTrainingAudioBuilder.CHORD_DURATION_MS +
            SeventhChordTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs is positive`() {
        val q = createQuestion(SeventhChordQuality.MAJOR_7)
        assertTrue(builder.estimateDurationMs(q) > 0)
    }

    @Test
    fun `estimateDurationMs is consistent across qualities`() {
        val maj7 = builder.estimateDurationMs(createQuestion(SeventhChordQuality.MAJOR_7))
        val dim7 = builder.estimateDurationMs(createQuestion(SeventhChordQuality.DIMINISHED_7))
        assertEquals(maj7, dim7)
    }

    // ── 常量合理性 ────────────────────────────────────────────

    @Test
    fun `chord duration is reasonable`() {
        assertTrue(
            "和弦时长应在 1-5 秒之间",
            SeventhChordTrainingAudioBuilder.CHORD_DURATION_MS in 1000..5000
        )
    }

    @Test
    fun `lead silence is reasonable`() {
        assertTrue(
            "前导静音应在 0-1 秒之间",
            SeventhChordTrainingAudioBuilder.LEAD_SILENCE_MS in 0..1000
        )
    }

    @Test
    fun `tail silence is reasonable`() {
        assertTrue(
            "尾部静音应在 0-2 秒之间",
            SeventhChordTrainingAudioBuilder.TAIL_SILENCE_MS in 0..2000
        )
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, SeventhChordTrainingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `default velocity is reasonable`() {
        assertTrue(
            "默认力度应在 1-127 之间",
            SeventhChordTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127
        )
    }

    @Test
    fun `softclip K is positive`() {
        assertTrue(
            "软限幅拐点应为正数",
            SeventhChordTrainingAudioBuilder.SOFTCLIP_K > 0f
        )
    }

    // ── renderChord 直接调用 ──────────────────────────────────

    @Test
    fun `renderChord with empty list returns empty`() {
        val audio = builder.renderChord(emptyList())
        assertEquals(0, audio.size)
    }

    @Test
    fun `renderChord produces audio with 4 notes`() {
        val notes = listOf(60, 64, 67, 71) // Cmaj7
        val audio = builder.renderChord(notes)
        assertTrue("4音和弦渲染应非空", audio.isNotEmpty())
    }

    @Test
    fun `renderChord all samples in range`() {
        val notes = listOf(48, 51, 54, 57) // Cdim7
        val audio = builder.renderChord(notes)
        for (sample in audio) {
            assertTrue("样本应在 [-1, 1] 范围内", sample in -1.0f..1.0f)
        }
    }
}
