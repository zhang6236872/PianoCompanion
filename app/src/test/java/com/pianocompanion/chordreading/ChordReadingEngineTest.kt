package com.pianocompanion.chordreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChordReadingEngine] 单元测试。
 *
 * 验证：
 * - 谱表位置 → MIDI 换算（高音/低音谱号）
 * - MIDI → 音名字母映射（仅自然音）
 * - 和弦分类（三和弦性质、七和弦性质）
 * - 出题确定性（相同种子相同题目）
 * - 选项构建（正确答案在选项中、选项唯一、选项数量=4）
 * - 各难度 × 各谱号的出题覆盖
 * - 三和弦三个音符、七和弦四个音符
 * - 初级不含减三和弦（根音避免 B），中级包含减三
 */
class ChordReadingEngineTest {

    private val engine = ChordReadingEngine()

    // ── 谱表位置 → MIDI ──────────────────────────────────

    @Test
    fun `treble clef bottom line is E4`() {
        assertEquals(64, engine.diatonicStepToMidi(ChordReadingClef.TREBLE, 0))
    }

    @Test
    fun `treble clef lines are E4 G4 B4 D5 F5`() {
        assertEquals(64, engine.diatonicStepToMidi(ChordReadingClef.TREBLE, 0)) // E4
        assertEquals(67, engine.diatonicStepToMidi(ChordReadingClef.TREBLE, 2)) // G4
        assertEquals(71, engine.diatonicStepToMidi(ChordReadingClef.TREBLE, 4)) // B4
        assertEquals(74, engine.diatonicStepToMidi(ChordReadingClef.TREBLE, 6)) // D5
        assertEquals(77, engine.diatonicStepToMidi(ChordReadingClef.TREBLE, 8)) // F5
    }

    @Test
    fun `bass clef bottom line is G2`() {
        assertEquals(43, engine.diatonicStepToMidi(ChordReadingClef.BASS, 0))
    }

    @Test
    fun `bass clef spaces are A2 C3 E3 G3`() {
        assertEquals(45, engine.diatonicStepToMidi(ChordReadingClef.BASS, 1)) // A2
        assertEquals(48, engine.diatonicStepToMidi(ChordReadingClef.BASS, 3)) // C3
        assertEquals(52, engine.diatonicStepToMidi(ChordReadingClef.BASS, 5)) // E3
        assertEquals(55, engine.diatonicStepToMidi(ChordReadingClef.BASS, 7)) // G3
    }

    // ── MIDI → 音名字母 ──────────────────────────────────

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

    // ── 和弦分类（三和弦）──────────────────────────────

    @Test
    fun `classify C major triad`() {
        // C-E-G: third=4, fifth=7 → 大三
        val type = engine.classifyTriad(4, 7)
        assertEquals(ChordType.MAJOR, type)
    }

    @Test
    fun `classify A minor triad`() {
        // A-C-E: third=3, fifth=7 → 小三
        val type = engine.classifyTriad(3, 7)
        assertEquals(ChordType.MINOR, type)
    }

    @Test
    fun `classify B diminished triad`() {
        // B-D-F: third=3, fifth=6 → 减三
        val type = engine.classifyTriad(3, 6)
        assertEquals(ChordType.DIMINISHED, type)
    }

    @Test
    fun `classify augmented triad`() {
        // third=4, fifth=8 → 增三
        val type = engine.classifyTriad(4, 8)
        assertEquals(ChordType.AUGMENTED, type)
    }

    // ── 和弦分类（七和弦）──────────────────────────────

    @Test
    fun `classify C major seventh`() {
        // C-E-G-B: 4,7,11 → 大七
        val type = engine.classifySeventh(4, 7, 11)
        assertEquals(ChordType.MAJOR_SEVENTH, type)
    }

    @Test
    fun `classify G dominant seventh`() {
        // G-B-D-F: 4,7,10 → 属七
        val type = engine.classifySeventh(4, 7, 10)
        assertEquals(ChordType.DOMINANT_SEVENTH, type)
    }

    @Test
    fun `classify A minor seventh`() {
        // A-C-E-G: 3,7,10 → 小七
        val type = engine.classifySeventh(3, 7, 10)
        assertEquals(ChordType.MINOR_SEVENTH, type)
    }

    @Test
    fun `classify B half diminished seventh`() {
        // B-D-F-A: 3,6,10 → 半减七
        val type = engine.classifySeventh(3, 6, 10)
        assertEquals(ChordType.HALF_DIMINISHED_SEVENTH, type)
    }

    // ── classify(List) 分发方法 ─────────────────────────

    @Test
    fun `classify dispatches triad from midi list`() {
        assertEquals(ChordType.MAJOR, engine.classify(listOf(60, 64, 67)))
        assertEquals(ChordType.MINOR, engine.classify(listOf(69, 72, 76)))
        assertEquals(ChordType.DIMINISHED, engine.classify(listOf(71, 74, 77)))
    }

    @Test
    fun `classify dispatches seventh from midi list`() {
        assertEquals(ChordType.MAJOR_SEVENTH, engine.classify(listOf(60, 64, 67, 71)))
        assertEquals(ChordType.DOMINANT_SEVENTH, engine.classify(listOf(67, 71, 74, 77)))
    }

    // ── 出题测试 ──────────────────────────────────────────

    @Test
    fun `generate returns valid question with all fields`() {
        val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
        assertNotNull(q)
        assertEquals(ChordReadingClef.TREBLE, q.clef)
        assertEquals(ChordReadingDifficulty.BEGINNER, q.difficulty)
        assertTrue(q.noteStaffSteps.isNotEmpty())
        assertTrue(q.noteNames.isNotEmpty())
        assertTrue(q.answerChoices.isNotEmpty())
        assertTrue(q.correctAnswer.isNotEmpty())
    }

