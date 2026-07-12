package com.pianocompanion.meterrecognition

import org.junit.Assert.*
import org.junit.Test

/**
 * 拍号听辨训练音频构建器单元测试。
 */
class MeterRecognitionAudioBuilderTest {

    private val builder = MeterRecognitionAudioBuilder()

    // ── 基础渲染 ──────────────────────────────────────

    @Test
    fun `渲染非空缓冲区`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `渲染值在有效范围内`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.ADVANCED, MeterRecognitionTempo.FAST)
        val pcm = builder.render(q)
        for (sample in pcm) {
            assertTrue("采样值 $sample 超出范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `渲染非全零缓冲区`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        val pcm = builder.render(q)
        val hasNonZero = pcm.any { it != 0.0f }
        assertTrue("缓冲区全为零", hasNonZero)
    }

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同题目渲染相同音频`() {
        val engine = MeterRecognitionEngine.withSeed(42)
        val q = engine.generate(MeterRecognitionDifficulty.INTERMEDIATE)
        val pcm1 = builder.render(q)
        val pcm2 = builder.render(q)
        assertArrayEquals(pcm1, pcm2, 0.0001f)
    }

    // ── PCM 长度合理性 ─────────────────────────────────

    @Test
    fun `PCM长度至少覆盖最后一个onset加click加尾部静音`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER, MeterRecognitionTempo.SLOW)
        val pcm = builder.render(q)
        val onsets = builder.computeOnsetTimes(q.meter, q.tempo, q.measureRepeat)
        val expectedMinMs = onsets.last() + MeterRecognitionAudioBuilder.CLICK_DURATION_MS +
            MeterRecognitionAudioBuilder.TAIL_SILENCE_MS
        val actualMs = pcm.size.toDouble() / MeterRecognitionAudioBuilder.DEFAULT_SAMPLE_RATE * 1000.0
        assertTrue("PCM 时长 ${actualMs}ms < 预期最小 ${expectedMinMs}ms", actualMs >= expectedMinMs - 10)
    }

    @Test
    fun `更多重复次数产生更长音频`() {
        val short = builder.renderMeter(MeterType.THREE_FOUR, MeterRecognitionTempo.SLOW, 2)
        val long = builder.renderMeter(MeterType.THREE_FOUR, MeterRecognitionTempo.SLOW, 4)
        assertTrue("重复 4 次应比 2 次长", long.size > short.size)
    }

    @Test
    fun `更快速度产生更短音频`() {
        val slow = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 4)
        val fast = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.FAST, 4)
        assertTrue("快速应比慢速短", fast.size < slow.size)
    }

    @Test
    fun `更多拍数的拍号产生更长音频`() {
        val two = builder.renderMeter(MeterType.TWO_FOUR, MeterRecognitionTempo.MEDIUM, 4)
        val four = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.MEDIUM, 4)
        assertTrue("4/4 应比 2/4 长", four.size > two.size)
    }

    // ── computeOnsetTimes 一致性 ────────────────────────

    @Test
    fun `computeOnsetTimes与Engine一致`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val builderOnsets = builder.computeOnsetTimes(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 4)
        val engineOnsets = engine.computeOnsetTimes(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 4)
        assertEquals(engineOnsets.size, builderOnsets.size)
        for (i in builderOnsets.indices) {
            assertEquals(engineOnsets[i], builderOnsets[i], 0.01)
        }
    }

    @Test
    fun `computeAccentPattern与Engine一致`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val builderAccents = builder.computeAccentPattern(MeterType.SIX_EIGHT, 3)
        val engineAccents = engine.computeAccentPattern(MeterType.SIX_EIGHT, 3)
        assertEquals(engineAccents.size, builderAccents.size)
        for (i in builderAccents.indices) {
            assertEquals(engineAccents[i], builderAccents[i])
        }
    }

    // ── estimateDurationMs ──────────────────────────────

    @Test
    fun `estimateDurationMs为正值`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        val duration = builder.estimateDurationMs(q)
        assertTrue(duration > 0)
    }

    @Test
    fun `estimateDurationMs与实际PCM时长一致`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.INTERMEDIATE, MeterRecognitionTempo.MEDIUM)
        val duration = builder.estimateDurationMs(q)
        val pcm = builder.render(q)
        val actualMs = pcm.size.toDouble() / MeterRecognitionAudioBuilder.DEFAULT_SAMPLE_RATE * 1000.0
        // 允许小误差（因整数采样舍入）
        assertTrue("估算 ${duration}ms vs 实际 ${actualMs}ms", kotlin.math.abs(duration - actualMs) < 50)
    }

    // ── 不同拍号渲染差异 ─────────────────────────────────

    @Test
    fun `2-4和3-4渲染不同`() {
        val twoFour = builder.renderMeter(MeterType.TWO_FOUR, MeterRecognitionTempo.SLOW, 4)
        val threeFour = builder.renderMeter(MeterType.THREE_FOUR, MeterRecognitionTempo.SLOW, 4)
        assertNotEquals(twoFour.size, threeFour.size)
    }

    @Test
    fun `4-4和6-8渲染不同`() {
        val fourFour = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 4)
        val sixEight = builder.renderMeter(MeterType.SIX_EIGHT, MeterRecognitionTempo.SLOW, 4)
        assertNotEquals(fourFour.size, sixEight.size)
    }

    // ── 边界情况 ────────────────────────────────────────

    @Test
    fun `measureRepeat为0返回空缓冲区`() {
        val pcm = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 0)
        assertEquals(0, pcm.size)
    }

    @Test
    fun `空onsets返回空缓冲区`() {
        val pcm = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 0)
        assertTrue(pcm.isEmpty())
    }

    // ── 强拍 vs 弱拍振幅差异 ─────────────────────────────

    @Test
    fun `强拍区域振幅大于弱拍区域`() {
        // 2/4, 1 小节: STRONG(0), WEAK(1)
        val pcm = builder.renderMeter(MeterType.TWO_FOUR, MeterRecognitionTempo.SLOW, 1)
        val sampleRate = MeterRecognitionAudioBuilder.DEFAULT_SAMPLE_RATE
        val onset0Sample = (MeterRecognitionAudioBuilder.LEAD_SILENCE_MS * sampleRate / 1000.0).toInt()
        val onset1Sample = onset0Sample + (MeterRecognitionTempo.SLOW.clickIntervalMs * sampleRate / 1000.0).toInt()
        val clickSamples = (sampleRate * MeterRecognitionAudioBuilder.CLICK_DURATION_MS / 1000.0).toInt()

        // 计算 STRONG click 的峰值
        var strongPeak = 0.0f
        for (i in onset0Sample until minOf(onset0Sample + clickSamples, pcm.size)) {
            strongPeak = maxOf(strongPeak, kotlin.math.abs(pcm[i]))
        }
        // 计算 WEAK click 的峰值
        var weakPeak = 0.0f
        for (i in onset1Sample until minOf(onset1Sample + clickSamples, pcm.size)) {
            weakPeak = maxOf(weakPeak, kotlin.math.abs(pcm[i]))
        }
        assertTrue("强拍峰值 $strongPeak 应大于弱拍峰值 $weakPeak", strongPeak > weakPeak)
    }

    @Test
    fun `4-4拍第3拍MEDIUM振幅介于STRONG和WEAK之间`() {
        // 4/4, 1 小节: STRONG(0), WEAK(1), MEDIUM(2), WEAK(3)
        val pcm = builder.renderMeter(MeterType.FOUR_FOUR, MeterRecognitionTempo.SLOW, 1)
        val sampleRate = MeterRecognitionAudioBuilder.DEFAULT_SAMPLE_RATE
        val interval = MeterRecognitionTempo.SLOW.clickIntervalMs * sampleRate / 1000.0
        val onset0 = (MeterRecognitionAudioBuilder.LEAD_SILENCE_MS * sampleRate / 1000.0).toInt()
        val clickSamples = (sampleRate * MeterRecognitionAudioBuilder.CLICK_DURATION_MS / 1000.0).toInt()

        fun peakAt(onsetSample: Int): Float {
            var peak = 0.0f
            for (i in onsetSample until minOf(onsetSample + clickSamples, pcm.size)) {
                peak = maxOf(peak, kotlin.math.abs(pcm[i]))
            }
            return peak
        }

        val strongPeak = peakAt(onset0)
        val mediumPeak = peakAt(onset0 + (2 * interval).toInt())
        val weakPeak = peakAt(onset0 + interval.toInt())

        assertTrue("STRONG($strongPeak) > MEDIUM($mediumPeak)", strongPeak > mediumPeak)
        assertTrue("MEDIUM($mediumPeak) > WEAK($weakPeak)", mediumPeak > weakPeak)
    }

    // ── 全部拍号渲染不崩溃 ──────────────────────────────

    @Test
    fun `全部6种拍号渲染不崩溃`() {
        for (meter in MeterType.ALL) {
            for (tempo in MeterRecognitionTempo.ALL) {
                val pcm = builder.renderMeter(meter, tempo, 4)
                assertTrue("渲染 ${meter.symbol} ${tempo.name} 失败", pcm.isNotEmpty())
            }
        }
    }

    @Test
    fun `全部渲染值在有效范围内`() {
        for (meter in MeterType.ALL) {
            for (tempo in MeterRecognitionTempo.ALL) {
                val pcm = builder.renderMeter(meter, tempo, 4)
                for (sample in pcm) {
                    assertTrue(sample in -1.0f..1.0f)
                }
            }
        }
    }

    // ── AccentLevel 音频特征 ────────────────────────────

    @Test
    fun `STRONG频率880Hz`() {
        assertEquals(880.0, AccentLevel.STRONG.frequency, 0.1)
    }

    @Test
    fun `WEAK频率660Hz`() {
        assertEquals(660.0, AccentLevel.WEAK.frequency, 0.1)
    }

    @Test
    fun `MEDIUM频率740Hz`() {
        assertEquals(740.0, AccentLevel.MEDIUM.frequency, 0.1)
    }
}
