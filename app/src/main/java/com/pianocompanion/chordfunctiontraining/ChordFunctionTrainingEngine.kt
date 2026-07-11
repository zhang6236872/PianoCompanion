package com.pianocompanion.chordfunctiontraining

import kotlin.random.Random

/**
 * 和弦功能听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ChordFunctionDifficulty] 生成 [ChordFunctionQuestion]：
 * 1. 随机选择一个调性（C/G/F/D 大调）——防止用户靠绝对音高记忆答案
 * 2. 从该难度可用的音级集合中随机选择一个音级
 * 3. 根据音级的音阶偏移和和弦音程构建 MIDI 音符列表
 * 4. 选项 = 三种和声功能的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class ChordFunctionTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ChordFunctionDifficulty): ChordFunctionQuestion {
        // 随机选择调性
        val key = MusicalKey.ALL.random(root)

        // 根据难度选择音级集合
        val availableDegrees = degreesForDifficulty(difficulty)
        val degree = availableDegrees.random(root)

        // 判断是否使用七和弦（仅高级难度）
        val useSeventh = difficulty == ChordFunctionDifficulty.ADVANCED

        // 构建和弦 MIDI 音符
        val midiNotes = buildChordMidiNotes(degree, key.tonicMidi, useSeventh)

        // 选项 = 三种功能的显示名（已打乱）
        val choices = HarmonicFunction.ALL
            .map { it.displayName }
            .shuffled(root)

        return ChordFunctionQuestion(
            scaleDegree = degree,
            function = degree.function,
            key = key,
            chordRootMidi = key.tonicMidi + degree.scaleOffset,
            difficulty = difficulty,
            useSeventh = useSeventh,
            midiNotes = midiNotes,
            answerChoices = choices,
            correctAnswer = degree.function.displayName
        )
    }

    companion object {
        /**
         * 根据难度返回可用的音级集合。
         * - 初级：I, IV, V（正三和弦）
         * - 中级：全部 7 个自然音三和弦
         * - 高级：全部 7 个自然音七和弦（音级相同，但使用七和弦音程）
         */
        fun degreesForDifficulty(difficulty: ChordFunctionDifficulty): List<ScaleDegree> = when (difficulty) {
            ChordFunctionDifficulty.BEGINNER -> ScaleDegree.PRIMARY_TRIADS
            ChordFunctionDifficulty.INTERMEDIATE -> ScaleDegree.ALL
            ChordFunctionDifficulty.ADVANCED -> ScaleDegree.ALL
        }

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ChordFunctionTrainingEngine =
            ChordFunctionTrainingEngine(Random(seed))

        /**
         * 根据音级和调性主音构建和弦 MIDI 音符列表。
         *
         * @param degree 音级
         * @param tonicMidi 调性主音的 MIDI 音符号
         * @param useSeventh 是否使用七和弦音程
         * @return 和弦 MIDI 音符号列表（已钳制到钢琴范围 [21, 108]，从低到高排列）
         */
        fun buildChordMidiNotes(
            degree: ScaleDegree,
            tonicMidi: Int,
            useSeventh: Boolean = false
        ): List<Int> {
            val chordRoot = tonicMidi + degree.scaleOffset
            val intervals = if (useSeventh) degree.seventhIntervals else degree.triadIntervals
            return intervals
                .map { chordRoot + it }
                .map { it.coerceIn(CF_MIN_MIDI, CF_MAX_MIDI) }
                .sorted()
        }
    }
}
