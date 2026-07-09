package com.pianocompanion.inversiontraining

import kotlin.random.Random

/**
 * 和弦转位听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [InversionDifficulty] 生成 [InversionQuestion]：
 * 1. 从该难度的可用和弦性质集合中随机选择一个性质
 * 2. 从该难度可用的转位类型集合中随机选择一个转位
 * 3. 随机选择一个根音（决定和弦的实际音高）
 * 4. 根据性质音程和转位构建 MIDI 音符列表（从低到高排列）
 * 5. 选项 = 该难度下所有可用转位类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class InversionTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: InversionDifficulty): InversionQuestion {
        val availableQualities = ChordQuality.forDifficulty(difficulty)
        val availableInversions = InversionType.forDifficulty(difficulty)

        val quality = availableQualities.random(root)
        val inversion = availableInversions.random(root)

        // 随机选择根音，限定在 C3-G3 范围（保证转位后仍在钢琴音域内）
        val rootPc = ROOT_PITCH_CLASSES.random(root)
        val rootMidi = BASE_OCTAVE_MIDI + rootPc
        val rootName = ROOT_NAMES[rootPc]

        // 构建和弦 MIDI 音符列表（按转位排列，从低到高）
        val midiNotes = buildChordMidiNotes(quality, inversion, rootMidi)

        // 选项 = 该难度所有可用转位类型名（已打乱）
        val choices = availableInversions
            .map { it.displayName }
            .shuffled(root)

        return InversionQuestion(
            quality = quality,
            inversion = inversion,
            rootMidi = rootMidi,
            rootName = rootName,
            difficulty = difficulty,
            midiNotes = midiNotes,
            answerChoices = choices,
            correctAnswer = inversion.displayName
        )
    }

    companion object {
        /** C3 的 MIDI 编号（基准八度）。 */
        const val BASE_OCTAVE_MIDI = 48

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 可用根音音级类（C, D, E, F, G）。 */
        private val ROOT_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)

        /** 根音名（按音级类索引，共 12 个半音位置）。 */
        private val ROOT_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G")

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): InversionTrainingEngine = InversionTrainingEngine(Random(seed))

        /**
         * 根据和弦性质和转位构建 MIDI 音符列表。
         *
         * 三和弦音程 [0, i1, i2]（i1 = 三音偏移, i2 = 五音偏移）。
         *
         * - **原位**：[root, root+i1, root+i2]——根音在最低处
         * - **第一转位**：[root+i1, root+i2, root+12]——三音在最低处，根音上移八度
         * - **第二转位**：[root+i2, root+12, root+i1+12]——五音在最低处，根音和三音上移八度
         *
         * 结果始终按音高从低到高排列（MIDI 值递增）。
         *
         * @param quality 和弦性质
         * @param inversion 转位类型
         * @param rootMidi 根音 MIDI 音符号
         * @return 和弦 MIDI 音符号列表（已钳制到钢琴范围 [21, 108]，从低到高排列）
         */
        fun buildChordMidiNotes(
            quality: ChordQuality,
            inversion: InversionType,
            rootMidi: Int
        ): List<Int> {
            val i0 = quality.intervals[0] // 始终 0
            val i1 = quality.intervals[1] // 三音偏移
            val i2 = quality.intervals[2] // 五音偏移

            val notes = when (inversion) {
                InversionType.ROOT_POSITION ->
                    listOf(rootMidi + i0, rootMidi + i1, rootMidi + i2)
                InversionType.FIRST_INVERSION ->
                    listOf(rootMidi + i1, rootMidi + i2, rootMidi + 12)
                InversionType.SECOND_INVERSION ->
                    listOf(rootMidi + i2, rootMidi + 12, rootMidi + i1 + 12)
            }

            // 钳制到钢琴范围并确保从低到高排列
            return notes.map { it.coerceIn(MIN_MIDI, MAX_MIDI) }.sorted()
        }
    }
}
