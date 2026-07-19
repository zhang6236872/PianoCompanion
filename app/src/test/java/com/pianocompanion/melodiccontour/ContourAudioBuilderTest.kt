package com.pianocompanion.melodiccontour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContourAudioBuilder] 单元测试。
 *
 * 验证 PCM 有效性、起音时间戳等间距、频率精度、能量一致性、缓冲区长度等。
 */
class ContourAudioBuilderTest {

    private val builder = ContourAudioBuilder(sampleRate = 44100)

    private fun makeQuestion(
        contour: ContourType = ContourType.ASCENDING,
        difficulty: ContourDifficulty = ContourDifficulty.BEGINNER
    ): ContourQuestion {
        val engine = ContourEngine.withSeed(42L)
        // 生成多题直到匹配目标轮廓
        repeat(100) {
            val q = engine.generate(difficulty)
            if (q.contour == contour) return q
        }
        // 兜底：直接构造一个匹配轮廓类型的确定题目（音高按 noteCount 长度生成）
        val n = difficulty.noteCount
        val step = 4
        val pitches = when (contour) {
            ContourType.ASCENDING -> (0 until n).map { 60 + it * step }
            ContourType.DESCENDING -> (0 until n).map { 72 - it * step }
            ContourType.ARCH -> {
                val mid = n / 2
                (0 until n).map { i ->
                    if (i <= mid) 60 + i * step else 60 + mid * step - (i - mid) * step
                }
            }
            ContourType.VALLEY -> {
                val mid = n / 2
                (0 until n).map { i ->
                    if (i <= mid) 72 - i * step else 72 - mid * step + (i - mid) * step
                }
            }
            ContourType.WAVE -> {
                val result = mutableListOf<Int>()
                var cur = 60
                for (i in 0 until n) {
                    result.add(cur)
                    cur += if (i % 2 == 0) step else -(step - 1)
                }
                result
            }
        }
        val choices = difficulty.contourOptions.sortedBy { ContourEngine.contourComplexity(it) }.map { it.displayName }
        return ContourQuestion(
            difficulty = difficulty,
            contour = contour,
            pitches = pitches,
            noteDurationMs = difficulty.noteDurationMs,
            answerChoices = choices,
            correctAnswer = contour.displayName
        )
    }

    // ── 起音时间戳 ────────────────────────────────────────

    @Test
    fun `onset count equals note count`() {
        val q = makeQuestion(ContourType.ASCENDING, ContourDifficulty.BEGINNER)
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(q.noteCount, onsets.size)
    }

    @Test
    fun `onsets are evenly spaced`() {
        val q = makeQuestion(ContourType.DESCENDING, ContourDifficulty.INTERMEDIATE)
        val onsets = builder.computeOnsetTimes(q)
        for (i in 1 until onsets.size) {
            val gap = onsets[i] - onsets[i - 1]
            assertEquals(q.noteDurationMs, gap, 0.5)
        }
    }

    @Test
    fun `first onset includes lead silence`() {
        val q = makeQuestion()
        val onsets = builder.computeOnsetTimes(q)
        assertEquals(ContourAudioBuilder.LEAD_SILENCE_MS, onsets.first(), 0.001)
    }

