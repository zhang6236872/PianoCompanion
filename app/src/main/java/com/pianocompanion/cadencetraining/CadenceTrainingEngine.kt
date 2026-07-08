package com.pianocompanion.cadencetraining

import kotlin.random.Random

/**
 * 终止式听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [CadenceDifficulty] 生成 [CadenceQuestion]：
 * 1. 从该难度的可用终止式类型集合中随机选择一个类型
 * 2. 随机选择一个调性（主音）
 * 3. 根据终止式的和弦进行定义和主音构建各和弦的 MIDI 音符列表
 * 4. 选项 = 该难度下所有可用终止式类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class CadenceTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: CadenceDifficulty): CadenceQuestion {
        val availableTypes = CadenceType.forDifficulty(difficulty)
        val type = availableTypes.random(root)

        // 随机选择调性（主音），限定在 C3-G3 范围以保持和弦在合理音域
        val tonicPc = TONIC_PITCH_CLASSES.random(root)
        val tonicMidi = BASE_OCTAVE_MIDI + tonicPc
        val tonicName = TONIC_NAMES[tonicPc]

        // 构建和弦进行的 MIDI 音符列表（每个和弦功能用其各自的音程构建）
        val chordProgression = type.progression.map { function ->
            function.buildMidiNotes(tonicMidi)
        }

        // 选项 = 该难度所有可用终止式类型名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(root)

        return CadenceQuestion(
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
        /** C3 的 MIDI 编号（基准八度，确保和弦在钢琴中音区）。 */
        const val BASE_OCTAVE_MIDI = 48

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 可用主音音级类（C, D, E, F, G —— 常见且和弦不超出范围）。 */
        private val TONIC_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)

        /** 主音名（按音级类索引）。 */
        private val TONIC_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G")

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): CadenceTrainingEngine = CadenceTrainingEngine(Random(seed))
    }
}
