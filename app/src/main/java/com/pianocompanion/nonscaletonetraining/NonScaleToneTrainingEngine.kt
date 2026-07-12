package com.pianocompanion.nonscaletonetraining

import kotlin.random.Random

/**
 * 调外音听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [NonScaleToneDifficulty] 生成 [NonScaleToneQuestion]：
 * 1. 随机选择一个调性（C/G/F/D 大调）——防止用户靠绝对音高记忆答案
 * 2. 从该难度可用的调外音类型集合中随机选择一个类型
 * 3. 以主音为起点，按自然大调音阶第 1~5 级构建上行旋律短句，
 *    并在指定音级上应用半音偏移
 * 4. 选项 = 该难度可用类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class NonScaleToneTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: NonScaleToneDifficulty): NonScaleToneQuestion {
        // 随机选择调性
        val key = NstMusicalKey.ALL.random(root)

        // 从该难度可用的类型集合中随机选择一个类型
        val availableTypes = NonScaleToneDifficulty.typesForDifficulty(difficulty)
        val type = availableTypes.random(root)

        // 构建旋律短句 MIDI 音符
        val midiNotes = buildPhraseMidiNotes(type, key.tonicMidi)

        // 选项 = 该难度可用类型的显示名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(root)

        return NonScaleToneQuestion(
            type = type,
            key = key,
            difficulty = difficulty,
            tonicMidi = key.tonicMidi,
            midiNotes = midiNotes,
            answerChoices = choices,
            correctAnswer = type.displayName
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): NonScaleToneTrainingEngine =
            NonScaleToneTrainingEngine(Random(seed))

        /**
         * 根据调外音类型和主音构建上行旋律短句（5 个音：do re mi fa sol）。
         *
         * 在自然大调音阶第 1~5 级偏移量的基础上，对被变化音的音级叠加半音偏移。
         * 其余音级保持调内不变。
         *
         * @param type 调外音类型
         * @param tonicMidi 调性主音的 MIDI 音符号
         * @return 旋律短句的 MIDI 音符号列表（5 个音，按上行旋律顺序，已钳制到钢琴范围）
         */
        fun buildPhraseMidiNotes(type: NonScaleToneType, tonicMidi: Int): List<Int> {
            return DIATONIC_DEGREE_OFFSETS.mapIndexed { index, offset ->
                val degree = index + 1 // 音级 1~5
                val deviation = if (type.alteredDegree == degree) type.semitoneDeviation else 0
                (tonicMidi + offset + deviation).coerceIn(NST_MIN_MIDI, NST_MAX_MIDI)
            }
        }
    }
}
