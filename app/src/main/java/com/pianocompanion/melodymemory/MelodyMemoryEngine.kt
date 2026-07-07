package com.pianocompanion.melodymemory

import kotlin.random.Random

/**
 * 旋律记忆训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [MelodyDifficulty] 生成 [MelodyQuestion]：
 * 1. 随机选择一个起始音（在舒适中音区 C4-G4 范围内）
 * 2. 逐个生成音程走向（方向 + 半音数），受难度约束
 * 3. 构建 MIDI 音符序列（钳制到钢琴范围 [21, 108]）
 * 4. 计算正确走向的箭头序列
 * 5. 生成 3 个干扰走向选项（与正确走向不同），凑齐 4 个选项并打乱
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class MelodyMemoryEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param tempo 播放速度（默认慢速）
     * @return 生成的题目
     */
    fun generate(
        difficulty: MelodyDifficulty,
        tempo: MelodyTempo = MelodyTempo.SLOW
    ): MelodyQuestion {
        val noteCount = difficulty.noteCount
        val maxInterval = difficulty.maxIntervalSemitones
        val availableDirections = if (difficulty.allowSameDirection) {
            MelodicDirection.ALL
        } else {
            MelodicDirection.UP_DOWN
        }

        // 选择起始音（C4-G4 中音区，避免旋律超出钢琴范围）
        val startMidi = root.nextInt(START_MIN, START_MAX + 1)

        // 逐个生成音程走向，构建 MIDI 音符序列
        val midiNotes = mutableListOf(startMidi)
        val contour = mutableListOf<ContourInterval>()
        var currentMidi = startMidi

        for (i in 1 until noteCount) {
            val direction = availableDirections.random(root)
            val semitones = if (direction == MelodicDirection.SAME) {
                0
            } else {
                root.nextInt(1, maxInterval + 1)
            }

            val delta = when (direction) {
                MelodicDirection.UP -> semitones
                MelodicDirection.DOWN -> -semitones
                MelodicDirection.SAME -> 0
            }

            currentMidi = (currentMidi + delta).coerceIn(MIN_MIDI, MAX_MIDI)
            midiNotes.add(currentMidi)
            contour.add(ContourInterval(direction, semitones))
        }

        val correctArrows = contour.joinToString(" ") { it.arrow }

        // 生成干扰选项
        val options = generateOptions(
            correctDirections = contour.map { it.direction },
            intervalCount = noteCount - 1,
            availableDirections = availableDirections,
            correctArrows = correctArrows
        )

        return MelodyQuestion(
            difficulty = difficulty,
            tempo = tempo,
            startMidi = startMidi,
            midiNotes = midiNotes,
            contour = contour,
            answerChoices = options,
            correctAnswer = correctArrows
        )
    }

    /**
     * 生成 4 个走向选项（含正确答案，已打乱，全部互不相同）。
     *
     * @param correctDirections 正确走向的方向序列
     * @param intervalCount 音程数（= 音符数 - 1）
     * @param availableDirections 该难度可用的方向集合
     * @param correctArrows 正确走向的箭头字符串
     * @return 4 个互不相同的箭头字符串选项（已打乱）
     */
    private fun generateOptions(
        correctDirections: List<MelodicDirection>,
        intervalCount: Int,
        availableDirections: List<MelodicDirection>,
        correctArrows: String
    ): List<String> {
        val optionSet = LinkedHashSet<String>()
        optionSet.add(correctArrows)

        var attempts = 0
        while (optionSet.size < MelodyDifficulty.optionCount && attempts < MAX_OPTION_ATTEMPTS) {
            val candidate = generateDistractor(correctDirections, availableDirections)
            optionSet.add(candidate.joinToString(" ") { it.symbol })
            attempts++
        }

        // 若空间不足（如初级 2 音程 × 2 方向 = 恰好 4 种），穷举所有可能
        if (optionSet.size < MelodyDifficulty.optionCount) {
            enumerateAllContours(intervalCount, availableDirections).forEach { dirs ->
                if (optionSet.size >= MelodyDifficulty.optionCount) return@forEach
                optionSet.add(dirs.joinToString(" ") { it.symbol })
            }
        }

        return optionSet.toList().shuffled(root)
    }

    /**
     * 生成一个干扰走向：对正确走向的每个方向有 50% 概率随机替换。
     */
    private fun generateDistractor(
        correct: List<MelodicDirection>,
        available: List<MelodicDirection>
    ): List<MelodicDirection> {
        return correct.map { d ->
            if (root.nextBoolean()) available.random(root) else d
        }
    }

    /**
     * 穷举给定音程数和方向集合的所有走向组合（用于空间较小的情况）。
     */
    private fun enumerateAllContours(
        intervalCount: Int,
        directions: List<MelodicDirection>
    ): List<List<MelodicDirection>> {
        if (intervalCount == 0) return listOf(emptyList())
        val results = mutableListOf<List<MelodicDirection>>()
        fun build(current: List<MelodicDirection>) {
            if (current.size == intervalCount) {
                results.add(current)
                return
            }
            for (d in directions) {
                build(current + d)
            }
        }
        build(emptyList())
        return results
    }

    companion object {
        /** 起始音最低 C4。 */
        const val START_MIN = 60

        /** 起始音最高 G4。 */
        const val START_MAX = 67

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 生成干扰选项的最大尝试次数。 */
        private const val MAX_OPTION_ATTEMPTS = 200

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): MelodyMemoryEngine = MelodyMemoryEngine(Random(seed))
    }
}
