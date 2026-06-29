package com.pianocompanion.training

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * [EarTrainingEngine] 出题引擎单元测试。
 *
 * 覆盖：
 * - 三种练习类型（音程/和弦/音阶）的题目生成
 * - 三种难度的选项池正确性
 * - 确定性（相同种子相同题目）
 * - 根音范围、播放模式、选项唯一性
 * - 音乐理论正确性（音程半音数、和弦/音阶音高结构）
 */
class EarTrainingEngineTest {

    // ── 基础生成 ──────────────────────────────────────────

    @Test
    fun `音程题目生成返回正确类型`() {
        val engine = EarTrainingEngine(Random(42))
        val q = engine.generate(ExerciseType.INTERVAL, Difficulty.BEGINNER)
        assertEquals(ExerciseType.INTERVAL, q.exerciseType)
        assertEquals(2, q.midiNotes.size) // 音程 = 2 个音
    }

    @Test
    fun `和弦题目生成返回正确类型`() {
        val engine = EarTrainingEngine(Random(42))
        val q = engine.generate(ExerciseType.CHORD, Difficulty.BEGINNER)
        assertEquals(ExerciseType.CHORD, q.exerciseType)
        assertTrue(q.midiNotes.size >= 3) // 和弦至少 3 个音
    }

    @Test
    fun `音阶题目生成返回正确类型`() {
        val engine = EarTrainingEngine(Random(42))
        val q = engine.generate(ExerciseType.SCALE, Difficulty.BEGINNER)
        assertEquals(ExerciseType.SCALE, q.exerciseType)
        assertTrue(q.midiNotes.size >= 2)
    }

    // ── 确定性 ────────────────────────────────────────────

