package com.pianocompanion.voiceentryorder

import kotlin.random.Random

/**
 * 声部进入顺序辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [EntryDifficulty] 生成 [EntryOrderQuestion]：
 *
 * 1. **随机选择进入顺序**：从该难度的音区集合中生成一个随机全排列作为正确答案。
 * 2. **构建干扰项**：从其余全排列中随机选取若干作为干扰项。
 * 3. **构建选项**：正确答案 + 干扰项，打乱顺序。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class VoiceEntryEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: EntryDifficulty): EntryOrderQuestion {
        val seed = random.nextLong()

        // 1. 随机选取正确的进入顺序（全排列）
        val correctOrder = difficulty.registers.shuffled(random)
        val correctLabel = orderLabel(correctOrder)

        // 2. 生成全部排列，排除正确答案，随机选取干扰项
        val allPermutations = permutations(difficulty.registers)
        val distractorPool = allPermutations.filter { it != correctOrder }
        val distractors = distractorPool.shuffled(random).take(difficulty.choiceCount - 1)

        // 3. 构建选项（正确 + 干扰项），打乱
        val allOrders = (listOf(correctOrder) + distractors)
        val choices = allOrders.map { orderLabel(it) }.shuffled(random)

        return EntryOrderQuestion(
            difficulty = difficulty,
            seed = seed,
            entryOrder = correctOrder,
            answerChoices = choices,
            correctAnswer = correctLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): VoiceEntryEngine = VoiceEntryEngine(Random(seed))

        /**
         * 生成列表的全部全排列（字典序）。
         *
         * 公开以便单元测试直接验证排列完整性。
         */
        fun <T> permutations(items: List<T>): List<List<T>> {
            if (items.isEmpty()) return listOf(emptyList())
            if (items.size == 1) return listOf(items)
            val result = mutableListOf<List<T>>()
            val remaining = items.toMutableList()
            // 使用 Heap 算法生成全排列
            heapPermute(remaining, items.size, result)
            return result.distinct()
        }

        private fun <T> heapPermute(items: MutableList<T>, n: Int, result: MutableList<List<T>>) {
            if (n == 1) {
                result.add(items.toList())
                return
            }
            for (i in 0 until n) {
                heapPermute(items, n - 1, result)
                if (n % 2 == 1) {
                    val tmp = items[0]
                    items[0] = items[n - 1]
                    items[n - 1] = tmp
                } else {
                    val tmp = items[i]
                    items[i] = items[n - 1]
                    items[n - 1] = tmp
                }
            }
        }
    }
}
