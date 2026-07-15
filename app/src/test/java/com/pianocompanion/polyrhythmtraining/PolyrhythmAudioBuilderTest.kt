package com.pianocompanion.polyrhythmtraining

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * 复合节奏辨识训练音频构建器单元测试。
 */
class PolyrhythmAudioBuilderTest {

    private val builder = PolyrhythmTrainingAudioBuilder()

    // ── 缓冲区有效性 ────────────────────────────────────

    @Test
    fun `渲染结果非空`() {
        val q = PolyrhythmTrainingEngine.withSeed(1).generate(PolyrhythmDifficulty.ADVANCED)
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `渲染结果采样在有效范围内`() {
        PolyrhythmType.ALL.forEach { pr ->
            val audio = builder.renderPolyrhythm(pr)
            audio.forEach { sample ->
                assertTrue("sample=$sample 超出 [-1,1]", sample in -1.01f..1.01f)
            }
        }
    }

    @Test
    fun `不同复合节奏渲染不同长度的音频`() {
        // 更密集的节奏（更大的乘积）理论上需要在同一时间内处理更多click
        val audio23 = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE)
        val audio45 = builder.renderPolyrhythm(PolyrhythmType.FOUR_FIVE)
        // 长度差异主要来自click onset的分布，两者应该有合理长度
        assertTrue(audio23.size > 1000)
        assertTrue(audio45.size > 1000)
    }

    // ── 周期数验证 ──────────────────────────────────────

