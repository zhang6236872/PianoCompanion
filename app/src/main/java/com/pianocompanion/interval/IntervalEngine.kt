package com.pianocompanion.interval

import kotlin.random.Random

/**
 * 音程识别训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [IntervalClef]、[IntervalDifficulty] 和随机种子生成 [IntervalQuestion]。
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * 设计要点：
 * - 所有题目仅使用自然音（白键），不含升降号
 * - 谱表位置系统与 [com.pianocompanion.notation.NoteReadingEngine] 一致：底线 = step 0
 * - 初级仅出度数判断（2-8 度），中级/高级加入性质判断（大/小/纯/增/减）
 * - 两个音符保证不同（度数 ≥ 2），避免出现一度
 * - 选项数固定为 4（正确答案 + 3 个干扰项）
 *
 * 音程分类算法：
 * 1. 度数 = |较高音自然音步距 - 较低音自然音步距| + 1
 * 2. 半音差 = 较高音 MIDI - 较低音 MIDI
 * 3. 性质 = 查表分类（[Interval.classify]）
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class IntervalEngine(
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
        clef: IntervalClef,
        difficulty: IntervalDifficulty
    ): IntervalQuestion {
        // 选择音程跨度（自然音步距），根据难度限制最大跨度
        val maxSpan = when (difficulty) {
            IntervalDifficulty.BEGINNER -> 4   // 最大五度（跨4步）
            IntervalDifficulty.INTERMEDIATE -> 6 // 最大七度
            IntervalDifficulty.ADVANCED -> 7     // 最大八度
        }
        val minSpan = 1  // 最小二度（跨1步）
        val span = (minSpan..maxSpan).random(root)

        // 选择较低音的谱表位置
        val (lowStep, highStep) = pickNotePositions(clef, span, difficulty)

        val lowMidi = diatonicStepToMidi(clef, lowStep)
        val highMidi = diatonicStepToMidi(clef, highStep)
        val lowLetter = midiToLetterName(lowMidi)
        val highLetter = midiToLetterName(highMidi)

        // 计算音程
        val diatonicDiff = highStep - lowStep
        val semitoneDiff = highMidi - lowMidi
        val number = IntervalNumber.fromDiatonicSteps(diatonicDiff)
            ?: IntervalNumber.OCTAVE
        val interval = Interval.classify(number, semitoneDiff)!!

        // 构建答案和选项
        val requiresQuality = difficulty.requiresQuality
        val correctAnswer = if (requiresQuality) interval.displayName else number.displayName

        val choices = buildChoices(interval, number, requiresQuality, difficulty)

        return IntervalQuestion(
            clef = clef,
            difficulty = difficulty,
            lowerStaffStep = lowStep,
            higherStaffStep = highStep,
            lowerMidi = lowMidi,
            higherMidi = highMidi,
            lowerLetterName = lowLetter,
            higherLetterName = highLetter,
            interval = interval,
            requiresQuality = requiresQuality,
            answerChoices = choices,
            correctAnswer = correctAnswer
        )
    }

    // ── 谱表位置 → MIDI ──────────────────────────────────

    /**
     * 将谱表位置（step）转换为 MIDI 音符号。
     *
     * 与 NoteReadingEngine 相同的算法：
     * - 高音谱号底线 = E4：letter=2(E), octave=4
     * - 低音谱号底线 = G2：letter=4(G), octave=2
     * - 每上一个 step 对应字母 +1，跨 C 时八度 +1
     *
     * @param clef 谱号
     * @param step 谱表位置（底线 = 0）
     * @return MIDI 音符号
     */
    fun diatonicStepToMidi(clef: IntervalClef, step: Int): Int {
        val baseLetter = if (clef == IntervalClef.TREBLE) LETTER_E else LETTER_G
        val baseOctave = if (clef == IntervalClef.TREBLE) 4 else 2

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

    /**
     * MIDI 音符号 → 八度数。
     */
    fun midiToOctave(midi: Int): Int = midi / 12 - 1

    // ── 内部方法 ──────────────────────────────────────────

    /**
     * 选择两个音符的谱表位置，保证跨度为 [span] 个自然音步距。
     * 根据难度选择可用的起始位置范围。
     *
     * @return (较低音 step, 较高音 step)
     */
    private fun pickNotePositions(
        clef: IntervalClef,
        span: Int,
        difficulty: IntervalDifficulty
    ): Pair<Int, Int> {
        // 可用的较低音起始范围（根据难度）
        val range = when (difficulty) {
            IntervalDifficulty.BEGINNER -> when (clef) {
                IntervalClef.TREBLE -> 0..6   // E4..D5，较高音不超过 F5
                IntervalClef.BASS -> 0..6     // G2..F3
            }
            IntervalDifficulty.INTERMEDIATE -> when (clef) {
                IntervalClef.TREBLE -> -1..6  // 含一条下加线
                IntervalClef.BASS -> -1..6
            }
            IntervalDifficulty.ADVANCED -> when (clef) {
                IntervalClef.TREBLE -> -2..7  // 含两条加线
                IntervalClef.BASS -> -2..7
            }
        }

        // 确保较高音不超出合理范围
        val validStarts = (range.first..range.last).filter { start ->
            start + span <= range.last + span.coerceAtMost(2)
        }

        val start = if (validStarts.isNotEmpty()) {
            validStarts.random(root)
        } else {
            range.first
        }

        return Pair(start, start + span)
    }

    /**
     * 构建 4 个选项（正确答案 + 3 个干扰项）。
     *
     * 干扰项策略：
     * - 不含性质时：随机选取其他度数
     * - 含性质时：同度数不同性质 + 其他度数
     */
    private fun buildChoices(
        correctInterval: Interval,
        correctNumber: IntervalNumber,
        requiresQuality: Boolean,
        difficulty: IntervalDifficulty
    ): List<String> {
        if (!requiresQuality) {
            // 初级：选项为度数名称
            val allNumbers = when (difficulty) {
                IntervalDifficulty.BEGINNER -> listOf(
                    IntervalNumber.SECOND, IntervalNumber.THIRD,
                    IntervalNumber.FOURTH, IntervalNumber.FIFTH
                )
                else -> listOf(
                    IntervalNumber.SECOND, IntervalNumber.THIRD,
                    IntervalNumber.FOURTH, IntervalNumber.FIFTH,
                    IntervalNumber.SIXTH, IntervalNumber.SEVENTH,
                    IntervalNumber.OCTAVE
                )
            }
            val correctStr = correctNumber.displayName
            val distractors = allNumbers
                .filter { it != correctNumber }
                .map { it.displayName }
                .distinct()
                .shuffled(root)
                .take(3)
                .toMutableList()

            // 如果干扰项不足 3 个，从所有度数中补充
            val pool = IntervalNumber.entries
                .filter { it != IntervalNumber.UNISON && it != correctNumber }
                .map { it.displayName }
            while (distractors.size < 3 && pool.isNotEmpty()) {
                val candidate = pool.random(root)
                if (candidate !in distractors && candidate != correctStr) {
                    distractors.add(candidate)
                }
            }

            return (distractors + correctStr).shuffled(root)
        } else {
            // 中级/高级：选项为完整音程名称
            val correctStr = correctInterval.displayName
            val distractors = mutableListOf<String>()

            // 策略 1：同度数不同性质
            val sameNumberQualities = IntervalQuality.entries
                .filter { q ->
                    val candidate = Interval(correctNumber, q).displayName
                    candidate != correctStr && Interval.classify(correctNumber, semitonesForQuality(correctNumber, q)) != null
                }
                .map { Interval(correctNumber, it).displayName }
                .filter { it != correctStr }
                .shuffled(root)

            distractors.addAll(sameNumberQualities.take(2))

            // 策略 2：相近度数
            val nearbyNumbers = listOfNotNull(
                IntervalNumber.fromDiatonicSteps(correctNumber.diatonicSteps - 1),
                IntervalNumber.fromDiatonicSteps(correctNumber.diatonicSteps + 1)
            ).filter { it != IntervalNumber.UNISON }

            for (num in nearbyNumbers.shuffled(root)) {
                if (distractors.size >= 3) break
                // 取该度数的一个常见性质
                val commonQuality = if (num.isPerfect) IntervalQuality.PERFECT else IntervalQuality.MAJOR
                val candidate = Interval(num, commonQuality).displayName
                if (candidate !in distractors && candidate != correctStr) {
                    distractors.add(candidate)
                }
            }

            // 策略 3：如果仍不足，从预设池补充
            val fallbackPool = listOf(
                "大二度", "小二度", "大三度", "小三度",
                "纯四度", "增四度", "纯五度", "减五度",
                "大六度", "小六度", "大七度", "小七度", "纯八度"
            ).filter { it != correctStr && it !in distractors }
            while (distractors.size < 3 && fallbackPool.isNotEmpty()) {
                val candidate = fallbackPool.random(root)
                if (candidate !in distractors) {
                    distractors.add(candidate)
                }
            }

            return (distractors.take(3) + correctStr).shuffled(root)
        }
    }

    /**
     * 估算给定度数+性质对应的半音数（用于验证候选干扰项的有效性）。
     */
    private fun semitonesForQuality(number: IntervalNumber, quality: IntervalQuality): Int {
        return when (Pair(number, quality)) {
            Pair(IntervalNumber.UNISON, IntervalQuality.PERFECT) -> 0
            Pair(IntervalNumber.SECOND, IntervalQuality.MINOR) -> 1
            Pair(IntervalNumber.SECOND, IntervalQuality.MAJOR) -> 2
            Pair(IntervalNumber.THIRD, IntervalQuality.MINOR) -> 3
            Pair(IntervalNumber.THIRD, IntervalQuality.MAJOR) -> 4
            Pair(IntervalNumber.FOURTH, IntervalQuality.PERFECT) -> 5
            Pair(IntervalNumber.FOURTH, IntervalQuality.AUGMENTED) -> 6
            Pair(IntervalNumber.FIFTH, IntervalQuality.DIMINISHED) -> 6
            Pair(IntervalNumber.FIFTH, IntervalQuality.PERFECT) -> 7
            Pair(IntervalNumber.SIXTH, IntervalQuality.MINOR) -> 8
            Pair(IntervalNumber.SIXTH, IntervalQuality.MAJOR) -> 9
            Pair(IntervalNumber.SEVENTH, IntervalQuality.MINOR) -> 10
            Pair(IntervalNumber.SEVENTH, IntervalQuality.MAJOR) -> 11
            Pair(IntervalNumber.OCTAVE, IntervalQuality.PERFECT) -> 12
            else -> -1
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
        fun withSeed(seed: Long): IntervalEngine = IntervalEngine(Random(seed))
    }
}
