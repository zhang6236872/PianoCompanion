package com.pianocompanion.tempochangetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 速度变化方向辨识训练音频构建器单元测试。
 *
 * 优先测试纯函数 [TempoChangeAudioBuilder.intervalAt]（确定性、无浮点叠加噪声），
 * 再测试 [TempoChangeAudioBuilder.computeOnsetTimes] / [computeInterOnsetIntervals]
 * 的间距走势，最后验证 [TempoChangeAudioBuilder.render] 输出。
 *
 * 与「力度变化方向」AudioBuilder 的测试对称：那里验证逐音符增益走势，这里验证逐音符
 * 间距（inter-onset interval）走势。
 */
class TempoChangeAudioBuilderTest {

    private val sampleRate = 44100
    private val builder = TempoChangeAudioBuilder(sampleRate)

    // ── intervalAt 形状验证（纯函数，确定性） ────────────────

    @Test
    fun `accelerando interval strictly decreases`() {
        // 渐快 = 间距越来越小
        val a = builder.intervalAt(TempoChange.ACCELERANDO, 0.0)
        val mid = builder.intervalAt(TempoChange.ACCELERANDO, 0.5)
        val b = builder.intervalAt(TempoChange.ACCELERANDO, 1.0)
        assertTrue("Accelerando should decrease: $a > $mid > $b", a > mid && mid > b)
        assertEquals(TempoChangeAudioBuilder.MAX_INTERVAL_MS, a, 1e-6)
        assertEquals(TempoChangeAudioBuilder.MIN_INTERVAL_MS, b, 1e-6)
    }

    @Test
    fun `ritardando interval strictly increases`() {
        // 渐慢 = 间距越来越大
        val a = builder.intervalAt(TempoChange.RITARDANDO, 0.0)
        val mid = builder.intervalAt(TempoChange.RITARDANDO, 0.5)
        val b = builder.intervalAt(TempoChange.RITARDANDO, 1.0)
        assertTrue("Ritardando should increase: $a < $mid < $b", a < mid && mid < b)
        assertEquals(TempoChangeAudioBuilder.MIN_INTERVAL_MS, a, 1e-6)
        assertEquals(TempoChangeAudioBuilder.MAX_INTERVAL_MS, b, 1e-6)
    }

    @Test
    fun `steady interval is constant at mid value`() {
        val a = builder.intervalAt(TempoChange.STEADY, 0.0)
        val mid = builder.intervalAt(TempoChange.STEADY, 0.5)
        val b = builder.intervalAt(TempoChange.STEADY, 1.0)
        assertEquals(a, mid, 1e-9)
        assertEquals(mid, b, 1e-9)
        assertEquals(TempoChangeAudioBuilder.MID_INTERVAL_MS, a, 1e-6)
    }

    @Test
    fun `accel-rit interval is valley shaped`() {
        // 渐快渐慢 = 间距两端大、中间小（间距的"山谷"=速度的"山丘"）
        val a = builder.intervalAt(TempoChange.ACCEL_RIT, 0.0)
        val mid = builder.intervalAt(TempoChange.ACCEL_RIT, 0.5)
        val b = builder.intervalAt(TempoChange.ACCEL_RIT, 1.0)
        assertTrue("Accel-Rit middle interval ($mid) should be below head ($a)", mid < a)
        assertTrue("Accel-Rit middle interval ($mid) should be below tail ($b)", mid < b)
        assertEquals(TempoChangeAudioBuilder.MAX_INTERVAL_MS, a, 1e-6)
        assertEquals(TempoChangeAudioBuilder.MAX_INTERVAL_MS, b, 1e-6)
        assertEquals(TempoChangeAudioBuilder.MIN_INTERVAL_MS, mid, 1e-6)
        // 对称：1/4 与 3/4 处应相等
        val q1 = builder.intervalAt(TempoChange.ACCEL_RIT, 0.25)
        val q3 = builder.intervalAt(TempoChange.ACCEL_RIT, 0.75)
        assertEquals(q1, q3, 1e-6)
    }

    @Test
    fun `rit-accel interval is mountain shaped`() {
        // 渐慢渐快 = 间距两端小、中间大（间距的"山丘"=速度的"山谷"）
        val a = builder.intervalAt(TempoChange.RIT_ACCEL, 0.0)
        val mid = builder.intervalAt(TempoChange.RIT_ACCEL, 0.5)
        val b = builder.intervalAt(TempoChange.RIT_ACCEL, 1.0)
        assertTrue("Rit-Accel middle interval ($mid) should exceed head ($a)", mid > a)
        assertTrue("Rit-Accel middle interval ($mid) should exceed tail ($b)", mid > b)
        assertEquals(TempoChangeAudioBuilder.MIN_INTERVAL_MS, a, 1e-6)
        assertEquals(TempoChangeAudioBuilder.MIN_INTERVAL_MS, b, 1e-6)
        assertEquals(TempoChangeAudioBuilder.MAX_INTERVAL_MS, mid, 1e-6)
    }

