package com.pianocompanion.pitchtraining

import kotlin.random.Random

/**
 * 绝对音高训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [PitchTrainingDifficulty] 生成 [PitchQuestion]：
 * 1. 从难度的音级类集合中随机选择一个正确的音级类
 * 2. 在难度的八度范围内随机选择一个包含该音级类的 MIDI 音符号
 *    （例如高级 C3-B5 范围内选 C → C3/C4/C5 之一）
 * 3. 选项 = 该难度的全部音级类集合（已打乱，含正确答案）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class PitchTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: PitchTrainingDifficulty): PitchQuestion {
        // 1. 选择正确的音级类
        val pitchClass = difficulty.pitchClasses.random(root)

        // 2. 在八度范围内寻找包含该音级类的 MIDI 音符号
        val candidateMidi = mutableListOf<Int>()
        var midi = difficulty.octaveLowest
        while (midi <= difficulty.octaveHighest) {
            if (midi % 12 == pitchClass.semitonesFromC) {
                candidateMidi.add(midi)
            }
            midi++
        }
        val midiNote = if (candidateMidi.isNotEmpty()) {
            candidateMidi.random(root)
        } else {
            // 回退：直接用八度范围内的一个对应音
            difficulty.octaveLowest + pitchClass.semitonesFromC
        }

        // 3. 选项 = 难度的全部音级类集合（打乱）
        val options = difficulty.pitchClasses.shuffled(root)

        return PitchQuestion(
            pitchClass = pitchClass,
            midiNote = midiNote,
            difficulty = difficulty,
            options = options
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): PitchTrainingEngine = PitchTrainingEngine(Random(seed))
    }
}
