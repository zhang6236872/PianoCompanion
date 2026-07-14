package com.pianocompanion.texturerecognitiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 织体辨识训练音频构建器单元测试。
 *
 * 验证 PCM 缓冲区有效性、采样范围、织体区分度。
 */
class TextureAudioBuilderTest {

    private val builder = TextureAudioBuilder(sampleRate = 44100)

    // ── 基础有效性 ──────────────────────────────────────────

    @Test
    fun `所有织体类型产生非空 PCM 缓冲区`() {
        for (texture in TextureType.ALL) {
            val events = builder.buildEvents(texture)
            val pcm = builder.renderEvents(events)
            assertTrue(
                "${texture.displayName} 的 PCM 缓冲区不应为空",
                pcm.isNotEmpty()
            )
        }
    }

    @Test
    fun `所有 PCM 采样在 -1 到 1 范围内`() {
        for (texture in TextureType.ALL) {
            val pcm = builder.renderEvents(builder.buildEvents(texture))
            for ((i, sample) in pcm.withIndex()) {
                assertTrue(
                    "${texture.displayName} 采样 #$i 超出范围: $sample",
                    sample in -1.0f..1.0f
                )
            }
        }
    }

    @Test
    fun `PCM 缓冲区有有效音频内容（非全零）`() {
        for (texture in TextureType.ALL) {
            val pcm = builder.renderEvents(builder.buildEvents(texture))
            val nonZero = pcm.count { kotlin.math.abs(it) > 0.001f }
            assertTrue(
                "${texture.displayName} 的 PCM 不应全为零（非零采样数=$nonZero/${pcm.size}）",
                nonZero > pcm.size * 0.1
            )
        }
    }

    // ── 事件构建验证 ──────────────────────────────────────────

    @Test
    fun `单声部只有旋律音符，无重叠`() {
        val events = builder.buildEvents(TextureType.MONOPHONIC)
        // C4-E4-G4-C5 = 4 个音符
        assertEquals(4, events.size)
        // 所有音符依次排列，无重叠
        for (i in 1 until events.size) {
            assertTrue(
                "单声部音符应依次排列",
                events[i].onsetMs >= events[i - 1].onsetMs + events[i - 1].durationMs - 1.0
            )
        }
        // MIDI 音高应该是 C4=60, E4=64, G4=67, C5=72
        val pitches = events.map { it.midi }
        assertEquals(listOf(60, 64, 67, 72), pitches)
    }

    @Test
    fun `柱式和弦有旋律音和和弦伴奏`() {
        val events = builder.buildEvents(TextureType.HOMOPHONIC_CHORDAL)
        // 4 拍，每拍 1 旋律音 + 3 和弦音 = 4 * (1+3) = 16 个事件
        assertEquals(16, events.size)
        // 第一拍应有 4 个同时开始的音符（1 旋律 + 3 和弦）
        val firstBeat = events.filter { it.onsetMs == 0.0 }
        assertEquals(4, firstBeat.size)
        // 包含旋律音 C4=60 和和弦 C3=48, E3=52, G3=55
        val firstPitches = firstBeat.map { it.midi }.sorted()
        assertEquals(listOf(48, 52, 55, 60), firstPitches)
    }

    @Test
    fun `分解和弦有旋律音和琶音伴奏`() {
        val events = builder.buildEvents(TextureType.HOMOPHONIC_ARPEGGIATED)
        // 4 拍，每拍 1 旋律音 + 2 琶音 = 4 * (1+2) = 12 个事件
        assertEquals(12, events.size)
        // 旋律音增益应高于伴奏
        val melodyNotes = events.filter { it.gain >= 1.0f }
        val accompNotes = events.filter { it.gain < 1.0f }
        assertEquals(4, melodyNotes.size)
        assertEquals(8, accompNotes.size)
    }

