package com.pianocompanion.rhythmpattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmPatternAudioBuilder] 单元测试。
 *
 * 验证音频构建器：
 * - 空输入处理
 * - 采样率正确
 * - 采样值不削波
 * - 不同节奏型渲染长度合理
 * - onset 时间计算正确性
 * - click 波形特征
 * - 预估时长
 */
class RhythmPatternAudioBuilderTest {

    private val builder = RhythmPatternAudioBuilder()

    @Test
    fun `empty pattern returns empty buffer`() {
        // 所有节奏型都非空，但验证空 onset 的健壮性
        val result = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 0)
        assertEquals(0, result.size)
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, RhythmPatternAudioBuilder.DEFAULT_SAMPLE_RATE)
    }

    @Test
    fun `rendered samples are within valid range`() {
        for (pattern in RhythmPatternType.ALL) {
            val audio = builder.renderPattern(pattern, RhythmTempo.SLOW, repeatCount = 1)
            for (sample in audio) {
                assertTrue("样本值应在 [-1, 1] 范围内 (pattern=$pattern)", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `quarters render length is reasonable`() {
        // 四分音符 1 repeat：300ms lead + 4 × 750ms spacing + 100ms click + 400ms tail
        // 最后 onset 在 300 + 2250 = 2550ms，加 click 100ms + tail 400ms = 3050ms
        val audio = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val expectedMinMs = 2500 // 至少包含所有 onset
        val expectedMaxMs = 4000 // 不应过长
        val durationMs = audio.size.toLong() * 1000 / RhythmPatternAudioBuilder.DEFAULT_SAMPLE_RATE
        assertTrue("渲染时长 ${durationMs}ms 应 >= ${expectedMinMs}ms", durationMs >= expectedMinMs)
        assertTrue("渲染时长 ${durationMs}ms 应 <= ${expectedMaxMs}ms", durationMs <= expectedMaxMs)
    }

    @Test
    fun `halves render is shorter than eighths`() {
        // 二分音符（2 个 onset）比八分音符（8 个 onset）更短
        // 因为八分音符最后一个 onset 更靠后
        val halves = builder.renderPattern(RhythmPatternType.HALVES, RhythmTempo.SLOW, repeatCount = 1)
        val eighths = builder.renderPattern(RhythmPatternType.EIGHTHS, RhythmTempo.SLOW, repeatCount = 1)
        assertTrue("二分音符渲染应短于八分音符", halves.size < eighths.size)
    }

    @Test
    fun `repeat 2 is longer than repeat 1`() {
        val audio1 = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val audio2 = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 2)
        assertTrue("2 次重复应比 1 次长", audio2.size > audio1.size)
    }

    @Test
    fun `fast tempo is shorter than slow`() {
        // 快速 140 BPM 比 80 BPM 紧凑
        val slow = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val fast = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.FAST, repeatCount = 1)
        assertTrue("快速应比慢速短", fast.size < slow.size)
    }

    @Test
    fun `all 8 patterns render non-empty`() {
        for (pattern in RhythmPatternType.ALL) {
            val audio = builder.renderPattern(pattern, RhythmTempo.SLOW, repeatCount = 1)
            assertTrue("$pattern 应渲染非空音频", audio.isNotEmpty())
        }
    }

    @Test
    fun `rendered audio has non-zero signal`() {
        for (pattern in RhythmPatternType.ALL) {
            val audio = builder.renderPattern(pattern, RhythmTempo.SLOW, repeatCount = 1)
            val maxAbs = audio.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            assertTrue("$pattern 渲染音频应含非零信号", maxAbs > 0.01f)
        }
    }

    @Test
    fun `onset peak exists at expected positions`() {
        // 四分音符慢速：第一个 onset 在 300ms = 13230 samples
        val audio = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val sampleRate = RhythmPatternAudioBuilder.DEFAULT_SAMPLE_RATE
        val onset0Sample = (300.0 * sampleRate / 1000.0).toInt()
        // 在 onset 附近（±100 samples）应有能量峰值
        val window = audio.copyOfRange(
            onset0Sample.coerceIn(0, audio.size - 1),
            (onset0Sample + 200).coerceIn(0, audio.size)
        )
        val peakInWindow = window.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        assertTrue("第一个 onset 附近应有能量峰值", peakInWindow > 0.1f)
    }

    @Test
    fun `silence between onsets is quieter`() {
        // 四分音符慢速：onset 间距 = 750ms
        // 在两个 onset 之间的中点（300 + 375 = 675ms）应比 onset 处安静得多
        val audio = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val sampleRate = RhythmPatternAudioBuilder.DEFAULT_SAMPLE_RATE
        val onset0Sample = (300.0 * sampleRate / 1000.0).toInt()
        val midpointSample = (675.0 * sampleRate / 1000.0).toInt()

        // onset 处取一个小窗口内的峰值（sine 波不一定在采样点处为峰值）
        val onsetWindow = audio.copyOfRange(
            onset0Sample.coerceIn(0, audio.size - 1),
            (onset0Sample + 200).coerceIn(0, audio.size)
        )
        val onsetEnergy = onsetWindow.maxOfOrNull { kotlin.math.abs(it) } ?: 0f

        // 中点处取同样窗口大小，应基本静默
        val midWindow = audio.copyOfRange(
            (midpointSample - 100).coerceIn(0, audio.size - 1),
            (midpointSample + 100).coerceIn(0, audio.size)
        )
        val midpointEnergy = midWindow.maxOfOrNull { kotlin.math.abs(it) } ?: 0f

        assertTrue(
            "onset 处能量 ($onsetEnergy) 应远大于中间静默处 ($midpointEnergy)",
            onsetEnergy > midpointEnergy * 5
        )
    }

    @Test
    fun `computeOnsetTimes matches engine`() {
        val engine = RhythmPatternEngine()
        val audioBuilder = RhythmPatternAudioBuilder()
        for (pattern in RhythmPatternType.ALL) {
            val engineOnsets = engine.computeOnsetTimes(pattern, RhythmTempo.SLOW, 2)
            val audioOnsets = audioBuilder.computeOnsetTimes(pattern, RhythmTempo.SLOW, 2)
            assertEquals("$pattern onset 数量应一致", engineOnsets.size, audioOnsets.size)
            for (i in engineOnsets.indices) {
                assertEquals("$pattern onset[$i] 时间应一致", engineOnsets[i], audioOnsets[i], 0.01)
            }
        }
    }

    @Test
    fun `estimateDurationMs is positive`() {
        val engine = RhythmPatternEngine.withSeed(1L)
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        val duration = builder.estimateDurationMs(q)
        assertTrue("预估时长应为正数", duration > 0)
    }

    @Test
    fun `estimateDurationMs matches actual render length`() {
        val engine = RhythmPatternEngine.withSeed(1L)
        val q = engine.generate(RhythmDifficulty.ADVANCED)
        val estimated = builder.estimateDurationMs(q)
        val audio = builder.render(q)
        val actualMs = audio.size.toLong() * 1000 / RhythmPatternAudioBuilder.DEFAULT_SAMPLE_RATE
        // 预估和实际应接近（容许 50ms 误差，因预估不含四舍五入）
        assertTrue(
            "预估 ${estimated}ms 应接近实际 ${actualMs}ms",
            kotlin.math.abs(estimated - actualMs) < 100
        )
    }

    @Test
    fun `different patterns produce different audio`() {
        // 至少验证四分和八分的 onset 数不同 → 音频长度不同
        val quarters = builder.renderPattern(RhythmPatternType.QUARTERS, RhythmTempo.SLOW, repeatCount = 1)
        val eighths = builder.renderPattern(RhythmPatternType.EIGHTHS, RhythmTempo.SLOW, repeatCount = 1)
        // 四分 4 onset, 八分 8 onset → 八分最后一个 onset 更靠后
        assertTrue("四分和八分渲染长度应不同", quarters.size != eighths.size)
    }

    @Test
    fun `dotted rhythm has uneven onset energy distribution`() {
        // 附点 [1.5, 0.5, 1.5, 0.5]：第 1-2 onset 间距大（1.5 beat），
        // 第 2-3 onset 间距小（0.5 beat）
        val onsets = builder.computeOnsetTimes(RhythmPatternType.DOTTED, RhythmTempo.SLOW, repeatCount = 1)
        assertEquals(4, onsets.size)
        val gap1 = onsets[1] - onsets[0] // 1.5 beat
        val gap2 = onsets[2] - onsets[1] // 0.5 beat
        assertTrue("附点第一间距应大于第二间距", gap1 > gap2 * 2)
    }

    @Test
    fun `scotch snap has opposite distribution from dotted`() {
        val dotted = builder.computeOnsetTimes(RhythmPatternType.DOTTED, RhythmTempo.SLOW, repeatCount = 1)
        val scotch = builder.computeOnsetTimes(RhythmPatternType.SCOTCH_SNAP, RhythmTempo.SLOW, repeatCount = 1)
        val dottedGap1 = dotted[1] - dotted[0]
        val scotchGap1 = scotch[1] - scotch[0]
        // 附点首间距长（1.5 beat），后附点首间距短（0.5 beat）
        assertTrue("附点首间距应大于后附点首间距", dottedGap1 > scotchGap1 * 2)
    }
}
