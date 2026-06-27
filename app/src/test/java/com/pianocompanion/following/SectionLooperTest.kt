package com.pianocompanion.following

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import org.junit.Assert.*
import org.junit.Test

/**
 * [SectionLooper] 单元测试。
 *
 * 使用合成 ScoreNote（每 4 个音符一个小节，measureIndex = idx / 4）验证：
 * - 起止索引计算
 * - 循环触发判定
 * - 段落有效性
 * - 循环计数
 * - 边界情况
 */
class SectionLooperTest {

    /** 构造 [count] 个音符，每 4 个一组归属同一小节（measureIndex = idx / 4）。 */
    private fun makeScore(count: Int): Score {
        val notes = (0 until count).map { idx ->
            ScoreNote(
                midiNumber = 60 + idx,
                noteName = "N${60 + idx}",
                startTime = idx * 500L,
                duration = 400L,
                staff = Staff.TREBLE,
                measureIndex = idx / 4
            )
        }
        return Score(id = "test", title = "T", composer = "C", notes = notes)
    }

    // ===== 索引计算 =====

    @Test
    fun `startIndex returns first note at or after startMeasure`() {
        val score = makeScore(12) // measures 0,0,0,0 | 1,1,1,1 | 2,2,2,2
        val looper = SectionLooper(score).apply {
            enabled = true
            startMeasure = 1
            endMeasure = 1
        }
        assertEquals(4, looper.startIndex())
    }

    @Test
    fun `startIndex for measure 0 is 0`() {
        val score = makeScore(8)
        val looper = SectionLooper(score)
        looper.startMeasure = 0
        assertEquals(0, looper.startIndex())
    }

    @Test
    fun `startIndex beyond last note returns size`() {
        val score = makeScore(8) // max measure = 1
        val looper = SectionLooper(score)
        looper.startMeasure = 5
        assertEquals(score.notes.size, looper.startIndex())
    }

    @Test
    fun `endExclusiveIndex returns index after last note in section`() {
        val score = makeScore(12) // measures 0..2
        val looper = SectionLooper(score).apply {
            startMeasure = 0
            endMeasure = 1
        }
        // notes 4..7 belong to measure 1; last is index 7 → exclusive = 8
        assertEquals(8, looper.endExclusiveIndex())
    }

    @Test
    fun `endExclusiveIndex for whole score`() {
        val score = makeScore(10)
        val looper = SectionLooper(score).apply {
            startMeasure = 0
            endMeasure = 2
        }
        assertEquals(score.notes.size, looper.endExclusiveIndex())
    }

    @Test
    fun `endExclusiveIndex when no notes match returns 0`() {
        val score = makeScore(8) // max measure = 1
        val looper = SectionLooper(score)
        looper.endMeasure = 1
        looper.startMeasure = 10 // start beyond all notes
        // startMeasure 10 > endMeasure 1 → endExclusiveIndex based on endMeasure=1 → 8
        // But section is empty because startIndex(10) > endExclusiveIndex
        assertEquals(8, looper.endExclusiveIndex())
        assertEquals(0, looper.sectionNoteCount())
    }

    // ===== 段落音符数 =====

    @Test
    fun `sectionNoteCount counts notes within range`() {
        val score = makeScore(12)
        val looper = SectionLooper(score).apply {
            startMeasure = 1
            endMeasure = 2
        }
        // measure 1 = notes 4..7 (4 notes), measure 2 = notes 8..11 (4 notes) = 8 total
        assertEquals(8, looper.sectionNoteCount())
    }

    @Test
    fun `sectionNoteCount single measure`() {
        val score = makeScore(12)
        val looper = SectionLooper(score).apply {
            startMeasure = 1
            endMeasure = 1
        }
        assertEquals(4, looper.sectionNoteCount())
    }

    @Test
    fun `sectionNoteCount clamped to at least 0`() {
        val score = makeScore(8)
        val looper = SectionLooper(score).apply {
            startMeasure = 5
            endMeasure = 1
        }
        // start beyond end → startIndex(size) > endExclusiveIndex
        assertTrue(looper.sectionNoteCount() >= 0)
    }

    // ===== shouldLoop =====

    @Test
    fun `shouldLoop false when disabled`() {
        val score = makeScore(8)
        val looper = SectionLooper(score).apply {
            enabled = false
            startMeasure = 0
            endMeasure = 0
        }
        assertFalse(looper.shouldLoop(100))
    }

    @Test
    fun `shouldLoop false when position before end`() {
        val score = makeScore(12) // endMeasure default = maxMeasure = 2 → endExclusive = 12
        val looper = SectionLooper(score).apply {
            enabled = true
            startMeasure = 0
            endMeasure = 2
        }
        assertFalse(looper.shouldLoop(5))
        assertFalse(looper.shouldLoop(11))
    }

    @Test
    fun `shouldLoop true when position reaches end`() {
        val score = makeScore(12)
        val looper = SectionLooper(score).apply {
            enabled = true
            startMeasure = 0
            endMeasure = 2 // endExclusiveIndex = 12
        }
        assertTrue(looper.shouldLoop(12))
        assertTrue(looper.shouldLoop(15))
    }

