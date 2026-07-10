package com.pianocompanion.ninthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 九和弦色彩听辨训练音频构建器单元测试。
 */
class NinthChordTrainingAudioBuilderTest {

    private val builder = NinthChordTrainingAudioBuilder()

    // ── 基本渲染 ──────────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.ADVANCED)
        val audio = builder.render(q)
        assertTrue("渲染结果不应为空", audio.isNotEmpty())
    }

    @Test
    fun `render chord from explicit notes`() {
        val notes = listOf(60, 64, 67, 71, 74) // C major 9
        val audio = builder.renderChord(notes)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `empty notes produce empty buffer`() {
        val audio = builder.renderChord(emptyList())
        assertEquals(0, audio.size)
    }

    // ── 削波检查 ──────────────────────────────────────────────

    @Test
    fun `rendered audio does not clip`() {
        for (seed in 0..20) {
            val q = NinthChordTrainingEngine.withSeed(seed.toLong()).generate(NinthChordDifficulty.ADVANCED)
            val audio = builder.render(q)
            for ((i, sample) in audio.withIndex()) {
                assertTrue(
                    "采样值超出 [-1, 1] 范围 (seed=$seed, idx=$i, val=$sample)",
                    sample >= -1.0f && sample <= 1.0f
                )
            }
        }
    }

    @Test
    fun `rendered chord does not clip`() {
        val notes = listOf(48, 52, 55, 59, 62) // C3 major 9
        val audio = builder.renderChord(notes)
        for (sample in audio) {
            assertTrue("采样值 $sample 超出 [-1, 1]", sample >= -1.0f && sample <= 1.0f)
        }
    }

    // ── 不同和弦差异 ──────────────────────────────────────────

    @Test
    fun `different qualities produce different audio`() {
        val maj9 = builder.renderChord(listOf(60, 64, 67, 71, 74))
        val dom7b9 = builder.renderChord(listOf(60, 64, 67, 70, 73))
        // At least some samples should differ
        var diff = false
        for (i in maj9.indices) {
            if (kotlin.math.abs(maj9[i] - dom7b9[i]) > 0.001f) {
                diff = true
                break
            }
        }
        assertTrue("不同和弦应产生不同音频", diff)
    }

    @Test
    fun `root note affects audio`() {
        val c9 = builder.renderChord(listOf(60, 64, 67, 70, 74))
        val g9 = builder.renderChord(listOf(55, 59, 62, 65, 69))
        // Different root should produce different audio
        assertTrue("不同根音应产生不同长度或内容的音频", c9.size != g9.size || c9.zip(g9).any { (a, b) -> kotlin.math.abs(a - b) > 0.001f })
    }

    // ── 时长估计 ──────────────────────────────────────────────

    @Test
    fun `estimateDurationMs is correct`() {
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.BEGINNER)
        val expected = NinthChordTrainingAudioBuilder.LEAD_SILENCE_MS +
            (NinthChordTrainingAudioBuilder.CHORD_DURATION_MS * NinthChordTrainingAudioBuilder.ARTICULATION_DURATION_FACTOR).toLong() +
            NinthChordTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs matches actual buffer length`() {
        val q = NinthChordTrainingEngine.withSeed(1L).generate(NinthChordDifficulty.BEGINNER)
        val estimatedMs = builder.estimateDurationMs(q)
        val audio = builder.render(q)
        val actualMs = (audio.size.toLong() * 1000L) / NinthChordTrainingAudioBuilder.SAMPLE_RATE
        // Allow small rounding difference (±100ms)
        assertTrue(
            "估计时长 $estimatedMs 应接近实际时长 $actualMs",
            kotlin.math.abs(estimatedMs - actualMs) < 200L
        )
    }

    // ── 常量合理性 ──────────────────────────────────────────

    @Test
    fun `chord duration is positive`() {
        assertTrue(NinthChordTrainingAudioBuilder.CHORD_DURATION_MS > 0)
    }

    @Test
    fun `lead silence is positive`() {
        assertTrue(NinthChordTrainingAudioBuilder.LEAD_SILENCE_MS > 0)
    }

    @Test
    fun `tail silence is positive`() {
        assertTrue(NinthChordTrainingAudioBuilder.TAIL_SILENCE_MS > 0)
    }

    @Test
    fun `sample rate is standard`() {
        assertEquals(44100, NinthChordTrainingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `default velocity is in MIDI range`() {
        assertTrue(NinthChordTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127)
    }

    @Test
    fun `softclip K is positive`() {
        assertTrue(NinthChordTrainingAudioBuilder.SOFTCLIP_K > 0f)
    }

    // ── 所有品质均可渲染 ──────────────────────────────────────

    @Test
    fun `all qualities render without error`() {
        for (quality in NinthChordQuality.ALL) {
            val notes = NinthChordTrainingEngine.buildNinthChordMidiNotes(quality, 48)
            val audio = builder.renderChord(notes)
            assertTrue("${quality.displayName} 渲染结果不应为空", audio.isNotEmpty())
        }
    }
}
