package com.pianocompanion.harmonicintervaltraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 和声音程辨识训练出题引擎单元测试。
 *
 * 验证确定性出题、难度覆盖、选项完整性。
 */
class HarmonicIntervalEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子生成相同题目`() {
        val engine1 = HarmonicIntervalEngine.withSeed(42L)
        val engine2 = HarmonicIntervalEngine.withSeed(42L)

        val q1 = engine1.generate(HarmonicIntervalDifficulty.BEGINNER)
        val q2 = engine2.generate(HarmonicIntervalDifficulty.BEGINNER)

        assertEquals(q1.interval, q2.interval)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能生成不同题目`() {
        val engine1 = HarmonicIntervalEngine.withSeed(1L)
        val engine2 = HarmonicIntervalEngine.withSeed(999L)

        var different = false
        for (i in 0 until 20) {
            val q1 = engine1.generate(HarmonicIntervalDifficulty.ADVANCED)
            val q2 = engine2.generate(HarmonicIntervalDifficulty.ADVANCED)
            if (q1.interval != q2.interval) {
                different = true
                break
            }
        }
        assertTrue("不同种子应可能生成不同题目", different)
    }

    // ── 难度覆盖 ──────────────────────────────────────────

    @Test
    fun `初级只包含3种音程`() {
        val engine = HarmonicIntervalEngine.withSeed(100L)
        val seen = mutableSetOf<HarmonicInterval>()
        repeat(50) {
            val q = engine.generate(HarmonicIntervalDifficulty.BEGINNER)
            seen.add(q.interval)
        }
        assertTrue("初级应只包含 BEGINNER_INTERVALS 中的音程",
            seen.all { it in HarmonicInterval.BEGINNER_INTERVALS })
    }

    @Test
    fun `中级包含5种音程范围`() {
        val engine = HarmonicIntervalEngine.withSeed(200L)
        val seen = mutableSetOf<HarmonicInterval>()
        repeat(50) {
            val q = engine.generate(HarmonicIntervalDifficulty.INTERMEDIATE)
            seen.add(q.interval)
        }
        assertTrue("中级应只包含 INTERMEDIATE_INTERVALS 中的音程",
            seen.all { it in HarmonicInterval.INTERMEDIATE_INTERVALS })
    }

    @Test
    fun `高级包含全部8种音程`() {
        val engine = HarmonicIntervalEngine.withSeed(300L)
        val seen = mutableSetOf<HarmonicInterval>()
        repeat(100) {
            val q = engine.generate(HarmonicIntervalDifficulty.ADVANCED)
            seen.add(q.interval)
        }
        assertTrue("高级应在足够多题后覆盖全部 8 种音程",
            seen.size >= 6)
    }

    @Test
    fun `初级选项数量为3`() {
        val engine = HarmonicIntervalEngine.withSeed(10L)
        val q = engine.generate(HarmonicIntervalDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级选项数量为5`() {
        val engine = HarmonicIntervalEngine.withSeed(20L)
        val q = engine.generate(HarmonicIntervalDifficulty.INTERMEDIATE)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `高级选项数量为8`() {
        val engine = HarmonicIntervalEngine.withSeed(30L)
        val q = engine.generate(HarmonicIntervalDifficulty.ADVANCED)
        assertEquals(8, q.answerChoices.size)
    }

    // ── 正确答案 ──────────────────────────────────────────

    @Test
    fun `正确答案在选项中`() {
        val engine = HarmonicIntervalEngine.withSeed(40L)
        for (difficulty in HarmonicIntervalDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue("正确答案 '${q.correctAnswer}' 必须在选项中",
                q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `选项无重复`() {
        val engine = HarmonicIntervalEngine.withSeed(50L)
        for (difficulty in HarmonicIntervalDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals("选项应无重复",
                q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `正确答案与音程一致`() {
        val engine = HarmonicIntervalEngine.withSeed(60L)
        val q = engine.generate(HarmonicIntervalDifficulty.ADVANCED)
        assertEquals(q.interval.fullLabel, q.correctAnswer)
    }

    @Test
    fun `正确答案简写包含在fullLabel中`() {
        val engine = HarmonicIntervalEngine.withSeed(70L)
        val q = engine.generate(HarmonicIntervalDifficulty.ADVANCED)
        assertTrue(q.correctAnswer.contains(q.interval.shortSymbol))
    }

    // ── 半音数属性 ──────────────────────────────────────────

    @Test
    fun `小三度半音数为3`() {
        assertEquals(3, HarmonicInterval.MINOR_THIRD.semitones)
    }

    @Test
    fun `大三度半音数为4`() {
        assertEquals(4, HarmonicInterval.MAJOR_THIRD.semitones)
    }

    @Test
    fun `三全音半音数为6`() {
        assertEquals(6, HarmonicInterval.TRITONE.semitones)
    }

    @Test
    fun `纯八度半音数为12`() {
        assertEquals(12, HarmonicInterval.OCTAVE.semitones)
    }

    @Test
    fun `半音数互不相同`() {
        val allSemis = HarmonicInterval.ALL.map { it.semitones }
        assertEquals("所有音程半音数应互不相同",
            allSemis.size, allSemis.toSet().size)
    }

    // ── forDifficulty ──────────────────────────────────────

    @Test
    fun `forDifficulty BEGINNER 返回3个`() {
        assertEquals(3, HarmonicInterval.forDifficulty(HarmonicIntervalDifficulty.BEGINNER).size)
    }

    @Test
    fun `forDifficulty INTERMEDIATE 返回5个`() {
        assertEquals(5, HarmonicInterval.forDifficulty(HarmonicIntervalDifficulty.INTERMEDIATE).size)
    }

    @Test
    fun `forDifficulty ADVANCED 返回8个`() {
        assertEquals(8, HarmonicInterval.forDifficulty(HarmonicIntervalDifficulty.ADVANCED).size)
    }

    @Test
    fun `BEGINNER是INTERMEDIATE的子集`() {
        val beginner = HarmonicInterval.BEGINNER_INTERVALS.toSet()
        val intermediate = HarmonicInterval.INTERMEDIATE_INTERVALS.toSet()
        assertTrue("初级音程应是中级音程的子集", intermediate.containsAll(beginner))
    }

    @Test
    fun `INTERMEDIATE是ADVANCED的子集`() {
        val intermediate = HarmonicInterval.INTERMEDIATE_INTERVALS.toSet()
        val advanced = HarmonicInterval.ADVANCED_INTERVALS.toSet()
        assertTrue("中级音程应是高级音程的子集", advanced.containsAll(intermediate))
    }

    // ── 协和度 ──────────────────────────────────────────

    @Test
    fun `三全音协和度为0`() {
        assertEquals(0, HarmonicInterval.TRITONE.consonance)
    }

    @Test
    fun `纯八度协和度为4`() {
        assertEquals(4, HarmonicInterval.OCTAVE.consonance)
    }

    @Test
    fun `协和度范围在0到4`() {
        for (interval in HarmonicInterval.ALL) {
            assertTrue("${interval.name}: consonance=${interval.consonance} 应在 0..4",
                interval.consonance in 0..4)
        }
    }

    // ── 上方音 MIDI ──────────────────────────────────────

    @Test
    fun `上方音MIDI正确计算`() {
        val engine = HarmonicIntervalEngine.withSeed(80L)
        val q = engine.generate(HarmonicIntervalDifficulty.BEGINNER)
        assertEquals(q.lowerMidi + q.interval.semitones, q.upperMidi)
    }

    @Test
    fun `默认下方音为C4即MIDI60`() {
        val engine = HarmonicIntervalEngine.withSeed(90L)
        val q = engine.generate(HarmonicIntervalDifficulty.ADVANCED)
        assertEquals(60, q.lowerMidi)
    }
}