    @Test
    fun `generate produces deterministic output with same seed`() {
        val e1 = ChordReadingEngine.withSeed(42L)
        val e2 = ChordReadingEngine.withSeed(42L)
        val q1 = e1.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.INTERMEDIATE)
        val q2 = e2.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.INTERMEDIATE)
        assertEquals(q1.noteStaffSteps, q2.noteStaffSteps)
        assertEquals(q1.noteMidis, q2.noteMidis)
        assertEquals(q1.chordType, q2.chordType)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `triad questions have exactly 3 notes`() {
        repeat(50) {
            val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.INTERMEDIATE)
            assertFalse("中级应为三和弦", q.isSeventh)
            assertEquals(
                "三和弦音符数应为 3，实际 ${q.noteStaffSteps.size}",
                3, q.noteStaffSteps.size
            )
        }
    }

    @Test
    fun `seventh questions have exactly 4 notes`() {
        repeat(50) {
            val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.ADVANCED)
            assertTrue("高级应为七和弦", q.isSeventh)
            assertEquals(
                "七和弦音符数应为 4，实际 ${q.noteStaffSteps.size}",
                4, q.noteStaffSteps.size
            )
        }
    }

    @Test
    fun `correct answer is always in choices`() {
        repeat(50) {
            val clef = if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS
            val diff = ChordReadingDifficulty.ALL[it % 3]
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
            val clef = if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS
            val diff = ChordReadingDifficulty.ALL[it % 3]
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
            val clef = if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS
            val diff = ChordReadingDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            assertEquals(
                "选项数量应为 4，实际 ${q.answerChoices.size}",
                4, q.answerChoices.size
            )
        }
    }

    @Test
    fun `beginner difficulty only produces major and minor`() {
        repeat(100) {
            val q = engine.generate(
                if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS,
                ChordReadingDifficulty.BEGINNER
            )
            assertTrue(
                "初级难度产生了 ${q.chordType}，应仅为大三或小三",
                q.chordType == ChordType.MAJOR ||
                    q.chordType == ChordType.MINOR
            )
        }
    }

    @Test
    fun `intermediate difficulty includes diminished`() {
        // 中级含减三（B 根音），运行多次至少应出现一次减三
        var hasDiminished = false
        repeat(300) {
            val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.INTERMEDIATE)
            if (q.chordType == ChordType.DIMINISHED) {
                hasDiminished = true
            }
            // 中级不应出现七和弦
            assertFalse("中级不应出现七和弦", q.isSeventh)
        }
        assertTrue("中级应能在 300 次中出现减三和弦", hasDiminished)
    }

    @Test
    fun `advanced difficulty only produces seventh chords`() {
        repeat(100) {
            val q = engine.generate(
                if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS,
                ChordReadingDifficulty.ADVANCED
            )
            assertTrue("高级应全部为七和弦", q.isSeventh)
            assertTrue(
                "高级和弦类型 ${q.chordType} 不在七和弦范围内",
                q.chordType in ChordType.SEVENTHS
            )
        }
    }

    @Test
    fun `beginner root is never B`() {
        // 初级避免 B 根音（B 自然构成减三）
        repeat(100) {
            val q = engine.generate(
                if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS,
                ChordReadingDifficulty.BEGINNER
            )
            val rootName = q.noteNames.first()
            assertFalse("初级根音不应为 B，实际为 $rootName", rootName.startsWith("B"))
        }
    }

    @Test
    fun `note names are all natural letters`() {
        repeat(50) {
            val clef = if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS
            val diff = ChordReadingDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            for (name in q.noteNames) {
                assertTrue(
                    "音名 $name 不是自然音字母（A-G）",
                    name.isNotEmpty() && name[0] in 'A'..'G'
                )
            }
        }
    }

    @Test
    fun `notes are in ascending order`() {
        repeat(50) {
            val clef = if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS
            val diff = ChordReadingDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            val steps = q.noteStaffSteps
            for (i in 1 until steps.size) {
                assertTrue(
                    "音符应按升序排列，但 steps[$i-1]=${steps[i - 1]} >= steps[$i]=${steps[i]}",
                    steps[i] > steps[i - 1]
                )
            }
        }
    }

    @Test
    fun `chord type matches midi intervals`() {
        repeat(100) {
            val clef = if (it % 2 == 0) ChordReadingClef.TREBLE else ChordReadingClef.BASS
            val diff = ChordReadingDifficulty.ALL[it % 3]
            val q = engine.generate(clef, diff)
            val expected = engine.classify(q.noteMidis)
            assertEquals(
                "和弦分类不匹配: ${q.noteMidis}",
                expected, q.chordType
            )
        }
    }

    @Test
    fun `answer choices match difficulty level`() {
        // 初级选项应在三和弦类型池中
        repeat(20) {
            val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.BEGINNER)
            val triadNames = ChordType.TRIADS.map { it.displayName }
            for (choice in q.answerChoices) {
                assertTrue(
                    "初级选项 $choice 不在三和弦类型池中",
                    triadNames.contains(choice)
                )
            }
        }
        // 高级选项应在七和弦类型池中
        repeat(20) {
            val q = engine.generate(ChordReadingClef.TREBLE, ChordReadingDifficulty.ADVANCED)
            val seventhNames = ChordType.SEVENTHS.map { it.displayName }
            for (choice in q.answerChoices) {
                assertTrue(
                    "高级选项 $choice 不在七和弦类型池中",
                    seventhNames.contains(choice)
                )
            }
        }
    }
}
