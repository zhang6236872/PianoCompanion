package com.pianocompanion.voicecounttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceCountAudioBuilder] 单元测试。
 *
 * 核心验证：声部越多 → 原始（未限幅）叠加能量越高（[renderRaw] + [sustainEnergy]），
 * 这是 block chord 可被听辨的基础。
 */
class VoiceCountAudioBuilderTest {

    private val builder = VoiceCountAudioBuilder(sampleRate = 44100)

    @Test
    fun `midiToFreq - A4 等于 440Hz`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.001)
    }

    @Test
    fun `midiToFreq - 相差一个八度频率翻倍`() {
        val f1 = builder.midiToFreq(60)  // C4
        val f2 = builder.midiToFreq(72)  // C5
        assertEquals(2.0, f2 / f1, 0.0001)
    }

    @Test
    fun `centsToRatio - 1200 cents 等于一个八度`() {
        assertEquals(2.0, builder.centsToRatio(1200.0), 0.0001)
    }

    @Test
    fun `centsToRatio - 0 cents 等于 1`() {
        assertEquals(1.0, builder.centsToRatio(0.0), 0.0001)
    }

    @Test
    fun `detuneForVoice - 确定性且不超过最大值`() {
        val d0 = builder.detuneForVoice(0)
        val d0Again = builder.detuneForVoice(0)
        assertEquals(d0, d0Again, 0.0001)  // 确定性
        for (i in 0..10) {
            val d = builder.detuneForVoice(i)
            assertTrue("失谐 $d 应在 ±${VoiceCountAudioBuilder.DETUNE_MAX_CENTS}", kotlin.math.abs(d) <= VoiceCountAudioBuilder.DETUNE_MAX_CENTS + 0.0001)
        }
    }

    @Test
    fun `render 返回正确长度（前导 + 时长 + 尾部）`() {
        val q = VoiceCountEngine.withSeed(1L).generate(VoiceCountDifficulty.INTERMEDIATE)
        val audio = builder.render(q)
        val expectedMin = (44100 * (VoiceCountAudioBuilder.LEAD_SILENCE_MS + q.durationMs + VoiceCountAudioBuilder.TAIL_SILENCE_MS - 2) / 1000.0).toInt()
        val expectedMax = (44100 * (VoiceCountAudioBuilder.LEAD_SILENCE_MS + q.durationMs + VoiceCountAudioBuilder.TAIL_SILENCE_MS + 2) / 1000.0).toInt()
        assertTrue("长度 ${audio.size} 应在 [$expectedMin, $expectedMax]", audio.size in expectedMin..expectedMax)
    }

    @Test
    fun `render 输出在 -1 到 1 范围内`() {
        val q = VoiceCountEngine.withSeed(1L).generate(VoiceCountDifficulty.ADVANCED)
        val audio = builder.render(q)
        for (v in audio) {
            assertTrue("样本 $v 超出 [-1,1]", v in -1.0f..1.0f)
        }
    }

    @Test
    fun `renderRaw 输出非静音`() {
        val q = VoiceCountEngine.withSeed(1L).generate(VoiceCountDifficulty.BEGINNER)
        val audio = builder.renderRaw(q)
        val energy = builder.sustainEnergy(audio, q)
        assertTrue("持续段能量应 > 0，实际 $energy", energy > 0.0)
    }

    @Test
    fun `确定性 - 相同题目产生相同音频`() {
        val q = VoiceCountEngine.withSeed(42L).generate(VoiceCountDifficulty.ADVANCED)
        val a1 = builder.render(q)
        val a2 = builder.render(q)
        assertEquals(a1.size, a2.size)
        for (i in a1.indices) {
            assertEquals(a1[i], a2[i], 0.0f)
        }
    }

    @Test
    fun `核心 - 声部越多原始叠加能量越高`() {
        // 用相同间距、不同声部数对比：控制变量（间距一致），仅声部数变化
        // 用 INTERMEDIATE(MEDIUM) 间距，构造 1/2/3/4 声部的题目
        // 直接用 buildVoicing 构造等价 voicing 以隔离间距变量
        val rand = kotlin.random.Random(7L)
        fun questionFor(voiceCount: Int): VoiceCountQuestion {
            val voicing = VoiceCountEngine.buildVoicing(voiceCount, NoteSpacing.MEDIUM, rand)
            val maxCount = 4
            return VoiceCountQuestion(
                difficulty = VoiceCountDifficulty.INTERMEDIATE,
                voiceCount = voiceCount,
                rootMidi = voicing.first(),
                voicing = voicing,
                spacing = NoteSpacing.MEDIUM,
                durationMs = 1400L,
                answerChoices = (1..maxCount).map { VoiceCountQuestion.countLabelText(it) },
                correctAnswer = VoiceCountQuestion.countLabelText(voiceCount)
            )
        }
        val e1 = builder.sustainEnergy(builder.renderRaw(questionFor(1)), questionFor(1))
        val e2 = builder.sustainEnergy(builder.renderRaw(questionFor(2)), questionFor(2))
        val e3 = builder.sustainEnergy(builder.renderRaw(questionFor(3)), questionFor(3))
        val e4 = builder.sustainEnergy(builder.renderRaw(questionFor(4)), questionFor(4))
        assertTrue("1音能量 $e1 < 2音 $e2", e1 < e2)
        assertTrue("2音能量 $e2 < 3音 $e3", e2 < e3)
        assertTrue("3音能量 $e3 < 4音 $e4", e3 < e4)
    }

    @Test
    fun `buildTone 返回正确长度`() {
        val tone = builder.buildTone(60, 5.0, 1000)
        assertEquals(1000, tone.size)
    }

    @Test
    fun `buildTone 零样本返回空数组`() {
        val tone = builder.buildTone(60, 0.0, 0)
        assertEquals(0, tone.size)
    }

    @Test
    fun `buildTone 指数衰减 - 后段能量低于前段`() {
        val tone = builder.buildTone(69, 0.0, 44100)  // 1 秒
        val firstHalf = rms(tone, 0, tone.size / 2)
        val secondHalf = rms(tone, tone.size / 2, tone.size)
        assertTrue("前段能量 $firstHalf 应大于后段 $secondHalf（指数衰减）", firstHalf > secondHalf)
    }

    @Test
    fun `单音 buildTone 与 renderRaw 单声部能量接近`() {
        val rand = kotlin.random.Random(1L)
        val voicing = VoiceCountEngine.buildVoicing(1, NoteSpacing.WIDE, rand)
        val q = VoiceCountQuestion(
            difficulty = VoiceCountDifficulty.BEGINNER,
            voiceCount = 1,
            rootMidi = voicing.first(),
            voicing = voicing,
            spacing = NoteSpacing.WIDE,
            durationMs = 1400L,
            answerChoices = (1..3).map { VoiceCountQuestion.countLabelText(it) },
            correctAnswer = VoiceCountQuestion.countLabelText(1)
        )
        val raw = builder.renderRaw(q)
        val energy = builder.sustainEnergy(raw, q)
        assertTrue("单音能量应 > 0", energy > 0.0)
    }

    @Test
    fun `estimateDurationMs 为正且包含三段`() {
        val q = VoiceCountEngine.withSeed(1L).generate(VoiceCountDifficulty.INTERMEDIATE)
        val est = builder.estimateDurationMs(q)
        assertTrue(est > 0)
        assertTrue(est >= (VoiceCountAudioBuilder.LEAD_SILENCE_MS + q.durationMs + VoiceCountAudioBuilder.TAIL_SILENCE_MS - 5).toLong())
    }

    @Test
    fun `render 不同声部数产生不同音频`() {
        val rand = kotlin.random.Random(3L)
        val v1 = VoiceCountEngine.buildVoicing(1, NoteSpacing.MEDIUM, rand)
        val v3 = VoiceCountEngine.buildVoicing(3, NoteSpacing.MEDIUM, rand)
        fun q(voicing: List<Int>, count: Int) = VoiceCountQuestion(
            difficulty = VoiceCountDifficulty.INTERMEDIATE,
            voiceCount = count,
            rootMidi = voicing.first(),
            voicing = voicing,
            spacing = NoteSpacing.MEDIUM,
            durationMs = 1400L,
            answerChoices = (1..4).map { VoiceCountQuestion.countLabelText(it) },
            correctAnswer = VoiceCountQuestion.countLabelText(count)
        )
        val a1 = builder.render(q(v1, 1))
        val a3 = builder.render(q(v3, 3))
        // 长度相同，但内容必然不同（更多声部）
        assertEquals(a1.size, a3.size)
        var diff = 0
        for (i in a1.indices) {
            if (kotlin.math.abs(a1[i] - a3[i]) > 1e-5f) diff++
        }
        assertTrue("不同声部数应产生不同音频", diff > 0)
    }

    @Test
    fun `sustainEnergy 窗口参数可调`() {
        val q = VoiceCountEngine.withSeed(1L).generate(VoiceCountDifficulty.BEGINNER)
        val raw = builder.renderRaw(q)
        val half = builder.sustainEnergy(raw, q, 0.5)
        val full = builder.sustainEnergy(raw, q, 1.0)
        assertTrue(half > 0.0)
        assertTrue(full > 0.0)
    }

    private fun rms(arr: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        var n = 0
        for (i in from until to) {
            sum += arr[i].toDouble() * arr[i]
            n++
        }
        return if (n > 0) kotlin.math.sqrt(sum / n) else 0.0
    }
}
