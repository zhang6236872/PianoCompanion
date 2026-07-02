package com.pianocompanion.interval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IntervalEngine] 单元测试。
 *
 * 验证：
 * - 谱表位置 → MIDI 换算（高音/低音谱号）
 * - MIDI → 音名字母映射
 * - MIDI → 八度映射
 * - 音程分类（度数 + 半音差 → 性质）
 * - 出题确定性（相同种子相同题目）
 * - 选项构建（正确答案在选项中、选项唯一、选项数量）
 * - 各难度 × 各谱号的出题覆盖
 * - 两个音符高低关系正确
 */
class IntervalEngineTest {

    private val engine = IntervalEngine()

    // ── 谱表位置 → MIDI（高音谱号）──────────────────────

    @Test
    fun `treble clef bottom line is E4`() {
        assertEquals(64, engine.diatonicStepToMidi(IntervalClef.TREBLE, 0))
    }

    @Test
    fun `treble clef lines are E4 G4 B4 D5 F5`() {
        assertEquals(64, engine.diatonicStepToMidi(IntervalClef.TREBLE, 0)) // E4
        assertEquals(67, engine.diatonicStepToMidi(IntervalClef.TREBLE, 2)) // G4
        assertEquals(71, engine.diatonicStepToMidi(IntervalClef.TREBLE, 4)) // B4
        assertEquals(74, engine.diatonicStepToMidi(IntervalClef.TREBLE, 6)) // D5
        assertEquals(77, engine.diatonicStepToMidi(IntervalClef.TREBLE, 8)) // F5
    }

    @Test
    fun `treble clef spaces are F4 A4 C5 E5`() {
        assertEquals(65, engine.diatonicStepToMidi(IntervalClef.TREBLE, 1)) // F4
        assertEquals(69, engine.diatonicStepToMidi(IntervalClef.TREBLE, 3)) // A4
        assertEquals(72, engine.diatonicStepToMidi(IntervalClef.TREBLE, 5)) // C5
        assertEquals(76, engine.diatonicStepToMidi(IntervalClef.TREBLE, 7)) // E5
    }

    // ── 谱表位置 → MIDI（低音谱号）──────────────────────

    @Test
    fun `bass clef bottom line is G2`() {
        assertEquals(43, engine.diatonicStepToMidi(IntervalClef.BASS, 0))
    }

    @Test
    fun `bass clef lines are G2 B2 D3 F3 A3`() {
        assertEquals(43, engine.diatonicStepToMidi(IntervalClef.BASS, 0)) // G2
        assertEquals(47, engine.diatonicStepToMidi(IntervalClef.BASS, 2)) // B2
        assertEquals(50, engine.diatonicStepToMidi(IntervalClef.BASS, 4)) // D3
        assertEquals(53, engine.diatonicStepToMidi(IntervalClef.BASS, 6)) // F3
        assertEquals(57, engine.diatonicStepToMidi(IntervalClef.BASS, 8)) // A3
    }

    @Test
    fun `bass clef spaces are A2 C3 E3 G3`() {
        assertEquals(45, engine.diatonicStepToMidi(IntervalClef.BASS, 1)) // A2
        assertEquals(48, engine.diatonicStepToMidi(IntervalClef.BASS, 3)) // C3
        assertEquals(52, engine.diatonicStepToMidi(IntervalClef.BASS, 5)) // E3
        assertEquals(55, engine.diatonicStepToMidi(IntervalClef.BASS, 7)) // G3
    }

    // ── MIDI → 音名字母 ─────────────────────────────────

    @Test
    fun `midi to letter name natural notes`() {
        assertEquals("C", engine.midiToLetterName(60)) // C4
        assertEquals("D", engine.midiToLetterName(62)) // D4
        assertEquals("E", engine.midiToLetterName(64)) // E4
        assertEquals("F", engine.midiToLetterName(65)) // F4
        assertEquals("G", engine.midiToLetterName(67)) // G4
        assertEquals("A", engine.midiToLetterName(69)) // A4
        assertEquals("B", engine.midiToLetterName(71)) // B4
    }

    @Test
    fun `midi to letter name returns empty for accidentals`() {
        assertEquals("", engine.midiToLetterName(61)) // C#4
        assertEquals("", engine.midiToLetterName(63)) // D#4
        assertEquals("", engine.midiToLetterName(66)) // F#4
    }

    // ── MIDI → 八度 ──────────────────────────────────────

    @Test
    fun `midi to octave`() {
        assertEquals(4, engine.midiToOctave(60)) // C4
        assertEquals(4, engine.midiToOctave(71)) // B4
        assertEquals(5, engine.midiToOctave(72)) // C5
        assertEquals(2, engine.midiToOctave(43)) // G2
        assertEquals(0, engine.midiToOctave(12)) // C0
    }

    // ── 音程分类 ──────────────────────────────────────────

