package com.pianocompanion.rhythm

import org.junit.Assert.*
import org.junit.Test

/**
 * [RhythmAudioBuilder] 节奏型音频构建器单元测试。
 *
 * 覆盖：
 * - 渲染缓冲区长度正确（含预备拍 + 节奏型 + 尾部衰减）
 * - 预备拍嗒声存在
 * - 音符在正确时间位置发声
 * - 休止符处静音
 * - 软限幅防削波
 * - renderClick 嗒声特征
 */
class RhythmAudioBuilderTest {

    private val builder = RhythmAudioBuilder()
    private val sampleRate = RhythmAudioBuilder.SAMPLE_RATE

    private fun makePattern(
        events: List<RhythmEvent>,
        tempoBpm: Int = 120
    ): RhythmPattern = RhythmPattern(events = events, tempoBpm = tempoBpm)

    private fun msToSamples(ms: Long): Int = (sampleRate * ms / 1000.0).toInt()

    // ── 缓冲区长度 ────────────────────────────────────────

    @Test
    fun `缓冲区长度等于预备拍加节奏型加尾部衰减`() {
        val pattern = makePattern(
            List(4) { RhythmEvent(RhythmDuration.QUARTER) },
            tempoBpm = 120
        )
        // msPerBeat = 500ms, countOff = 4*500 = 2000ms, pattern = 2000ms, tail = 200ms
        // total = 4200ms
        val audio = builder.render(pattern, countOffBeats = 4)
        val expectedMs = 4 * 500L + 2000L + 200L
        val expectedSamples = msToSamples(expectedMs)
        assertEquals(expectedSamples, audio.size)
    }

    @Test
    fun `无预备拍时缓冲区更短`() {
        val pattern = makePattern(
            listOf(RhythmEvent(RhythmDuration.QUARTER)),
            tempoBpm = 120
        )
        val withCount = builder.render(pattern, countOffBeats = 4)
        val noCount = builder.render(pattern, countOffBeats = 0)
        assertTrue(noCount.size < withCount.size)
    }

    @Test
    fun `不同预备拍数影响长度`() {
        val pattern = makePattern(
            listOf(RhythmEvent(RhythmDuration.QUARTER)),
            tempoBpm = 120
        )
        val twoBeats = builder.render(pattern, countOffBeats = 2)
        val fourBeats = builder.render(pattern, countOffBeats = 4)
        // 4 拍比 2 拍多 2*500ms = 1000ms 的采样
        val diff = fourBeats.size - twoBeats.size
        val expectedDiff = msToSamples(1000)
        assertEquals(expectedDiff.toDouble(), diff.toDouble(), sampleRate * 0.01) // 1% 容差
    }

    // ── 预备拍嗒声 ────────────────────────────────────────

    @Test
    fun `预备拍区域有嗒声信号`() {
        val pattern = makePattern(
            listOf(RhythmEvent(RhythmDuration.HALF)),
            tempoBpm = 120
        )
        val audio = builder.render(pattern, countOffBeats = 4)
        // 第一个预备拍在 0ms 处
        val clickSamples = msToSamples(30) // 嗒声持续 30ms
        var hasSignal = false
        for (i in 0 until clickSamples) {
            if (kotlin.math.abs(audio[i]) > 0.01f) {
                hasSignal = true
                break
            }
        }
        assertTrue("预备拍区域应有嗒声信号", hasSignal)
    }

    @Test
    fun `预备拍间隙有静音`() {
        val pattern = makePattern(
            listOf(RhythmEvent(RhythmDuration.HALF)),
            tempoBpm = 120
        )
        val audio = builder.render(pattern, countOffBeats = 4)
        // 在 30ms~450ms 之间应该接近静音（嗒声30ms后到第二拍500ms前）
        val start = msToSamples(100)
        val end = msToSamples(400)
        var maxAmp = 0.0f
        for (i in start until end) {
            if (i < audio.size) {
                maxAmp = maxOf(maxAmp, kotlin.math.abs(audio[i]))
            }
        }
        assertTrue("嗒声间隙应接近静音, max=$maxAmp", maxAmp < 0.05f)
    }

    // ── 音符发声 ──────────────────────────────────────────

    @Test
    fun `音符在正确时间位置发声`() {
        val pattern = makePattern(
            listOf(RhythmEvent(RhythmDuration.QUARTER)),
            tempoBpm = 120
        )
        val audio = builder.render(pattern, countOffBeats = 4)
        // 音符在 2000ms（预备拍结束）处开始
        val noteStart = msToSamples(2000)
        var hasSignal = false
        for (i in noteStart until minOf(noteStart + msToSamples(100), audio.size)) {
            if (kotlin.math.abs(audio[i]) > 0.01f) {
                hasSignal = true
                break
            }
        }
        assertTrue("音符应在预备拍后发声", hasSignal)
    }

