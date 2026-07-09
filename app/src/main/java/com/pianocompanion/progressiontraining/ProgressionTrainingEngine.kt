package com.pianocompanion.progressiontraining

import kotlin.random.Random

/**
 * 和弦进行听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ProgressionDifficulty] 生成 [ProgressionQuestion]：
 * 1. 从该难度的可用进行类型集合中随机选择一个进行
 * 2. 随机选择一个主音（决定调性）
 * 3. 根据主音 + 各音级的半音偏移构建每个和弦的 MIDI 音符列表
 * 4. 选项 = 该难度下所有可用进行类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class ProgressionTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ProgressionDifficulty): ProgressionQuestion {
        val availableProgressions = ProgressionType.forDifficulty(difficulty)

        val type = availableProgressions.random(root)

        // 随机选择主音，限定在 C3-G3 范围（保证和弦仍在合理音域内）
        val tonicPc = TONIC_PITCH_CLASSES.random(root)
        val tonicMidi = BASE_OCTAVE_MIDI + tonicPc
        val tonicName = TONIC_NAMES[tonicPc]

        // 构建和弦进行的 MIDI 音符列表
        val chordProgression = buildProgressionMidiNotes(type, tonicMidi)

        // 选项 = 该难度所有可用进行类型名（已打乱）
        val choices = availableProgressions
            .map { it.displayName }
            .shuffled(root)

        return ProgressionQuestion(
            type = type,
            tonicMidi = tonicMidi,
            tonicName = tonicName,
            difficulty = difficulty,
            chordProgression = chordProgression,
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

        /** 主音名（按音级类索引，共 8 个位置对应 C-G）。 */
        private val TONIC_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G")

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ProgressionTrainingEngine = ProgressionTrainingEngine(Random(seed))

        /**
         * 根据进行类型和主音构建各和弦的 MIDI 音符列表。
         *
         * 对于进行中的每个音级 [DiatonicDegree]：
         * - 和弦根音 = tonicMidi + degree.semitoneFromTonic
         * - 三和弦 = 根音 + degree.chordIntervals()
         * - 所有音钳制到钢琴范围 [21, 108] 并按音高排序
         *
         * @param type 进行类型
         * @param tonicMidi 主音 MIDI 音符号
         * @return 各和弦的 MIDI 音符号列表（每个和弦 3 个音，从低到高排列）
         */
        fun buildProgressionMidiNotes(
            type: ProgressionType,
            tonicMidi: Int
        ): List<List<Int>> {
            return type.degrees.map { degree ->
                val chordRoot = tonicMidi + degree.semitoneFromTonic
                val intervals = degree.chordIntervals()
                intervals
                    .map { chordRoot + it }
                    .map { it.coerceIn(MIN_MIDI, MAX_MIDI) }
                    .sorted()
            }
        }
    }
}
