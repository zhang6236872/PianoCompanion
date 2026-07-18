package com.pianocompanion.dynamicsdirectiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 力度变化方向辨识训练音频构建器单元测试。
 *
 * 优先测试纯函数 [DynamicsDirectionAudioBuilder.contourValue]（确定性、无浮点叠加噪声），
 * 再测试渲染输出 [DynamicsDirectionAudioBuilder.render] 与逐音符能量
 * [DynamicsDirectionAudioBuilder.noteRmsEnergy] 的整体走势。
 */
class DynamicsDirectionAudioBuilderTest {

    private val sampleRate = 44100
    private val builder = DynamicsDirectionAudioBuilder(sampleRate)

    // ── contourValue 形状验证（纯函数，确定性） ────────

    @Test
    fun `crescendo contour strictly increases`() {
        val a = builder.contourValue(DynamicsDirection.CRESCENDO, 0.0)
        val mid = builder.contourValue(DynamicsDirection.CRESCENDO, 0.5)
        val b = builder.contourValue(DynamicsDirection.CRESCENDO, 1.0)
        assertTrue("Crescendo should increase: $a < $mid < $b", a < mid && mid < b)
        assertEquals(DynamicsDirectionAudioBuilder.MIN_GAIN, a.toDouble(), 1e-6)
        assertEquals(DynamicsDirectionAudioBuilder.MAX_GAIN, b.toDouble(), 1e-6)
    }

    @Test
    fun `decrescendo contour strictly decreases`() {
        val a = builder.contourValue(DynamicsDirection.DECRESCENDO, 0.0)
        val mid = builder.contourValue(DynamicsDirection.DECRESCENDO, 0.5)
        val b = builder.contourValue(DynamicsDirection.DECRESCENDO, 1.0)
        assertTrue("Decrescendo should decrease: $a > $mid > $b", a > mid && mid > b)
        assertEquals(DynamicsDirectionAudioBuilder.MAX_GAIN, a.toDouble(), 1e-6)
        assertEquals(DynamicsDirectionAudioBuilder.MIN_GAIN, b.toDouble(), 1e-6)
    }

    @Test
    fun `steady contour is constant at mid gain`() {
        val a = builder.contourValue(DynamicsDirection.STEADY, 0.0)
        val mid = builder.contourValue(DynamicsDirection.STEADY, 0.5)
        val b = builder.contourValue(DynamicsDirection.STEADY, 1.0)
        assertEquals(a.toDouble(), mid.toDouble(), 1e-9)
        assertEquals(mid.toDouble(), b.toDouble(), 1e-9)
        assertEquals(DynamicsDirectionAudioBuilder.MID_GAIN.toDouble(), a.toDouble(), 1e-6)
    }

    @Test
    fun `swell contour is mountain shaped`() {
        val a = builder.contourValue(DynamicsDirection.SWELL, 0.0)
        val mid = builder.contourValue(DynamicsDirection.SWELL, 0.5)
        val b = builder.contourValue(DynamicsDirection.SWELL, 1.0)
        // 两端低、中间高
        assertTrue("Swell middle ($mid) should exceed head ($a)", mid > a)
        assertTrue("Swell middle ($mid) should exceed tail ($b)", mid > b)
        assertEquals(DynamicsDirectionAudioBuilder.MIN_GAIN, a.toDouble(), 1e-6)
        assertEquals(DynamicsDirectionAudioBuilder.MIN_GAIN, b.toDouble(), 1e-6)
        assertEquals(DynamicsDirectionAudioBuilder.MAX_GAIN, mid.toDouble(), 1e-6)
        // 对称：1/4 与 3/4 处应相等
        val q1 = builder.contourValue(DynamicsDirection.SWELL, 0.25)
        val q3 = builder.contourValue(DynamicsDirection.SWELL, 0.75)
        assertEquals(q1.toDouble(), q3.toDouble(), 1e-6)
    }

    @Test
    fun `reverse swell contour is valley shaped`() {
        val a = builder.contourValue(DynamicsDirection.REVERSE_SWELL, 0.0)
        val mid = builder.contourValue(DynamicsDirection.REVERSE_SWELL, 0.5)
        val b = builder.contourValue(DynamicsDirection.REVERSE_SWELL, 1.0)
        // 两端高、中间低
        assertTrue("Reverse swell head ($a) should exceed middle ($mid)", a > mid)
        assertTrue("Reverse swell tail ($b) should exceed middle ($mid)", b > mid)
        assertEquals(DynamicsDirectionAudioBuilder.MAX_GAIN, a.toDouble(), 1e-6)
        assertEquals(DynamicsDirectionAudioBuilder.MAX_GAIN, b.toDouble(), 1e-6)
        assertEquals(DynamicsDirectionAudioBuilder.MIN_GAIN, mid.toDouble(), 1e-6)
    }