    @Test
    fun `休止符处保持静音`() {
        val pattern = makePattern(
            listOf(
                RhythmEvent(RhythmDuration.QUARTER),       // 0ms (offset by countOff)
                RhythmEvent(RhythmDuration.QUARTER_REST),  // 500ms → 静音
                RhythmEvent(RhythmDuration.QUARTER)        // 1000ms
            ),
            tempoBpm = 120
        )
        val audio = builder.render(pattern, countOffBeats = 4)
        // 休止符在 countOff(2000ms) + 500ms = 2500ms 处
        val restCenter = msToSamples(2650)
        var maxAmp = 0.0f
        for (i in restCenter until minOf(restCenter + msToSamples(200), audio.size)) {
            maxAmp = maxOf(maxAmp, kotlin.math.abs(audio[i]))
        }
        // 休止期间可能有前一个音符的衰减残余，但应该很弱
        assertTrue("休止符处应基本静音, max=$maxAmp", maxAmp < 0.15f)
    }

    // ── 软限幅 ────────────────────────────────────────────

    @Test
    fun `输出值在负1到1范围内`() {
        // 多个同时发声的和弦（超出单声道范围）
        val pattern = makePattern(
            List(4) { RhythmEvent(RhythmDuration.QUARTER, midiNote = 60 + it) },
            tempoBpm = 120
        )
        val audio = builder.render(pattern, countOffBeats = 2)
        for (i in audio.indices) {
            assertTrue("sample $i = ${audio[i]} 超出 [-1,1]", audio[i] >= -1.0f)
            assertTrue("sample $i = ${audio[i]} 超出 [-1,1]", audio[i] <= 1.0f)
        }
    }

    @Test
    fun `多次渲染输出一致`() {
        val pattern = makePattern(
            listOf(
                RhythmEvent(RhythmDuration.QUARTER, 60),
                RhythmEvent(RhythmDuration.EIGHTH, 64),
                RhythmEvent(RhythmDuration.QUARTER, 67)
            ),
            tempoBpm = 90
        )
        val audio1 = builder.render(pattern)
        val audio2 = builder.render(pattern)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    // ── renderClick ───────────────────────────────────────

    @Test
    fun `renderClick长度正确`() {
        val click = builder.renderClick(30L)
        assertEquals(msToSamples(30), click.size)
    }

    @Test
    fun `renderClick强拍比普通拍音量大`() {
        val accent = builder.renderClick(30L, isAccent = true)
        val normal = builder.renderClick(30L, isAccent = false)
        val accentMax = accent.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val normalMax = normal.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        assertTrue("强拍音量 ${accentMax} 应大于普通拍 ${normalMax}", accentMax > normalMax)
    }

    @Test
    fun `renderClick值在范围内`() {
        val click = builder.renderClick(50L, isAccent = true)
        for (v in click) {
            assertTrue(v >= -1.0f && v <= 1.0f)
        }
    }

    @Test
    fun `renderClick指数衰减`() {
        val click = builder.renderClick(100L)
        // 前 1/4 的最大振幅应大于后 1/4
        val quarterLen = click.size / 4
        val earlyMax = click.take(quarterLen).maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val lateMax = click.drop(3 * quarterLen).maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        assertTrue("前期振幅 $earlyMax 应大于后期 $lateMax", earlyMax > lateMax)
    }

    // ── 边界情况 ──────────────────────────────────────────

    @Test
    fun `空pattern仍生成有效缓冲区（仅预备拍和尾部）`() {
        val pattern = makePattern(emptyList(), tempoBpm = 120)
        val audio = builder.render(pattern, countOffBeats = 2)
        // 至少有预备拍和尾部衰减
        assertTrue(audio.size > 0)
        // 所有值在范围内
        for (v in audio) {
            assertTrue(v >= -1.0f && v <= 1.0f)
        }
    }

    @Test
    fun `十六分音符pattern可渲染`() {
        val pattern = makePattern(
            List(16) { RhythmEvent(RhythmDuration.SIXTEENTH, 60 + it % 12) },
            tempoBpm = 120
        )
        val audio = builder.render(pattern, countOffBeats = 1)
        assertTrue(audio.size > 0)
        for (v in audio) {
            assertTrue(v >= -1.0f && v <= 1.0f)
        }
    }

    @Test
    fun `不同MIDI音符产生不同波形`() {
        val pattern1 = makePattern(
            listOf(RhythmEvent(RhythmDuration.QUARTER, 60)),
            tempoBpm = 120
        )
        val pattern2 = makePattern(
            listOf(RhythmEvent(RhythmDuration.QUARTER, 72)),
            tempoBpm = 120
        )
        val audio1 = builder.render(pattern1, countOffBeats = 0)
        val audio2 = builder.render(pattern2, countOffBeats = 0)
        // 长度相同但内容不同（不同频率）
        assertEquals(audio1.size, audio2.size)
        var hasDiff = false
        for (i in audio1.indices) {
            if (kotlin.math.abs(audio1[i] - audio2[i]) > 0.001f) {
                hasDiff = true
                break
            }
        }
        assertTrue("不同音高应产生不同波形", hasDiff)
    }

    @Test
    fun `常量值正确`() {
        assertEquals(44100, RhythmAudioBuilder.SAMPLE_RATE)
        assertEquals(75, RhythmAudioBuilder.DEFAULT_VELOCITY)
        assertEquals(30L, RhythmAudioBuilder.CLICK_DURATION_MS)
        assertTrue(RhythmAudioBuilder.NORMAL_CLICK_AMP < RhythmAudioBuilder.ACCENT_CLICK_AMP)
        assertTrue(RhythmAudioBuilder.NORMAL_CLICK_FREQ < RhythmAudioBuilder.ACCENT_CLICK_FREQ)
    }
}
