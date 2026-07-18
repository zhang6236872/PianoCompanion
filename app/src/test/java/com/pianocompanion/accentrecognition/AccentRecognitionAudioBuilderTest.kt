package com.pianocompanion.accentrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AccentAudioBuilder] 单元测试。
 *
 * 验证：onset 时间序列正确性、重音标志正确性、PCM 缓冲区有效性、采样范围、
 * 强拍位置能量高于普通拍（核心听辨线索）、各难度渲染均非静音、估计时长合理。
 */
class AccentRecognitionAudioBuilderTest {

    private val builder = AccentAudioBuilder(sampleRate = 44100)

    private fun makeQuestion(
        beats: Int = 4,
        accentPosition: Int = 1,
        difficulty: AccentDifficulty = when (beats) {
            4 -> AccentDifficulty.BEGINNER
            3 -> AccentDifficulty.INTERMEDIATE
            else -> AccentDifficulty.ADVANCED // ADVANCED 允许 [2,3,4,5]
        }
    ): AccentQuestion {
        return AccentQuestion(
            difficulty = difficulty,
            beatsPerMeasure = beats,
            accentPosition = accentPosition,
            strength = difficulty.strength,
            beatIntervalMs = difficulty.tempoIntervalMs,
            measureRepeat = difficulty.measureRepeat,
            answerChoices = (1..beats).map { "第 $it 拍" },
            correctAnswer = "第 $accentPosition 拍"
        )
    }

    // ── onset 时间序列 ──────────────────────────────────

    @Test
    fun `computeOnsetTimes produces correct count`() {
        val q = makeQuestion(beats = 4, accentPosition = 1)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(q.beatsPerMeasure * q.measureRepeat, onsets.size)
        assertEquals(12, onsets.size) // 4 * 3
    }

    @Test
    fun `onsets start at lead silence`() {
        val q = makeQuestion(beats = 3, accentPosition = 1)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(AccentAudioBuilder.LEAD_SILENCE_MS, onsets.first(), 0.0001)
    }

    @Test
    fun `onsets are evenly spaced`() {
        val q = makeQuestion(beats = 4, accentPosition = 2)
        val onsets = builder.computeOnsetTimes(q)
        val interval = q.beatIntervalMs
        for (i in 1 until onsets.size) {
            val delta = onsets[i] - onsets[i - 1]
            assertEquals(interval, delta, 0.001)
        }
    }

    @Test
    fun `onset timing respects beat interval per difficulty`() {
        for (d in AccentDifficulty.ALL) {
            val q = makeQuestion(beats = d.beatsPerMeasureOptions.first(), difficulty = d)
            val onsets = builder.computeOnsetTimes(q)
            assertEquals(d.tempoIntervalMs, onsets[1] - onsets[0], 0.001)
        }
    }

    @Test
    fun `measure boundary spacing equals beats times interval`() {
        val q = makeQuestion(beats = 4, accentPosition = 1)
        val onsets = builder.computeOnsetTimes(q)
        // 第 0 拍和第 4 拍（下一小节起点）应相隔 beatsPerMeasure * interval
        val measureSpan = onsets[q.beatsPerMeasure] - onsets[0]
        assertEquals(q.beatsPerMeasure * q.beatIntervalMs, measureSpan, 0.001)
    }

    // ── 重音标志 ────────────────────────────────────────

    @Test
    fun `computeAccentFlags marks exactly one accent per measure`() {
        val q = makeQuestion(beats = 4, accentPosition = 3)
        val flags = builder.computeAccentFlags(q)
        assertEquals(q.totalClicks, flags.size)
        // 每小节恰好 1 个强拍
        for (rep in 0 until q.measureRepeat) {
            val measureFlags = flags.subList(rep * q.beatsPerMeasure, (rep + 1) * q.beatsPerMeasure)
            assertEquals(1, measureFlags.count { it })
        }
        // 总强拍数 = 重复次数
        assertEquals(q.measureRepeat, flags.count { it })
    }

    @Test
    fun `accent flags align with accent position`() {
        val q = makeQuestion(beats = 4, accentPosition = 2)
        val flags = builder.computeAccentFlags(q)
        for (rep in 0 until q.measureRepeat) {
            // accentPosition=2 → 每小节索引 1（0-based）为 true
            val accentIdx = rep * q.beatsPerMeasure + (q.accentPosition - 1)
            assertTrue("重复 $rep 的强拍位置应标记", flags[accentIdx])
            // 其他位置为 false
            for (b in 0 until q.beatsPerMeasure) {
                if (b != q.accentPosition - 1) {
                    val idx = rep * q.beatsPerMeasure + b
                    assert(!flags[idx]) { "重复 $rep 第 ${b + 1} 拍不应是强拍" }
                }
            }
        }
    }

    @Test
    fun `accent flags work for various positions`() {
        for (pos in 1..4) {
            val q = makeQuestion(beats = 4, accentPosition = pos)
            val flags = builder.computeAccentFlags(q)
            assertTrue("第 $pos 拍应被标记为强拍", flags[pos - 1])
        }
    }

