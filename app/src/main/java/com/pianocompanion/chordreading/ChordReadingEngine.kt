package com.pianocompanion.chordreading

import kotlin.random.Random

/**
 * 和弦识别训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ChordReadingClef]、[ChordReadingDifficulty] 和随机种子生成 [ChordReadingQuestion]。
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * 设计要点：
 * - 所有题目仅使用自然音（白键），不含升降号
 * - 谱表位置系统与 [com.pianocompanion.notation.NoteReadingEngine] /
 *   [com.pianocompanion.interval.IntervalEngine] 一致：底线 = step 0
 * - 三和弦 = 根音 + 三音(root+2 步) + 五音(root+4 步)
 * - 七和弦 = 三和弦 + 七音(root+6 步)
 * - 初级仅出大三/小三（根音避开 B）；中级加入减三（含 B）；高级出七和弦
 * - 选项固定为 4 个（三和弦阶段：大/小/减/增；七和弦阶段：大七/属七/小七/半减七）
 *
 * 和弦分类算法：
 * 1. 根音 = 最低音；三度 = 三音 MIDI - 根音 MIDI
 * 2. 五度 = 五音 MIDI - 根音 MIDI
 * 3. （七和弦）七度 = 七音 MIDI - 根音 MIDI
 * 4. 查表分类（[classifyTriad] / [classifySeventh]）
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class ChordReadingEngine(
    private val root: Random = Random.Default
) {

    /**
     * 生成一道题目。
     *
     * @param clef 谱号
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(
        clef: ChordReadingClef,
        difficulty: ChordReadingDifficulty
    ): ChordReadingQuestion {
        val isSeventh = difficulty == ChordReadingDifficulty.ADVANCED
        val stepCount = if (isSeventh) 4 else 3

        // 选择根音的谱表位置
        val rootStep = pickRootStep(clef, difficulty, stepCount)

        // 计算各音的谱表位置（按三度叠置）
        val staffSteps = (0 until stepCount).map { i -> rootStep + i * 2 }

        // 谱表位置 → MIDI
        val midis = staffSteps.map { diatonicStepToMidi(clef, it) }

        // MIDI → 音名字母
        val names = midis.map { midiToLetterName(it) }

        // 分类和弦类型
        val chordType = classify(midis)

        // 构建答案选项
        val options = if (isSeventh) {
            ChordType.SEVENTHS.map { it.displayName }
        } else {
            ChordType.TRIADS.map { it.displayName }
        }
        val shuffledChoices = options.shuffled(root)
        val correctAnswer = chordType.displayName

        return ChordReadingQuestion(
            clef = clef,
            difficulty = difficulty,
            noteStaffSteps = staffSteps,
            noteMidis = midis,
            noteNames = names,
            rootLetterName = names.first(),
            chordType = chordType,
            isSeventh = isSeventh,
            answerChoices = shuffledChoices,
            correctAnswer = correctAnswer
        )
    }

    // ── 谱表位置 → MIDI ──────────────────────────────────

    /**
     * 将谱表位置（step）转换为 MIDI 音符号。
     *
     * 与 NoteReadingEngine / IntervalEngine 相同的算法：
     * - 高音谱号底线 = E4：letter=2(E), octave=4
     * - 低音谱号底线 = G2：letter=4(G), octave=2
     * - 每上一个 step 对应字母 +1，跨 C 时八度 +1
     *
     * @param clef 谱号
     * @param step 谱表位置（底线 = 0）
     * @return MIDI 音符号
     */
    fun diatonicStepToMidi(clef: ChordReadingClef, step: Int): Int {
        val baseLetter = if (clef == ChordReadingClef.TREBLE) LETTER_E else LETTER_G
        val baseOctave = if (clef == ChordReadingClef.TREBLE) 4 else 2

        val totalDiatonic = baseLetter + step
        val letter = Math.floorMod(totalDiatonic, 7)
        val octaveOffset = Math.floorDiv(totalDiatonic, 7)
        val octave = baseOctave + octaveOffset

        return LETTER_SEMITONES[letter] + 12 * (octave + 1)
    }

    /**
     * MIDI 音符号 → 音名字母（仅自然音）。
     */
    fun midiToLetterName(midi: Int): String {
        val pc = Math.floorMod(midi, 12)
        return PC_TO_LETTER[pc] ?: ""
    }

    // ── 内部方法 ──────────────────────────────────────────

    /**
     * 选择根音的谱表位置。
     *
     * 根据难度限制可出现的和弦类型：
     * - 初级：根音避开 B（letter class 6），只出现大三/小三
     * - 中级/高级：所有根音字母均可
     *
     * 确保和弦最高音（rootStep + 2*(stepCount-1)）不超出合理范围。
     *
     * @param stepCount 和弦音符数（三和弦=3，七和弦=4）
     * @return 根音谱表位置
     */
    private fun pickRootStep(
        clef: ChordReadingClef,
        difficulty: ChordReadingDifficulty,
        stepCount: Int
    ): Int {
        val topSpan = 2 * (stepCount - 1) // 最高音相对根音的步距

        // 根音起始范围（根据难度）
        val range = when (difficulty) {
            ChordReadingDifficulty.BEGINNER -> when (clef) {
                ChordReadingClef.TREBLE -> 1..7
                ChordReadingClef.BASS -> 1..7
            }
            ChordReadingDifficulty.INTERMEDIATE -> when (clef) {
                ChordReadingClef.TREBLE -> 0..9
                ChordReadingClef.BASS -> 0..9
            }
            ChordReadingDifficulty.ADVANCED -> when (clef) {
                ChordReadingClef.TREBLE -> 0..6
                ChordReadingClef.BASS -> 0..6
            }
        }

        val baseLetter = if (clef == ChordReadingClef.TREBLE) LETTER_E else LETTER_G

        // 过滤出合法的根音位置：
        // 1) 最高音不超出范围（rootStep + topSpan <= range.last + 少量溢出容忍）
        // 2) 初级排除 letter class B(6)
        val excludeB = difficulty == ChordReadingDifficulty.BEGINNER
        val validSteps = (range.first..range.last).filter { step ->
            val topStep = step + topSpan
            topStep <= range.last + 2 &&
                (!excludeB || Math.floorMod(baseLetter + step, 7) != LETTER_B)
        }

        return if (validSteps.isNotEmpty()) {
            validSteps.random(root)
        } else {
            range.first
        }
    }

    /**
     * 根据 MIDI 音符列表分类和弦类型。
     *
     * @param midis 从低到高排列的 MIDI 音符（三和弦 3 个 / 七和弦 4 个）
     * @return 和弦类型
     */
    fun classify(midis: List<Int>): ChordType {
        require(midis.size >= 3) { "和弦至少需要 3 个音" }
        val root = midis[0]
        val third = midis[1] - root
        val fifth = midis[2] - root
        return if (midis.size >= 4) {
            val seventh = midis[3] - root
            classifySeventh(third, fifth, seventh)
        } else {
            classifyTriad(third, fifth)
        }
    }

    /**
     * 根据三度、五度半音数分类三和弦。
     */
    fun classifyTriad(thirdSemitones: Int, fifthSemitones: Int): ChordType {
        return when (Pair(thirdSemitones, fifthSemitones)) {
            Pair(4, 7) -> ChordType.MAJOR
            Pair(3, 7) -> ChordType.MINOR
            Pair(3, 6) -> ChordType.DIMINISHED
            Pair(4, 8) -> ChordType.AUGMENTED
            else -> ChordType.MAJOR // 容错回退
        }
    }

    /**
     * 根据三度、五度、七度半音数分类七和弦。
     */
    fun classifySeventh(
        thirdSemitones: Int,
        fifthSemitones: Int,
        seventhSemitones: Int
    ): ChordType {
        return when (Triple(thirdSemitones, fifthSemitones, seventhSemitones)) {
            Triple(4, 7, 11) -> ChordType.MAJOR_SEVENTH
            Triple(4, 7, 10) -> ChordType.DOMINANT_SEVENTH
            Triple(3, 7, 10) -> ChordType.MINOR_SEVENTH
            Triple(3, 6, 10) -> ChordType.HALF_DIMINISHED_SEVENTH
            else -> ChordType.DOMINANT_SEVENTH // 容错回退
        }
    }

    companion object {
        /** 音名字母索引：C=0, D=1, E=2, F=3, G=4, A=5, B=6 */
        const val LETTER_C = 0
        const val LETTER_D = 1
        const val LETTER_E = 2
        const val LETTER_F = 3
        const val LETTER_G = 4
        const val LETTER_A = 5
        const val LETTER_B = 6

        /** 各字母对应的半音数（相对 C） */
        val LETTER_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        /** 音级类（pitch class）→ 字母名，仅自然音 */
        val PC_TO_LETTER = mapOf(
            0 to "C", 2 to "D", 4 to "E", 5 to "F",
            7 to "G", 9 to "A", 11 to "B"
        )

        /** 字母名列表（C 到 B） */
        val LETTER_NAMES = listOf("C", "D", "E", "F", "G", "A", "B")

        /**
         * 创建带固定种子的引擎实例（用于测试确定性）。
         */
        fun withSeed(seed: Long): ChordReadingEngine = ChordReadingEngine(Random(seed))
    }
}
