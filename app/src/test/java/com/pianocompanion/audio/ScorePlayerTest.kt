package com.pianocompanion.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScorePlayer] 单元测试。
 *
 * 测试纯 Kotlin 逻辑部分（floatToPcm16 转换）。
 * AudioTrack 实际播放部分需要真机验证。
 */
class ScorePlayerTest {

    @Test
    fun `floatToPcm16 正常值转换正确`() {
        val input = floatArrayOf(0.0f, 0.5f, -0.5f, 1.0f, -1.0f)
        val output = ScorePlayer.floatToPcm16(input)
        assertEquals(0, output[0].toInt())           // 0.0 → 0
        assertEquals(16383, output[1].toInt())        // 0.5 × 32767 = 16383.5 → 截断 16383
        assertEquals(-16383, output[2].toInt())       // -0.5 × 32767 = -16383.5 → 截断 -16383
        assertEquals(32767, output[3].toInt())        // 1.0 → MAX
        assertEquals(-32767, output[4].toInt())       // -1.0 → -MAX
    }

    @Test
    fun `floatToPcm16 超出范围的值被钳位`() {
        val input = floatArrayOf(2.0f, -2.0f, 100.0f, -100.0f)
        val output = ScorePlayer.floatToPcm16(input)
        assertEquals(32767, output[0].toInt())   // 钳位到 MAX
        assertEquals(-32767, output[1].toInt())  // 钳位到 -MAX
        assertEquals(32767, output[2].toInt())
        assertEquals(-32767, output[3].toInt())
    }

    @Test
    fun `floatToPcm16 空数组返回空数组`() {
        val output = ScorePlayer.floatToPcm16(floatArrayOf())
        assertEquals(0, output.size)
    }

    @Test
    fun `floatToPcm16 单元素数组`() {
        val output = ScorePlayer.floatToPcm16(floatArrayOf(0.0f))
        assertEquals(1, output.size)
        assertEquals(0, output[0].toInt())
    }

    @Test
    fun `floatToPcm16 输出长度等于输入长度`() {
        val input = FloatArray(1000) { (it % 200 - 100) / 100.0f }
        val output = ScorePlayer.floatToPcm16(input)
        assertEquals(input.size, output.size)
    }

    @Test
    fun `floatToPcm16 正弦波往返转换保真`() {
        // 生成正弦波 → PCM → 验证PCM范围正确
        val samples = 441
        val input = FloatArray(samples) { i ->
            kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 44100.0).toFloat()
        }
        val pcm = ScorePlayer.floatToPcm16(input)
        assertEquals(samples, pcm.size)

        // 所有值应在 Short 范围内
        pcm.forEach { v ->
            assertTrue("PCM 值 $v 超出 Short 范围", v in Short.MIN_VALUE..Short.MAX_VALUE)
        }

        // 峰值应合理
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("峰值 $peak 应大于 0", peak > 0)
    }
}
