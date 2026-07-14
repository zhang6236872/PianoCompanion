package com.pianocompanion.melodicdirectiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 旋律方向辨识训练音频构建器单元测试。
 */
class MelodicDirectionAudioBuilderTest {

    private val builder = MelodicDirectionAudioBuilder()

    // ── 基础渲染 ────────────────────────────────────────

    @Test
    fun `渲染所有方向返回非空缓冲区`() {
        for (direction in MelodicDirection.ALL) {
            val pcm = builder.renderDirection(direction)
            assertTrue("${direction.name} 缓冲区为空", pcm.isNotEmpty())
        }
    }

    @Test
    fun `渲染缓冲区采样值在有效范围内`() {
        for (direction in MelodicDirection.ALL) {
            val pcm = builder.renderDirection(direction)
            for (sample in pcm) {
                assertTrue(sample >= -1.0f && sample <= 1.0f)
            }
        }
    }

    @Test
    fun `渲染缓冲区包含非零采样`() {
        for (direction in MelodicDirection.ALL) {
            val pcm = builder.renderDirection(direction)
            val nonZeroCount = pcm.count { kotlin.math.abs(it) > 0.001f }
            assertTrue("${direction.name} 应有非零采样", nonZeroCount > 100)
        }
    }

    // ── 缓冲区长度 ──────────────────────────────────────

    @Test
    fun `缓冲区长度包含前导+音符+尾部`() {
        val expectedSamples = (
            MelodicDirectionAudioBuilder.LEAD_SILENCE_SAMPLES +
            4 * (44100 * MelodicDirectionAudioBuilder.NOTE_DURATION_MS / 1000.0).toInt() +
            (44100 * MelodicDirectionAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        )
        for (direction in MelodicDirection.ALL) {
            val pcm = builder.renderDirection(direction)
            assertEquals(expectedSamples, pcm.size)
        }
    }

    @Test
    fun `所有方向缓冲区长度一致`() {
        val sizes = MelodicDirection.ALL.map { builder.renderDirection(it).size }
        assertEquals(1, sizes.toSet().size)
    }

    // ── 静音段验证 ──────────────────────────────────────

    @Test
    fun `前导静音段接近零`() {
        val pcm = builder.renderDirection(MelodicDirection.ASCENDING)
        val leadSamples = MelodicDirectionAudioBuilder.LEAD_SILENCE_SAMPLES
        for (i in 0 until leadSamples) {
            assertTrue("前导静音段应接近零: index=$i val=${pcm[i]}", kotlin.math.abs(pcm[i]) < 0.01f)
        }
    }

    @Test
    fun `尾部静音段接近零`() {
        val pcm = builder.renderDirection(MelodicDirection.ASCENDING)
        val noteSamples = (44100 * MelodicDirectionAudioBuilder.NOTE_DURATION_MS / 1000.0).toInt()
        val noteEnd = MelodicDirectionAudioBuilder.LEAD_SILENCE_SAMPLES + 4 * noteSamples
        for (i in noteEnd until pcm.size) {
            assertTrue("尾部静音段应接近零: index=$i val=${pcm[i]}", kotlin.math.abs(pcm[i]) < 0.01f)
        }
    }

    // ── 频率验证 ────────────────────────────────────────

    @Test
    fun `computeFrequencies返回4个频率`() {
        for (direction in MelodicDirection.ALL) {
            val freqs = builder.computeFrequencies(direction)
            assertEquals(4, freqs.size)
        }
    }

    @Test
    fun `上行频率单调递增`() {
        val freqs = builder.computeFrequencies(MelodicDirection.ASCENDING)
        for (i in 1 until freqs.size) {
            assertTrue(freqs[i] > freqs[i - 1])
        }
    }

    @Test
    fun `下行频率单调递减`() {
        val freqs = builder.computeFrequencies(MelodicDirection.DESCENDING)
        for (i in 1 until freqs.size) {
            assertTrue(freqs[i] < freqs[i - 1])
        }
    }

    @Test
    fun `平行频率全部相同`() {
        val freqs = builder.computeFrequencies(MelodicDirection.STATIC)
        for (freq in freqs) {
            assertEquals(freqs[0], freq, 0.01)
        }
    }

    @Test
    fun `拱形频率先升后降`() {
        val freqs = builder.computeFrequencies(MelodicDirection.ARCH)
        assertTrue(freqs[0] < freqs[1])
        assertTrue(freqs[1] < freqs[2])
        assertTrue(freqs[2] > freqs[3])
    }

    @Test
    fun `V形频率先降后升`() {
        val freqs = builder.computeFrequencies(MelodicDirection.V_SHAPE)
        assertTrue(freqs[0] > freqs[1])
        assertTrue(freqs[1] > freqs[2])
        assertTrue(freqs[2] < freqs[3])
    }

    @Test
    fun `所有频率为正数`() {
        for (direction in MelodicDirection.ALL) {
            val freqs = builder.computeFrequencies(direction)
            for (freq in freqs) {
                assertTrue("${direction.name} freq=$freq 应为正数", freq > 0.0)
            }
        }
    }

    // ── 能量分布验证（旋律轮廓区分度） ─────────────────

    @Test
    fun `平行旋律各音符段能量一致`() {
        val pcm = builder.renderDirection(MelodicDirection.STATIC)
        val noteSamples = (44100 * MelodicDirectionAudioBuilder.NOTE_DURATION_MS / 1000.0).toInt()
        val lead = MelodicDirectionAudioBuilder.LEAD_SILENCE_SAMPLES

        // 计算每个音符段的 RMS
        val rmsValues = mutableListOf<Double>()
        for (noteIdx in 0 until 4) {
            val start = lead + noteIdx * noteSamples
            var sumSq = 0.0
            for (i in start until start + noteSamples) {
                sumSq += pcm[i].toDouble() * pcm[i]
            }
            rmsValues.add(kotlin.math.sqrt(sumSq / noteSamples))
        }

        // 平行旋律所有音符应能量相近（音高相同）
        val maxRms = rmsValues.max()
        val minRms = rmsValues.min()
        assertTrue("平行旋律 RMS 差异过大: $rmsValues", maxRms / minRms < 1.5)
    }

    // ── estimateDurationMs ──────────────────────────────

    @Test
    fun `estimateDurationMs返回合理值`() {
        for (direction in MelodicDirection.ALL) {
            val ms = builder.estimateDurationMs(direction)
            assertTrue(ms > 0)
        }
    }

    @Test
    fun `estimateDurationMs与采样数一致`() {
        val direction = MelodicDirection.ASCENDING
        val pcm = builder.renderDirection(direction)
        val expectedMs = (pcm.size.toDouble() / 44100 * 1000).toLong()
        val actualMs = builder.estimateDurationMs(direction)
        assertEquals(expectedMs, actualMs)
    }

    // ── render from Question ────────────────────────────

    @Test
    fun `从Question渲染返回有效缓冲区`() {
        val engine = MelodicDirectionEngine.withSeed(1)
        for (difficulty in MelodicDirectionDifficulty.ALL) {
            val q = engine.generate(difficulty)
            val pcm = builder.render(q)
            assertTrue(pcm.isNotEmpty())
        }
    }
}
