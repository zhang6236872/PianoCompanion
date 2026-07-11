package com.pianocompanion.thirteenthchordtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十三和弦色彩听辨训练音频构建器单元测试。
 *
 * 验证渲染非空、不削波、不同品质差异、estimateDurationMs 正确性、常量合理性等。
 */
class ThirteenthChordTrainingAudioBuilderTest {

    private val builder = ThirteenthChordTrainingAudioBuilder()

    // ── 渲染非空 ──────────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        val buffer = builder.render(q)
        assertTrue("渲染缓冲区不应为空", buffer.isNotEmpty())
    }

    @Test
    fun `renderChord with empty notes returns empty`() {
        val result = builder.renderChord(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `renderChord with single note produces sound`() {
        val result = builder.renderChord(listOf(60))
        assertTrue("单音渲染也应非空", result.isNotEmpty())
    }

    // ── 不削波 ──────────────────────────────────────────────────

    @Test
    fun `rendered audio stays within -1 to 1`() {
        for (seed in 0..20) {
            val q = ThirteenthChordTrainingEngine.withSeed(seed.toLong()).generate(ThirteenthChordDifficulty.ADVANCED)
            val buffer = builder.render(q)
            for (i in buffer.indices) {
                assertTrue(
                    "采样值 ${buffer[i]} 超出 [-1, 1] (seed=$seed, idx=$i)",
                    buffer[i] in -1.0f..1.0f
                )
            }
        }
    }

    @Test
    fun `renderChord with 7 notes does not clip`() {
        // C major 13th from C4: 7 notes spanning almost 2 octaves
        val result = builder.renderChord(listOf(60, 64, 67, 71, 74, 77, 81))
        for (i in result.indices) {
            assertTrue("采样值 ${result[i]} 超出 [-1, 1]", result[i] in -1.0f..1.0f)
        }
    }

    // ── 不同品质差异 ──────────────────────────────────────────

    @Test
    fun `different qualities produce different audio`() {
        val maj13 = builder.renderChord(
            ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MAJOR_13, 60)
        )
        val min13 = builder.renderChord(
            ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MINOR_13, 60)
        )
        assertNotEquals("大十三和小十三的音频应不同", maj13.toList(), min13.toList())
    }

    @Test
    fun `dominant 13 and minor major 13 produce different audio`() {
        val dom13 = builder.renderChord(
            ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.DOMINANT_13, 60)
        )
        val mMaj13 = builder.renderChord(
            ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(ThirteenthChordQuality.MINOR_MAJOR_13, 60)
        )
        assertNotEquals("属十三和小大十三的音频应不同", dom13.toList(), mMaj13.toList())
    }

    @Test
    fun `all 5 qualities produce distinct audio`() {
        val audios = ThirteenthChordQuality.ALL.map { quality ->
            builder.renderChord(
                ThirteenthChordTrainingEngine.buildThirteenthChordMidiNotes(quality, 60)
            ).toList()
        }
        for (i in audios.indices) {
            for (j in i + 1 until audios.size) {
                assertNotEquals(
                    "${ThirteenthChordQuality.ALL[i].displayName} 和 ${ThirteenthChordQuality.ALL[j].displayName} 的音频应不同",
                    audios[i], audios[j]
                )
            }
        }
    }

    // ── buffer 长度 ──────────────────────────────────────────

    @Test
    fun `buffer length matches expected duration`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        val buffer = builder.render(q)
        val estimatedMs = builder.estimateDurationMs(q)
        // 允许 ±200 samples 的误差（浮点取整）
        val expectedMinSamples = (estimatedMs.toDouble() / 1000.0 * ThirteenthChordTrainingAudioBuilder.SAMPLE_RATE * 0.95).toInt()
        val expectedMaxSamples = (estimatedMs.toDouble() / 1000.0 * ThirteenthChordTrainingAudioBuilder.SAMPLE_RATE * 1.15).toInt()
        assertTrue(
            "缓冲区长度 ${buffer.size} 应在 [$expectedMinSamples, $expectedMaxSamples] 范围内",
            buffer.size in expectedMinSamples..expectedMaxSamples
        )
    }

    // ── estimateDurationMs ────────────────────────────────────

    @Test
    fun `estimateDurationMs is correct`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        val expected = ThirteenthChordTrainingAudioBuilder.LEAD_SILENCE_MS +
            (ThirteenthChordTrainingAudioBuilder.CHORD_DURATION_MS * ThirteenthChordTrainingAudioBuilder.ARTICULATION_DURATION_FACTOR).toLong() +
            ThirteenthChordTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, builder.estimateDurationMs(q))
    }

    @Test
    fun `estimateDurationMs is positive`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.ADVANCED)
        assertTrue(builder.estimateDurationMs(q) > 0)
    }

    @Test
    fun `estimateDurationMs includes lead silence`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        val duration = builder.estimateDurationMs(q)
        assertTrue(
            "估算时长应包含前导静音",
            duration >= ThirteenthChordTrainingAudioBuilder.LEAD_SILENCE_MS
        )
    }

    @Test
    fun `estimateDurationMs includes tail silence`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        val duration = builder.estimateDurationMs(q)
        assertTrue(
            "估算时长应包含尾部静音",
            duration >= ThirteenthChordTrainingAudioBuilder.TAIL_SILENCE_MS
        )
    }

    @Test
    fun `estimateDurationMs applies articulation factor`() {
        val q = ThirteenthChordTrainingEngine.withSeed(1L).generate(ThirteenthChordDifficulty.BEGINNER)
        val duration = builder.estimateDurationMs(q)
        val rawTotal = ThirteenthChordTrainingAudioBuilder.LEAD_SILENCE_MS +
            ThirteenthChordTrainingAudioBuilder.CHORD_DURATION_MS +
            ThirteenthChordTrainingAudioBuilder.TAIL_SILENCE_MS
        assertTrue(
            "估算时长应因运音法因子(0.90)而短于原始总时长",
            duration < rawTotal
        )
    }

    // ── 常量合理性 ──────────────────────────────────────────

    @Test
    fun `chord duration is at least 2000ms`() {
        assertTrue(
            "十三和弦持续时间应 ≥2000ms（7 音和弦需要足够辨识时间）",
            ThirteenthChordTrainingAudioBuilder.CHORD_DURATION_MS >= 2000L
        )
    }

    @Test
    fun `lead silence is at least 100ms`() {
        assertTrue(
            "前导静音应 ≥100ms",
            ThirteenthChordTrainingAudioBuilder.LEAD_SILENCE_MS >= 100L
        )
    }

    @Test
    fun `tail silence is at least 300ms`() {
        assertTrue(
            "尾部静音应 ≥300ms",
            ThirteenthChordTrainingAudioBuilder.TAIL_SILENCE_MS >= 300L
        )
    }

    @Test
    fun `default velocity is in valid MIDI range`() {
        assertTrue(
            "默认力度应在 [1, 127] 范围内",
            ThirteenthChordTrainingAudioBuilder.DEFAULT_VELOCITY in 1..127
        )
    }

    @Test
    fun `softclip K is positive`() {
        assertTrue(
            "软限幅拐点应为正数",
            ThirteenthChordTrainingAudioBuilder.SOFTCLIP_K > 0f
        )
    }

    @Test
    fun `articulation duration factor is between 0 and 1`() {
        assertTrue(
            "运音法时长因子应在 (0, 1) 范围内",
            ThirteenthChordTrainingAudioBuilder.ARTICULATION_DURATION_FACTOR in 0.0..1.0
        )
    }

    @Test
    fun `sample rate is standard 44100`() {
        assertEquals(
            "采样率应为 44100Hz",
            44100,
            ThirteenthChordTrainingAudioBuilder.SAMPLE_RATE
        )
    }
}