    @Test
    fun `classify major second`() {
        val interval = Interval.classify(IntervalNumber.SECOND, 2)
        assertNotNull(interval)
        assertEquals(IntervalQuality.MAJOR, interval!!.quality)
        assertEquals("大二度", interval.displayName)
    }

    @Test
    fun `classify minor second`() {
        val interval = Interval.classify(IntervalNumber.SECOND, 1)
        assertNotNull(interval)
        assertEquals(IntervalQuality.MINOR, interval!!.quality)
        assertEquals("小二度", interval.displayName)
    }

    @Test
    fun `classify major third`() {
        val interval = Interval.classify(IntervalNumber.THIRD, 4)
        assertNotNull(interval)
        assertEquals(IntervalQuality.MAJOR, interval!!.quality)
    }

    @Test
    fun `classify minor third`() {
        val interval = Interval.classify(IntervalNumber.THIRD, 3)
        assertNotNull(interval)
        assertEquals(IntervalQuality.MINOR, interval!!.quality)
    }

    @Test
    fun `classify perfect fifth`() {
        val interval = Interval.classify(IntervalNumber.FIFTH, 7)
        assertNotNull(interval)
        assertEquals(IntervalQuality.PERFECT, interval!!.quality)
        assertEquals("纯五度", interval.displayName)
    }

    @Test
    fun `classify augmented fourth`() {
        val interval = Interval.classify(IntervalNumber.FOURTH, 6)
        assertNotNull(interval)
        assertEquals(IntervalQuality.AUGMENTED, interval!!.quality)
        assertEquals("增四度", interval.displayName)
    }

    @Test
    fun `classify diminished fifth`() {
        val interval = Interval.classify(IntervalNumber.FIFTH, 6)
        assertNotNull(interval)
        assertEquals(IntervalQuality.DIMINISHED, interval!!.quality)
        assertEquals("减五度", interval.displayName)
    }

    @Test
    fun `classify perfect octave`() {
        val interval = Interval.classify(IntervalNumber.OCTAVE, 12)
        assertNotNull(interval)
        assertEquals(IntervalQuality.PERFECT, interval!!.quality)
    }

    // ── 出题测试 ──────────────────────────────────────────

    @Test
    fun `generate returns valid question with all fields`() {
        val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
        assertNotNull(q)
        assertEquals(IntervalClef.TREBLE, q.clef)
        assertEquals(IntervalDifficulty.BEGINNER, q.difficulty)
        assertTrue(q.lowerMidi > 0)
        assertTrue(q.higherMidi > 0)
        assertTrue(q.lowerLetterName.isNotEmpty())
        assertTrue(q.higherLetterName.isNotEmpty())
        assertTrue(q.answerChoices.isNotEmpty())
        assertTrue(q.correctAnswer.isNotEmpty())
    }