    @Test
    fun `interval value clamps t outside 0 to 1`() {
        // t<0 应等价于 t=0，t>1 应等价于 t=1
        val dir = TempoChange.ACCELERANDO
        assertEquals(builder.intervalAt(dir, 0.0), builder.intervalAt(dir, -0.5), 1e-9)
        assertEquals(builder.intervalAt(dir, 1.0), builder.intervalAt(dir, 1.5), 1e-9)
    }

    @Test
    fun `accelerando and ritardando are mirrors`() {
        for (i in 0..10) {
            val t = i / 10.0
            val acc = builder.intervalAt(TempoChange.ACCELERANDO, t)
            val rit = builder.intervalAt(TempoChange.RITARDANDO, t)
            assertEquals(
                "At t=$t accel+rit should equal MIN+MAX",
                acc + rit,
                TempoChangeAudioBuilder.MIN_INTERVAL_MS + TempoChangeAudioBuilder.MAX_INTERVAL_MS,
                1e-6
            )
        }
    }

    // ── computeOnsetTimes / computeInterOnsetIntervals ────────

    @Test
    fun `onset array length matches melody steps`() {
        TempoChange.ALL.forEach { dir ->
            val q = question(dir)
            val onsets = builder.computeOnsetTimes(q)
            assertEquals(
                "Onset array for $dir has wrong length",
                TempoChangeAudioBuilder.MELODY_STEPS.size,
                onsets.size
            )
        }
    }

    @Test
    fun `first onset is always zero`() {
        TempoChange.ALL.forEach { dir ->
            val q = question(dir)
            val onsets = builder.computeOnsetTimes(q)
            assertEquals(0.0, onsets[0], 1e-9)
        }
    }

    @Test
    fun `onsets are strictly increasing`() {
        TempoChange.ALL.forEach { dir ->
            val q = question(dir)
            val onsets = builder.computeOnsetTimes(q)
            for (i in 1 until onsets.size) {
                assertTrue(
                    "Onsets should strictly increase for $dir: ${onsets[i - 1]} < ${onsets[i]}",
                    onsets[i] > onsets[i - 1]
                )
            }
        }
    }

    @Test
    fun `inter-onset intervals length is n-1`() {
        TempoChange.ALL.forEach { dir ->
            val q = question(dir)
            val gaps = builder.computeInterOnsetIntervals(q)
            assertEquals(
                TempoChangeAudioBuilder.MELODY_STEPS.size - 1,
                gaps.size
            )
        }
    }

    @Test
    fun `accelerando intervals are strictly decreasing`() {
        val gaps = builder.computeInterOnsetIntervals(question(TempoChange.ACCELERANDO))
        for (i in 1 until gaps.size) {
            assertTrue(
                "Accelerando gaps should strictly decrease: ${gaps[i - 1]} > ${gaps[i]}",
                gaps[i - 1] > gaps[i]
            )
        }
    }

    @Test
    fun `ritardando intervals are strictly increasing`() {
        val gaps = builder.computeInterOnsetIntervals(question(TempoChange.RITARDANDO))
        for (i in 1 until gaps.size) {
            assertTrue(
                "Ritardando gaps should strictly increase: ${gaps[i - 1]} < ${gaps[i]}",
                gaps[i - 1] < gaps[i]
            )
        }
    }

    @Test
    fun `steady intervals are all equal`() {
        val gaps = builder.computeInterOnsetIntervals(question(TempoChange.STEADY))
        for (i in 1 until gaps.size) {
            assertEquals(gaps[0], gaps[i], 1e-9)
        }
        assertEquals(TempoChangeAudioBuilder.MID_INTERVAL_MS, gaps[0], 1e-6)
    }

    @Test
    fun `accel-rit intervals valley in the middle`() {
        // 渐快渐慢：间距两端大、中间小
        val gaps = builder.computeInterOnsetIntervals(question(TempoChange.ACCEL_RIT))
        val midIdx = gaps.size / 2
        assertTrue(
            "Accel-Rit middle gap (${gaps[midIdx]}) should be below first (${gaps.first()})",
            gaps[midIdx] < gaps.first()
        )
        assertTrue(
            "Accel-Rit middle gap (${gaps[midIdx]}) should be below last (${gaps.last()})",
            gaps[midIdx] < gaps.last()
        )
    }

