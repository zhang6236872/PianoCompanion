package com.pianocompanion.intervalsequence

import kotlin.random.Random

/**
 * 音程序列记忆出题引擎（纯 Kotlin，无 Android 依赖）。
 *
 * 根据难度级别和随机种子生成可复现的题目：
 * 1. 从难度的可用音程集合中随机选取 [sequenceLength] 个音程；
 * 2. 每个音程随机决定上行或下行（纯一度除外——永远上行）；
 * 3. 生成目标序列；
 * 4. 从「所有可能的序列」中选取干扰项，与正确答案一起打乱。
 */
class IntervalSequenceEngine(
    private val difficulty: IntervalSequenceDifficulty,
    private val rng: Random
) {
    /**
     * 生成一道题目。
     *
     * @param seed 随机种子（用于复现）
     */
    fun generate(seed: Long): IntervalSequenceQuestion {
        val localRng = Random(seed)
        val targetEntries = (0 until difficulty.sequenceLength).map {
            val interval = difficulty.availableIntervals.random(localRng)
            val ascending = if (interval == IntervalType.UNISON) true else localRng.nextBoolean()
            IntervalEntry(interval, ascending)
        }
        val targetSequence = IntervalSequence(targetEntries)

        val correctDisplay = targetSequence.displayString
        val choices = mutableSetOf(correctDisplay)

        // 生成干扰项：随机生成不同序列直到凑够选项
        var attempts = 0
        val distractorRng = Random(seed + 999_983L) // 质数偏移
        while (choices.size < difficulty.choiceCount && attempts < MAX_DISTRACCTOR_ATTEMPTS) {
            val distractorEntries = (0 until difficulty.sequenceLength).map {
                val interval = difficulty.availableIntervals.random(distractorRng)
                val ascending = if (interval == IntervalType.UNISON) true else distractorRng.nextBoolean()
                IntervalEntry(interval, ascending)
            }
            choices.add(IntervalSequence(distractorEntries).displayString)
            attempts++
        }

        // 如果随机生成不够，强制构造不同序列
        while (choices.size < difficulty.choiceCount) {
            val baseEntries = targetEntries.mapIndexed { i, e ->
                if (i == choices.size - 1) {
                    // 替换一个音程确保不同
                    val altInterval = difficulty.availableIntervals
                        .filter { it != e.interval }
                        .random(distractorRng)
                    IntervalEntry(altInterval, e.ascending)
                } else e
            }
            choices.add(IntervalSequence(baseEntries).displayString)
        }

        val shuffledChoices = choices.shuffled(Random(seed + 1L))

        return IntervalSequenceQuestion(
            difficulty = difficulty,
            seed = seed,
            targetSequence = targetSequence,
            answerChoices = shuffledChoices,
            correctAnswer = correctDisplay
        )
    }

    companion object {
        private const val MAX_DISTRACCTOR_ATTEMPTS = 200

        /**
         * 工厂方法：使用指定种子创建确定性引擎。
         */
        fun withSeed(difficulty: IntervalSequenceDifficulty, seed: Long): IntervalSequenceEngine {
            return IntervalSequenceEngine(difficulty, Random(seed))
        }
    }
}
