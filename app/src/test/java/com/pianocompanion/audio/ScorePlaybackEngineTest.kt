package com.pianocompanion.audio

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScorePlaybackEngine] 单元测试。
 */
class ScorePlaybackEngineTest {

    private val engine = ScorePlaybackEngine(sampleRate = 44100)

    private fun makeNote(
        midi: Int,
        startTime: Long,
        duration: Long,
        velocity: Int = 64,
        staff: Staff = Staff.TREBLE
    ): ScoreNote {
        return ScoreNote(
            midiNumber = midi,
            noteName = "C4",
            startTime = startTime,
            duration = duration,
            velocity = velocity,
            staff = staff
        )
    }

    private fun makeScore(notes: List<ScoreNote>, tempo: Int = 120): Score {
        return Score(
            id = "test",
            title = "Test",
            composer = "Test",
            notes = notes,
            tempo = tempo,
            source = ScoreSource.GENERATED
        )
    }

    // ===== 空乐谱测试 =====

    @Test
    fun `空乐谱返回空缓冲区`() {
        val score = makeScore(emptyList())
        val buffer = engine.render(score)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `空乐谱时长为0`() {
        val score = makeScore(emptyList())
        assertEquals(0L, engine.totalDurationMs(score))
    }

    // ===== 单音符测试 =====

    @Test
    fun `单音符产生正确缓冲区长度`() {
        val score = makeScore(listOf(makeNote(60, 0, 500)))
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        // 500ms × 44100Hz / 1000 = 22050 个样本
        assertEquals(22050, buffer.size)
    }

    @Test
    fun `单音符缓冲区有非零内容`() {
        val score = makeScore(listOf(makeNote(60, 0, 500)))
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("缓冲区应有音频内容，峰值 $peak", peak > 0.01f)
    }

    @Test
    fun `开头静音时间正确偏移`() {
        val score = makeScore(listOf(makeNote(60, 0, 500)))
        val buffer = engine.render(score, leadSilenceMs = 200, trailSilenceMs = 0)
        // 前 200ms 应接近静音（静音区 + 起音）
        val silenceEnd = engine.msToSamples(200)
        val silencePeak = buffer.copyOfRange(0, silenceEnd).maxOf { kotlin.math.abs(it) }
        assertTrue("前导静音区峰值 $silencePeak 应接近0", silencePeak < 0.01f)
    }

    // ===== 多音符测试 =====

    @Test
    fun `两个不重叠音符缓冲区长度正确`() {
        val notes = listOf(
            makeNote(60, 0, 500),     // 0-500ms
            makeNote(62, 500, 500)    // 500-1000ms
        )
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        // 总时长 1000ms
        assertEquals(44100, buffer.size)
    }

    @Test
    fun `两音符间有振幅谷`() {
        val notes = listOf(
            makeNote(60, 0, 200),      // 0-200ms
            makeNote(62, 400, 200)     // 400-600ms (有间隙)
        )
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        // 间隙在 200-400ms，采样 8820-17640
        val gapPeak = buffer.copyOfRange(12000, 15000).maxOf { kotlin.math.abs(it) }
        assertTrue("间隙区振幅 $gapPeak 应很小", gapPeak < 0.01f)
    }

    // ===== 和弦测试 =====

    @Test
    fun `和弦（重叠音符）正确混合`() {
        val notes = listOf(
            makeNote(60, 0, 500),  // C4
            makeNote(64, 0, 500),  // E4
            makeNote(67, 0, 500)   // G4
        )
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)

        // 和弦应比单音符振幅大
        val singleScore = makeScore(listOf(makeNote(60, 0, 500)))
        val singleBuffer = engine.render(singleScore, leadSilenceMs = 0, trailSilenceMs = 0)

        val chordPeak = buffer.copyOfRange(2000, 3000).maxOf { kotlin.math.abs(it) }
        val singlePeak = singleBuffer.copyOfRange(2000, 3000).maxOf { kotlin.math.abs(it) }
        assertTrue(
            "和弦峰值 $chordPeak 应大于单音符 $singlePeak",
            chordPeak > singlePeak
        )
    }

