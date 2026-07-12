package com.pianocompanion.rhythmdictation

import org.junit.Assert.*
import org.junit.Test

/**
 * 节奏听写出题引擎单元测试。
 */
class RhythmDictationEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = RhythmDictationEngine.withSeed(42)
        val e2 = RhythmDictationEngine.withSeed(42)
        val q1 = e1.generate(RhythmDictationDifficulty.BEGINNER)
        val q2 = e2.generate(RhythmDictationDifficulty.BEGINNER)
        assertEquals(q1.cell, q2.cell)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val engine2 = RhythmDictationEngine.withSeed(999)
        // 多次生成，至少有一次不同
        var foundDifferent = false
        for (i in 1..20) {
            val q1 = engine.generate(RhythmDictationDifficulty.ADVANCED)
            val q2 = engine2.generate(RhythmDictationDifficulty.ADVANCED)
            if (q1.cell != q2.cell) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为4`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.BEGINNER)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为5`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `所有选项唯一`() {
        val engine = RhythmDictationEngine.withSeed(7)
        for (difficulty in RhythmDictationDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `正确答案在选项中`() {
        val engine = RhythmDictationEngine.withSeed(3)
        for (difficulty in RhythmDictationDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue("正确答案不在选项中: ${q.correctAnswer}", q.answerChoices.contains(q.correctAnswer))
        }
    }

    // ── 正确答案匹配类型 ────────────────────────────────

    @Test
    fun `correctAnswer 等于符号加显示名`() {
        val engine = RhythmDictationEngine.withSeed(5)
        val q = engine.generate(RhythmDictationDifficulty.ADVANCED)
        assertEquals("${q.cell.symbol}  ${q.cell.displayName}", q.correctAnswer)
    }

    // ── forDifficulty 覆盖正确类型 ──────────────────────

    @Test
    fun `初级只从4种基础型中选题`() {
        val engine = RhythmDictationEngine.withSeed(10)
        val beginnerCells = RhythmCellType.BEGINNER_CELLS.toSet()
        for (i in 1..50) {
            val q = engine.generate(RhythmDictationDifficulty.BEGINNER)
            assertTrue("题 $i: ${q.cell} 不在初级范围内", q.cell in beginnerCells)
        }
    }

    @Test
    fun `中级从7种类型中选题`() {
        val engine = RhythmDictationEngine.withSeed(10)
        val intermediateCells = RhythmCellType.INTERMEDIATE_CELLS.toSet()
        for (i in 1..50) {
            val q = engine.generate(RhythmDictationDifficulty.INTERMEDIATE)
            assertTrue(q.cell in intermediateCells)
        }
    }

    @Test
    fun `高级从全部8种类型中选题`() {
        val engine = RhythmDictationEngine.withSeed(10)
        val allCells = RhythmCellType.ALL.toSet()
        for (i in 1..50) {
            val q = engine.generate(RhythmDictationDifficulty.ADVANCED)
            assertTrue(q.cell in allCells)
        }
    }

    // ── 节奏单元时值正确性 ──────────────────────────────

    @Test
    fun `所有节奏单元时值之和为2拍`() {
        for (cell in RhythmCellType.ALL) {
            assertEquals(2.0, cell.totalBeats, 0.001)
        }
    }

    @Test
    fun `两个四分音符时值为1_0_1_0`() {
        assertEquals(listOf(1.0, 1.0), RhythmCellType.TWO_QUARTERS.durations)
    }

    @Test
    fun `四个八分音符时值为4个0_5`() {
        assertEquals(listOf(0.5, 0.5, 0.5, 0.5), RhythmCellType.FOUR_EIGHTHS.durations)
    }

    @Test
    fun `二分音符时值为单个2_0`() {
        assertEquals(listOf(2.0), RhythmCellType.HALF_NOTE.durations)
    }

    @Test
    fun `附点四分接八分时值为1_5_0_5`() {
        assertEquals(listOf(1.5, 0.5), RhythmCellType.DOTTED_QUARTER_EIGHTH.durations)
    }

    @Test
    fun `八分接附点四分时值为0_5_1_5`() {
        assertEquals(listOf(0.5, 1.5), RhythmCellType.EIGHTH_DOTTED_QUARTER.durations)
    }

    @Test
    fun `切分节奏时值为0_5_1_0_0_5`() {
        assertEquals(listOf(0.5, 1.0, 0.5), RhythmCellType.SYNCOPATED.durations)
    }

    @Test
    fun `noteCount 等于 durations 大小`() {
        for (cell in RhythmCellType.ALL) {
            assertEquals(cell.durations.size, cell.noteCount)
        }
    }

    // ── computeOnsetTimes 正确性 ────────────────────────

    @Test
    fun `onset 数量等于音符数乘以重复次数`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val onsets = engine.computeOnsetTimes(
            RhythmCellType.TWO_QUARTERS,
            RhythmDictationTempo.SLOW,
            repeatCount = 3
        )
        assertEquals(2 * 3, onsets.size)
    }

    @Test
    fun `第一个 onset 等于前导静音`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val onsets = engine.computeOnsetTimes(
            RhythmCellType.FOUR_EIGHTHS,
            RhythmDictationTempo.MEDIUM,
            repeatCount = 1
        )
        assertEquals(RhythmDictationEngine.LEAD_SILENCE_MS, onsets.first(), 0.01)
    }

    @Test
    fun `onset 间距正确反映时值`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val tempo = RhythmDictationTempo.SLOW // 72 BPM → beatMs = 833.33ms
        val beatMs = tempo.beatMs
        val onsets = engine.computeOnsetTimes(
            RhythmCellType.DOTTED_QUARTER_EIGHTH, // [1.5, 0.5]
            tempo,
            repeatCount = 1
        )
        assertEquals(2, onsets.size)
        // onset[0] = LEAD_SILENCE_MS
        // onset[1] = onset[0] + 1.5 * beatMs
        assertEquals(onsets[0] + 1.5 * beatMs, onsets[1], 0.1)
    }

    @Test
    fun `重复播放时每次的起始间隔正确`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val tempo = RhythmDictationTempo.FAST
        val beatMs = tempo.beatMs
        val cellMs = 2.0 * beatMs
        val onsets = engine.computeOnsetTimes(
            RhythmCellType.HALF_NOTE, // [2.0] → 1 click per repetition
            tempo,
            repeatCount = 2
        )
        // 第 1 次 onset[0] = LEAD_SILENCE_MS
        // 第 2 次 onset[1] = LEAD_SILENCE_MS + cellMs
        assertEquals(onsets[0] + cellMs, onsets[1], 0.1)
    }

    // ── 速度正确性 ──────────────────────────────────────

    @Test
    fun `慢速 beatMs 正确`() {
        assertEquals(60000.0 / 72, RhythmDictationTempo.SLOW.beatMs, 0.01)
    }

    @Test
    fun `中速 beatMs 正确`() {
        assertEquals(60000.0 / 100, RhythmDictationTempo.MEDIUM.beatMs, 0.01)
    }

    @Test
    fun `快速 beatMs 正确`() {
        assertEquals(60000.0 / 132, RhythmDictationTempo.FAST.beatMs, 0.01)
    }

    // ── 难度配置 ────────────────────────────────────────

    @Test
    fun `初级有4种基础节奏单元`() {
        assertEquals(4, RhythmCellType.BEGINNER_CELLS.size)
    }

    @Test
    fun `中级有7种节奏单元`() {
        assertEquals(7, RhythmCellType.INTERMEDIATE_CELLS.size)
    }

    @Test
    fun `全部有8种节奏单元`() {
        assertEquals(8, RhythmCellType.ALL.size)
    }

    @Test
    fun `难度选项数正确`() {
        assertEquals(4, RhythmDictationDifficulty.BEGINNER.choiceCount)
        assertEquals(4, RhythmDictationDifficulty.INTERMEDIATE.choiceCount)
        assertEquals(5, RhythmDictationDifficulty.ADVANCED.choiceCount)
    }

    @Test
    fun `forDifficulty 包含关系正确`() {
        val beginner = RhythmCellType.forDifficulty(RhythmDictationDifficulty.BEGINNER)
        val intermediate = RhythmCellType.forDifficulty(RhythmDictationDifficulty.INTERMEDIATE)
        val advanced = RhythmCellType.forDifficulty(RhythmDictationDifficulty.ADVANCED)
        assertTrue(beginner.all { it in intermediate })
        assertTrue(intermediate.all { it in advanced })
        assertEquals(RhythmCellType.ALL, advanced)
    }

    @Test
    fun `初级不含附点和切分`() {
        val cells = RhythmCellType.forDifficulty(RhythmDictationDifficulty.BEGINNER)
        assertFalse(RhythmCellType.DOTTED_QUARTER_EIGHTH in cells)
        assertFalse(RhythmCellType.EIGHTH_DOTTED_QUARTER in cells)
        assertFalse(RhythmCellType.SYNCOPATED in cells)
        assertFalse(RhythmCellType.HALF_NOTE in cells)
    }

    @Test
    fun `高级包含切分`() {
        val cells = RhythmCellType.forDifficulty(RhythmDictationDifficulty.ADVANCED)
        assertTrue(RhythmCellType.SYNCOPATED in cells)
    }

    // ── Question 属性 ───────────────────────────────────

    @Test
    fun `fullName 包含速度和类型名`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.BEGINNER, RhythmDictationTempo.FAST)
        assertTrue(q.fullName.contains(RhythmDictationTempo.FAST.displayName))
        assertTrue(q.fullName.contains(q.cell.displayName))
    }

    @Test
    fun `repeatCount 默认为2`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.BEGINNER)
        assertEquals(2, q.repeatCount)
    }

    @Test
    fun `自定义 repeatCount 传入正确`() {
        val engine = RhythmDictationEngine.withSeed(1)
        val q = engine.generate(RhythmDictationDifficulty.BEGINNER, repeatCount = 3)
        assertEquals(3, q.repeatCount)
    }
}
