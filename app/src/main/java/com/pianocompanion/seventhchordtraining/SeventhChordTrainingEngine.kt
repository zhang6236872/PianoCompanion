package com.pianocompanion.seventhchordtraining

import kotlin.random.Random

/**
 * 七和弦品质听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [SeventhChordDifficulty] 生成 [SeventhChordQuestion]：
 * 1. 从该难度的可用七和弦品质集合中随机选择一个品质
 * 2. 随机选择一个根音（决定和弦的实际音高）
 * 3. 根据品质音程构建 MIDI 音符列表（从低到高排列）
 * 4. 选项 = 该难度下所有可用品质的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class SeventhChordTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: SeventhChordDifficulty): SeventhChordQuestion {
        val availableQualities = SeventhChordQuality.forDifficulty(difficulty)

        val quality = availableQualities.random(root)

        // 随机选择根音，限定在 C3-F3 范围（保证 4 音七和弦在钢琴音域内）
        val rootPc = ROOT_PITCH_CLASSES.random(root)
        val rootMidi = BASE_OCTAVE_MIDI + rootPc
        val rootName = ROOT_NAMES[rootPc] ?: "C"

        // 构建七和弦 MIDI 音符列表（从低到高排列）
        val midiNotes = buildSeventhChordMidiNotes(quality, rootMidi)

        // 选项 = 该难度所有可用品质名（已打乱）
        val choices = availableQualities
            .map { it.displayName }
            .shuffled(root)

        return SeventhChordQuestion(
            quality = quality,
            rootMidi = rootMidi,
            rootName = rootName,
            difficulty = difficulty,
            midiNotes = midiNotes,
            answerChoices = choices,
            correctAnswer = quality.displayName
        )
    }

    companion object {
        /** C3 的 MIDI 编号（基准八度）。 */
        const val BASE_OCTAVE_MIDI = 48

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 可用根音音级类（C, D, E, F, G），限定最高到 G 保证七音在音域内。 */
        private val ROOT_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)

        /** 根音名（按音级类索引）。 */
        private val ROOT_NAMES = mapOf(
            0 to "C", 2 to "D", 4 to "E", 5 to "F", 7 to "G"
        )

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): SeventhChordTrainingEngine =
            SeventhChordTrainingEngine(Random(seed))

        /**
         * 根据七和弦品质构建 MIDI 音符列表。
         *
         * 七和弦音程 [0, i1, i2, i3]（根音、三音、五音、七音偏移）。
         * 根音在最低处，所有音符按半音偏移叠加。
         *
         * 结果始终按音高从低到高排列（MIDI 值递增）。
         *
         * @param quality 七和弦品质
         * @param rootMidi 根音 MIDI 音符号
         * @return 和弦 MIDI 音符号列表（已钳制到钢琴范围 [21, 108]，从低到高排列）
         */
        fun buildSeventhChordMidiNotes(
            quality: SeventhChordQuality,
            rootMidi: Int
        ): List<Int> {
            val notes = quality.intervals.map { interval ->
                rootMidi + interval
            }
            // 钳制到钢琴范围并确保从低到高排列
            return notes.map { it.coerceIn(MIN_MIDI, MAX_MIDI) }.sorted()
        }
    }
}
