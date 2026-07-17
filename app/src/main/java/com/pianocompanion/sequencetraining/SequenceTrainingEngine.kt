package com.pianocompanion.sequencetraining

import kotlin.random.Random

/**
 * 模进辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [SequenceDifficulty] 生成 [SequenceQuestion]：
 * 1. 从该难度的可用构造类型集合中随机选择一个类型
 * 2. 随机选择一个动机模板（3 音符的相对半音偏移，首音为 0）
 * 3. 根据类型构建完整旋律（动机重复 3 次，按步距转调）：
 *    - ASCENDING：每次重复向上转调 +step
 *    - DESCENDING：每次重复向下转调 -step
 *    - REPETITION：每次重复在同一音高（step = 0）
 *    - FREE：用随机偏移生成 9 个音符，不构成模进关系
 * 4. 选项 = 该难度下所有可用类型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class SequenceTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: SequenceDifficulty): SequenceQuestion {
        val availableTypes = SequenceType.forDifficulty(difficulty)
        val type = availableTypes.random(root)

        // 起始音音级类（保证完整旋律在钢琴音域内）
        val pc = START_PITCH_CLASSES.random(root)
        val octave = START_OCTAVES.random(root)
        val startMidi = octave * 12 + pc
        val startNoteName = "${NOTE_NAMES[pc]}${octave - 1}"

        // 选择动机模板
        val motif = MOTIF_TEMPLATES.random(root)

        val noteMidiSequence: List<Int>
        val stepSemitones: Int

        when (type) {
            SequenceType.ASCENDING -> {
                val step = ASCENDING_STEPS.random(root)
                stepSemitones = step
                noteMidiSequence = buildSequence(startMidi, motif, step, STATEMENT_COUNT)
            }
            SequenceType.DESCENDING -> {
                val step = DESCENDING_STEPS.random(root)
                stepSemitones = step
                noteMidiSequence = buildSequence(startMidi, motif, step, STATEMENT_COUNT)
            }
            SequenceType.REPETITION -> {
                stepSemitones = 0
                noteMidiSequence = buildSequence(startMidi, motif, 0, STATEMENT_COUNT)
            }
            SequenceType.FREE -> {
                stepSemitones = 0
                noteMidiSequence = buildFreeMelody(startMidi, motif.size * STATEMENT_COUNT)
            }
        }

        // 钳制到钢琴音域内（保证不越界）
        val clamped = noteMidiSequence.map { it.coerceIn(MIN_MIDI, MAX_MIDI) }

        // 选项 = 该难度所有可用类型名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(root)

        return SequenceQuestion(
            type = type,
            startMidi = startMidi,
            startNoteName = startNoteName,
            motifOffsets = motif,
            stepSemitones = stepSemitones,
            statementCount = STATEMENT_COUNT,
            noteMidiSequence = clamped,
            noteDurationMs = DEFAULT_NOTE_DURATION_MS,
            difficulty = difficulty,
            answerChoices = choices,
            correctAnswer = type.displayName
        )
    }

    /**
     * 构建模进旋律：将动机重复 [statementCount] 次，每次整体转调 [step] 个半音。
     *
     * @param startMidi 第一个动机的起始 MIDI
     * @param motif 动机偏移序列（首音为 0）
     * @param step 每次重复的转调半音数（正=上行，负=下行，0=重复）
     * @param statementCount 重复次数
     * @return 完整旋律的 MIDI 序列
     */
    private fun buildSequence(
        startMidi: Int,
        motif: List<Int>,
        step: Int,
        statementCount: Int
    ): List<Int> {
        val result = mutableListOf<Int>()
        for (s in 0 until statementCount) {
            val transpose = step * s
            motif.forEach { offset ->
                result.add(startMidi + offset + transpose)
            }
        }
        return result
    }

    /**
     * 构建自由旋律：生成 [count] 个音符，使用无规律的随机音级偏移，
     * 确保不构成清晰的模进或重复关系。
     *
     * @param startMidi 起始 MIDI（仅作为音区中心参考）
     * @param count 音符总数
     * @return 自由旋律的 MIDI 序列
     */
    private fun buildFreeMelody(startMidi: Int, count: Int): List<Int> {
        val result = mutableListOf<Int>()
        // 自由旋律：使用较大的、无规律的音级跳变，避免任何重复结构
        var current = startMidi
        repeat(count) {
            // 随机步进 -5..+5 半音，但不允许长时间单调（混合方向）
            val delta = FREE_DELTAS.random(root)
            current = (current + delta).coerceIn(startMidi - 7, startMidi + 7)
            result.add(current)
        }
        return result
    }

    companion object {
        /** 动机重复次数。 */
        const val STATEMENT_COUNT = 3

        /** 每个音符的默认时长（毫秒）。 */
        const val DEFAULT_NOTE_DURATION_MS = 320L

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /**
         * 动机模板（3 音符，首音偏移为 0）。
         * 涵盖级进、跳进、回音等多种可识别的旋律形状。
         */
        val MOTIF_TEMPLATES: List<List<Int>> = listOf(
            listOf(0, 2, 4),    // 级进上行 do-re-mi
            listOf(0, 4, 7),    // 大三和弦琶音 do-mi-sol
            listOf(0, 2, 0),    // 上方辅助音 do-re-do
            listOf(0, -2, 0),   // 下方辅助音 do-ti(do 低) -do
            listOf(0, 4, 2),    // 上跳下级 do-mi-re
            listOf(0, 5, 7),    // 大跳 + 级进 do-fa-sol
            listOf(0, -2, -4),  // 级进下行
            listOf(0, 7, 5),    // 大跳下行级进
            listOf(0, 3, 5),    // 小三度级进 do-mib-sol
            listOf(0, 2, -1)    // 上级后大回
        )

        /** 上行模进的步距候选（半音数）：2/3/4/5（二度到四度）。 */
        val ASCENDING_STEPS: List<Int> = listOf(2, 3, 4, 5)

        /** 下行模进的步距候选（半音数，负值）：-2/-3/-4/-5。 */
        val DESCENDING_STEPS: List<Int> = listOf(-2, -3, -4, -5)

        /** 自由旋律的随机步进候选（半音数）。混合大小跳，避免规律。 */
        val FREE_DELTAS: List<Int> = listOf(-5, -4, -2, 1, 3, 5, -3, 2, 4, -1, -4, 3, 5, -2, 1)

        /** 可用起始音音级类（C, D, E, F, G —— 常见且保证模进音域不超界）。 */
        private val START_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)

        /** 起始八度的 MIDI 基准（C4=60, C5=72）。用 [12 * octave + pc] 计算 MIDI。 */
        private val START_OCTAVES = listOf(5, 6) // C5=60 区域 / C6=72 区域

        /** 音名（按音级类索引 0-11）。 */
        private val NOTE_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): SequenceTrainingEngine = SequenceTrainingEngine(Random(seed))
    }
}
