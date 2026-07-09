package com.pianocompanion.keyidentificationtraining

import kotlin.random.Random

/**
 * 调性中心辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [KeyDifficulty] 生成 [KeyQuestion]：
 * 1. 从该难度的可用调性集合中随机选择一个调
 * 2. 随机选择一个旋律模式（上行音阶/五度往返/琶音/完整上下行）
 * 3. 根据 主音 + 旋律模式的音阶级数 + 调性音程结构 构建 MIDI 音符序列
 * 4. 选项 = 该难度下所有可用调性的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class KeyIdentificationTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: KeyDifficulty): KeyQuestion {
        val availableKeys = MusicKey.forDifficulty(difficulty)

        val key = availableKeys.random(root)
        val pattern = MelodyPattern.ALL.random(root)

        // 主音 MIDI = 基准八度(C4=60) + 主音音高类
        val tonicMidi = BASE_OCTAVE_MIDI + key.tonicPitchClass
        val tonicName = TONIC_NAMES[key.tonicPitchClass]

        // 构建旋律 MIDI 音符序列
        val midiNotes = buildMelodyMidiNotes(key, tonicMidi, pattern)

        // 选项 = 该难度所有可用调性名（已打乱）
        val choices = availableKeys
            .map { it.displayName }
            .shuffled(root)

        return KeyQuestion(
            key = key,
            tonicMidi = tonicMidi,
            tonicName = tonicName,
            melodyPattern = pattern,
            midiNotes = midiNotes,
            difficulty = difficulty,
            answerChoices = choices,
            correctAnswer = key.displayName
        )
    }

    companion object {
        /** C4 的 MIDI 编号（基准八度，旋律主音在此八度）。 */
        const val BASE_OCTAVE_MIDI = 60

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 主音名（按音高类 0-11 索引）。 */
        private val TONIC_NAMES = listOf(
            "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯(B♭)", "B"
        )

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): KeyIdentificationTrainingEngine =
            KeyIdentificationTrainingEngine(Random(seed))

        /**
         * 根据调性、主音和旋律模式构建 MIDI 音符序列。
         *
         * 旋律模式的 [MelodyPattern.scaleDegrees] 是音阶级数（0=主音, 1=2级, ..., 7=八度），
         * 通过调性的 [MusicKey.scaleIntervals] 映射到具体半音偏移。
         * - 大调音阶：[0, 2, 4, 5, 7, 9, 11]，八度=12
         * - 小调音阶：[0, 2, 3, 5, 7, 8, 10]，八度=12
         *
         * 所有音符钳制到钢琴范围 [21, 108]。
         *
         * @param key 调性
         * @param tonicMidi 主音 MIDI 音符号
         * @param pattern 旋律模式
         * @return 旋律 MIDI 音符号列表（按播放顺序）
         */
        fun buildMelodyMidiNotes(
            key: MusicKey,
            tonicMidi: Int,
            pattern: MelodyPattern
        ): List<Int> {
            val scale = key.scaleIntervals()
            // 扩展音阶：7 个音级 + 八度（degree 7 = 高八度主音 = +12 半音）
            val extendedScale = scale + listOf(12)
            return pattern.scaleDegrees.map { degree ->
                val semitoneOffset = extendedScale[degree]
                (tonicMidi + semitoneOffset).coerceIn(MIN_MIDI, MAX_MIDI)
            }
        }
    }
}
