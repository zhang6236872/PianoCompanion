package com.pianocompanion.melodiccontour

import kotlin.random.Random

/**
 * 旋律轮廓辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ContourDifficulty] 生成 [ContourQuestion]：
 *
 * 1. **随机选轮廓类型**：从该难度的 [ContourDifficulty.contourOptions] 中随机一种。
 * 2. **生成音高序列**：根据轮廓类型生成匹配的 MIDI 音高序列（详见 [generatePitches]）。
 * 3. **构建选项**：按轮廓复杂度（上行/下行 → 拱形/谷形 → 波浪）排序生成显示名。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ContourEngine(
    private val random: Random = Random.Default
) {
    /** 音高允许的最小 MIDI 值（C3）。 */
    private val minPitch: Int = 48

    /** 音高允许的最大 MIDI 值（C6）。 */
    private val maxPitch: Int = 84

    /** 上行/谷形起始音的 MIDI 范围下限（C4）。 */
    private val lowStartMin: Int = 60

    /** 上行/谷形起始音的 MIDI 范围上限（G4）。 */
    private val lowStartMax: Int = 67

    /** 下行/拱形起始音的 MIDI 范围下限（C5）。 */
    private val highStartMin: Int = 72

    /** 下行/拱形起始音的 MIDI 范围上限（G5）。 */
    private val highStartMax: Int = 79

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ContourDifficulty): ContourQuestion {
        val contour = difficulty.contourOptions.random(random)
        val pitches = generatePitches(contour, difficulty)
        val choices = difficulty.contourOptions
            .sortedBy { contourComplexity(it) }
            .map { it.displayName }

        return ContourQuestion(
            difficulty = difficulty,
            contour = contour,
            pitches = pitches,
            noteDurationMs = difficulty.noteDurationMs,
            answerChoices = choices,
            correctAnswer = contour.displayName
        )
    }

    /**
     * 根据轮廓类型生成匹配的 MIDI 音高序列。
     *
     * - ASCENDING：从较低起始音开始，每步向上跳一个随机音程。
     * - DESCENDING：从较高起始音开始，每步向下跳一个随机音程。
     * - ARCH：前半段上行到中部峰值，后半段下行。
     * - VALLEY：前半段下行到中部谷值，后半段上行。
     * - WAVE：方向交替（上、下、上、下…或下、上、下、上…）。
     *
     * 生成后整体平移以确保所有音落在 [minPitch]..[maxPitch] 范围内。
     */
    internal fun generatePitches(contour: ContourType, difficulty: ContourDifficulty): List<Int> {
        val n = difficulty.noteCount
        val pool = difficulty.stepPool
        val pitches = IntArray(n)

        // 随机起始方向（用于 WAVE）
        val startDirUp = random.nextBoolean()

        when (contour) {
            ContourType.ASCENDING -> {
                pitches[0] = random.nextInt(lowStartMin, lowStartMax + 1)
                for (i in 1 until n) pitches[i] = pitches[i - 1] + pool.random(random)
            }
            ContourType.DESCENDING -> {
                pitches[0] = random.nextInt(highStartMin, highStartMax + 1)
                for (i in 1 until n) pitches[i] = pitches[i - 1] - pool.random(random)
            }
            ContourType.ARCH -> {
                // 中部峰索引
                val peakIdx = n / 2
                pitches[peakIdx] = random.nextInt(highStartMin, highStartMax + 1)
                // 峰左侧上行
                for (i in peakIdx - 1 downTo 0) pitches[i] = pitches[i + 1] - pool.random(random)
                // 峰右侧下行
                for (i in peakIdx + 1 until n) pitches[i] = pitches[i - 1] - pool.random(random)
            }
            ContourType.VALLEY -> {
                val troughIdx = n / 2
                pitches[troughIdx] = random.nextInt(lowStartMin, lowStartMax + 1)
                // 谷左侧下行（从左到谷：越来越低 → 谷最低）
                for (i in troughIdx - 1 downTo 0) pitches[i] = pitches[i + 1] + pool.random(random)
                // 谷右侧上行
                for (i in troughIdx + 1 until n) pitches[i] = pitches[i - 1] + pool.random(random)
            }
            ContourType.WAVE -> {
                pitches[0] = random.nextInt(lowStartMin, highStartMax + 1)
                var dirUp = startDirUp
                for (i in 1 until n) {
                    val step = pool.random(random)
                    pitches[i] = if (dirUp) pitches[i - 1] + step else pitches[i - 1] - step
                    dirUp = !dirUp
                }
            }
        }

        // 整体平移到合法音域。
        // 合法 shift 区间为 [minPitch - lo, maxPitch - hi]，因 span ≤ maxPitch - minPitch，
        // 该区间非空。优先 shift = 0（不平移），否则按需平移。
        val lo = pitches.min()
        val hi = pitches.max()
        var shift = 0
        if (lo + shift < minPitch) shift = minPitch - lo   // 最低音低于下限 → 上移
        if (hi + shift > maxPitch) shift = maxPitch - hi   // 最高音高于上限 → 下移

        // 安全夹取（极端边界情况）
        return pitches.map { (it + shift).coerceIn(minPitch, maxPitch) }
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ContourEngine = ContourEngine(Random(seed))

        /** 轮廓复杂度排序权重（用于选项排序：简单→复杂）。 */
        internal fun contourComplexity(c: ContourType): Int = when (c) {
            ContourType.ASCENDING -> 0
            ContourType.DESCENDING -> 1
            ContourType.ARCH -> 2
            ContourType.VALLEY -> 3
            ContourType.WAVE -> 4
        }
    }
}
