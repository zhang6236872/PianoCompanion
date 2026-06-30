package com.pianocompanion.chord

import com.pianocompanion.audio.PianoToneSynthesizer
import org.junit.Assert.*
import org.junit.Test

/**
 * 和弦音频渲染器 [ChordAudioBuilder] 单元测试。
 *
 * 验证柱式/琶音渲染的缓冲区长度、范围限制（不削波）、
 * 前导/尾部静音边界等核心功能。
 */
class ChordAudioBuilderTest {

    private val builder = ChordAudioBuilder()

    // ════════════════════════════════════════
    //  柱式和弦渲染
    // ════════════════════════════════════════

    @Test
    fun `柱式和弦渲染非空缓冲区`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val pcm = builder.renderBlocked(voicing)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `柱式和弦缓冲区包含前导静音 + 音符时值 + 尾部静音`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val pcm = builder.renderBlocked(voicing)
        val sampleRate = ChordAudioBuilder.SAMPLE_RATE

        // 前导静音 = 200ms → 前 200*44100/1000 = 8820 个样本应接近静音
        val leadSilenceSamples = (sampleRate * ChordAudioBuilder.LEAD_SILENCE_MS / 1000).toInt()
        for (i in 0 until leadSilenceSamples) {
            assertEquals("前导静音区样本 $i 应为 0", 0f, pcm[i], 1e-5f)
        }
    }

    @Test
    fun `柱式和弦缓冲区尾部有静音`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val pcm = builder.renderBlocked(voicing)
        val sampleRate = ChordAudioBuilder.SAMPLE_RATE

        // 尾部静音 = 500ms
        val tailSilenceSamples = (sampleRate * ChordAudioBuilder.TAIL_SILENCE_MS / 1000).toInt()
        // 最后 tailSilenceSamples 个样本应接近静音
        val startIdx = pcm.size - tailSilenceSamples
        for (i in startIdx until pcm.size) {
            assertTrue(
                "尾部样本 $i 应接近静音, 实际 ${pcm[i]}",
                kotlin.math.abs(pcm[i]) < 0.01f
            )
        }
    }

    @Test
    fun `柱式和弦所有样本在 -1 到 1 范围内（不削波）`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR_9)
        val pcm = builder.renderBlocked(voicing)
        for (sample in pcm) {
            assertTrue("样本值 $sample 超出范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `柱式和弦音符区域有非零内容`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val pcm = builder.renderBlocked(voicing)
        val sampleRate = ChordAudioBuilder.SAMPLE_RATE

        // 前导静音后 + 100ms 处应有非零内容
        val contentStart = (sampleRate * ChordAudioBuilder.LEAD_SILENCE_MS / 1000).toInt() + sampleRate / 10
        assertTrue("音符区域应有非零内容", kotlin.math.abs(pcm[contentStart]) > 0.01f)
    }

    // ════════════════════════════════════════
    //  琶音和弦渲染
    // ════════════════════════════════════════

    @Test
    fun `琶音和弦渲染非空缓冲区`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val pcm = builder.renderArpeggiated(voicing)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `琶音和弦比柱式和弦缓冲区更长（因琶音延迟）`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR_9) // 5 个音符
        val blockedPcm = builder.renderBlocked(voicing)
        val arpeggiatedPcm = builder.renderArpeggiated(voicing)
        assertTrue(
            "琶音缓冲区 (${arpeggiatedPcm.size}) 应长于柱式 (${blockedPcm.size})",
            arpeggiatedPcm.size > blockedPcm.size
        )
    }

    @Test
    fun `琶音和弦所有样本在 -1 到 1 范围内（不削波）`() {
        val voicing = ChordEngine.build(ChordRoot.G, ChordType.DOMINANT_7)
        val pcm = builder.renderArpeggiated(voicing)
        for (sample in pcm) {
            assertTrue("样本值 $sample 超出范围", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `琶音和弦尾部有静音`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val pcm = builder.renderArpeggiated(voicing)
        val sampleRate = ChordAudioBuilder.SAMPLE_RATE
        val tailSilenceSamples = (sampleRate * ChordAudioBuilder.TAIL_SILENCE_MS / 1000).toInt()
        val startIdx = pcm.size - tailSilenceSamples
        for (i in startIdx until pcm.size) {
            assertTrue(
                "琶音尾部样本 $i 应接近静音, 实际 ${pcm[i]}",
                kotlin.math.abs(pcm[i]) < 0.01f
            )
        }
    }

    // ════════════════════════════════════════
    //  不同和弦类型
    // ════════════════════════════════════════

    @Test
    fun `三和弦和七和弦缓冲区长度相同（柱式模式）`() {
        val triad = builder.renderBlocked(ChordEngine.build(ChordRoot.C, ChordType.MAJOR))
        val seventh = builder.renderBlocked(ChordEngine.build(ChordRoot.C, ChordType.DOMINANT_7))
        // 柱式模式：所有音符同时开始，缓冲区长度 = 前导静音 + 音符时值 + 尾部静音
        // 不同和弦的音符数不同，但柱式模式的总长度应该相同
        assertEquals(triad.size, seventh.size)
    }

    @Test
    fun `不同根音的柱式和弦缓冲区长度相同`() {
        val c = builder.renderBlocked(ChordEngine.build(ChordRoot.C, ChordType.MAJOR))
        val f = builder.renderBlocked(ChordEngine.build(ChordRoot.F, ChordType.MAJOR))
        assertEquals(c.size, f.size)
    }

    @Test
    fun `柱式和琶音单音和弦缓冲区长度相同`() {
        // 单音没有琶音延迟
        // 但和弦至少有 3 个音符，所以琶音总是更长
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val blocked = builder.renderBlocked(voicing)
        val arpeggiated = builder.renderArpeggiated(voicing)
        // 琶音比柱式多 2 个音符的延迟 (3 音符 → 2 间隔)
        val sampleRate = ChordAudioBuilder.SAMPLE_RATE
        val expectedDiff = 2 * (sampleRate * ChordAudioBuilder.ARPEGGIO_DELAY_MS / 1000).toInt()
        val actualDiff = arpeggiated.size - blocked.size
        assertEquals(expectedDiff, actualDiff)
    }

    // ════════════════════════════════════════
    //  estimateDurationMs
    // ════════════════════════════════════════

    @Test
    fun `estimateDurationMs 柱式三和弦 = 前导 + 时值 + 尾部`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val duration = builder.estimateDurationMs(voicing, arpeggiated = false)
        val expected = ChordAudioBuilder.LEAD_SILENCE_MS + ChordAudioBuilder.NOTE_DURATION_MS + ChordAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, duration)
    }

    @Test
    fun `estimateDurationMs 琶音三和弦 = 前导 + 2延迟 + 时值 + 尾部`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val duration = builder.estimateDurationMs(voicing, arpeggiated = true)
        val expected = ChordAudioBuilder.LEAD_SILENCE_MS +
            2 * ChordAudioBuilder.ARPEGGIO_DELAY_MS +
            ChordAudioBuilder.NOTE_DURATION_MS +
            ChordAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, duration)
    }

    @Test
    fun `estimateDurationMs 琶音九和弦比三和弦更长`() {
        val triadVoicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val ninthVoicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR_9)
        val triadDur = builder.estimateDurationMs(triadVoicing, arpeggiated = true)
        val ninthDur = builder.estimateDurationMs(ninthVoicing, arpeggiated = true)
        assertTrue("九和弦琶音应更长", ninthDur > triadDur)
    }

    // ════════════════════════════════════════
    //  力度影响
    // ════════════════════════════════════════

    @Test
    fun `高力度比低力度产生更大振幅`() {
        val voicing = ChordEngine.build(ChordRoot.C, ChordType.MAJOR)
        val lowVel = builder.renderBlocked(voicing, velocity = 30)
        val highVel = builder.renderBlocked(voicing, velocity = 100)

        val sampleRate = ChordAudioBuilder.SAMPLE_RATE
        val contentStart = (sampleRate * ChordAudioBuilder.LEAD_SILENCE_MS / 1000).toInt() + sampleRate / 10

        val lowMax = lowVel.sliceArray(contentStart until contentStart + 1000).maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val highMax = highVel.sliceArray(contentStart until contentStart + 1000).maxOfOrNull { kotlin.math.abs(it) } ?: 0f

        assertTrue("高力度 ($highMax) 应大于低力度 ($lowMax)", highMax > lowMax)
    }

    // ════════════════════════════════════════
    //  确定性
    // ════════════════════════════════════════

    @Test
    fun `相同参数产生相同输出`() {
        val voicing = ChordEngine.build(ChordRoot.E_FLAT, ChordType.MINOR_7)
        val pcm1 = builder.renderBlocked(voicing)
        val pcm2 = builder.renderBlocked(voicing)
        assertArrayEquals(pcm1, pcm2, 0f)
    }

    @Test
    fun `不同根音产生不同输出`() {
        val pcm1 = builder.renderBlocked(ChordEngine.build(ChordRoot.C, ChordType.MAJOR))
        val pcm2 = builder.renderBlocked(ChordEngine.build(ChordRoot.D, ChordType.MAJOR))
        // 不同根音 → 不同频率 → 不同波形
        var hasDifference = false
        for (i in pcm1.indices) {
            if (kotlin.math.abs(pcm1[i] - pcm2[i]) > 0.001f) {
                hasDifference = true
                break
            }
        }
        assertTrue("不同根音应产生不同输出", hasDifference)
    }
}
