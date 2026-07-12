package com.pianocompanion.meterrecognition

import org.junit.Assert.*
import org.junit.Test

/**
 * 拍号听辨出题引擎单元测试。
 */
class MeterRecognitionEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = MeterRecognitionEngine.withSeed(42)
        val e2 = MeterRecognitionEngine.withSeed(42)
        val q1 = e1.generate(MeterRecognitionDifficulty.BEGINNER)
        val q2 = e2.generate(MeterRecognitionDifficulty.BEGINNER)
        assertEquals(q1.meter, q2.meter)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val engine2 = MeterRecognitionEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..20) {
            val q1 = engine.generate(MeterRecognitionDifficulty.ADVANCED)
            val q2 = engine2.generate(MeterRecognitionDifficulty.ADVANCED)
            if (q1.meter != q2.meter) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为4`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.ADVANCED)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `所有选项唯一`() {
        val engine = MeterRecognitionEngine.withSeed(7)
        for (difficulty in MeterRecognitionDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `正确答案在选项中`() {
        val engine = MeterRecognitionEngine.withSeed(3)
        for (difficulty in MeterRecognitionDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue("正确答案不在选项中: ${q.correctAnswer}", q.answerChoices.contains(q.correctAnswer))
        }
    }

    @Test
    fun `选项不含正确答案以外的重复项`() {
        val engine = MeterRecognitionEngine.withSeed(100)
        for (i in 1..50) {
            for (difficulty in MeterRecognitionDifficulty.ALL) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    // ── 难度对应的拍号池 ─────────────────────────────────

    @Test
    fun `初级拍号池为2-4 3-4 4-4`() {
        assertEquals(listOf(MeterType.TWO_FOUR, MeterType.THREE_FOUR, MeterType.FOUR_FOUR), MeterType.BEGINNER_METERS)
    }

    @Test
    fun `中级拍号池加入6-8`() {
        assertEquals(4, MeterType.INTERMEDIATE_METERS.size)
        assertTrue(MeterType.INTERMEDIATE_METERS.contains(MeterType.SIX_EIGHT))
    }

    @Test
    fun `高级拍号池包含全部6种`() {
        assertEquals(6, MeterType.ALL.size)
        assertEquals(MeterType.ALL, MeterType.forDifficulty(MeterRecognitionDifficulty.ADVANCED))
    }

    // ── 多次生成覆盖性 ─────────────────────────────────

    @Test
    fun `初级难度多次生成覆盖所有3种拍号`() {
        val engine = MeterRecognitionEngine.withSeed(50)
        val seen = mutableSetOf<MeterType>()
        for (i in 1..100) {
            seen.add(engine.generate(MeterRecognitionDifficulty.BEGINNER).meter)
        }
        assertEquals(3, seen.size)
    }

    @Test
    fun `高级难度多次生成覆盖所有6种拍号`() {
        val engine = MeterRecognitionEngine.withSeed(50)
        val seen = mutableSetOf<MeterType>()
        for (i in 1..200) {
            seen.add(engine.generate(MeterRecognitionDifficulty.ADVANCED).meter)
        }
        assertEquals(6, seen.size)
    }

    // ── 题目属性 ──────────────────────────────────────

    @Test
    fun `题目包含难度和速度`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(
            MeterRecognitionDifficulty.INTERMEDIATE,
            MeterRecognitionTempo.FAST
        )
        assertEquals(MeterRecognitionDifficulty.INTERMEDIATE, q.difficulty)
        assertEquals(MeterRecognitionTempo.FAST, q.tempo)
    }

    @Test
    fun `题目正确答案格式正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        assertTrue(q.correctAnswer.contains(q.meter.symbol))
        assertTrue(q.correctAnswer.contains(q.meter.displayName))
    }

    @Test
    fun `默认measureRepeat为4`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        assertEquals(4, q.measureRepeat)
    }

    @Test
    fun `totalClicks等于每小节拍数乘重复次数`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val q = engine.generate(MeterRecognitionDifficulty.BEGINNER)
        assertEquals(q.meter.beatsPerMeasure * q.measureRepeat, q.totalClicks)
    }

    // ── AccentPattern 不变量 ───────────────────────────

    @Test
    fun `所有拍号的accentPattern长度等于beatsPerMeasure`() {
        for (meter in MeterType.ALL) {
            assertEquals(meter.beatsPerMeasure, meter.accentPattern.size)
        }
    }

    @Test
    fun `所有拍号的第一拍为STRONG`() {
        for (meter in MeterType.ALL) {
            assertEquals(AccentLevel.STRONG, meter.accentPattern[0])
        }
    }

    // ── onset 时间计算 ─────────────────────────────────

    @Test
    fun `computeOnsetTimes慢速2-4拍4小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val onsets = engine.computeOnsetTimes(
            MeterType.TWO_FOUR,
            MeterRecognitionTempo.SLOW,
            measureRepeat = 4
        )
        // 4 小节 × 2 拍 = 8 个 onset
        assertEquals(8, onsets.size)
        // 第一个 onset = LEAD_SILENCE_MS
        assertEquals(400.0, onsets[0], 0.01)
        // 第二个 onset = 400 + 500 = 900
        assertEquals(900.0, onsets[1], 0.01)
        // 第三个 onset = 400 + 2*500 = 1400
        assertEquals(1400.0, onsets[2], 0.01)
        // 第八个 onset = 400 + 7*500 = 3900
        assertEquals(3900.0, onsets[7], 0.01)
    }

    @Test
    fun `computeOnsetTimes快速4-4拍2小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val onsets = engine.computeOnsetTimes(
            MeterType.FOUR_FOUR,
            MeterRecognitionTempo.FAST,
            measureRepeat = 2
        )
        // 2 小节 × 4 拍 = 8 个 onset
        assertEquals(8, onsets.size)
        // 间隔 300ms
        assertEquals(400.0, onsets[0], 0.01)
        assertEquals(700.0, onsets[1], 0.01)
        assertEquals(1000.0, onsets[2], 0.01)
        assertEquals(1300.0, onsets[3], 0.01)
        // 第二小节开始 = 400 + 4*300 = 1600
        assertEquals(1600.0, onsets[4], 0.01)
    }

    @Test
    fun `computeOnsetTimes中速3-4拍3小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val onsets = engine.computeOnsetTimes(
            MeterType.THREE_FOUR,
            MeterRecognitionTempo.MEDIUM,
            measureRepeat = 3
        )
        // 3 小节 × 3 拍 = 9 个 onset
        assertEquals(9, onsets.size)
        // 间隔 400ms
        assertEquals(400.0, onsets[0], 0.01)
        assertEquals(800.0, onsets[1], 0.01)
        assertEquals(1200.0, onsets[2], 0.01)
        // 第二小节 = 400 + 3*400 = 1600
        assertEquals(1600.0, onsets[3], 0.01)
        // 第三小节 = 400 + 6*400 = 2800
        assertEquals(2800.0, onsets[6], 0.01)
    }

    @Test
    fun `computeOnsetTimes空重复返回空列表`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val onsets = engine.computeOnsetTimes(
            MeterType.FOUR_FOUR,
            MeterRecognitionTempo.SLOW,
            measureRepeat = 0
        )
        assertTrue(onsets.isEmpty())
    }

    // ── AccentPattern 计算 ────────────────────────────

    @Test
    fun `computeAccentPattern 2-4拍4小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val accents = engine.computeAccentPattern(MeterType.TWO_FOUR, measureRepeat = 4)
        assertEquals(8, accents.size)
        // 每小节: STRONG, WEAK
        assertEquals(AccentLevel.STRONG, accents[0])
        assertEquals(AccentLevel.WEAK, accents[1])
        assertEquals(AccentLevel.STRONG, accents[2])
        assertEquals(AccentLevel.WEAK, accents[3])
        assertEquals(AccentLevel.STRONG, accents[6])
        assertEquals(AccentLevel.WEAK, accents[7])
    }

    @Test
    fun `computeAccentPattern 4-4拍2小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val accents = engine.computeAccentPattern(MeterType.FOUR_FOUR, measureRepeat = 2)
        assertEquals(8, accents.size)
        // 每小节: STRONG, WEAK, MEDIUM, WEAK
        assertEquals(AccentLevel.STRONG, accents[0])
        assertEquals(AccentLevel.WEAK, accents[1])
        assertEquals(AccentLevel.MEDIUM, accents[2])
        assertEquals(AccentLevel.WEAK, accents[3])
        // 第二小节重复
        assertEquals(AccentLevel.STRONG, accents[4])
        assertEquals(AccentLevel.MEDIUM, accents[6])
    }

    @Test
    fun `computeAccentPattern 6-8拍1小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val accents = engine.computeAccentPattern(MeterType.SIX_EIGHT, measureRepeat = 1)
        assertEquals(6, accents.size)
        // STRONG, WEAK, WEAK, MEDIUM, WEAK, WEAK
        assertEquals(AccentLevel.STRONG, accents[0])
        assertEquals(AccentLevel.WEAK, accents[1])
        assertEquals(AccentLevel.WEAK, accents[2])
        assertEquals(AccentLevel.MEDIUM, accents[3])
        assertEquals(AccentLevel.WEAK, accents[4])
        assertEquals(AccentLevel.WEAK, accents[5])
    }

    @Test
    fun `computeAccentPattern 7-8拍2小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val accents = engine.computeAccentPattern(MeterType.SEVEN_EIGHT, measureRepeat = 2)
        assertEquals(14, accents.size)
        // 每小节: STRONG, WEAK, MEDIUM, WEAK, MEDIUM, WEAK, WEAK
        assertEquals(AccentLevel.STRONG, accents[0])
        assertEquals(AccentLevel.MEDIUM, accents[2])
        assertEquals(AccentLevel.MEDIUM, accents[4])
        assertEquals(AccentLevel.STRONG, accents[7])
    }

    @Test
    fun `computeAccentPattern 5-4拍1小节正确`() {
        val engine = MeterRecognitionEngine.withSeed(1)
        val accents = engine.computeAccentPattern(MeterType.FIVE_FOUR, measureRepeat = 1)
        assertEquals(5, accents.size)
        // STRONG, WEAK, WEAK, MEDIUM, WEAK
        assertEquals(AccentLevel.STRONG, accents[0])
        assertEquals(AccentLevel.WEAK, accents[1])
        assertEquals(AccentLevel.WEAK, accents[2])
        assertEquals(AccentLevel.MEDIUM, accents[3])
        assertEquals(AccentLevel.WEAK, accents[4])
    }

    // ── AccentLevel 属性 ──────────────────────────────

    @Test
    fun `STRONG比MEDIUM振幅大`() {
        assertTrue(AccentLevel.STRONG.amplitude > AccentLevel.MEDIUM.amplitude)
    }

    @Test
    fun `MEDIUM比WEAK振幅大`() {
        assertTrue(AccentLevel.MEDIUM.amplitude > AccentLevel.WEAK.amplitude)
    }

    @Test
    fun `STRONG比MEDIUM频率高`() {
        assertTrue(AccentLevel.STRONG.frequency > AccentLevel.MEDIUM.frequency)
    }

    @Test
    fun `MEDIUM比WEAK频率高`() {
        assertTrue(AccentLevel.MEDIUM.frequency > AccentLevel.WEAK.frequency)
    }
}