    @Test
    fun `contour value clamps t outside 0 to 1`() {
        // t<0 应等价于 t=0，t>1 应等价于 t=1
        val dir = DynamicsDirection.CRESCENDO
        assertEquals(builder.contourValue(dir, 0.0).toDouble(), builder.contourValue(dir, -0.5).toDouble(), 1e-9)
        assertEquals(builder.contourValue(dir, 1.0).toDouble(), builder.contourValue(dir, 1.5).toDouble(), 1e-9)
    }

    @Test
    fun `crescendo and decrescendo are mirrors`() {
        for (i in 0..10) {
            val t = i / 10.0
            val c = builder.contourValue(DynamicsDirection.CRESCENDO, t)
            val d = builder.contourValue(DynamicsDirection.DECRESCENDO, t)
            assertEquals("At t=$t crescendo+decrescendo should equal MIN+MAX", (c + d).toDouble(),
                DynamicsDirectionAudioBuilder.MIN_GAIN + DynamicsDirectionAudioBuilder.MAX_GAIN, 1e-6)
        }
    }

    // ── gainContour ────────────────────────────────────

    @Test
    fun `gain contour length matches melody steps`() {
        DynamicsDirection.ALL.forEach { dir ->
            val gains = builder.gainContour(dir)
            assertEquals(
                "Gain contour for $dir has wrong length",
                DynamicsDirectionAudioBuilder.MELODY_STEPS.size,
                gains.size
            )
        }
    }

    @Test
    fun `gain contour endpoints match contourValue`() {
        DynamicsDirection.ALL.forEach { dir ->
            val gains = builder.gainContour(dir)
            val n = gains.size
            val t0 = builder.contourValue(dir, 0.0)
            val tEnd = builder.contourValue(dir, 1.0)
            assertEquals("Head gain mismatch for $dir", t0, gains.first(), 1e-6f)
            assertEquals("Tail gain mismatch for $dir", tEnd, gains.last(), 1e-6f)
        }
    }

    @Test
    fun `crescendo gain contour is non-decreasing`() {
        val gains = builder.gainContour(DynamicsDirection.CRESCENDO).toList()
        for (i in 1 until gains.size) {
            assertTrue(
                "Crescendo gain should be non-decreasing: ${gains[i - 1]} <= ${gains[i]}",
                gains[i] >= gains[i - 1] - 1e-6f
            )
        }
    }

    @Test
    fun `decrescendo gain contour is non-increasing`() {
        val gains = builder.gainContour(DynamicsDirection.DECRESCENDO).toList()
        for (i in 1 until gains.size) {
            assertTrue(
                "Decrescendo gain should be non-increasing: ${gains[i - 1]} >= ${gains[i]}",
                gains[i - 1] >= gains[i] - 1e-6f
            )
        }
    }

    // ── midiToFreq ─────────────────────────────────────

    @Test
    fun `midi to freq for A4 is 440`() {
        assertEquals(440.0, builder.midiToFreq(69), 1e-6)
    }

    @Test
    fun `midi to freq doubles per octave`() {
        val c4 = builder.midiToFreq(60)
        val c5 = builder.midiToFreq(72)
        assertEquals(2.0, c5 / c4, 1e-6)
    }

    // ── render 输出 ────────────────────────────────────

    private fun question(
        direction: DynamicsDirection,
        difficulty: DynamicsDirectionDifficulty = DynamicsDirectionDifficulty.ADVANCED,
        tonic: Int = 60
    ): DynamicsDirectionQuestion {
        // 找一个能产生指定 direction 的种子
        var seed = 0L
        while (true) {
            val e = DynamicsDirectionEngine.withSeed(seed)
            val q = e.generate(difficulty)
            if (q.direction == direction) {
                return q
            }
            seed++
        }
    }

    @Test
    fun `render returns non-empty buffer`() {
        val q = question(DynamicsDirection.CRESCENDO)
        val buf = builder.render(q)
        assertTrue("Rendered buffer should not be empty", buf.isNotEmpty())
    }

    @Test
    fun `render produces at least 1 second of audio`() {
        val q = question(DynamicsDirection.STEADY)
        val buf = builder.render(q)
        assertTrue(
            "Expected >= $sampleRate samples, got ${buf.size}",
            buf.size >= sampleRate
        )
    }

