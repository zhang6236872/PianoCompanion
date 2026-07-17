package com.pianocompanion.ornamenttraining

import kotlin.random.Random

/**
 * 装饰音辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [OrnamentDifficulty] 生成 [OrnamentQuestion]：
 * 1. 从该难度的可用装饰音类型集合中随机选择一个类型
 * 2. 随机选择一个主音（限定 C5-G5 范围，保证上方/下方音不超出钢琴音域）
 * 3. 取该类型的音符序列
 * 4. 选项 = 该难度下所有可用装饰音类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class OrnamentTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: OrnamentDifficulty): OrnamentQuestion {
        val availableTypes = OrnamentType.forDifficulty(difficulty)
        val type = availableTypes.random(root)

        // 随机选择主音，限定 C5-G5（保证 ±2 半音辅助音在钢琴音域内）
        val pc = MAIN_PITCH_CLASSES.random(root)
        val mainMidi = BASE_OCTAVE_MIDI + pc
        val mainNoteName = "${NOTE_NAMES[pc]}5"

        val noteEvents = type.noteSequence()

        // 选项 = 该难度所有可用装饰音类型名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(root)

        return OrnamentQuestion(
            type = type,
            mainMidi = mainMidi,
            mainNoteName = mainNoteName,
            difficulty = difficulty,
            noteEvents = noteEvents,
            answerChoices = choices,
            correctAnswer = type.displayName
        )
    }

    companion object {
        /** C5 的 MIDI 编号（基准八度，确保装饰音在钢琴中高音区清晰可辨）。 */
        const val BASE_OCTAVE_MIDI = 72

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 可用主音音级类（C, D, E, F, G —— 常见且辅助音不超出范围）。 */
        private val MAIN_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)

        /** 主音名（按音级类索引）。 */
        private val NOTE_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G")

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): OrnamentTrainingEngine = OrnamentTrainingEngine(Random(seed))
    }
}