    // ── PCM 缓冲区有效性 ────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = makeQuestion()
        val buffer = builder.render(q)
        assertTrue(buffer.isNotEmpty())
    }

    @Test
    fun `render samples within valid range`() {
        val q = makeQuestion(difficulty = AccentDifficulty.ADVANCED)
        val buffer = builder.render(q)
        for (sample in buffer) {
            assertTrue("采样 ${sample} 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `render all difficulties produces non-silent audio`() {
        for (d in AccentDifficulty.ALL) {
            val q = makeQuestion(beats = d.beatsPerMeasureOptions.first(), difficulty = d)
            val buffer = builder.render(q)
            val maxAbs = buffer.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            assertTrue("难度 ${d.displayName} 渲染应非静音 (max=$maxAbs)", maxAbs > 0.01f)
        }
    }

    @Test
    fun `render buffer length matches estimate`() {
        val q = makeQuestion(beats = 5, accentPosition = 2, difficulty = AccentDifficulty.ADVANCED)
        val buffer = builder.render(q)
        val expectedSamples = (builder.estimateDurationMs(q) * 44100 / 1000.0).toInt()
        // 容许 1 个采样误差
        assertTrue(
            "缓冲区长度 ${buffer.size} 与估计 $expectedSamples 不符",
            kotlin.math.abs(buffer.size - expectedSamples) <= 2
        )
    }

    // ── 强拍能量高于普通拍（核心听辨线索） ────────────────

    @Test
    fun `accented click has higher energy than base click`() {
        val q = makeQuestion(beats = 4, accentPosition = 1)
        val buffer = builder.render(q)

        val accentEnergy = builder.clickRmsEnergy(buffer, q, clickIndex = 0) // 第 1 拍 = 强拍
        val baseEnergy = builder.clickRmsEnergy(buffer, q, clickIndex = 1)   // 第 2 拍 = 普通拍

        assertTrue(
            "强拍能量 ($accentEnergy) 应高于普通拍 ($baseEnergy)",
            accentEnergy > baseEnergy
        )
    }

    @Test
    fun `accent energy pattern repeats across measures`() {
        val q = makeQuestion(beats = 4, accentPosition = 3)
        val buffer = builder.render(q)
        val flags = builder.computeAccentFlags(q)

        // 验证每个 click 的能量与重音标志一致：强拍能量 > 普通拍能量
        val accentEnergies = mutableListOf<Double>()
        val baseEnergies = mutableListOf<Double>()
        for (i in flags.indices) {
            val e = builder.clickRmsEnergy(buffer, q, clickIndex = i)
            if (flags[i]) accentEnergies.add(e) else baseEnergies.add(e)
        }
        val avgAccent = accentEnergies.average()
        val avgBase = baseEnergies.average()
        assertTrue(
            "平均强拍能量 ($avgAccent) 应大于平均普通拍 ($avgBase)",
            avgAccent > avgBase
        )
    }

    @Test
    fun `strong accent has larger contrast than subtle accent`() {
        // STRONG 重音与普通拍的能量比应大于 SUBTLE
        val strongQ = makeQuestion(beats = 4, accentPosition = 1, difficulty = AccentDifficulty.BEGINNER)
        val subtleQ = makeQuestion(beats = 4, accentPosition = 1, difficulty = AccentDifficulty.ADVANCED)

        val strongBuffer = builder.render(strongQ)
        val subtleBuffer = builder.render(subtleQ)

        val strongAccent = builder.clickRmsEnergy(strongBuffer, strongQ, 0)
        val strongBase = builder.clickRmsEnergy(strongBuffer, strongQ, 1)
        val subtleAccent = builder.clickRmsEnergy(subtleBuffer, subtleQ, 0)
        val subtleBase = builder.clickRmsEnergy(subtleBuffer, subtleQ, 1)

        val strongRatio = strongAccent / strongBase
        val subtleRatio = subtleAccent / subtleBase
        assertTrue(
            "鲜明重音对比 ($strongRatio) 应大于微妙重音 ($subtleRatio)",
            strongRatio > subtleRatio
        )
    }

    // ── 估计时长 ────────────────────────────────────────

    @Test
    fun `estimateDurationMs is positive`() {
        for (d in AccentDifficulty.ALL) {
            val q = makeQuestion(beats = d.beatsPerMeasureOptions.first(), difficulty = d)
            assertTrue(builder.estimateDurationMs(q) > 0)
        }
    }

    @Test
    fun `estimateDurationMs includes lead and tail silence`() {
        val q = makeQuestion(beats = 4, accentPosition = 1)
        val onsets = builder.computeOnsetTimes(q)
        val lastClickEnd = onsets.last() + AccentAudioBuilder.CLICK_DURATION_MS
        val estimate = builder.estimateDurationMs(q)
        // 估计应 >= 最后 click 结束 + 尾部静音
        assertTrue(estimate >= lastClickEnd.toLong())
    }

    // ── 边界情况 ────────────────────────────────────────

    @Test
    fun `2-beat measure renders correctly`() {
        val q = makeQuestion(beats = 2, accentPosition = 1, difficulty = AccentDifficulty.ADVANCED)
        val buffer = builder.render(q)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(q.totalClicks, onsets.size)
        assertTrue(buffer.isNotEmpty())
    }

    @Test
    fun `accent on last beat is detectable`() {
        val q = makeQuestion(beats = 4, accentPosition = 4)
        val buffer = builder.render(q)
        val lastBeatEnergy = builder.clickRmsEnergy(buffer, q, clickIndex = 3)
        val firstBeatEnergy = builder.clickRmsEnergy(buffer, q, clickIndex = 0)
        assertTrue(
            "末拍（强拍）能量 ($lastBeatEnergy) 应高于首拍（普通拍）($firstBeatEnergy)",
            lastBeatEnergy > firstBeatEnergy
        )
    }

    @Test
    fun `clickRmsEnergy returns 0 for out of range index`() {
        val q = makeQuestion()
        val buffer = builder.render(q)
        assertEquals(0.0, builder.clickRmsEnergy(buffer, q, clickIndex = 9999), 0.0001)
    }
}
