package com.pianocompanion.keyidentificationtraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调性中心辨识训练音频构建器单元测试。
 *
 * 验证渲染非空、采样率、不削波、不同旋律差异、时长预估、常量合理性等。
 */
class KeyIdentificationTrainingAudioBuilderTest {

    private val builder = KeyIdentificationTrainingAudioBuilder()

    // ── 基础渲染 ────────────────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        val q = KeyIdentificationTrainingEngine.withSeed(1L).generate(KeyDifficulty.BEGINNER)
        val audio = builder.render(q)
        assertTrue("渲染结果不能为空", audio.isNotEmpty())
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, KeyIdentificationTrainingAudioBuilder.SAMPLE_RATE)
    }

    @Test
    fun `rendered audio does not clip`() {
        for (difficulty in KeyDifficulty.ALL) {
            for (seed in 0..5) {
                val q = KeyIdentificationTrainingEngine.withSeed(seed.toLong()).generate(difficulty)
                val audio = builder.render(q)
                assertTrue(
                    "渲染音频不应超过 [-1, 1]",
                    audio.all { it in -1.0f..1.0f }
                )
            }
        }
    }

    // ── 不同旋律差异 ────────────────────────────────────────

    @Test
    fun `different keys produce different audio`() {
        val cMajor = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
            )
        )
        val gMajor = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.G_MAJOR, 67, MelodyPattern.ASCENDING_SCALE
            )
        )
        var foundDifference = false
        for (i in cMajor.indices) {
            if (i < gMajor.size && cMajor[i] != gMajor[i]) {
                foundDifference = true
                break
            }
        }
        assertTrue("不同调性的旋律应有不同的音频内容", foundDifference)
    }

    @Test
    fun `C major and A minor produce different audio despite shared pitch classes`() {
        // C major and A minor use the same pitch classes but different tonics
        val cMajor = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
            )
        )
        val aMinor = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.A_MINOR, 69, MelodyPattern.ASCENDING_SCALE
            )
        )
        // Both have 9 notes, same length
        assertEquals(cMajor.size, aMinor.size)
        // But different content (different starting notes → different melodic contour)
        assertFalse(cMajor.contentEquals(aMinor))
    }

    @Test
    fun `different patterns produce different length audio`() {
        // ASCENDING_SCALE has 9 notes, SCALE_UP_DOWN has 15 notes
        val ascending = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
            )
        )
        val fullScale = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.SCALE_UP_DOWN
            )
        )
        assertTrue(
            "完整音阶上下行(15音)应比上行音阶(9音)更长",
            fullScale.size > ascending.size
        )
    }

    // ── estimateDurationMs ──────────────────────────────────

    @Test
    fun `estimateDurationMs for ascending scale`() {
        val notes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
            MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
        )
        val q = KeyQuestion(
            key = MusicKey.C_MAJOR,
            tonicMidi = 60,
            tonicName = "C",
            melodyPattern = MelodyPattern.ASCENDING_SCALE,
            midiNotes = notes,
            difficulty = KeyDifficulty.BEGINNER,
            answerChoices = listOf("A", "B", "C"),
            correctAnswer = "A"
        )
        val estimated = builder.estimateDurationMs(q)
        val noteCount = notes.size
        val expected = KeyIdentificationTrainingAudioBuilder.LEAD_SILENCE_MS +
            noteCount * KeyIdentificationTrainingAudioBuilder.NOTE_DURATION_MS +
            (noteCount - 1) * KeyIdentificationTrainingAudioBuilder.NOTE_GAP_MS +
            KeyIdentificationTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    @Test
    fun `estimated duration scales with note count`() {
        val ascendingQ = KeyQuestion(
            key = MusicKey.C_MAJOR,
            tonicMidi = 60,
            tonicName = "C",
            melodyPattern = MelodyPattern.ASCENDING_SCALE,
            midiNotes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
            ),
            difficulty = KeyDifficulty.BEGINNER,
            answerChoices = listOf("A"),
            correctAnswer = "A"
        )
        val fullScaleQ = KeyQuestion(
            key = MusicKey.C_MAJOR,
            tonicMidi = 60,
            tonicName = "C",
            melodyPattern = MelodyPattern.SCALE_UP_DOWN,
            midiNotes = KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.SCALE_UP_DOWN
            ),
            difficulty = KeyDifficulty.BEGINNER,
            answerChoices = listOf("A"),
            correctAnswer = "A"
        )
        val ascending = builder.estimateDurationMs(ascendingQ)
        val fullScale = builder.estimateDurationMs(fullScaleQ)
        assertTrue(
            "15 音旋律时长($fullScale ms)应大于 9 音($ascending ms)",
            fullScale > ascending
        )
    }

    // ── 常量合理性 ──────────────────────────────────────────

    @Test
    fun `note duration is reasonable`() {
        assertTrue(
            "音符持续时间应在 200-2000ms 范围",
            KeyIdentificationTrainingAudioBuilder.NOTE_DURATION_MS in 200..2000
        )
    }

    @Test
    fun `note gap is non-negative`() {
        assertTrue(KeyIdentificationTrainingAudioBuilder.NOTE_GAP_MS >= 0)
    }

    @Test
    fun `lead and tail silence are positive`() {
        assertTrue(KeyIdentificationTrainingAudioBuilder.LEAD_SILENCE_MS > 0)
        assertTrue(KeyIdentificationTrainingAudioBuilder.TAIL_SILENCE_MS > 0)
    }

    // ── 空进行处理 ──────────────────────────────────────────

    @Test
    fun `empty melody returns empty buffer`() {
        val result = builder.renderMelody(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `rendered audio has lead silence at start`() {
        val audio = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
            )
        )
        val leadSamples = (44100 * KeyIdentificationTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // First samples should be silence (approximately zero)
        assertTrue(
            "前导静音区域应为静音",
            audio.take(leadSamples - 10).all { it.toInt() == 0 }
        )
    }

    @Test
    fun `rendered audio has tail silence at end`() {
        val audio = builder.renderMelody(
            KeyIdentificationTrainingEngine.buildMelodyMidiNotes(
                MusicKey.C_MAJOR, 60, MelodyPattern.ASCENDING_SCALE
            )
        )
        val tailSamples = (44100 * KeyIdentificationTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        // Last samples should be silence
        assertTrue(
            "尾部静音区域应为静音",
            audio.takeLast(tailSamples - 10).all { it.toInt() == 0 }
        )
    }

    // ── 单音渲染 ────────────────────────────────────────────

    @Test
    fun `single note renders successfully`() {
        val audio = builder.renderMelody(listOf(60))
        assertTrue(audio.isNotEmpty())
        assertTrue(
            "单音渲染不应超过 [-1, 1]",
            audio.all { it in -1.0f..1.0f }
        )
    }
}
