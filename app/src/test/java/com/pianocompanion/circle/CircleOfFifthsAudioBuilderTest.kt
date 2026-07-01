package com.pianocompanion.circle

import org.junit.Assert.*
import org.junit.Test

/**
 * 五度圈音频渲染器 [CircleOfFifthsAudioBuilder] 单元测试。
 *
 * 验证顺阶和弦序列与音阶的 PCM 渲染：
 * - 输出非空且长度合理
 * - 采样值在有效范围 [-1, 1] 内
 * - 前导静音与尾部静音存在
 * - 时长估算与实际采样数一致
 */
class CircleOfFifthsAudioBuilderTest {

    private val builder = CircleOfFifthsAudioBuilder()
    private val cm = CircleKey(0, CircleMode.MAJOR)
    private val gm = CircleKey(7, CircleMode.MAJOR)

    // ════════════════════════════════════════
    //  顺阶和弦渲染
    // ════════════════════════════════════════

    @Test
    fun `渲染顺阶和弦返回非空FloatArray`() {
        val audio = builder.renderDiatonicChords(cm)
        assertTrue("顺阶和弦音频不应为空", audio.isNotEmpty())
    }

    @Test
    fun `顺阶和弦音频包含7个和弦的长度`() {
        val audio = builder.renderDiatonicChords(cm)
        // 200ms 前导 + 7 × 600ms 和弦 + 400ms 尾部 = 4800ms
        val expectedDurationMs = CircleOfFifthsAudioBuilder.LEAD_SILENCE_MS +
            7 * CircleOfFifthsAudioBuilder.CHORD_DURATION_MS +
            CircleOfFifthsAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * expectedDurationMs / 1000.0).toInt()
        assertEquals(expectedSamples, audio.size)
    }

    @Test
    fun `顺阶和弦音频前导静音区为0`() {
        val audio = builder.renderDiatonicChords(cm)
        val leadSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * CircleOfFifthsAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前 leadSamples 个采样应该全部为 0（静音）
        for (i in 0 until leadSamples) {
            assertEquals("前导静音区第 $i 个采样应为0", 0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `顺阶和弦音频尾部静音区为0`() {
        val audio = builder.renderDiatonicChords(cm)
        val tailSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * CircleOfFifthsAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        // 最后 tailSamples 个采样应该全部为 0（静音）
        for (i in audio.size - tailSamples until audio.size) {
            assertEquals("尾部静音区第 $i 个采样应为0", 0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `顺阶和弦音频至少有一个非零采样（有声音）`() {
        val audio = builder.renderDiatonicChords(cm)
        val leadSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * CircleOfFifthsAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val hasSound = audio.drop(leadSamples).any { kotlin.math.abs(it) > 0.0001f }
        assertTrue("顺阶和弦音频在静音区之后应有声音", hasSound)
    }

    // ════════════════════════════════════════
    //  音阶渲染
    // ════════════════════════════════════════

    @Test
    fun `渲染音阶返回非空FloatArray`() {
        val audio = builder.renderScale(cm)
        assertTrue("音阶音频不应为空", audio.isNotEmpty())
    }

    @Test
    fun `音阶音频包含7个音符的长度`() {
        val audio = builder.renderScale(cm)
        // 200ms 前导 + 7 × 400ms 音符 + 400ms 尾部 = 3400ms
        val expectedDurationMs = CircleOfFifthsAudioBuilder.LEAD_SILENCE_MS +
            7 * CircleOfFifthsAudioBuilder.SCALE_NOTE_DURATION_MS +
            CircleOfFifthsAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * expectedDurationMs / 1000.0).toInt()
        assertEquals(expectedSamples, audio.size)
    }

    @Test
    fun `音阶音频前导静音区为0`() {
        val audio = builder.renderScale(cm)
        val leadSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * CircleOfFifthsAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        for (i in 0 until leadSamples) {
            assertEquals(0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `音阶音频至少有一个非零采样`() {
        val audio = builder.renderScale(cm)
        val leadSamples = (CircleOfFifthsAudioBuilder.SAMPLE_RATE * CircleOfFifthsAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val hasSound = audio.drop(leadSamples).any { kotlin.math.abs(it) > 0.0001f }
        assertTrue("音阶音频在静音区之后应有声音", hasSound)
    }

    // ════════════════════════════════════════
    //  软限幅验证
    // ════════════════════════════════════════

    @Test
    fun `所有采样值在 -1 到 1 范围内`() {
        val audio = builder.renderDiatonicChords(gm)
        audio.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1,1] 范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `音阶采样值在 -1 到 1 范围内`() {
        val audio = builder.renderScale(gm)
        audio.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1,1] 范围", sample in -1.0f..1.0f)
        }
    }

    // ════════════════════════════════════════
    //  时长估算
    // ════════════════════════════════════════

    @Test
    fun `顺阶和弦时长估算 = 200 + 7×600 + 400 = 4800ms`() {
        val duration = builder.estimateDiatonicChordsDurationMs(cm)
        assertEquals(4800L, duration)
    }

    @Test
    fun `音阶时长估算 = 200 + 7×400 + 400 = 3400ms`() {
        val duration = builder.estimateScaleDurationMs(cm)
        assertEquals(3400L, duration)
    }

    // ════════════════════════════════════════
    //  多调性验证
    // ════════════════════════════════════════

    @Test
    fun `不同调性的顺阶和弦音频长度相同（都是7个和弦）`() {
        val cmAudio = builder.renderDiatonicChords(cm)
        val gmAudio = builder.renderDiatonicChords(gm)
        assertEquals(cmAudio.size, gmAudio.size)
    }

    @Test
    fun `小调音阶也能正常渲染`() {
        val audio = builder.renderScale(CircleKey(9, CircleMode.MINOR))
        assertTrue(audio.isNotEmpty())
        audio.forEach { sample ->
            assertTrue(sample in -1.0f..1.0f)
        }
    }
}