    @Test
    fun `rendered samples are within normalized range`() {
        DynamicsDirection.ALL.forEach { dir ->
            val buf = builder.render(question(dir))
            buf.forEach { s ->
                assertTrue("Sample $s out of [-1,1] for $dir", s in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `rendered audio has non-zero content for all directions`() {
        DynamicsDirection.ALL.forEach { dir ->
            val buf = builder.render(question(dir))
            assertTrue(
                "Direction $dir produced all-zero buffer",
                buf.any { it != 0.0f }
            )
        }
    }

    @Test
    fun `estimate duration ms is positive and reasonable`() {
        val q = question(DynamicsDirection.CRESCENDO)
        val ms = builder.estimateDurationMs(q)
        assertTrue("Estimated duration should be positive: $ms", ms > 0)
        // 9 音符 × 340ms 间隔 + 前导 200 + 尾部 350 ≈ 3600+ms
        assertTrue("Estimated duration too short: $ms", ms >= 3000)
    }

    // ── noteRmsEnergy 走势验证 ─────────────────────────

    @Test
    fun `crescendo rendered energy increases across notes`() {
        val q = question(DynamicsDirection.CRESCENDO)
        val buf = builder.render(q)
        val n = DynamicsDirectionAudioBuilder.MELODY_STEPS.size
        val energies = (0 until n).map { builder.noteRmsEnergy(buf, it) }
        val head = energies.first()
        val tail = energies.last()
        assertTrue(
            "Crescendo energy should increase head→tail: $head → $tail",
            tail > head
        )
    }

    @Test
    fun `decrescendo rendered energy decreases across notes`() {
        val q = question(DynamicsDirection.DECRESCENDO)
        val buf = builder.render(q)
        val n = DynamicsDirectionAudioBuilder.MELODY_STEPS.size
        val energies = (0 until n).map { builder.noteRmsEnergy(buf, it) }
        val head = energies.first()
        val tail = energies.last()
        assertTrue(
            "Decrescendo energy should decrease head→tail: $head → $tail",
            head > tail
        )
    }

    @Test
    fun `swell rendered energy peaks in the middle`() {
        val q = question(DynamicsDirection.SWELL)
        val buf = builder.render(q)
        val n = DynamicsDirectionAudioBuilder.MELODY_STEPS.size
        val energies = (0 until n).map { builder.noteRmsEnergy(buf, it) }
        val midIdx = n / 2
        assertTrue(
            "Swell middle energy (${energies[midIdx]}) should exceed head (${energies.first()})",
            energies[midIdx] > energies.first()
        )
        assertTrue(
            "Swell middle energy (${energies[midIdx]}) should exceed tail (${energies.last()})",
            energies[midIdx] > energies.last()
        )
    }

    @Test
    fun `reverse swell rendered energy troughs in the middle`() {
        val q = question(DynamicsDirection.REVERSE_SWELL)
        val buf = builder.render(q)
        val n = DynamicsDirectionAudioBuilder.MELODY_STEPS.size
        val energies = (0 until n).map { builder.noteRmsEnergy(buf, it) }
        val midIdx = n / 2
        assertTrue(
            "Reverse swell middle energy (${energies[midIdx]}) should be below head (${energies.first()})",
            energies[midIdx] < energies.first()
        )
        assertTrue(
            "Reverse swell middle energy (${energies[midIdx]}) should be below tail (${energies.last()})",
            energies[midIdx] < energies.last()
        )
    }

    @Test
    fun `steady rendered energy is roughly uniform`() {
        val q = question(DynamicsDirection.STEADY)
        val buf = builder.render(q)
        val n = DynamicsDirectionAudioBuilder.MELODY_STEPS.size
        val energies = (0 until n).map { builder.noteRmsEnergy(buf, it) }
        val maxE = energies.max()
        val minE = energies.min()
        // 持平时各音能量应接近（衰减包络导致轻微差异，但增益相同）
        assertTrue(
            "Steady energy should be roughly uniform: min=$minE max=$maxE",
            minE > maxE * 0.3
        )
    }

    @Test
    fun `build note events count matches melody steps`() {
        val q = question(DynamicsDirection.CRESCENDO)
        val events = builder.buildNoteEvents(q)
        assertEquals(
            DynamicsDirectionAudioBuilder.MELODY_STEPS.size,
            events.size
        )
    }

    @Test
    fun `build note events onsets are evenly spaced`() {
        val q = question(DynamicsDirection.STEADY)
        val events = builder.buildNoteEvents(q)
        for (i in events.indices) {
            val expected = i * DynamicsDirectionAudioBuilder.NOTE_ONSET_SPACING_MS
            assertEquals(expected, events[i].onsetMs, 1e-6)
        }
    }

    @Test
    fun `default sample rate is 44100`() {
        assertEquals(44100, DynamicsDirectionAudioBuilder.DEFAULT_SAMPLE_RATE)
        val defaultBuilder = DynamicsDirectionAudioBuilder()
        val q = question(DynamicsDirection.STEADY)
        val buf = defaultBuilder.render(q)
        assertTrue(buf.isNotEmpty())
    }
}
