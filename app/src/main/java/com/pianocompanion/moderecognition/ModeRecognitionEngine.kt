package com.pianocompanion.moderecognition

import kotlin.random.Random

/**
 * 调式识别训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ModeDifficulty] 生成 [ModeQuestion]：
 * 1. 从该难度的可用调式集合中随机选择一个调式
 * 2. 随机选择一个主音（12 个音级类）
 * 3. 根据调式的半音音程结构构建上行/下行 MIDI 音符序列
 * 4. 选项 = 该难度下所有可用调式的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class ModeRecognitionEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param playMode 播放方向（默认上行）
     * @return 生成的题目
     */
    fun generate(
        difficulty: ModeDifficulty,
        playMode: PlayMode = PlayMode.ASCENDING
    ): ModeQuestion {
        val availableModes = ModeType.forDifficulty(difficulty)
        val mode = availableModes.random(root)
        val tonic = Tonic.ALL.random(root)

        val ascendingMidi = buildAscendingMidi(tonic.pitchClass, mode)
        val descendingMidi = if (playMode == PlayMode.ASCENDING_DESCENDING) {
            buildDescendingMidi(tonic.pitchClass, mode)
        } else {
            emptyList()
        }

        // 选项 = 该难度所有可用调式名（已打乱）
        val choices = availableModes
            .map { it.displayName }
            .shuffled(root)

        return ModeQuestion(
            mode = mode,
            tonic = tonic,
            difficulty = difficulty,
            playMode = playMode,
            ascendingMidiNotes = ascendingMidi,
            descendingMidiNotes = descendingMidi,
            answerChoices = choices,
            correctAnswer = mode.displayName
        )
    }

    /**
     * 构建上行 MIDI 音符序列：主音 + 各音阶音 + 八度主音（共 8 个音）。
     *
     * 以 C4(=60) 所在八度为基准，主音 pitchClass 决定起始 MIDI。
     * 所有音符钳制在钢琴范围 [21, 108] 内。
     *
     * @param tonicPc 主音音级类
     * @param mode 调式类型
     */
    fun buildAscendingMidi(tonicPc: Int, mode: ModeType): List<Int> {
        val rootMidi = BASE_MIDI + tonicPc
        val notes = mutableListOf(rootMidi)
        for (interval in mode.intervals) {
            notes.add(rootMidi + interval)
        }
        notes.add(rootMidi + SEMITONES_PER_OCTAVE)
        return notes.map { it.coerceIn(MIN_MIDI, MAX_MIDI) }
    }

    /**
     * 构建下行 MIDI 音符序列：八度主音 + 逆序音阶音 + 主音（共 8 个音）。
     */
    fun buildDescendingMidi(tonicPc: Int, mode: ModeType): List<Int> {
        val rootMidi = BASE_MIDI + tonicPc
        val notes = mutableListOf(rootMidi + SEMITONES_PER_OCTAVE)
        for (interval in mode.intervals.reversed()) {
            notes.add(rootMidi + interval)
        }
        notes.add(rootMidi)
        return notes.map { it.coerceIn(MIN_MIDI, MAX_MIDI) }
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
        fun withSeed(seed: Long): ModeRecognitionEngine = ModeRecognitionEngine(Random(seed))
    }
}
