package com.pianocompanion.eleventhchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十一和弦色彩听辨训练音频构建器单元测试。
 *
 * 验证渲染非空、不削波、不同品质差异、时长预估等。
 */
class EleventhChordTrainingAudioBuilderTest {

    private val builder = EleventhChordTrainingAudioBuilder()

    // ── 渲染非空 ──────────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val engine = EleventhChordTrainingEngine.withSeed(1L)
        val question = engine.generate(EleventhChordDifficulty.ADVANCED)
        val audio = builder.render(question)
        assertTrue("渲染缓冲区不应为空", audio.isNotEmpty())
    }

    @Test
    fun `render chord with empty list returns empty`() {
        val audio = builder.renderChord(emptyList())
        assertEquals(0, audio.size)
    }

    @Test
    fun `render chord with single note produces audio`() {
        val audio = builder.renderChord(listOf(60))
        assertTrue("单音渲染应产生音频", audio.isNotEmpty())
    }

    @Test
    fun `render chord with 6 notes produces audio`() {
        val audio = builder.renderChord(listOf(60, 64, 67, 70, 74, 77))
        assertTrue("6 音和弦应产生音频", audio.isNotEmpty())
    }

    // ── 不削波 ──────────────────────────────────────────────

    @Test
    fun `all samples are within -1 to 1`() {
        for (seed in 0..20) {
            val engine = EleventhChordTrainingEngine.withSeed(seed.toLong())
            val question = engine.generate(EleventhChordDifficulty.ADVANCED)
            val audio = builder.render(question)
            for (i in audio.indices) {
                assertTrue(
                    "采样 ${audio[i]} 超出 [-1, 1] (seed=$seed, idx=$i)",
                    audio[i] >= -1.0f && audio[i] <= 1.0f
                )
            }
        }
    }

    @Test
    fun `six note chord does not clip`() {
        // 6 notes simultaneously — verify soft clipping keeps everything in range
        val audio = builder.renderChord(listOf(48, 52, 55, 58, 62, 65))
        for (i in audio.indices) {
            assertTrue("采样超出范围 (idx=$i)", audio[i] >= -1.0f && audio[i] <= 1.0f)
        }
    }

    // ── 不同品质差异 ──────────────────────────────────────────

    @Test
    fun `different qualities produce different audio`() {
        val maj11 = builder.renderChord(
            EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MAJOR_11, 60)
        )
        val halfDim11 = builder.renderChord(
            EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.HALF_DIMINISHED_11, 60)
        )
        // The audio should differ because the MIDI notes differ
        assertEquals(maj11.size, halfDim11.size)
        var foundDifference = false
        for (i in maj11.indices) {
            if (kotlin.math.abs(maj11[i] - halfDim11[i]) > 0.001f) {
                foundDifference = true
                break
            }
        }
        assertTrue("不同品质应产生不同音频", foundDifference)
    }

    @Test
    fun `major 11 and minor 11 produce different audio`() {
        val maj11 = builder.renderChord(
            EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MAJOR_11, 60)
        )
        val min11 = builder.renderChord(
            EleventhChordTrainingEngine.buildEleventhChordMidiNotes(EleventhChordQuality.MINOR_11, 60)
        )
        assertEquals(maj11.size, min11.size)
        var foundDifference = false
        for (i in maj11.indices) {
            if (kotlin.math.abs(maj11[i] - min11[i]) > 0.001f) {
                foundDifference = true
                break
            }
        }
        assertTrue("大十一和小十一应产生不同音频", foundDifference)
    }

    // ── estimateDurationMs ───────────────────────────────────

    @Test
    fun `estimateDurationMs is reasonable`() {
        val engine = EleventhChordTrainingEngine.withSeed(1L)
        val question = engine.generate(EleventhChordDifficulty.ADVANCED)
        val duration = builder.estimateDurationMs(question)
        // LEAD(200) + CHORD(2200*0.9=1980) + TAIL(500) = 2680
        assertEquals(2680L, duration)
    }

    @Test
    fun `estimateDurationMs includes all segments`() {
        val engine = EleventhChordTrainingEngine.withSeed(1L)
        val question = engine.generate(EleventhChordDifficulty.BEGINNER)
        val duration = builder.estimateDurationMs(question)
        assertTrue(
            "时长应包含前导+和弦+尾部",
            duration > EleventhChordTrainingAudioBuilder.LEAD_SILENCE_MS +
                EleventhChordTrainingAudioBuilder.TAIL_SILENCE_MS
        )
    }

    // ── 常量合理性 ──────────────────────────────────────────

    @Test
    fun `chord duration is at least 2 seconds`() {
        assertTrue(
            "和弦持续时间应至少 2 秒让用户辨识",
            EleventhChordTrainingAudioBuilder.CHORD_DURATION_MS >= 2000L
        )
    }

    @Test
    fun `lead silence is positive`() {
        assertTrue(
            "前导静音应为正数",
            EleventhChordTrainingAudioBuilder.LEAD_SILENCE_MS > 0L
        )
    }

    @Test
    fun `tail silence is positive`() {
        assertTrue(
            "尾部静音应为正数",
            EleventhChordTrainingAudioBuilder.TAIL_SILENCE_MS > 0L
        )
    }

    @Test
    fun `default velocity is reasonable`() {
        assertTrue(
            "默认力度应在合理范围 (1-127)",
            EleventhChordTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127
        )
    }

    @Test
    fun `softclip constant is positive`() {
        assertTrue(
            "软限幅拐点应为正数",
            EleventhChordTrainingAudioBuilder.SOFTCLIP_K > 0f
        )
    }

    @Test
    fun `articulation factor is less than 1`() {
        assertTrue(
            "运音法时长因子应小于 1",
            EleventhChordTrainingAudioBuilder.ARTICULATION_DURATION_FACTOR < 1.0
        )
        assertTrue(
            "运音法时长因子应大于 0",
            EleventhChordTrainingAudioBuilder.ARTICULATION_DURATION_FACTOR > 0.0
        )
    }

    // ── 渲染长度 ──────────────────────────────────────────────

    @Test
    fun `render length matches estimate`() {
        val engine = EleventhChordTrainingEngine.withSeed(1L)
        val question = engine.generate(EleventhChordDifficulty.ADVANCED)
        val audio = builder.render(question)
        val estimatedMs = builder.estimateDurationMs(question)
        val estimatedSamples = (EleventhChordTrainingAudioBuilder.SAMPLE_RATE.toDouble() * estimatedMs / 1000.0).toInt()
        // Allow some tolerance for rounding
        assertTrue(
            "渲染长度 (${-1}) 应接近估算 (${estimatedSamples})",
            kotlin.math.abs(audio.size - estimatedSamples) <= 2
        )
    }

    @Test
    fun `render produces consistent length for same note count`() {
        val audio1 = builder.renderChord(listOf(48, 52, 55, 58, 62, 65))
        val audio2 = builder.renderChord(listOf(55, 59, 62, 65, 69, 72))
        // Same number of notes → same length (determined by the longest tone)
        assertEquals("同数量音符应产生相同长度", audio1.size, audio2.size)
    }
}