    @Test
    fun `复调有两条独立声部，不同节奏`() {
        val events = builder.buildEvents(TextureType.POLYPHONIC)
        // 上方 3 个四分音符 + 下方 2 个附点四分音符 = 5 个事件
        assertEquals(5, events.size)
        // 上方声部音符在 onset 0, 500, 1000
        val upperNotes = events.filter { it.midi >= 60 } // C4 以上
        assertEquals(3, upperNotes.size)
        // 下方声部音符在 onset 0, 750
        val lowerNotes = events.filter { it.midi < 60 }
        assertEquals(2, lowerNotes.size)
    }

    @Test
    fun `支声有两个声部，第二声部有额外经过音`() {
        val events = builder.buildEvents(TextureType.HETEROPHONIC)
        // 声部 1: 4 个旋律音 + 声部 2: 6 个音（含 2 个经过音） = 10 个事件
        assertEquals(10, events.size)
        // 第二声部增益应低于主声部
        val voice2 = events.filter { it.gain < 1.0f }
        assertTrue("支声第二声部应存在", voice2.isNotEmpty())
        // 应包含经过音 D4=62 和 B4=71
        val pitches = events.map { it.midi }
        assertTrue("应包含经过音 D4=62", 62 in pitches)
        assertTrue("应包含经过音 B4=71", 71 in pitches)
    }

    // ── MIDI 转频率 ──────────────────────────────────────────

    @Test
    fun `midiToFreq A4 等于 440Hz`() {
        val freq = builder.midiToFreq(69)
        assertEquals(440.0, freq, 0.1)
    }

    @Test
    fun `midiToFreq C4 约等于 261_63Hz`() {
        val freq = builder.midiToFreq(60)
        assertEquals(261.63, freq, 0.1)
    }

    @Test
    fun `midiToFreq 高八度频率翻倍`() {
        val freq1 = builder.midiToFreq(60) // C4
        val freq2 = builder.midiToFreq(72) // C5
        assertEquals(2.0, freq2 / freq1, 0.001)
    }

    // ── 织体区分度 ──────────────────────────────────────────

    @Test
    fun `不同织体产生不同的波形`() {
        val mono = builder.renderEvents(builder.buildEvents(TextureType.MONOPHONIC))
        val chordal = builder.renderEvents(builder.buildEvents(TextureType.HOMOPHONIC_CHORDAL))
        val polyphonic = builder.renderEvents(builder.buildEvents(TextureType.POLYPHONIC))

        // 多声部织体的波形与单声部应有显著差异
        val diff1 = waveformDifference(mono, chordal)
        val diff2 = waveformDifference(mono, polyphonic)
        assertTrue(
            "柱式和弦与单声部波形应有显著差异 (diff=$diff1)",
            diff1 > 0.01
        )
        assertTrue(
            "复调与单声部波形应有显著差异 (diff=$diff2)",
            diff2 > 0.01
        )
    }

    @Test
    fun `多声部织体同时刻事件数多于单声部`() {
        // 结构性验证：多声部织体在任意时刻有更多同时响的音符
        val monoEvents = builder.buildEvents(TextureType.MONOPHONIC)
        val chordalEvents = builder.buildEvents(TextureType.HOMOPHONIC_CHORDAL)

        // 在 onset=0 处，单声部只有 1 个音符，柱式和弦有 4 个（旋律 + 3 和弦）
        val monoAt0 = monoEvents.count { it.onsetMs == 0.0 }
        val chordalAt0 = chordalEvents.count { it.onsetMs == 0.0 }
        assertEquals(1, monoAt0)
        assertEquals(4, chordalAt0)
        assertTrue("柱式和弦同时刻音符数应多于单声部", chordalAt0 > monoAt0)
    }

    @Test
    fun `柱式和弦与分解和弦能量不同`() {
        val chordal = builder.renderEvents(builder.buildEvents(TextureType.HOMOPHONIC_CHORDAL))
        val arpeggiated = builder.renderEvents(builder.buildEvents(TextureType.HOMOPHONIC_ARPEGGIATED))

        // 它们的波形应该不同（时间结构不同）
        val diff = waveformDifference(chordal, arpeggiated)
        assertTrue("柱式和弦与分解和弦波形应有显著差异", diff > 0.01)
    }

