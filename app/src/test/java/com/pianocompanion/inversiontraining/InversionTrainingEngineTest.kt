package com.pianocompanion.inversiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦转位听辨训练出题引擎单元测试。
 *
 * 验证确定性、选项完整性、和弦 MIDI 正确性、转位排列、音域范围、难度配置等。
 */
class InversionTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = InversionTrainingEngine.withSeed(42L)
        val e2 = InversionTrainingEngine.withSeed(42L)
        val q1 = e1.generate(InversionDifficulty.INTERMEDIATE)
        val q2 = e2.generate(InversionDifficulty.INTERMEDIATE)
        assertEquals(q1.quality, q2.quality)
        assertEquals(q1.inversion, q2.inversion)
        assertEquals(q1.rootMidi, q2.rootMidi)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds may produce different questions`() {
        // 多次尝试找到一个不同的题目
        var foundDifferent = false
        for (seed in 0..100) {
            val q1 = InversionTrainingEngine.withSeed(seed.toLong()).generate(InversionDifficulty.ADVANCED)
            val q2 = InversionTrainingEngine.withSeed((seed + 500).toLong()).generate(InversionDifficulty.ADVANCED)
            if (q1.inversion != q2.inversion || q1.quality != q2.quality || q1.rootMidi != q2.rootMidi) {
                foundDifferent = true
                break
            }
        }
        assertTrue("不同种子应该能产生不同题目", foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────────

    @Test
    fun `beginner has 2 options`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.BEGINNER)
        assertEquals(2, q.answerChoices.size)
    }

    @Test
    fun `intermediate has 3 options`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.INTERMEDIATE)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `advanced has 3 options`() {
        val q = InversionTrainingEngine.withSeed(1L).generate(InversionDifficulty.ADVANCED)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `options contain correct answer`() {
        InversionDifficulty.ALL.forEach { d ->
            val q = InversionTrainingEngine.withSeed(7L).generate(d)
            assertTrue("$d 选项应包含正确答案", q.answerChoices.contains(q.correctAnswer))
        }
    }

    @Test
    fun `options are unique`() {
        InversionDifficulty.ALL.forEach { d ->
            val q = InversionTrainingEngine.withSeed(7L).generate(d)
            assertEquals("$d 选项应无重复", q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `options equal difficulty inversion set`() {
        InversionDifficulty.ALL.forEach { d ->
            val expectedNames = InversionType.forDifficulty(d).map { it.displayName }.toSet()
            repeat(5) {
                val q = InversionTrainingEngine.withSeed((it * 13 + 1).toLong()).generate(d)
                assertEquals("$d 选项集合应等于难度转位集合", expectedNames, q.answerChoices.toSet())
            }
        }
    }

    // ── 和弦性质范围 ────────────────────────────────────────

    @Test
    fun `beginner only uses major quality`() {
        repeat(50) {
            val q = InversionTrainingEngine.withSeed(it.toLong()).generate(InversionDifficulty.BEGINNER)
            assertEquals(ChordQuality.MAJOR, q.quality)
        }
    }

    @Test
    fun `intermediate uses major or minor`() {
        val qualities = mutableSetOf<ChordQuality>()
        repeat(100) {
            val q = InversionTrainingEngine.withSeed(it.toLong()).generate(InversionDifficulty.INTERMEDIATE)
            assertTrue(q.quality == ChordQuality.MAJOR || q.quality == ChordQuality.MINOR)
            qualities.add(q.quality)
        }
        assertTrue("中级应能出现大调和小调", qualities.size >= 2)
    }

    @Test
    fun `advanced uses all four qualities`() {
        val qualities = mutableSetOf<ChordQuality>()
        repeat(200) {
            val q = InversionTrainingEngine.withSeed(it.toLong()).generate(InversionDifficulty.ADVANCED)
            assertTrue(q.quality in ChordQuality.ALL)
            qualities.add(q.quality)
        }
        assertTrue("高级应能出现全部 4 种和弦性质", qualities.size == 4)
    }

    // ── 转位类型范围 ────────────────────────────────────────

    @Test
    fun `beginner only uses root or first inversion`() {
        repeat(100) {
            val q = InversionTrainingEngine.withSeed(it.toLong()).generate(InversionDifficulty.BEGINNER)
            assertTrue(
                "初级应只用原位或第一转位",
                q.inversion == InversionType.ROOT_POSITION || q.inversion == InversionType.FIRST_INVERSION
            )
        }
    }

    @Test
    fun `intermediate uses all three inversions`() {
        val inversions = mutableSetOf<InversionType>()
        repeat(150) {
            val q = InversionTrainingEngine.withSeed(it.toLong()).generate(InversionDifficulty.INTERMEDIATE)
            inversions.add(q.inversion)
        }
        assertTrue("中级应能出现全部 3 种转位", inversions.size == 3)
    }

    // ── 和弦 MIDI 正确性 ────────────────────────────────────

    @Test
    fun `chord always has 3 notes`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(20) {
                val q = InversionTrainingEngine.withSeed((it * 17).toLong()).generate(d)
                assertEquals(3, q.midiNotes.size)
            }
        }
    }

    @Test
    fun `midi notes sorted ascending`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(20) {
                val q = InversionTrainingEngine.withSeed((it * 19).toLong()).generate(d)
                val sorted = q.midiNotes.sorted()
                assertEquals("$d 音符应从低到高排列", sorted, q.midiNotes)
            }
        }
    }

    @Test
    fun `midi notes within piano range`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(30) {
                val q = InversionTrainingEngine.withSeed((it * 23).toLong()).generate(d)
                q.midiNotes.forEach { note ->
                    assertTrue("MIDI $note 应在 [21, 108]", note in 21..108)
                }
            }
        }
    }

    // ── buildChordMidiNotes 单元测试 ───────────────────────

    @Test
    fun `root position major C = C E G`() {
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MAJOR, InversionType.ROOT_POSITION, 60
        )
        assertEquals(listOf(60, 64, 67), notes)
    }

    @Test
    fun `root position minor C = C Eb G`() {
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MINOR, InversionType.ROOT_POSITION, 60
        )
        assertEquals(listOf(60, 63, 67), notes)
    }

    @Test
    fun `root position augmented C = C E G#`() {
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.AUGMENTED, InversionType.ROOT_POSITION, 60
        )
        assertEquals(listOf(60, 64, 68), notes)
    }

    @Test
    fun `root position diminished C = C Eb Gb`() {
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.DIMINISHED, InversionType.ROOT_POSITION, 60
        )
        assertEquals(listOf(60, 63, 66), notes)
    }

    @Test
    fun `first inversion major C = E G C`() {
        // 原位: C(60) E(64) G(67) → 第一转位: E(64) G(67) C(72)
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MAJOR, InversionType.FIRST_INVERSION, 60
        )
        assertEquals(listOf(64, 67, 72), notes)
    }

    @Test
    fun `first inversion minor C = Eb G C`() {
        // 原位: C(60) Eb(63) G(67) → 第一转位: Eb(63) G(67) C(72)
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MINOR, InversionType.FIRST_INVERSION, 60
        )
        assertEquals(listOf(63, 67, 72), notes)
    }

    @Test
    fun `second inversion major C = G C E`() {
        // 原位: C(60) E(64) G(67) → 第二转位: G(67) C(72) E(76)
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.MAJOR, InversionType.SECOND_INVERSION, 60
        )
        assertEquals(listOf(67, 72, 76), notes)
    }

    @Test
    fun `second inversion diminished C = Gb C Eb`() {
        // 原位: C(60) Eb(63) Gb(66) → 第二转位: Gb(66) C(72) Eb(75)
        val notes = InversionTrainingEngine.buildChordMidiNotes(
            ChordQuality.DIMINISHED, InversionType.SECOND_INVERSION, 60
        )
        assertEquals(listOf(66, 72, 75), notes)
    }

    // ── 转位区分性（不同转位产生不同的最低音） ───────────

    @Test
    fun `different inversions have different bass notes for same quality and root`() {
        ChordQuality.ALL.forEach { quality ->
            val root = 60
            val rootPos = InversionTrainingEngine.buildChordMidiNotes(quality, InversionType.ROOT_POSITION, root)
            val firstInv = InversionTrainingEngine.buildChordMidiNotes(quality, InversionType.FIRST_INVERSION, root)
            val secondInv = InversionTrainingEngine.buildChordMidiNotes(quality, InversionType.SECOND_INVERSION, root)

            val bassNotes = setOf(rootPos.first(), firstInv.first(), secondInv.first())
            assertTrue("$quality 三种转位的最低音应各不相同: $bassNotes", bassNotes.size == 3)
        }
    }

    @Test
    fun `root position bass is the root`() {
        ChordQuality.ALL.forEach { quality ->
            val root = 55
            val notes = InversionTrainingEngine.buildChordMidiNotes(quality, InversionType.ROOT_POSITION, root)
            assertEquals("$quality 原位最低音应为根音", root, notes.first())
        }
    }

    @Test
    fun `first inversion bass is the third`() {
        ChordQuality.ALL.forEach { quality ->
            val root = 55
            val thirdInterval = quality.intervals[1]
            val notes = InversionTrainingEngine.buildChordMidiNotes(quality, InversionType.FIRST_INVERSION, root)
            assertEquals("$quality 第一转位最低音应为三音", root + thirdInterval, notes.first())
        }
    }

    @Test
    fun `second inversion bass is the fifth`() {
        ChordQuality.ALL.forEach { quality ->
            val root = 55
            val fifthInterval = quality.intervals[2]
            val notes = InversionTrainingEngine.buildChordMidiNotes(quality, InversionType.SECOND_INVERSION, root)
            assertEquals("$quality 第二转位最低音应为五音", root + fifthInterval, notes.first())
        }
    }

    // ── 难度配置嵌套子集 ───────────────────────────────────

    @Test
    fun `quality sets are nested subsets`() {
        val beginner = ChordQuality.forDifficulty(InversionDifficulty.BEGINNER)
        val intermediate = ChordQuality.forDifficulty(InversionDifficulty.INTERMEDIATE)
        val advanced = ChordQuality.forDifficulty(InversionDifficulty.ADVANCED)
        assertTrue("初级 ⊆ 中级", intermediate.containsAll(beginner))
        assertTrue("中级 ⊆ 高级", advanced.containsAll(intermediate))
    }

    @Test
    fun `inversion sets are nested subsets`() {
        val beginner = InversionType.forDifficulty(InversionDifficulty.BEGINNER)
        val intermediate = InversionType.forDifficulty(InversionDifficulty.INTERMEDIATE)
        val advanced = InversionType.forDifficulty(InversionDifficulty.ADVANCED)
        assertTrue("初级 ⊆ 中级", intermediate.containsAll(beginner))
        assertTrue("中级 ⊆ 高级", advanced.containsAll(intermediate))
    }

    // ── 枚举完整性 ─────────────────────────────────────────

    @Test
    fun `all chord qualities have 3 intervals`() {
        ChordQuality.ALL.forEach { q ->
            assertEquals("$q 应有 3 个音程", 3, q.intervals.size)
            assertEquals(0, q.intervals[0])
        }
    }

    @Test
    fun `all inversion types have valid bass degree`() {
        InversionType.ALL.forEach { inv ->
            assertTrue(inv.bassDegree in 0..2)
        }
    }

    @Test
    fun `correct answer matches inversion display name`() {
        InversionDifficulty.ALL.forEach { d ->
            val q = InversionTrainingEngine.withSeed(99L).generate(d)
            assertEquals(q.inversion.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `root name is valid`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(10) {
                val q = InversionTrainingEngine.withSeed((it * 31).toLong()).generate(d)
                assertTrue("根音名应非空: ${q.rootName}", q.rootName.isNotEmpty())
            }
        }
    }

    @Test
    fun `root midi in expected range`() {
        InversionDifficulty.ALL.forEach { d ->
            repeat(30) {
                val q = InversionTrainingEngine.withSeed((it * 37).toLong()).generate(d)
                // C3(48) 到 G3(55)
                assertTrue("根音 ${q.rootMidi} 应在 C3-G3 范围", q.rootMidi in 48..55)
            }
        }
    }
}
