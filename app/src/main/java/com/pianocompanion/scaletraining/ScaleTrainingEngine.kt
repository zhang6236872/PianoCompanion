package com.pianocompanion.scaletraining

import kotlin.random.Random

/**
 * 音阶听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ScaleDifficulty] 生成 [ScaleQuestion]：
 * 1. 从该难度的可用音阶类型集合中随机选择一个类型
 * 2. 随机选择一个调性（主音）
 * 3. 随机选择播放方向（上行/下行）
 * 4. 根据音阶类型的音程模式和主音构建 MIDI 音符列表
 * 5. 选项 = 该难度下所有可用音阶类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class ScaleTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ScaleDifficulty): ScaleQuestion {
        val availableTypes = ScaleType.forDifficulty(difficulty)
        val type = availableTypes.random(root)

        // 随机选择调性（主音），限定在 C3-G3 范围
        val tonicPc = TONIC_PITCH_CLASSES.random(root)
        val tonicMidi = BASE_OCTAVE_MIDI + tonicPc
        val tonicName = TONIC_NAMES[tonicPc]

        // 随机选择播放方向
        val direction = if (root.nextBoolean()) ScaleDirection.ASCENDING else ScaleDirection.DESCENDING

        // 构建音阶 MIDI 音符列表
        val midiNotes = buildScaleMidiNotes(type, tonicMidi, direction)

        // 选项 = 该难度所有可用音阶类型名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(root)

        return ScaleQuestion(
            type = type,
            tonicMidi = tonicMidi,
            tonicName = tonicName,
            difficulty = difficulty,
            direction = direction,
            midiNotes = midiNotes,
            answerChoices = choices,
            correctAnswer = type.displayName
        )
    }

    companion object {
        /** C3 的 MIDI 编号（基准八度）。 */
        const val BASE_OCTAVE_MIDI = 48

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 可用主音音级类（C, D, E, F, G）。 */
        private val TONIC_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)

        /** 主音名（按音级类索引）。 */
        private val TONIC_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G")

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ScaleTrainingEngine = ScaleTrainingEngine(Random(seed))

        /**
         * 根据音阶类型和主音构建 MIDI 音符列表。
         *
         * @param type 音阶类型
         * @param tonicMidi 主音 MIDI 音符号
         * @param direction 播放方向（上行 = 从主音到八度，下行 = 从八度到主音）
         * @return 音阶 MIDI 音符号列表（已钳制到钢琴范围 [21, 108]）
         */
        fun buildScaleMidiNotes(
            type: ScaleType,
            tonicMidi: Int,
            direction: ScaleDirection
        ): List<Int> {
            val ascending = type.intervals.map { (tonicMidi + it).coerceIn(MIN_MIDI, MAX_MIDI) }
            return if (direction == ScaleDirection.DESCENDING) ascending.reversed() else ascending
        }
    }
}
