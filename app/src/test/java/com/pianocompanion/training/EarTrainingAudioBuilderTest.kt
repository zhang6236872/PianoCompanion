package com.pianocompanion.training

import com.pianocompanion.audio.PianoToneSynthesizer
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * [EarTrainingAudioBuilder] 音频构建器单元测试。
 *
 * 覆盖：各播放模式音频渲染、缓冲区非空、采样值范围、多音符叠加限幅、
 * 旋律音符时序正确性、空输入处理。
 */
class EarTrainingAudioBuilderTest {

    private val builder = EarTrainingAudioBuilder(PianoToneSynthesizer())
    private val engine = EarTrainingEngine(Random(7))

    // ── 基础渲染 ──────────────────────────────────────────

    @Test
    fun `音程题目渲染非空缓冲区`() {
        val q = engine.generate(ExerciseType.INTERVAL, Difficulty.BEGINNER)
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `和弦题目渲染非空缓冲区`() {
        val q = engine.generate(ExerciseType.CHORD, Difficulty.INTERMEDIATE)
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `音阶题目渲染非空缓冲区`() {
        val q = engine.generate(ExerciseType.SCALE, Difficulty.ADVANCED)
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    // ── 采样值范围 ────────────────────────────────────────

    @Test
    fun `所有采样值在正负 1 范围内`() {
        for (type in ExerciseType.ALL) {
            for (diff in Difficulty.ALL) {
                val q = engine.generate(type, diff)
                val audio = builder.render(q)
                audio.forEach { sample ->
                    assertTrue("采样值 $sample 超出 [-1,1]: type=$type diff=$diff", sample in -1f..1f)
                }
            }
        }
    }

    // ── BLOCK 模式 ────────────────────────────────────────

    @Test
    fun `BLOCK 模式缓冲区长度等于单个音符长度`() {
        val synth = PianoToneSynthesizer()
        val b = EarTrainingAudioBuilder(synth)
        val q = engine.generate(ExerciseType.CHORD, Difficulty.BEGINNER).copy(playMode = PlayMode.BLOCK)
        val audio = b.render(q)
        // BLOCK 模式下所有音符同时叠加，缓冲区长度 = 最长音符长度
        val singleNoteLen = synth.synthesize(440.0, EarTrainingAudioBuilder.CHORD_DURATION_MS).size
        assertEquals(singleNoteLen, audio.size)
    }

    @Test
    fun `BLOCK 模式多音叠加后软限幅`() {
        val q = engine.generate(ExerciseType.CHORD, Difficulty.ADVANCED).copy(playMode = PlayMode.BLOCK)
        val audio = builder.render(q)
        // 软限幅后最大值不应超过 1.0
        val maxAbs = audio.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        assertTrue(maxAbs <= 1.0f)
    }

    // ── 旋律模式 ──────────────────────────────────────────

    @Test
    fun `旋律模式缓冲区长于单音符`() {
        val q = engine.generate(ExerciseType.SCALE, Difficulty.BEGINNER).copy(playMode = PlayMode.ASCENDING)
        val audio = builder.render(q)
        val synth = PianoToneSynthesizer()
        val singleNoteLen = synth.synthesize(440.0, builder.melodicNoteMs).size
        // 音阶有多个音符，缓冲区应远长于单个音符
        assertTrue("旋律缓冲区 ${audio.size} 不长于单音符 $singleNoteLen", audio.size > singleNoteLen * 2)
    }

    @Test
    fun `下行旋律缓冲区与上行长度相近`() {
        val q1 = engine.generate(ExerciseType.SCALE, Difficulty.BEGINNER).copy(playMode = PlayMode.ASCENDING)
        val q2 = q1.copy(playMode = PlayMode.DESCENDING)
        val audio1 = builder.render(q1)
        val audio2 = builder.render(q2)
        // 相同音符数的上行/下行，长度应相同
        assertEquals(audio1.size, audio2.size)
    }

    // ── 空输入 ────────────────────────────────────────────

    @Test
    fun `空音符列表返回空缓冲区`() {
        val q = engine.generate(ExerciseType.INTERVAL, Difficulty.BEGINNER).copy(midiNotes = emptyList())
        val audio = builder.render(q)
        assertEquals(0, audio.size)
    }

    @Test
    fun `BLOCK 模式空音符返回空缓冲区`() {
        val q = engine.generate(ExerciseType.CHORD, Difficulty.BEGINNER)
            .copy(midiNotes = emptyList(), playMode = PlayMode.BLOCK)
        val audio = builder.render(q)
        assertEquals(0, audio.size)
    }

    // ── 单音符 ────────────────────────────────────────────

    @Test
    fun `单音符旋律产生非静音输出`() {
        val q = EarTrainingQuestion(
            exerciseType = ExerciseType.INTERVAL,
            playMode = PlayMode.ASCENDING,
            midiNotes = listOf(60),
            correctAnswer = "测试",
            answerChoices = listOf("测试"),
            displayInfo = "C4"
        )
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
        // 应有非零值（实际音频信号）
        val nonZero = audio.count { it != 0f }
        assertTrue("单音符输出全为零", nonZero > 0)
    }

    // ── 音频有效性 ────────────────────────────────────────

    @Test
    fun `和弦音频有实际信号`() {
        val q = engine.generate(ExerciseType.CHORD, Difficulty.ADVANCED).copy(playMode = PlayMode.BLOCK)
        val audio = builder.render(q)
        val nonZero = audio.count { it != 0f }
        assertTrue("和弦音频无信号", nonZero > audio.size * 0.1) // 至少 10% 非零
    }

    @Test
    fun `音阶音频有多个非零段`() {
        val q = engine.generate(ExerciseType.SCALE, Difficulty.INTERMEDIATE)
        val audio = builder.render(q)
        // 计算非零采样占比
        val nonZeroRatio = audio.count { it != 0f }.toDouble() / audio.size
        assertTrue("音阶音频信号占比过低: $nonZeroRatio", nonZeroRatio > 0.3)
    }

    // ── 确定性 ────────────────────────────────────────────

    @Test
    fun `相同题目渲染相同音频`() {
        val q = engine.generate(ExerciseType.CHORD, Difficulty.BEGINNER)
        val audio1 = builder.render(q)
        val audio2 = builder.render(q)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals("采样 $i 不一致", audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `不同音符渲染不同音频`() {
        val q1 = EarTrainingQuestion(
            exerciseType = ExerciseType.INTERVAL, playMode = PlayMode.BLOCK,
            midiNotes = listOf(60, 64), correctAnswer = "a", answerChoices = listOf("a"), displayInfo = ""
        )
        val q2 = q1.copy(midiNotes = listOf(60, 67))
        val audio1 = builder.render(q1)
        val audio2 = builder.render(q2)
        // 不同和弦应产生不同音频
        assertFalse(java.util.Arrays.equals(audio1, audio2))
    }
}
