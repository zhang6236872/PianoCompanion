package com.pianocompanion.harmonycolor

import kotlin.random.Random

/**
 * 和声色彩听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [HarmonyColorDifficulty] 生成 [HarmonyColorQuestion]：
 *
 * 1. **随机选择根音（root）**：和弦的根音在中低音区随机变化，避免每次都是同一根音，
 *    但音区不影响色彩判断（色彩由音程结构决定，与绝对音高无关）。
 * 2. **从该难度候选集中选正确色彩**，并据此构建 voicing（根音 + intervals）。
 * 3. **构建选项**：候选集全部色彩的完整标签（已打乱）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class HarmonyColorEngine(
    private val random: Random = Random.Default
) {
    /**
     * 候选根音集合（MIDI）：C3-D3-E3-F3-G3-A3-B3 白键，确保和弦音（根音 + 最多 +8 半音）
     * 落在丰满的钢琴中低音区，色彩层次分明。
     */
    private val rootPool: IntArray = intArrayOf(
        48, // C3
        50, // D3
        52, // E3
        53, // F3
        55, // G3
        57, // A3
        59  // B3
    )

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: HarmonyColorDifficulty): HarmonyColorQuestion {
        val seed = random.nextLong()

        // 随机选根音
        val rootMidi = rootPool.random(random)

        // 从候选色彩集合选正确答案
        val correctColor = difficulty.colors.random(random)

        // 构建 voicing：根音 + 该色彩的半音偏移
        val voicing = correctColor.intervals.map { (rootMidi + it).coerceIn(0, 127) }

        // 构建选项：候选集全部色彩的完整标签（已打乱）
        // 初级 2 选项 / 中级 3 选项 / 高级 4 选项
        val choices = difficulty.colors
            .map { it.fullLabel }
            .shuffled(random)

        return HarmonyColorQuestion(
            color = correctColor,
            difficulty = difficulty,
            seed = seed,
            rootMidi = rootMidi,
            voicing = voicing,
            answerChoices = choices,
            correctAnswer = correctColor.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): HarmonyColorEngine = HarmonyColorEngine(Random(seed))
    }
}