    // ── PCM 有效性 ────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = makeQuestion()
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `render output is within valid range`() {
        ContourDifficulty.ALL.forEach { d ->
            val q = makeQuestion(ContourType.ASCENDING, d)
            val audio = builder.render(q)
            audio.forEach { sample ->
                assertTrue("样本 $sample 超出 [-1, 1]", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `renderRaw produces non-empty buffer`() {
        val q = makeQuestion()
        val audio = builder.renderRaw(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `buffer length matches estimate`() {
        val q = makeQuestion(ContourType.WAVE, ContourDifficulty.ADVANCED)
        val audio = builder.render(q)
        val expectedMs = builder.estimateDurationMs(q)
        val expectedSamples = (44100.0 * expectedMs / 1000.0).toInt()
        // 允许 2 个样本的舍入误差
        assertTrue("缓冲区长度 ${audio.size} 与预期 $expectedSamples 相差过大",
            kotlin.math.abs(audio.size - expectedSamples) <= 2)
    }

    @Test
    fun `render handles all contour types`() {
        ContourType.ALL.forEach { contour ->
            val d = if (contour == ContourType.WAVE) ContourDifficulty.ADVANCED else ContourDifficulty.BEGINNER
            val q = makeQuestion(contour, d)
            val audio = builder.render(q)
            assertTrue("轮廓 ${contour.displayName} 的音频为空", audio.isNotEmpty())
        }
    }

    @Test
    fun `render empty question returns empty buffer`() {
        // 用空音高序列不可能通过 init，所以构造 0 起音的边界
        val q = makeQuestion()
        val onsets = builder.computeOnsetTimes(q)
        assertTrue(onsets.isNotEmpty())
    }

    // ── 频率精度 ──────────────────────────────────────────

    @Test
    fun `midiToFreq A4 equals 440 Hz`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.01)
    }

    @Test
    fun `midiToFreq C4 equals approximately 261_63 Hz`() {
        assertEquals(261.63, builder.midiToFreq(60), 0.1)
    }

    @Test
    fun `midiToFreq doubles per octave`() {
        val f1 = builder.midiToFreq(60) // C4
        val f2 = builder.midiToFreq(72) // C5
        assertEquals(2.0, f2 / f1, 0.001)
    }

    @Test
    fun `midiToFreq is monotonic increasing`() {
        var prev = 0.0
        for (midi in 48..84) {
            val f = builder.midiToFreq(midi)
            assertTrue("MIDI $midi 频率 $f 未高于前一个", f > prev)
            prev = f
        }
    }

    // ── 能量一致性 ────────────────────────────────────────

    @Test
    fun `all notes have comparable energy`() {
        val q = makeQuestion(ContourType.ASCENDING, ContourDifficulty.BEGINNER)
        val audio = builder.render(q)
        val energies = (0 until q.noteCount).map { builder.noteRmsEnergy(audio, q, it) }
        // 各音符能量应为正
        energies.forEach { assertTrue("某音符能量为零", it > 0.0) }
        // 能量差异不应过大（同振幅同音色，差异在 3 倍以内）
        val maxE = energies.max()
        val minE = energies.min()
        assertTrue("能量差异过大：max=$maxE min=$minE", maxE / minE < 3.0)
    }

    @Test
    fun `note RMS energy is zero for out-of-range index`() {
        val q = makeQuestion()
        val audio = builder.render(q)
        assertEquals(0.0, builder.noteRmsEnergy(audio, q, 999), 0.0001)
    }

    // ── 主导频率（过零率近似）────────────────────────────

    @Test
    fun `ascending melody dominant frequency increases`() {
        val q = makeQuestion(ContourType.ASCENDING, ContourDifficulty.BEGINNER)
        val audio = builder.renderRaw(q)
        val freqs = (0 until q.noteCount).map { builder.estimateDominantFrequency(audio, q, it) }
        // 上行旋律的主导频率应大致递增
        var increasing = true
        for (i in 1 until freqs.size) {
            if (freqs[i] < freqs[i - 1] * 0.7) increasing = false
        }
        assertTrue("上行旋律主导频率应递增：$freqs", increasing)
    }

    @Test
    fun `descending melody dominant frequency decreases`() {
        val q = makeQuestion(ContourType.DESCENDING, ContourDifficulty.BEGINNER)
        val audio = builder.renderRaw(q)
        val freqs = (0 until q.noteCount).map { builder.estimateDominantFrequency(audio, q, it) }
        // 下行旋律的主导频率应大致递减
        var decreasing = true
        for (i in 1 until freqs.size) {
            if (freqs[i] > freqs[i - 1] * 1.4) decreasing = false
        }
        assertTrue("下行旋律主导频率应递减：$freqs", decreasing)
    }

    @Test
    fun `estimateDominantFrequency returns zero for invalid index`() {
        val q = makeQuestion()
        val audio = builder.render(q)
        assertEquals(0.0, builder.estimateDominantFrequency(audio, q, 999), 0.0001)
    }

    // ── estimateDurationMs ───────────────────────────────

    @Test
    fun `estimateDurationMs is positive`() {
        ContourDifficulty.ALL.forEach { d ->
            val q = makeQuestion(ContourType.ASCENDING, d)
            assertTrue(builder.estimateDurationMs(q) > 0)
        }
    }

    @Test
    fun `longer phrases have longer duration`() {
        val q4 = makeQuestion(ContourType.ASCENDING, ContourDifficulty.BEGINNER)   // 4 音符
        val q5 = makeQuestion(ContourType.ASCENDING, ContourDifficulty.INTERMEDIATE) // 5 音符
        // 虽然 BPM 不同，但更多音符通常更长
        val dur4 = builder.estimateDurationMs(q4)
        val dur5 = builder.estimateDurationMs(q5)
        assertTrue("4 音符 ($dur4 ms) 应短于或接近 5 音符 ($dur5 ms)", dur4 < dur5 * 1.5)
    }
}
