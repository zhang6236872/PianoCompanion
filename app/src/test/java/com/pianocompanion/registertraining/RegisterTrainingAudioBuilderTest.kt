package com.pianocompanion.registertraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音区辨识训练音频构建器单元测试。
 */
class RegisterTrainingAudioBuilderTest {

    private val builder = RegisterTrainingAudioBuilder()

    // ── 基本渲染 ──────────────────────────────────────

    @Test
    fun `渲染低低音区返回非空缓冲区`() {
        val audio = builder.renderRegister(MusicRegister.DEEP_BASS)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `渲染中音区返回非空缓冲区`() {
        val audio = builder.renderRegister(MusicRegister.TENOR)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `渲染高音区返回非空缓冲区`() {
        val audio = builder.renderRegister(MusicRegister.SOPRANO)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `渲染极高音区返回非空缓冲区`() {
        val audio = builder.renderRegister(MusicRegister.TOP)
        assertTrue(audio.isNotEmpty())
    }

    // ── 采样范围 ────────────────────────────────────────

    @Test
    fun `所有采样值在-1到1之间`() {
        for (register in MusicRegister.ALL) {
            val audio = builder.renderRegister(register)
            for (sample in audio) {
                assertTrue("音区 ${register.displayName} 有越界采样: $sample", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `缓冲区包含非零值`() {
        for (register in MusicRegister.ALL) {
            val audio = builder.renderRegister(register)
            val hasNonZero = audio.any { it != 0.0f }
            assertTrue("音区 ${register.displayName} 缓冲区全零", hasNonZero)
        }
    }

    // ── 不同音区产生不同音频 ──────────────────────────

    @Test
    fun `低低音区和中音区音频不同`() {
        val lowAudio = builder.renderRegister(MusicRegister.DEEP_BASS)
        val midAudio = builder.renderRegister(MusicRegister.TENOR)
        assertFalse(lowAudio.contentEquals(midAudio))
    }

    @Test
    fun `中音区和高音区音频不同`() {
        val midAudio = builder.renderRegister(MusicRegister.TENOR)
        val highAudio = builder.renderRegister(MusicRegister.SOPRANO)
        assertFalse(midAudio.contentEquals(highAudio))
    }

    // ── RMS 差异验证音区差异 ─────────────────────────

    @Test
    fun `不同音区RMS值不同`() {
        val rmsLow = computeRms(builder.renderRegister(MusicRegister.DEEP_BASS))
        val rmsMid = computeRms(builder.renderRegister(MusicRegister.TENOR))
        val rmsHigh = computeRms(builder.renderRegister(MusicRegister.SOPRANO))
        // 不同音区的 RMS 不完全相同
        assertTrue(rmsLow != rmsMid || rmsMid != rmsHigh)
    }

    @Test
    fun `频谱质心随音区升高而增加`() {
        // 高音区的频谱质心应高于低音区
        val centroidLow = computeSpectralCentroid(builder.renderRegister(MusicRegister.DEEP_BASS))
        val centroidHigh = computeSpectralCentroid(builder.renderRegister(MusicRegister.SOPRANO))
        assertTrue(
            "高音区频谱质心($centroidHigh)应高于低音区($centroidLow)",
            centroidHigh > centroidLow
        )
    }

    // ── noteCount ──────────────────────────────────────

    @Test
    fun `更多音符产生更长缓冲区`() {
        val shortAudio = builder.renderRegister(MusicRegister.TENOR, noteCount = 2)
        val longAudio = builder.renderRegister(MusicRegister.TENOR, noteCount = 8)
        assertTrue(longAudio.size > shortAudio.size)
    }

    // ── onset 时间 ──────────────────────────────────────

    @Test
    fun `computeOnsetTimes首音在LEAD_SILENCE`() {
        val onsets = builder.computeOnsetTimes(noteCount = 4)
        assertEquals(RegisterTrainingEngine.LEAD_SILENCE_MS, onsets[0], 0.01)
    }

    @Test
    fun `computeOnsetTimes数量等于noteCount`() {
        for (count in listOf(1, 2, 4, 8)) {
            val onsets = builder.computeOnsetTimes(noteCount = count)
            assertEquals(count, onsets.size)
        }
    }

    @Test
    fun `computeOnsetTimes间距等于NOTE_DURATION`() {
        val onsets = builder.computeOnsetTimes(noteCount = 4)
        for (i in 1 until onsets.size) {
            assertEquals(
                RegisterTrainingAudioBuilder.NOTE_DURATION_MS,
                onsets[i] - onsets[i - 1], 0.01
            )
        }
    }

    // ── estimateDurationMs ─────────────────────────────

    @Test
    fun `estimateDurationMs为正值`() {
        val engine = RegisterTrainingEngine.withSeed(1)
        for (difficulty in RegisterTrainingDifficulty.ALL) {
            val q = engine.generate(difficulty)
            val duration = builder.estimateDurationMs(q)
            assertTrue(duration > 0)
        }
    }

    @Test
    fun `更多音符的预估时长更长`() {
        val engine = RegisterTrainingEngine()
        val shortQ = engine.generate(RegisterTrainingDifficulty.BEGINNER, noteCount = 2)
        val longQ = engine.generate(RegisterTrainingDifficulty.BEGINNER, noteCount = 8)
        assertTrue(builder.estimateDurationMs(longQ) > builder.estimateDurationMs(shortQ))
    }

    // ── 辅助方法 ──────────────────────────────────────

    private fun computeRms(audio: FloatArray): Double {
        var sumSquares = 0.0
        for (s in audio) {
            sumSquares += s * s
        }
        return kotlin.math.sqrt(sumSquares / audio.size)
    }

    private fun computeSpectralCentroid(audio: FloatArray): Double {
        // 简化版频谱质心：使用过零率作为近似指标
        // 高频信号的过零率高于低频信号
        var zeroCrossings = 0
        for (i in 1 until audio.size) {
            if ((audio[i] >= 0 && audio[i - 1] < 0) || (audio[i] < 0 && audio[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        return zeroCrossings.toDouble() / audio.size
    }
}
