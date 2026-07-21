package com.pianocompanion.voiceentryorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声部进入顺序辨识训练音频合成器单元测试。
 *
 * 覆盖 [VoiceEntryAudioBuilder] 的事件构建、进入时间错开、频率换算与渲染输出。
 */
class VoiceEntryAudioBuilderTest {

    private val builder = VoiceEntryAudioBuilder()
    private val engine = VoiceEntryEngine.withSeed(42)

    private fun makeQuestion(difficulty: EntryDifficulty = EntryDifficulty.ADVANCED): EntryOrderQuestion =
        engine.generate(difficulty)

    // ── 基本输出 ──────────────────────────────────

    @Test
    fun `render returns non-empty float array`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        assertTrue("PCM must be non-empty", pcm.isNotEmpty())
    }

    @Test
    fun `render output is FloatArray`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        assertTrue("PCM must be FloatArray", pcm is FloatArray)
    }

    @Test
    fun `render is deterministic for same question`() {
        val q = makeQuestion()
        val pcm1 = builder.render(q)
        val pcm2 = builder.render(q)
        assertEquals(pcm1.size, pcm2.size)
        pcm1.indices.forEach { i ->
            assertEquals("Sample $i differs", pcm1[i], pcm2[i], 0.0f)
        }
    }

    @Test
    fun `render values are within normalized range`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        // tanh 限幅后应在 [-1, 1]
        assertTrue(pcm.all { it in -1.0f..1.0f })
    }

    // ── 音符事件构建 ──────────────────────────────────

    @Test
    fun `buildNoteEvents count equals voiceCount times notesPerVoice`() {
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = makeQuestion(difficulty)
            val events = builder.buildNoteEvents(q)
            assertEquals(
                "Difficulty ${difficulty.displayName} should have ${difficulty.voiceCount}×${difficulty.notesPerVoice} events",
                difficulty.voiceCount * difficulty.notesPerVoice,
                events.size
            )
        }
    }

    @Test
    fun `note events duration matches difficulty`() {
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = makeQuestion(difficulty)
            val events = builder.buildNoteEvents(q)
            assertTrue(events.all { it.durationMs == difficulty.noteDurationMs.toDouble() })
        }
    }

    @Test
    fun `note events use correct midi from register motif`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        val notesPerVoice = q.difficulty.notesPerVoice
        // 按声部分组：第 k 组的 notesPerVoice 个事件属于 entryOrder[k]
        q.entryOrder.forEachIndexed { voiceIdx, register ->
            val base = voiceIdx * notesPerVoice
            for (j in 0 until notesPerVoice) {
                val expectedMidi = register.motif[j % register.motif.size]
                assertEquals(
                    "Voice $voiceIdx ($register) note $j midi mismatch",
                    expectedMidi,
                    events[base + j].midi
                )
                assertEquals(register, events[base + j].register)
            }
        }
    }

    @Test
    fun `each voice first note onset matches its entry onset`() {
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = makeQuestion(difficulty)
            val events = builder.buildNoteEvents(q)
            val notesPerVoice = difficulty.notesPerVoice
            q.entryOrder.forEachIndexed { voiceIdx, _ ->
                val expectedOnset = voiceIdx * difficulty.entryGapMs.toDouble()
                val actualOnset = events[voiceIdx * notesPerVoice].onsetMs
                assertEquals(
                    "Voice $voiceIdx first-note onset should equal entry time",
                    expectedOnset,
                    actualOnset,
                    0.001
                )
            }
        }
    }

    @Test
    fun `within-voice note spacing equals noteDuration plus gap`() {
        val q = makeQuestion()
        val events = builder.buildNoteEvents(q)
        val notesPerVoice = q.difficulty.notesPerVoice
        val expectedStep = q.difficulty.noteDurationMs.toDouble() + VoiceEntryAudioBuilder.GAP_MS
        q.entryOrder.forEachIndexed { voiceIdx, _ ->
            val base = voiceIdx * notesPerVoice
            for (j in 1 until notesPerVoice) {
                val delta = events[base + j].onsetMs - events[base + j - 1].onsetMs
                assertEquals(
                    "Voice $voiceIdx note $j spacing",
                    expectedStep,
                    delta,
                    0.001
                )
            }
        }
    }

    // ── 进入时间（声部先后） ──────────────────────────────────

    @Test
    fun `entryOnsetsMs returns one per voice`() {
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = makeQuestion(difficulty)
            val onsets = builder.entryOnsetsMs(q)
            assertEquals(difficulty.voiceCount, onsets.size)
        }
    }

    @Test
    fun `first entry onset is zero`() {
        val q = makeQuestion()
        val onsets = builder.entryOnsetsMs(q)
        assertEquals(0.0, onsets[0].second, 0.001)
    }

    @Test
    fun `entry onsets are monotonically increasing`() {
        val q = makeQuestion()
        val onsets = builder.entryOnsetsMs(q)
        for (i in 1 until onsets.size) {
            assertTrue(
                "Entry onset $i must be after onset ${i - 1}",
                onsets[i].second > onsets[i - 1].second
            )
        }
    }

    @Test
    fun `entry onset spacing equals entryGapMs`() {
        val q = makeQuestion()
        val onsets = builder.entryOnsetsMs(q)
        val expected = q.difficulty.entryGapMs.toDouble()
        for (i in 1 until onsets.size) {
            assertEquals(
                "Entry onset $i spacing",
                expected,
                onsets[i].second - onsets[i - 1].second,
                0.001
            )
        }
    }

    @Test
    fun `entryOnsetsMs registers match entry order`() {
        val q = makeQuestion()
        val onsets = builder.entryOnsetsMs(q)
        q.entryOrder.forEachIndexed { i, register ->
            assertEquals(register, onsets[i].first)
        }
    }

    // ── 频率换算 ──────────────────────────────────

    @Test
    fun `midiToFreq A4 equals 440`() {
        assertEquals(440.0, builder.midiToFreq(69), 0.01)
    }

    @Test
    fun `midiToFreq A5 is double A4`() {
        assertEquals(880.0, builder.midiToFreq(81), 0.01)
    }

    @Test
    fun `midiToFreq A3 is half A4`() {
        assertEquals(220.0, builder.midiToFreq(57), 0.01)
    }

    @Test
    fun `midiToFreq is monotonically increasing`() {
        var prev = builder.midiToFreq(36)
        for (midi in 37..96) {
            val freq = builder.midiToFreq(midi)
            assertTrue("Freq should increase for midi $midi", freq > prev)
            prev = freq
        }
    }

    // ── 时长估算 ──────────────────────────────────

    @Test
    fun `estimateDurationMs is positive`() {
        val q = makeQuestion()
        assertTrue(builder.estimateDurationMs(q) > 0)
    }

    @Test
    fun `estimateDurationMs includes lead and tail silence`() {
        val q = makeQuestion()
        val estimated = builder.estimateDurationMs(q)
        // 至少包含前导 + 尾部静音
        assertTrue(
            "Estimated ${estimated}ms should exceed silence padding",
            estimated > (VoiceEntryAudioBuilder.LEAD_SILENCE_MS + VoiceEntryAudioBuilder.TAIL_SILENCE_MS).toLong()
        )
    }

    @Test
    fun `musicDurationMs matches computed formula`() {
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = makeQuestion(difficulty)
            val d = difficulty
            // 最后一进入声部的最后一个音符结束时间
            val expected = (d.voiceCount - 1) * d.entryGapMs.toDouble() +
                (d.notesPerVoice - 1) * (d.noteDurationMs.toDouble() + VoiceEntryAudioBuilder.GAP_MS) +
                d.noteDurationMs.toDouble()
            assertEquals(
                "musicDurationMs for ${d.displayName}",
                expected,
                builder.musicDurationMs(q),
                0.001
            )
        }
    }

    @Test
    fun `beginner estimates longer than advanced`() {
        // beginner: entryGapMs=700, noteDurationMs=420（更慢）→ 总时长更长
        val beginner = makeQuestion(EntryDifficulty.BEGINNER)
        val advanced = makeQuestion(EntryDifficulty.ADVANCED)
        val bDur = builder.estimateDurationMs(beginner)
        val aDur = builder.estimateDurationMs(advanced)
        assertTrue("Slower difficulty should estimate longer duration (b=$bDur, a=$aDur)", bDur > aDur)
    }

    // ── 渲染输出特性 ──────────────────────────────────

    @Test
    fun `render produces dynamic range`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        val max = pcm.maxOrNull() ?: 0f
        val min = pcm.minOrNull() ?: 0f
        assertTrue("Should have dynamic range", max - min > 0.1f)
    }

    @Test
    fun `render has silence at start`() {
        val q = makeQuestion()
        val pcm = builder.render(q)
        val leadSamples = (VoiceEntryAudioBuilder.DEFAULT_SAMPLE_RATE * VoiceEntryAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前导静音区域应接近 0
        assertTrue("Lead silence region should be near-zero", pcm[0] < 0.01f)
        assertTrue("Lead silence region should be near-zero", pcm[leadSamples / 2] < 0.01f)
    }

    @Test
    fun `render different questions produce different output`() {
        val q1 = engine.generate(EntryDifficulty.ADVANCED)
        val q2 = engine.generate(EntryDifficulty.ADVANCED)
        val pcm1 = builder.render(q1)
        val pcm2 = builder.render(q2)
        // 不同题（种子推进 / 进入顺序不同）应产生不同波形或不同长度
        var anyDiff = false
        val minLen = minOf(pcm1.size, pcm2.size)
        for (i in 0 until minLen) {
            if (pcm1[i] != pcm2[i]) {
                anyDiff = true
                break
            }
        }
        assertTrue("Different questions should produce different PCM", anyDiff || pcm1.size != pcm2.size)
    }

    @Test
    fun `renderEvents of empty list returns empty array`() {
        val pcm = builder.renderEvents(emptyList())
        assertEquals(0, pcm.size)
    }

    @Test
    fun `all difficulties render without error`() {
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = makeQuestion(difficulty)
            val pcm = builder.render(q)
            assertTrue("PCM for ${difficulty.displayName} must be non-empty", pcm.isNotEmpty())
        }
    }

    @Test
    fun `render respects custom sample rate`() {
        val q = makeQuestion()
        val lowRate = VoiceEntryAudioBuilder(sampleRate = 22050)
        val pcm = lowRate.render(q)
        // 低采样率应产生更少采样点
        val highRate = VoiceEntryAudioBuilder(sampleRate = 44100)
        val pcmHigh = highRate.render(q)
        assertTrue(
            "Lower sample rate should produce fewer samples (${pcm.size} vs ${pcmHigh.size})",
            pcm.size < pcmHigh.size
        )
    }
}