    @Test
    fun `相同种子生成相同题目`() {
        val engine1 = EarTrainingEngine(Random(100))
        val engine2 = EarTrainingEngine(Random(100))
        val q1 = engine1.generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
        val q2 = engine2.generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.playMode, q2.playMode)
    }

    @Test
    fun `withSeed 工厂方法确定性`() {
        val q1 = EarTrainingEngine.withSeed(999).generate(ExerciseType.CHORD, Difficulty.ADVANCED)
        val q2 = EarTrainingEngine.withSeed(999).generate(ExerciseType.CHORD, Difficulty.ADVANCED)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `不同种子生成不同题目（大概率）`() {
        val q1 = EarTrainingEngine.withSeed(1).generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
        val q2 = EarTrainingEngine.withSeed(2).generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
        // 不要求必须不同，但至少验证不崩溃
        assertNotNull(q1)
        assertNotNull(q2)
    }

    // ── 选项唯一性与正确性 ────────────────────────────────

    @Test
    fun `选项列表唯一`() {
        repeat(50) { seed ->
            val engine = EarTrainingEngine(Random(seed.toLong()))
            for (type in ExerciseType.ALL) {
                for (diff in Difficulty.ALL) {
                    val q = engine.generate(type, diff)
                    assertEquals(
                        "选项不唯一: seed=$seed type=$type diff=$diff",
                        q.answerChoices.size,
                        q.answerChoices.toSet().size
                    )
                }
            }
        }
    }

    @Test
    fun `正确答案始终在选项中`() {
        repeat(50) { seed ->
            val engine = EarTrainingEngine(Random(seed.toLong()))
            for (type in ExerciseType.ALL) {
                for (diff in Difficulty.ALL) {
                    val q = engine.generate(type, diff)
                    assertTrue(
                        "正确答案不在选项中: seed=$seed type=$type diff=$diff",
                        q.correctAnswer in q.answerChoices
                    )
                }
            }
        }
    }

    @Test
    fun `初级音程选项数不超过 4 且至少 2`() {
        repeat(20) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong()).generate(ExerciseType.INTERVAL, Difficulty.BEGINNER)
            assertTrue(q.answerChoices.size in 2..4)
        }
    }

    // ── 音乐理论正确性 ────────────────────────────────────

    @Test
    fun `音程两音的半音差等于正确答案的半音数`() {
        repeat(50) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
            val semitoneDiff = kotlin.math.abs(q.midiNotes[1] - q.midiNotes[0])
            val expected = IntervalType.entries.firstOrNull { it.fullName == q.correctAnswer }
            assertNotNull("正确答案 ${q.correctAnswer} 不在 IntervalType 中", expected)
            assertEquals(expected!!.semitones, semitoneDiff)
        }
    }

    @Test
    fun `上行音程第二个音高于等于第一个`() {
        repeat(30) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
            if (q.playMode == PlayMode.ASCENDING) {
                assertTrue(q.midiNotes[1] >= q.midiNotes[0])
            }
        }
    }

    @Test
    fun `下行音程第一个音高于第二个`() {
        repeat(50) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
            if (q.playMode == PlayMode.DESCENDING) {
                assertTrue(q.midiNotes[0] > q.midiNotes[1])
            }
        }
    }

    @Test
    fun `和弦音符与正确答案的音程结构匹配`() {
        repeat(50) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.ADVANCED)
            val expected = ChordType.entries.firstOrNull { it.fullName == q.correctAnswer }
            assertNotNull("正确答案 ${q.correctAnswer} 不在 ChordType 中", expected)
            // BLOCK 模式下音符就是和弦音；旋律模式下是排序后的
            val sortedNotes = q.midiNotes.sorted()
            val root = sortedNotes.minOrNull()!!
            val intervals = sortedNotes.map { it - root }
            assertEquals(expected!!.intervals, intervals)
        }
    }

    @Test
    fun `和弦至少有 3 个音符`() {
        repeat(30) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.ADVANCED)
            assertTrue("和弦音符数 ${q.midiNotes.size} < 3", q.midiNotes.size >= 3)
        }
    }

    @Test
    fun `七和弦有 4 个音符`() {
        repeat(100) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.ADVANCED)
            val expected = ChordType.entries.firstOrNull { it.fullName == q.correctAnswer }
            if (expected != null && expected.intervals.size == 4) {
                assertEquals(4, q.midiNotes.size)
            }
        }
    }

    @Test
    fun `音阶音符与正确答案的音程结构匹配`() {
        repeat(50) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.SCALE, Difficulty.ADVANCED)
            val expected = ScaleType.entries.firstOrNull { it.fullName == q.correctAnswer }
            assertNotNull("正确答案 ${q.correctAnswer} 不在 ScaleType 中", expected)
            // 上行音阶
            if (q.playMode == PlayMode.ASCENDING) {
                val root = q.midiNotes.minOrNull()!!
                val intervals = q.midiNotes.map { it - root }
                assertEquals(expected!!.intervals, intervals)
            }
        }
    }

    @Test
    fun `音阶至少有 7 个音符`() {
        repeat(30) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.SCALE, Difficulty.INTERMEDIATE)
            assertTrue("音阶音符数 ${q.midiNotes.size} < 7", q.midiNotes.size >= 7)
        }
    }

    // ── 根音范围 ──────────────────────────────────────────

    @Test
    fun `音程根音在 C4 到 B4 范围内`() {
        repeat(50) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
            val root = q.midiNotes.minOrNull()!!
            assertTrue("根音 $root 不在 60-71 范围内", root in 60..71)
        }
    }

    @Test
    fun `所有音符在合理钢琴音域内`() {
        repeat(50) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.SCALE, Difficulty.ADVANCED)
            q.midiNotes.forEach { midi ->
                assertTrue("音符 $midi 超出钢琴音域", midi in 21..108)
            }
        }
    }

    // ── 播放模式 ──────────────────────────────────────────

    @Test
    fun `播放模式始终是有效值`() {
        repeat(20) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.BEGINNER)
            assertTrue(q.playMode in PlayMode.entries)
        }
    }

    @Test
    fun `BLOCK 模式和弦音符未排序（保持根音-三-五结构）`() {
        repeat(100) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.ADVANCED)
            if (q.playMode == PlayMode.BLOCK) {
                val root = q.midiNotes[0]
                val expected = ChordType.entries.first { it.fullName == q.correctAnswer }
                val intervals = q.midiNotes.map { it - root }
                assertEquals(expected.intervals, intervals)
            }
        }
    }

    // ── 难度池验证 ────────────────────────────────────────

    @Test
    fun `初级和弦只有大小三和弦选项`() {
        repeat(20) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.BEGINNER)
            // 初级和弦池只有 MAJOR 和 MINOR
            assertTrue(q.correctAnswer == ChordType.MAJOR.fullName || q.correctAnswer == ChordType.MINOR.fullName)
            // 选项最多 2 个
            assertTrue(q.answerChoices.size <= 2)
        }
    }

    @Test
    fun `初级音程只含易区分音程`() {
        val validNames = setOf(
            IntervalType.MINOR_3RD.fullName, IntervalType.MAJOR_3RD.fullName,
            IntervalType.PERFECT_4TH.fullName, IntervalType.PERFECT_5TH.fullName
        )
        repeat(30) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.INTERVAL, Difficulty.BEGINNER)
            assertTrue(q.correctAnswer in validNames)
        }
    }

    @Test
    fun `高级音程覆盖全部 12 种`() {
        val seen = mutableSetOf<String>()
        repeat(500) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.INTERVAL, Difficulty.ADVANCED)
            seen.add(q.correctAnswer)
        }
        // 高级应该能覆盖全部 12 种音程（概率上）
        assertEquals(12, seen.size)
    }

    @Test
    fun `高级和弦覆盖七和弦`() {
        val seventhNames = setOf(
            ChordType.DOMINANT_7TH.fullName,
            ChordType.MAJOR_7TH.fullName,
            ChordType.MINOR_7TH.fullName
        )
        val seen = mutableSetOf<String>()
        repeat(500) { seed ->
            val q = EarTrainingEngine.withSeed(seed.toLong())
                .generate(ExerciseType.CHORD, Difficulty.ADVANCED)
            seen.add(q.correctAnswer)
        }
        // 七和弦应该出现
        assertTrue("未覆盖七和弦: seen=$seen", seen.intersect(seventhNames).isNotEmpty())
    }
}