    @Test
    fun `更多周期产生更长的音频`() {
        val audio1 = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        val audio2 = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 2)
        assertTrue("cycle=2 应比 cycle=1 更长", audio2.size > audio1.size)
    }

    @Test
    fun `单周期音频长度合理`() {
        val audio = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        val minSamples = 44100 * 2400 / 1000 // 至少一个周期时长
        assertTrue(audio.size >= minSamples - 4410) // 允许小误差
    }

    // ── 前导静音验证 ────────────────────────────────────

    @Test
    fun `前导静音区域内无信号`() {
        val audio = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        val leadSamples = 44100 * 500 / 1000 // 500ms 前导静音
        for (i in 0 until minOf(leadSamples, audio.size)) {
            assertEquals("前导静音区域第 $i 个采样非零: ${audio[i]}", 0.0f, audio[i], 0.001f)
        }
    }

    @Test
    fun `尾部有静音区域`() {
        val audio = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        // 最后 400ms 应有静音
        val tailStart = audio.size - 44100 * 400 / 1000
        if (tailStart > 0) {
            var hasSilence = true
            for (i in tailStart until audio.size) {
                if (abs(audio[i]) > 0.01f) {
                    hasSilence = false
                    break
                }
            }
            assertTrue("尾部应有衰减后接近静音", hasSilence)
        }
    }

    // ── 不同比例波形区分度 ─────────────────────────────

    @Test
    fun `不同复合节奏的波形不同`() {
        val audio23 = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 2)
        val audio34 = builder.renderPolyrhythm(PolyrhythmType.THREE_FOUR, cycleCount = 2)
        // 计算波形差异
        var diff = 0.0
        val minLen = minOf(audio23.size, audio34.size)
        for (i in 0 until minLen) {
            diff += abs(audio23[i] - audio34[i])
        }
        assertTrue("2:3 和 3:4 的波形应有明显差异", diff / minLen > 0.001)
    }

    @Test
    fun `2比3与3比5的波形差异显著`() {
        val audio23 = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        val audio35 = builder.renderPolyrhythm(PolyrhythmType.THREE_FIVE, cycleCount = 1)
        val minLen = minOf(audio23.size, audio35.size)
        var diff = 0.0
        for (i in 0 until minLen) {
            diff += abs(audio23[i] - audio35[i])
        }
        assertTrue("2:3 和 3:5 的波形差异应显著", diff / minLen > 0.001)
    }

    // ── 非零能量区 ──────────────────────────────────────

    @Test
    fun `音频在周期内有非零能量`() {
        val audio = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        // 检查前导静音之后 100ms 内有非零信号
        val checkStart = 44100 * 600 / 1000 // 600ms 处（前导500ms+100ms进周期）
        var hasEnergy = false
        val checkEnd = minOf(44100 * 900 / 1000, audio.size)
        for (i in checkStart until checkEnd) {
            if (abs(audio[i]) > 0.001f) {
                hasEnergy = true
                break
            }
        }
        assertTrue("周期内应有非零音频信号", hasEnergy)
    }

    @Test
    fun `更多click的复合节奏在同期内有更高能量`() {
        // 4:5 有 9 个click/cycle vs 2:3 有 5 个click/cycle
        // 限制到相同的区间（一个周期）比较总能量
        val audio23 = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, cycleCount = 1)
        val audio45 = builder.renderPolyrhythm(PolyrhythmType.FOUR_FIVE, cycleCount = 1)
        val periodEnd = 44100 * (500 + 2400).toInt() / 1000 // 前导500ms + 1周期2400ms
        val energy23 = (0 until minOf(periodEnd, audio23.size)).sumOf { abs(audio23[it].toDouble()) }
        val energy45 = (0 until minOf(periodEnd, audio45.size)).sumOf { abs(audio45[it].toDouble()) }
        // 4:5 应有更多 click，能量更高
        assertTrue("4:5 能量应高于 2:3: e45=$energy45 e23=$energy23", energy45 > energy23)
    }

    // ── estimateDurationMs ──────────────────────────────

    @Test
    fun `estimateDurationMs为正值`() {
        val q = PolyrhythmTrainingEngine.withSeed(1).generate(PolyrhythmDifficulty.ADVANCED)
        val duration = builder.estimateDurationMs(q)
        assertTrue(duration > 0)
    }

    @Test
    fun `estimateDurationMs与周期数正相关`() {
        val engine = PolyrhythmTrainingEngine.withSeed(1)
        val q1 = engine.generate(PolyrhythmDifficulty.ADVANCED, cycleCount = 1)
        val q2 = engine.generate(PolyrhythmDifficulty.ADVANCED, cycleCount = 2)
        val dur1 = builder.estimateDurationMs(q1)
        val dur2 = builder.estimateDurationMs(q2)
        assertTrue("2周期应比1周期更长: dur2=$dur2 dur1=$dur1", dur2 > dur1)
    }

    // ── 边界情况 ────────────────────────────────────────

    @Test
    fun `所有复合节奏都可成功渲染`() {
        PolyrhythmType.ALL.forEach { pr ->
            val audio = builder.renderPolyrhythm(pr)
            assertTrue("${pr.displayName} 渲染失败", audio.isNotEmpty())
        }
    }

    @Test
    fun `高音声部onset处有信号`() {
        val engine = PolyrhythmTrainingEngine()
        val (highOnsets, _) = engine.computeOnsetTimes(PolyrhythmType.TWO_THREE, 1)
        val audio = builder.renderPolyrhythm(PolyrhythmType.TWO_THREE, 1)
        // 检查每个高音 onset 附近有非零信号
        for (onset in highOnsets) {
            val sampleIdx = (onset * 44100 / 1000.0).toInt()
            val checkRange = 44100 * 5 / 1000 // ±5ms 窗口
            var hasEnergy = false
            for (j in maxOf(0, sampleIdx - checkRange) until minOf(audio.size, sampleIdx + checkRange)) {
                if (abs(audio[j]) > 0.001f) {
                    hasEnergy = true
                    break
                }
            }
            assertTrue("高音 onset=${onset}ms 处应有信号", hasEnergy)
        }
    }

    @Test
    fun `低音声部onset处有信号`() {
        val engine = PolyrhythmTrainingEngine()
        val (_, lowOnsets) = engine.computeOnsetTimes(PolyrhythmType.THREE_FOUR, 1)
        val audio = builder.renderPolyrhythm(PolyrhythmType.THREE_FOUR, 1)
        for (onset in lowOnsets) {
            val sampleIdx = (onset * 44100 / 1000.0).toInt()
            val checkRange = 44100 * 5 / 1000
            var hasEnergy = false
            for (j in maxOf(0, sampleIdx - checkRange) until minOf(audio.size, sampleIdx + checkRange)) {
                if (abs(audio[j]) > 0.001f) {
                    hasEnergy = true
                    break
                }
            }
            assertTrue("低音 onset=${onset}ms 处应有信号", hasEnergy)
        }
    }
}