    @Test
    fun `generate produces deterministic output with same seed`() {
        val e1 = IntervalEngine.withSeed(42L)
        val e2 = IntervalEngine.withSeed(42L)
        val q1 = e1.generate(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE)
        val q2 = e2.generate(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE)
        assertEquals(q1.lowerStaffStep, q2.lowerStaffStep)
        assertEquals(q1.higherStaffStep, q2.higherStaffStep)
        assertEquals(q1.lowerMidi, q2.lowerMidi)
        assertEquals(q1.higherMidi, q2.higherMidi)
        assertEquals(q1.interval, q2.interval)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `higher note is always above lower note`() {
        repeat(100) {
            val clef = if (it % 2 == 0) IntervalClef.TREBLE else IntervalClef.BASS
            val diff = IntervalDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            assertTrue(
                "较高音 step(${q.higherStaffStep}) 应大于较低音 step(${q.lowerStaffStep})",
                q.higherStaffStep > q.lowerStaffStep
            )
            assertTrue(
                "较高音 MIDI(${q.higherMidi}) 应大于较低音 MIDI(${q.lowerMidi})",
                q.higherMidi > q.lowerMidi
            )
        }
    }

    @Test
    fun `correct answer is always in choices`() {
        repeat(50) {
            val clef = if (it % 2 == 0) IntervalClef.TREBLE else IntervalClef.BASS
            val diff = IntervalDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            assertTrue(
                "正确答案 ${q.correctAnswer} 不在选项 ${q.answerChoices} 中",
                q.answerChoices.contains(q.correctAnswer)
            )
        }
    }

    @Test
    fun `choices are unique`() {
        repeat(50) {
            val clef = if (it % 2 == 0) IntervalClef.TREBLE else IntervalClef.BASS
            val diff = IntervalDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            assertEquals(
                "选项有重复: ${q.answerChoices}",
                q.answerChoices.size,
                q.answerChoices.toSet().size
            )
        }
    }

    @Test
    fun `choices count is exactly 4`() {
        repeat(50) {
            val clef = if (it % 2 == 0) IntervalClef.TREBLE else IntervalClef.BASS
            val diff = IntervalDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            assertEquals(
                "选项数量应为 4，实际 ${q.answerChoices.size}",
                4, q.answerChoices.size
            )
        }
    }

    @Test
    fun `beginner difficulty does not require quality`() {
        repeat(50) {
            val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
            assertFalse("初级不应要求判断性质", q.requiresQuality)
        }
    }

    @Test
    fun `intermediate and advanced require quality`() {
        val q1 = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE)
        assertTrue("中级应要求判断性质", q1.requiresQuality)
        val q2 = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.ADVANCED)
        assertTrue("高级应要求判断性质", q2.requiresQuality)
    }

    @Test
    fun `beginner correct answer is interval number only`() {
        repeat(20) {
            val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
            // 初级正确答案应仅为度数（如"三度"），不包含性质前缀
            assertEquals(q.interval.number.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `intermediate correct answer includes quality`() {
        repeat(20) {
            val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.INTERMEDIATE)
            // 中级正确答案应包含性质+度数（如"大三度"）
            assertEquals(q.interval.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `beginner max span is fifth`() {
        // 初级最大跨度为 4 步（五度）
        repeat(100) {
            val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.BEGINNER)
            val span = q.higherStaffStep - q.lowerStaffStep
            assertTrue(
                "初级跨度 $span 超出最大五度(4步)",
                span in 1..4
            )
        }
    }

    @Test
    fun `intermediate max span is seventh`() {
        // 中级最大跨度为 6 步（七度）
        repeat(100) {
            val q = engine.generate(IntervalClef.BASS, IntervalDifficulty.INTERMEDIATE)
            val span = q.higherStaffStep - q.lowerStaffStep
            assertTrue(
                "中级跨度 $span 超出最大七度(6步)",
                span in 1..6
            )
        }
    }

    @Test
    fun `advanced max span is octave`() {
        // 高级最大跨度为 7 步（八度）
        repeat(100) {
            val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.ADVANCED)
            val span = q.higherStaffStep - q.lowerStaffStep
            assertTrue(
                "高级跨度 $span 超出最大八度(7步)",
                span in 1..7
            )
        }
    }

    @Test
    fun `interval classification matches midi difference`() {
        repeat(100) {
            val clef = if (it % 2 == 0) IntervalClef.TREBLE else IntervalClef.BASS
            val diff = IntervalDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            val semitoneDiff = q.higherMidi - q.lowerMidi
            val expected = Interval.classify(q.interval.number, semitoneDiff)
            assertNotNull("度数+半音差应能分类: ${q.interval.number}, $semitoneDiff", expected)
            assertEquals(expected, q.interval)
        }
    }

    @Test
    fun `diatonic step matches interval number`() {
        repeat(100) {
            val clef = if (it % 2 == 0) IntervalClef.TREBLE else IntervalClef.BASS
            val diff = IntervalDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            val diatonicDiff = q.higherStaffStep - q.lowerStaffStep
            val expectedNumber = IntervalNumber.fromDiatonicSteps(diatonicDiff)
            assertEquals(expectedNumber, q.interval.number)
        }
    }

    @Test
    fun `midi note matches staffStep for treble clef`() {
        repeat(50) {
            val q = engine.generate(IntervalClef.TREBLE, IntervalDifficulty.ADVANCED)
            val expectedLow = engine.diatonicStepToMidi(IntervalClef.TREBLE, q.lowerStaffStep)
            val expectedHigh = engine.diatonicStepToMidi(IntervalClef.TREBLE, q.higherStaffStep)
            assertEquals(expectedLow, q.lowerMidi)
            assertEquals(expectedHigh, q.higherMidi)
        }
    }

    @Test
    fun `midi note matches staffStep for bass clef`() {
        repeat(50) {
            val q = engine.generate(IntervalClef.BASS, IntervalDifficulty.ADVANCED)
            val expectedLow = engine.diatonicStepToMidi(IntervalClef.BASS, q.lowerStaffStep)
            val expectedHigh = engine.diatonicStepToMidi(IntervalClef.BASS, q.higherStaffStep)
            assertEquals(expectedLow, q.lowerMidi)
            assertEquals(expectedHigh, q.higherMidi)
        }
    }

    @Test
    fun `E to F interval is minor second`() {
        // E4(64) → F4(65)，步距 1，半音差 1 → 小二度
        val interval = Interval.classify(IntervalNumber.SECOND, 1)
        assertEquals(IntervalQuality.MINOR, interval!!.quality)
    }

    @Test
    fun `F to B interval is augmented fourth`() {
        // F4(65) → B4(71)，步距 3，半音差 6 → 增四度（三全音）
        val interval = Interval.classify(IntervalNumber.FOURTH, 6)
        assertEquals(IntervalQuality.AUGMENTED, interval!!.quality)
    }

    @Test
    fun `B to F interval is diminished fifth`() {
        // B4(71) → F5(77)，步距 4，半音差 6 → 减五度
        val interval = Interval.classify(IntervalNumber.FIFTH, 6)
        assertEquals(IntervalQuality.DIMINISHED, interval!!.quality)
    }
}
