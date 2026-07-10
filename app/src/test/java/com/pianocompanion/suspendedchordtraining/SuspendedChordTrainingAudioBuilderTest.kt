package com.pianocompanion.suspendedchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挂留和弦品质听辨训练音频构建器单元测试。
 *
 * 验证渲染非空、不削波、不同和弦差异、时长估算、常量合理性。
 */
class SuspendedChordTrainingAudioBuilderTest {

    private val builder = SuspendedChordTrainingAudioBuilder()

    private fun createQuestion(quality: SuspendedChordQuality, rootMidi: Int = 60): SuspendedChordQuestion {
        val notes = SuspendedChordTrainingEngine.buildSuspendedChordMidiNotes(quality, rootMidi)
        return SuspendedChordQuestion(
            quality = quality,
            rootMidi = rootMidi,
            rootName = "C",
            difficulty = SuspendedChordDifficulty.ADVANCED,
            midiNotes = notes,
            answerChoices = listOf(quality.displayName),
            correctAnswer = quality.displayName
        )
    }

    // ── 渲染非空 ──────────────────────────────────────────────

    @Test
    fun `render produces non-empty audio`() {
        val q = createQuestion(SuspendedChordQuality.MAJOR_TRIAD)
        val audio = builder.render(q)
        assertTrue("渲染结果应非空", audio.isNotEmpty())
    }

    @Test
    fun `render produces audio with reasonable length`() {
        val q = createQuestion(SuspendedChordQuality.MAJOR_TRIAD)
        val audio = builder.render(q)
        // At least 1 second of audio (44100 samples)
        assertTrue("渲染结果应至少 1 秒长 (实际 ${audio.size} 样本)", audio.size >= 44100)
    }

    // ── 不削波 ────────────────────────────────────────────────

    @Test
    fun `render output is within minus 1 to 1`() {
        for (quality in SuspendedChordQuality.ALL) {
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
        val q = createQuestion(SuspendedChordQuality.MAJOR_TRIAD)
        val audio = builder.render(q)
        val nonZeroCount = audio.count { it != 0.0f }
        assertTrue("应有非零样本 (实际 $nonZeroCount)", nonZeroCount > 1000)
    }

    // ── 不同和弦差异 ──────────────────────────────────────────

    @Test
    fun `different qualities produce different audio`() {
        val major = builder.render(createQuestion(SuspendedChordQuality.MAJOR_TRIAD))
        val sus2 = builder.render(createQuestion(SuspendedChordQuality.SUS2))
        var different = false
        val start = 44100 / 10
        val end = minOf(major.size, sus2.size)
        for (i in start until end) {
            if (kotlin.math.abs(major[i] - sus2[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("大三和挂二的音频应有差异", different)
    }

    @Test
    fun `sus2 sounds different from sus4`() {
        val sus2 = builder.render(createQuestion(SuspendedChordQuality.SUS2))
        val sus4 = builder.render(createQuestion(SuspendedChordQuality.SUS4))
        var different = false
        val start = 44100 / 10
        val end = minOf(sus2.size, sus4.size)
        for (i in start until end) {
            if (kotlin.math.abs(sus2[i] - sus4[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("挂二和挂四的音频应有差异", different)
    }

    @Test
    fun `major sounds different from minor`() {
        val major = builder.render(createQuestion(SuspendedChordQuality.MAJOR_TRIAD))
        val minor = builder.render(createQuestion(SuspendedChordQuality.MINOR_TRIAD))
        var different = false
        val start = 44100 / 10
        val end = minOf(major.size, minor.size)
        for (i in start until end) {
            if (kotlin.math.abs(major[i] - minor[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("大三和小三的音频应有差异", different)
    }

    @Test
    fun `sus2sus4 sounds different from sus4`() {
        val sus2sus4 = builder.render(createQuestion(SuspendedChordQuality.SUS2_SUS4))
        val sus4 = builder.render(createQuestion(SuspendedChordQuality.SUS4))
        var different = false
        val start = 44100 / 10
        val end = minOf(sus2sus4.size, sus4.size)
        for (i in start until end) {
            if (kotlin.math.abs(sus2sus4[i] - sus4[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("双挂和挂四的音频应有差异", different)
    }

    // ── estimateDurationMs ────────────────────────────────────

    @Test
    fun `estimateDurationMs equals lead plus chord plus tail`() {
        val q = createQuestion(SuspendedChordQuality.MAJOR_TRIAD)
        val expected = SuspendedChordTrainingAudioBuilder.LEAD_SILENCE_MS +
            SuspendedChordTrainingAudioBuilder.CHORD_DURATION_MS +
            SuspendedChordTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs is positive`() {
        val q = createQuestion(SuspendedChordQuality.MAJOR_TRIAD)
        assertTrue(builder.estimateDurationMs(q) > 0)
    }

    @Test
    fun `estimateDurationMs is consistent across qualities`() {
        val major = builder.estimateDurationMs(createQuestion(SuspendedChordQuality.MAJOR_TRIAD))
        val sus2sus4 = builder.estimateDurationMs(createQuestion(SuspendedChordQuality.SUS2_SUS4))
        assertEquals(major, sus2sus4)
    }

    // ── 常量合理性 ────────────────────────────────────────────

    @Test
    fun `chord duration is reasonable`() {
        assertTrue(
            "和弦时长应在 1-5 秒之间",
            SuspendedChordTrainingAudioBuilder.CHORD_DURATION_MS in 1000..5000
        )
    }

    @Test
    fun `lead silence is reasonable`() {
        assertTrue(
            "前导静音应在 0-1 秒之间",
            SuspendedChordTrainingAudioBuilder.LEAD_SILENCE_MS in 0..1000
        )
    }

    @Test
    fun `tail silence is reasonable`() {
        assertTrue(
            "尾部静音应在 0-2 秒之间",
            SuspendedChordTrainingAudioBuilder.TAIL_SILENCE_MS in 0..2000
        )
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, SuspendedChordTrainingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `default velocity is reasonable`() {
        assertTrue(
            "默认力度应在 1-127 之间",
            SuspendedChordTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127
        )
    }

    @Test
    fun `softclip K is positive`() {
        assertTrue(
            "软限幅拐点应为正数",
            SuspendedChordTrainingAudioBuilder.SOFTCLIP_K > 0f
        )
    }

    // ── renderChord 直接调用 ──────────────────────────────────

    @Test
    fun `renderChord with empty list returns empty`() {
        val audio = builder.renderChord(emptyList())
        assertEquals(0, audio.size)
    }

    @Test
    fun `renderChord produces audio with 3 notes`() {
        val notes = listOf(60, 64, 67) // C major
        val audio = builder.renderChord(notes)
        assertTrue("3音和弦渲染应非空", audio.isNotEmpty())
    }

    @Test
    fun `renderChord produces audio with 4 notes`() {
        val notes = listOf(60, 62, 65, 67) // C sus2sus4
        val audio = builder.renderChord(notes)
        assertTrue("4音和弦渲染应非空", audio.isNotEmpty())
    }

    @Test
    fun `renderChord all samples in range`() {
        val notes = listOf(48, 50, 53, 55) // C sus2sus4
        val audio = builder.renderChord(notes)
        for (sample in audio) {
            assertTrue("样本应在 [-1, 1] 范围内", sample in -1.0f..1.0f)
        }
    }
}
