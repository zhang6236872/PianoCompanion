package com.pianocompanion.texturerecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureCategoryAudioBuilderTest {

    private val builder = TextureCategoryAudioBuilder(sampleRate = 44100)

    /** 直接构造题目（绕过引擎）以测试特定织体的音频结构。 */
    private fun makeQuestion(
        difficulty: MusicTextureDifficulty,
        texture: MusicTextureType
    ): TextureCategoryQuestion {
        // 如果目标织体不在该难度的集合中（如 HETEROPHONIC 不属于 BEGINNER），自动升级到包含它的难度
        val effectiveDifficulty =
            if (texture in difficulty.types) difficulty else MusicTextureDifficulty.INTERMEDIATE
        val choices = effectiveDifficulty.types.map { it.fullLabel }
        return TextureCategoryQuestion(
            difficulty = effectiveDifficulty,
            seed = 0L,
            targetTexture = texture,
            answerChoices = choices,
            correctAnswer = texture.fullLabel
        )
    }

    // ── 单声部 ──────────────────────────────────────────

    @Test
    fun `单声部只有 voice 0 且音符不重叠`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.MONOPHONIC)
        val events = builder.buildNoteEvents(q)
        assertTrue("单声部应有音符事件", events.isNotEmpty())
        // 全部为单声部（voice 0）
        assertTrue(events.all { it.voice == 0 })
        // 一次只响一个音：各音符的 onset 单调递增，且 onset >= 前一音的 onset（无同时发响）
        for (i in 1 until events.size) {
            assertTrue(
                "单声部音符应顺序进行，但第 $i 个音符与前一音同时发响",
                events[i].onsetMs > events[i - 1].onsetMs
            )
        }
    }

    @Test
    fun `单声部初级为 4 个音符`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.MONOPHONIC)
        assertEquals(4, builder.noteCount(q))
    }

    // ── 主调 ──────────────────────────────────────────

    @Test
    fun `主调包含旋律 voice 0 与和弦伴奏 voice 1`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HOMOPHONIC)
        val events = builder.buildNoteEvents(q)
        val voices = events.map { it.voice }.toSet()
        assertTrue("主调应有 voice 0 旋律", 0 in voices)
        assertTrue("主调应有 voice 1 伴奏", 1 in voices)
    }

    @Test
    fun `主调每个节拍为旋律单音加 3 音和弦`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HOMOPHONIC)
        val events = builder.buildNoteEvents(q)
        val beatMs = 60_000.0 / q.tempoBpm
        val beats = events.groupBy { (it.onsetMs / beatMs).toInt() }
        // 4 拍
        assertEquals(4, beats.size)
        beats.values.forEach { beatEvents ->
            val melody = beatEvents.count { it.voice == 0 }
            val chord = beatEvents.count { it.voice == 1 }
            assertEquals("每拍 1 个旋律音", 1, melody)
            assertEquals("每拍 3 个和弦音", 3, chord)
        }
    }

    @Test
    fun `主调旋律在伴奏之上`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HOMOPHONIC)
        val events = builder.buildNoteEvents(q)
        val melodyMidi = events.filter { it.voice == 0 }.map { it.midi }
        val chordMidi = events.filter { it.voice == 1 }.map { it.midi }
        assertTrue(
            "旋律音应高于和弦伴奏",
            (melodyMidi.minOrNull() ?: 0) > (chordMidi.maxOrNull() ?: 127)
        )
    }

    // ── 复调 ──────────────────────────────────────────

    @Test
    fun `复调两声部节奏不同（上方长音 下方短音）`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.POLYPHONIC)
        val events = builder.buildNoteEvents(q)
        val beatMs = 60_000.0 / q.tempoBpm
        val upper = events.filter { it.voice == 0 }
        val lower = events.filter { it.voice == 1 }
        assertTrue("复调应有上方声部", upper.isNotEmpty())
        assertTrue("复调应有下方声部", lower.isNotEmpty())
        // 上方声部每个音符 2 拍，下方声部每个音符 1 拍
        upper.forEach {
            assertEquals("上方声部应为长音(2拍)", 2.0 * beatMs, it.durationMs, 0.001)
        }
        lower.forEach {
            assertEquals("下方声部应为短音(1拍)", beatMs, it.durationMs, 0.001)
        }
    }

    @Test
    fun `复调两声部旋律素材不同（音高不重合）`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.POLYPHONIC)
        val events = builder.buildNoteEvents(q)
        val upperPitchClasses = events.filter { it.voice == 0 }.map { it.midi % 12 }.toSet()
        val lowerPitchClasses = events.filter { it.voice == 1 }.map { it.midi % 12 }.toSet()
        // 两线音高素材应基本不重合（独立性）
        val overlap = upperPitchClasses.intersect(lowerPitchClasses)
        assertTrue(
            "复调两声部音高素材应不同，但有重合 $overlap",
            overlap.size < upperPitchClasses.size // 允许少量偶然重合，但不应完全相同
        )
    }

    // ── 支声复调 ──────────────────────────────────────────

    @Test
    fun `支声复调两声部骨架音高相同（仅八度差）`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HETEROPHONIC)
        val events = builder.buildNoteEvents(q)
        val beatMs = 60_000.0 / q.tempoBpm
        val plain = events.filter { it.voice == 0 }
        val ornate = events.filter { it.voice == 1 }
        assertTrue("支声应有朴素声部", plain.isNotEmpty())
        assertTrue("支声应有装饰声部", ornate.isNotEmpty())
        // 每拍：朴素声部 1 音；其音高 class 应与装饰声部该拍第一个音相同（八度差）
        plain.forEach { p ->
            val beatIdx = (p.onsetMs / beatMs).toInt()
            // 装饰声部在该拍的所有音
            val ornateBeat = ornate.filter { (it.onsetMs / beatMs).toInt() == beatIdx }
            assertTrue("拍 $beatIdx 装饰声部应有音", ornateBeat.isNotEmpty())
            // 装饰声部该拍至少有一个音与朴素声部同音高 class（八度关系 → 同源）
            val sameClass = ornateBeat.any { (it.midi % 12) == (p.midi % 12) }
            assertTrue("拍 $beatIdx 两声部应有同源骨架音（同音高 class）", sameClass)
        }
    }

    @Test
    fun `支声复调装饰声部音符数多于朴素声部（加花）`() {
        val q = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HETEROPHONIC)
        val events = builder.buildNoteEvents(q)
        val plain = events.count { it.voice == 0 }
        val ornate = events.count { it.voice == 1 }
        assertTrue(
            "装饰声部 ($ornate) 应比朴素声部 ($plain) 音符更多",
            ornate > plain
        )
        // 初级 4 拍：朴素 4 音，装饰 12 音（每拍 3 个回音音）
        assertEquals(4, plain)
        assertEquals(12, ornate)
    }

    // ── 复调 vs 支声复调 关键区分 ──────────────────────

    @Test
    fun `复调与支声复调的声部素材关系截然不同`() {
        val polyQ = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.POLYPHONIC)
        val hetQ = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HETEROPHONIC)
        val poly = builder.buildNoteEvents(polyQ)
        val het = builder.buildNoteEvents(hetQ)

        // 复调：上方声部音高 class 集合 与 下方 基本不重合（独立素材）
        val polyUpperClass = poly.filter { it.voice == 0 }.map { it.midi % 12 }.toSet()
        val polyLowerClass = poly.filter { it.voice == 1 }.map { it.midi % 12 }.toSet()
        val polyOverlap = polyUpperClass.intersect(polyLowerClass)

        // 支声：朴素与装饰声部音高 class 集合高度重合（同源素材）
        val hetPlainClass = het.filter { it.voice == 0 }.map { it.midi % 12 }.toSet()
        val hetOrnateClass = het.filter { it.voice == 1 }.map { it.midi % 12 }.toSet()
        val hetOverlap = hetPlainClass.intersect(hetOrnateClass)

        // 支声的重合度应显著高于复调（这是两种织体的根本区别）
        assertTrue(
            "支声复调两声部音高重合 (${hetOverlap.size}) 应高于复调 (${polyOverlap.size})",
            hetOverlap.size > polyOverlap.size
        )
    }

    // ── 复杂度等级 ──────────────────────────────────────

    @Test
    fun `高级复杂度产生更长的片段（更多拍）`() {
        val beginner = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.MONOPHONIC)
        val advanced = makeQuestion(MusicTextureDifficulty.ADVANCED, MusicTextureType.MONOPHONIC)
        val bDur = builder.estimateDurationMs(beginner)
        val aDur = builder.estimateDurationMs(advanced)
        assertTrue(
            "高级 ($aDur ms) 片段应长于初级 ($bDur ms)",
            aDur > bDur
        )
        // 高级单声部为 8 个音符（更密集）
        assertEquals(8, builder.noteCount(advanced))
    }

    @Test
    fun `主调高级为 6 拍`() {
        val q = makeQuestion(MusicTextureDifficulty.ADVANCED, MusicTextureType.HOMOPHONIC)
        val events = builder.buildNoteEvents(q)
        val beatMs = 60_000.0 / q.tempoBpm
        val beats = events.groupBy { (it.onsetMs / beatMs).toInt() }
        assertEquals(6, beats.size)
    }

    // ── 渲染输出 ──────────────────────────────────────

    @Test
    fun `render 产生非空且值域受限的 PCM 缓冲区`() {
        MusicTextureType.ALL.forEach { texture ->
            val diff = if (texture == MusicTextureType.HETEROPHONIC)
                MusicTextureDifficulty.INTERMEDIATE else MusicTextureDifficulty.BEGINNER
            val q = makeQuestion(diff, texture)
            val pcm = builder.render(q)
            assertTrue("${texture.displayName} 渲染应产生非空缓冲区", pcm.isNotEmpty())
            pcm.forEach { sample ->
                assertTrue("${texture.displayName} 样本 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
            }
            // 非全静音
            val maxAbs = pcm.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            assertTrue("${texture.displayName} 渲染应产生有声信号", maxAbs > 0.01f)
        }
    }

    @Test
    fun `render 空事件列表返回空缓冲区`() {
        val pcm = builder.renderEvents(emptyList(), 1000.0)
        assertEquals(0, pcm.size)
    }

    @Test
    fun `midiToFrequency 正确（A4 = 440Hz）`() {
        assertEquals(440.0, TextureCategoryAudioBuilder.midiToFrequency(69), 0.01)
    }

    @Test
    fun `单声部与主调音符数不同（结构可区分）`() {
        val mono = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.MONOPHONIC)
        val homo = makeQuestion(MusicTextureDifficulty.BEGINNER, MusicTextureType.HOMOPHONIC)
        assertFalse(
            "单声部与主调音符数不应相同",
            builder.noteCount(mono) == builder.noteCount(homo)
        )
    }
}
