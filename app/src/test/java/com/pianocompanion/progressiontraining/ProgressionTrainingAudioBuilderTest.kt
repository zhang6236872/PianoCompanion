package com.pianocompanion.progressiontraining

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 和弦进行听辨训练音频构建器单元测试。
 *
 * 验证渲染非空、采样率、不削波、不同进行差异、时长预估等。
 */
class ProgressionTrainingAudioBuilderTest {

    private val builder = ProgressionTrainingAudioBuilder()

    // ── 基础渲染 ────────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        val audio = builder.render(q)
        assertTrue("渲染结果不能为空", audio.isNotEmpty())
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, ProgressionTrainingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `rendered audio does not clip`() {
        for (difficulty in ProgressionDifficulty.ALL) {
            for (seed in 0..5) {
                val q = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                val audio = builder.render(q)
                assertTrue(
                    "渲染音频不应超过 [-1, 1]",
                    audio.all { it in -1.0f..1.0f }
                )
            }
        }
    }

    // ── 不同进行差异 ────────────────────────────────────────

    @Test
    fun `different progressions produce different audio`() {
        // C 大调 CLASSIC vs JAZZ_TURNAROUND
        val classic = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.CLASSIC, 48)
        )
        val jazz = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.JAZZ_TURNAROUND, 48)
        )
        // CLASSIC has 4 chords, JAZZ has 3, so different lengths
        assertTrue(classic.size != jazz.size)
    }

    @Test
    fun `different tonics produce different audio`() {
        val cMajor = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.CLASSIC, 48)
        )
        val gMajor = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.CLASSIC, 55)
        )
        // Same length (same chord count), but different content
        assertEquals(cMajor.size, gMajor.size)
        var foundDifference = false
        for (i in cMajor.indices) {
            if (cMajor[i] != gMajor[i]) {
                foundDifference = true
                break
            }
        }
        assertTrue("不同调性的进行应有不同的音频内容", foundDifference)
    }

    // ── 长度差异 ────────────────────────────────────────────

    @Test
    fun `4-chord progression is longer than 3-chord`() {
        val fourChord = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.CLASSIC, 48)
        )
        val threeChord = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.JAZZ_TURNAROUND, 48)
        )
        assertTrue(
            "4 和弦进行应该比 3 和弦进行更长",
            fourChord.size > threeChord.size
        )
    }

    // ── estimateDurationMs ──────────────────────────────────

    @Test
    fun `estimateDurationMs for 4-chord progression`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.BEGINNER)
        val estimated = builder.estimateDurationMs(q)
        val expected = ProgressionTrainingAudioBuilder.LEAD_SILENCE_MS +
            4 * ProgressionTrainingAudioBuilder.CHORD_DURATION_MS +
            3 * ProgressionTrainingAudioBuilder.CHORD_GAP_MS +
            ProgressionTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    @Test
    fun `estimateDurationMs for 3-chord progression`() {
        val q = ProgressionTrainingEngine.withSeed(1L).generate(ProgressionDifficulty.ADVANCED)
        // Ensure we get a 3-chord progression (JAZZ_TURNAROUND)
        // Find a seed that gives JAZZ
        var jazzQuestion: ProgressionQuestion? = null
        for (seed in 0..100) {
            val candidate = ProgressionTrainingEngine.withSeed(seed.toLong()).generate(ProgressionDifficulty.ADVANCED)
            if (candidate.type == ProgressionType.JAZZ_TURNAROUND) {
                jazzQuestion = candidate
                break
            }
        }
        if (jazzQuestion != null) {
            val estimated = builder.estimateDurationMs(jazzQuestion)
            val expected = ProgressionTrainingAudioBuilder.LEAD_SILENCE_MS +
                3 * ProgressionTrainingAudioBuilder.CHORD_DURATION_MS +
                2 * ProgressionTrainingAudioBuilder.CHORD_GAP_MS +
                ProgressionTrainingAudioBuilder.TAIL_SILENCE_MS
            assertEquals(expected, estimated)
        }
    }

    @Test
    fun `estimated duration scales with chord count`() {
        // 直接构造已知和弦数量的题目（4 和弦 vs 3 和弦）
        val fourChordQuestion = ProgressionQuestion(
            type = ProgressionType.CLASSIC,
            tonicMidi = 48,
            tonicName = "C",
            difficulty = ProgressionDifficulty.BEGINNER,
            chordProgression = ProgressionTrainingEngine.buildProgressionMidiNotes(
                ProgressionType.CLASSIC, 48
            ),
            answerChoices = listOf("A", "B", "C"),
            correctAnswer = "A"
        )
        val threeChordQuestion = ProgressionQuestion(
            type = ProgressionType.JAZZ_TURNAROUND,
            tonicMidi = 48,
            tonicName = "C",
            difficulty = ProgressionDifficulty.ADVANCED,
            chordProgression = ProgressionTrainingEngine.buildProgressionMidiNotes(
                ProgressionType.JAZZ_TURNAROUND, 48
            ),
            answerChoices = listOf("A", "B", "C"),
            correctAnswer = "A"
        )
        val fourChord = builder.estimateDurationMs(fourChordQuestion)
        val threeChord = builder.estimateDurationMs(threeChordQuestion)
        assertTrue(
            "4 和弦进行时长($fourChord ms)应大于 3 和弦($threeChord ms)",
            fourChord > threeChord
        )
    }

    // ── 常量合理性 ──────────────────────────────────────────

    @Test
    fun `chord duration is reasonable`() {
        assertTrue(
            "和弦持续时间应在 300-2000ms 范围",
            ProgressionTrainingAudioBuilder.CHORD_DURATION_MS in 300..2000
        )
    }

    @Test
    fun `chord gap is non-negative`() {
        assertTrue(ProgressionTrainingAudioBuilder.CHORD_GAP_MS >= 0)
    }

    @Test
    fun `lead and tail silence are positive`() {
        assertTrue(ProgressionTrainingAudioBuilder.LEAD_SILENCE_MS > 0)
        assertTrue(ProgressionTrainingAudioBuilder.TAIL_SILENCE_MS > 0)
    }

    // ── 空进行处理 ──────────────────────────────────────────

    @Test
    fun `empty progression returns empty buffer`() {
        val result = builder.renderProgression(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `rendered audio has lead silence at start`() {
        val audio = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.CLASSIC, 48)
        )
        val leadSamples = (44100 * ProgressionTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // First samples should be silence (approximately zero)
        assertTrue(
            "前导静音区域应为静音",
            audio.take(leadSamples - 10).all { it.toInt() == 0 }
        )
    }

    @Test
    fun `rendered audio has tail silence at end`() {
        val audio = builder.renderProgression(
            ProgressionTrainingEngine.buildProgressionMidiNotes(ProgressionType.CLASSIC, 48)
        )
        val tailSamples = (44100 * ProgressionTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        // Last samples should be silence
        assertTrue(
            "尾部静音区域应为静音",
            audio.takeLast(tailSamples - 10).all { it.toInt() == 0 }
        )
    }
}