    @Test
    fun `rit-accel intervals peak in the middle`() {
        // 渐慢渐快：间距两端小、中间大
        val gaps = builder.computeInterOnsetIntervals(question(TempoChange.RIT_ACCEL))
        val midIdx = gaps.size / 2
        assertTrue(
            "Rit-Accel middle gap (${gaps[midIdx]}) should exceed first (${gaps.first()})",
            gaps[midIdx] > gaps.first()
        )
        assertTrue(
            "Rit-Accel middle gap (${gaps[midIdx]}) should exceed last (${gaps.last()})",
            gaps[midIdx] > gaps.last()
        )
    }

    @Test
    fun `all intervals stay within min-max bounds`() {
        TempoChange.ALL.forEach { dir ->
            val gaps = builder.computeInterOnsetIntervals(question(dir))
            gaps.forEach { g ->
                assertTrue("Interval $g below MIN for $dir", g >= TempoChangeAudioBuilder.MIN_INTERVAL_MS - 1e-6)
                assertTrue("Interval $g above MAX for $dir", g <= TempoChangeAudioBuilder.MAX_INTERVAL_MS + 1e-6)
            }
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
        direction: TempoChange,
        difficulty: TempoChangeDifficulty = TempoChangeDifficulty.ADVANCED,
        tonic: Int = 60
    ): TempoChangeQuestion {
        // 找一个能产生指定 direction 的种子
        var seed = 0L
        while (true) {
            val e = TempoChangeEngine.withSeed(seed)
            val q = e.generate(difficulty)
            if (q.direction == direction) {
                return q
            }
            seed++
        }
    }

    @Test
    fun `render returns non-empty buffer`() {
        val q = question(TempoChange.ACCELERANDO)
        val buf = builder.render(q)
        assertTrue("Rendered buffer should not be empty", buf.isNotEmpty())
    }

    @Test
    fun `render produces at least 1 second of audio`() {
        val q = question(TempoChange.STEADY)
        val buf = builder.render(q)
        assertTrue(
            "Expected >= $sampleRate samples, got ${buf.size}",
            buf.size >= sampleRate
        )
    }

    @Test
    fun `rendered samples are within normalized range`() {
        TempoChange.ALL.forEach { dir ->
            val buf = builder.render(question(dir))
            buf.forEach { s ->
                assertTrue("Sample $s out of [-1,1] for $dir", s in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `rendered audio has non-zero content for all directions`() {
        TempoChange.ALL.forEach { dir ->
            val buf = builder.render(question(dir))
            assertTrue(
                "Direction $dir produced all-zero buffer",
                buf.any { it != 0.0f }
            )
        }
    }

    @Test
    fun `estimate duration ms is positive and reasonable`() {
        val q = question(TempoChange.ACCELERANDO)
        val ms = builder.estimateDurationMs(q)
        assertTrue("Estimated duration should be positive: $ms", ms > 0)
        // 9 音符 × 210-470ms 间距 + 前导 200 + 尾部 400 ≈ 2000+ms
        assertTrue("Estimated duration too short: $ms", ms >= 2000)
    }

    @Test
    fun `build note events count matches melody steps`() {
        val q = question(TempoChange.ACCELERANDO)
        val events = builder.buildNoteEvents(q)
        assertEquals(
            TempoChangeAudioBuilder.MELODY_STEPS.size,
            events.size
        )
    }

    @Test
    fun `build note events all have constant gain`() {
        // 速度方向训练中所有音符增益恒定
        val q = question(TempoChange.RITARDANDO)
        val events = builder.buildNoteEvents(q)
        events.forEach { e ->
            assertEquals(TempoChangeAudioBuilder.MID_GAIN, e.gain, 1e-6f)
        }
    }

    @Test
    fun `build note events onsets match computeOnsetTimes`() {
        val q = question(TempoChange.ACCEL_RIT)
        val events = builder.buildNoteEvents(q)
        val onsets = builder.computeOnsetTimes(q)
        for (i in events.indices) {
            assertEquals(onsets[i], events[i].onsetMs, 1e-6)
        }
    }

    @Test
    fun `note duration is less than minimum interval`() {
        // 确保起音清晰不重叠
        assertTrue(
            "NOTE_DURATION_MS (${TempoChangeAudioBuilder.NOTE_DURATION_MS}) should be < MIN_INTERVAL_MS (${TempoChangeAudioBuilder.MIN_INTERVAL_MS})",
            TempoChangeAudioBuilder.NOTE_DURATION_MS < TempoChangeAudioBuilder.MIN_INTERVAL_MS
        )
    }

    @Test
    fun `default sample rate is 44100`() {
        assertEquals(44100, TempoChangeAudioBuilder.DEFAULT_SAMPLE_RATE)
        val defaultBuilder = TempoChangeAudioBuilder()
        val q = question(TempoChange.STEADY)
        val buf = defaultBuilder.render(q)
        assertTrue(buf.isNotEmpty())
    }
}
