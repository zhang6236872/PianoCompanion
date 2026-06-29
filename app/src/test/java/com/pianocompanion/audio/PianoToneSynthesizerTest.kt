package com.pianocompanion.audio

import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PianoToneSynthesizer] 单元测试。
 */
class PianoToneSynthesizerTest {

    private val synth = PianoToneSynthesizer(sampleRate = 44100)

    // ===== 采样数量测试 =====

    @Test
    fun `样本数与时长匹配`() {
        // TENUTO 时值因子为 1.0，样本数精确匹配
        val durationMs = 1000L
        val buffer = synth.synthesize(440.0, durationMs, articulation = Articulation.TENUTO)
        // 44100 Hz × 1s = 44100 个样本
        assertEquals(44100, buffer.size)
    }

    @Test
    fun `短音符产生正确样本数`() {
        val buffer = synth.synthesize(440.0, 50, articulation = Articulation.TENUTO)
        // 44100 × 50 / 1000 = 2205
        assertEquals(2205, buffer.size)
    }

    @Test
    fun `极短音符至少产生1个样本`() {
        val buffer = synth.synthesize(440.0, 0)
        assertTrue(buffer.size >= 1)
    }

    // ===== 振幅范围测试 =====

    @Test
    fun `所有采样值在正负1范围内`() {
        val buffer = synth.synthesize(440.0, 500, velocity = 127)
        buffer.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1, 1]", sample in -1f..1f)
        }
    }

    @Test
    fun `起音阶段从零渐入`() {
        val buffer = synth.synthesize(440.0, 100)
        // 第一个样本应该接近 0（起音开始）
        assertTrue(
            "第一个样本 ${buffer[0]} 应接近 0",
            kotlin.math.abs(buffer[0]) < 0.01f
        )
    }

    @Test
    fun `峰值振幅为正`() {
        val buffer = synth.synthesize(440.0, 500, velocity = 100)
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("峰值振幅 $peak 应为正", peak > 0.01f)
    }

    // ===== 力度映射测试 =====

    @Test
    fun `高力度振幅大于低力度`() {
        val loud = synth.synthesize(440.0, 300, velocity = 120)
        val soft = synth.synthesize(440.0, 300, velocity = 30)
        val peakLoud = loud.maxOf { kotlin.math.abs(it) }
        val peakSoft = soft.maxOf { kotlin.math.abs(it) }
        assertTrue(
            "高力度峰值 $peakLoud 应大于低力度峰值 $peakSoft",
            peakLoud > peakSoft
        )
    }

    @Test
    fun `力度为0时振幅为0`() {
        val buffer = synth.synthesize(440.0, 300, velocity = 0)
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("力度0时振幅应为0，实际 $peak", peak < 0.001f)
    }

    // ===== 指数衰减测试 =====

    @Test
    fun `音符后半段振幅小于前半段`() {
        // 长音符，衰减明显
        val buffer = synth.synthesize(440.0, 2000)
        val midPoint = buffer.size / 2
        val firstHalfPeak = buffer.copyOfRange(0, midPoint).maxOf { kotlin.math.abs(it) }
        val secondHalfPeak = buffer.copyOfRange(midPoint, buffer.size).maxOf { kotlin.math.abs(it) }
        assertTrue(
            "前半段峰值 $firstHalfPeak 应大于后半段 $secondHalfPeak",
            firstHalfPeak > secondHalfPeak
        )
    }

    @Test
    fun `高频衰减比低频快`() {
        val lowBuffer = synth.synthesize(110.0, 2000, velocity = 80)
        val highBuffer = synth.synthesize(1760.0, 2000, velocity = 80)

        val lowMidPoint = lowBuffer.size / 2
        val highMidPoint = highBuffer.size / 2

        // 高频在中间点的衰减比例（相对于峰值）应大于低频
        val lowPeak = lowBuffer.maxOf { kotlin.math.abs(it) }
        val highPeak = highBuffer.maxOf { kotlin.math.abs(it) }

        val lowMidRatio = kotlin.math.abs(lowBuffer[lowMidPoint]) / lowPeak
        val highMidRatio = kotlin.math.abs(highBuffer[highMidPoint]) / highPeak

        assertTrue(
            "高频衰减比例 $highMidRatio 应小于低频 $lowMidRatio",
            highMidRatio < lowMidRatio
        )
    }

    // ===== 演奏法测试 =====

    @Test
    fun `断奏时值比正常短`() {
        val normal = synth.synthesize(440.0, 1000, articulation = Articulation.NONE)
        val staccato = synth.synthesize(440.0, 1000, articulation = Articulation.STACCATO)
        assertTrue(
            "断奏 ${staccato.size} 应短于正常 ${normal.size}",
            staccato.size < normal.size
        )
    }

    @Test
    fun `短断奏时值比断奏更短`() {
        val staccato = synth.synthesize(440.0, 1000, articulation = Articulation.STACCATO)
        val staccatissimo = synth.synthesize(440.0, 1000, articulation = Articulation.STACCATISSIMO)
        assertTrue(
            "短断奏 ${staccatissimo.size} 应短于断奏 ${staccato.size}",
            staccatissimo.size < staccato.size
        )
    }

    @Test
    fun `保持音时值等于记谱时值`() {
        val tenuto = synth.synthesize(440.0, 1000, articulation = Articulation.TENUTO)
        assertEquals(44100, tenuto.size)
    }

    @Test
    fun `默认演奏法时值为记谱时值的0_9倍`() {
        val normal = synth.synthesize(440.0, 1000, articulation = Articulation.NONE)
        // 0.9 × 1000ms = 900ms → 44100 × 0.9 = 39690 样本
        assertEquals(39690, normal.size)
    }

    @Test
    fun `重音力度大于正常`() {
        val normal = synth.synthesize(440.0, 300, velocity = 64, articulation = Articulation.NONE)
        val accent = synth.synthesize(440.0, 300, velocity = 64, articulation = Articulation.ACCENT)
        val normalPeak = normal.maxOf { kotlin.math.abs(it) }
        val accentPeak = accent.maxOf { kotlin.math.abs(it) }
        assertTrue(
            "重音峰值 $accentPeak 应大于正常 $normalPeak",
            accentPeak > normalPeak
        )
    }

    @Test
    fun `强音力度最大`() {
        val accent = synth.synthesize(440.0, 300, velocity = 64, articulation = Articulation.ACCENT)
        val marcato = synth.synthesize(440.0, 300, velocity = 64, articulation = Articulation.MARCATO)
        val accentPeak = accent.maxOf { kotlin.math.abs(it) }
        val marcatoPeak = marcato.maxOf { kotlin.math.abs(it) }
        assertTrue(
            "强音峰值 $marcatoPeak 应大于重音 $accentPeak",
            marcatoPeak > accentPeak
        )
    }

    // ===== 频率正确性测试 =====

    @Test
    fun `不同频率产生不同波形`() {
        val freq1 = synth.synthesize(440.0, 100)
        val freq2 = synth.synthesize(880.0, 100)
        // 不同频率应该产生不同的波形（至少在第50个样本处不同）
        assertTrue(
            "不同频率波形应不同",
            kotlin.math.abs(freq1[50] - freq2[50]) > 0.001f
        )
    }

    @Test
    fun `使用实际MIDI频率合成`() {
        val midi = 60 // C4
        val freq = MusicUtils.midiToFrequency(midi)
        val buffer = synth.synthesize(freq, 100)
        // C4 ≈ 261.63 Hz
        assertTrue("C4 频率应约 261.63 Hz", kotlin.math.abs(freq - 261.63) < 0.1)
        assertTrue(buffer.size > 0)
    }

    // ===== 确定性测试 =====

    @Test
    fun `相同参数产生相同输出`() {
        val buf1 = synth.synthesize(440.0, 500, velocity = 80)
        val buf2 = synth.synthesize(440.0, 500, velocity = 80)
        assertArrayEquals(buf1, buf2, 0.0001f)
    }

    @Test
    fun `不同力度产生不同输出`() {
        val buf1 = synth.synthesize(440.0, 500, velocity = 50)
        val buf2 = synth.synthesize(440.0, 500, velocity = 90)
        // 至少在某些采样处不同
        var different = false
        for (i in buf1.indices) {
            if (kotlin.math.abs(buf1[i] - buf2[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue("不同力度应产生不同输出", different)
    }

    // ===== 自定义采样率测试 =====

    @Test
    fun `自定义采样率正确`() {
        val synth22k = PianoToneSynthesizer(sampleRate = 22050)
        val buffer = synth22k.synthesize(440.0, 1000, articulation = Articulation.TENUTO)
        assertEquals(22050, buffer.size)
    }

    // ===== 辅助方法 =====

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("采样 $i 不匹配", expected[i], actual[i], delta)
        }
    }
}
