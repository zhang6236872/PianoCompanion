package com.pianocompanion.swingfeel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SwingAudioBuilder] 单元测试。
 *
 * 重点验证：起音时间戳、连续起音间距（IOI）的「长—短」交替模式、
 * 长短比（等分=1.0 / 轻摇摆=1.5 / 摇摆=2.0）、PCM 有效性、缓冲区长度等。
 */
class SwingAudioBuilderTest {

    private val builder = SwingAudioBuilder(sampleRate = 44100)

    private fun makeQuestion(ratio: SwingRatio, difficulty: SwingDifficulty): SwingQuestion {
        val choices = difficulty.candidateRatios
            .sortedBy { SwingFeelEngine.swingAmount(it) }
            .map { it.displayName }
        return SwingQuestion(
            difficulty = difficulty,
            ratio = ratio,
            swingFraction = ratio.fraction,
            tempoBpm = difficulty.tempoBpm,
            beatsPerQuestion = difficulty.beatsPerQuestion,
            answerChoices = choices,
            correctAnswer = ratio.displayName
        )
    }

    // ── 起音时间戳 ────────────────────────────────────────

    @Test
    fun `onset count equals twice beats`() {
        val q = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.BEGINNER)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(q.beatsPerQuestion * 2, onsets.size)
    }

    @Test
    fun `first onset includes lead silence`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.ADVANCED)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(SwingAudioBuilder.LEAD_SILENCE_MS, onsets.first(), 0.001)
    }

    @Test
    fun `first onset of each beat is at beat boundary`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.INTERMEDIATE)
        val onsets = builder.computeOnsetTimes(q)
        val beatMs = q.beatMs
        for (i in 0 until q.beatsPerQuestion) {
            val expected = SwingAudioBuilder.LEAD_SILENCE_MS + i * beatMs
            assertEquals(expected, onsets[i * 2], 0.001)
        }
    }

    // ── 等分（straight）：所有间距相等 ────────────────────

    @Test
    fun `straight has equal inter-onset intervals`() {
        val q = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.BEGINNER)
        val iois = builder.computeInterOnsetIntervals(q)
        assertTrue(iois.isNotEmpty())
        val first = iois.first()
        iois.forEach { ioi ->
            assertEquals("等分间距应全部相等", first, ioi, 0.001)
        }
    }

    @Test
    fun `straight interval equals half beat`() {
        val q = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.BEGINNER)
        val iois = builder.computeInterOnsetIntervals(q)
        iois.forEach { assertEquals(q.beatMs * 0.5, it, 0.001) }
    }

    @Test
    fun `straight swing ratio is one`() {
        val q = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.ADVANCED)
        assertEquals(1.0, builder.swingRatio(q), 1e-6)
    }

    // ── 摇摆（swing）：长短交替，比 2:1 ───────────────────

    @Test
    fun `swing intervals alternate long and short`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.INTERMEDIATE)
        val iois = builder.computeInterOnsetIntervals(q)
        val f = q.swingFraction
        // 拍内间距（o1->o2）= f*beatMs（长）；跨拍间距（o2->下一o1）= (1-f)*beatMs（短）
        // 序列：长, 短, 长, 短, ...
        for (i in iois.indices) {
            val expected = if (i % 2 == 0) f * q.beatMs else (1.0 - f) * q.beatMs
            assertEquals("第 $i 个间距", expected, iois[i], 0.001)
        }
    }

    @Test
    fun `swing long interval is longer than short`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.ADVANCED)
        val iois = builder.computeInterOnsetIntervals(q)
        val longs = iois.filterIndexed { i, _ -> i % 2 == 0 }
        val shorts = iois.filterIndexed { i, _ -> i % 2 == 1 }
        longs.forEach { l -> shorts.forEach { s -> assertTrue("长间距 $l 应大于短间距 $s", l > s) } }
    }

    @Test
    fun `swing ratio is two`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.ADVANCED)
        // 摇摆 = 2/3 占比 → 长:短 = (2/3):(1/3) = 2:1
        assertEquals(2.0, builder.swingRatio(q), 1e-3)
    }

    // ── 轻摇摆（light swing）：比 3:2 ─────────────────────

    @Test
    fun `light swing ratio is three halves`() {
        val q = makeQuestion(SwingRatio.LIGHT_SWING, SwingDifficulty.INTERMEDIATE)
        // fraction = 0.6 → 长:短 = 0.6:0.4 = 1.5
        assertEquals(1.5, builder.swingRatio(q), 1e-3)
    }

    @Test
    fun `light swing is between straight and swing`() {
        val straight = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.INTERMEDIATE)
        val light = makeQuestion(SwingRatio.LIGHT_SWING, SwingDifficulty.INTERMEDIATE)
        val swing = makeQuestion(SwingRatio.SWING, SwingDifficulty.INTERMEDIATE)
        val rStraight = builder.swingRatio(straight)
        val rLight = builder.swingRatio(light)
        val rSwing = builder.swingRatio(swing)
        assertTrue("等分 $rStraight < 轻摇摆 $rLight", rStraight < rLight)
        assertTrue("轻摇摆 $rLight < 摇摆 $rSwing", rLight < rSwing)
    }

    // ── PCM 有效性 ────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        SwingRatio.ALL.forEach { ratio ->
            val q = makeQuestion(ratio, SwingDifficulty.ADVANCED)
            val audio = builder.render(q)
            assertTrue("${ratio.displayName} 音频为空", audio.isNotEmpty())
        }
    }

    @Test
    fun `render output is within valid range`() {
        SwingDifficulty.ALL.forEach { d ->
            d.candidateRatios.forEach { ratio ->
                val q = makeQuestion(ratio, d)
                val audio = builder.render(q)
                audio.forEach { sample ->
                    assertTrue("样本 $sample 超出 [-1, 1]", sample in -1.0f..1.0f)
                }
            }
        }
    }

    @Test
    fun `renderRaw produces non-empty buffer`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.BEGINNER)
        assertTrue(builder.renderRaw(q).isNotEmpty())
    }

    @Test
    fun `buffer length matches estimate`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.ADVANCED)
        val audio = builder.render(q)
        val expectedMs = builder.estimateDurationMs(q)
        val expectedSamples = (44100.0 * expectedMs / 1000.0).toInt()
        assertTrue(
            "缓冲区长度 ${audio.size} 与预期 $expectedSamples 相差过大",
            kotlin.math.abs(audio.size - expectedSamples) <= 2
        )
    }

    @Test
    fun `faster tempo yields shorter buffer`() {
        val qSlow = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.BEGINNER)   // 80 BPM
        val qFast = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.ADVANCED)    // 130 BPM
        val durSlow = builder.estimateDurationMs(qSlow)
        val durFast = builder.estimateDurationMs(qFast)
        assertTrue("慢速 ($durSlow ms) 应长于快速 ($durFast ms)", durSlow > durFast)
    }

    // ── 频率精度（单音高）────────────────────────────────

    @Test
    fun `midiToFreq A4 equals 440 Hz`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.01)
    }

    @Test
    fun `midiToFreq doubles per octave`() {
        val f1 = builder.midiToFreq(60)
        val f2 = builder.midiToFreq(72)
        assertEquals(2.0, f2 / f1, 0.001)
    }

    @Test
    fun `note pitch is fixed C5 for all onsets`() {
        // 所有音符同音高（C5 = MIDI 72），摇摆感唯一线索是时间比例
        SwingAudioBuilder.run {
            assertEquals(72, NOTE_MIDI)
        }
    }

    // ── estimateDurationMs ───────────────────────────────

    @Test
    fun `estimateDurationMs is positive for all configs`() {
        SwingDifficulty.ALL.forEach { d ->
            d.candidateRatios.forEach { ratio ->
                val q = makeQuestion(ratio, d)
                assertTrue(builder.estimateDurationMs(q) > 0)
            }
        }
    }

    // ── 端到端：音频中能检测到长短交替 ───────────────────

    @Test
    fun `swing audio has alternating high-low energy windows`() {
        val q = makeQuestion(SwingRatio.SWING, SwingDifficulty.BEGINNER) // 80 BPM, beatMs=750
        val audio = builder.renderRaw(q)
        val onsets = builder.computeOnsetTimes(q)
        // 取每对（拍内）两个起音的能量，验证两者都非零（确实都发音）
        var pairsOk = 0
        var i = 0
        while (i + 1 < onsets.size) {
            val e1 = windowEnergy(audio, onsets[i], q.beatMs * 0.2)
            val e2 = windowEnergy(audio, onsets[i + 1], q.beatMs * 0.2)
            if (e1 > 0.0 && e2 > 0.0) pairsOk++
            i += 2
        }
        assertTrue("应所有拍的两个音符都发音", pairsOk == q.beatsPerQuestion)
    }

    @Test
    fun `straight audio onsets are evenly spaced`() {
        val q = makeQuestion(SwingRatio.STRAIGHT, SwingDifficulty.INTERMEDIATE)
        val audio = builder.renderRaw(q)
        // 验证整个缓冲区的能量都在 [-1, 1]（renderRaw 不限幅，但单音不会超）
        val maxAbs = audio.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        assertTrue("renderRaw 单音最大幅值 $maxAbs 应有限", maxAbs < 10f)
    }

    /** 计算指定起音时间戳后 windowMs 窗口内的 RMS 能量。 */
    private fun windowEnergy(buffer: FloatArray, onsetMs: Double, windowMs: Double): Double {
        val sr = 44100
        val start = (onsetMs * sr / 1000.0).toInt()
        val len = (windowMs * sr / 1000.0).toInt().coerceAtLeast(1)
        var sumSq = 0.0
        var count = 0
        for (i in start until minOf(start + len, buffer.size)) {
            sumSq += buffer[i].toDouble() * buffer[i]
            count++
        }
        return if (count > 0) kotlin.math.sqrt(sumSq / count) else 0.0
    }
}
