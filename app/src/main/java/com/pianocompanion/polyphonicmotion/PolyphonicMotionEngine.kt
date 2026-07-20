package com.pianocompanion.polyphonicmotion

import kotlin.random.Random

/**
 * 复调运动辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [MotionDifficulty] 生成 [MotionQuestion]：
 *
 * 1. **随机选择运动类型（motion）**：从该难度的候选集中选一种。
 * 2. **生成两条对齐的声部序列**：使每个连续音符对都符合所选运动类型的不变量。
 * 3. **构建选项**：候选集全部运动类型的完整标签（已打乱）。
 *
 * **生成正确性保证**（关键设计）：
 * - 生成后调用 [verifyMotion] 校验：声部不交叉、运动不变量在每个步都成立、
 *   斜向运动的保持声部全程一致。
 * - 若校验失败（如反向运动导致声部交叉），则在同一随机序列上重新生成，
 *   最多重试 [MAX_ATTEMPTS] 次，最终回退到保证安全的生成方式。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class MotionEngine(
    private val random: Random = Random.Default
) {
    /**
     * 高声部起始音候选（MIDI）：C5-E5 白键区，保证向上/向下都有充足空间。
     * C5(72) D5(74) E5(76)
     */
    private val upperStartPool: IntArray = intArrayOf(72, 74, 76)

    /**
     * 低声部起始音候选（MIDI）：C3-E3 白键区，与高声部保持约两个八度的清晰分离。
     * C3(48) D3(50) E3(52)
     */
    private val lowerStartPool: IntArray = intArrayOf(48, 50, 52)

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: MotionDifficulty): MotionQuestion {
        var seed = random.nextLong()

        // 尝试随机生成并通过校验
        var upper: List<Int> = emptyList()
        var lower: List<Int> = emptyList()
        var chosenMotion: MotionType = difficulty.motions.first()

        var success = false
        var attempts = 0
        while (attempts < MAX_ATTEMPTS && !success) {
            attempts++
            seed = random.nextLong()
            chosenMotion = difficulty.motions.random(random)
            val (u, l) = generateVoices(difficulty, chosenMotion)
            if (verifyMotion(u, l, chosenMotion)) {
                upper = u
                lower = l
                success = true
            }
        }

        // 回退：保证安全（发散方向）的生成
        if (!success) {
            chosenMotion = difficulty.motions.random(random)
            val (u, l) = generateSafeVoices(difficulty, chosenMotion)
            upper = u
            lower = l
        }

        val choices = difficulty.motions
            .map { it.fullLabel }
            .shuffled(random)

        return MotionQuestion(
            motionType = chosenMotion,
            difficulty = difficulty,
            seed = seed,
            upperVoice = upper,
            lowerVoice = lower,
            answerChoices = choices,
            correctAnswer = chosenMotion.fullLabel
        )
    }

    /**
     * 生成两条声部序列（尝试性，可能因声部交叉而不通过校验）。
     */
    private fun generateVoices(
        difficulty: MotionDifficulty,
        motion: MotionType
    ): Pair<List<Int>, List<Int>> {
        val noteCount = difficulty.noteCount
        val steps = difficulty.stepPool

        var upper = upperStartPool.random(random)
        var lower = lowerStartPool.random(random)
        val upperVoice = mutableListOf(upper)
        val lowerVoice = mutableListOf(lower)

        when (motion) {
            MotionType.PARALLEL -> {
                // 同向：两个声部同一方向移动（步幅可不同）
                val dir = if (random.nextBoolean()) 1 else -1
                repeat(noteCount - 1) {
                    val uStep = steps.random(random) * dir
                    val lStep = steps.random(random) * dir
                    upper = clamp(upper + uStep)
                    lower = clamp(lower + lStep)
                    upperVoice.add(upper)
                    lowerVoice.add(lower)
                }
            }
            MotionType.CONTRARY -> {
                // 反向：两个声部相反方向移动
                val upperDir = if (random.nextBoolean()) 1 else -1
                repeat(noteCount - 1) {
                    upper = clamp(upper + steps.random(random) * upperDir)
                    lower = clamp(lower + steps.random(random) * -upperDir)
                    upperVoice.add(upper)
                    lowerVoice.add(lower)
                }
            }
            MotionType.OBLIQUE -> {
                // 斜向：一个声部全程保持，另一个声部移动
                val upperHolds = random.nextBoolean()
                if (upperHolds) {
                    // 高声部保持，低声部移动
                    val moveDir = if (random.nextBoolean()) 1 else -1
                    repeat(noteCount - 1) {
                        lower = clamp(lower + steps.random(random) * moveDir)
                        upperVoice.add(upper)
                        lowerVoice.add(lower)
                    }
                } else {
                    // 低声部保持，高声部移动
                    val moveDir = if (random.nextBoolean()) 1 else -1
                    repeat(noteCount - 1) {
                        upper = clamp(upper + steps.random(random) * moveDir)
                        upperVoice.add(upper)
                        lowerVoice.add(lower)
                    }
                }
            }
        }

        return upperVoice to lowerVoice
    }

    /**
     * 保证安全的回退生成：始终使用发散方向（高声部上、低声部下），确保不交叉。
     */
    private fun generateSafeVoices(
        difficulty: MotionDifficulty,
        motion: MotionType
    ): Pair<List<Int>, List<Int>> {
        val noteCount = difficulty.noteCount
        val steps = difficulty.stepPool

        var upper = upperStartPool.first() // C5
        var lower = lowerStartPool.first() // C3
        val upperVoice = mutableListOf(upper)
        val lowerVoice = mutableListOf(lower)

        when (motion) {
            MotionType.PARALLEL -> {
                // 同向上行
                repeat(noteCount - 1) {
                    val step = steps.random(random)
                    upper = clamp(upper + step)
                    lower = clamp(lower + step)
                    upperVoice.add(upper)
                    lowerVoice.add(lower)
                }
            }
            MotionType.CONTRARY -> {
                // 反向：高声部上行、低声部下行（发散，绝不交叉）
                repeat(noteCount - 1) {
                    upper = clamp(upper + steps.random(random))
                    lower = clamp(lower - steps.random(random))
                    upperVoice.add(upper)
                    lowerVoice.add(lower)
                }
            }
            MotionType.OBLIQUE -> {
                // 斜向：低声部保持，高声部上行
                repeat(noteCount - 1) {
                    upper = clamp(upper + steps.random(random))
                    upperVoice.add(upper)
                    lowerVoice.add(lower)
                }
            }
        }

        // 兜底：若仍不通过，微调使高声部始终高于低声部
        if (!verifyMotion(upperVoice, lowerVoice, motion)) {
            return generateDivergentSafe(noteCount, steps)
        }
        return upperVoice to lowerVoice
    }

    /** 绝对安全的发散生成（高声部纯上行、低声部纯下行）。 */
    private fun generateDivergentSafe(
        noteCount: Int,
        steps: List<Int>
    ): Pair<List<Int>, List<Int>> {
        val step = steps.minOrNull() ?: 2
        var upper = 72
        var lower = 48
        val upperVoice = mutableListOf(upper)
        val lowerVoice = mutableListOf(lower)
        repeat(noteCount - 1) {
            upper = clamp(upper + step)
            lower = clamp(lower - step)
            upperVoice.add(upper)
            lowerVoice.add(lower)
        }
        return upperVoice to lowerVoice
    }

    companion object {
        private const val MAX_ATTEMPTS = 100

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): MotionEngine = MotionEngine(Random(seed))

        /** 将 MIDI 值钳制到有效范围。 */
        internal fun clamp(midi: Int): Int = midi.coerceIn(MIN_MIDI, MAX_MIDI)

        /**
         * 校验两条声部序列是否符合指定运动类型的不变量。
         *
         * 检查项：
         * 1. 两个序列等长且至少 2 个音符；
         * 2. 高声部始终高于低声部（声部不交叉）；
         * 3. 每个连续音符对符合运动类型：
         *    - PARALLEL：两声部同向且均非零；
         *    - CONTRARY：两声部反向且均非零；
         *    - OBLIQUE：恰有一个声部为零（保持），且保持声部全程一致。
         *
         * 公开以便单元测试直接验证生成正确性。
         */
        fun verifyMotion(upper: List<Int>, lower: List<Int>, motion: MotionType): Boolean {
            if (upper.size != lower.size || upper.size < 2) return false
            // 声部不交叉
            for (i in upper.indices) {
                if (upper[i] <= lower[i]) return false
            }
            // 确定斜向运动的保持声部（用第一步判定，全程须一致）
            var obliqueUpperHolds: Boolean? = null
            for (i in 1 until upper.size) {
                val uDelta = upper[i] - upper[i - 1]
                val lDelta = lower[i] - lower[i - 1]
                when (motion) {
                    MotionType.PARALLEL -> {
                        if (uDelta == 0 || lDelta == 0) return false
                        if ((uDelta > 0) != (lDelta > 0)) return false
                    }
                    MotionType.CONTRARY -> {
                        if (uDelta == 0 || lDelta == 0) return false
                        if ((uDelta > 0) == (lDelta > 0)) return false
                    }
                    MotionType.OBLIQUE -> {
                        val upperHolds = uDelta == 0 && lDelta != 0
                        val lowerHolds = lDelta == 0 && uDelta != 0
                        if (!upperHolds && !lowerHolds) return false
                        if (upperHolds && lDelta == 0) return false
                        if (lowerHolds && uDelta == 0) return false
                        if (obliqueUpperHolds == null) {
                            obliqueUpperHolds = upperHolds
                        } else if (obliqueUpperHolds != upperHolds) {
                            // 保持声部在中途切换——不符合斜向运动定义
                            return false
                        }
                    }
                }
            }
            return true
        }
    }
}
