package com.pianocompanion.rhythmmemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmMemoryAudioBuilder] 单元测试。
 *
 * 验证节奏型渲染的击打时序、强度分层、重复次数等核心正确性。
 */
class RhythmMemoryAudioBuilderTest {

    private val builder = RhythmMemoryAudioBuilder()

    private fun question(
        cells: List<RhythmCellType>,
        difficulty: RhythmMemoryDifficulty = RhythmMemoryDifficulty.INTERMEDIATE
    ): RhythmMemoryQuestion {
        val pattern = RhythmPattern(cells)
        // 根据 difficulty.choiceCount 生成足够数量的合法选项（含正确答案）
        val choices = (0 until difficulty.choiceCount).mapIndexed { i, _ ->
            if (i == 0) pattern.displayString else "干扰$i"
        }
        return RhythmMemoryQuestion(
            difficulty = difficulty,
            seed = 1L,
            targetPattern = pattern,
            answerChoices = choices,
            correctAnswer = pattern.displayString
        )
    }

    // ── 击打次数 ──────────────────────────────────────

    @Test
    fun `四分音符每拍 1 个击打`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.QUARTER))
        val hits = builder.buildHits(q)
        // 2 拍 × 1 击 × 2 重复 = 4
        assertEquals(4, hits.size)
    }

    @Test
    fun `两个八分每拍 2 个击打`() {
        val q = question(listOf(RhythmCellType.TWO_EIGHTHS, RhythmCellType.TWO_EIGHTHS))
        val hits = builder.buildHits(q)
        // 2 拍 × 2 击 × 2 重复 = 8
        assertEquals(8, hits.size)
    }

    @Test
    fun `四个十六分每拍 4 个击打`() {
        val q = question(listOf(RhythmCellType.FOUR_SIXTEENTHS))
        val hits = builder.buildHits(q)
        // 1 拍 × 4 击 × 2 重复 = 8
        assertEquals(8, hits.size)
    }

    @Test
    fun `三连音每拍 3 个击打`() {
        val q = question(listOf(RhythmCellType.TRIPLET))
        val hits = builder.buildHits(q)
        assertEquals(6, hits.size) // 1拍 × 3 × 2
    }

    @Test
    fun `长短短每拍 3 个击打`() {
        val q = question(listOf(RhythmCellType.LONG_SHORT_SHORT))
        assertEquals(6, builder.buildHits(q).size)
    }

    @Test
    fun `附点长短每拍 2 个击打`() {
        val q = question(listOf(RhythmCellType.DOTTED_LONG_SHORT))
        assertEquals(4, builder.buildHits(q).size) // 1拍 × 2 × 2
    }

    @Test
    fun `混合节奏型击打数为各拍之和 × 重复`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.TWO_EIGHTHS, RhythmCellType.FOUR_SIXTEENTHS))
        val hits = builder.buildHits(q)
        // (1 + 2 + 4) × 2 = 14
        assertEquals(14, hits.size)
    }

    // ── 击打时序位置 ──────────────────────────────────

    @Test
    fun `两个八分的第二击在拍中点`() {
        val q = question(listOf(RhythmCellType.TWO_EIGHTHS))
        val hits = builder.buildHits(q)
        val beatMs = 60_000.0 / q.tempoBpm
        val firstRepHits = hits.take(2)
        // 第一击在 0
        assertEquals(0.0, firstRepHits[0].onsetMs, 0.5)
        // 第二击在拍中点
        assertEquals(beatMs * 0.5, firstRepHits[1].onsetMs, 0.5)
    }

    @Test
    fun `四个十六分等间距排列`() {
        val q = question(listOf(RhythmCellType.FOUR_SIXTEENTHS))
        val hits = builder.buildHits(q).take(4)
        val beatMs = 60_000.0 / q.tempoBpm
        for (i in 0 until 4) {
            assertEquals(beatMs * 0.25 * i, hits[i].onsetMs, 0.5)
        }
    }

    @Test
    fun `附点长短第二击在 3-4 处`() {
        val q = question(listOf(RhythmCellType.DOTTED_LONG_SHORT))
        val hits = builder.buildHits(q).take(2)
        val beatMs = 60_000.0 / q.tempoBpm
        assertEquals(0.0, hits[0].onsetMs, 0.5)
        assertEquals(beatMs * 0.75, hits[1].onsetMs, 0.5)
    }

    @Test
    fun `长短短第二击在 1-2 处第三击在 3-4 处`() {
        val q = question(listOf(RhythmCellType.LONG_SHORT_SHORT))
        val hits = builder.buildHits(q).take(3)
        val beatMs = 60_000.0 / q.tempoBpm
        assertEquals(0.0, hits[0].onsetMs, 0.5)
        assertEquals(beatMs * 0.5, hits[1].onsetMs, 0.5)
        assertEquals(beatMs * 0.75, hits[2].onsetMs, 0.5)
    }

    @Test
    fun `三连音等分 1-3 间距`() {
        val q = question(listOf(RhythmCellType.TRIPLET))
        val hits = builder.buildHits(q).take(3)
        val beatMs = 60_000.0 / q.tempoBpm
        val third = beatMs / 3.0
        assertEquals(0.0, hits[0].onsetMs, 0.5)
        assertEquals(third, hits[1].onsetMs, 0.5)
        assertEquals(2.0 * third, hits[2].onsetMs, 0.5)
    }

    @Test
    fun `第二拍首击在 beatMs 处`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.QUARTER))
        val hits = builder.buildHits(q)
        val beatMs = 60_000.0 / q.tempoBpm
        // 第一次重复：[0, beatMs, ...] 第二击 = 第二拍
        assertEquals(beatMs, hits[1].onsetMs, 0.5)
    }

    // ── 强度分层 ──────────────────────────────────────

    @Test
    fun `小节首拍重音最强`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.QUARTER))
        val hits = builder.buildHits(q)
        val phraseStart = hits.first { it.beatIndex == 0 && it.onsetMs < 50.0 }
        val otherBeat = hits.first { it.beatIndex == 1 && it.onsetMs < 1500.0 }
        assertEquals(RhythmMemoryAudioBuilder.ACCENT_INTENSITY, phraseStart.intensity, 0.0f)
        assertEquals(RhythmMemoryAudioBuilder.BEAT_INTENSITY, otherBeat.intensity, 0.0f)
        assertTrue(phraseStart.intensity > otherBeat.intensity)
    }

    @Test
    fun `拍内细分击打弱于拍首`() {
        // 双拍节奏型：第一拍 QUARTER，第二拍 TWO_EIGHTHS
        // 第二拍（beatIndex=1）的拍首击打为 BEAT，拍内细分击打为 SUBDIVISION
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.TWO_EIGHTHS))
        val hits = builder.buildHits(q)
        val beatStart = hits[1]   // beat 1 拍首
        val subdivision = hits[2] // beat 1 拍中
        assertEquals(RhythmMemoryAudioBuilder.BEAT_INTENSITY, beatStart.intensity, 0.0f)
        assertEquals(RhythmMemoryAudioBuilder.SUBDIVISION_INTENSITY, subdivision.intensity, 0.0f)
        assertTrue(beatStart.intensity > subdivision.intensity)
    }

    // ── 重复 ──────────────────────────────────────────

    @Test
    fun `节奏型重复播放两次`() {
        val q = question(listOf(RhythmCellType.QUARTER)) // 1 拍
        val hits = builder.buildHits(q)
        // 1 击 × 2 重复 = 2
        assertEquals(2, hits.size)
        val beatMs = 60_000.0 / q.tempoBpm
        // 第二次重复在第 1 拍之后 + gap
        val expectedSecondOnset = beatMs + RhythmMemoryAudioBuilder.GAP_BETWEEN_REPETITIONS_MS
        assertEquals(expectedSecondOnset, hits[1].onsetMs, 0.5)
    }

    @Test
    fun `两次重复之间有间隔`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.QUARTER))
        val hits = builder.buildHits(q)
        val beatMs = 60_000.0 / q.tempoBpm
        // 第一次重复最后一击在第 2 拍
        val firstRepLast = hits[1]
        // 第二次重复第一击
        val secondRepFirst = hits[2]
        val gap = secondRepFirst.onsetMs - firstRepLast.onsetMs
        // 间隔应大于 0 且等于一拍 + GAP（因为第二拍到下一重复起点 = 0 拍 + GAP）
        // 第一次重复最后一击 onset = beatMs; 第二次重复第一击 onset = 2*beatMs + GAP
        assertEquals(beatMs + RhythmMemoryAudioBuilder.GAP_BETWEEN_REPETITIONS_MS, gap, 0.5)
    }

    // ── 渲染输出 ──────────────────────────────────────

    @Test
    fun `render 返回非空缓冲区`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.TWO_EIGHTHS))
        val audio = builder.render(q)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `render 值域在 -1 到 1 之间`() {
        val q = question(listOf(RhythmCellType.FOUR_SIXTEENTHS, RhythmCellType.TRIPLET, RhythmCellType.DOTTED_LONG_SHORT))
        val audio = builder.render(q)
        for (sample in audio) {
            assertTrue("样本 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `render 空事件列表返回空数组`() {
        val audio = builder.renderHits(emptyList(), 100.0)
        assertTrue(audio.isEmpty())
    }

    @Test
    fun `estimateDurationMs 大于 0`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.QUARTER))
        val dur = builder.estimateDurationMs(q)
        assertTrue(dur > 0)
    }

    @Test
    fun `不同节奏型击打数不同`() {
        val quarter = builder.hitCount(question(listOf(RhythmCellType.QUARTER)))
        val eighths = builder.hitCount(question(listOf(RhythmCellType.TWO_EIGHTHS)))
        val sixteenths = builder.hitCount(question(listOf(RhythmCellType.FOUR_SIXTEENTHS)))
        assertEquals(2, quarter) // 1拍 × 1 × 2
        assertEquals(4, eighths)
        assertEquals(8, sixteenths)
        assertTrue(quarter < eighths)
        assertTrue(eighths < sixteenths)
    }

    @Test
    fun `拍数越多击打事件越多（同单元）`() {
        val oneBeat = builder.hitCount(question(listOf(RhythmCellType.TWO_EIGHTHS)))
        val twoBeat = builder.hitCount(question(listOf(RhythmCellType.TWO_EIGHTHS, RhythmCellType.TWO_EIGHTHS)))
        assertTrue(oneBeat < twoBeat)
    }

    @Test
    fun `击打事件时间单调不减（同一重复内）`() {
        val q = question(listOf(RhythmCellType.FOUR_SIXTEENTHS, RhythmCellType.TWO_EIGHTHS, RhythmCellType.QUARTER))
        val hits = builder.buildHits(q)
        // 第一次重复的前一半
        val firstRepCount = q.targetPattern.totalHits
        val firstRep = hits.take(firstRepCount)
        for (i in 1 until firstRep.size) {
            assertTrue("击打 $i 时间应不早于前一个", firstRep[i].onsetMs >= firstRep[i - 1].onsetMs - 0.5)
        }
    }

    @Test
    fun `beatIndex 正确标记所属拍`() {
        val q = question(listOf(RhythmCellType.QUARTER, RhythmCellType.TWO_EIGHTHS, RhythmCellType.QUARTER))
        val hits = builder.buildHits(q)
        val firstRep = hits.take(4) // 1 + 2 + 1 = 4
        assertEquals(0, firstRep[0].beatIndex)
        assertEquals(1, firstRep[1].beatIndex)
        assertEquals(1, firstRep[2].beatIndex)
        assertEquals(2, firstRep[3].beatIndex)
    }

    @Test
    fun `大样本覆盖 - 所有难度均能渲染非空音频`() {
        RhythmMemoryDifficulty.ALL.forEach { d ->
            for (seed in 0 until 10) {
                val q = RhythmMemoryEngine.withSeed(seed.toLong()).generate(d)
                val audio = builder.render(q)
                assertTrue("${d.name} seed=$seed 渲染为空", audio.isNotEmpty())
            }
        }
    }

    @Test
    fun `midiToFrequency A4 = 440`() {
        assertEquals(440.0, RhythmMemoryAudioBuilder.midiToFrequency(69), 0.1)
    }
}
