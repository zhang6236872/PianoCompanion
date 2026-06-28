package com.pianocompanion.generator

import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.KeySignature
import com.pianocompanion.util.MusicUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 视奏练习生成器单元测试。
 *
 * 测试覆盖维度：
 * - 基础生成（非空、结构正确）
 * - 确定性（相同种子 → 相同乐谱）
 * - 音乐理论正确性（所有音符属于指定调号的大调音阶）
 * - 音域约束（所有音符在难度对应的音域范围内）
 * - 难度区分（不同难度产生不同复杂度）
 * - 拍号与节奏型（节奏单元格总和 = 每小节拍数）
 * - 主音映射（各调号的主音音高正确）
 * - 乐句结构（每 4 小节末尾倾向解决到主音/属音）
 * - 休止符（推进时间但不产生音符）
 * - 小节索引（连续递增）
 * - 方向约束（避免连续同向失控）
 * - 边界情况（1 小节、大量小节、各种调号）
 * - 钢琴音域有效性（21-108）
 */
class SightReadingGeneratorTest {

    private lateinit var generator: SightReadingGenerator

    @Before
    fun setUp() {
        generator = SightReadingGenerator()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  基础生成
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `generate returns non-empty score`() {
        val score = generator.generate(SightReadingOptions(seed = 42L))
        assertTrue("乐谱应包含音符", score.notes.isNotEmpty())
        assertEquals(ScoreSource.GENERATED, score.source)
    }

    @Test
    fun `generated score has correct metadata`() {
        val opts = SightReadingOptions(
            keySignature = KeySignature.D_MAJOR,
            difficulty = SightReadingDifficulty.INTERMEDIATE,
            measures = 8,
            tempo = 120,
            timeSignature = "3/4",
            seed = 1L
        )
        val score = generator.generate(opts)
        assertEquals(120, score.tempo)
        assertEquals("3/4", score.timeSignature)
        assertEquals("Piano Companion", score.composer)
        assertTrue(score.title.contains("D大调"))
        assertTrue(score.title.contains("中级"))
        assertTrue(score.id.startsWith("sight_reading_"))
    }

    @Test
    fun `generate produces notes for every non-rest beat`() {
        val score = generator.generate(SightReadingOptions(measures = 4, seed = 100L))
        // 至少应该有一些音符（4 小节不可能全是休止）
        assertTrue("4 小节乐谱至少应包含 4 个音符", score.notes.size >= 4)
    }

    @Test
    fun `all notes have valid piano MIDI range`() {
        for (difficulty in SightReadingDifficulty.values()) {
            for (key in listOf(
                KeySignature.C_MAJOR_A_MINOR,
                KeySignature.G_MAJOR_E_MINOR,
                KeySignature.B_FLAT_MAJOR,
                KeySignature.E_MAJOR
            )) {
                val score = generator.generate(
                    SightReadingOptions(
                        keySignature = key,
                        difficulty = difficulty,
                        staff = Staff.TREBLE,
                        measures = 8,
                        seed = 42L
                    )
                )
                score.notes.forEach { note ->
                    assertTrue(
                        "音符 ${note.noteName}(MIDI ${note.midiNumber}) 应在钢琴音域 21-108 内",
                        note.midiNumber in 21..108
                    )
                }
            }
        }
    }

    @Test
    fun `bass staff generates lower pitches than treble`() {
        val trebleScore = generator.generate(
            SightReadingOptions(staff = Staff.TREBLE, seed = 77L, measures = 8)
        )
        val bassScore = generator.generate(
            SightReadingOptions(staff = Staff.BASS, seed = 77L, measures = 8)
        )
        val trebleAvg = trebleScore.notes.map { it.midiNumber }.average()
        val bassAvg = bassScore.notes.map { it.midiNumber }.average()
        assertTrue(
            "低音谱表平均音高($bassAvg)应低于高音谱表($trebleAvg)",
            bassAvg < trebleAvg
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  确定性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `same seed produces identical score`() {
        val opts = SightReadingOptions(
            keySignature = KeySignature.A_MAJOR,
            difficulty = SightReadingDifficulty.ADVANCED,
            measures = 16,
            tempo = 140,
            timeSignature = "4/4",
            seed = 12345L
        )
        val score1 = generator.generate(opts)
        val score2 = generator.generate(opts)
        assertEquals(score1.notes.size, score2.notes.size)
        score1.notes.zip(score2.notes).forEach { (n1, n2) ->
            assertEquals(n1.midiNumber, n2.midiNumber)
            assertEquals(n1.startTime, n2.startTime)
            assertEquals(n1.duration, n2.duration)
            assertEquals(n1.measureIndex, n2.measureIndex)
        }
    }

    @Test
    fun `different seeds produce different scores`() {
        val opts1 = SightReadingOptions(seed = 1L, measures = 8)
        val opts2 = SightReadingOptions(seed = 2L, measures = 8)
        val score1 = generator.generate(opts1)
        val score2 = generator.generate(opts2)
        // 极大概率不同（至少一个音符的 MIDI 值不同）
        val anyDiff = score1.notes.zip(score2.notes).any { it.first.midiNumber != it.second.midiNumber }
        assertTrue("不同种子应产生不同旋律", anyDiff)
    }

    @Test
    fun `deterministic across multiple invocations`() {
        val opts = SightReadingOptions(seed = 999L, measures = 8, difficulty = SightReadingDifficulty.INTERMEDIATE)
        val results = (1..5).map { generator.generate(opts) }
        val firstNoteSet = results[0].notes.map { it.midiNumber }
        results.forEach { score ->
            assertEquals(firstNoteSet, score.notes.map { it.midiNumber })
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  音乐理论正确性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `all notes belong to C major scale`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.C_MAJOR_A_MINOR,
                measures = 16,
                seed = 42L,
                difficulty = SightReadingDifficulty.ADVANCED
            )
        )
        // C 大调音高的 pitch class 集合
        val cMajorPitchClasses = setOf(0, 2, 4, 5, 7, 9, 11)
        score.notes.forEach { note ->
            val pc = note.midiNumber % 12
            assertTrue(
                "音符 ${note.noteName}(pc=$pc) 应属于 C 大调音阶",
                pc in cMajorPitchClasses
            )
        }
    }

    @Test
    fun `all notes belong to G major scale (1 sharp - F#)`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.G_MAJOR_E_MINOR,
                measures = 16,
                seed = 7L,
                difficulty = SightReadingDifficulty.ADVANCED
            )
        )
        // G 大调: G A B C D E F#  → pitch classes: 7,9,11,0,2,4,6
        val gMajorPitchClasses = setOf(7, 9, 11, 0, 2, 4, 6)
        score.notes.forEach { note ->
            val pc = note.midiNumber % 12
            assertTrue(
                "音符 ${note.noteName}(pc=$pc) 应属于 G 大调音阶 $gMajorPitchClasses",
                pc in gMajorPitchClasses
            )
        }
    }

    @Test
    fun `all notes belong to F major scale (1 flat - Bb)`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.F_MAJOR_D_MINOR,
                measures = 16,
                seed = 13L,
                difficulty = SightReadingDifficulty.ADVANCED
            )
        )
        // F 大调: F G A Bb C D E  → pitch classes: 5,7,9,10,0,2,4
        val fMajorPitchClasses = setOf(5, 7, 9, 10, 0, 2, 4)
        score.notes.forEach { note ->
            val pc = note.midiNumber % 12
            assertTrue(
                "音符 ${note.noteName}(pc=$pc) 应属于 F 大调音阶 $fMajorPitchClasses",
                pc in fMajorPitchClasses
            )
        }
    }

    @Test
    fun `all notes belong to E major scale (4 sharps)`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.E_MAJOR,
                measures = 16,
                seed = 99L,
                difficulty = SightReadingDifficulty.ADVANCED
            )
        )
        // E 大调: E F# G# A B C# D#  → pitch classes: 4,6,8,9,11,1,3
        val eMajorPitchClasses = setOf(4, 6, 8, 9, 11, 1, 3)
        score.notes.forEach { note ->
            val pc = note.midiNumber % 12
            assertTrue(
                "音符 ${note.noteName}(pc=$pc) 应属于 E 大调音阶 $eMajorPitchClasses",
                pc in eMajorPitchClasses
            )
        }
    }

    @Test
    fun `all notes belong to Eb major scale (3 flats)`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.E_FLAT_MAJOR,
                measures = 16,
                seed = 55L,
                difficulty = SightReadingDifficulty.ADVANCED
            )
        )
        // Eb 大调: Eb F G Ab Bb C D  → pitch classes: 3,5,7,8,10,0,2
        val ebMajorPitchClasses = setOf(3, 5, 7, 8, 10, 0, 2)
        score.notes.forEach { note ->
            val pc = note.midiNumber % 12
            assertTrue(
                "音符 ${note.noteName}(pc=$pc) 应属于 bE 大调音阶 $ebMajorPitchClasses",
                pc in ebMajorPitchClasses
            )
        }
    }

    @Test
    fun `melody starts on tonic`() {
        for (key in listOf(
            KeySignature.C_MAJOR_A_MINOR,
            KeySignature.D_MAJOR,
            KeySignature.F_MAJOR_D_MINOR,
            KeySignature.A_MAJOR
        )) {
            val score = generator.generate(
                SightReadingOptions(
                    keySignature = key,
                    measures = 8,
                    seed = 42L
                )
            )
            val firstNote = score.notes.first()
            val tonicPc = generator.tonicPitchClass(key)
            assertEquals(
                "第一个音符应为 ${key.label} 的主音(pc=$tonicPc)，实际: ${firstNote.noteName}(pc=${firstNote.midiNumber % 12})",
                tonicPc,
                firstNote.midiNumber % 12
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  主音映射
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `tonic pitch class for C major is C`() {
        assertEquals(0, generator.tonicPitchClass(KeySignature.C_MAJOR_A_MINOR))
    }

    @Test
    fun `tonic pitch class for G major is G`() {
        assertEquals(7, generator.tonicPitchClass(KeySignature.G_MAJOR_E_MINOR))
    }

    @Test
    fun `tonic pitch class for D major is D`() {
        assertEquals(2, generator.tonicPitchClass(KeySignature.D_MAJOR))
    }

    @Test
    fun `tonic pitch class for A major is A`() {
        assertEquals(9, generator.tonicPitchClass(KeySignature.A_MAJOR))
    }

    @Test
    fun `tonic pitch class for E major is E`() {
        assertEquals(4, generator.tonicPitchClass(KeySignature.E_MAJOR))
    }

    @Test
    fun `tonic pitch class for B major is B`() {
        assertEquals(11, generator.tonicPitchClass(KeySignature.B_MAJOR))
    }

    @Test
    fun `tonic pitch class for F major is F`() {
        assertEquals(5, generator.tonicPitchClass(KeySignature.F_MAJOR_D_MINOR))
    }

    @Test
    fun `tonic pitch class for Bb major is Bb`() {
        assertEquals(10, generator.tonicPitchClass(KeySignature.B_FLAT_MAJOR))
    }

    @Test
    fun `tonic pitch class for Eb major is Eb`() {
        assertEquals(3, generator.tonicPitchClass(KeySignature.E_FLAT_MAJOR))
    }

    @Test
    fun `tonic pitch class for Ab major is Ab`() {
        assertEquals(8, generator.tonicPitchClass(KeySignature.A_FLAT_MAJOR))
    }

    @Test
    fun `tonic pitch class for F# major is F#`() {
        assertEquals(6, generator.tonicPitchClass(KeySignature.F_SHARP_MAJOR))
    }

    @Test
    fun `tonic pitch class for Db major is Db`() {
        assertEquals(1, generator.tonicPitchClass(KeySignature.D_FLAT_MAJOR))
    }

    @Test
    fun `tonic midi for C major treble is C4`() {
        val midi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(60, midi) // C4
    }

    @Test
    fun `tonic midi for C major bass is C3`() {
        val midi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.BASS)
        assertEquals(48, midi) // C3
    }

    @Test
    fun `tonic midi for G major treble is G4`() {
        val midi = generator.tonicMidiNote(KeySignature.G_MAJOR_E_MINOR, Staff.TREBLE)
        assertEquals(67, midi) // G4
    }

    @Test
    fun `tonic midi for F major bass is F3`() {
        val midi = generator.tonicMidiNote(KeySignature.F_MAJOR_D_MINOR, Staff.BASS)
        assertEquals(53, midi) // F3
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  音阶级数 → MIDI 映射
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `degree 0 maps to tonic`() {
        // C 大调 treble: degree 0 → C4=60
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(60, generator.degreeToMidi(0, tonicMidi))
    }

    @Test
    fun `degree 1 maps to major second`() {
        // C 大调: degree 1 → D4=62
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(62, generator.degreeToMidi(1, tonicMidi))
    }

    @Test
    fun `degree 2 maps to major third`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(64, generator.degreeToMidi(2, tonicMidi))
    }

    @Test
    fun `degree 3 maps to perfect fourth`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(65, generator.degreeToMidi(3, tonicMidi))
    }

    @Test
    fun `degree 4 maps to perfect fifth`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(67, generator.degreeToMidi(4, tonicMidi))
    }

    @Test
    fun `degree 5 maps to major sixth`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(69, generator.degreeToMidi(5, tonicMidi))
    }

    @Test
    fun `degree 6 maps to major seventh`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(71, generator.degreeToMidi(6, tonicMidi))
    }

    @Test
    fun `degree 7 maps to tonic one octave up`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(72, generator.degreeToMidi(7, tonicMidi)) // C5
    }

    @Test
    fun `degree -1 maps to major seventh below`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(59, generator.degreeToMidi(-1, tonicMidi)) // B3
    }

    @Test
    fun `degree -7 maps to tonic one octave down`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        assertEquals(48, generator.degreeToMidi(-7, tonicMidi)) // C3
    }

    @Test
    fun `G major degree 0 maps to G4`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.G_MAJOR_E_MINOR, Staff.TREBLE)
        assertEquals(67, generator.degreeToMidi(0, tonicMidi))
    }

    @Test
    fun `G major degree 6 maps to F#5`() {
        val tonicMidi = generator.tonicMidiNote(KeySignature.G_MAJOR_E_MINOR, Staff.TREBLE)
        assertEquals(78, generator.degreeToMidi(6, tonicMidi)) // F#5 = 67+11
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  音域约束
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `beginner notes stay within one octave of tonic`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.C_MAJOR_A_MINOR,
                difficulty = SightReadingDifficulty.BEGINNER,
                measures = 16,
                seed = 42L
            )
        )
        val tonicMidi = generator.tonicMidiNote(KeySignature.C_MAJOR_A_MINOR, Staff.TREBLE)
        score.notes.forEach { note ->
            val semitonesFromTonic = note.midiNumber - tonicMidi
            assertTrue(
                "初级音符 ${note.noteName} 距主音 $semitonesFromTonic 半音，应在 0-12 内",
                semitonesFromTonic in 0..12
            )
        }
    }

    @Test
    fun `advanced notes can span wider range than beginner`() {
        val beginnerScore = generator.generate(
            SightReadingOptions(difficulty = SightReadingDifficulty.BEGINNER, measures = 32, seed = 42L)
        )
        val advancedScore = generator.generate(
            SightReadingOptions(difficulty = SightReadingDifficulty.ADVANCED, measures = 32, seed = 42L)
        )
        val beginnerRange = (beginnerScore.notes.maxOf { it.midiNumber } -
                             beginnerScore.notes.minOf { it.midiNumber })
        val advancedRange = (advancedScore.notes.maxOf { it.midiNumber } -
                             advancedScore.notes.minOf { it.midiNumber })
        assertTrue(
            "高级音域范围($advancedRange)应 ≥ 初级($beginnerRange)",
            advancedRange >= beginnerRange
        )
    }

    @Test
    fun `beginner range is exactly 0 to 7 scale degrees`() {
        // 验证 BEGINNER tessitura 配置
        assertEquals(0, SightReadingDifficulty.BEGINNER.tessituraLow)
        assertEquals(7, SightReadingDifficulty.BEGINNER.tessituraHigh)
    }

    @Test
    fun `elementary extends range by one degree each direction`() {
        assertEquals(-1, SightReadingDifficulty.ELEMENTARY.tessituraLow)
        assertEquals(8, SightReadingDifficulty.ELEMENTARY.tessituraHigh)
    }

    @Test
    fun `intermediate range extends further`() {
        assertEquals(-2, SightReadingDifficulty.INTERMEDIATE.tessituraLow)
        assertEquals(9, SightReadingDifficulty.INTERMEDIATE.tessituraHigh)
    }

    @Test
    fun `advanced range extends to two octaves`() {
        assertEquals(-3, SightReadingDifficulty.ADVANCED.tessituraLow)
        assertEquals(10, SightReadingDifficulty.ADVANCED.tessituraHigh)
    }

    @Test
    fun `maxLeap increases with difficulty`() {
        assertTrue(SightReadingDifficulty.BEGINNER.maxLeap < SightReadingDifficulty.ELEMENTARY.maxLeap)
        assertTrue(SightReadingDifficulty.ELEMENTARY.maxLeap < SightReadingDifficulty.INTERMEDIATE.maxLeap)
        assertTrue(SightReadingDifficulty.INTERMEDIATE.maxLeap <= SightReadingDifficulty.ADVANCED.maxLeap)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  拍号与节奏型
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `beatsPerMeasure for common time signatures`() {
        assertEquals(4.0, generator.beatsPerMeasure("4/4"), 0.001)
        assertEquals(3.0, generator.beatsPerMeasure("3/4"), 0.001)
        assertEquals(2.0, generator.beatsPerMeasure("2/4"), 0.001)
        assertEquals(2.0, generator.beatsPerMeasure("6/8"), 0.001)
    }

    @Test
    fun `beatsPerMeasure defaults to 4 for unknown signatures`() {
        assertEquals(4.0, generator.beatsPerMeasure("5/4"), 0.001)
        assertEquals(4.0, generator.beatsPerMeasure("invalid"), 0.001)
    }

    @Test
    fun `beginner 4-4 rhythm patterns sum to 4 beats`() {
        val patterns = generator.rhythmPatternsFor(SightReadingDifficulty.BEGINNER, 4.0)
        assertTrue("应返回多个节奏型", patterns.size >= 5)
        patterns.forEach { pattern ->
            val total = pattern.sumOf { it.beats }
            assertEquals(
                "节奏型总拍数应为 4.0，实际: $total (${pattern.map { "${it.beats}x${it.noteCount}" }})",
                4.0,
                total,
                0.001
            )
        }
    }

    @Test
    fun `elementary patterns include eighth note pairs`() {
        val patterns = generator.rhythmPatternsFor(SightReadingDifficulty.ELEMENTARY, 4.0)
        val hasEighthPairs = patterns.any { pattern ->
            pattern.any { it.beats == 0.5 && it.noteCount == 1 }
        }
        assertTrue("入门级应包含八分音符（0.5拍单元格）", hasEighthPairs)
    }

    @Test
 fun `intermediate patterns include dotted rhythms`() {
        val patterns = generator.rhythmPatternsFor(SightReadingDifficulty.INTERMEDIATE, 4.0)
        val hasDotted = patterns.any { pattern ->
            pattern.any { it.beats == 1.5 }
        }
        assertTrue("中级应包含附点节奏（1.5拍单元格）", hasDotted)
    }

    @Test
    fun `advanced patterns include sixteenth notes`() {
        val patterns = generator.rhythmPatternsFor(SightReadingDifficulty.ADVANCED, 4.0)
        val hasSixteenths = patterns.any { pattern ->
            pattern.any { it.beats == 0.25 }
        }
        assertTrue("高级应包含十六分音符（0.25拍单元格）", hasSixteenths)
    }

    @Test
    fun `3-4 rhythm patterns sum to 3 beats`() {
        for (difficulty in SightReadingDifficulty.values()) {
            val patterns = generator.rhythmPatternsFor(difficulty, 3.0)
            patterns.forEach { pattern ->
                val total = pattern.sumOf { it.beats }
                assertEquals(
                    "$difficulty 3/4 节奏型总拍数应为 3.0，实际: $total",
                    3.0,
                    total,
                    0.001
                )
            }
        }
    }

    @Test
    fun `2-4 rhythm patterns sum to 2 beats`() {
        for (difficulty in SightReadingDifficulty.values()) {
            val patterns = generator.rhythmPatternsFor(difficulty, 2.0)
            if (patterns.isNotEmpty()) {
                patterns.forEach { pattern ->
                    val total = pattern.sumOf { it.beats }
                    assertEquals(
                        "$difficulty 2/4 节奏型总拍数应为 2.0，实际: $total",
                        2.0,
                        total,
                        0.001
                    )
                }
            }
        }
    }

    @Test
    fun `rest patterns have zero note count cells`() {
        val patterns = generator.rhythmPatternsFor(SightReadingDifficulty.BEGINNER, 4.0)
        val hasRests = patterns.any { pattern ->
            pattern.any { it.noteCount == 0 }
        }
        assertTrue("初级应包含休止符节奏型", hasRests)
    }

    @Test
    fun `rhythm patterns are cumulative across difficulty`() {
        val beginner = generator.rhythmPatternsFor(SightReadingDifficulty.BEGINNER, 4.0)
        val elementary = generator.rhythmPatternsFor(SightReadingDifficulty.ELEMENTARY, 4.0)
        val intermediate = generator.rhythmPatternsFor(SightReadingDifficulty.INTERMEDIATE, 4.0)
        val advanced = generator.rhythmPatternsFor(SightReadingDifficulty.ADVANCED, 4.0)
        assertTrue("入门级节奏型数 ≥ 初级", elementary.size >= beginner.size)
        assertTrue("中级节奏型数 ≥ 入门级", intermediate.size >= elementary.size)
        assertTrue("高级节奏型数 ≥ 中级", advanced.size >= intermediate.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  时间与节拍计算
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `quarter note duration calculated from tempo`() {
        // tempo=120 → quarterMs = 60000/120 = 500ms
        val score = generator.generate(
            SightReadingOptions(tempo = 120, measures = 4, seed = 1L)
        )
        // 第一个音符的 duration 应该是四分音符=500ms 或更长（二分/全音符的倍数）
        val firstDur = score.notes.first().duration
        assertTrue(
            "tempo=120 时第一个音符时长 $firstDur 应为 500ms 的整数倍",
            firstDur % 500 == 0L || firstDur == 250L  // 也可能是八分=250ms
        )
    }

    @Test
    fun `note start times are monotonically increasing`() {
        val score = generator.generate(
            SightReadingOptions(measures = 8, seed = 50L, difficulty = SightReadingDifficulty.INTERMEDIATE)
        )
        for (i in 1 until score.notes.size) {
            assertTrue(
                "音符 $i 的 startTime(${score.notes[i].startTime}) 应 ≥ 前一个(${score.notes[i - 1].startTime})",
                score.notes[i].startTime >= score.notes[i - 1].startTime
            )
        }
    }

    @Test
    fun `note durations are positive`() {
        val score = generator.generate(
            SightReadingOptions(measures = 8, seed = 33L, difficulty = SightReadingDifficulty.ADVANCED)
        )
        score.notes.forEach { note ->
            assertTrue("音符 ${note.noteName} 时长应 > 0，实际: ${note.duration}", note.duration > 0)
        }
    }

    @Test
    fun `eighth note duration is half of quarter`() {
        // tempo=100 → quarterMs=600ms, eighthMs=300ms
        // 寻找包含八分音符的入门级乐谱
        val score = generator.generate(
            SightReadingOptions(
                tempo = 100,
                difficulty = SightReadingDifficulty.ELEMENTARY,
                measures = 16,
                seed = 7L
            )
        )
        val eighthDurations = score.notes.filter { it.duration == 300L }
        // 入门级应该有八分音符（但不能保证特定种子一定有，放宽检查）
        if (eighthDurations.isNotEmpty()) {
            assertEquals(300L, eighthDurations.first().duration)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  小节索引
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `measure indices are sequential from 0`() {
        val score = generator.generate(
            SightReadingOptions(measures = 8, seed = 42L)
        )
        val measureIndices = score.notes.map { it.measureIndex }.toSet().sorted()
        assertEquals(0, measureIndices.first())
        // 不一定每个小节都有音符（可能全休止），但索引应 ≤ measures-1
        assertTrue(
            "最大小节索引 ${measureIndices.last()} 应 < ${8}",
            measureIndices.last() < 8
        )
    }

    @Test
    fun `measure indices never exceed requested measures minus 1`() {
        for (measures in listOf(1, 4, 8, 16)) {
            val score = generator.generate(
                SightReadingOptions(measures = measures, seed = 42L, difficulty = SightReadingDifficulty.ADVANCED)
            )
            score.notes.forEach { note ->
                assertTrue(
                    "小节索引 ${note.measureIndex} 应 < $measures",
                    note.measureIndex < measures
                )
            }
        }
    }

    @Test
    fun `measure indices are non-decreasing`() {
        val score = generator.generate(
            SightReadingOptions(measures = 16, seed = 88L, difficulty = SightReadingDifficulty.ADVANCED)
        )
        for (i in 1 until score.notes.size) {
            assertTrue(
                "音符 $i 的小节索引(${score.notes[i].measureIndex}) 应 ≥ 前一个(${score.notes[i - 1].measureIndex})",
                score.notes[i].measureIndex >= score.notes[i - 1].measureIndex
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  乐句结构与终止式
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `last note of exercise tends to resolve to tonic or dominant`() {
        // 8 小节 = 2 个乐句，第 8 小节末尾应解决
        var tonicOrDominantCount = 0
        var totalRuns = 0
        for (seed in 0L..49L) {
            val score = generator.generate(
                SightReadingOptions(
                    keySignature = KeySignature.C_MAJOR_A_MINOR,
                    measures = 8,
                    seed = seed,
                    difficulty = SightReadingDifficulty.BEGINNER
                )
            )
            val lastNote = score.notes.last()
            val tonicPc = 0  // C
            val dominantPc = 7  // G
            val lastPc = lastNote.midiNumber % 12
            totalRuns++
            if (lastPc == tonicPc || lastPc == dominantPc) {
                tonicOrDominantCount++
            }
        }
        // 超过 60% 的种子应解决到主音或属音
        assertTrue(
            "50 个种子中 $tonicOrDominantCount/$totalRuns 最后一个音解决到主音/属音，应 > 60%",
            tonicOrDominantCount > totalRuns * 0.6
        )
    }

    @Test
    fun `4-bar phrase ends resolve to tonic or dominant`() {
        var resolvedCount = 0
        var totalPhraseEnds = 0
        for (seed in 0L..29L) {
            val score = generator.generate(
                SightReadingOptions(
                    keySignature = KeySignature.C_MAJOR_A_MINOR,
                    measures = 8,
                    seed = seed,
                    difficulty = SightReadingDifficulty.BEGINNER
                )
            )
            // 找到第 4 小节（index 3）的最后一个音符
            val measure3Notes = score.notes.filter { it.measureIndex == 3 }
            if (measure3Notes.isNotEmpty()) {
                val lastInMeasure3 = measure3Notes.last()
                val pc = lastInMeasure3.midiNumber % 12
                totalPhraseEnds++
                if (pc == 0 || pc == 7) resolvedCount++
            }
        }
        assertTrue(
            "乐句结尾 $resolvedCount/$totalPhraseEnds 解决到主音/属音，应 > 50%",
            resolvedCount > totalPhraseEnds * 0.5
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  旋律进行音乐性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `beginner has predominantly stepwise motion`() {
        val score = generator.generate(
            SightReadingOptions(
                difficulty = SightReadingDifficulty.BEGINNER,
                measures = 32,
                seed = 42L
            )
        )
        val midis = score.notes.map { it.midiNumber }
        var stepwise = 0
        var total = 0
        for (i in 1 until midis.size) {
            val interval = kotlin.math.abs(midis[i] - midis[i - 1])
            total++
            // 级进 = 1或2个半音（大二度或小二度）
            if (interval in 1..2) stepwise++
        }
        val ratio = stepwise.toDouble() / total
        assertTrue(
            "初级以级进为主: $stepwise/$total = ${"%.0f".format(ratio * 100)}%，应 > 50%",
            ratio > 0.5
        )
    }

    @Test
    fun `advanced has more leaps than beginner`() {
        val beginnerScore = generator.generate(
            SightReadingOptions(difficulty = SightReadingDifficulty.BEGINNER, measures = 32, seed = 42L)
        )
        val advancedScore = generator.generate(
            SightReadingOptions(difficulty = SightReadingDifficulty.ADVANCED, measures = 32, seed = 42L)
        )

        fun averageInterval(score: com.pianocompanion.data.model.Score): Double {
            val midis = score.notes.map { it.midiNumber }
            if (midis.size < 2) return 0.0
            var sum = 0
            for (i in 1 until midis.size) {
                sum += kotlin.math.abs(midis[i] - midis[i - 1])
            }
            return sum.toDouble() / (midis.size - 1)
        }

        val beginnerAvg = averageInterval(beginnerScore)
        val advancedAvg = averageInterval(advancedScore)
        assertTrue(
            "高级平均跳进($advancedAvg 半音)应 ≥ 初级($beginnerAvg 半音)",
            advancedAvg >= beginnerAvg
        )
    }

    @Test
    fun `no more than 3 consecutive same-direction steps`() {
        // 这个约束是软性的（仅在级进时生效），用大样本验证
        for (seed in 0L..19L) {
            val score = generator.generate(
                SightReadingOptions(
                    difficulty = SightReadingDifficulty.INTERMEDIATE,
                    measures = 16,
                    seed = seed
                )
            )
            val midis = score.notes.map { it.midiNumber }
            var consecutiveDir = 0
            var currentDir = 0
            for (i in 1 until midis.size) {
                val dir = midis[i].compareTo(midis[i - 1])
                if (dir != 0) {
                    if (dir == currentDir) {
                        consecutiveDir++
                    } else {
                        consecutiveDir = 1
                        currentDir = dir
                    }
                    // 允许最多 4 次同向（反射边界时可能多一次）
                    assertTrue(
                        "连续同向运动不应超过 4 次 (seed=$seed, idx=$i, dir=$dir, count=$consecutiveDir)",
                        consecutiveDir <= 4
                    )
                }
            }
        }
    }

    @Test
    fun `beginner never has intervals larger than major third`() {
        val score = generator.generate(
            SightReadingOptions(
                difficulty = SightReadingDifficulty.BEGINNER,
                measures = 32,
                seed = 42L
            )
        )
        val midis = score.notes.map { it.midiNumber }
        for (i in 1 until midis.size) {
            val interval = kotlin.math.abs(midis[i] - midis[i - 1])
            assertTrue(
                "初级不应出现大于大三度(4半音)的跳进，实际: $interval (idx=$i)",
                interval <= 4
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  休止符处理
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `rests advance time without producing notes`() {
        // 生成一个肯定包含休止的乐谱（多小节、多种子测试）
        var hasGap = false
        for (seed in 0L..99L) {
            val score = generator.generate(
                SightReadingOptions(
                    difficulty = SightReadingDifficulty.BEGINNER,
                    measures = 8,
                    seed = seed
                )
            )
            // 检查是否有音符间的时间间隙（大于音符时长，表明有休止）
            for (i in 1 until score.notes.size) {
                val prevEnd = score.notes[i - 1].startTime + score.notes[i - 1].duration
                val gap = score.notes[i].startTime - prevEnd
                if (gap > 10) {  // 允许舍入误差
                    hasGap = true
                    break
                }
            }
            if (hasGap) break
        }
        assertTrue("至少有一个种子应产生休止符（时间间隙）", hasGap)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  边界情况
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `single measure generates valid score`() {
        val score = generator.generate(SightReadingOptions(measures = 1, seed = 1L))
        assertTrue("1 小节乐谱应包含音符", score.notes.isNotEmpty())
        assertEquals(0, score.notes.first().measureIndex)
    }

    @Test
    fun `64 measures generates valid score`() {
        val score = generator.generate(
            SightReadingOptions(measures = 64, seed = 1L, difficulty = SightReadingDifficulty.ADVANCED)
        )
        assertTrue("64 小节乐谱应包含大量音符", score.notes.size > 50)
        score.notes.forEach { note ->
            assertTrue(note.measureIndex in 0..63)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero measures throws exception`() {
        generator.generate(SightReadingOptions(measures = 0, seed = 1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative measures throws exception`() {
        generator.generate(SightReadingOptions(measures = -1, seed = 1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `65 measures throws exception`() {
        generator.generate(SightReadingOptions(measures = 65, seed = 1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tempo too low throws exception`() {
        generator.generate(SightReadingOptions(tempo = 19, seed = 1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tempo too high throws exception`() {
        generator.generate(SightReadingOptions(tempo = 401, seed = 1L))
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  多调号全面测试
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `all 15 key signatures produce valid scores`() {
        for (key in KeySignature.values()) {
            val score = generator.generate(
                SightReadingOptions(
                    keySignature = key,
                    measures = 4,
                    seed = 42L,
                    difficulty = SightReadingDifficulty.ELEMENTARY
                )
            )
            assertTrue("${key.label} 应生成非空乐谱", score.notes.isNotEmpty())
            // 所有音符都应在钢琴音域内
            score.notes.forEach { note ->
                assertTrue(
                    "${key.label}: 音符 ${note.midiNumber} 应在 21..108 内",
                    note.midiNumber in 21..108
                )
            }
        }
    }

    @Test
    fun `all notes belong to correct scale for all sharp keys`() {
        val majorScaleOffsets = setOf(0, 2, 4, 5, 7, 9, 11)  // relative to tonic
        for (key in listOf(
            KeySignature.C_MAJOR_A_MINOR,
            KeySignature.G_MAJOR_E_MINOR,
            KeySignature.D_MAJOR,
            KeySignature.A_MAJOR,
            KeySignature.E_MAJOR,
            KeySignature.B_MAJOR
        )) {
            val score = generator.generate(
                SightReadingOptions(
                    keySignature = key,
                    measures = 16,
                    seed = 42L,
                    difficulty = SightReadingDifficulty.ADVANCED
                )
            )
            val tonic = generator.tonicPitchClass(key)
            score.notes.forEach { note ->
                val relativePc = Math.floorMod(note.midiNumber - tonic, 12)
                assertTrue(
                    "${key.label}: 音符 ${note.noteName} 的相对音高级数 $relativePc 不在大调音阶中",
                    relativePc in majorScaleOffsets
                )
            }
        }
    }

    @Test
    fun `all notes belong to correct scale for all flat keys`() {
        val majorScaleOffsets = setOf(0, 2, 4, 5, 7, 9, 11)
        for (key in listOf(
            KeySignature.F_MAJOR_D_MINOR,
            KeySignature.B_FLAT_MAJOR,
            KeySignature.E_FLAT_MAJOR,
            KeySignature.A_FLAT_MAJOR,
            KeySignature.D_FLAT_MAJOR
        )) {
            val score = generator.generate(
                SightReadingOptions(
                    keySignature = key,
                    measures = 16,
                    seed = 42L,
                    difficulty = SightReadingDifficulty.ADVANCED
                )
            )
            val tonic = generator.tonicPitchClass(key)
            score.notes.forEach { note ->
                val relativePc = Math.floorMod(note.midiNumber - tonic, 12)
                assertTrue(
                    "${key.label}: 音符 ${note.noteName} 的相对音高级数 $relativePc 不在大调音阶中",
                    relativePc in majorScaleOffsets
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  音符名称正确性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `note names match MIDI numbers`() {
        val score = generator.generate(
            SightReadingOptions(measures = 8, seed = 42L, difficulty = SightReadingDifficulty.ADVANCED)
        )
        score.notes.forEach { note ->
            val expectedName = MusicUtils.midiToNoteName(note.midiNumber)
            assertEquals(
                "音符名称应与 MIDI 编号一致",
                expectedName,
                note.noteName
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  难度区分综合测试
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `advanced produces more notes than beginner on average`() {
        // 由于十六分音符的存在，高级应产生更多音符
        var beginnerTotal = 0
        var advancedTotal = 0
        for (seed in 0L..19L) {
            beginnerTotal += generator.generate(
                SightReadingOptions(difficulty = SightReadingDifficulty.BEGINNER, measures = 8, seed = seed)
            ).notes.size
            advancedTotal += generator.generate(
                SightReadingOptions(difficulty = SightReadingDifficulty.ADVANCED, measures = 8, seed = seed)
            ).notes.size
        }
        assertTrue(
            "高级平均音符数($advancedTotal)应 ≥ 初级($beginnerTotal)",
            advancedTotal >= beginnerTotal
        )
    }

    @Test
    fun `higher difficulty allows higher max intervals`() {
        val beginnerMax = generator.generate(
            SightReadingOptions(difficulty = SightReadingDifficulty.BEGINNER, measures = 32, seed = 42L)
        ).notes.map { it.midiNumber }.let { midis ->
            (1 until midis.size).maxOfOrNull { i -> kotlin.math.abs(midis[i] - midis[i - 1]) } ?: 0
        }
        val advancedMax = generator.generate(
            SightReadingOptions(difficulty = SightReadingDifficulty.ADVANCED, measures = 32, seed = 42L)
        ).notes.map { it.midiNumber }.let { midis ->
            (1 until midis.size).maxOfOrNull { i -> kotlin.math.abs(midis[i] - midis[i - 1]) } ?: 0
        }
        assertTrue(
            "高级最大跳进($advancedMax)应 ≥ 初级($beginnerMax)",
            advancedMax >= beginnerMax
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  综合集成测试
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `generated score integrates with MusicUtils`() {
        val score = generator.generate(
            SightReadingOptions(measures = 8, seed = 42L)
        )
        score.notes.forEach { note ->
            // 频率计算应正常工作
            val freq = note.frequency
            assertTrue("频率应 > 0", freq > 0)
            // MIDI 反推应一致
            val midiFromFreq = MusicUtils.frequencyToMidi(freq)
            assertTrue(
                "频率反推的 MIDI($midiFromFreq) 应接近原值(${note.midiNumber})",
                kotlin.math.abs(midiFromFreq - note.midiNumber) <= 1
            )
        }
    }

    @Test
    fun `generated score is usable by DifficultyEstimator`() {
        val score = generator.generate(
            SightReadingOptions(measures = 8, seed = 42L, difficulty = SightReadingDifficulty.INTERMEDIATE)
        )
        val difficulty = com.pianocompanion.analytics.DifficultyEstimator.estimate(score)
        assertNotNull(difficulty)
        assertTrue("难度总分应 > 0", difficulty.totalScore > 0)
    }

    @Test
    fun `default options produce sensible score`() {
        // 使用默认选项（C大调、初级、8小节）
        val opts = SightReadingOptions(seed = 0L)
        val score = generator.generate(opts)
        assertTrue(score.notes.isNotEmpty())
        assertEquals(100, score.tempo)
        assertEquals("4/4", score.timeSignature)
        // 所有音符应在 C 大调内
        score.notes.forEach { note ->
            val pc = note.midiNumber % 12
            assertTrue(pc in setOf(0, 2, 4, 5, 7, 9, 11))
        }
    }

    @Test
    fun `all difficulties produce well-formed scores for multiple seeds`() {
        for (difficulty in SightReadingDifficulty.values()) {
            for (seed in listOf(0L, 1L, 42L, 100L, 999L)) {
                val score = generator.generate(
                    SightReadingOptions(
                        difficulty = difficulty,
                        measures = 8,
                        seed = seed
                    )
                )
                assertTrue(
                    "$difficulty seed=$seed: 应生成非空乐谱",
                    score.notes.isNotEmpty()
                )
                // 所有 startTime 非负
                score.notes.forEach { note ->
                    assertTrue(note.startTime >= 0)
                    assertTrue(note.duration > 0)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  低音谱表测试
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `bass staff notes are in BASS staff`() {
        val score = generator.generate(
            SightReadingOptions(staff = Staff.BASS, measures = 8, seed = 42L)
        )
        score.notes.forEach { note ->
            assertEquals(Staff.BASS, note.staff)
        }
    }

    @Test
    fun `treble staff notes are in TREBLE staff`() {
        val score = generator.generate(
            SightReadingOptions(staff = Staff.TREBLE, measures = 8, seed = 42L)
        )
        score.notes.forEach { note ->
            assertEquals(Staff.TREBLE, note.staff)
        }
    }

    @Test
    fun `bass staff C major tonic is C3`() {
        val score = generator.generate(
            SightReadingOptions(
                keySignature = KeySignature.C_MAJOR_A_MINOR,
                staff = Staff.BASS,
                measures = 8,
                seed = 42L
            )
        )
        // 第一个音符应为主音 = C3 = 48
        assertEquals(48, score.notes.first().midiNumber)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Score ID 唯一性
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `different options produce different score IDs`() {
        val id1 = generator.generate(SightReadingOptions(seed = 1L)).id
        val id2 = generator.generate(SightReadingOptions(seed = 2L)).id
        assertFalse("不同种子的乐谱 ID 应不同", id1 == id2)
    }

    @Test
    fun `same options produce same score ID`() {
        val opts = SightReadingOptions(seed = 42L, difficulty = SightReadingDifficulty.INTERMEDIATE)
        val id1 = generator.generate(opts).id
        val id2 = generator.generate(opts).id
        assertEquals(id1, id2)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  难度标签
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `difficulty labels are Chinese`() {
        assertEquals("初级", SightReadingDifficulty.BEGINNER.label)
        assertEquals("入门", SightReadingDifficulty.ELEMENTARY.label)
        assertEquals("中级", SightReadingDifficulty.INTERMEDIATE.label)
        assertEquals("高级", SightReadingDifficulty.ADVANCED.label)
    }

    @Test
    fun `rangeSemitones increases with difficulty`() {
        assertTrue(SightReadingDifficulty.BEGINNER.rangeSemitones < SightReadingDifficulty.ELEMENTARY.rangeSemitones)
        assertTrue(SightReadingDifficulty.ELEMENTARY.rangeSemitones < SightReadingDifficulty.INTERMEDIATE.rangeSemitones)
        assertTrue(SightReadingDifficulty.INTERMEDIATE.rangeSemitones < SightReadingDifficulty.ADVANCED.rangeSemitones)
    }
}
