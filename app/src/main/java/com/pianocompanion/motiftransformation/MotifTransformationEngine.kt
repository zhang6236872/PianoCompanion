package com.pianocompanion.motiftransformation

import kotlin.random.Random

/**
 * 动机发展辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [MotifTransformationDifficulty] 生成 [MotifTransformationQuestion]：
 *
 * 1. **随机选择主音（tonic）**：在中音区随机变化。
 * 2. **生成原始动机**：以主音为起点，使用小音程步进（±2/±3 半音）生成 4 音符动机。
 * 3. **从该难度候选集中选正确变换类型**。
 * 4. **应用变换**：根据变换类型对原始动机进行变换，得到变换后动机。
 * 5. **构建选项**：候选集全部变换类型的完整标签（已打乱）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class MotifTransformationEngine(
    private val random: Random = Random.Default
) {
    /**
     * 候选主音集合（MIDI）：C4-D4-E4-F4-G4-A4 白键，确保动机在中音区，
     * 变换后仍有足够余量不超出有效范围。
     */
    private val tonicPool: IntArray = intArrayOf(
        60, // C4
        62, // D4
        64, // E4
        65, // F4
        67, // G4
        69  // A4
    )

    /** 动机音程步进池（半音）：小音程，保证旋律连贯可唱。 */
    private val intervalPool: IntArray = intArrayOf(-3, -2, 2, 3)

    /** 模进移位候选（半音）：四度/五度上行或下行。 */
    private val sequenceShifts: IntArray = intArrayOf(5, 7, -5, -7)

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: MotifTransformationDifficulty): MotifTransformationQuestion {
        val seed = random.nextLong()

        // 随机选主音
        val tonic = tonicPool.random(random)

        // 生成原始动机
        val originalNotes = generateMotif(tonic, MOTIF_LENGTH, difficulty.baseNoteMs)

        // 从候选变换集合选正确答案
        val transformation = difficulty.candidates.random(random)

        // 应用变换
        val transformedNotes = applyTransformation(originalNotes, transformation, random)

        // 构建选项：候选集全部变换类型的完整标签（已打乱）
        val choices = difficulty.candidates
            .map { it.fullLabel }
            .shuffled(random)

        return MotifTransformationQuestion(
            transformation = transformation,
            difficulty = difficulty,
            seed = seed,
            originalNotes = originalNotes,
            transformedNotes = transformedNotes,
            answerChoices = choices,
            correctAnswer = transformation.fullLabel
        )
    }

    /**
     * 生成原始动机：以 tonic 为起点，使用小音程随机步进。
     *
     * @param tonic 起始 MIDI 音高
     * @param length 动机长度（音符数）
     * @param durationMs 每个音符的基础时值
     * @return 动机音符列表
     */
    private fun generateMotif(
        tonic: Int,
        length: Int,
        durationMs: Double
    ): List<MotifNote> {
        val notes = mutableListOf<MotifNote>()
        var currentPitch = tonic
        notes.add(MotifNote(currentPitch, durationMs))
        for (i in 1 until length) {
            val step = intervalPool.random(random)
            currentPitch = (currentPitch + step).coerceIn(MIN_MOTIF_MIDI, MAX_MOTIF_MIDI)
            notes.add(MotifNote(currentPitch, durationMs))
        }
        return notes
    }

    companion object {
        /** 动机长度（音符数）。 */
        const val MOTIF_LENGTH: Int = 4

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): MotifTransformationEngine =
            MotifTransformationEngine(Random(seed))
    }
}

/**
 * 对动机应用指定的变换（纯 Kotlin 顶层函数，完全可单元测试）。
 *
 * @param notes 原始动机音符列表
 * @param transformation 变换类型
 * @param random 随机数生成器（用于模进移位等随机参数）
 * @return 变换后的动机音符列表
 */
fun applyTransformation(
    notes: List<MotifNote>,
    transformation: MotifTransformation,
    random: Random = Random.Default
): List<MotifNote> {
    if (notes.isEmpty()) return emptyList()
    return when (transformation) {
        MotifTransformation.REPETITION -> notes.map { MotifNote(it.midi, it.durationMs) }

        MotifTransformation.SEQUENCE -> {
            val shift = pickSequenceShift(notes, random)
            notes.map { MotifNote((it.midi + shift).coerceIn(MIN_MIDI, MAX_MIDI), it.durationMs) }
        }

        MotifTransformation.INVERSION -> {
            val axis = notes.first().midi
            notes.map {
                MotifNote((axis - (it.midi - axis)).coerceIn(MIN_MIDI, MAX_MIDI), it.durationMs)
            }
        }

        MotifTransformation.RETROGRADE -> notes.reversed().map { MotifNote(it.midi, it.durationMs) }

        MotifTransformation.AUGMENTATION -> notes.map { MotifNote(it.midi, it.durationMs * AUGMENTATION_FACTOR) }

        MotifTransformation.DIMINUTION -> notes.map { MotifNote(it.midi, it.durationMs / AUGMENTATION_FACTOR) }
    }
}

/** 节奏扩张/紧缩系数（2.0 = 加倍/减半）。 */
const val AUGMENTATION_FACTOR: Double = 2.0

/**
 * 为模进选择一个合适的移位量，确保变换后所有音符仍在有效 MIDI 范围内。
 *
 * @param notes 原始动机音符
 * @param random 随机数生成器
 * @return 移位半音数
 */
private fun pickSequenceShift(notes: List<MotifNote>, random: Random): Int {
    val maxPitch = notes.maxOf { it.midi }
    val minPitch = notes.minOf { it.midi }
    val candidates = intArrayOf(5, 7, -5, -7).toMutableList()
    candidates.shuffle(random)
    for (shift in candidates) {
        if (maxPitch + shift <= MAX_MIDI && minPitch + shift >= MIN_MIDI) {
            return shift
        }
    }
    // 回退：用小三度
    val fallbackShift = if (maxPitch + 3 <= MAX_MIDI) 3 else -3
    return fallbackShift
}
