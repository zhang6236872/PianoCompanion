package com.pianocompanion.rhythmdictation

import org.junit.Assert.*
import org.junit.Test

/**
 * 节奏听写音频构建器单元测试。
 */
class RhythmDictationAudioBuilderTest {

    private val builder = RhythmDictationAudioBuilder(sampleRate = 44100)

    // ── 基础渲染 ────────────────────────────────────────

    @Test
    fun `render 返回非空 PCM 缓冲区`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.BEGINNER)
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `renderCell 非空`() {
        val pcm = builder.renderCell(
            RhythmCellType.TWO_QUARTERS,
            RhythmDictationTempo.SLOW,
            repeatCount = 1
        )
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `renderCell 空重复返回空数组`() {
        // 虽然正常不会传 0，但验证边界安全
        val pcm = builder.renderCell(
            RhythmCellType.HALF_NOTE,
            RhythmDictationTempo.SLOW,
            repeatCount = 0
        )
        // repeatCount=0 → 无 onset → 空
        assertEquals(0, pcm.size)
    }

    // ── PCM 范围 ────────────────────────────────────────

    @Test
    fun `render 所有采样在 -1 到 1 范围内`() {
        for (cell in RhythmCellType.ALL) {
            val pcm = builder.renderCell(cell, RhythmDictationTempo.MEDIUM, 2)
            for (sample in pcm) {
                assertTrue("采样 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `renderCell 所有采样在范围内`() {
        val pcm = builder.renderCell(RhythmCellType.FOUR_EIGHTHS, RhythmDictationTempo.FAST, 3)
        for (s in pcm) {
            assertTrue(s in -1.0f..1.0f)
        }
    }

    // ── 时长正确性 ──────────────────────────────────────

    @Test
    fun `PCM 长度包含前导静音+节奏+尾部静音`() {
        val tempo = RhythmDictationTempo.SLOW // 72 BPM
        val beatMs = tempo.beatMs
        val cell = RhythmCellType.TWO_QUARTERS // 2 拍，最后 onset 在第 1 拍
        val repeatCount = 1
        val pcm = builder.renderCell(cell, tempo, repeatCount)
        // TWO_QUARTERS 的最后 onset 位于 LEAD_SILENCE + beatMs
        // PCM 长度 = lastOnsetSample + clickSamples + tailSamples
        val sr = 44100
        val lastOnsetMs = RhythmDictationAudioBuilder.LEAD_SILENCE_MS + beatMs
        val expectedMinSamples = (lastOnsetMs * sr / 1000.0).toInt()
        assertTrue(
            "pcm.size=${pcm.size} 应 > lastOnsetSamples=$expectedMinSamples",
            pcm.size > expectedMinSamples
        )
    }

    @Test
    fun `重复2次比重复1次更长`() {
        val pcm1 = builder.renderCell(RhythmCellType.QUARTER_EIGHTHS, RhythmDictationTempo.MEDIUM, 1)
        val pcm2 = builder.renderCell(RhythmCellType.QUARTER_EIGHTHS, RhythmDictationTempo.MEDIUM, 2)
        assertTrue("重复2次应该更长: ${pcm2.size} > ${pcm1.size}", pcm2.size > pcm1.size)
    }

    // ── 不同节奏产生不同音频 ────────────────────────────

    @Test
    fun `不同节奏单元产生不同音频`() {
        val pcm1 = builder.renderCell(RhythmCellType.TWO_QUARTERS, RhythmDictationTempo.SLOW, 1)
        val pcm2 = builder.renderCell(RhythmCellType.FOUR_EIGHTHS, RhythmDictationTempo.SLOW, 1)
        // 不同节奏至少在某处不同
        assertNotEquals(pcm1.size, pcm2.size) // 4 eighths 比 2 quarters 更多 onset → 更长？
        // 实际上两者总拍数相同(2拍), onset 数不同但总长接近
        // 用内容差异验证
        var foundDiff = false
        val minLen = minOf(pcm1.size, pcm2.size)
        for (i in 0 until minLen) {
            if (kotlin.math.abs(pcm1[i] - pcm2[i]) > 0.01f) {
                foundDiff = true
                break
            }
        }
        assertTrue(foundDiff)
    }

    @Test
    fun `不同速度产生不同音频长度`() {
        val pcmSlow = builder.renderCell(RhythmCellType.TWO_QUARTERS, RhythmDictationTempo.SLOW, 1)
        val pcmFast = builder.renderCell(RhythmCellType.TWO_QUARTERS, RhythmDictationTempo.FAST, 1)
        assertNotEquals(pcmSlow.size, pcmFast.size)
    }

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同参数渲染结果一致`() {
        val pcm1 = builder.renderCell(RhythmCellType.DOTTED_QUARTER_EIGHTH, RhythmDictationTempo.MEDIUM, 2)
        val pcm2 = builder.renderCell(RhythmCellType.DOTTED_QUARTER_EIGHTH, RhythmDictationTempo.MEDIUM, 2)
        assertTrue(pcm1.contentEquals(pcm2))
    }

    // ── computeOnsetTimes 一致性 ────────────────────────

    @Test
    fun `computeOnsetTimes 与引擎一致`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val cell = RhythmCellType.SYNCOPATED
        val tempo = RhythmDictationTempo.MEDIUM
        val onsetsEngine = engine.computeOnsetTimes(cell, tempo, 2)
        val onsetsBuilder = builder.computeOnsetTimes(cell, tempo, 2)
        assertEquals(onsetsEngine, onsetsBuilder)
    }

    @Test
    fun `onset 数量等于 noteCount 乘以 repeatCount`() {
        val cell = RhythmCellType.EIGHTHS_QUARTER // 3 notes
        val onsets = builder.computeOnsetTimes(cell, RhythmDictationTempo.SLOW, 3)
        assertEquals(3 * 3, onsets.size)
    }

    // ── 能量/峰值 ───────────────────────────────────────

    @Test
    fun `PCM 有非零能量`() {
        val pcm = builder.renderCell(RhythmCellType.TWO_QUARTERS, RhythmDictationTempo.SLOW, 1)
        val energy = pcm.sumOf { (it.toDouble() * it.toDouble()) }
        assertTrue("能量为零", energy > 0.0)
    }

    @Test
    fun `PCM 有前导静音段`() {
        val pcm = builder.renderCell(RhythmCellType.TWO_QUARTERS, RhythmDictationTempo.SLOW, 1)
        val sr = 44100
        val leadSamples = (RhythmDictationAudioBuilder.LEAD_SILENCE_MS * sr / 1000).toInt()
        // 前导静音区的采样应接近 0（onset 之前无信号）
        val leadEnergy = (0 until leadSamples).sumOf { (pcm[it].toDouble() * pcm[it].toDouble()) }
        assertEquals(0.0, leadEnergy, 1e-6)
    }

    @Test
    fun `PCM 有尾部静音段`() {
        val pcm = builder.renderCell(RhythmCellType.HALF_NOTE, RhythmDictationTempo.SLOW, 1)
        val sr = 44100
        val tailSamples = (RhythmDictationAudioBuilder.TAIL_SILENCE_MS * sr / 1000).toInt()
        // 最后 tailSamples 个采样应接近 0
        val start = pcm.size - tailSamples
        val tailEnergy = (start until pcm.size).sumOf { (pcm[it].toDouble() * pcm[it].toDouble()) }
        assertTrue("尾部能量过高: $tailEnergy", tailEnergy < 1e-4)
    }

    // ── estimateDurationMs ─────────────────────────────

    @Test
    fun `estimateDurationMs 为正数`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.BEGINNER)
        val ms = builder.estimateDurationMs(q)
        assertTrue(ms > 0)
    }

    // ── 常量验证 ────────────────────────────────────────

    @Test
    fun `常量值合理`() {
        assertEquals(44100, RhythmDictationAudioBuilder.DEFAULT_SAMPLE_RATE)
        assertTrue(RhythmDictationAudioBuilder.LEAD_SILENCE_MS > 0)
        assertTrue(RhythmDictationAudioBuilder.TAIL_SILENCE_MS > 0)
        assertTrue(RhythmDictationAudioBuilder.CLICK_FREQ > 0)
        assertTrue(RhythmDictationAudioBuilder.CLICK_AMP > 0 && RhythmDictationAudioBuilder.CLICK_AMP <= 1.0)
    }
}