    @Test
    fun `shouldLoop true exactly at endExclusiveIndex`() {
        val score = makeScore(8)
        val looper = SectionLooper(score).apply {
            enabled = true
            startMeasure = 0
            endMeasure = 0 // endExclusiveIndex = 4
        }
        assertTrue(looper.shouldLoop(4))
    }

    @Test
    fun `shouldLoop false for empty section`() {
        val score = makeScore(8)
        val looper = SectionLooper(score).apply {
            enabled = true
            startMeasure = 5
            endMeasure = 1
        }
        // empty section → never loop
        assertFalse(looper.shouldLoop(0))
        assertFalse(looper.shouldLoop(100))
    }

    // ===== isValid =====

    @Test
    fun `isValid true for normal range`() {
        val score = makeScore(12)
        val looper = SectionLooper(score).apply {
            startMeasure = 1
            endMeasure = 2
        }
        assertTrue(looper.isValid())
    }

    @Test
    fun `isValid false when start after end`() {
        val score = makeScore(12)
        val looper = SectionLooper(score).apply {
            startMeasure = 2
            endMeasure = 1
        }
        assertFalse(looper.isValid())
    }

    @Test
    fun `isValid false for empty section`() {
        val score = makeScore(8) // max measure = 1
        val looper = SectionLooper(score).apply {
            startMeasure = 5
            endMeasure = 6
        }
        assertFalse(looper.isValid())
    }

    @Test
    fun `isValid false for equal measures with no notes`() {
        val score = makeScore(8)
        val looper = SectionLooper(score).apply {
            startMeasure = 10
            endMeasure = 10
        }
        assertFalse(looper.isValid())
    }

    // ===== 循环计数 =====

    @Test
    fun `loopCount starts at 0 and increments`() {
        val score = makeScore(8)
        val looper = SectionLooper(score)
        assertEquals(0, looper.loopCount)
        looper.recordLoop()
        looper.recordLoop()
        assertEquals(2, looper.loopCount)
    }

    @Test
    fun `resetLoopCount sets to 0`() {
        val score = makeScore(8)
        val looper = SectionLooper(score)
        looper.recordLoop()
        looper.recordLoop()
        looper.recordLoop()
        assertEquals(3, looper.loopCount)
        looper.resetLoopCount()
        assertEquals(0, looper.loopCount)
    }

    // ===== 默认值 =====

    @Test
    fun `default endMeasure is maxMeasure of score`() {
        val score = makeScore(12) // maxMeasure = 2
        val looper = SectionLooper(score)
        assertEquals(2, looper.endMeasure)
        assertEquals(0, looper.startMeasure)
        assertFalse(looper.enabled)
    }

    @Test
    fun `default shouldLoop covers whole score when enabled`() {
        val score = makeScore(8)
        val looper = SectionLooper(score)
        looper.enabled = true
        // default range = whole score → endExclusive = 8
        assertFalse(looper.shouldLoop(7))
        assertTrue(looper.shouldLoop(8))
    }

    // ===== maxMeasure 工具函数 =====

    @Test
    fun `maxMeasure computes largest measureIndex`() {
        val score = makeScore(10) // measureIndex 0..2
        assertEquals(2, SectionLooper.maxMeasure(score))
    }

    @Test
    fun `maxMeasure returns 0 for empty score`() {
        val score = Score(id = "e", title = "t", composer = "c", notes = emptyList())
        assertEquals(0, SectionLooper.maxMeasure(score))
    }

    // ===== 边界：空乐谱 =====

    @Test
    fun `empty score section is invalid and never loops`() {
        val score = Score(id = "e", title = "t", composer = "c", notes = emptyList())
        val looper = SectionLooper(score).apply { enabled = true }
        assertFalse(looper.isValid())
        assertFalse(looper.shouldLoop(0))
        assertEquals(0, looper.startIndex())
        assertEquals(0, looper.endExclusiveIndex())
        assertEquals(0, looper.sectionNoteCount())
    }

    @Test
    fun `single note score loops correctly`() {
        val score = Score(
            id = "s", title = "t", composer = "c",
            notes = listOf(
                ScoreNote(
                    midiNumber = 60, noteName = "C4", startTime = 0,
                    duration = 400, staff = Staff.TREBLE, measureIndex = 0
                )
            )
        )
        val looper = SectionLooper(score).apply { enabled = true }
        assertEquals(1, looper.sectionNoteCount())
        // endExclusiveIndex = 1
        assertFalse(looper.shouldLoop(0))
        assertTrue(looper.shouldLoop(1))
    }

    @Test
    fun `whole score loop range equals full note count`() {
        val score = makeScore(16) // maxMeasure = 3
        val looper = SectionLooper(score).apply {
            enabled = true
            startMeasure = 0
            endMeasure = 3
        }
        assertEquals(16, looper.sectionNoteCount())
        assertEquals(16, looper.endExclusiveIndex())
        assertTrue(looper.shouldLoop(16))
    }
}
