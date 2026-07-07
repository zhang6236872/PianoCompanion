package com.pianocompanion.chordtraining

import kotlin.random.Random

/**
 * 和弦听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ChordEarDifficulty] 生成 [ChordEarQuestion]：
 * 1. 从该难度的可用和弦类型集合中随机选择一个类型
 * 2. 随机选择一个根音（12 个音级类）
 * 3. 根据和弦类型的半音音程结构构建 MIDI 音符列表（根音八度起始）
 * 4. 选项 = 该难度下所有可用和弦类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class ChordTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param playStyle 播放方式（默认柱式）
     * @return 生成的题目
     */
    fun generate(
        difficulty: ChordEarDifficulty,
        playStyle: ChordPlayStyle = ChordPlayStyle.BLOCK
    ): ChordEarQuestion {
        val availableTypes = ChordEarType.forDifficulty(difficulty)
        val type = availableTypes.random(root)
        val chordRoot = ChordRoot.ALL.random(root)

        val midiNotes = buildMidiNotes(chordRoot.pitchClass, type)

        // 选项 = 该难度所有可用和弦类型名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(root)

        return ChordEarQuestion(
            type = type,
            root = chordRoot,
            difficulty = difficulty,
            playStyle = playStyle,
            midiNotes = midiNotes,
            answerChoices = choices,
            correctAnswer = type.displayName
        )
    }

    /**
     * 构建和弦的 MIDI 音符列表：根音 + 各音程音。
     *
     * 以 C4(=60) 所在八度为基准，根音 pitchClass 决定起始 MIDI。
     * 所有音符钳制在钢琴范围 [21, 108] 内。
     *
     * @param rootPc 根音音级类
     * @param type 和弦类型
     */
    fun buildMidiNotes(rootPc: Int, type: ChordEarType): List<Int> {
        val rootMidi = BASE_MIDI + rootPc
        return type.allIntervals
            .map { rootMidi + it }
            .map { it.coerceIn(MIN_MIDI, MAX_MIDI) }
    }

    companion object {
        /** C4 的 MIDI 编号。 */
        const val BASE_MIDI = 60

        const val SEMITONES_PER_OCTAVE = 12

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ChordTrainingEngine = ChordTrainingEngine(Random(seed))
    }
}