    @Test
    fun `复调与单声部波形不同`() {
        val mono = builder.renderEvents(builder.buildEvents(TextureType.MONOPHONIC))
        val polyphonic = builder.renderEvents(builder.buildEvents(TextureType.POLYPHONIC))

        // 复调有同时响的多个音，能量分布不同
        val diff = waveformDifference(mono, polyphonic)
        assertTrue("复调与单声部波形应有显著差异", diff > 0.01)
    }

    @Test
    fun `支声与单声部波形不同`() {
        val mono = builder.renderEvents(builder.buildEvents(TextureType.MONOPHONIC))
        val hetero = builder.renderEvents(builder.buildEvents(TextureType.HETEROPHONIC))

        // 支声有额外的装饰音
        val diff = waveformDifference(mono, hetero)
        assertTrue("支声与单声部波形应有差异", diff > 0.01)
    }

    // ── 前导/尾部静音 ──────────────────────────────────────────

    @Test
    fun `PCM 开头有前导静音`() {
        val pcm = builder.renderEvents(builder.buildEvents(TextureType.MONOPHONIC))
        val leadSamples = (44100 * TextureAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前 leadSamples 中应有大量零
        val silenceCount = pcm.take(leadSamples).count { kotlin.math.abs(it) < 0.001f }
        assertTrue(
            "前导静音区域应有大量零采样",
            silenceCount > leadSamples * 0.9
        )
    }

    @Test
    fun `PCM 结尾有尾部静音`() {
        val pcm = builder.renderEvents(builder.buildEvents(TextureType.MONOPHONIC))
        val tailSamples = (44100 * TextureAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val tail = pcm.takeLast(tailSamples)
        val silenceCount = tail.count { kotlin.math.abs(it) < 0.001f }
        assertTrue(
            "尾部静音区域应有大量零采样",
            silenceCount > tailSamples * 0.9
        )
    }

    // ── estimateDurationMs ──────────────────────────────────────────

    @Test
    fun `estimateDurationMs 为正值`() {
        for (texture in TextureType.ALL) {
            val duration = builder.estimateDurationMs(texture)
            assertTrue(
                "${texture.displayName} 预估时长应 > 0",
                duration > 0
            )
        }
    }

    @Test
    fun `estimateDurationMs 包含前导和尾部静音`() {
        val duration = builder.estimateDurationMs(TextureType.MONOPHONIC)
        // 单声部 4 个 500ms 音符 = 2000ms 音乐 + 400ms 前导 + 300ms 尾部 = 2700ms
        assertEquals(2700L, duration)
    }

    // ── 渲染一致性 ──────────────────────────────────────────

    @Test
    fun `相同织体多次渲染结果一致`() {
        val events = builder.buildEvents(TextureType.POLYPHONIC)
        val pcm1 = builder.renderEvents(events)
        val pcm2 = builder.renderEvents(events)
        assertArrayEquals(pcm1, pcm2, 0.0001f)
    }

    @Test
    fun `空事件列表返回空缓冲区`() {
        val pcm = builder.renderEvents(emptyList())
        assertEquals(0, pcm.size)
    }

    // ── 辅助函数 ──────────────────────────────────────────

    private fun rms(pcm: FloatArray): Double {
        if (pcm.isEmpty()) return 0.0
        val sumSquares = pcm.sumOf { (it.toDouble() * it).toLong() }
        return kotlin.math.sqrt(sumSquares.toDouble() / pcm.size)
    }

    private fun waveformDifference(a: FloatArray, b: FloatArray): Double {
        val minLen = minOf(a.size, b.size)
        if (minLen == 0) return 1.0
        var sumDiff = 0.0
        for (i in 0 until minLen) {
            sumDiff += kotlin.math.abs(a[i] - b[i])
        }
        return sumDiff / minLen
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals("数组长度", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("采样 #$i", expected[i], actual[i], delta)
        }
    }
}