    @Test
    fun `和弦不削波（软限幅保证范围内）`() {
        val notes = listOf(
            makeNote(48, 0, 1000, velocity = 120),
            makeNote(52, 0, 1000, velocity = 120),
            makeNote(55, 0, 1000, velocity = 120),
            makeNote(60, 0, 1000, velocity = 120),
            makeNote(64, 0, 1000, velocity = 120)
        )
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        buffer.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1, 1]", sample in -1f..1f)
        }
    }

    // ===== 力度测试 =====

    @Test
    fun `力度影响振幅`() {
        val loudScore = makeScore(listOf(makeNote(60, 0, 500, velocity = 120)))
        val softScore = makeScore(listOf(makeNote(60, 0, 500, velocity = 30)))
        val loudBuffer = engine.render(loudScore, leadSilenceMs = 0, trailSilenceMs = 0)
        val softBuffer = engine.render(softScore, leadSilenceMs = 0, trailSilenceMs = 0)

        val loudPeak = loudBuffer.copyOfRange(1000, 3000).maxOf { kotlin.math.abs(it) }
        val softPeak = softBuffer.copyOfRange(1000, 3000).maxOf { kotlin.math.abs(it) }
        assertTrue(
            "高力度 $loudPeak 应大于低力度 $softPeak",
            loudPeak > softPeak
        )
    }

    // ===== 速度控制测试 =====

    @Test
    fun `低速播放缓冲区更长`() {
        val notes = listOf(makeNote(60, 0, 500), makeNote(62, 500, 500))
        val score = makeScore(notes, tempo = 120)

        val fastBuffer = engine.render(score, tempoBpm = 240, leadSilenceMs = 0, trailSilenceMs = 0)
        val slowBuffer = engine.render(score, tempoBpm = 60, leadSilenceMs = 0, trailSilenceMs = 0)

        assertTrue(
            "低速缓冲区 ${slowBuffer.size} 应长于高速 ${fastBuffer.size}",
            slowBuffer.size > fastBuffer.size
        )
    }

    @Test
    fun `使用乐谱默认速度时缓冲区长度正确`() {
        val notes = listOf(makeNote(60, 0, 500))
        val score = makeScore(notes, tempo = 120)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        // tempoBpm = null → 使用乐谱速度 → tempoScale = 1.0
        assertEquals(22050, buffer.size)
    }

    // ===== 多声部测试 =====

    @Test
    fun `treble 和 bass 同时混合`() {
        val notes = listOf(
            makeNote(60, 0, 500, staff = Staff.TREBLE),
            makeNote(48, 0, 500, staff = Staff.BASS)
        )
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        assertEquals(22050, buffer.size)
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("混合应有音频内容", peak > 0.01f)
    }

    // ===== 无效音符测试 =====

    @Test
    fun `力度为0的音符不发声`() {
        val notes = listOf(makeNote(60, 0, 500, velocity = 0))
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        // 缓冲区有长度（因为分配了空间），但内容应接近0
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("力度0音符峰值 $peak 应接近0", peak < 0.001f)
    }

    @Test
    fun `midiNumber为0的音符不发声`() {
        val notes = listOf(makeNote(0, 0, 500))
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)
        val peak = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("无效音符峰值 $peak 应接近0", peak < 0.001f)
    }

    // ===== 时长计算测试 =====

    @Test
    fun `总时长正确`() {
        val notes = listOf(
            makeNote(60, 0, 500),
            makeNote(62, 1000, 300)  // 最后结束于 1300ms
        )
        val score = makeScore(notes)
        assertEquals(1300L, engine.totalDurationMs(score))
    }

    // ===== 工具方法测试 =====

    @Test
    fun `msToSamples 转换正确`() {
        assertEquals(44100, engine.msToSamples(1000))
        assertEquals(22050, engine.msToSamples(500))
        assertEquals(0, engine.msToSamples(0))
    }

    @Test
    fun `samplesToMs 转换正确`() {
        assertEquals(1000L, engine.samplesToMs(44100))
        assertEquals(500L, engine.samplesToMs(22050))
        assertEquals(0L, engine.samplesToMs(0))
    }

    // ===== 结尾静音测试 =====

    @Test
    fun `结尾静音正确添加`() {
        val score = makeScore(listOf(makeNote(60, 0, 500)))
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 500)
        // 500ms 音符 + 500ms 结尾静音 = 1000ms = 44100 样本
        assertEquals(44100, buffer.size)
    }

    // ===== 多音符时间轴正确性 =====

    @Test
    fun `三音符按时间顺序排列`() {
        val notes = listOf(
            makeNote(72, 0, 200),     // 0-200ms, 高音
            makeNote(60, 250, 200),   // 250-450ms, 中音
            makeNote(48, 500, 200)    // 500-700ms, 低音
        )
        val score = makeScore(notes)
        val buffer = engine.render(score, leadSilenceMs = 0, trailSilenceMs = 0)

        // 三个时间段各有内容
        val seg1 = buffer.copyOfRange(engine.msToSamples(100), engine.msToSamples(150))
        val seg2 = buffer.copyOfRange(engine.msToSamples(350), engine.msToSamples(400))
        val seg3 = buffer.copyOfRange(engine.msToSamples(600), engine.msToSamples(650))

        assertTrue("第一段应有内容", seg1.maxOf { kotlin.math.abs(it) } > 0.01f)
        assertTrue("第二段应有内容", seg2.maxOf { kotlin.math.abs(it) } > 0.01f)
        assertTrue("第三段应有内容", seg3.maxOf { kotlin.math.abs(it) } > 0.01f)

        // 间隙应为静音
        val gap = buffer.copyOfRange(engine.msToSamples(220), engine.msToSamples(240))
        assertTrue("间隙应静音", gap.maxOf { kotlin.math.abs(it) } < 0.01f)
    }
}
