package com.pianocompanion.notation

import kotlin.random.Random

/**
 * 识谱训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [NoteReadingClef]、[NoteReadingDifficulty] 和随机种子生成 [NoteReadingQuestion]。
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * 设计要点：
 * - 所有题目仅使用自然音（C/D/E/F/G/A/B），不含升降号
 * - 谱表位置系统：底线 = step 0，偶数步 = 线上，奇数步 = 间内
 * - 初级仅出五线谱线上的音符，中级加入间，高级加入上下加线音符
 * - 选项数固定为 4（正确答案 + 3 个干扰项），从同谱号同难度的可能音名中随机选取
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class NoteReadingEngine(
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
        clef: NoteReadingClef,
        difficulty: NoteReadingDifficulty
    ): NoteReadingQuestion {
        val pool = stepPool(difficulty)
        val step = pool.random(root)
        val midi = diatonicStepToMidi(clef, step)
        val letter = midiToLetterName(midi)
        val octave = midiToOctave(midi)
        val fullNoteName = "$letter$octave"

        // 从该难度下所有可能的音名中构建选项
        val allLetters = pool
            .map { diatonicStepToMidi(clef, it) }
            .map { midiToLetterName(it) }
            .distinct()

        val choices = buildChoices(letter, allLetters)

        return NoteReadingQuestion(
            clef = clef,
            difficulty = difficulty,
            staffStep = step,
            midiNote = midi,
            letterName = letter,
            fullNoteName = fullNoteName,
            answerChoices = choices
        )
    }

    // ── 谱表位置 → MIDI ──────────────────────────────────

    /**
     * 将谱表位置（step）转换为 MIDI 音符号。
     *
     * 原理：
     * - 每个谱表底线对应一个基础音级（letter）和八度
     *   - 高音谱号底线 = E4：letter=2(E), octave=4
     *   - 低音谱号底线 = G2：letter=4(G), octave=2
     * - 每上一个 step 对应字母 +1（C→D→E→F→G→A→B→C 循环）
     * - 字母跨 C 时八度 +1（B→C 是八度边界）
     * - 每个字母对应固定的半音数：C=0, D=2, E=4, F=5, G=7, A=9, B=11
     * - MIDI = 半音数 + 12 × (八度 + 1)
     *
     * @param clef 谱号
     * @param step 谱表位置（底线 = 0）
     * @return MIDI 音符号
     */
    fun diatonicStepToMidi(clef: NoteReadingClef, step: Int): Int {
        val baseLetter = if (clef == NoteReadingClef.TREBLE) LETTER_E else LETTER_G
        val baseOctave = if (clef == NoteReadingClef.TREBLE) 4 else 2

        val totalDiatonic = baseLetter + step
        val letter = Math.floorMod(totalDiatonic, 7)
        val octaveOffset = Math.floorDiv(totalDiatonic, 7)
        val octave = baseOctave + octaveOffset

        return LETTER_SEMITONES[letter] + 12 * (octave + 1)
    }

    /**
     * MIDI 音符号 → 音名字母（仅自然音）。
     * 返回 "C".."B" 之一。如果 MIDI 对应升降音（非自然音），返回空字符串。
     */
    fun midiToLetterName(midi: Int): String {
        val pc = Math.floorMod(midi, 12)
        return PC_TO_LETTER[pc] ?: ""
    }

    /**
     * MIDI 音符号 → 八度数。
     * MIDI 60 (C4) → 4, MIDI 69 (A4) → 4, MIDI 72 (C5) → 5。
     */
    fun midiToOctave(midi: Int): Int = midi / 12 - 1

    // ── 难度池 ────────────────────────────────────────────

    /**
     * 各难度可用的谱表位置范围。
     *
     * 五线谱结构（从底线到顶线 step 0-8）：
     * - 线（偶数步）：0, 2, 4, 6, 8
     * - 间（奇数步）：1, 3, 5, 7
     * - 底线下方：-1, -2（加线）, -3, -4
     * - 顶线上方：9, 10（加线）, 11, 12
     */
    private fun stepPool(difficulty: NoteReadingDifficulty): List<Int> = when (difficulty) {
        NoteReadingDifficulty.BEGINNER -> listOf(0, 2, 4, 6, 8)
        NoteReadingDifficulty.INTERMEDIATE -> listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        NoteReadingDifficulty.ADVANCED -> listOf(-2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    }

    // ── 工具方法 ──────────────────────────────────────────

    /**
     * 构建选项列表：正确答案 + 从池中随机选取的干扰项，共 4 个选项（或池大小，取较小值）。
     * 保证所有选项唯一且打乱顺序。
     */
    private fun buildChoices(correct: String, allLabels: List<String>): List<String> {
        val distractors = allLabels.filter { it != correct }
        val numChoices = minOf(allLabels.size, 4).coerceAtLeast(minOf(allLabels.size, 2))
        val numDistractors = numChoices - 1
        val selectedDistractors = if (distractors.size <= numDistractors) {
            distractors
        } else {
            distractors.shuffled(root).take(numDistractors)
        }
        return (selectedDistractors + correct).shuffled(root)
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
        fun withSeed(seed: Long): NoteReadingEngine = NoteReadingEngine(Random(seed))
    }
}
