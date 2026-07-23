package com.pianocompanion.rhythmmemory

import kotlin.random.Random

/**
 * 节奏型记忆训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [RhythmMemoryDifficulty] 生成 [RhythmMemoryQuestion]：
 *
 * 1. **生成目标节奏型**：从该难度的节奏单元池中随机选取 `beats` 个单元组成序列。
 * 2. **生成干扰项**：对目标节奏型做**细微变异**（交换相邻拍 / 替换某拍的单元 /
 *    多拍替换），生成（choiceCount - 1）个与目标及彼此都不同的干扰节奏型。
 *    变异保持「相似度」——干扰项与正确答案仅差一两个拍，迫使精确记忆而非粗略猜测。
 * 3. **构建选项**：正确答案 + 干扰项的显示串，打乱顺序。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class RhythmMemoryEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: RhythmMemoryDifficulty): RhythmMemoryQuestion {
        val seed = random.nextLong()

        // 1. 生成目标节奏型
        val target = generatePattern(difficulty)

        // 2. 生成干扰节奏型
        val distractors = generateDistractors(target, difficulty)

        // 3. 构建选项（正确 + 干扰项），打乱
        val allPatterns = listOf(target) + distractors
        val choices = allPatterns.map { it.displayString }.shuffled(random)

        return RhythmMemoryQuestion(
            difficulty = difficulty,
            seed = seed,
            targetPattern = target,
            answerChoices = choices,
            correctAnswer = target.displayString
        )
    }

    /** 从难度的节奏单元池中随机生成一条 `beats` 拍的节奏型。 */
    private fun generatePattern(difficulty: RhythmMemoryDifficulty): RhythmPattern {
        val cells = (0 until difficulty.beats).map { difficulty.cellPool.random(random) }
        return RhythmPattern(cells)
    }

    /**
     * 生成与目标及彼此都不同的干扰节奏型。
     *
     * 通过反复变异直到收集到足够数量的唯一节奏型。变异策略保持「相似度」：
     * 交换相邻拍、替换单拍单元、或多拍替换。
     */
    private fun generateDistractors(
        target: RhythmPattern,
        difficulty: RhythmMemoryDifficulty
    ): List<RhythmPattern> {
        val pool = difficulty.cellPool
        val needed = difficulty.choiceCount - 1
        val result = mutableListOf<RhythmPattern>()
        val seen = mutableSetOf(target.displayString)
        var attempts = 0
        val maxAttempts = 500

        while (result.size < needed && attempts < maxAttempts) {
            attempts++
            val mutant = mutate(target, pool)
            if (mutant.displayString !in seen) {
                seen.add(mutant.displayString)
                result.add(mutant)
            }
        }

        // 兜底：若变异空间不足，用池中其他单元逐拍替换补齐
        while (result.size < needed) {
            val cells = target.toMutable()
            val idx = result.size % cells.size
            val alternatives = pool.filter { it != cells[idx] }
            if (alternatives.isNotEmpty()) {
                cells[idx] = alternatives[result.size % alternatives.size]
            }
            val mutant = RhythmPattern(cells)
            if (mutant.displayString !in seen) {
                seen.add(mutant.displayString)
                result.add(mutant)
            } else {
                // 实在无法区分时停止，避免死循环
                break
            }
        }

        return result
    }

    /**
     * 对节奏型做一次细微变异。
     *
     * 策略（随机三选一）：
     * - 0 替换单拍：随机一拍换为池中不同的单元；
     * - 1 交换相邻拍：随机一对相邻拍互换（若两拍相同则无变化，由 seen 过滤重试）；
     * - 2 多拍替换：每拍有 30% 概率换为不同单元（更强的变异，用于凑足干扰项）。
     */
    private fun mutate(pattern: RhythmPattern, pool: List<RhythmCellType>): RhythmPattern {
        val cells = pattern.toMutable()
        if (cells.isEmpty()) return pattern
        when (random.nextInt(3)) {
            0 -> {
                // 替换单拍
                val idx = random.nextInt(cells.size)
                val alternatives = pool.filter { it != cells[idx] }
                if (alternatives.isNotEmpty()) {
                    cells[idx] = alternatives.random(random)
                }
            }
            1 -> {
                // 交换相邻拍
                if (cells.size >= 2) {
                    val idx = random.nextInt(cells.size - 1)
                    val tmp = cells[idx]
                    cells[idx] = cells[idx + 1]
                    cells[idx + 1] = tmp
                }
            }
            2 -> {
                // 多拍替换
                for (i in cells.indices) {
                    if (random.nextDouble() < 0.3) {
                        val alternatives = pool.filter { it != cells[i] }
                        if (alternatives.isNotEmpty()) {
                            cells[i] = alternatives.random(random)
                        }
                    }
                }
            }
        }
        return RhythmPattern(cells)
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): RhythmMemoryEngine = RhythmMemoryEngine(Random(seed))
    }
}
